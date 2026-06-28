package com.jgh.ghairouter.service;

import com.jgh.ghairouter.model.dto.competition.RecommendedEmploymentQueryRequest;
import com.jgh.ghairouter.model.entity.RecommendedEmploymentRecord;
import com.jgh.ghairouter.model.vo.RecommendedEmploymentRecordVO;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.service.IService;
import org.springframework.web.multipart.MultipartFile;

/**
 * 推荐学院学生签约就业记录服务接口（v13）。
 * 纯数据记录，不涉及计分。
 */
public interface RecommendedEmploymentRecordService extends IService<RecommendedEmploymentRecord> {

    /**
     * 提交推荐就业记录
     */
    Long addRecord(Long userId, String teacherName, String companyName,
                   Integer contractCount, String recommendationTime, MultipartFile file);

    /**
     * 获取查询条件
     */
    QueryWrapper getQueryWrapper(RecommendedEmploymentQueryRequest req);

    /**
     * 获取记录VO
     */
    RecommendedEmploymentRecordVO getRecordVO(RecommendedEmploymentRecord record);

    /**
     * 分页查询记录
     */
    Page<RecommendedEmploymentRecordVO> pageRecords(RecommendedEmploymentQueryRequest req);

    /**
     * 分页查询我的相关记录
     */
    Page<RecommendedEmploymentRecordVO> pageMyRelatedRecords(Long userId, RecommendedEmploymentQueryRequest req);

    /**
     * 管理员审核
     */
    void adminReview(Long recordId, String reviewStatus, String reviewComment, Long adminId);

    /**
     * 导出推荐就业Excel
     */
    byte[] exportRecordsToExcel();
}
