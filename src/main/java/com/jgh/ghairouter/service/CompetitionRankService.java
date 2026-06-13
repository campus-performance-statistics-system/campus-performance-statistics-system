package com.jgh.ghairouter.service;

import com.jgh.ghairouter.model.entity.CompetitionRank;
import com.mybatisflex.core.service.IService;

import java.util.List;

public interface CompetitionRankService extends IService<CompetitionRank> {
    List<CompetitionRank> listAll();
}
