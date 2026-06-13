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
     * 提交比赛记录（含 multipart 图片文件）
     */
    Long addRecord(Long userId, Long categoryId, String awardLevel,
                   String firstAuthor, List<String> authors, MultipartFile file);

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
