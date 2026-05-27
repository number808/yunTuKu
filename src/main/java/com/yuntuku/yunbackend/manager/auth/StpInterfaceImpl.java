package com.yuntuku.yunbackend.manager.auth;

import cn.dev33.satoken.stp.StpInterface;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.ReflectUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.extra.servlet.ServletUtil;
import cn.hutool.http.ContentType;
import cn.hutool.http.Header;
import cn.hutool.json.JSONUtil;
import com.yuntuku.yunbackend.constant.SpaceConstant;
import com.yuntuku.yunbackend.exception.BusinessException;
import com.yuntuku.yunbackend.exception.ErrorCode;
import com.yuntuku.yunbackend.exception.ThrowUtils;
import com.yuntuku.yunbackend.manager.auth.model.SpaceUserPermissionConstant;
import com.yuntuku.yunbackend.model.entity.Picture;
import com.yuntuku.yunbackend.model.entity.Space;
import com.yuntuku.yunbackend.model.entity.SpaceUser;
import com.yuntuku.yunbackend.model.entity.User;
import com.yuntuku.yunbackend.model.enums.SpaceRoleEnum;
import com.yuntuku.yunbackend.model.enums.SpaceTypeEnum;
import com.yuntuku.yunbackend.service.PictureService;
import com.yuntuku.yunbackend.service.SpaceService;
import com.yuntuku.yunbackend.service.SpaceUserService;
import com.yuntuku.yunbackend.service.UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.*;

import static com.yuntuku.yunbackend.constant.UserConstant.USER_LOGIN_STATE;

/**
 * 自定义权限加载接口实现类
 */
@Component    // 保证此类被 SpringBoot 扫描，完成 Sa-Token 的自定义权限验证扩展
public class StpInterfaceImpl implements StpInterface {

    @Resource
    private SpaceUserAuthManager spaceUserAuthManager;

    @Resource
    private SpaceService spaceService;
    @Resource
    private SpaceUserService spaceUserService;
    @Resource
    private UserService userService;
    @Resource
    private PictureService pictureService;
    /**
     * 返回一个账号所拥有的权限码集合 
     */

    private boolean isAllFieldsNull(Object object) {
        if (object == null) {
            return true; // 对象本身为空
        }
        // 获取所有字段并判断是否所有字段都为空
        return Arrays.stream(ReflectUtil.getFields(object.getClass()))
                // 获取字段值
                .map(field -> ReflectUtil.getFieldValue(object, field))
                // 检查是否所有字段都为空
                .allMatch(ObjectUtil::isEmpty);
    }

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        //判断loginType，只对space进行权限校验(对图片或者用户的功能校验，先前已经使用AOP切面来解决，所以我们只对space权限校验)
        if (!StpKit.SPACE_TYPE.equals(loginType)) {
            return new ArrayList<>();
        }
        //管理员权限，表示权限校验通过
        List<String> ADMIN_PERMISSIONS = spaceUserAuthManager.getPermissionsByRole(SpaceRoleEnum.ADMIN.getValue());
        //获取上下文信息
        SpaceUserAuthContext authContext = getAuthContextByRequest();

        //如果所有字段为空，则查询公共图库，直接通过
        if (isAllFieldsNull(authContext)) {
            return ADMIN_PERMISSIONS;
        }

        //获取userId
        User loginUser = (User) StpKit.SPACE.getSessionByLoginId(loginId).get(USER_LOGIN_STATE);
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR, "没有登录");
        Long userId = loginUser.getId();

        //先从上下文获取SpaceUser对象
        SpaceUser spaceUser = authContext.getSpaceUser();
        //如果存在，直接返回空间里存放的space权限
        if (spaceUser != null) {
            return spaceUserAuthManager.getPermissionsByRole(spaceUser.getSpaceRole());
        }
        //没有从authContext中取到spaceUser，
        // 如果有spaceUserId，那么就从当前spaceUserId和loginUserId中，查到SpaceUser
        //再从中返回权限
        Long spaceUserId = authContext.getSpaceUserId();
        if (spaceUserId != null) {
            spaceUser = spaceUserService.getById(spaceUserId);
            ThrowUtils.throwIf(spaceUser == null, ErrorCode.NOT_FOUND_ERROR, "空间信息无该用户");

        //取当前登录用户对应的spaceUser
        SpaceUser loginSpaceUser = spaceUserService.lambdaQuery().eq(SpaceUser::getSpaceId, spaceUserId).eq(SpaceUser::getUserId, userId).one();
        if (loginSpaceUser == null) {
            return new ArrayList<>();
        }
            /**
             * TODO
             * 提示：
             * 这里会导致管理员在私有空间没有权限，要再查一次库处理
             * 我觉得是是因为查数据库时.eq(SpaceUser::getSpaceId, spaceUserId).eq(SpaceUser::getUserId, userId).one();
             */
        return spaceUserAuthManager.getPermissionsByRole(loginSpaceUser.getSpaceRole());
        }
        //如果没有spaceUserId，尝试通过spaceId，或者pictureId获取spaceUser
        // 如果没有 spaceUserId，尝试通过 spaceId 或 pictureId 获取 Space 对象并处理
        Long spaceId = authContext.getSpaceId();
        if (spaceId == null) {
            // 如果没有 spaceId，通过 pictureId 获取 Picture 对象和 Space 对象
            Long pictureId = authContext.getPictureId();
            // 图片 id 也没有，则默认通过权限校验
            if (pictureId == null) {
                return ADMIN_PERMISSIONS;
            }
            Picture picture = pictureService.lambdaQuery()
                    .eq(Picture::getId, pictureId)
                    .select(Picture::getId, Picture::getSpaceId, Picture::getUserId)
                    .one();
            if (picture == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "未找到图片信息");
            }
            spaceId = picture.getSpaceId();
            // 公共图库，仅本人或管理员可操作
            if (SpaceConstant.isPublicPictureSpace(spaceId)) {
                if (picture.getUserId().equals(userId) || userService.isAdmin(loginUser)) {
                    return ADMIN_PERMISSIONS;
                } else {
                    // 不是自己的图片，仅可查看
                    return Collections.singletonList(SpaceUserPermissionConstant.PICTURE_VIEW);
                }
            }
        }
        if (SpaceConstant.isPublicPictureSpace(spaceId)) {
            return ADMIN_PERMISSIONS;
        }
        // 获取 Space 对象
        Space space = spaceService.getById(spaceId);
        if (space == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "未找到空间信息");
        }
        // 根据 Space 类型判断权限
        if (space.getSpaceType() == SpaceTypeEnum.PRIVATE.getValue()) {
            // 私有空间，仅本人或管理员有权限
            if (space.getUserId().equals(userId) || userService.isAdmin(loginUser)) {
                return ADMIN_PERMISSIONS;
            } else {
                return new ArrayList<>();
            }
        } else {
            // 团队空间，查询 SpaceUser 并获取角色和权限
            spaceUser = spaceUserService.lambdaQuery()
                    .eq(SpaceUser::getSpaceId, spaceId)
                    .eq(SpaceUser::getUserId, userId)
                    .one();
            if (spaceUser == null) {
                return new ArrayList<>();
            }
            return spaceUserAuthManager.getPermissionsByRole(spaceUser.getSpaceRole());
        }
    }



    /**
     * 返回一个账号所拥有的角色标识集合 (权限与角色可分开校验)
     */
    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        // 本 list 仅做模拟，实际项目中要根据具体业务逻辑来查询角色
        List<String> list = new ArrayList<String>();
        return list;
    }

    @Value("${server.servlet.context-path}")
    private String contextPath;

    /**
     * 从请求中获取上下文对象
     */
    private SpaceUserAuthContext getAuthContextByRequest() {
        /**
         * RequestAttributes是一个父类，包含端口调用
         * 定时任务
         * 非 Web 环境
         * 这里强转成ServletRequestAttributes，这个子类，告诉系统我们是Web环境，这样才可以使用ServletRequestAttributes子类带的getRequest
         */
        HttpServletRequest request=((ServletRequestAttributes)RequestContextHolder.currentRequestAttributes()).getRequest();
        String contentType = request.getHeader("content-type");

        SpaceUserAuthContext authRequest;
        // 兼容 get 和 post 操作
        //判断是不是JSON类型(POST类型)，如果不是则是Bean类型（GET类型如/api/space/789?id=789&spaceId=100）
       if(ContentType.JSON.getValue().equals(contentType)) {
            String body = ServletUtil.getBody(request);
            authRequest = JSONUtil.toBean(body, SpaceUserAuthContext.class);
        }else {
            Map<String,String> paramMap = ServletUtil.getParamMap(request);
            authRequest = BeanUtil.toBean(paramMap, SpaceUserAuthContext.class);
        }

        // 根据请求路径区分 id 字段的含义
        Long id = authRequest.getId();
        if (ObjUtil.isNotNull(id)) {
            String requestUri = request.getRequestURI();
            String partUri = requestUri.replace(contextPath + "/", "");
            String moduleName = StrUtil.subBefore(partUri, "/", false);
            switch (moduleName) {
                case "picture":
                    authRequest.setPictureId(id);
                    break;
                case "spaceUser":
                    authRequest.setSpaceUserId(id);
                    break;
                case "space":
                    authRequest.setSpaceId(id);
                    break;
                default:
            }
        }
        return authRequest;
    }



}
