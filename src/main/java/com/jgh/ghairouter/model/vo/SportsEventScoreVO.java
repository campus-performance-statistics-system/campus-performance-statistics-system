package com.jgh.ghairouter.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 体育比赛业绩得分明细 视图对象（v9）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SportsEventScoreVO {

    /** 教师用户ID */
    private Long userId;

    /** 教师姓名 */
    private String teacherName;

    /** 得分 */
    private BigDecimal score;

    /** 名次（仅运动会项目，1-8） */
    private Integer placement;
}
