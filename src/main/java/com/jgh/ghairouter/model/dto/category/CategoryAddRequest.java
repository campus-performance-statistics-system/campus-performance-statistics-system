package com.jgh.ghairouter.model.dto.category;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 新增分类请求
 */
@Data
public class CategoryAddRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 分类名称
     */
    private String name;

    /**
     * 分类描述
     */
    private String description;

}
