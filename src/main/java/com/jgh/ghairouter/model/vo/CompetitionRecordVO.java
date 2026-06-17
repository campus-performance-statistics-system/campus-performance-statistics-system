package com.jgh.ghairouter.model.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 比赛记录 VO（v2 重构：移除分类/活动类型字段，新增 typeName）
 */
@Data
public class CompetitionRecordVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long userId;
    private String userName;
    private String typeName;
    private String competitionName;
    private String sponsorUnit;
    private String competitionRank;
    private String gradeName;
    private BigDecimal baseScore;
    private Integer teamMemberNum;
    private Long firstAuthorId;
    private String firstAuthorName;
    private String otherAuthorIds;
    private String otherAuthorNames;
    private String proofImageData;
    private String autoReviewStatus;
    private String autoReviewComment;
    private String adminReviewStatus;
    private String adminReviewComment;
    private String adminName;
    private LocalDateTime adminReviewTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    /** 团队成员得分明细 */
    private List<TeacherScoreVO> teacherScores;

    /** 当前用户在该记录中的得分 */
    private BigDecimal myScore;
}
