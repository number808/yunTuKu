package com.yuntuku.yunbackend.common;

import com.yuntuku.yunbackend.exception.BusinessException;
import com.yuntuku.yunbackend.exception.ErrorCode;

/**
 * 返回结果工具类（快速生成成功/失败响应）
 */
public class ResultUtils {

    /**
     *
     * @param data
     * @return
     * @param <T>
     */
    public static<T> BaseResponse<T> success(T data) {
        return new BaseResponse<>(0,data,"ok");
    }

    /**
     *
     * @param errorCode 错误码
     * @return
     */
   public static  BaseResponse<?> error(ErrorCode errorCode) {
        return  new BaseResponse<>(errorCode);
   }

    /**
     *
     * @param errorCode 错误码
     * @param message 相应信息
     * @return
     */
   public static  BaseResponse<?> error(ErrorCode errorCode, String message) {
       return   new BaseResponse<>(errorCode.getCode(),null, message);
   }

   public static  BaseResponse<?> error(int Code, String message) {
       return  new BaseResponse<>(Code,"", message);
   }
}
