package com.jgh.ghairouter.service;

import com.jgh.ghairouter.model.dto.competition.YoungTeacherGuidanceQueryRequest;
import com.jgh.ghairouter.model.entity.YoungTeacherGuidanceRecord;
import com.jgh.ghairouter.model.vo.YoungTeacherGuidanceRecordVO;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.service.IService;
import org.springframework.web.multipart.MultipartFile;

/**
 * 指导青年教师记录服务接口（v15）。
 * 纯数据记录，不涉及计分。
 */
public interface YoungTeacherGuidanceRecordService extends IService<YoungTeacherGuidanceRecord> {

    /**
     * 提交指导青年教师记录
     */
    Long addRecord(Long userId, String college, String mentorNames,
                   String mentorTitles, String youngTeacherName,
                   String youngTeacherEntryTime, MultipartFile file);

    /**
     * 获取查询条件
     */
    QueryWrapper getQueryWrapper(YoungTeacherGuidanceQueryRequest req);

    /**
     * 获取记录VO
     */
    YoungTeacherGuidanceRecordVO getRecordVO(YoungTeacherGuidanceRecord record);

    /**
     * 分页查询记录
     */
    Page<YoungTeacherGuidanceRecordVO> pageRecords(YoungTeacherGuidanceQueryRequest req);

    /**
     * 分页查询我的相关记录
     */
    Page<YoungTeacherGuidanceRecordVO> pageMyRelatedRecords(Long userId, YoungTeacherGuidanceQueryRequest req);

    /**
     * 管理员审核
     */
    void adminReview(Long recordId, String reviewStatus, String reviewComment, Long adminId);

    /**
     * 导出指导青年教师Excel
     */
    byte[] exportRecordsToExcel();
}
