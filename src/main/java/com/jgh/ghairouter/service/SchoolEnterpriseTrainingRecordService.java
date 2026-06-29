package com.jgh.ghairouter.service;

import com.jgh.ghairouter.model.dto.competition.SchoolEnterpriseTrainingQueryRequest;
import com.jgh.ghairouter.model.entity.SchoolEnterpriseTrainingRecord;
import com.jgh.ghairouter.model.vo.SchoolEnterpriseTrainingRecordVO;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.service.IService;
import org.springframework.web.multipart.MultipartFile;

/**
 * 校企联合培养记录服务接口（v17）。
 * 纯数据记录，不涉及计分。
 */
public interface SchoolEnterpriseTrainingRecordService extends IService<SchoolEnterpriseTrainingRecord> {

    /**
     * 提交校企联合培养记录
     */
    Long addRecord(Long userId, String studentName, String studentId,
                   String major, String companyName, String remark,
                   String projectCollectionStatus, String advisorName,
                   String counselorName, MultipartFile file);

    /**
     * 获取查询条件
     */
    QueryWrapper getQueryWrapper(SchoolEnterpriseTrainingQueryRequest req);

    /**
     * 获取记录VO
     */
    SchoolEnterpriseTrainingRecordVO getRecordVO(SchoolEnterpriseTrainingRecord record);

    /**
     * 分页查询记录
     */
    Page<SchoolEnterpriseTrainingRecordVO> pageRecords(SchoolEnterpriseTrainingQueryRequest req);

    /**
     * 分页查询我的相关记录
     */
    Page<SchoolEnterpriseTrainingRecordVO> pageMyRelatedRecords(Long userId, SchoolEnterpriseTrainingQueryRequest req);

    /**
     * 管理员审核
     */
    void adminReview(Long recordId, String reviewStatus, String reviewComment, Long adminId);

    /**
     * 导出校企联合培养Excel
     */
    byte[] exportRecordsToExcel();
}
