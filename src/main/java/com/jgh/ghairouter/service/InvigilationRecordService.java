package com.jgh.ghairouter.service;

import com.jgh.ghairouter.model.dto.competition.InvigilationQueryRequest;
import com.jgh.ghairouter.model.entity.InvigilationRecord;
import com.jgh.ghairouter.model.vo.InvigilationRecordVO;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.service.IService;
import org.springframework.web.multipart.MultipartFile;

/**
 * 监考次数统计记录服务接口（v11）
 */
public interface InvigilationRecordService extends IService<InvigilationRecord> {

    /**
     * 提交监考次数统计记录
     */
    Long addRecord(Long userId, String teacherName, Integer invigilationCount, MultipartFile file);

    /**
     * 获取查询条件
     */
    QueryWrapper getQueryWrapper(InvigilationQueryRequest req);

    /**
     * 获取记录VO
     */
    InvigilationRecordVO getRecordVO(InvigilationRecord record);

    /**
     * 分页查询记录
     */
    Page<InvigilationRecordVO> pageRecords(InvigilationQueryRequest req);

    /**
     * 分页查询我的相关记录
     */
    Page<InvigilationRecordVO> pageMyRelatedRecords(Long userId, InvigilationQueryRequest req);

    /**
     * 管理员审核
     */
    void adminReview(Long recordId, String reviewStatus, String reviewComment, Long adminId);

    /**
     * 导出监考次数统计Excel
     */
    byte[] exportRecordsToExcel();
}
