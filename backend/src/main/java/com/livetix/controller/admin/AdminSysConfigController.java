package com.livetix.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.livetix.common.Result;
import com.livetix.entity.SysConfig;
import com.livetix.mapper.SysConfigMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Admin: System configuration
 */
@RestController
@RequestMapping("/api/admin/config")
@RequiredArgsConstructor
public class AdminSysConfigController {

    private final SysConfigMapper sysConfigMapper;

    @GetMapping
    public Result<?> list() {
        List<SysConfig> configs = sysConfigMapper.selectList(null);
        return Result.ok(configs);
    }

    @PutMapping("/{id}")
    public Result<?> update(@PathVariable Long id, @RequestBody SysConfig config) {
        config.setId(id);
        sysConfigMapper.updateById(config);
        return Result.ok("更新成功");
    }
}
