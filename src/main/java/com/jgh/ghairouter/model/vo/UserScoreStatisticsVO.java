package com.jgh.ghairouter.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 用户得分统计 VO（管理员统计管理页使用）。
 * totalScore 含义取决于查询分类（教师获奖 / 指导学生科技竞赛 / 所有比赛合计）。
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

    /** 用户工号（账号） */
    private String userAccount;

    /** 用户姓名 */
    private String userName;

    /** 总得分 */
    private BigDecimal totalScore;
}
