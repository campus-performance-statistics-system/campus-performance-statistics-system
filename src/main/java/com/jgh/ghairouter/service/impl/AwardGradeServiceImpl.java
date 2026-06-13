package com.jgh.ghairouter.service.impl;

import com.jgh.ghairouter.mapper.AwardGradeMapper;
import com.jgh.ghairouter.model.entity.AwardGrade;
import com.jgh.ghairouter.service.AwardGradeService;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AwardGradeServiceImpl
        extends ServiceImpl<AwardGradeMapper, AwardGrade>
        implements AwardGradeService {

    @Override
    public List<AwardGrade> listAll() {
        return this.list(QueryWrapper.create().orderBy("sort_order", true));
    }
}
