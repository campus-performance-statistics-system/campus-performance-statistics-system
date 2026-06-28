package com.jgh.ghairouter.mapper;

import com.jgh.ghairouter.model.entity.InvigilationRecord;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 监考次数统计记录 Mapper（v11 新增）
 */
@Mapper
public interface InvigilationRecordMapper extends BaseMapper<InvigilationRecord> {
}
