package com.jgh.ghairouter.model.dto.competition;

import com.jgh.ghairouter.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 查询比赛记录请求
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class CompetitionQueryRequest extends PageRequest {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long userId;
    private Long categoryId;
    private Long activityTypeId;
    private String competitionName;
    private String autoReviewStatus;
    private String adminReviewStatus;
}
