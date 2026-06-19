package com.jgh.ghairouter.service;

import com.jgh.ghairouter.model.dto.competition.TrainingGuidanceQueryRequest;
import com.jgh.ghairouter.model.entity.TrainingGuidanceRecord;
import com.jgh.ghairouter.model.vo.TrainingGuidanceRecordVO;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.service.IService;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;

/**
 * 指导实训记录 服务层（v4）
 */
public interface TrainingGuidanceRecordService extends IService<TrainingGuidanceRecord> {

    /**
     * 提交记录
     */
    Long addRecord(Long userId,
                   String semester, String trainingName,
                   String responsibleTeachers, String participatingTeachers,
                   MultipartFile file);

    QueryWrapper getQueryWrapper(TrainingGuidanceQueryRequest queryRequest);

    TrainingGuidanceRecordVO getRecordVO(TrainingGuidanceRecord record);

    Page<TrainingGuidanceRecordVO> pageRecords(TrainingGuidanceQueryRequest queryRequest);

    Page<TrainingGuidanceRecordVO> pageMyRelatedRecords(Long userId, TrainingGuidanceQueryRequest req);

    BigDecimal getMyTotalScore(Long userId);

    void adminReview(Long recordId, String reviewStatus, String reviewComment, Long adminId);

    /**
     * 导出所有记录为Excel
     */
    byte[] exportRecordsToExcel();
}
