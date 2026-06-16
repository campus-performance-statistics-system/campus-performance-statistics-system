package com.jgh.ghairouter.service;

import com.jgh.ghairouter.model.entity.ActivityRankGradeScore;
import com.mybatisflex.core.service.IService;

import java.util.List;

/**
 * 活动类型-竞赛等级-获奖等级得分规则 服务层
 */
public interface ActivityRankGradeScoreService extends IService<ActivityRankGradeScore> {

    /**
     * 获取指定活动类型的所有得分规则
     */
    List<ActivityRankGradeScore> listByActivityTypeId(Long activityTypeId);

    /**
     * 批量保存规则（先删后增）
     */
    void batchSave(Long activityTypeId, List<ActivityRankGradeScore> rules);
}
