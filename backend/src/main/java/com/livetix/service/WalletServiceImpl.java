package com.livetix.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.livetix.common.Result;
import com.livetix.entity.WalletTransaction;
import com.livetix.entity.User;
import com.livetix.mapper.WalletTransactionMapper;
import com.livetix.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class WalletServiceImpl extends ServiceImpl<WalletTransactionMapper, WalletTransaction> implements WalletService {

    private final UserMapper userMapper;

    @Override
    public Result<?> getBalance() {
        long userId = StpUtil.getLoginIdAsLong();
        User user = userMapper.selectById(userId);
        return Result.ok(user.getBalance());
    }

    @Override
    public Result<?> getTransactions(Integer page, Integer pageSize) {
        long userId = StpUtil.getLoginIdAsLong();
        Page<WalletTransaction> result = this.page(new Page<>(page, pageSize),
                new LambdaQueryWrapper<WalletTransaction>()
                        .eq(WalletTransaction::getUserId, userId)
                        .orderByDesc(WalletTransaction::getCreateTime));
        return Result.ok(result);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<?> recharge(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.valueOf(50)) < 0 || amount.compareTo(BigDecimal.valueOf(5000)) > 0) {
            return Result.fail("充值金额需在50-5000元之间");
        }
        long userId = StpUtil.getLoginIdAsLong();

        int rows = userMapper.addBalance(userId, amount);
        if (rows == 0) {
            return Result.fail("充值失败，用户不存在");
        }

        User user = userMapper.selectById(userId);
        BigDecimal balanceAfter = user != null ? user.getBalance() : BigDecimal.ZERO;

        WalletTransaction wt = new WalletTransaction();
        wt.setUserId(userId);
        wt.setType("recharge");
        wt.setAmount(amount);
        wt.setBalanceAfter(balanceAfter);
        wt.setRemark("余额充值");
        this.save(wt);

        return Result.ok("充值成功");
    }
}