package com.livetix.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.livetix.common.Result;
import com.livetix.entity.RefundRequest;

public interface RefundService extends IService<RefundRequest> {

    Result<?> applyRefund(Long userId, Long orderId, String reason);

    Result<?> listRefunds(Integer page, Integer pageSize, String status);

    Result<?> approveRefund(Long refundId, String comment);

    Result<?> rejectRefund(Long refundId, String comment);
}