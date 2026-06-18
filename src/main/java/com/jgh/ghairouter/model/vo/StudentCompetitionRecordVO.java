package com.jgh.ghairouter.model.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 指导学生科技竞赛记录 VO（v3）
 */
@Data
public class StudentCompetitionRecordVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long userId;
    private String userName;
    private String typeName;
    private String competitionName;
    private String sponsorUnit;
    private String competitionTopic;
    private String studentNames;
    private String competitionRank;
    private String gradeName;
    private String awardLevelText;
    private Integer isOrganizer;

    /** 指导老师得分明细 */
    private List<AdvisorScoreVO> advisorScores;

    /** 证明图片 */
    private String proofImageData;

    /** 审核信息 */
    private String autoReviewStatus;
    private String autoReviewComment;
    private String adminReviewStatus;
    private String adminReviewComment;
    private String adminName;
    private LocalDateTime adminReviewTime;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    /** 当前用户在该记录中的得分 */
    private String myScoreDisplay;
}
