package com.jgh.ghairouter.model.dto.competition;

import com.jgh.ghairouter.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 查询指导学生科技竞赛记录请求（v3）
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class StudentCompetitionQueryRequest extends PageRequest {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long userId;
    private String competitionName;
    private String adminReviewStatus;
}
