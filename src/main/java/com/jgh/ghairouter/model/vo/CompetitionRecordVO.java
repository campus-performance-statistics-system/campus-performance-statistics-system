package com.jgh.ghairouter.model.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 比赛记录 VO（含关联信息）
 */
@Data
public class CompetitionRecordVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long userId;
    private String userName;
    private Long categoryId;
    private String categoryName;
    private Long activityTypeId;
    private String activityTypeName;
    private Long competitionRankId;
    private String competitionRankName;
    private Long awardGradeId;
    private String awardGradeName;
    private String competitionName;
    private String sponsorUnit;
    private Integer teamMemberNum;
    private Long distributeRuleId;
    private String distributeRuleDesc;
    private Long firstAuthorId;
    private String firstAuthorName;
    private String otherAuthorIds;
    private String otherAuthorNames;
    private String proofImageData;
    private String autoReviewStatus;
    private String autoReviewComment;
    private String adminReviewStatus;
    private String adminReviewComment;
    private Long adminId;
    private String adminName;
    private LocalDateTime adminReviewTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    // === 计分相关 ===
    private BigDecimal baseScore;
    // === 个人得分（仅当前登录用户）===
    private BigDecimal personalScore;
}
