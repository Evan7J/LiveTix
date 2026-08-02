package com.livetix.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 注册请求DTO —— 仅包含注册必要字段，防止客户端注入 role/balance 等敏感字段
 */
@Data
public class RegisterDTO {

    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 32, message = "用户名长度3-32位")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 64, message = "密码长度6-64位")
    private String password;

    private String nickname;
    private String phone;
    private String email;
    private Integer gender;

    /** 注册验证码（集成短信/邮件服务后启用校验） */
    private String code;
}
