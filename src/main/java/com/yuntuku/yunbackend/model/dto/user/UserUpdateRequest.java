package com.yuntuku.yunbackend.model.dto.user;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import java.io.Serializable;
@Data
public class UserUpdateRequest implements Serializable {
    private static final long serialVersionUID = -1184688356614579953L;
    /**
     * 用户id
     */
    private Long id;

    /**
     * 用户昵称
     */
    private String userName;

    /**
     * 用户头像
     */
    private String userAvatar;

    /**
     * 用户简介
     */
    private String userProfile;

    /**
     * 新密码（留空表示不修改）
     */
    private String userPassword;

    /**
     * 确认新密码（填写新密码时必填）
     */
    private String checkPassword;

    /**
     * 用户角色：user/admin（仅历史/管理端兼容；前台自助更新时请忽略）
     */
    private String userRole;


}


