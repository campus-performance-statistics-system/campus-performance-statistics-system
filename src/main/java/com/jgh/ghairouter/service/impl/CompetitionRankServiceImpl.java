package com.jgh.ghairouter.service.impl;

import com.jgh.ghairouter.mapper.CompetitionRankMapper;
import com.jgh.ghairouter.model.entity.CompetitionRank;
import com.jgh.ghairouter.service.CompetitionRankService;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CompetitionRankServiceImpl
        extends ServiceImpl<CompetitionRankMapper, CompetitionRank>
        implements CompetitionRankService {

    @Override
    public List<CompetitionRank> listAll() {
        return this.list(QueryWrapper.create().orderBy("sort_order", true));
    }
}
