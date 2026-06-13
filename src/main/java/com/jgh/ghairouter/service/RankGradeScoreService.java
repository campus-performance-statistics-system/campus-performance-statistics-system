package com.jgh.ghairouter.service;

import com.jgh.ghairouter.model.entity.RankGradeScore;
import com.mybatisflex.core.service.IService;

import java.util.List;

public interface RankGradeScoreService extends IService<RankGradeScore> {
    List<RankGradeScore> listAll();
    List<RankGradeScore> listByRankId(Long rankId);
}
