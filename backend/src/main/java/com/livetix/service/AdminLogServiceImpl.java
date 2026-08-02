package com.livetix.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.livetix.common.Result;
import com.livetix.entity.AdminLog;
import com.livetix.entity.User;
import com.livetix.mapper.AdminLogMapper;
import com.livetix.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AdminLogServiceImpl extends ServiceImpl<AdminLogMapper, AdminLog> implements AdminLogService {

    private final UserMapper userMapper;

    @Override
    public void record(String module, String action, Long targetId, String detail, String ip) {
        try {
            long adminId = StpUtil.getLoginIdAsLong();
            User admin = userMapper.selectById(adminId);
            AdminLog log = new AdminLog();
            log.setAdminId(adminId);
            log.setAdminName(admin != null ? admin.getUsername() : "unknown");
            log.setModule(module);
            log.setAction(action);
            log.setTargetId(targetId);
            log.setDetail(detail);
            log.setIp(ip);
            this.save(log);
        } catch (Exception e) {
            // Silently fail - don't break business flow for logging
        }
    }

    @Override
    public Result<?> listLogs(Integer page, Integer pageSize, String module, String action, String adminName) {
        LambdaQueryWrapper<AdminLog> wrapper = new LambdaQueryWrapper<>();
        if (module != null && !module.isBlank()) {
            wrapper.eq(AdminLog::getModule, module);
        }
        if (action != null && !action.isBlank()) {
            wrapper.eq(AdminLog::getAction, action);
        }
        if (adminName != null && !adminName.isBlank()) {
            wrapper.like(AdminLog::getAdminName, adminName);
        }
        wrapper.orderByDesc(AdminLog::getCreateTime);
        Page<AdminLog> result = this.page(new Page<>(page, pageSize), wrapper);
        return Result.ok(result);
    }
}