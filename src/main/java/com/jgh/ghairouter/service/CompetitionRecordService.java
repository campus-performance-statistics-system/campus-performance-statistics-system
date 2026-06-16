package com.jgh.ghairouter.service;

import com.jgh.ghairouter.model.dto.competition.CompetitionQueryRequest;
import com.jgh.ghairouter.model.entity.CompetitionRecord;
import com.jgh.ghairouter.model.vo.CompetitionRecordVO;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.service.IService;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.List;

/**
 * 比赛记录 服务层
 */
public interface CompetitionRecordService extends IService<CompetitionRecord> {

    /**
     * 用户提交比赛记录
     */
    Long addRecord(Long userId, Long categoryId, Long activityTypeId,
                   String competitionName, String sponsorUnit,
                   String competitionRank, String gradeName, BigDecimal baseScore,
                   Integer teamMemberNum, Long firstAuthorId,
                   List<Long> otherAuthorIds, MultipartFile file);

    QueryWrapper getQueryWrapper(CompetitionQueryRequest queryRequest);

    CompetitionRecordVO getRecordVO(CompetitionRecord record);

    Page<CompetitionRecordVO> pageRecords(CompetitionQueryRequest queryRequest);

    void adminReview(Long recordId, String reviewStatus, String reviewComment, Long adminId);

    /**
     * 管理员直接添加比赛记录（指定得分）
     */
    Long adminAddRecord(Long adminId, String competitionName, String sponsorUnit,
                        Long rankId, String gradeName, BigDecimal baseScore,
                        Integer teamMemberNum, Long firstAuthorId,
                        List<Long> otherAuthorIds, MultipartFile file);

    /**
     * 查询用户相关的所有记录（含自己提交的 + 作为团队成员被共享的）
     */
    Page<CompetitionRecordVO> pageMyRelatedRecords(Long userId, CompetitionQueryRequest req);

    /**
     * 获取用户总得分
     */
    BigDecimal getMyTotalScore(Long userId);

    /**
     * 导出所有记录为Excel
     */
    byte[] exportRecordsToExcel();
}
