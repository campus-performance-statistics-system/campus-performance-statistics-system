package com.jgh.ghairouter.service.impl;

import com.jgh.ghairouter.mapper.ScoreDistributeRuleMapper;
import com.jgh.ghairouter.model.entity.ScoreDistributeRule;
import com.jgh.ghairouter.service.ScoreDistributeRuleService;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ScoreDistributeRuleServiceImpl
        extends ServiceImpl<ScoreDistributeRuleMapper, ScoreDistributeRule>
        implements ScoreDistributeRuleService {

    @Override
    public List<ScoreDistributeRule> listAll() {
        return this.list(QueryWrapper.create().orderBy("member_count", true));
    }

    @Override
    public ScoreDistributeRule getByMemberCount(int memberCount) {
        return this.getOne(QueryWrapper.create().eq("member_count", memberCount));
    }
}
