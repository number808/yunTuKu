package com.yuntuku.yunbackend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.yuntuku.yunbackend.api.aliyunai.model.CreateOutPaintingTaskResponse;
import com.yuntuku.yunbackend.model.dto.picture.*;
import com.yuntuku.yunbackend.model.entity.Picture;
import com.yuntuku.yunbackend.model.entity.User;
import com.yuntuku.yunbackend.model.vo.PictureVO;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.annotation.Transactional;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
* @author PUFF
* @description 针对表【picture(图片)】的数据库操作Service
* @createDate 2026-03-24 16:53:37
*/
public interface PictureService extends IService<Picture> {

    PictureVO getPictureVO(Picture picture, HttpServletRequest request);

    Page<PictureVO> getPictureVOPage(Page<Picture> picturePage, HttpServletRequest request);


    // 上传图片
    PictureVO uploadPicture(Object inputSource, PictureUploadRequest pictureUploadRequest, User loginUser);

    QueryWrapper<Picture> getQueryWrapper(PictureQueryRequest pictureQueryRequest);

    void validPicture(Picture picture);
    /**
     * 图片审核
     */
    void doPictureReview(PictureReviewRequest pictureReviewRequest, User loginUser);

    void fillReviewParams(Picture picture, User loginUser);


    Integer uploadPictureByBatch(PictureUploadByBatchRequest pictureUploadByBatchRequest, User loginUser);

    @Async
    void clearPictureFile(Picture oldPicture);

   // void checkPictureAuth(User loginUser, Picture picture);

    void deletePicture(long pictureId, User loginUser);

    void editPicture(PictureEditRequest pictureEditRequest, User loginUser);

    List<PictureVO> searchPictureByColor(Long spaceId, String picColor, User loginUser);

    @Transactional(rollbackFor = Exception.class)
    void editPictureByBatch(PictureEditByBatchRequest pictureEditByBatchRequest, User loginUser);
/*
    @Transactional(rollbackFor = Exception.class)
    void batchEditPictureMetadata(PictureBatchEditRequest request, Long spaceId, Long loginUserId);*/

    CreateOutPaintingTaskResponse createPictureOutPaintingTask(CreatePictureOutPaintingTaskRequest createPictureOutPaintingTaskRequest, User loginUser);
}
