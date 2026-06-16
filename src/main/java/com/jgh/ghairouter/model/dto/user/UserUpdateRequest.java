package com.jgh.ghairouter.model.dto.user;

import lombok.Data;

import java.io.Serializable;

/**
 * 用户更新请求
 */
@Data
public class UserUpdateRequest implements Serializable {

    private Long id;
    private String userName;
    private String userRole;

    private static final long serialVersionUID = 1L;
}
