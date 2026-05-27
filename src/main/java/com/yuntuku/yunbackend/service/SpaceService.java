package com.yuntuku.yunbackend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.yuntuku.yunbackend.model.dto.space.SpaceAddRequest;
import com.yuntuku.yunbackend.model.dto.space.SpaceQueryRequest;
import com.yuntuku.yunbackend.model.entity.Space ;
import com.yuntuku.yunbackend.model.entity.User;
import com.yuntuku.yunbackend.model.vo.SpaceVO;

import javax.servlet.http.HttpServletRequest;

/**
* @author PUFF
* @description 针对表【space(空间)】的数据库操作Service
* @createDate 2026-04-01 15:09:16
*/
public interface SpaceService extends IService<Space> {

    void validSpace(Space space, boolean add);

    void fillSpaceBySpaceLevel(Space space);


    SpaceVO getSpaceVO(Space space, HttpServletRequest request);

    Page<SpaceVO> getSpaceVOPage(Page<Space> spacePage, HttpServletRequest request);

    QueryWrapper<Space> getQueryWrapper(SpaceQueryRequest spaceQueryRequest);

    long addSpace(SpaceAddRequest spaceAddRequest, User loginUser);

    void checkSpaceAuth(User loginUser, Space space);
}
