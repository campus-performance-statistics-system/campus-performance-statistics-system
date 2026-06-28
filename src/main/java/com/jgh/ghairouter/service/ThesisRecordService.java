package com.jgh.ghairouter.service;

import com.jgh.ghairouter.model.dto.competition.ThesisQueryRequest;
import com.jgh.ghairouter.model.entity.ThesisRecord;
import com.jgh.ghairouter.model.vo.ThesisRecordVO;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.service.IService;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;

/**
 * 论文业绩记录服务接口（v8）
 */
public interface ThesisRecordService extends IService<ThesisRecord> {

    /**
     * 提交论文业绩记录
     */
    Long addRecord(Long userId, String thesisName, String journalName,
                   String thesisLevel, String authorsData, String scoreData,
                   MultipartFile file);

    /**
     * 获取查询条件
     */
    QueryWrapper getQueryWrapper(ThesisQueryRequest req);

    /**
     * 获取记录VO
     */
    ThesisRecordVO getRecordVO(ThesisRecord record);

    /**
     * 分页查询记录
     */
    Page<ThesisRecordVO> pageRecords(ThesisQueryRequest req);

    /**
     * 分页查询我的相关记录（按得分表匹配）
     */
    Page<ThesisRecordVO> pageMyRelatedRecords(Long userId, ThesisQueryRequest req);

    /**
     * 获取我的总得分
     */
    BigDecimal getMyTotalScore(Long userId);

    /**
     * 管理员审核
     */
    void adminReview(Long recordId, String reviewStatus, String reviewComment, Long adminId);

    /**
     * 导出论文业绩得分Excel
     */
    byte[] exportRecordsToExcel();
}
