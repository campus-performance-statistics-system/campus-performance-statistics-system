package com.jgh.ghairouter.service.impl;

import com.jgh.ghairouter.mapper.CategoryMapper;
import com.jgh.ghairouter.model.entity.Category;
import com.jgh.ghairouter.service.CategoryService;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 比赛分类 服务层实现
 */
@Service
public class CategoryServiceImpl extends ServiceImpl<CategoryMapper, Category> implements CategoryService {

    @Override
    public List<Category> listAll() {
        return this.list(QueryWrapper.create().orderBy("sort_order", true));
    }

    @Override
    public List<Category> listTree() {
        List<Category> allCategories = this.listAll();

        // 分离顶层和子分类
        List<Category> roots = new ArrayList<>();
        Map<Long, List<Category>> childrenMap = allCategories.stream()
                .filter(c -> c.getParentId() != null && c.getParentId() > 0)
                .collect(Collectors.groupingBy(Category::getParentId));

        for (Category category : allCategories) {
            if (category.getParentId() == null || category.getParentId() == 0) {
                category.setChildren(childrenMap.getOrDefault(category.getId(), new ArrayList<>()));
                roots.add(category);
            }
        }

        return roots;
    }

    @Override
    public List<Category> listChildren() {
        return this.list(QueryWrapper.create()
                .gt("parent_id", 0)
                .orderBy("sort_order", true));
    }
}
