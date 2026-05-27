package com.yuntuku.yunbackend.model.dto.user;


import lombok.Data;

import java.io.Serializable;

@Data
public class UserLoginRequest implements Serializable {
    private static final long serialVersionUID = 37009017694519186L;
    private String userAccount;
    private String userPassword;
}
