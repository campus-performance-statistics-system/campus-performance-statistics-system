package com.jgh.ghairouter.model.dto.category;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 更新分类请求
 */
@Data
public class CategoryUpdateRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * id
     */
    private Long id;

    /**
     * 分类名称
     */
    private String name;

    /**
     * 分类描述
     */
    private String description;

    /**
     * 排序
     */
    private Integer sortOrder;
}
