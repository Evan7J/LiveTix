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
     * 扣减用户余额，余额不足时返回 0 行。
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
