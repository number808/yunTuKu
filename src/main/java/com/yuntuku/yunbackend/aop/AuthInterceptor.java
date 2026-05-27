package com.yuntuku.yunbackend.aop;
import com.yuntuku.yunbackend.annotation.AuthCheck;
import com.yuntuku.yunbackend.mapper.UserMapper;
import com.yuntuku.yunbackend.model.entity.User;
import com.yuntuku.yunbackend.model.enums.UserRoleEnums;
import com.yuntuku.yunbackend.service.UserService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

/**
 * 这是一个拦截器，在Controller层内，使用@AuthCheck（输入必须要的用户Role），该接口被使用时就会自动被拦截，判断Role，如果正确才放行
 * 和全局拦截器其实一个样
 */
@Aspect
@Component
public class AuthInterceptor {

    /**
     * 首先拿到authCheck的身份类型
     * 在前端发的Request中找到用户User信息
     * 在用户信息中找到UserRole
     *
     * 通过value（userRole），也就是Admin/User来从userRoleEnums这个枚举类来查找value
     * 从value是否不同判断身份
     */
    @Resource
    private UserService userService;

    @Around("@annotation(authCheck)")
    public Object doInterceptor(ProceedingJoinPoint joinPoint, AuthCheck authCheck) throws Throwable {
        String mustRole = authCheck.mustRole();
        RequestAttributes requestAttributes = RequestContextHolder.currentRequestAttributes();
        HttpServletRequest request = ((ServletRequestAttributes) requestAttributes).getRequest();
        // 当前登录用户
        User loginUser = userService.getLoginUser(request);
        UserRoleEnums mustRoleEnum = UserRoleEnums.findByValue(mustRole);
        // 不需要权限，放行
        if (mustRoleEnum == null) {
            return joinPoint.proceed();
        }

        // 必须有权限，才会执行以下
        UserRoleEnums userRoleEnum = UserRoleEnums.findByValue(loginUser.getUserRole());
        if (userRoleEnum == null) {
            throw new RuntimeException("用户没有权限");
        }
        // 要求管理员权限，并且用户不是管理员
        if (UserRoleEnums.ADMIN.equals(mustRoleEnum) &&!UserRoleEnums.ADMIN.equals(userRoleEnum) ) {
            throw new RuntimeException("用户没有权限");
        }
        return joinPoint.proceed();
    }
}