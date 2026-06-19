package com.jgh.ghairouter.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 用户得分统计 VO（管理员统计管理页使用）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserScoreStatisticsVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 用户ID */
    private Long userId;

    /** 用户姓名 */
    private String userName;

    /** 教师获奖总得分 */
    private BigDecimal teacherScore;

    /** 指导学生科技竞赛总得分 */
    private BigDecimal studentScore;

    /** 总分（教师获奖 + 学生竞赛） */
    private BigDecimal totalScore;
}
