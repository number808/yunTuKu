package com.yuntuku.yunbackend.manager.upload;

import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpUtil;
import com.yuntuku.yunbackend.exception.ThrowUtils;
import org.springframework.stereotype.Service;


import com.yuntuku.yunbackend.exception.ErrorCode;

import cn.hutool.core.io.FileUtil;



import java.io.File;





@Service
public class UrlPictureUpload extends PictureUploadTemplate {  
    @Override  
    protected void validPicture(Object inputSource) {  
        String fileUrl = (String) inputSource;  
        ThrowUtils.throwIf(StrUtil.isBlank(fileUrl), ErrorCode.PARAMS_ERROR, "文件地址不能为空");
        // ... 跟之前的校验逻辑保持一致  
    }
    // ===================== 核心修改 =====================
    @Override
    protected String getOriginFilename(Object inputSource) {
        String fileUrl = (String) inputSource;

        // 1. 获取后缀
        String suffix = FileUtil.getSuffix(fileUrl);
        /**
         * AI扩图返回的suffix是jpg+‘一长串字符’
         * 直接通过判断强行转格式！不然AI扩图结果无法存入存储桶。）
         * */
        if (StrUtil.isBlank(suffix) || suffix.contains("=") || suffix.contains("?") || suffix.contains("&")) {
            suffix = "jpg";
        }

        // 2. 生成唯一名称：关键词_随机字符
        String    uniqueName = searchText + "_"
                    + "_" + RandomUtil.randomString(6);
            // 3. 拼接后缀
        return uniqueName + "." + suffix;


    }

    // ====================================================

  
    @Override  
    protected void processFile(Object inputSource, File file) throws Exception {  
        String fileUrl = (String) inputSource;  
        // 下载文件到临时目录  
        HttpUtil.downloadFile(fileUrl, file);
    }  
}
