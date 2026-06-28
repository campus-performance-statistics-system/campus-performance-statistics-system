package com.jgh.ghairouter.service;

import com.jgh.ghairouter.model.dto.competition.CooperativeEnterpriseQueryRequest;
import com.jgh.ghairouter.model.entity.CooperativeEnterpriseRecord;
import com.jgh.ghairouter.model.vo.CooperativeEnterpriseRecordVO;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.service.IService;
import org.springframework.web.multipart.MultipartFile;

/**
 * 签订合作企业记录服务接口（v14）。
 * 纯数据记录，不涉及计分。
 */
public interface CooperativeEnterpriseRecordService extends IService<CooperativeEnterpriseRecord> {

    /**
     * 提交签订合作企业记录
     */
    Long addRecord(Long userId, String college, String enterpriseName,
                   String teacherName, MultipartFile file);

    /**
     * 获取查询条件
     */
    QueryWrapper getQueryWrapper(CooperativeEnterpriseQueryRequest req);

    /**
     * 获取记录VO
     */
    CooperativeEnterpriseRecordVO getRecordVO(CooperativeEnterpriseRecord record);

    /**
     * 分页查询记录
     */
    Page<CooperativeEnterpriseRecordVO> pageRecords(CooperativeEnterpriseQueryRequest req);

    /**
     * 分页查询我的相关记录
     */
    Page<CooperativeEnterpriseRecordVO> pageMyRelatedRecords(Long userId, CooperativeEnterpriseQueryRequest req);

    /**
     * 管理员审核
     */
    void adminReview(Long recordId, String reviewStatus, String reviewComment, Long adminId);

    /**
     * 导出签订合作企业Excel
     */
    byte[] exportRecordsToExcel();
}
