package com.jgh.ghairouter.model.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
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
    private String competitionName;
    private String proofImageUrl;
    private String autoReviewStatus;
    private String autoReviewComment;
    private String adminReviewStatus;
    private String adminReviewComment;
    private Long adminId;
    private String adminName;
    private LocalDateTime adminReviewTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
