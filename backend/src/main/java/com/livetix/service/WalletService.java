package com.livetix.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.livetix.common.Result;
import com.livetix.entity.WalletTransaction;

import java.math.BigDecimal;

public interface WalletService extends IService<WalletTransaction> {

    Result<?> getBalance();

    Result<?> getTransactions(Integer page, Integer pageSize);

    Result<?> recharge(BigDecimal amount);
}