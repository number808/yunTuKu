package com.yuntuku.yunbackend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.IService;
import com.yuntuku.yunbackend.model.dto.spaceuser.SpaceUserAddRequest;
import com.yuntuku.yunbackend.model.dto.spaceuser.SpaceUserQueryRequest;
import com.yuntuku.yunbackend.model.entity.SpaceUser ;
import com.yuntuku.yunbackend.model.vo.SpaceUserVO;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
* @author PUFF
* @description 针对表【space_user(空间用户关联)】的数据库操作Service
* @createDate 2026-04-09 11:55:04
*/
public interface SpaceUserService extends IService<SpaceUser> {

    long addSpaceUser(SpaceUserAddRequest spaceUserAddRequest);

    void validSpaceUser(SpaceUser spaceUser, boolean add);

    QueryWrapper<SpaceUser> getQueryWrapper(SpaceUserQueryRequest spaceUserQueryRequest);

    SpaceUserVO getSpaceUserVO(SpaceUser spaceUser, HttpServletRequest request);

    List<SpaceUserVO> getSpaceUserVOList(List<SpaceUser> spaceUserList);
}
