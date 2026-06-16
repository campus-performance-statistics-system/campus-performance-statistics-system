package com.jgh.ghairouter.model.dto.user;

import lombok.Data;

import java.io.Serializable;

/**
 * 用户创建请求
 */
@Data
public class UserAddRequest implements Serializable {

    private String userName;
    private String userAccount;
    private String userRole;

    private static final long serialVersionUID = 1L;
}
