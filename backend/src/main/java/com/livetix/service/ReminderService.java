package com.livetix.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.livetix.common.Result;
import com.livetix.entity.ShowReminder;

public interface ReminderService extends IService<ShowReminder> {

    Result<?> setReminder(Long showId);

    Result<?> cancelReminder(Long showId);

    Result<?> getMyReminders();
}