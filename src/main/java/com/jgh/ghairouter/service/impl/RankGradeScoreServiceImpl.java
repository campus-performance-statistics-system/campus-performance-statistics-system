package com.jgh.ghairouter.service.impl;

import com.jgh.ghairouter.mapper.RankGradeScoreMapper;
import com.jgh.ghairouter.model.entity.RankGradeScore;
import com.jgh.ghairouter.service.RankGradeScoreService;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RankGradeScoreServiceImpl
        extends ServiceImpl<RankGradeScoreMapper, RankGradeScore>
        implements RankGradeScoreService {

    @Override
    public List<RankGradeScore> listAll() {
        return this.list(QueryWrapper.create().orderBy("rank_id", true));
    }

    @Override
    public List<RankGradeScore> listByRankId(Long rankId) {
        return this.list(QueryWrapper.create()
                .eq("rank_id", rankId)
                .orderBy("grade_id", true));
    }

    @Override
    public RankGradeScore getByRankAndGrade(Long rankId, Long gradeId) {
        return this.getOne(QueryWrapper.create()
                .eq("rank_id", rankId)
                .eq("grade_id", gradeId));
    }
}
