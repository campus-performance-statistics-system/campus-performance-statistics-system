package com.jgh.ghairouter.service;

import com.jgh.ghairouter.model.entity.AwardGrade;
import com.mybatisflex.core.service.IService;

import java.util.List;

public interface AwardGradeService extends IService<AwardGrade> {
    List<AwardGrade> listAll();
}
