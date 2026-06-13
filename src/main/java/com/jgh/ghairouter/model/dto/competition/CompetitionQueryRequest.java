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

    /**
     * 记录ID
     */
    private Long id;

    /**
     * 用户ID（管理员查询时使用）
     */
    private Long userId;

    /**
     * 分类ID
     */
    private Long categoryId;

    /**
     * 活动类型ID
     */
    private Long activityTypeId;

    /**
     * 比赛名称（模糊搜索）
     */
    private String competitionName;

    /**
     * 自动审核状态
     */
    private String autoReviewStatus;

    /**
     * 管理员审核状态
     */
    private String adminReviewStatus;
}
