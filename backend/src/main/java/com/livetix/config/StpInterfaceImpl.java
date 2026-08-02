package com.livetix.config;

import cn.dev33.satoken.stp.StpInterface;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.livetix.entity.AdminPermission;
import com.livetix.entity.AdminRolePermission;
import com.livetix.entity.User;
import com.livetix.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Sa-Token permission & role loader with DB-backed RBAC.
 *
 * 20 修复: 支持 admin / operator / finance / cs 四种角色
 * 21 修复: 权限列表缓存到 Redis（5分钟TTL），避免每次请求 4 次 DB
 */
@Component
@RequiredArgsConstructor
public class StpInterfaceImpl implements StpInterface {

    private final UserMapper userMapper;
    private final AdminRoleMapper adminRoleMapper;
    private final AdminRolePermissionMapper rolePermissionMapper;
    private final AdminPermissionMapper permissionMapper;
    private final RedisTemplate<String, Object> redisTemplate;

    /** 20: 所有受支持的后台管理角色 */
    private static final Set<String> ADMIN_ROLES = Set.of("admin", "operator", "finance", "cs");
    private static final String PERM_CACHE_KEY = "livetix:perm:";

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        long userId = Long.parseLong(loginId.toString());
        String cacheKey = PERM_CACHE_KEY + userId;

        // 21: 从 Redis 缓存读取权限
        @SuppressWarnings("unchecked")
        List<String> cached = (List<String>) redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            return cached;
        }

        List<String> permissions = new ArrayList<>();
        User user = userMapper.selectById(userId);
        if (user == null) return permissions;

        String role = user.getRole();
        // 20: 修复 — 所有后台管理角色都有通配符权限
        if (ADMIN_ROLES.contains(role)) {
            permissions.add("*");
            // 21: 缓存
            redisTemplate.opsForValue().set(cacheKey, permissions, 5, TimeUnit.MINUTES);
            return permissions;
        }

        // 对于非标准角色的用户，从 RBAC 表加载权限
        var userRole = adminRoleMapper.selectOne(new LambdaQueryWrapper<com.livetix.entity.AdminRole>()
                .eq(com.livetix.entity.AdminRole::getRoleCode, role));
        if (userRole != null) {
            var rpList = rolePermissionMapper.selectList(new LambdaQueryWrapper<AdminRolePermission>()
                    .eq(AdminRolePermission::getRoleId, userRole.getId()));
            List<Long> permIds = rpList.stream()
                    .map(AdminRolePermission::getPermissionId)
                    .collect(Collectors.toList());
            if (!permIds.isEmpty()) {
                List<AdminPermission> permList = permissionMapper.selectBatchIds(permIds);
                permissions = permList.stream()
                        .map(AdminPermission::getPermCode)
                        .collect(Collectors.toList());
            }
        }

        if (permissions.isEmpty()) {
            permissions.add("user:view");
            permissions.add("user:order");
            permissions.add("user:profile");
        }

        // 21: 缓存权限列表
        redisTemplate.opsForValue().set(cacheKey, permissions, 5, TimeUnit.MINUTES);

        return permissions;
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        List<String> roles = new ArrayList<>();
        User user = userMapper.selectById(Long.valueOf(loginId.toString()));
        if (user != null) {
            roles.add(user.getRole());
        }
        return roles;
    }
}
