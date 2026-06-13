package com.jgh.ghairouter.service;

import com.jgh.ghairouter.model.entity.Category;
import com.mybatisflex.core.service.IService;

import java.util.List;

/**
 * 比赛分类 服务层
 */
public interface CategoryService extends IService<Category> {

    /**
     * 获取所有分类列表（按 sort_order 排序）
     */
    List<Category> listAll();
}
