package com.jgh.ghairouter.service;

import com.jgh.ghairouter.model.dto.competition.ResearchAchievementQueryRequest;
import com.jgh.ghairouter.model.entity.ResearchAchievementRecord;
import com.jgh.ghairouter.model.vo.ResearchAchievementRecordVO;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.service.IService;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;

/**
 * 科研及教材业绩记录 服务层（v5）
 */
public interface ResearchAchievementRecordService extends IService<ResearchAchievementRecord> {

    /**
     * 提交记录
     */
    Long addRecord(Long userId,
                   String subType,
                   String achievementName,
                   String projectSource,
                   BigDecimal fundingAmount,
                   String patentNumber,
                   String patentType,
                   BigDecimal wordCount,
                   String textbookType,
                   String memberData,
                   String scoreData,
                   MultipartFile file);

    QueryWrapper getQueryWrapper(ResearchAchievementQueryRequest queryRequest);

    ResearchAchievementRecordVO getRecordVO(ResearchAchievementRecord record);

    Page<ResearchAchievementRecordVO> pageRecords(ResearchAchievementQueryRequest queryRequest);

    Page<ResearchAchievementRecordVO> pageMyRelatedRecords(Long userId, ResearchAchievementQueryRequest req);

    BigDecimal getMyTotalScore(Long userId);

    void adminReview(Long recordId, String reviewStatus, String reviewComment, Long adminId);

    /**
     * 导出所有记录为Excel
     */
    byte[] exportRecordsToExcel();
}
