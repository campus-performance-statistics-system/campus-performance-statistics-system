package com.jgh.ghairouter.service;

import com.jgh.ghairouter.model.dto.competition.InnovationEntrepreneurshipQueryRequest;
import com.jgh.ghairouter.model.entity.InnovationEntrepreneurshipRecord;
import com.jgh.ghairouter.model.vo.InnovationEntrepreneurshipRecordVO;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.service.IService;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;

/**
 * 大创业绩记录服务接口（v6）
 */
public interface InnovationEntrepreneurshipRecordService extends IService<InnovationEntrepreneurshipRecord> {

    /**
     * 提交大创业绩记录
     */
    Long addRecord(Long userId, String projectNumber, String projectName,
                   String projectLevel, String projectType, String projectStatus,
                   String studentLeader,
                   String memberData, String scoreData, MultipartFile file);

    /**
     * 获取查询条件
     */
    QueryWrapper getQueryWrapper(InnovationEntrepreneurshipQueryRequest req);

    /**
     * 获取记录VO
     */
    InnovationEntrepreneurshipRecordVO getRecordVO(InnovationEntrepreneurshipRecord record);

    /**
     * 分页查询记录
     */
    Page<InnovationEntrepreneurshipRecordVO> pageRecords(InnovationEntrepreneurshipQueryRequest req);

    /**
     * 分页查询我的相关记录（按得分表匹配）
     */
    Page<InnovationEntrepreneurshipRecordVO> pageMyRelatedRecords(Long userId, InnovationEntrepreneurshipQueryRequest req);

    /**
     * 获取我的总得分
     */
    BigDecimal getMyTotalScore(Long userId);

    /**
     * 管理员审核
     */
    void adminReview(Long recordId, String reviewStatus, String reviewComment, Long adminId);

    /**
     * 导出大创业绩得分Excel
     */
    byte[] exportRecordsToExcel();
}
