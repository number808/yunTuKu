package com.yuntuku.yunbackend.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yuntuku.yunbackend.config.CosClientConfig;
import com.yuntuku.yunbackend.constant.UserConstant;
import com.yuntuku.yunbackend.exception.BusinessException;
import com.yuntuku.yunbackend.exception.ErrorCode;
import com.yuntuku.yunbackend.exception.ThrowUtils;
import com.yuntuku.yunbackend.manager.CosManager;
import com.yuntuku.yunbackend.manager.auth.StpKit;
import com.yuntuku.yunbackend.model.dto.user.UserQueryRequest;
import com.yuntuku.yunbackend.model.entity.User;
import com.yuntuku.yunbackend.model.enums.UserRoleEnums;
import com.yuntuku.yunbackend.model.vo.LoginUserVO;
import com.yuntuku.yunbackend.model.vo.UserVO;
import com.yuntuku.yunbackend.service.PictureService;
import com.yuntuku.yunbackend.service.UserService;
import com.yuntuku.yunbackend.mapper.UserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
* @author PUFF
 * 针对表【user(用户)】的数据库操作Service实现
* @createDate 2026-03-22 15:52:51
*/
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User>
    implements UserService {

    @Resource
    private CosManager cosManager;

    @Resource
    private CosClientConfig cosClientConfig;

    @Resource
    private PictureService pictureService;

    @Override
    public long userRegister(String userAccount, String userPassword, String checkPassword) {
        if (StrUtil.hasBlank(userAccount,userPassword,checkPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"注册信息有空");
        }
        if (userAccount.length() < 4) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"用户账号过短");
        }
        if (userPassword.length() < 6) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"用户密码过短");
        }
        if (!userPassword.equals(checkPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"两次密码不一致");
        }

        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("userAccount",userAccount);
        long count = baseMapper.selectCount(queryWrapper);
        if (count > 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"账号已经存在");
        }
    //对密码加密
        userPassword = getEncryptNumber(userPassword);
        User user = new User();
        user.setUserAccount(userAccount);
        user.setUserPassword(userPassword);
        user.setUserRole(UserRoleEnums.USER.getValue());


        boolean result = this.save(user);
        if (!result) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR,"注册错误，数据库错误");
        }

        //主键回填，因为save完后，ID自增了
        return user.getId();

    }


    @Override
    public LoginUserVO userLogin(String userAccount, String userpassword, HttpServletRequest  request) {
        if(StrUtil.hasBlank(userAccount,userpassword)){
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR,"空账号或空密码");
        }
        String password = getEncryptNumber(userpassword);
        //判断用户是否存在
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("userAccount",userAccount);
        queryWrapper.eq("userPassword",password);
        User user = baseMapper.selectOne(queryWrapper);
        if (user == null) {
            log.info("用户不存在");
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR,"用户不存在");
        }
        //保存用户登录态
        //为什么要用到Constant常量？因为这里保存session的时候，我们的Key用的是login_user，如果拼错了，那么将
        //无法得到正确的session，业务就出错了，所以需要一个constant声明常量，来保存不会出错
        request.getSession().setAttribute(UserConstant.USER_LOGIN_STATE,user);
        //将用户登录信息传给sa-token
        StpKit.SPACE.login(user.getId());
        StpKit.SPACE.getSession().set(UserConstant.USER_LOGIN_STATE,user);


        return this.getLoginUserVO(user);
    }

    @Override
    public LoginUserVO getLoginUserVO(User user) {
        if (user == null) {
            return null;
        }
        LoginUserVO loginUserVO = new LoginUserVO();
        BeanUtils.copyProperties(user,loginUserVO);
        return loginUserVO;
    }

    @Override
    public User getLoginUser(HttpServletRequest request){

       Object userObj= request.getSession().getAttribute(UserConstant.USER_LOGIN_STATE);
       User user = (User) userObj;
       if (user == null||user.getId()==null) {
           throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
       }
       User currentUser= this.getById(user.getId());
       if (currentUser==null) {
           throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
       }
       return currentUser;
    }


    @Override
    public String getEncryptNumber(String num){
        final String SALT="thisisasalt";
        return DigestUtils.md5DigestAsHex((SALT+num).getBytes());
    }



    @Override
    public Boolean userLogout(HttpServletRequest request){
        User user = this.getLoginUser(request);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
       request.getSession().removeAttribute(UserConstant.USER_LOGIN_STATE);

        return  true;
    }

    /**
     * 脱敏用户
     * @param user
     * @return
     */
    @Override
    public UserVO getUserVO(User user) {
        if (user == null) {
            return null;
        }
        UserVO UserVO = new UserVO();
        BeanUtils.copyProperties(user,UserVO);
        return UserVO;
    }
    /**
     * 脱敏用户列表
     * @param userList
     * @return
     */
    @Override
    public List<UserVO> getUserVOList(List<User> userList) {
        if (CollUtil.isEmpty(userList)) {
            return new ArrayList<>();
        }

        return userList.stream().map(this::getUserVO).collect(Collectors.toList());
    }

    @Override
    public QueryWrapper<User> getQueryWrapper(UserQueryRequest userQueryRequest){
        if (userQueryRequest == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
        }

        Long id = userQueryRequest.getId();
        String userName = userQueryRequest.getUserName();
        String userAccount = userQueryRequest.getUserAccount();
        String userProfile = userQueryRequest.getUserProfile();
        String userRole = userQueryRequest.getUserRole();
        int current = userQueryRequest.getCurrent();
        int pageSize = userQueryRequest.getPageSize();
        String sortField = userQueryRequest.getSortField();
        String sortOrder = userQueryRequest.getSortOrder();
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq(ObjUtil.isNotNull(id),  "id", id);
        queryWrapper.eq(StrUtil.isNotBlank(userRole),  "userRole", userRole);
        queryWrapper.like(StrUtil.isNotBlank(userAccount),"userAccount", userAccount);
        queryWrapper.like(StrUtil.isNotBlank(userName), "userName", userName);
        queryWrapper.like(StrUtil.isNotBlank(userProfile), "userProfile", userProfile);
        queryWrapper.orderBy(StrUtil.isNotEmpty(sortField), sortOrder.equals("ascend"), sortField);
        return queryWrapper;
    }

    @Override
    public Boolean isAdmin(User user) {
        return user!=null && UserRoleEnums.ADMIN.getValue().equals(user.getUserRole());
    }

    @Override
    public String uploadAvatar(MultipartFile multipartFile, HttpServletRequest request) {
        ThrowUtils.throwIf(multipartFile == null || multipartFile.isEmpty(), ErrorCode.PARAMS_ERROR, "文件不能为空");

        // 校验文件大小
        final long ONE_M = 1024 * 1024L;
        ThrowUtils.throwIf(multipartFile.getSize() > 2 * ONE_M, ErrorCode.PARAMS_ERROR, "文件大小不能超过 2M");

        // 校验文件类型
        String fileSuffix = FileUtil.getSuffix(multipartFile.getOriginalFilename());
        final List<String> ALLOW_FORMAT_LIST = Arrays.asList("jpeg", "jpg", "png", "webp");
        ThrowUtils.throwIf(!ALLOW_FORMAT_LIST.contains(fileSuffix), ErrorCode.PARAMS_ERROR, "文件类型错误，仅支持 jpg/png/webp");

        User loginUser = getLoginUser(request);

       // pictureService.fillReviewParams(picture, loginUser);
        // 构建上传路径
        String uuid = RandomUtil.randomString(16);
        String uploadKey = String.format("avatar/%d_%s.%s", loginUser.getId(), uuid, fileSuffix);

        File tempFile = null;
        try {
            tempFile = File.createTempFile(uploadKey, null);
            multipartFile.transferTo(tempFile);
            cosManager.putObject(uploadKey, tempFile);
        } catch (IOException e) {
            log.error("头像上传失败", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "头像上传失败");
        } finally {
            if (tempFile != null && tempFile.exists()) {
                tempFile.delete();
            }
        }

        String avatarUrl = cosClientConfig.getHost() + "/" + uploadKey;
        User user = getById(loginUser.getId());
        user.setUserAvatar(avatarUrl);
        updateById(user);

        return avatarUrl;
    }

}




