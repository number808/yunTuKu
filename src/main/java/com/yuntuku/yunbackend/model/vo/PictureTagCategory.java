package com.yuntuku.yunbackend.model.vo;

import lombok.Data;

import java.util.List;

/**
 * 标签视图
 */
@Data
public class PictureTagCategory {
    /*
    标签列表
*/
    private List<String> tagList;
    /**
     *  分类列表
     */
    private List<String> categoryList;
}
