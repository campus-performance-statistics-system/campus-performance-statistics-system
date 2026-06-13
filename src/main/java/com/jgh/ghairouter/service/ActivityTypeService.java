package com.jgh.ghairouter.service;

import com.jgh.ghairouter.model.entity.ActivityType;
import com.mybatisflex.core.service.IService;

import java.util.List;

/**
 * 活动类型 服务接口
 */
public interface ActivityTypeService extends IService<ActivityType> {

    /**
     * 根据分类ID获取活动类型列表
     */
    List<ActivityType> listByCategoryId(Long categoryId);

    /**
     * 获取所有活动类型
     */
    List<ActivityType> listAll();
}
