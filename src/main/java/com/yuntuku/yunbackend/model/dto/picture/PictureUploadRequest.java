package com.yuntuku.yunbackend.model.dto.picture;

import lombok.Data;

import java.io.Serializable;

@Data
public class PictureUploadRequest implements Serializable {
    private static final long serialVersionUID = 1L;
    /**
     * 一个图片ID足以
     */
    private Long id;
    /**
     * 空间 id
     */
    private Long spaceId;


    /**
         * 文件地址
         */
        private String fileUrl;



}
