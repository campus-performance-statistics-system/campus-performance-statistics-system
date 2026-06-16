package com.jgh.ghairouter.model.dto.competition;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 管理员添加比赛记录请求
 */
@Data
public class AdminAddCompetitionRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 比赛全称 */
    private String competitionName;

    /** 颁奖/主办单位 */
    private String sponsorUnit;

    /** 竞赛等级ID（关联competition_rank） */
    private Long rankId;

    /** 获奖等级名称：一等奖/二等奖/三等奖/优秀奖/未获奖 */
    private String gradeName;

    /** 基础总分 */
    private BigDecimal baseScore;

    /** 参赛总人数 */
    private Integer teamMemberNum;

    /** 第一负责人用户ID */
    private Long firstAuthorId;

    /** 其他参赛教师ID列表 */
    private List<Long> otherAuthorIds;
}
