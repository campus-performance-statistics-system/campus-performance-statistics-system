package com.jgh.ghairouter.service;

import com.jgh.ghairouter.model.dto.competition.StudentCompetitionQueryRequest;
import com.jgh.ghairouter.model.entity.StudentCompetitionRecord;
import com.jgh.ghairouter.model.vo.StudentCompetitionRecordVO;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.service.IService;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;

/**
 * 指导学生科技竞赛记录 服务层（v3）
 */
public interface StudentCompetitionRecordService extends IService<StudentCompetitionRecord> {

    /**
     * 提交记录
     */
    Long addRecord(Long userId,
                   String competitionName, String sponsorUnit,
                   String competitionTopic, String studentNames,
                   String competitionRank, String gradeName, String awardLevelText,
                   String awardDetails,
                   Integer isOrganizer, String advisorScoreData,
                   MultipartFile file);

    QueryWrapper getQueryWrapper(StudentCompetitionQueryRequest queryRequest);

    StudentCompetitionRecordVO getRecordVO(StudentCompetitionRecord record);

    Page<StudentCompetitionRecordVO> pageRecords(StudentCompetitionQueryRequest queryRequest);

    Page<StudentCompetitionRecordVO> pageMyRelatedRecords(Long userId, StudentCompetitionQueryRequest req);

    BigDecimal getMyTotalScore(Long userId);

    void adminReview(Long recordId, String reviewStatus, String reviewComment, Long adminId);

    /**
     * 导出所有记录为Excel（v3格式）
     */
    byte[] exportRecordsToExcel();
}
