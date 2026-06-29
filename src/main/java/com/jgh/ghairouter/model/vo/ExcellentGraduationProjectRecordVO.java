package com.jgh.ghairouter.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 优秀毕设记录视图（v16 新增）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExcellentGraduationProjectRecordVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private String typeName;
    private Long userId;
    private String userName;
    private String major;
    private String studentId;
    private String studentName;
    private String projectTitle;
    private String advisorName;
    private Integer rank;

    /** 证明图片（base64数据） */
    private String proofImageData;

    /** 自动审核状态 */
    private String autoReviewStatus;
    /** 自动审核意见 */
    private String autoReviewComment;
    /** 管理员审核状态 */
    private String adminReviewStatus;
    /** 管理员审核意见 */
    private String adminReviewComment;
    /** 审核管理员姓名 */
    private String adminName;
    /** 管理员审核时间 */
    private LocalDateTime adminReviewTime;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
