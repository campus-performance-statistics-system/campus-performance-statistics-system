package com.jgh.ghairouter.service;

import com.jgh.ghairouter.model.dto.competition.OnlineEvaluationQueryRequest;
import com.jgh.ghairouter.model.entity.OnlineEvaluationRecord;
import com.jgh.ghairouter.model.vo.OnlineEvaluationRecordVO;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.service.IService;
import org.springframework.web.multipart.MultipartFile;

/**
 * 网上评教记录服务接口（v12）
 */
public interface OnlineEvaluationRecordService extends IService<OnlineEvaluationRecord> {

    /**
     * 提交网上评教记录
     */
    Long addRecord(Long userId, String teacherName, String teacherType,
                   String academicYear, String semester,
                   String courseCode, String courseName,
                   Integer participantCount, java.math.BigDecimal averageScore,
                   MultipartFile file);

    /**
     * 获取查询条件
     */
    QueryWrapper getQueryWrapper(OnlineEvaluationQueryRequest req);

    /**
     * 获取记录VO
     */
    OnlineEvaluationRecordVO getRecordVO(OnlineEvaluationRecord record);

    /**
     * 分页查询记录
     */
    Page<OnlineEvaluationRecordVO> pageRecords(OnlineEvaluationQueryRequest req);

    /**
     * 分页查询我的相关记录
     */
    Page<OnlineEvaluationRecordVO> pageMyRelatedRecords(Long userId, OnlineEvaluationQueryRequest req);

    /**
     * 管理员审核
     */
    void adminReview(Long recordId, String reviewStatus, String reviewComment, Long adminId);

    /**
     * 导出网上评教Excel
     */
    byte[] exportRecordsToExcel();
}
