package com.jgh.ghairouter.service;

import com.jgh.ghairouter.model.entity.TeacherCompetitionAuditRecord;
import com.mybatisflex.core.service.IService;

/**
 * 教师比赛审核记录 服务层
 */
public interface TeacherCompetitionAuditRecordService extends IService<TeacherCompetitionAuditRecord> {

    /**
     * 根据记录ID获取审核记录
     */
    TeacherCompetitionAuditRecord getAuditByRecordId(Long recordId);

    /**
     * 更新自动审核结果
     */
    void updateAutoReview(Long recordId, String status, String comment);

    /**
     * 管理员审核
     */
    void adminReview(Long recordId, String reviewStatus, String reviewComment, Long adminId);
}
