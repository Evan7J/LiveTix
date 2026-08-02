package com.livetix.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LoginVO {

    private String token;
    private Long userId;
    private String username;
    private String nickname;
    private String avatar;
    private String role;
    private Integer memberLevel;
}
