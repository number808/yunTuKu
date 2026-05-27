package com.yuntuku.yunbackend.controller;

import com.yuntuku.yunbackend.annotation.AuthCheck;
import com.yuntuku.yunbackend.common.BaseResponse;
import com.yuntuku.yunbackend.common.DeleteRequest;
import com.yuntuku.yunbackend.common.ResultUtils;
import com.yuntuku.yunbackend.constant.UserConstant;
import com.yuntuku.yunbackend.exception.BusinessException;
import com.yuntuku.yunbackend.exception.ErrorCode;
import com.yuntuku.yunbackend.exception.ThrowUtils;
import com.yuntuku.yunbackend.manager.CosManager;
import com.yuntuku.yunbackend.model.dto.picture.PictureUploadRequest;
import com.yuntuku.yunbackend.model.entity.Picture;
import com.yuntuku.yunbackend.model.entity.User;
import com.yuntuku.yunbackend.model.vo.PictureVO;
import com.yuntuku.yunbackend.service.PictureService;
import com.yuntuku.yunbackend.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.io.File;



@RestController
@RequestMapping("/file")
@Slf4j
public class FileController {

@Resource
private CosManager cosManager;
@Resource
private UserService userService;
@Resource
private PictureService pictureService;
    /**
     * 测试文件上传
     *
     * @param multipartFile
     * @return
     */
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @PostMapping("/test/upload")
    public BaseResponse<String> testUploadFile(@RequestPart("file") MultipartFile multipartFile) {
        // 文件目录
        String filename = multipartFile.getOriginalFilename();
        String filepath = String.format("/test/%s", filename);
        File file = null;
        try {
            // 上传文件
            file = File.createTempFile(filepath, null);
            multipartFile.transferTo(file);
            cosManager.putObject(filepath, file);
            // 返回可访问地址
            return ResultUtils.success(filepath);
        } catch (Exception e) {
            //  把真实错误完整打出来
            log.error("==================== 上传真实异常 ====================", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "上传失败：" + e.getMessage());
        } finally {
            if (file != null) {
                // 删除临时文件
                boolean delete = file.delete();
                if (!delete) {
                    log.error("file delete error, filepath = {}", filepath);
                }
            }
        }
    }






}
