package com.yuntuku.yunbackend.manager.sharding;

import org.apache.shardingsphere.sharding.api.sharding.standard.PreciseShardingValue;
import org.apache.shardingsphere.sharding.api.sharding.standard.RangeShardingValue;
import org.apache.shardingsphere.sharding.api.sharding.standard.StandardShardingAlgorithm;

import com.yuntuku.yunbackend.constant.SpaceConstant;

import java.util.Collection;
import java.util.Properties;


public class PictureShardingAlgorithm implements StandardShardingAlgorithm<Long> {

    @Override
    public String doSharding(Collection<String> availableTargetNames, PreciseShardingValue<Long> preciseShardingValue) {
        Long spaceId = preciseShardingValue.getValue();
        String logicTableName = preciseShardingValue.getLogicTableName();
        // 公共图库：spaceId 为 null（历史条件）或 0 — 使用逻辑主表 picture，不使用分表 picture_0
        if (SpaceConstant.isPublicPictureSpace(spaceId)) {
            return logicTableName;
        }
        // 根据 spaceId 动态生成分表名（这里通过分表名来查找该空间的图片）
        String realTableName = "picture_" + spaceId;
        //如果表存在就返回该分表
        if (availableTargetNames.contains(realTableName)) {
            return realTableName;
        } else {
           // 不存在就返回默认的picture表
            return logicTableName;
        }
    }

    @Override
    public Collection<String> doSharding(Collection<String> availableTables, RangeShardingValue<Long> rangeShardingValue) {
        // 无分片条件时 → 只查主表，不查分表
        String logicTable = rangeShardingValue.getLogicTableName();
        return java.util.Collections.singleton(logicTable);
    }


    @Override
    public Properties getProps() {
        return null;
    }

    @Override
    public void init(Properties properties) {

    }
}

