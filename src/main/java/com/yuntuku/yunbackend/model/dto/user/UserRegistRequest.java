package com.yuntuku.yunbackend.model.dto.user;

import lombok.Data;

import java.io.Serializable;

@Data
public class UserRegistRequest implements Serializable {

    private static final long serialVersionUID = -8917363577093490447L;
    private String userAccount;
    private String userPassword;
    private String checkPassword;
}
