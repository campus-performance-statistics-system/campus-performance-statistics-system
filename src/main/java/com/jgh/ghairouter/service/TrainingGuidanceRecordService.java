package com.jgh.ghairouter.service;

import com.jgh.ghairouter.model.dto.competition.TrainingGuidanceQueryRequest;
import com.jgh.ghairouter.model.entity.TrainingGuidanceRecord;
import com.jgh.ghairouter.model.vo.TrainingGuidanceRecordVO;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.service.IService;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

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

    /**
     * 获取所有教师的总得分汇总（按教师姓名分组求和，按分数降序）
     */
    List<Map<String, Object>> getTeacherTotalScores();
}
