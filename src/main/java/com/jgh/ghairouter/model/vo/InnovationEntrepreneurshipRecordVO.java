package com.jgh.ghairouter.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 大创业绩记录 视图对象（v6）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InnovationEntrepreneurshipRecordVO {

    private Long id;
    private Long userId;
    private String userName;
    private String typeName;

    /** 项目编号 */
    private String projectNumber;
    /** 项目名称 */
    private String projectName;
    /** 项目级别 */
    private String projectLevel;
    /** 项目级别显示名称 */
    private String projectLevelText;
    /** 项目类型 */
    private String projectType;
    /** 项目类型显示名称 */
    private String projectTypeText;
    /** 项目状态 */
    private String projectStatus;
    /** 项目状态显示名称 */
    private String projectStatusText;
    /** 项目负责人（学生） */
    private String studentLeader;

    /** 指导教师成员数据JSON */
    private String memberData;
    /** 得分明细JSON */
    private String scoreData;

    /** 证明图片base64 */
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
    private List<InnovationEntrepreneurshipScoreVO> scores;

    /** 当前用户在此记录中的得分（用于"我的记录"展示） */
    private String myScoreDisplay;

    /** 创建时间 */
    private LocalDateTime createTime;
    /** 更新时间 */
    private LocalDateTime updateTime;
}
