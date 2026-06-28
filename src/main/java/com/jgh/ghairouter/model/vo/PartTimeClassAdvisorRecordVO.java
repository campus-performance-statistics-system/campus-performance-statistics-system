package com.jgh.ghairouter.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 兼职班主任业绩记录 视图对象（v10）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PartTimeClassAdvisorRecordVO {

    private Long id;
    private Long userId;
    private String userName;
    private String typeName;

    /** 教师姓名 */
    private String teacherName;
    /** 负责班级 */
    private String classId;

    /** 学风建设-工作要求 */
    private BigDecimal studyStyleWorkReq;
    /** 学风建设-效果评估 */
    private BigDecimal studyStyleEffect;
    /** 安全教育-工作要求 */
    private BigDecimal safetyEduWorkReq;
    /** 安全教育-效果评估 */
    private BigDecimal safetyEduEffect;
    /** 后进生帮扶-工作要求 */
    private BigDecimal strugglingStudentWorkReq;
    /** 后进生帮扶-效果评估 */
    private BigDecimal strugglingStudentEffect;
    /** 育人成果-安全稳定 */
    private BigDecimal achievementSafety;
    /** 育人成果-学风建设 */
    private BigDecimal achievementStudyStyle;
    /** 育人成果-后进生帮扶 */
    private BigDecimal achievementStruggling;

    /** 行总得分 */
    private BigDecimal rowTotalScore;
    /** 行换算得分 */
    private BigDecimal rowConvertedScore;

    /** 行政班分 */
    private BigDecimal adminClassScore;
    /** 是否新生或毕业班 */
    private Integer isFreshmenOrGraduating;

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
    private List<PartTimeClassAdvisorScoreVO> scores;

    /** 当前用户在此记录中的得分（用于"我的记录"展示） */
    private String myScoreDisplay;

    /** 创建时间 */
    private LocalDateTime createTime;
    /** 更新时间 */
    private LocalDateTime updateTime;
}
