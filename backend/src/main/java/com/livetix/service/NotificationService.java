package com.livetix.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.livetix.common.Result;
import com.livetix.entity.Notification;

public interface NotificationService extends IService<Notification> {

    void send(Long userId, String type, String title, String content);

    void send(Long userId, String type, String title, String content, Long relatedId);

    Result<?> getUserNotifications(Integer page, Integer pageSize, String type);

    Result<?> markAsRead(Long id);

    Result<?> markAllAsRead();

    Result<?> getUnreadCount();
}