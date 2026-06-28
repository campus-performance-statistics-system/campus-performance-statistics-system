package com.jgh.ghairouter.service;

import com.jgh.ghairouter.model.dto.competition.SportsEventQueryRequest;
import com.jgh.ghairouter.model.entity.SportsEventRecord;
import com.jgh.ghairouter.model.vo.SportsEventRecordVO;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.service.IService;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;

/**
 * 体育比赛业绩记录服务接口（v9）
 */
public interface SportsEventRecordService extends IService<SportsEventRecord> {

    /**
     * 提交体育比赛业绩记录
     */
    Long addRecord(Long userId, String eventName, String eventType, String eventResult,
                   String memberData, String scoreData, MultipartFile file);

    /**
     * 获取查询条件
     */
    QueryWrapper getQueryWrapper(SportsEventQueryRequest req);

    /**
     * 获取记录VO
     */
    SportsEventRecordVO getRecordVO(SportsEventRecord record);

    /**
     * 分页查询记录
     */
    Page<SportsEventRecordVO> pageRecords(SportsEventQueryRequest req);

    /**
     * 分页查询我的相关记录（按得分表匹配）
     */
    Page<SportsEventRecordVO> pageMyRelatedRecords(Long userId, SportsEventQueryRequest req);

    /**
     * 获取我的总得分
     */
    BigDecimal getMyTotalScore(Long userId);

    /**
     * 管理员审核
     */
    void adminReview(Long recordId, String reviewStatus, String reviewComment, Long adminId);

    /**
     * 导出体育比赛业绩得分Excel
     */
    byte[] exportRecordsToExcel();
}
