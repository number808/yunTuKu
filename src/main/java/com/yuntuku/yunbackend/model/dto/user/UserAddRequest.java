package com.yuntuku.yunbackend.model.dto.user;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import java.io.Serializable;
import java.util.Date;

/**
 * 给管理员用的
 */
@Data
public class UserAddRequest implements Serializable {
    private static final long serialVersionUID = 4154998127224436304L;

    /**
     * 账号
     * 不能为空，长度至少4位
     */
    @NotBlank(message = "用户账号不能为空")
    @Size(min = 4, message = "用户账号长度不能少于4位")
    private String userAccount;


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
     * 用户角色：user/admin
     */
    private String userRole;

}
