package com.jgh.ghairouter.service;

import com.jgh.ghairouter.model.dto.competition.PartTimeClassAdvisorQueryRequest;
import com.jgh.ghairouter.model.entity.PartTimeClassAdvisorRecord;
import com.jgh.ghairouter.model.vo.PartTimeClassAdvisorRecordVO;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.service.IService;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;

/**
 * 兼职班主任业绩记录服务接口（v10）
 */
public interface PartTimeClassAdvisorRecordService extends IService<PartTimeClassAdvisorRecord> {

    /**
     * 提交兼职班主任业绩记录
     */
    Long addRecord(Long userId, String teacherName, String classId,
                   BigDecimal studyStyleWorkReq, BigDecimal studyStyleEffect,
                   BigDecimal safetyEduWorkReq, BigDecimal safetyEduEffect,
                   BigDecimal strugglingStudentWorkReq, BigDecimal strugglingStudentEffect,
                   BigDecimal achievementSafety, BigDecimal achievementStudyStyle,
                   BigDecimal achievementStruggling,
                   Integer isFreshmenOrGraduating,
                   MultipartFile file);

    /**
     * 获取查询条件
     */
    QueryWrapper getQueryWrapper(PartTimeClassAdvisorQueryRequest req);

    /**
     * 获取记录VO
     */
    PartTimeClassAdvisorRecordVO getRecordVO(PartTimeClassAdvisorRecord record);

    /**
     * 分页查询记录
     */
    Page<PartTimeClassAdvisorRecordVO> pageRecords(PartTimeClassAdvisorQueryRequest req);

    /**
     * 分页查询我的相关记录（按得分表匹配）
     */
    Page<PartTimeClassAdvisorRecordVO> pageMyRelatedRecords(Long userId, PartTimeClassAdvisorQueryRequest req);

    /**
     * 获取我的总得分
     */
    BigDecimal getMyTotalScore(Long userId);

    /**
     * 管理员审核
     */
    void adminReview(Long recordId, String reviewStatus, String reviewComment, Long adminId);

    /**
     * 导出兼职班主任业绩得分Excel
     */
    byte[] exportRecordsToExcel();
}
