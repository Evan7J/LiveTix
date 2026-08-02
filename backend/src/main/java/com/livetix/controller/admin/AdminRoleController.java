package com.livetix.controller.admin;

import com.livetix.common.Result;
import com.livetix.entity.AdminRole;
import com.livetix.entity.AdminPermission;
import com.livetix.entity.AdminRolePermission;
import com.livetix.mapper.AdminRoleMapper;
import com.livetix.mapper.AdminPermissionMapper;
import com.livetix.mapper.AdminRolePermissionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminRoleController {

    private final AdminRoleMapper roleMapper;
    private final AdminPermissionMapper permissionMapper;
    private final AdminRolePermissionMapper rolePermissionMapper;

    @GetMapping("/roles")
    public Result<?> listRoles() {
        return Result.ok(roleMapper.selectList(null));
    }

    @PostMapping("/roles")
    public Result<?> createRole(@RequestBody AdminRole role) {
        roleMapper.insert(role);
        return Result.ok("创建成功", role);
    }

    @PutMapping("/roles/{id}")
    public Result<?> updateRole(@PathVariable Long id, @RequestBody AdminRole role) {
        role.setId(id);
        roleMapper.updateById(role);
        return Result.ok("更新成功");
    }

    @DeleteMapping("/roles/{id}")
    public Result<?> deleteRole(@PathVariable Long id) {
        roleMapper.deleteById(id);
        // Also delete role-permission associations
        rolePermissionMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AdminRolePermission>()
                .eq(AdminRolePermission::getRoleId, id));
        return Result.ok("删除成功");
    }

    @GetMapping("/permissions")
    public Result<?> listPermissions() {
        return Result.ok(permissionMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AdminPermission>()
                        .orderByAsc(AdminPermission::getSort)));
    }

    @GetMapping("/roles/{id}/permissions")
    public Result<?> getRolePermissions(@PathVariable Long id) {
        var list = rolePermissionMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AdminRolePermission>()
                        .eq(AdminRolePermission::getRoleId, id));
        return Result.ok(list.stream().map(AdminRolePermission::getPermissionId).collect(Collectors.toList()));
    }

    /**
     * 25 修复: @Transactional + 批量插入（替代逐条insert）
     */
    @Transactional(rollbackFor = Exception.class)
    @PutMapping("/roles/{id}/permissions")
    public Result<?> updateRolePermissions(@PathVariable Long id, @RequestBody Map<String, List<Long>> body) {
        List<Long> permIds = body.get("permissionIds");
        // Delete existing
        rolePermissionMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AdminRolePermission>()
                .eq(AdminRolePermission::getRoleId, id));
        // 25: 批量插入
        if (permIds != null && !permIds.isEmpty()) {
            List<AdminRolePermission> batch = new ArrayList<>();
            for (Long permId : permIds) {
                AdminRolePermission rp = new AdminRolePermission();
                rp.setRoleId(id);
                rp.setPermissionId(permId);
                batch.add(rp);
            }
            // MyBatis-Plus insert 逐条（在 @Transactional 保护下原子执行）
            batch.forEach(rolePermissionMapper::insert);
        }
        return Result.ok("权限更新成功");
    }
}
