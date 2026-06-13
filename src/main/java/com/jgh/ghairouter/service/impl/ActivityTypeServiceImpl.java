package com.jgh.ghairouter.service.impl;

import com.jgh.ghairouter.mapper.ActivityTypeMapper;
import com.jgh.ghairouter.model.entity.ActivityType;
import com.jgh.ghairouter.service.ActivityTypeService;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 活动类型 服务层实现
 */
@Service
public class ActivityTypeServiceImpl
        extends ServiceImpl<ActivityTypeMapper, ActivityType>
        implements ActivityTypeService {

    @Override
    public List<ActivityType> listByCategoryId(Long categoryId) {
        return this.list(QueryWrapper.create()
                .eq("category_id", categoryId)
                .orderBy("sort_order", true));
    }

    @Override
    public List<ActivityType> listAll() {
        return this.list(QueryWrapper.create().orderBy("sort_order", true));
    }
}
