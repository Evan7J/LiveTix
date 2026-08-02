package com.livetix.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.livetix.common.Result;
import com.livetix.entity.RealNameInfo;

public interface RealNameService extends IService<RealNameInfo> {

    Result<?> listMyRealNames();

    Result<?> addRealName(RealNameInfo info);

    Result<?> updateRealName(Long id, RealNameInfo info);

    Result<?> deleteRealName(Long id);
}