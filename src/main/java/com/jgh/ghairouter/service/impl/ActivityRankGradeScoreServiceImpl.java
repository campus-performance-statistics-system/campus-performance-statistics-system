package com.jgh.ghairouter.service.impl;

import com.jgh.ghairouter.mapper.ActivityRankGradeScoreMapper;
import com.jgh.ghairouter.model.entity.ActivityRankGradeScore;
import com.jgh.ghairouter.service.ActivityRankGradeScoreService;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 活动类型-竞赛等级-获奖等级得分规则 服务层实现
 */
@Service
public class ActivityRankGradeScoreServiceImpl
        extends ServiceImpl<ActivityRankGradeScoreMapper, ActivityRankGradeScore>
        implements ActivityRankGradeScoreService {

    @Override
    public List<ActivityRankGradeScore> listByActivityTypeId(Long activityTypeId) {
        return this.list(QueryWrapper.create()
                .eq("activity_type_id", activityTypeId)
                .orderBy("competition_rank", true)
                .orderBy("base_score", false));
    }

    @Override
    public void batchSave(Long activityTypeId, List<ActivityRankGradeScore> rules) {
        // 删除旧规则
        this.remove(QueryWrapper.create().eq("activity_type_id", activityTypeId));
        // 插入新规则
        for (ActivityRankGradeScore rule : rules) {
            rule.setActivityTypeId(activityTypeId);
            this.save(rule);
        }
    }
}
