package com.jgh.ghairouter.service;

import com.jgh.ghairouter.model.entity.ScoreDistributeRule;
import com.mybatisflex.core.service.IService;

import java.util.List;

public interface ScoreDistributeRuleService extends IService<ScoreDistributeRule> {
    List<ScoreDistributeRule> listAll();
    ScoreDistributeRule getByMemberCount(int memberCount);
}
