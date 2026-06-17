package com.jgh.ghairouter.service.impl;

import cn.hutool.core.util.StrUtil;
import com.jgh.ghairouter.exception.BusinessException;
import com.jgh.ghairouter.exception.ErrorCode;
import com.jgh.ghairouter.mapper.TeacherCompetitionAuditRecordMapper;
import com.jgh.ghairouter.model.entity.TeacherCompetitionAuditRecord;
import com.jgh.ghairouter.model.enums.ReviewStatusEnum;
import com.jgh.ghairouter.service.TeacherCompetitionAuditRecordService;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 教师比赛审核记录 服务层实现
 */
@Service
public class TeacherCompetitionAuditRecordServiceImpl
        extends ServiceImpl<TeacherCompetitionAuditRecordMapper, TeacherCompetitionAuditRecord>
        implements TeacherCompetitionAuditRecordService {

    @Override
    public TeacherCompetitionAuditRecord getAuditByRecordId(Long recordId) {
        return this.getOne(QueryWrapper.create().eq("record_id", recordId));
    }

    @Override
    public void updateAutoReview(Long recordId, String status, String comment) {
        TeacherCompetitionAuditRecord audit = getAuditByRecordId(recordId);
        if (audit != null) {
            audit.setAutoReviewStatus(status);
            audit.setAutoReviewComment(comment);
            this.updateById(audit);
        }
    }

    @Override
    public void adminReview(Long recordId, String reviewStatus, String reviewComment, Long adminId) {
        if (recordId == null) throw new BusinessException(ErrorCode.PARAMS_ERROR, "记录ID不能为空");
        if (StrUtil.isBlank(reviewStatus)) throw new BusinessException(ErrorCode.PARAMS_ERROR, "审核状态不能为空");

        ReviewStatusEnum statusEnum = ReviewStatusEnum.getEnumByValue(reviewStatus);
        if (statusEnum == null) throw new BusinessException(ErrorCode.PARAMS_ERROR, "无效的审核状态");
        if (statusEnum == ReviewStatusEnum.PENDING)
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "审核状态不能为待审核");

        TeacherCompetitionAuditRecord audit = getAuditByRecordId(recordId);
        if (audit == null) throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "审核记录不存在");

        if (statusEnum == ReviewStatusEnum.PASSED && StrUtil.isBlank(reviewComment))
            reviewComment = "审核通过";

        audit.setAdminReviewStatus(reviewStatus);
        audit.setAdminReviewComment(reviewComment);
        audit.setAdminId(adminId);
        audit.setAdminReviewTime(LocalDateTime.now());

        if (!this.updateById(audit))
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "审核失败");
    }
}
