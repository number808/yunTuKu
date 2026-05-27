package com.yuntuku.yunbackend.manager.sharding;

import com.baomidou.mybatisplus.extension.toolkit.SqlRunner;
import com.yuntuku.yunbackend.model.entity.Space;
import com.yuntuku.yunbackend.model.enums.SpaceLevelEnum;
import com.yuntuku.yunbackend.model.enums.SpaceTypeEnum;
import com.yuntuku.yunbackend.service.SpaceService;
import lombok.extern.slf4j.Slf4j;
import org.apache.shardingsphere.driver.jdbc.core.connection.ShardingSphereConnection;
import org.apache.shardingsphere.infra.metadata.database.rule.ShardingSphereRuleMetaData;
import org.apache.shardingsphere.mode.manager.ContextManager;
import org.apache.shardingsphere.sharding.api.config.ShardingRuleConfiguration;
import org.apache.shardingsphere.sharding.api.config.rule.ShardingTableRuleConfiguration;
import org.apache.shardingsphere.sharding.rule.ShardingRule;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@Slf4j
@ConditionalOnBean(name = "spaceServiceImpl")
public class DynamicShardingManager {

    @Resource
    private DataSource dataSource;

    @Resource
    private SpaceService spaceService;

    private static final String LOGIC_TABLE_NAME = "picture";

    private static final String DATABASE_NAME = "logic_db";// 默认的配置文件中的数据库名称
    //改（Spring 完全启动后再执行）
    @EventListener(ApplicationReadyEvent.class)
    public void initialize() {
        log.info("初始化动态分表配置...");
        updateShardingTableNodes();
    }

    /**
     * 获取所有动态表名，包括初始表 picture 和分表 picture_{spaceId}
     */
    private Set<String> fetchAllPictureTableNames() {
        // 为了测试方便，直接对所有团队空间分表（实际上线改为仅对旗舰版生效）
        //已改
        //提取SpaceId放入Set数组里
        // 改成（正确，只查旗舰版）
        Set<Long> spaceIds = spaceService.lambdaQuery()
                .eq(Space::getSpaceType, SpaceTypeEnum.TEAM.getValue())
                .eq(Space::getSpaceLevel, SpaceLevelEnum.FLAGSHIP.getValue()) // 加这一行！
                .list()
                .stream()
                .map(Space::getId)
                .collect(Collectors.toSet());
        //通过spaceId拿到分表的名称
        Set<String> tableNames = spaceIds.stream()
                .map(spaceId -> LOGIC_TABLE_NAME + "_" + spaceId)
                .collect(Collectors.toSet());
        // 添加初始逻辑表
        tableNames.add(LOGIC_TABLE_NAME);
        return tableNames;
    }

    /**
     * 更新 ShardingSphere 的 actual-data-nodes 动态表名配置
     */
    private void updateShardingTableNodes() {
        // 1. 获取当前所有分表名：picture、picture_1、picture_2...
        Set<String> tableNames = fetchAllPictureTableNames();
        // 2. 拼成字符串：把表名列表 拼接成 ShardingSphere 要求的格式
        // yun_picture.picture,yun_picture.picture_1,yun_picture.picture_2
        String newActualDataNodes = tableNames.stream()
                .map(tableName -> "yun_picture." + tableName) // 确保前缀合法
                .collect(Collectors.joining(","));
        log.info("动态分表 actual-data-nodes 配置: {}", newActualDataNodes);

        // 3. 获取 ShardingSphere 内部核心管理器
        ContextManager contextManager = getContextManager();
        // 4. 获取分表规则
        /**
         * 进入 ShardingSphere 内部，拿到 当前数据库的规则管理器拿到它才能修改分表配置。
         */
        ShardingSphereRuleMetaData ruleMetaData = contextManager.getMetaDataContexts()
                .getMetaData()
                .getDatabases()
                .get(DATABASE_NAME)
                .getRuleMetaData();
        /**
        * 找到 分片规则（ShardingRule）
         * 如果找不到，就报错：未找到分片规则。
        */
        Optional<ShardingRule> shardingRule = ruleMetaData.findSingleRule(ShardingRule.class);

        if (shardingRule.isPresent()) {
            //拿到ShardingRule的Configrration
            ShardingRuleConfiguration ruleConfig = (ShardingRuleConfiguration) shardingRule.get().getConfiguration();
            List<ShardingTableRuleConfiguration> updatedRules = ruleConfig.getTables()
                    .stream()
                    .map(oldTableRule -> {
                        //找到逻辑表 picture 的配置。只修改 picture 这张逻辑表
                        if (LOGIC_TABLE_NAME.equals(oldTableRule.getLogicTable())) {
                            /**找到了！
                             *
                             * 创建一个全新的 picture 逻辑表规则
                             * 并且把最新的 “真实存在的表” 绑定给它
                             * 第一个参数：LOGIC_TABLE_NAME
                             * 逻辑表名 = picture
                             * 第二个参数：newActualDataNodes
                             * 真实表列表 = yun_picture.picture_1,yun_picture.picture_2...
                             */
                            ShardingTableRuleConfiguration newTableRuleConfig = new ShardingTableRuleConfiguration(LOGIC_TABLE_NAME, newActualDataNodes);
                            newTableRuleConfig.setDatabaseShardingStrategy(oldTableRule.getDatabaseShardingStrategy());
                            newTableRuleConfig.setTableShardingStrategy(oldTableRule.getTableShardingStrategy());
                            newTableRuleConfig.setKeyGenerateStrategy(oldTableRule.getKeyGenerateStrategy());
                            newTableRuleConfig.setAuditStrategy(oldTableRule.getAuditStrategy());
                            return newTableRuleConfig;
                        }
                        return oldTableRule;
                    })
                    .collect(Collectors.toList());
            /**-
             * 把新规则覆盖旧规则
             * 告诉 ShardingSphere：规则已修改
             * 重新加载数据库
             * 输出成功日志
             */
            ruleConfig.setTables(updatedRules);
            contextManager.alterRuleConfiguration(DATABASE_NAME, Collections.singleton(ruleConfig));
            contextManager.reloadDatabase(DATABASE_NAME);
            log.info("动态分表规则更新成功！");
        } else {
            log.error("未找到 ShardingSphere 的分片规则配置，动态分表更新失败。");
        }
    }

    /**
     * 获取 ShardingSphere ContextManager
     */
    private ContextManager getContextManager() {
        try (ShardingSphereConnection connection = dataSource.getConnection().unwrap(ShardingSphereConnection.class)) {
            return connection.getContextManager();
        } catch (SQLException e) {
            throw new RuntimeException("获取 ShardingSphere ContextManager 失败", e);
        }
    }

    public void createSpacePictureTable(Space space) {
        // 动态创建分表
        // 仅为旗舰版团队空间创建分表
        if (space.getSpaceType() == SpaceTypeEnum.TEAM.getValue() && space.getSpaceLevel() == SpaceLevelEnum.FLAGSHIP.getValue()) {
            Long spaceId = space.getId();
            String tableName = "picture_" + spaceId;
            // 创建新表
            String createTableSql = "CREATE TABLE " + tableName + " LIKE picture";
            try {
                SqlRunner.db().update(createTableSql);
                // 更新分表
                updateShardingTableNodes();
            } catch (Exception e) {
                log.error("创建图片空间分表失败，空间 id = {}", space.getId());
            }
        }
    }

}
