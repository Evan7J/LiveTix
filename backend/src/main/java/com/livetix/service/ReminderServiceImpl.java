package com.livetix.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.livetix.common.Result;
import com.livetix.entity.ShowReminder;
import com.livetix.entity.Show;
import com.livetix.mapper.ShowReminderMapper;
import com.livetix.mapper.ShowMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ReminderServiceImpl extends ServiceImpl<ShowReminderMapper, ShowReminder> implements ReminderService {

    private final ShowMapper showMapper;

    @Override
    public Result<?> setReminder(Long showId) {
        long userId = StpUtil.getLoginIdAsLong();
        Show show = showMapper.selectById(showId);
        if (show == null) {
            return Result.fail("演出不存在");
        }
        long count = this.count(new LambdaQueryWrapper<ShowReminder>()
                .eq(ShowReminder::getUserId, userId)
                .eq(ShowReminder::getShowId, showId));
        if (count > 0) {
            return Result.fail("已设置提醒");
        }
        ShowReminder reminder = new ShowReminder();
        reminder.setUserId(userId);
        reminder.setShowId(showId);
        reminder.setIsReminded(0);
        this.save(reminder);
        return Result.ok("开售提醒已设置");
    }

    @Override
    public Result<?> cancelReminder(Long showId) {
        long userId = StpUtil.getLoginIdAsLong();
        this.remove(new LambdaQueryWrapper<ShowReminder>()
                .eq(ShowReminder::getUserId, userId)
                .eq(ShowReminder::getShowId, showId));
        return Result.ok("已取消提醒");
    }

    @Override
    public Result<?> getMyReminders() {
        long userId = StpUtil.getLoginIdAsLong();
        var list = this.list(new LambdaQueryWrapper<ShowReminder>()
                .eq(ShowReminder::getUserId, userId)
                .orderByDesc(ShowReminder::getCreateTime));
        return Result.ok(list);
    }
}