package com.livetix.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.livetix.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;

@Mapper
public interface UserMapper extends BaseMapper<User> {

    /**
     * P0-4: 原子余额扣减 — 乐观锁单条 SQL
     *
     * 替代"读-改-写"三步操作，在数据库层面保证：
     *   1. 余额不足时返回 0 行，不会超扣
     *   2. 多线程并发扣款仅一条成功，其余返回 0
     *
     * @param userId 用户ID
     * @param amount 扣减金额
     * @return 影响行数：1=扣减成功，0=余额不足
     */
    @Update("UPDATE t_user SET balance = balance - #{amount} " +
            "WHERE id = #{userId} AND balance >= #{amount}")
    int deductBalance(@Param("userId") Long userId, @Param("amount") BigDecimal amount);

    /**
     * 原子余额增加（充值）
     */
    @Update("UPDATE t_user SET balance = balance + #{amount} WHERE id = #{userId}")
    int addBalance(@Param("userId") Long userId, @Param("amount") BigDecimal amount);
}
