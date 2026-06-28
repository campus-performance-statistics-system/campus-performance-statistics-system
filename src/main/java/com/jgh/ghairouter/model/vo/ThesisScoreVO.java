package com.jgh.ghairouter.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 论文业绩得分明细 视图对象（v8）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ThesisScoreVO {

    /** 教师用户ID */
    private Long userId;

    /** 教师姓名 */
    private String teacherName;

    /** 得分 */
    private BigDecimal score;

    /** 是否第一作者：1是0否 */
    private Integer isFirstAuthor;
}
