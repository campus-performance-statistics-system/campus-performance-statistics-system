package com.jgh.ghairouter.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 指导实训记录 视图对象（v4）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrainingGuidanceRecordVO {

    private Long id;
    private Long userId;
    private String userName;
    private String typeName;

    /** 学期 */
    private String semester;

    /** 实训名称 */
    private String trainingName;

    /** 负责教师JSON */
    private String responsibleTeachers;

    /** 参与教师JSON */
    private String participatingTeachers;

    /** 证明图片（base64） */
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

    /** 得分明细列表 */
    private List<TrainingGuidanceScoreVO> scores;

    /** 当前用户在此记录中的得分（用于"我的记录"展示） */
    private String myScoreDisplay;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
