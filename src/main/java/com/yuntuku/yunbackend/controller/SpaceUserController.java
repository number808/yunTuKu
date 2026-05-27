package com.yuntuku.yunbackend.controller;

import cn.hutool.core.util.ObjectUtil;
import com.yuntuku.yunbackend.manager.auth.annotation.SaSpaceCheckPermission;
import com.yuntuku.yunbackend.manager.auth.model.SpaceUserPermissionConstant;
import com.yuntuku.yunbackend.model.dto.spaceuser.SpaceUserAddRequest;
import com.yuntuku.yunbackend.model.dto.spaceuser.SpaceUserEditRequest;
import com.yuntuku.yunbackend.model.dto.spaceuser.SpaceUserQueryRequest;
import com.yuntuku.yunbackend.model.entity.SpaceUser;
import com.yuntuku.yunbackend.model.vo.SpaceUserVO;
import com.yuntuku.yunbackend.service.SpaceService;
import com.yuntuku.yunbackend.service.SpaceUserService;
import com.yuntuku.yunbackend.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.ObjUtil;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yuntuku.yunbackend.annotation.AuthCheck;
import com.yuntuku.yunbackend.common.BaseResponse;
import com.yuntuku.yunbackend.common.DeleteRequest;
import com.yuntuku.yunbackend.common.ResultUtils;
import com.yuntuku.yunbackend.constant.UserConstant;
import com.yuntuku.yunbackend.exception.BusinessException;
import com.yuntuku.yunbackend.exception.ErrorCode;
import com.yuntuku.yunbackend.exception.ThrowUtils;
import com.yuntuku.yunbackend.model.dto.user.*;
import com.yuntuku.yunbackend.model.entity.User;
import com.yuntuku.yunbackend.model.vo.LoginUserVO;
import com.yuntuku.yunbackend.model.vo.UserVO;
import com.yuntuku.yunbackend.service.UserService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.List;

import javax.annotation.Resource;

@RestController
    @RequestMapping("/spaceUser")
    @Slf4j
    public class SpaceUserController {

        @Resource
        private SpaceUserService spaceUserService;

        @Resource
        private UserService userService;

        @Resource
        private SpaceService spaceService;

        /**
         * 添加成员到空间
         */
        @PostMapping("/add")
        @SaSpaceCheckPermission(value = SpaceUserPermissionConstant.SPACE_USER_MANAGE)
        public BaseResponse<Long> addSpaceUser(@RequestBody SpaceUserAddRequest spaceUserAddRequest, HttpServletRequest request) {
            ThrowUtils.throwIf(spaceUserAddRequest == null, ErrorCode.PARAMS_ERROR);
            long id = spaceUserService.addSpaceUser(spaceUserAddRequest);
            return ResultUtils.success(id);
        }

        /**
         * 从空间移除成员
         * (只有空间管理员有权限)
         */
        @PostMapping("/delete")
        @SaSpaceCheckPermission(value = SpaceUserPermissionConstant.SPACE_USER_MANAGE)
        public BaseResponse<Boolean> deleteSpaceUser(@RequestBody DeleteRequest deleteRequest,
                                                     HttpServletRequest request) {
            if (deleteRequest == null || deleteRequest.getId() <= 0) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR);
            }
            long id = deleteRequest.getId();
            User user = userService.getLoginUser(request);
            // 判断是否存在
            SpaceUser oldSpaceUser = spaceUserService.getById(id);
            ThrowUtils.throwIf(oldSpaceUser == null, ErrorCode.NOT_FOUND_ERROR);
            /**
             * 如果空间管理员把自己删了，默认删除整个空间
             */
            if (id == user.getId()) {
                //管理员把自己删了
                boolean result = spaceUserService.removeById(id);
                boolean result2 = spaceService.removeById(id);
                ThrowUtils.throwIf(!result &&!result2, ErrorCode.OPERATION_ERROR);
                return ResultUtils.success(true);

            }

            /**
             * 只是单纯删一个用户
             */
            // 操作数据库
            boolean result = spaceUserService.removeById(id);

            ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
            return ResultUtils.success(true);
        }

        /**
         * 查询某个成员在某个空间的信息
         */
        @PostMapping("/get")
        @SaSpaceCheckPermission(value = SpaceUserPermissionConstant.SPACE_USER_MANAGE)
        public BaseResponse<SpaceUser> getSpaceUser(@RequestBody SpaceUserQueryRequest spaceUserQueryRequest) {
            // 参数校验
            ThrowUtils.throwIf(spaceUserQueryRequest == null, ErrorCode.PARAMS_ERROR);
            Long spaceId = spaceUserQueryRequest.getSpaceId();
            Long userId = spaceUserQueryRequest.getUserId();
            ThrowUtils.throwIf(ObjectUtil.hasEmpty(spaceId, userId), ErrorCode.PARAMS_ERROR);
            // 查询数据库
            SpaceUser spaceUser = spaceUserService.getOne(spaceUserService.getQueryWrapper(spaceUserQueryRequest));
            ThrowUtils.throwIf(spaceUser == null, ErrorCode.NOT_FOUND_ERROR);
            return ResultUtils.success(spaceUser);
        }

        /**
         * 查询成员信息列表
         */
        @PostMapping("/list")
        @SaSpaceCheckPermission(value = SpaceUserPermissionConstant.SPACE_USER_MANAGE)
        public BaseResponse<List<SpaceUserVO>> listSpaceUser(@RequestBody SpaceUserQueryRequest spaceUserQueryRequest,
                                                             HttpServletRequest request) {
            ThrowUtils.throwIf(spaceUserQueryRequest == null, ErrorCode.PARAMS_ERROR);
            List<SpaceUser> spaceUserList = spaceUserService.list(
                    spaceUserService.getQueryWrapper(spaceUserQueryRequest)
            );
            return ResultUtils.success(spaceUserService.getSpaceUserVOList(spaceUserList));
        }

        /**
         * 编辑成员信息（设置权限）
         */
        @PostMapping("/edit")
        @SaSpaceCheckPermission(value = SpaceUserPermissionConstant.SPACE_USER_MANAGE)
        public BaseResponse<Boolean> editSpaceUser(@RequestBody SpaceUserEditRequest spaceUserEditRequest,
                                                   HttpServletRequest request) {
            if (spaceUserEditRequest == null || spaceUserEditRequest.getId() <= 0) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR);
            }
            // 将实体类和 DTO 进行转换
            SpaceUser spaceUser = new SpaceUser();
            BeanUtils.copyProperties(spaceUserEditRequest, spaceUser);
            // 数据校验
            spaceUserService.validSpaceUser(spaceUser, false);
            // 判断是否存在
            long id = spaceUserEditRequest.getId();
            SpaceUser oldSpaceUser = spaceUserService.getById(id);
            ThrowUtils.throwIf(oldSpaceUser == null, ErrorCode.NOT_FOUND_ERROR);
            // 操作数据库
            boolean result = spaceUserService.updateById(spaceUser);
            ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
            return ResultUtils.success(true);
        }

        /**
         * 查询我加入的团队空间列表
         */
        @PostMapping("/list/my")
        public BaseResponse<List<SpaceUserVO>> listMyTeamSpace(HttpServletRequest request) {
            User loginUser = userService.getLoginUser(request);
            SpaceUserQueryRequest spaceUserQueryRequest = new SpaceUserQueryRequest();
            spaceUserQueryRequest.setUserId(loginUser.getId());
            List<SpaceUser> spaceUserList = spaceUserService.list(
                    spaceUserService.getQueryWrapper(spaceUserQueryRequest)
            );
            return ResultUtils.success(spaceUserService.getSpaceUserVOList(spaceUserList));
        }
    }

