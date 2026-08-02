package com.livetix.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.livetix.common.Result;
import com.livetix.entity.Notification;
import com.livetix.mapper.NotificationMapper;
import org.springframework.stereotype.Service;

@Service
public class NotificationServiceImpl extends ServiceImpl<NotificationMapper, Notification> implements NotificationService {

    @Override
    public void send(Long userId, String type, String title, String content) {
        send(userId, type, title, content, null);
    }

    @Override
    public void send(Long userId, String type, String title, String content, Long relatedId) {
        Notification notif = new Notification();
        notif.setUserId(userId);
        notif.setType(type);
        notif.setTitle(title);
        notif.setContent(content);
        notif.setRelatedId(relatedId);
        notif.setIsRead(0);
        this.save(notif);
    }

    @Override
    public Result<?> getUserNotifications(Integer page, Integer pageSize, String type) {
        long userId = StpUtil.getLoginIdAsLong();
        LambdaQueryWrapper<Notification> wrapper = new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, userId);
        if (type != null && !type.isBlank()) {
            wrapper.eq(Notification::getType, type);
        }
        wrapper.orderByDesc(Notification::getCreateTime);
        Page<Notification> result = this.page(new Page<>(page, pageSize), wrapper);
        return Result.ok(result);
    }

    @Override
    public Result<?> markAsRead(Long id) {
        long userId = StpUtil.getLoginIdAsLong();
        Notification notif = this.getById(id);
        if (notif == null || !notif.getUserId().equals(userId)) {
            return Result.fail("通知不存在");
        }
        notif.setIsRead(1);
        this.updateById(notif);
        return Result.ok();
    }

    @Override
    public Result<?> markAllAsRead() {
        long userId = StpUtil.getLoginIdAsLong();
        var update = new LambdaUpdateWrapper<Notification>()
                .set(Notification::getIsRead, 1)
                .eq(Notification::getUserId, userId)
                .eq(Notification::getIsRead, 0);
        this.update(update);
        return Result.ok();
    }

    @Override
    public Result<?> getUnreadCount() {
        long userId = StpUtil.getLoginIdAsLong();
        long count = this.count(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .eq(Notification::getIsRead, 0));
        return Result.ok(count);
    }
}