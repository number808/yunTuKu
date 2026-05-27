package com.yuntuku.yunbackend.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yuntuku.yunbackend.api.aliyunai.AliYunAiApi;
import com.yuntuku.yunbackend.api.aliyunai.model.CreateOutPaintingTaskRequest;
import com.yuntuku.yunbackend.api.aliyunai.model.CreateOutPaintingTaskResponse;
import com.yuntuku.yunbackend.exception.BusinessException;
import com.yuntuku.yunbackend.manager.*;
import com.yuntuku.yunbackend.manager.upload.FilePictureUpload;
import com.yuntuku.yunbackend.manager.upload.PictureUploadTemplate;
import com.yuntuku.yunbackend.manager.upload.UrlPictureUpload;
import com.yuntuku.yunbackend.mapper.PictureMapper;
import com.yuntuku.yunbackend.model.dto.picture.*;

import com.yuntuku.yunbackend.model.entity.Picture ;
import com.yuntuku.yunbackend.model.entity.Space;
import com.yuntuku.yunbackend.model.entity.User;
import com.yuntuku.yunbackend.model.enums.PictureReviewStatusEnum;
import com.yuntuku.yunbackend.model.vo.PictureVO;

import com.yuntuku.yunbackend.model.vo.UserVO;
import com.yuntuku.yunbackend.service.PictureService;
import com.yuntuku.yunbackend.service.SpaceService;
import com.yuntuku.yunbackend.service.UserService;
import com.yuntuku.yunbackend.utils.ColorSimilarUtils;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.BeanUtils;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.yuntuku.yunbackend.constant.SpaceConstant;
import com.yuntuku.yunbackend.exception.ErrorCode;
import com.yuntuku.yunbackend.exception.ThrowUtils;
import com.yuntuku.yunbackend.model.dto.file.UploadPictureResult;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import javax.annotation.Resource;

import javax.servlet.http.HttpServletRequest;


import java.awt.*;
import java.io.IOException;
import java.util.*;
import java.util.List;

import java.util.stream.Collectors;

/**
 * @author PUFF
 * @description 针对表【picture(图片)】的数据库操作Service实现
 * @createDate 2026-03-24 16:53:37
 */
@Service
@Slf4j
public class PictureServiceImpl extends ServiceImpl<PictureMapper, Picture>
        implements PictureService{

    @Resource
    private FileManager fileManager;
    @Resource
    private UserService userService;
    @Resource
    private FilePictureUpload filePictureUpload;

    @Resource
    private UrlPictureUpload urlPictureUpload;

    @Resource
    private CosManager cosManager;

    @Resource
    @Lazy
    private SpaceService spaceService;

    @Resource
    private TransactionTemplate transactionTemplate;

    @Resource
    private AliYunAiApi aliYunAiApi;

    /**
     * 对单个图像进行处理
     * @param picture
     * @param request
     * @return
     */
    @Override
    public PictureVO getPictureVO(Picture picture, HttpServletRequest request){
        ThrowUtils.throwIf(picture==null,ErrorCode.NOT_FOUND_ERROR);
        PictureVO pictureVO = new PictureVO();
        BeanUtils.copyProperties(picture,pictureVO);
        if(picture.getId()!=null && picture.getId()>0) {
            User user = userService.getById(picture.getUserId());
            UserVO userVO = userService.getUserVO(user);
            pictureVO.setUser(userVO);
        }
        return  pictureVO;
    }

    /**
     * 对列表Picture进行处理
     * 使用map（Long ,List(Picture)）来判断一个用户Id对应的Picture，从而快速对picture处理
     * 优点：相较与一个一个处理，减少了对Service的处理
     * @param picturePage
     * @param request
     * @return
     */
    @Override
    public Page<PictureVO> getPictureVOPage(Page<Picture> picturePage, HttpServletRequest request) {
        //拿出当前页面所有图片
        List<Picture> pictureList = picturePage.getRecords();
        //根据信息创建新的空页面·
        // current：当前第几页
        //·  size：每页多少条
        //·  total：总共有多少条数据
        Page<PictureVO> pictureVOPage = new Page<>(picturePage.getCurrent(), picturePage.getSize(), picturePage.getTotal());
        if (CollUtil.isEmpty(pictureList)) {
            return pictureVOPage;
        }
        // 对象列表 => 封装对象列表
        List<PictureVO> pictureVOList = pictureList.stream().map(PictureVO::objToVo).collect(Collectors.toList());
        // 1. 关联查询用户信息
        Set<Long> userIdSet=pictureList.stream().map(Picture::getUserId).collect(Collectors.toSet());

        Map<Long,List<User>> userIdUserListMap = userService.listByIds(userIdSet).stream().collect(Collectors.groupingBy(User::getId));


        // 2. 填充信息
        pictureVOList.forEach(pictureVO -> {
            Long userId = pictureVO.getUserId();
            User user = null;
            if (userIdUserListMap.containsKey(userId)) {
                user = userIdUserListMap.get(userId).get(0);
            }
            pictureVO.setUser(userService.getUserVO(user));
        });
        pictureVOPage.setRecords(pictureVOList);
        return pictureVOPage;
    }



    // 上传图片

    /**
     *
     * @param inputSource 存的文件本体，或者是图片URL
     * 如果是文件本体，直接使用FilePictureUpload
     * 如果是图片URL，则拼接完图片路径之后，通过hutool，HttpUtil.downloadfile来下载文件，再进行后续
     * @param pictureUploadRequest
     * @param loginUser
     * @return
     */
    @Override
    public PictureVO uploadPicture(Object inputSource, PictureUploadRequest pictureUploadRequest, User loginUser) {
        if (inputSource == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "图片为空");
        }
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR);
        // 更新时请求里可能未带 spaceId，须沿用原图空间，避免被当成公共图库
        Long pictureId = pictureUploadRequest.getId();
        Long spaceId = pictureUploadRequest.getSpaceId();
        long oldFileSize = 0L;
        Picture oldPicture = null;
        if (pictureId != null) {
            oldPicture = this.getById(pictureId);
            ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR, "图片不存在");
            if (spaceId == null) {
                spaceId = oldPicture.getSpaceId();
            }
            if (oldPicture.getPicSize() != null) {
                oldFileSize = oldPicture.getPicSize();
            }
        }
        if (SpaceConstant.isPublicPictureSpace(spaceId)) {
            spaceId = SpaceConstant.PUBLIC_SPACE_ID;
        }
        if (spaceId > 0) {
            Space space = spaceService.getById(spaceId);
            ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
            // 仅「新增」占用一条名额；「更新」不增加 totalCount
            if (pictureId == null) {
                if (space.getTotalCount() >= space.getMaxCount()) {
                    throw new BusinessException(ErrorCode.OPERATION_ERROR, "空间条数不足");
                }
                if (space.getTotalSize() >= space.getMaxSize()) {
                    throw new BusinessException(ErrorCode.OPERATION_ERROR, "空间大小不足");
                }
            }
        }

        // 按照用户 id 划分目录 => 按照空间划分目录
        String uploadPathPrefix;
        if (spaceId == SpaceConstant.PUBLIC_SPACE_ID) {
            uploadPathPrefix = String.format("public/%s", loginUser.getId());
        } else {
            uploadPathPrefix = String.format("space/%s", spaceId);
        }

        // 根据 inputSource 类型区分上传方式
        /**
         * 这里默认Object使用的是文件上传
         * 后续再判断IF，传入的是URL
         * 再使用UrlPictuerUpload
         *
         !!!!!!!!!!!!!!父类使用子类
         */
        PictureUploadTemplate pictureUploadTemplate = filePictureUpload;
        //判断URL
        if (inputSource instanceof String) {
            pictureUploadTemplate = urlPictureUpload;
        }

        /**
         * 这里通过判断Object类型，选择了合适的pictureUploadTemplate的uploadPicture来下载文件
         */
        UploadPictureResult uploadPictureResult = pictureUploadTemplate.uploadPicture(inputSource, uploadPathPrefix);
        // 构造要入库的图片信息
        Picture picture = new Picture();
        picture.setUrl(uploadPictureResult.getUrl());
        picture.setName(uploadPictureResult.getPicName());
        picture.setPicSize(uploadPictureResult.getPicSize());
        picture.setPicWidth(uploadPictureResult.getPicWidth());
        picture.setPicHeight(uploadPictureResult.getPicHeight());
        picture.setPicScale(uploadPictureResult.getPicScale());
        picture.setPicFormat(uploadPictureResult.getPicFormat());
        picture.setUserId(loginUser.getId());
        picture.setThumbnailUrl(uploadPictureResult.getThumbnailUrl());
        picture.setPicColor(uploadPictureResult.getPicColor());

        // 补充设置 spaceId
        picture.setSpaceId(spaceId);

        // 如果 pictureId 不为空，表示更新，否则是新增
        //做一个图片校验
        this.fillReviewParams(picture, loginUser);
        /**
         * 如果是新增的话，Id会自增
         * 但是如果有pictureId，说明是更新（数据库已经有图片信息）
         * 这时候就需要把PictureId传进去
         * 让this.saveOrUpdate(picture);这条代码可以通过pictureId来对数据进行更新
         */

        /**！！**********极度危险***********
         * **********极度危险***********
         * **********极度危险***********
         * 如果是更新操作，绝对不能加SpaceId
         * 因为我们将SpaceId作为了分片键
         * 而ShardingSphere绝不允许分片键被修改！！！！！！！！！！！！！！
         * 这导致了我图片编辑更新操作一直报错
         * 由于做图片上传一直的时候没做分页分表，所有我没有考虑这点
         * 等写了分库分表之后，把这里忘了
         */
        if (pictureId != null) {
            picture.setId(pictureId);
            picture.setEditTime(new Date());
            // 重点：更新时清空分片键，避免报错
            picture.setSpaceId(null);
        }
        if (pictureId != null && spaceId > 0) {
            long delta = picture.getPicSize() - oldFileSize;
            Space sp = spaceService.getById(spaceId);
            ThrowUtils.throwIf(sp == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
            if (sp.getTotalSize() + delta > sp.getMaxSize()) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "空间大小不足");
            }
        }
        final long oldSizeForTransaction = oldFileSize;
        // 开启事务
        Long finalSpaceId = spaceId;
        transactionTemplate.execute(status -> {
            //上传图片
            boolean result = this.saveOrUpdate(picture);
            ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "图片上传失败");

            // 仅私有空间更新额度：新增 +条数&+大小；更新仅按与旧图体积差调整 totalSize
            if (finalSpaceId > 0) {
                if (pictureId == null) {
                    boolean update = spaceService.lambdaUpdate()
                            .eq(Space::getId, finalSpaceId)
                            .setSql("totalSize = totalSize + " + picture.getPicSize())
                            .setSql("totalCount = totalCount + 1")
                            .update();
                    ThrowUtils.throwIf(!update, ErrorCode.OPERATION_ERROR, "额度更新失败");
                } else {
                    //更新图片，只对大小进行处理
                    long sizeDelta = picture.getPicSize() - oldSizeForTransaction;
                    if (sizeDelta != 0) {
                        boolean update = spaceService.lambdaUpdate()
                                .eq(Space::getId, finalSpaceId)
                                .setSql("totalSize = totalSize + (" + sizeDelta + ")")
                                .update();
                        ThrowUtils.throwIf(!update, ErrorCode.OPERATION_ERROR, "额度更新失败");
                    }
                }
            }

            return picture;
        });

        return PictureVO.objToVo(picture);
    }


    @Override
    public QueryWrapper<Picture> getQueryWrapper(PictureQueryRequest pictureQueryRequest) {
        QueryWrapper<Picture> queryWrapper = new QueryWrapper<>();
        if (pictureQueryRequest == null) {
            return queryWrapper;
        }
        // 从对象中取值
        Long id = pictureQueryRequest.getId();
        String name = pictureQueryRequest.getName();
        String introduction = pictureQueryRequest.getIntroduction();
        String category = pictureQueryRequest.getCategory();
        List<String> tags = pictureQueryRequest.getTags();
        Long picSize = pictureQueryRequest.getPicSize();
        Integer picWidth = pictureQueryRequest.getPicWidth();
        Integer picHeight = pictureQueryRequest.getPicHeight();
        Double picScale = pictureQueryRequest.getPicScale();
        String picFormat = pictureQueryRequest.getPicFormat();
        String searchText = pictureQueryRequest.getSearchText();
        Long userId = pictureQueryRequest.getUserId();
        String sortField = pictureQueryRequest.getSortField();
        String sortOrder = pictureQueryRequest.getSortOrder();
        Integer reviewStatus = pictureQueryRequest.getReviewStatus();
        String reviewMessage = pictureQueryRequest.getReviewMessage();
        Long reviewerId = pictureQueryRequest.getReviewerId();
        Long spaceId = pictureQueryRequest.getSpaceId();

        boolean nullSpaceId = pictureQueryRequest.isNullSpaceId();  //boolean类型用 isNullSpaceId()
        Date startEditTime = pictureQueryRequest.getStartEditTime();
        Date endEditTime = pictureQueryRequest.getEndEditTime();


        // 从多字段中搜索
        if (StrUtil.isNotBlank(searchText)) {
            // 需要拼接查询条件
            queryWrapper.and(qw -> qw.like("name", searchText)
                    .or()
                    .like("introduction", searchText)
            );
        }
        queryWrapper.eq(ObjUtil.isNotEmpty(id), "id", id);
        queryWrapper.eq(ObjUtil.isNotEmpty(userId), "userId", userId);
        queryWrapper.like(StrUtil.isNotBlank(name), "name", name);
        queryWrapper.like(StrUtil.isNotBlank(introduction), "introduction", introduction);
        queryWrapper.like(StrUtil.isNotBlank(picFormat), "picFormat", picFormat);
        queryWrapper.eq(StrUtil.isNotBlank(category), "category", category);
        queryWrapper.eq(ObjUtil.isNotEmpty(picWidth), "picWidth", picWidth);
        queryWrapper.eq(ObjUtil.isNotEmpty(picHeight), "picHeight", picHeight);
        queryWrapper.eq(ObjUtil.isNotEmpty(picSize), "picSize", picSize);
        queryWrapper.eq(ObjUtil.isNotEmpty(picScale), "picScale", picScale);
        queryWrapper.eq(ObjUtil.isNotEmpty(reviewStatus), "reviewStatus", reviewStatus);
        queryWrapper.like(StrUtil.isNotBlank(reviewMessage), "reviewMessage", reviewMessage);
        queryWrapper.eq(ObjUtil.isNotEmpty(reviewerId), "reviewerId", reviewerId);
        queryWrapper.ge(ObjUtil.isNotEmpty(startEditTime), "editTime", startEditTime);
        queryWrapper.lt(ObjUtil.isNotEmpty(endEditTime), "editTime", endEditTime);
        // 公共图库：spaceId=0 或历史 null（与下方按 spaceId 筛选互斥，避免 AND 掉 isNull 行）
        if (pictureQueryRequest.isNullSpaceId()) {
            queryWrapper.and(w -> w.eq("spaceId", SpaceConstant.PUBLIC_SPACE_ID).or().isNull("spaceId"));
        } else if (ObjUtil.isNotEmpty(spaceId)) {
            queryWrapper.apply("spaceId = {0}", spaceId);
        }

        // JSON 数组查询
        if (CollUtil.isNotEmpty(tags)) {
            for (String tag : tags) {
                queryWrapper.like("tags", "\"" + tag + "\"");
            }
        }
        // 排序
        queryWrapper.orderBy(StrUtil.isNotEmpty(sortField), sortOrder.equals("ascend"), sortField);
        return queryWrapper;
    }

    @Override
    public void validPicture(Picture picture) {
        ThrowUtils.throwIf(picture == null, ErrorCode.PARAMS_ERROR);
        // 从对象中取值
        Long id = picture.getId();
        String url = picture.getUrl();
        String introduction = picture.getIntroduction();
        // 修改数据时，id 不能为空，有参数则校验
        ThrowUtils.throwIf(ObjUtil.isNull(id), ErrorCode.PARAMS_ERROR, "id 不能为空");
        if (StrUtil.isNotBlank(url)) {
            ThrowUtils.throwIf(url.length() > 1024, ErrorCode.PARAMS_ERROR, "url 过长");
        }
        if (StrUtil.isNotBlank(introduction)) {
            ThrowUtils.throwIf(introduction.length() > 800, ErrorCode.PARAMS_ERROR, "简介过长");
        }
    }

    @Override
    public void doPictureReview(PictureReviewRequest pictureReviewRequest, User loginUser) {
        Long id = pictureReviewRequest.getId();
        Integer reviewStatus = pictureReviewRequest.getReviewStatus();
        PictureReviewStatusEnum reviewStatusEnum = PictureReviewStatusEnum.getEnumByValue(reviewStatus);
        if (id == null || reviewStatusEnum == null || PictureReviewStatusEnum.REVIEWING.equals(reviewStatusEnum)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // 判断是否存在
        Picture oldPicture = this.getById(id);
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR);
        // 已是该状态
        if (oldPicture.getReviewStatus().equals(reviewStatus)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请勿重复审核");
        }
        // 更新审核状态
        Picture updatePicture = new Picture();
        BeanUtils.copyProperties(pictureReviewRequest, updatePicture);
        updatePicture.setReviewerId(loginUser.getId());
        updatePicture.setReviewTime(new Date());
        boolean result = this.updateById(updatePicture);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
    }

    @Override
    public void fillReviewParams(Picture picture, User loginUser) {
        if (userService.isAdmin(loginUser)) {
            // 管理员自动过审
            picture.setReviewStatus(PictureReviewStatusEnum.PASS.getValue());
            picture.setReviewerId(loginUser.getId());
            picture.setReviewMessage("管理员自动过审");
            picture.setReviewTime(new Date());
        } else {
            // 非管理员，创建或编辑都要改为待审核
            picture.setReviewStatus(PictureReviewStatusEnum.REVIEWING.getValue());
        }
    }


    @Override
    public Integer uploadPictureByBatch(PictureUploadByBatchRequest pictureUploadByBatchRequest, User loginUser) {
        String searchText = pictureUploadByBatchRequest.getSearchText();
        // 格式化数量
        Integer count = pictureUploadByBatchRequest.getCount();
        ThrowUtils.throwIf(count > 30, ErrorCode.PARAMS_ERROR, "最多 30 条");
        // 要抓取的地址
        String fetchUrl = String.format("https://cn.bing.com/images/async?q=%s&mmasync=1", searchText);
        Document document;
        try {
            document = Jsoup.connect(fetchUrl).get();
        } catch (IOException e) {
            log.error("获取页面失败", e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "获取页面失败");
        }
        Element div = document.getElementsByClass("dgControl").first();
        if (ObjUtil.isNull(div)) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "获取元素失败");
        }
        Elements imgElementList = div.select("img.mimg");
        int uploadCount = 0;
        for (Element imgElement : imgElementList) {
            String fileUrl = imgElement.attr("src");
            if (StrUtil.isBlank(fileUrl)) {
                log.info("当前链接为空，已跳过: {}", fileUrl);
                continue;
            }
            // 处理图片上传地址，防止出现转义问题
            int questionMarkIndex = fileUrl.indexOf("?");
            if (questionMarkIndex > -1) {
                fileUrl = fileUrl.substring(0, questionMarkIndex);
            }


            // 先设置搜索关键词（关键！）----------抓取的图片就会有关键词，而不是OIP-C（为什么是OIP-C？是百度抓取图片的url后缀）
            urlPictureUpload.setSearchText(pictureUploadByBatchRequest.getSearchText());


            // 上传图片
            PictureUploadRequest pictureUploadRequest = new PictureUploadRequest();
            try {
                PictureVO pictureVO = this.uploadPicture(fileUrl, pictureUploadRequest, loginUser);
                log.info("图片上传成功, id = {}", pictureVO.getId());
                uploadCount++;
            } catch (Exception e) {
                log.error("图片上传失败", e);
                continue;
            }
            if (uploadCount >= count) {
                break;
            }
        }
        return uploadCount;
    }

    //可被异步调用
    @Async
    @Override
    public void clearPictureFile(Picture oldPicture) {
        // 判断该图片是否被多条记录使用
        String pictureUrl = oldPicture.getUrl();
        long count = this.lambdaQuery()
                .eq(Picture::getUrl, pictureUrl)
                .count();
        // 有不止一条记录用到了该图片，不清理
        if (count > 1) {
            return;
        }
        // FIXME 注意，这里的 url 包含了域名，实际上只要传 key 值（存储路径）就够了
        cosManager.deleteObject(oldPicture.getUrl());
        // 清理缩略图
        String thumbnailUrl = oldPicture.getThumbnailUrl();
        if (StrUtil.isNotBlank(thumbnailUrl)) {
            cosManager.deleteObject(thumbnailUrl);
        }
    }

    /* /**
     * 用于判断删除空间图片时，是否是空间管理者（创建人）
     * 即便是管理员也无法随意删除他人的私人空间
     * @param loginUser
     * @param picture
     */
   /* @Override
    public void checkPictureAuth(User loginUser, Picture picture) {
        Long spaceId = picture.getSpaceId();
        if (spaceId == null) {
            // 公共图库，仅本人或管理员可操作
            if (!picture.getUserId().equals(loginUser.getId()) && !userService.isAdmin(loginUser)) {
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
            }
        } else {
            // 私有空间，仅空间管理员可操作
            if (!picture.getUserId().equals(loginUser.getId())) {
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
            }
        }
    }*/

    @Override
    public void deletePicture(long pictureId, User loginUser) {
        ThrowUtils.throwIf(pictureId <= 0, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR);
        // 判断是否存在
        Picture oldPicture = this.getById(pictureId);
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR);
        // 校验权限
        // checkPictureAuth(loginUser, oldPicture);
// 开启事务
        transactionTemplate.execute(status -> {
            // 操作数据库
            boolean result = this.removeById(pictureId);
            ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
            // 释放额度
            Long spaceId = oldPicture.getSpaceId();
            if (spaceId != null && spaceId > 0) {
                boolean update = spaceService.lambdaUpdate()
                        .eq(Space::getId, spaceId)
                        .setSql("totalSize = totalSize - " + oldPicture.getPicSize())
                        .setSql("totalCount = totalCount - 1")
                        .update();
                ThrowUtils.throwIf(!update, ErrorCode.OPERATION_ERROR, "额度更新失败");
            }
            return true;
        });
// 异步清理文件
        this.clearPictureFile(oldPicture);

    }

    @Override
    public void editPicture(PictureEditRequest pictureEditRequest, User loginUser) {
        // 在此处将实体类和 DTO 进行转换
        Picture picture = new Picture();
        BeanUtils.copyProperties(pictureEditRequest, picture);
        // 注意将 list 转为 string
        picture.setTags(JSONUtil.toJsonStr(pictureEditRequest.getTags()));
        // 设置编辑时间
        picture.setEditTime(new Date());
        // 数据校验
        this.validPicture(picture);
        // 判断是否存在
        long id = pictureEditRequest.getId();
        Picture oldPicture = this.getById(id);
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR);
        // 校验权限
        //checkPictureAuth(loginUser, oldPicture);
        // 补充审核参数
        this.fillReviewParams(picture, loginUser);
        // 操作数据库
        boolean result = this.updateById(picture);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
    }

    @Override
    public List<PictureVO> searchPictureByColor(Long spaceId, String picColor, User loginUser) {
        // 1. 校验参数
        ThrowUtils.throwIf(spaceId == null || StrUtil.isBlank(picColor), ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR);
        // 2. 校验空间权限
        Space space = spaceService.getById(spaceId);
        ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
        if (!loginUser.getId().equals(space.getUserId()) && !userService.isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "没有空间访问权限");
        }
        // 3. 查询该空间下所有图片（必须有主色调）
        List<Picture> pictureList = this.lambdaQuery()
                .eq(Picture::getSpaceId, spaceId)
                .isNotNull(Picture::getPicColor)
                .list();
        // 如果没有图片，直接返回空列表
        if (CollUtil.isEmpty(pictureList)) {
            return Collections.emptyList();
        }
        // 将目标颜色转为 Color 对象
        Color targetColor = Color.decode(picColor);
        // 4. 计算相似度并排序
        List<Picture> sortedPictures = pictureList.stream()
                .sorted(Comparator.comparingDouble(picture -> {
                    // 提取图片主色调
                    String hexColor = picture.getPicColor();
                    // 没有主色调的图片放到最后
                    if (StrUtil.isBlank(hexColor)) {
                        return Double.MAX_VALUE;
                    }
                    Color pictureColor = Color.decode(hexColor);
                    // 越大越相似
                    /**
                     * Java 默认是 从小到大排序（升序）
                     * 相似度 越高，数值越大，我们希望它 排在前面
                     */
                    return -ColorSimilarUtils.calculateSimilarity(targetColor, pictureColor);
                }))
                // 取前 12 个
                .limit(12)
                .collect(Collectors.toList());

        // 转换为 PictureVO
        return sortedPictures.stream()
                .map(PictureVO::objToVo)
                .collect(Collectors.toList());
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void editPictureByBatch(PictureEditByBatchRequest pictureEditByBatchRequest, User loginUser) {

        List<Long> pictureIdList = pictureEditByBatchRequest.getPictureIdList();
        String category = pictureEditByBatchRequest.getCategory();
        Long spaceId = pictureEditByBatchRequest.getSpaceId();
        List<String> tags = pictureEditByBatchRequest.getTags();
        Long userId = loginUser.getId();

        // 批量重命名
        String nameRule = pictureEditByBatchRequest.getNameRule();

        //数据校验
        ThrowUtils.throwIf(spaceId == null || CollUtil.isEmpty(pictureIdList), ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR);
        //权限校验,通过空间
        Space space = spaceService.getById(spaceId);
        ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR,"空间不存在");
        ThrowUtils.throwIf(!userId.equals(space.getUserId()), ErrorCode.NO_AUTH_ERROR,"没有访问权限");

        //指定图片查询，只返回需要的字段
        /**
         * select(Picture::getId, Picture::getSpaceId)：只查 ID 和空间 ID，不查全字段 → 性能更高
         * eq(Picture::getSpaceId, spaceId)：限定是这个空间下的图片
         * in(Picture::getId, pictureIdList)：只查前端传的那些图片 ID
         * .list()：返回符合条件的图片列表
         */
        List<Picture> pictureList = this.lambdaQuery()
                .select(Picture::getId,Picture::getSpaceId)
                .eq(Picture::getSpaceId,spaceId)
                .in(Picture::getId,pictureIdList)
                .list();

        if (CollUtil.isEmpty(pictureList)) {
            return;
        }

        // 4. 更新分类和标签
        pictureList.forEach(picture -> {
            if (StrUtil.isNotBlank(category)) {
                picture.setCategory(category);
            }
            if (CollUtil.isNotEmpty(tags)) {
                picture.setTags(JSONUtil.toJsonStr(tags));
            }
        });

        fillPictureWithNameRule(pictureList, nameRule);
        // 5. 批量更新
        boolean result = this.updateBatchById(pictureList);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
    }

    /**
     * nameRule 格式：图片{序号}
     *
     * @param pictureList 要重命名的图片列表
     * @param nameRule 命名规则
     */
    private void fillPictureWithNameRule(List<Picture> pictureList, String nameRule) {
        // 空值直接返回
        if (CollUtil.isEmpty(pictureList) || StrUtil.isBlank(nameRule)) {
            return;
        }

        // 序号从 1 开始
        long count = 1;

        try {
            // 遍历所有图片
            for (Picture picture : pictureList) {
                // 把 {序号} 替换成数字：1、2、3...
                /**找到 nameRule 里的 {序号} 这段文字，把它替换掉(所以传入的nameRule必定带有{序号}这一字段)
                 *
                 * String.valueOf(count++)
                 * 就是 数字：1、2、3、4……
                 */
                String pictureName = nameRule.replaceAll("\\{序号}", String.valueOf(count++));
                // 给图片设置新名字
                picture.setName(pictureName);
            }
        } catch (Exception e) {
            log.error("名称解析错误", e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "名称解析错误");
        }
    }



    /**
     * 批量编辑图片分类和标签

     @Transactional(rollbackFor = Exception.class)
     @Override
     public void batchEditPictureMetadata(PictureBatchEditRequest request, Long spaceId, Long loginUserId) {
     // 参数校验
     validateBatchEditRequest(request, spaceId, loginUserId);

     // 查询空间下的图片
     List<Picture> pictureList = this.lambdaQuery()
     .eq(Picture::getSpaceId, spaceId)
     .in(Picture::getId, request.getPictureIds())
     .list();

     if (pictureList.isEmpty()) {
     throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "指定的图片不存在或不属于该空间");
     }

     // 分批处理避免长事务
     int batchSize = 100;
     List<CompletableFuture<Void>> futures = new ArrayList<>();
     for (int i = 0; i < pictureList.size(); i += batchSize) {
     List<Picture> batch = pictureList.subList(i, Math.min(i + batchSize, pictureList.size()));

     // 异步处理每批数据
     CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
     batch.forEach(picture -> {
     // 编辑分类和标签
     if (request.getCategory() != null) {
     picture.setCategory(request.getCategory());
     }
     if (request.getTags() != null) {
     picture.setTags(String.join(",", request.getTags()));
     }
     });
     boolean result = this.updateBatchById(batch);
     if (!result) {
     throw new BusinessException(ErrorCode.OPERATION_ERROR, "批量更新图片失败");
     }
     }, customExecutor);

     futures.add(future);
     }

     // 等待所有任务完成
     CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
     }
     */
    @Override
    public CreateOutPaintingTaskResponse createPictureOutPaintingTask(CreatePictureOutPaintingTaskRequest createPictureOutPaintingTaskRequest, User loginUser) {
        // 获取图片信息
        Long pictureId = createPictureOutPaintingTaskRequest.getPictureId();
        Picture picture = Optional.ofNullable(this.getById(pictureId))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_ERROR, "图片不存在"));
        // 校验权限，已经改为使用注解鉴权
//        checkPictureAuth(loginUser, picture);
        // 创建扩图任务
        CreateOutPaintingTaskRequest createOutPaintingTaskRequest = new CreateOutPaintingTaskRequest();
        CreateOutPaintingTaskRequest.Input input = new CreateOutPaintingTaskRequest.Input();
        input.setImageUrl(picture.getUrl());
        createOutPaintingTaskRequest.setInput(input);
        createOutPaintingTaskRequest.setParameters(createPictureOutPaintingTaskRequest.getParameters());
        // 创建任务
        return aliYunAiApi.createOutPaintingTask(createOutPaintingTaskRequest);
    }








}




