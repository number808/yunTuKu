package com.yuntuku.yunbackend.manager.upload;

import cn.hutool.core.collection.CollUtil;
import com.qcloud.cos.model.ciModel.persistence.CIObject;
import com.qcloud.cos.model.ciModel.persistence.ProcessResults;
import com.yuntuku.yunbackend.config.CosClientConfig;
import com.yuntuku.yunbackend.exception.BusinessException;
import com.yuntuku.yunbackend.exception.ErrorCode;

import com.yuntuku.yunbackend.manager.CosManager;
import com.yuntuku.yunbackend.model.dto.file.UploadPictureResult;
import lombok.extern.slf4j.Slf4j;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.RandomUtil;

import com.qcloud.cos.model.ciModel.persistence.ImageInfo;


import javax.annotation.Resource;
import java.io.File;

import java.util.Date;
import java.util.List;


import com.qcloud.cos.model.PutObjectResult;

/**--------------面--------------向---------模-------块----------------开-发-------------------------------------------------------------------------
 * 父类！！！
 * 在子类中定义Url和文件的Upload判断
 * 子类中有相同名称的方法，根据选择的子类来使用不同的方法完成
 *
 */
@Slf4j
public abstract class PictureUploadTemplate {

        @Resource
        protected CosManager cosManager;

        @Resource
        protected CosClientConfig cosClientConfig;

         /**
             * 图片关键词（用于批量抓取时命名）
          * */
         String searchText;

        public void setSearchText(String searchText) {
        this.searchText = searchText;
        }

        /**
         * 模板方法，定义上传流程
         */
        public final UploadPictureResult uploadPicture(Object inputSource, String uploadPathPrefix) {
            // 1. 校验图片
            /**
             * 调用方法，校验图片
             */
            validPicture(inputSource);

            // 2. 图片上传地址
            String uuid = RandomUtil.randomString(16);
            /**
             * 调用子类方法
             * 获取文件名
             */
            String originFilename = getOriginFilename(inputSource);
            String uploadFilename = String.format("%s_%s.%s", DateUtil.formatDate(new Date()), uuid,
                    FileUtil.getSuffix(originFilename));
            String uploadPath = String.format("/%s/%s", uploadPathPrefix, uploadFilename);

            File file = null;
            try {
                // 创建临时文件
                file = File.createTempFile(uploadPath, null);
                // 处理文件来源（本地或 URL）
                processFile(inputSource, file);
                // 上传图片到对象存储
                PutObjectResult putObjectResult = cosManager.putPictureObject(uploadPath, file);
                ImageInfo imageInfo = putObjectResult.getCiUploadResult().getOriginalInfo().getImageInfo();
                /**
                 * putObjectResult：上传完返回的总结果（CosManager）
                 * getCiUploadResult()：拿到云处理（CI） 的结果
                 * getProcessResults()：拿到你刚才配置的转 WebP 处理的最终结果
                 */
                ProcessResults processResults = putObjectResult.getCiUploadResult().getProcessResults();
                /**
                 * processResults：处理结果
                 * getObjectList()：获取处理后生成的文件列表
                 * 所以objectList装着生成的 webp 文件信息（文件名、路径、大小、ETag 等）
                 */
                List<CIObject> objectList = processResults.getObjectList();
                /**
                * 先判断有没有生成压缩图
                * 有 → 用第 0 个（webp）
                * 没有 → 降级返回原图
                */
                if (CollUtil.isNotEmpty(objectList)) {
                    /**
                     * 这里只拿第一个元素，是因为Object里只有一张图片
                     * 因为在一开始我们为putObjectResult提供的RULE就只有一个，有多少个规则，就会生成多少个图片
                     *
                     */
                    //这里第一个拿压缩图片
                    CIObject compressedCiObject = objectList.get(0);
                    //第二个拿缩略图
                    CIObject thumbnailCiObject = compressedCiObject;
                    if (objectList.size() > 1) {
                        thumbnailCiObject = objectList.get(1);
                    }
                    // 封装压缩图返回结果
                    return buildResult(originFilename, compressedCiObject,thumbnailCiObject,imageInfo);
                }
                // 封装原图返回结果
                return buildResult(originFilename, file, uploadPath, imageInfo);
            } catch (Exception e) {
                log.error("图片上传到对象存储失败", e);
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "上传失败");
            }
            finally {
                // 6. 清理临时文件
                deleteTempFile(file);
            }
        }

        /**
         * 校验输入源（本地文件或 URL）
         */
        protected abstract void validPicture(Object inputSource);

        /**
         * 获取输入源的原始文件名
         */
        protected abstract String getOriginFilename(Object inputSource);

        /**
         * 处理输入源并生成本地临时文件
         */
        protected abstract void processFile(Object inputSource, File file) throws Exception;

    /**用于封装处理过的图片
     * 新的封装返回结果
     */
    private UploadPictureResult buildResult(String originFilename, CIObject compressedCiObject,CIObject thumbnailCiObject, ImageInfo imageInfo) {
        UploadPictureResult uploadPictureResult = new UploadPictureResult();
        int picWidth = compressedCiObject.getWidth();
        int picHeight = compressedCiObject.getHeight();
        double picScale = NumberUtil.round(picWidth * 1.0 / picHeight, 2).doubleValue();
        uploadPictureResult.setPicName(FileUtil.mainName(originFilename));
        uploadPictureResult.setPicWidth(picWidth);
        uploadPictureResult.setPicHeight(picHeight);
        uploadPictureResult.setPicScale(picScale);
        uploadPictureResult.setPicFormat(compressedCiObject.getFormat());
        uploadPictureResult.setPicSize(compressedCiObject.getSize().longValue());
        uploadPictureResult.setPicColor(imageInfo.getAve());

        // 设置图片为压缩后的地址
        uploadPictureResult.setUrl(cosClientConfig.getHost() + "/" + compressedCiObject.getKey());
        //缩略图
        uploadPictureResult.setThumbnailUrl(cosClientConfig.getHost() + "/" + thumbnailCiObject.getKey());
        return uploadPictureResult;
    }

    /**
         * 封装返回结果
         */
        private UploadPictureResult buildResult(String originFilename, File file, String uploadPath, ImageInfo imageInfo) {
            UploadPictureResult uploadPictureResult = new UploadPictureResult();
            int picWidth = imageInfo.getWidth();
            int picHeight = imageInfo.getHeight();
            double picScale = NumberUtil.round(picWidth * 1.0 / picHeight, 2).doubleValue();
            uploadPictureResult.setPicName(FileUtil.mainName(originFilename));
            uploadPictureResult.setPicWidth(picWidth);
            uploadPictureResult.setPicHeight(picHeight);
            uploadPictureResult.setPicScale(picScale);
            uploadPictureResult.setPicFormat(imageInfo.getFormat());
            uploadPictureResult.setPicSize(FileUtil.size(file));
            uploadPictureResult.setUrl(cosClientConfig.getHost() + "/" + uploadPath);
            uploadPictureResult.setPicColor(imageInfo.getAve());
            return uploadPictureResult;
        }

        /**
         * 删除临时文件
         */
        public void deleteTempFile(File file) {
            if (file == null) {
                return;
            }
            boolean deleteResult = file.delete();
            if (!deleteResult) {
                log.error("file delete error, filepath = {}", file.getAbsolutePath());
            }
        }
    }





