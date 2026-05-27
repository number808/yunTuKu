package com.yuntuku.yunbackend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.IService;
import com.yuntuku.yunbackend.model.dto.user.UserQueryRequest;
import com.yuntuku.yunbackend.model.entity.User;
import com.yuntuku.yunbackend.model.vo.LoginUserVO;
import com.yuntuku.yunbackend.model.vo.UserVO;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
* @author PUFF
* @description 针对表【user(用户)】的数据库操作Service
* @createDate 2026-03-22 15:52:51
*/
public interface UserService extends IService<User> {

    /**
     *
     * @param username
     * @param password
     * @param checkPassword
     * @return
     */
        long userRegister(String username,String password,String checkPassword);

        LoginUserVO userLogin(String userAccount, String userpassword, HttpServletRequest request);

    LoginUserVO getLoginUserVO(User user);
    UserVO getUserVO(User user);


    /**
     * 通过request来获得User，可与getLoginUserVO联动
     * @param request
     * @return
     */
    User getLoginUser(HttpServletRequest request);

    /**
     * 加密
     * @param num
     * @return
     */
    String getEncryptNumber(String num);

    /**
     * 用户注销
     */
    Boolean userLogout(HttpServletRequest request);

    List<UserVO> getUserVOList(List<User> userList);


    QueryWrapper<User> getQueryWrapper(UserQueryRequest userQueryRequest);
    Boolean isAdmin(User user);

    /**
     * 上传用户头像
     */
    String uploadAvatar(MultipartFile file, HttpServletRequest request);
}
