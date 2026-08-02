package com.livetix.controller.admin;

import com.livetix.common.Result;
import com.livetix.mapper.OrderMapper;
import com.livetix.mapper.ShowMapper;
import com.livetix.mapper.UserMapper;
import com.livetix.vo.DashboardVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@RestController
@RequestMapping("/api/admin/dashboard")
@RequiredArgsConstructor
public class AdminDashboardController {

    private final OrderMapper orderMapper;
    private final UserMapper userMapper;
    private final ShowMapper showMapper;

    @GetMapping
    public Result<?> getDashboard(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) Long categoryId) {

        // Parse date range, default to last 30 days
        LocalDateTime start;
        LocalDateTime end = LocalDateTime.now();
        if (startDate != null && endDate != null) {
            start = LocalDate.parse(startDate).atStartOfDay();
            end = LocalDate.parse(endDate).atTime(LocalTime.MAX);
        } else {
            start = LocalDateTime.now().minusDays(30);
        }

        DashboardVO vo = new DashboardVO();
        vo.setTotalRevenue(orderMapper.totalRevenue());
        vo.setTotalOrderCount(orderMapper.totalOrderCount());
        vo.setTotalUserCount(userMapper.selectCount(null));

        // Active shows with optional category filter
        var showWrapper = new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.livetix.entity.Show>()
                .eq(com.livetix.entity.Show::getStatus, 1);
        if (categoryId != null) {
            showWrapper.eq(com.livetix.entity.Show::getCategoryId, categoryId);
        }
        vo.setActiveShowCount(showMapper.selectCount(showWrapper));

        // Revenue trend for selected date range
        vo.setRevenueTrend(orderMapper.revenueTrend(start));

        // Hot show ranking
        vo.setHotShows(orderMapper.hotShowRanking(10));

        // Latest orders
        vo.setLatestOrders(orderMapper.latestOrders(10));

        return Result.ok(vo);
    }
}
