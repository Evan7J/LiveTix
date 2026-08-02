package com.livetix.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.livetix.common.Result;
import com.livetix.dto.LoginDTO;
import com.livetix.entity.User;
import com.livetix.vo.LoginVO;

public interface UserService extends IService<User> {

    Result<LoginVO> login(LoginDTO dto);

    Result<?> register(User user);

    User currentUser();

    Result<?> listUsers(Integer page, Integer pageSize, String keyword);
}