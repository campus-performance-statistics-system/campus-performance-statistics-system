package com.jgh.ghairouter.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 脱敏后的登录用户信息
 */
@Data
public class LoginUserVO implements Serializable {

    private Long id;
    private String userAccount;
    private String userName;
    private String userRole;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    private static final long serialVersionUID = 1L;
}
