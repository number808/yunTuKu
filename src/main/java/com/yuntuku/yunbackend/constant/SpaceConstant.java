package com.yuntuku.yunbackend.constant;

/**
 * 空间与图库相关常量。公共图库图片的 spaceId 固定为 0（非 null），以满足 ShardingSphere 分片键必须出现在 SQL 中的要求。
 */
public final class SpaceConstant {

    private SpaceConstant() {
    }

    /**
     * 公共图库占位 spaceId，不对应 space 表中的记录。
     */
    public static final long PUBLIC_SPACE_ID = 0;

    /**
     * 是否为公共图库图片（含历史数据中 spaceId 为 null 的情况）。
     */
    public static boolean isPublicPictureSpace(Long spaceId) {
        return spaceId == null || spaceId == PUBLIC_SPACE_ID;
    }
}
