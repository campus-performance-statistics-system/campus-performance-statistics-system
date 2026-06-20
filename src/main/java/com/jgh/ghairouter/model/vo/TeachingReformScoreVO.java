package com.jgh.ghairouter.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 教改科研项目业绩得分明细 视图对象（v7）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeachingReformScoreVO {

    /** 教师用户ID */
    private Long userId;

    /** 教师姓名 */
    private String teacherName;

    /** 得分 */
    private BigDecimal score;

    /** 是否负责人：1是0否 */
    private Integer isLeader;
}
