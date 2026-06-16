package com.jgh.ghairouter.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 教师个人得分展示
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeacherScoreVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 用户ID */
    private Long userId;

    /** 用户姓名 */
    private String userName;

    /** 个人得分 */
    private BigDecimal personalScore;

    /** 是否负责人 */
    private Integer isLeader;
}
