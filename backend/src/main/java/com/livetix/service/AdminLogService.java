package com.livetix.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.livetix.common.Result;
import com.livetix.entity.AdminLog;

public interface AdminLogService extends IService<AdminLog> {

    void record(String module, String action, Long targetId, String detail, String ip);

    Result<?> listLogs(Integer page, Integer pageSize, String module, String action, String adminName);
}