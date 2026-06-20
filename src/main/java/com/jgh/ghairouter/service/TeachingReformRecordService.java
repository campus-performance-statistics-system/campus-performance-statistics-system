package com.jgh.ghairouter.service;

import com.jgh.ghairouter.model.dto.competition.TeachingReformQueryRequest;
import com.jgh.ghairouter.model.entity.TeachingReformRecord;
import com.jgh.ghairouter.model.vo.TeachingReformRecordVO;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.service.IService;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;

/**
 * 教改科研项目业绩记录服务接口（v7）
 */
public interface TeachingReformRecordService extends IService<TeachingReformRecord> {

    /**
     * 提交教改科研项目业绩记录
     */
    Long addRecord(Long userId, String projectName,
                   String projectType, String projectStatus, String projectLeader,
                   String memberData, String scoreData, MultipartFile file);

    /**
     * 获取查询条件
     */
    QueryWrapper getQueryWrapper(TeachingReformQueryRequest req);

    /**
     * 获取记录VO
     */
    TeachingReformRecordVO getRecordVO(TeachingReformRecord record);

    /**
     * 分页查询记录
     */
    Page<TeachingReformRecordVO> pageRecords(TeachingReformQueryRequest req);

    /**
     * 分页查询我的相关记录（按得分表匹配）
     */
    Page<TeachingReformRecordVO> pageMyRelatedRecords(Long userId, TeachingReformQueryRequest req);

    /**
     * 获取我的总得分
     */
    BigDecimal getMyTotalScore(Long userId);

    /**
     * 管理员审核
     */
    void adminReview(Long recordId, String reviewStatus, String reviewComment, Long adminId);

    /**
     * 导出教改科研项目业绩得分Excel
     */
    byte[] exportRecordsToExcel();
}
