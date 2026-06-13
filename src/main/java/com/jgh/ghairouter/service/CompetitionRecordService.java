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

    Long addRecord(Long userId, Long categoryId, Long activityTypeId,
                   Long rankGradeScoreId, String competitionName, String sponsorUnit,
                   Integer teamMemberNum, Long firstAuthorId,
                   List<Long> otherAuthorIds, MultipartFile file);

    QueryWrapper getQueryWrapper(CompetitionQueryRequest queryRequest);

    CompetitionRecordVO getRecordVO(CompetitionRecord record);

    Page<CompetitionRecordVO> pageRecords(CompetitionQueryRequest queryRequest);

    void adminReview(Long recordId, String reviewStatus, String reviewComment, Long adminId);
}
