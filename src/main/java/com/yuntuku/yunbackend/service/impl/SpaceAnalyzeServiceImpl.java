package com.yuntuku.yunbackend.service.impl;

import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yuntuku.yunbackend.constant.SpaceConstant;
import com.yuntuku.yunbackend.exception.BusinessException;
import com.yuntuku.yunbackend.exception.ErrorCode;
import com.yuntuku.yunbackend.exception.ThrowUtils;
import com.yuntuku.yunbackend.mapper.SpaceMapper;
import com.yuntuku.yunbackend.model.dto.space.analyze.*;
import com.yuntuku.yunbackend.model.entity.Picture;
import com.yuntuku.yunbackend.model.entity.Space;
import com.yuntuku.yunbackend.model.entity.User;
import com.yuntuku.yunbackend.service.PictureService;
import com.yuntuku.yunbackend.service.SpaceAnalyzeService;
import com.yuntuku.yunbackend.service.SpaceService;
import com.yuntuku.yunbackend.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
public class SpaceAnalyzeServiceImpl extends ServiceImpl<SpaceMapper, Space> implements SpaceAnalyzeService{
    @Resource
    private UserService userService;

    @Resource
    private SpaceService spaceService;

    @Resource
    private PictureService pictureService;

    /**原本我的类实现了接口 (在extend 后面加入了implements SpaceAnalyzeService)
     * 而接口中声明的方法默认是 public 的。根据Java的规则，实现接口中的方法时，访问修饰符不能比接口中定义的更严格。
     * 所以当我的方法使用private声明时，由于定义比public严格，所以会报错
     *
     * 不用暴露接口，只是我们后端使用，所以使用private
     * 类没有实现接口，或者方法不需要对外暴露的
     * @param spaceAnalyzeRequest
     * @param loginUser
     */
    private void checkSpaceAnalyzeAuth(SpaceAnalyzeRequest spaceAnalyzeRequest, User loginUser) {
        // 检查权限
        if (spaceAnalyzeRequest.isQueryAll() || spaceAnalyzeRequest.isQueryPublic()) {
            // 全空间分析或者公共图库权限校验：仅管理员可访问
            ThrowUtils.throwIf(!userService.isAdmin(loginUser), ErrorCode.NO_AUTH_ERROR, "无权访问公共图库");
        } else {
            // 私有空间权限校验
            Long spaceId = spaceAnalyzeRequest.getSpaceId();
            ThrowUtils.throwIf(spaceId == null || spaceId <= 0, ErrorCode.PARAMS_ERROR);
            Space space = spaceService.getById(spaceId);
            ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
            spaceService.checkSpaceAuth(loginUser, space);
        }
    }

    /**
     * 根据分析范围补充查询条件
     * 判断是私人空间还是所有空间，还是公共图库
     * @param spaceAnalyzeRequest
     * @param queryWrapper
     */

    private static void fillAnalyzeQueryWrapper(SpaceAnalyzeRequest spaceAnalyzeRequest, QueryWrapper<Picture> queryWrapper) {
        if (spaceAnalyzeRequest.isQueryAll()) {
            return;
        }
        if (spaceAnalyzeRequest.isQueryPublic()) {
            queryWrapper.and(w -> w.eq("spaceId", SpaceConstant.PUBLIC_SPACE_ID).or().isNull("spaceId"));
            return;
        }
        Long spaceId = spaceAnalyzeRequest.getSpaceId();
        if (spaceId != null) {
            queryWrapper.eq("spaceId", spaceId);
            return;
        }
        throw new BusinessException(ErrorCode.PARAMS_ERROR, "未指定查询范围");
    }

    /**
     * 获取空间使用分析数据
     *
     * @param spaceUsageAnalyzeRequest SpaceUsageAnalyzeRequest 请求参数
     * @param loginUser                当前登录用户
     * @return SpaceUsageAnalyzeResponse 分析结果
     */
    @Override
    public SpaceUsageAnalyzeResponse getSpaceUsageAnalyze(SpaceUsageAnalyzeRequest spaceUsageAnalyzeRequest, User loginUser) {
        ThrowUtils.throwIf(spaceUsageAnalyzeRequest == null, ErrorCode.PARAMS_ERROR);
        // 查询全部或公共图库逻辑
        if (spaceUsageAnalyzeRequest.isQueryAll() || spaceUsageAnalyzeRequest.isQueryPublic()) {
            // 仅管理员可以访问
            Boolean isAdmin = userService.isAdmin(loginUser);
            ThrowUtils.throwIf(!isAdmin, ErrorCode.NO_AUTH_ERROR);
            // 统计公共图库的资源使用
            QueryWrapper<Picture> queryWrapper = new QueryWrapper<>();
            queryWrapper.select("picSize");
            //如果不是查全部图库信息，那就是公共图库，spaceId为空
            if (!spaceUsageAnalyzeRequest.isQueryAll()) {
                queryWrapper.and(w -> w.eq("spaceId", SpaceConstant.PUBLIC_SPACE_ID).or().isNull("spaceId"));
            }
            /**
             * 使用mapper 的selectobjs 方法直接返回Object 对象，而不用封装为Picture对象，可以提高性能并节约存储空间。
             */
            List<Object> pictureObjList = pictureService.getBaseMapper().selectObjs(queryWrapper);
            //强转Long类型，如果不存在，就设置为0
            /**
             * .stream()	将 List 转换为流，便于后续操作
             * .mapToLong(...)	将每个 Object 转换为 long 类型
             * result instanceof Long ? (Long) result : 0	如果对象是 Long 类型就强转，否则返回 0（防止类型转换异常）
             * .sum()	对所有转换后的值求和
             */
            Long usedSize = pictureObjList.stream().mapToLong(result -> result instanceof Long ? (Long) result : 0).sum();
            long usedCount = pictureObjList.size();
            // 封装返回结果
            SpaceUsageAnalyzeResponse spaceUsageAnalyzeResponse = new SpaceUsageAnalyzeResponse();
            spaceUsageAnalyzeResponse.setUsedSize(usedSize);
            spaceUsageAnalyzeResponse.setUsedCount(usedCount);
            // 全部或公共图库无上限、无比例
            spaceUsageAnalyzeResponse.setMaxSize(null);
            spaceUsageAnalyzeResponse.setSizeUsageRatio(null);
            spaceUsageAnalyzeResponse.setMaxCount(null);
            spaceUsageAnalyzeResponse.setCountUsageRatio(null);

            return spaceUsageAnalyzeResponse;
        } else {
            // 查询指定空间
            Long spaceId = spaceUsageAnalyzeRequest.getSpaceId();
            ThrowUtils.throwIf(spaceId == null || spaceId <= 0, ErrorCode.PARAMS_ERROR);
            // 获取空间信息
            Space space = spaceService.getById(spaceId);
            ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");

            // 权限校验：仅空间所有者或管理员可访问
            spaceService.checkSpaceAuth(loginUser, space);

            // 构造返回结果
            SpaceUsageAnalyzeResponse response = new SpaceUsageAnalyzeResponse();
            response.setUsedSize(space.getTotalSize());
            response.setMaxSize(space.getMaxSize());
            // 后端直接算好百分比，这样前端可以直接展示
            double sizeUsageRatio = NumberUtil.round(space.getTotalSize() * 100.0 / space.getMaxSize(), 2).doubleValue();
            response.setSizeUsageRatio(sizeUsageRatio);
            response.setUsedCount(space.getTotalCount());
            response.setMaxCount(space.getMaxCount());
            double countUsageRatio = NumberUtil.round(space.getTotalCount() * 100.0 / space.getMaxCount(), 2).doubleValue();
            response.setCountUsageRatio(countUsageRatio);
            return response;
        }
    }

    /**
     * 对 picture 表中的图片数据按 category（分类）字段进行分组统计，并返回每个分类的图片数量、总大小以及分类名称
     * @param spaceCategoryAnalyzeRequest
     * @param loginUser
     * @return
     */
        @Override
        public List<SpaceCategoryAnalyzeResponse> getSpaceCategoryAnalyze(SpaceCategoryAnalyzeRequest spaceCategoryAnalyzeRequest, User loginUser) {
            ThrowUtils.throwIf(spaceCategoryAnalyzeRequest == null, ErrorCode.PARAMS_ERROR);

            // 检查权限
            checkSpaceAnalyzeAuth(spaceCategoryAnalyzeRequest, loginUser);

            // 构造查询条件
            QueryWrapper<Picture> queryWrapper = new QueryWrapper<>();
            // 根据分析范围补充查询条件
            fillAnalyzeQueryWrapper(spaceCategoryAnalyzeRequest, queryWrapper);
        /**
        * category AS category,        -- 分类名称
        *     COUNT(*) AS count,           -- 该分类下的图片数量
        *     SUM(picSize) AS totalSize    -- 该分类下图片的总大小（字节）
         *
         *     .groupBy("category")按分类分组
        */
            // 使用 MyBatis-Plus 分组查询
            queryWrapper.select("category AS category",
                            "COUNT(*) AS count",
                            "SUM(picSize) AS totalSize")
                    .groupBy("category");

            // 查询并转换结果
            /**
             *  1. 执行查询，返回 List<Map<String, Object>>
             *  例如：[{category="风景", count=2, totalSize=1536}]
             *  2.赋值
             *  如果没有分类，赋值未分类
             *  对count和totalSize进行类型转换
             *  赋值给SpaceCategoryAnalyzeResponse
             *
             *  为什么从（Number）强转程Long？
             *  Number 是 Java 中所有数值类型的抽象父类
             *  因为 result.get("count") 返回的是 Object 类型，实际的数值可能是多种类型：比如Integer，Long，Double，所以先转成Number
             *  然后再转成Long类型，这样可以避免类型不匹配，强转后抛出 ClassCastException
             *
             */
            return pictureService.getBaseMapper().selectMaps(queryWrapper)
                    .stream()
                    .map(result -> {
                        String category = result.get("category") != null ? result.get("category").toString() : "未分类";
                        Long count = ((Number) result.get("count")).longValue();
                        Long totalSize = ((Number) result.get("totalSize")).longValue();
                        return new SpaceCategoryAnalyzeResponse(category, count, totalSize);
                    })
                    .collect(Collectors.toList());
        }

    @Override
    public List<SpaceTagAnalyzeResponse> getSpaceTagAnalyze(SpaceTagAnalyzeRequest spaceTagAnalyzeRequest, User loginUser) {
        ThrowUtils.throwIf(spaceTagAnalyzeRequest == null, ErrorCode.PARAMS_ERROR);

        // 检查权限
        checkSpaceAnalyzeAuth(spaceTagAnalyzeRequest, loginUser);

        // 构造查询条件
        QueryWrapper<Picture> queryWrapper = new QueryWrapper<>();
        fillAnalyzeQueryWrapper(spaceTagAnalyzeRequest, queryWrapper);

        // 查询所有符合条件的标签
        /**
         * 只有当你的实体中 tags 字段是 List<String> 类型，并且 MyBatis-Plus 配置了自动映射时，才需要序列化
         * 我们定义的tags是一个String类型
         * 这里为什么强转成String类型？
         * 因为后续代码是需要一个String，而我们目前是Object，虽然我们知道是String类型
         * 但是系统不知道
         */

      queryWrapper.select("tags");
      List<String> tagsJsonList = pictureService.getBaseMapper().selectObjs(queryWrapper)
              .stream()
              .filter(ObjUtil::isNotNull)
              .map(ObjUtil::toString)
              .collect(Collectors.toList());

        // 合并所有标签并统计使用次数
        /**
         * 将一个 JSON 数组格式的字符串，解析成一个 Java 的 List 集合。(本质还是String转List，由于我们对tags赋值时，故意用了
         * Json数组格式，所以可使用JSONUtil来转换)
         *
         * flatMap 括号内转成的流（.stream()），会被合并到 flatMap 返回的总体流中，然后这个总体流会继续传递给 collect
         */
       Map<String,Long> tagCountMap = tagsJsonList.stream()
               .flatMap(tags->JSONUtil.toList(tags,String.class).stream())
               .collect(Collectors.groupingBy(tags->tags, Collectors.counting()));

        // 转换为响应对象，按使用次数降序排序
      return tagCountMap.entrySet().stream()
              .sorted((e1,e2)->Long.compare(e2.getValue(),e1.getValue()))
              .map(entry->new SpaceTagAnalyzeResponse(entry.getKey(),entry.getValue()))
              .collect(Collectors.toList());
    }

    @Override
    public List<SpaceSizeAnalyzeResponse> getSpaceSizeAnalyze(SpaceSizeAnalyzeRequest spaceSizeAnalyzeRequest, User loginUser) {
        ThrowUtils.throwIf(spaceSizeAnalyzeRequest == null, ErrorCode.PARAMS_ERROR);

        // 检查权限
        checkSpaceAnalyzeAuth(spaceSizeAnalyzeRequest, loginUser);

        // 构造查询条件
        QueryWrapper<Picture> queryWrapper = new QueryWrapper<>();
        fillAnalyzeQueryWrapper(spaceSizeAnalyzeRequest, queryWrapper);

        // 查询所有符合条件的图片大小
        queryWrapper.select("picSize");
        List<Long> picSizes = pictureService.getBaseMapper().selectObjs(queryWrapper)
                .stream()
                .map(size -> ((Number) size).longValue())
                .collect(Collectors.toList());

        // 定义分段范围，使用有序 Map
        /** Map<String, Long> sizeRanges = new LinkedHashMap<>();
         * 创建一个能按照插入顺序存储键值对的 Map 对象，用来表示“存储大小范围”到“图片数量”的映射关系
         */
        Map<String, Long> sizeRanges = new LinkedHashMap<>();
        sizeRanges.put("<100KB", picSizes.stream().filter(size -> size < 100 * 1024).count());
        sizeRanges.put("100KB-500KB", picSizes.stream().filter(size -> size >= 100 * 1024 && size < 500 * 1024).count());
        sizeRanges.put("500KB-1MB", picSizes.stream().filter(size -> size >= 500 * 1024 && size < 1 * 1024 * 1024).count());
        sizeRanges.put(">1MB", picSizes.stream().filter(size -> size >= 1 * 1024 * 1024).count());

        // 转换为响应对象
        return sizeRanges.entrySet().stream()
                .map(entry -> new SpaceSizeAnalyzeResponse(entry.getKey(), entry.getValue()))
                .collect(Collectors.toList());
    }

    /**
     * 通过用户上传图片时间分析空间
     */
    @Override
    public List<SpaceUserAnalyzeResponse> getSpaceUserAnalyze(SpaceUserAnalyzeRequest spaceUserAnalyzeRequest, User loginUser) {
        ThrowUtils.throwIf(spaceUserAnalyzeRequest == null, ErrorCode.PARAMS_ERROR);
        // 检查权限
        checkSpaceAnalyzeAuth(spaceUserAnalyzeRequest, loginUser);

        // 构造查询条件
        QueryWrapper<Picture> queryWrapper = new QueryWrapper<>();
        Long userId = spaceUserAnalyzeRequest.getUserId();
        //这里如果userId为空，则默认是查询全站的用户
        queryWrapper.eq(ObjUtil.isNotNull(userId), "userId", userId);
        fillAnalyzeQueryWrapper(spaceUserAnalyzeRequest, queryWrapper);

        // 分析维度：每日、每周、每月
        String timeDimension = spaceUserAnalyzeRequest.getTimeDimension();
        switch (timeDimension) {
            case "day":
                queryWrapper.select("DATE_FORMAT(createTime, '%Y-%m-%d') AS period", "COUNT(*) AS count");
                break;
            case "week":
                queryWrapper.select("YEARWEEK(createTime) AS period", "COUNT(*) AS count");
                break;
            case "month":
                queryWrapper.select("DATE_FORMAT(createTime, '%Y-%m') AS period", "COUNT(*) AS count");
                break;
            default:
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "不支持的时间维度");
        }

        // 分组和排序
        queryWrapper.groupBy("period").orderByAsc("period");

        // 查询结果并转换
        List<Map<String, Object>> queryResult = pictureService.getBaseMapper().selectMaps(queryWrapper);
        return queryResult.stream()
                .map(result -> {
                    String period = result.get("period").toString();
                    Long count = ((Number) result.get("count")).longValue();
                    return new SpaceUserAnalyzeResponse(period, count);
                })
                .collect(Collectors.toList());
    }

    /**
     * 空间使用排行（感觉可以不需要这个功能，后期可以删）
     * 该功能仅管理员可使用，返回值就是前N个空间的信息。由于已经有现成的Space空间对象，就不用编写响应视图类了。
     */
    @Override
    public List<Space> getSpaceRankAnalyze(SpaceRankAnalyzeRequest spaceRankAnalyzeRequest, User loginUser) {
        ThrowUtils.throwIf(spaceRankAnalyzeRequest == null, ErrorCode.PARAMS_ERROR);
        // 仅管理员可查看空间排行
        ThrowUtils.throwIf(!userService.isAdmin(loginUser), ErrorCode.NO_AUTH_ERROR, "无权查看空间排行");

        // 构造查询条件
        QueryWrapper<Space> queryWrapper = new QueryWrapper<>();
        queryWrapper.select("id", "spaceName", "userId", "totalSize")
                .orderByDesc("totalSize")
                .last("LIMIT " + spaceRankAnalyzeRequest.getTopN()); // 取前 N 名

        // 查询结果
        return spaceService.list(queryWrapper);
    }






}




