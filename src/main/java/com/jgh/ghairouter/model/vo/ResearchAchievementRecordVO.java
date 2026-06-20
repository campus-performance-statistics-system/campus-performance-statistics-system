package com.jgh.ghairouter.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 科研及教材业绩记录 视图对象（v5）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResearchAchievementRecordVO {

    private Long id;
    private Long userId;
    private String userName;
    private String typeName;

    /** 子类型 */
    private String subType;
    /** 子类型显示名称 */
    private String subTypeText;

    /** 成果名称 */
    private String achievementName;

    // ==================== 横向科研项目 ====================
    /** 项目来源 */
    private String projectSource;
    /** 到位经费（万元） */
    private BigDecimal fundingAmount;

    // ==================== 专利 ====================
    /** 专利号 */
    private String patentNumber;
    /** 专利类别 */
    private String patentType;
    /** 专利类别显示名称 */
    private String patentTypeText;

    // ==================== 教材 ====================
    /** 字数（万） */
    private BigDecimal wordCount;
    /** 教材类型 */
    private String textbookType;
    /** 教材类型显示名称 */
    private String textbookTypeText;

    /** 项目组成员数据JSON */
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
    private List<ResearchAchievementScoreVO> scores;

    /** 当前用户在此记录中的得分（用于"我的记录"展示） */
    private String myScoreDisplay;

    /** 创建时间 */
    private LocalDateTime createTime;
    /** 更新时间 */
    private LocalDateTime updateTime;
}
