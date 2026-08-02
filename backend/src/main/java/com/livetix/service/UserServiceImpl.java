package com.livetix.service;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.livetix.common.Result;
import com.livetix.common.exception.BusinessException;
import com.livetix.dto.LoginDTO;
import com.livetix.entity.User;
import com.livetix.mapper.UserMapper;
import com.livetix.vo.LoginVO;
import org.mindrot.jbcrypt.BCrypt;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    @Override
    public Result<LoginVO> login(LoginDTO dto) {
        User user = this.getOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, dto.getUsername()));

        if (user == null) {
            return Result.fail("用户名或密码错误");
        }

        if (user.getStatus() == 0) {
            return Result.fail("账号已被禁用，请联系管理员");
        }

        if (!BCrypt.checkpw(dto.getPassword(), user.getPassword())) {
            return Result.fail("用户名或密码错误");
        }

        StpUtil.login(user.getId());

        user.setLastLoginTime(LocalDateTime.now());
        this.updateById(user);

        LoginVO vo = LoginVO.builder()
                .token(StpUtil.getTokenValue())
                .userId(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .avatar(user.getAvatar())
                .role(user.getRole())
                .memberLevel(user.getMemberLevel())
                .build();

        return Result.ok("登录成功", vo);
    }

    @Override
    public Result<?> register(User user) {
        long count = this.count(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, user.getUsername()));
        if (count > 0) {
            return Result.fail("用户名已存在");
        }

        user.setPassword(BCrypt.hashpw(user.getPassword(), BCrypt.gensalt()));
        user.setRole("user");
        user.setStatus(1);
        user.setMemberLevel(0);
        user.setBalance(java.math.BigDecimal.ZERO);
        user.setPoints(0);

        this.save(user);
        return Result.ok("注册成功");
    }

    @Override
    public User currentUser() {
        long userId = StpUtil.getLoginIdAsLong();
        User user = this.getById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        user.setPassword(null);
        return user;
    }

    @Override
    public Result<?> listUsers(Integer page, Integer pageSize, String keyword) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        if (StrUtil.isNotBlank(keyword)) {
            wrapper.like(User::getUsername, keyword)
                   .or()
                   .like(User::getNickname, keyword)
                   .or()
                   .like(User::getPhone, keyword);
        }
        wrapper.orderByDesc(User::getCreateTime);
        Page<User> result = this.page(new Page<>(page, pageSize), wrapper);
        result.getRecords().forEach(u -> u.setPassword(null));
        return Result.ok(result);
    }
}