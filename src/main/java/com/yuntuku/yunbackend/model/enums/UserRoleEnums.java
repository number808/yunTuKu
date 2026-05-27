package com.yuntuku.yunbackend.model.enums;

import lombok.Getter;

import java.util.Objects;

/**
 * 这是一个用户角色枚举类，
 * 专门用来统一管理系统里的用户身份（普通用户 / 管理员），
 * 避免代码里到处写死 "user"、"admin" 这种字符串，
 * 让系统更安全、更好维护
 */
@Getter
public enum UserRoleEnums {
    USER("用户","user"),
    ADMIN("管理员","admin");


    private final String text;
    private final String value;

    private UserRoleEnums(String text, String value){
        this.text = text;
        this.value = value;
    }

    public static UserRoleEnums findByValue(String value){
        if(Objects.isNull(value)){
            return null;
        }

        for(UserRoleEnums userRoleEnums: UserRoleEnums.values()){
            if(userRoleEnums.getValue().equals(value)){
                return userRoleEnums;
            }
        }
        return null;
    }

}
