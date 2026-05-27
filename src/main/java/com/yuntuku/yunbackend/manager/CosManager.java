package com.yuntuku.yunbackend.manager;

import cn.hutool.core.io.FileUtil;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.exception.CosClientException;
import com.qcloud.cos.exception.CosServiceException;
import com.qcloud.cos.model.COSObject;
import com.qcloud.cos.model.GetObjectRequest;
import com.qcloud.cos.model.PutObjectRequest;
import com.qcloud.cos.model.PutObjectResult;
/**
 * 这是上传我们本地的文件，所以用本地file组件，而不是用腾讯云COS
 */
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import com.qcloud.cos.model.ciModel.persistence.PicOperations;
import com.yuntuku.yunbackend.config.CosClientConfig;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * 在这里写对象存储方法
 * 这是一个通用类
 *
 */
@Component
public class CosManager {

    @Resource
    private CosClientConfig cosClientConfig;
    @Resource
    private COSClient  cosClient;

    // 将本地文件上传到 COS

    /**
     * https://cloud.tencent.com/document/product/436/65935#f1b2b774-d9cf-4645-8dea-b09a23045503
     * 看腾讯云上传文件的请求示例！！！！
     * 这里是上传文件
     * @param key
     * @param file
     * @return
     */
    public PutObjectResult putObject( String key, File file) {
        PutObjectRequest putObjectRequest = new PutObjectRequest(cosClientConfig.getBucket(), key, file);
        return  cosClient.putObject(putObjectRequest);

    }
    /**
     * 下载对象
     *
     * @param key 唯一键
     */
    public COSObject getObject(String key) {
        GetObjectRequest getObjectRequest = new GetObjectRequest(cosClientConfig.getBucket(), key);
        return cosClient.getObject(getObjectRequest);
    }
    /**
     * 上传对象（附带图片信息）
     *
     * @param key  唯一键
     * @param file 文件
     */
    /*
    public PutObjectResult putPictureObject(String key, File file) {
        PutObjectRequest putObjectRequest = new PutObjectRequest(cosClientConfig.getBucket(), key,
                file);
        // 对图片进行处理（获取基本信息也被视作为一种处理）
        PicOperations picOperations = new PicOperations();
        // 1 表示返回原图信息
        picOperations.setIsPicInfo(1);
        // 构造处理参数
        putObjectRequest.setPicOperations(picOperations);
        return cosClient.putObject(putObjectRequest);
    }*/
    /**
     * 上传对象（附带图片信息）
     *
     * @param key  唯一键
     * @param file 文件
     */
    public PutObjectResult putPictureObject(String key, File file) {
        /**
         * COS 上传文件的 “请求封装类”
         * 告诉COS，要上传的是那个存储桶，哪个路径，什么文件
         * 同时这行代码的天生使命就是：
         * 先把你给的 file 文件，原封不动上传到 COS
         * 后面的压缩图和缩略图只是附带的，由Rule决定
         */
        PutObjectRequest putObjectRequest = new PutObjectRequest(cosClientConfig.getBucket(), key,
                file);
        // 对图片进行处理（获取基本信息也被视作为一种处理）
        /**
         * 腾讯云 COS 的图片处理操作类
         * 上传图片时，让云端自动做哪些处理
         * 其中包含了如rules，图片基本信息（宽高、格式、大小）等
         */
        PicOperations picOperations = new PicOperations();
        //  表示返回原图信息,
        //  告诉 COS：上传完，把图片的宽、高、大小、格式 返回给我！
        picOperations.setIsPicInfo(1);
        List<PicOperations.Rule> rules = new ArrayList<>();
        //1. 图片压缩（转成 webp 格式）
        String webpKey = FileUtil.mainName(key) + ".webp";
        PicOperations.Rule compressRule = new PicOperations.Rule();
        compressRule.setRule("imageMogr2/format/webp");
        compressRule.setBucket(cosClientConfig.getBucket());
        compressRule.setFileId(webpKey);
        rules.add(compressRule);
        // 2.缩略图处理
        // 缩略图处理，仅对 > 20 KB 的图片生成缩略图
        if (file.length() > 2 * 1024) {
            PicOperations.Rule thumbnailRule = new PicOperations.Rule();
            thumbnailRule.setBucket(cosClientConfig.getBucket());
            String thumbnailKey = FileUtil.mainName(key) + "_thumbnail." + FileUtil.getSuffix(key);
            thumbnailRule.setFileId(thumbnailKey);
            // 缩放规则 /thumbnail/<Width>x<Height>>（如果大于原图宽高，则不处理）
            thumbnailRule.setRule(String.format("imageMogr2/thumbnail/%sx%s>", 128, 128));
            rules.add(thumbnailRule);
        }
        // 构造处理参数
        picOperations.setRules(rules);
        putObjectRequest.setPicOperations(picOperations);
        return cosClient.putObject(putObjectRequest);
    }

    /**
     * 删除对象
     *
     * @param key 文件 key
     */
    public void deleteObject(String key) throws CosClientException {
        cosClient.deleteObject(cosClientConfig.getBucket(), key);
    }




}
