package com.yuntuku.yunbackend.model.dto.user;

import com.yuntuku.yunbackend.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * 用户查询
 */
@EqualsAndHashCode(callSuper = false)
@Data
public class UserQueryRequest extends PageRequest implements Serializable {
    private static final long serialVersionUID = -8548666490798553570L;
    /**
     * 用户id
     */
    private Long id;

    /**
     * 用户昵称
     */
    private String userName;

    /**
     * 用户账号
     */
    private String userAccount;

    /**
     * 用户简介
     */
    private String userProfile;

    /**
     * 用户角色：user/admin
     */
    private String userRole;
    /**
     *
     */
    private String sortField;
    /**
     *
     */
    private String sortOrder;

}
