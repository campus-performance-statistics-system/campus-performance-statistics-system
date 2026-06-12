package com.jgh.ghairouter.service;

import com.jgh.ghairouter.model.dto.competition.CompetitionQueryRequest;
import com.jgh.ghairouter.model.entity.CompetitionRecord;
import com.jgh.ghairouter.model.vo.CompetitionRecordVO;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.service.IService;

/**
 * 比赛记录 服务层
 */
public interface CompetitionRecordService extends IService<CompetitionRecord> {

    /**
     * 提交比赛记录（参赛人员）
     */
    Long addRecord(Long userId, String competitionName, Long categoryId, String proofImageUrl);

    /**
     * 根据查询条件构造查询参数
     */
    QueryWrapper getQueryWrapper(CompetitionQueryRequest queryRequest);

    /**
     * 获取比赛记录VO
     */
    CompetitionRecordVO getRecordVO(CompetitionRecord record);

    /**
     * 分页查询比赛记录（含关联信息）
     */
    Page<CompetitionRecordVO> pageRecords(CompetitionQueryRequest queryRequest);

    /**
     * 管理员审核
     */
    void adminReview(Long recordId, String reviewStatus, String reviewComment, Long adminId);
}
