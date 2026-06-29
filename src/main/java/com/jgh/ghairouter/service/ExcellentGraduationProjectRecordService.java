package com.jgh.ghairouter.service;

import com.jgh.ghairouter.model.dto.competition.ExcellentGraduationProjectQueryRequest;
import com.jgh.ghairouter.model.entity.ExcellentGraduationProjectRecord;
import com.jgh.ghairouter.model.vo.ExcellentGraduationProjectRecordVO;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.service.IService;
import org.springframework.web.multipart.MultipartFile;

/**
 * 优秀毕设记录服务接口（v16）。
 * 纯数据记录，不涉及计分。
 */
public interface ExcellentGraduationProjectRecordService extends IService<ExcellentGraduationProjectRecord> {

    /**
     * 提交优秀毕设记录
     */
    Long addRecord(Long userId, String major, String studentId,
                   String studentName, String projectTitle,
                   String advisorName, Integer rank, MultipartFile file);

    /**
     * 获取查询条件
     */
    QueryWrapper getQueryWrapper(ExcellentGraduationProjectQueryRequest req);

    /**
     * 获取记录VO
     */
    ExcellentGraduationProjectRecordVO getRecordVO(ExcellentGraduationProjectRecord record);

    /**
     * 分页查询记录
     */
    Page<ExcellentGraduationProjectRecordVO> pageRecords(ExcellentGraduationProjectQueryRequest req);

    /**
     * 分页查询我的相关记录
     */
    Page<ExcellentGraduationProjectRecordVO> pageMyRelatedRecords(Long userId, ExcellentGraduationProjectQueryRequest req);

    /**
     * 管理员审核
     */
    void adminReview(Long recordId, String reviewStatus, String reviewComment, Long adminId);

    /**
     * 导出优秀毕设Excel
     */
    byte[] exportRecordsToExcel();
}
