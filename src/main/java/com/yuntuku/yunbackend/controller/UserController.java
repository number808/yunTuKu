package com.yuntuku.yunbackend.controller;


import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yuntuku.yunbackend.annotation.AuthCheck;
import com.yuntuku.yunbackend.common.BaseResponse;
import com.yuntuku.yunbackend.common.DeleteRequest;
import com.yuntuku.yunbackend.common.ResultUtils;
import com.yuntuku.yunbackend.constant.UserConstant;
import com.yuntuku.yunbackend.exception.BusinessException;
import com.yuntuku.yunbackend.exception.ErrorCode;
import com.yuntuku.yunbackend.exception.ThrowUtils;
import com.yuntuku.yunbackend.model.dto.user.*;
import com.yuntuku.yunbackend.model.entity.User;
import com.yuntuku.yunbackend.model.vo.LoginUserVO;
import com.yuntuku.yunbackend.model.vo.UserVO;
import com.yuntuku.yunbackend.service.UserService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.Date;
import java.util.List;

@RestController
@RequestMapping("/user")
public class UserController {

    @Resource
    private UserService userService;

    /**
     * 用户注册
     * @param userRegistRequest
     * @return
     */
    @PostMapping("/register")
    public BaseResponse<Long> RegisterUser(@RequestBody UserRegistRequest userRegistRequest) {
        String userAccount = userRegistRequest.getUserAccount();
        String userPassword = userRegistRequest.getUserPassword();
        String checkPassword = userRegistRequest.getCheckPassword();
        long result= userService.userRegister(userAccount, userPassword,checkPassword);
        return ResultUtils.success(result);
    }

    @PostMapping("/login")
    public BaseResponse<LoginUserVO> LoginUser(@RequestBody UserLoginRequest userLoginRequest, HttpServletRequest request) {

        ThrowUtils.throwIf(userLoginRequest==null,ErrorCode.NOT_FOUND_ERROR);
        String userAccount = userLoginRequest.getUserAccount();
        String userPassword = userLoginRequest.getUserPassword();

        LoginUserVO loginUserVO = userService.userLogin(userAccount,userPassword,request);
        return ResultUtils.success(loginUserVO);
    }

    @GetMapping("/get/login")
    public BaseResponse<LoginUserVO> getLoginUser(HttpServletRequest request) {
        User user =  userService.getLoginUser(request);
        if (user == null) {
            throw  new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        return ResultUtils.success(userService.getLoginUserVO(user));
    }

    @PostMapping("/logout")
    public BaseResponse<Boolean> logout(HttpServletRequest request) {
        Boolean result = userService.userLogout(request);
        return ResultUtils.success(result);
    }

    /**
     * 由于代码简单，所以写在Controller层
     * @param userAddRequest
     * @return
     */
    @PostMapping("/add")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Long> addUser(@RequestBody UserAddRequest userAddRequest) {
        if (ObjUtil.hasNull(userAddRequest)){
        throw  new BusinessException(ErrorCode.PARAMS_ERROR);}

    User user=new User();
    BeanUtil.copyProperties(userAddRequest,user);
    final String DEFAULT_PASSWORD ="12345678";
    user.setUserPassword(userService.getEncryptNumber(DEFAULT_PASSWORD));
    boolean result = userService.save(user);
    ThrowUtils.throwIf(!result,ErrorCode.OPERATION_ERROR);
    return ResultUtils.success(user.getId());
    }

    @GetMapping("/get")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<User> getUserById(@RequestParam("id") long id) {
        ThrowUtils.throwIf(id<=0, ErrorCode.NOT_FOUND_ERROR);
        User user = userService.getById(id);
        ThrowUtils.throwIf(user==null, ErrorCode.NOT_FOUND_ERROR);
        return ResultUtils.success(user);
    }

    @GetMapping("/get/vo")
    public BaseResponse<UserVO> getUserVoById(@RequestParam("id") long id) {
        ThrowUtils.throwIf(id<=0, ErrorCode.NOT_FOUND_ERROR);
        User user = userService.getById(id);
        return ResultUtils.success(userService.getUserVO(user));
    }

    @PostMapping("/delete")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> deleteUserById(@RequestParam("id") DeleteRequest deleteRequest) {
        ThrowUtils.throwIf(deleteRequest.getId()<=0 || deleteRequest==null, ErrorCode.NOT_FOUND_ERROR);
        Boolean  result= userService.removeById(deleteRequest.getId());
        return ResultUtils.success(result);
    }

    /**
     * 前台用户修改自己的昵称、头像、简介、可选修改密码。（需登录）
     * 不信任请求体中的 id、userRole，避免越权；原仅管理员可用的实现存在误 insert 等问题，已在本次调整。
     */
    @PostMapping("/update")
    public BaseResponse<Boolean> updateLoginUser(@RequestBody UserUpdateRequest userUpdateRequest,
                                                  HttpServletRequest request) {
        ThrowUtils.throwIf(userUpdateRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        User user = userService.getById(loginUser.getId());
        ThrowUtils.throwIf(user == null, ErrorCode.NOT_FOUND_ERROR, "用户不存在");

        if (StrUtil.isNotBlank(userUpdateRequest.getUserName())) {
            //去除字符串首尾的空白字符
            user.setUserName(userUpdateRequest.getUserName().trim());
        }
        // 允许清空头像、简介（传 ""）
        if (userUpdateRequest.getUserAvatar() != null) {
            user.setUserAvatar(StrUtil.blankToDefault(userUpdateRequest.getUserAvatar().trim(), ""));
        }
        if (userUpdateRequest.getUserProfile() != null) {
            user.setUserProfile(userUpdateRequest.getUserProfile());
        }

        String newPwd = userUpdateRequest.getUserPassword();
        if (StrUtil.isNotBlank(newPwd)) {
            String trimmed = newPwd.trim();
            ThrowUtils.throwIf(trimmed.length() < 8, ErrorCode.PARAMS_ERROR, "密码长度不能少于8位");
            String confirm = StrUtil.blankToDefault(userUpdateRequest.getCheckPassword(), "").trim();
            ThrowUtils.throwIf(!trimmed.equals(confirm), ErrorCode.PARAMS_ERROR, "两次输入的密码不一致");
            user.setUserPassword(userService.getEncryptNumber(trimmed));
        }

        user.setEditTime(new Date());
        boolean result = userService.updateById(user);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    /**
     * 上传用户头像（需登录）
     */
    @PostMapping("/upload/avatar")
    public BaseResponse<String> uploadAvatar(@RequestParam("file") MultipartFile file,
                                             HttpServletRequest request) {
        String avatarUrl = userService.uploadAvatar(file, request);
        return ResultUtils.success(avatarUrl);
    }

    @PostMapping("/list/page/vo")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<UserVO>> listUserVOByPage(@RequestBody UserQueryRequest userQueryRequest){
            ThrowUtils.throwIf(userQueryRequest ==null,ErrorCode.NOT_FOUND_ERROR);

            long current = userQueryRequest.getCurrent();
            long pageSize = userQueryRequest.getPageSize();

            Page<User> userPage = userService.page(new Page<>(current,pageSize),userService.getQueryWrapper(userQueryRequest));
        /**
         * 这条代码是直接新建一个New的page（空的），而上面的page是使用了Mybatis-plus自带的userService的page接口
         */
        // 注意：第三个参数是总条数，必须从 userPage 里拿，否则前端分页总数错误！
        Page<UserVO> userVOPage = new Page<>(current,pageSize,userPage.getTotal());
        //把分页的数据拿出来（getRecords），赋值给userVOList
        List<UserVO> userVOList = userService.getUserVOList(userPage.getRecords());
        /**
         * 把转化来的userVOList通过setRecord，赋值给空的userVOPage
         */
        userVOPage.setRecords(userVOList);
        return ResultUtils.success(userVOPage);

    }


}