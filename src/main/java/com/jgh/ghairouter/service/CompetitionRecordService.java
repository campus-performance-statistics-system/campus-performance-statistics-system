package com.jgh.ghairouter.service;

import com.jgh.ghairouter.model.dto.competition.CompetitionQueryRequest;
import com.jgh.ghairouter.model.entity.CompetitionRecord;
import com.jgh.ghairouter.model.vo.CompetitionRecordVO;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.service.IService;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 比赛记录 服务层
 */
public interface CompetitionRecordService extends IService<CompetitionRecord> {

    /**
     * 提交比赛记录
     */
    Long addRecord(Long userId, Long categoryId, Long activityTypeId,
                   Long competitionRankId, Long awardGradeId,
                   String competitionName, String sponsorUnit,
                   Integer teamMemberNum, Long firstAuthorId,
                   List<Long> otherAuthorIds, MultipartFile file);

    /**
     * 构建查询条件
     */
    QueryWrapper getQueryWrapper(CompetitionQueryRequest queryRequest);

    /**
     * 获取VO（含关联信息）
     */
    CompetitionRecordVO getRecordVO(CompetitionRecord record);

    /**
     * 获取VO并填充当前用户的个人得分
     */
    CompetitionRecordVO getRecordVO(CompetitionRecord record, Long currentUserId);

    /**
     * 分页查询
     */
    Page<CompetitionRecordVO> pageRecords(CompetitionQueryRequest queryRequest);

    /**
     * 分页查询（含个人得分）
     */
    Page<CompetitionRecordVO> pageRecords(CompetitionQueryRequest queryRequest, Long currentUserId);

    /**
     * 管理员审核
     */
    void adminReview(Long recordId, String reviewStatus, String reviewComment, Long adminId);
}
