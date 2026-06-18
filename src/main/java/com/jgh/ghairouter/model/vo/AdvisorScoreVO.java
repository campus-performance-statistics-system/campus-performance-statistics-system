package com.jgh.ghairouter.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 指导老师得分 VO（v3）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdvisorScoreVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 教师用户ID */
    private Long userId;

    /** 教师姓名 */
    private String teacherName;

    /** 基础分 */
    private BigDecimal baseScore;

    /** 获奖加分 */
    private BigDecimal bonusScore;

    /** 总得分 */
    private BigDecimal totalScore;

    /** 是否主持者 */
    private Integer isLeader;
}
