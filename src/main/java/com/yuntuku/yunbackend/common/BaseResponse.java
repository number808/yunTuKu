package com.yuntuku.yunbackend.common;


import com.yuntuku.yunbackend.exception.ErrorCode;
import lombok.Data;

import java.io.Serializable;

/**
 * 返回接口就用BaseResponse，而不是直接什么public String hello（）{}， int
 * @param <T>
 */
@Data
public class BaseResponse<T> implements Serializable {

        private int code;
        private String message;
        private T data;

        // 全参构造（顺序必须是：code、message、data）
        public BaseResponse(int code, T data, String message) {
            this.code = code;
            this.message = message;
            this.data = data;
        }

        // 成功：只传数据
        public BaseResponse(int code, T data) {
            this(code, data, "操作成功");
        }

        // 失败：通过错误码枚举构造
        public BaseResponse(ErrorCode errorCode) {
            this(errorCode.getCode(), null, errorCode.getMessage());

    }
}
