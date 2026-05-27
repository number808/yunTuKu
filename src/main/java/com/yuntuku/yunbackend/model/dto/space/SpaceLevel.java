package com.yuntuku.yunbackend.model.dto.space;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * AllArgsConstructor,生成接取所以参数的枚举类
 */
@Data
@AllArgsConstructor
public class SpaceLevel {

    private int value;

    private String text;

    private long maxCount;

    private long maxSize;
}
