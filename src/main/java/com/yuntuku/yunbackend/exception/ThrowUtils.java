package com.yuntuku.yunbackend.exception;

public class ThrowUtils {

    /**
     *
     */
    public static  void throwIf(boolean value,RuntimeException runtimeException){
        if(value){
            throw runtimeException;
        }
    }

    public static  void throwIf(boolean value,ErrorCode errorCode){
        ThrowUtils.throwIf(value,new BusinessException(errorCode));
    }

    public static  void throwIf(boolean value, ErrorCode errorCode, String message){
        ThrowUtils.throwIf(value,new BusinessException(errorCode,message));
    }
}
