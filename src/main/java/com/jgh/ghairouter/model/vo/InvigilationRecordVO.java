package com.jgh.ghairouter.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 监考次数统计记录视图（v11 新增）。
 * 纯数据记录，不涉及计分。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvigilationRecordVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private String typeName;
    private Long userId;
    private String userName;
    private String teacherName;
    private Integer invigilationCount;

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
