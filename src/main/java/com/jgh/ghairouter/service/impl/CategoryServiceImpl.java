package com.jgh.ghairouter.service.impl;

import com.jgh.ghairouter.mapper.CategoryMapper;
import com.jgh.ghairouter.model.entity.Category;
import com.jgh.ghairouter.service.CategoryService;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 比赛分类 服务层实现
 */
@Service
public class CategoryServiceImpl extends ServiceImpl<CategoryMapper, Category> implements CategoryService {

    @Override
    public List<Category> listAll() {
        return this.list(QueryWrapper.create().orderBy("id", true));
    }
}
