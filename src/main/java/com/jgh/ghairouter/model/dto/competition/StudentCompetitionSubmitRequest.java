package com.jgh.ghairouter.model.dto.competition;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 提交指导学生科技竞赛记录请求（v3）
 */
@Data
public class StudentCompetitionSubmitRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 竞赛名称 */
    private String competitionName;

    /** 主办单位 */
    private String sponsorUnit;

    /** 参赛题目/赛道（组织者行填"组织者"） */
    private String competitionTopic;

    /** 参赛队员姓名 */
    private String studentNames;

    /** 竞赛等级 */
    private String competitionRank;

    /** 获奖等级 */
    private String gradeName;

    /** 完整获奖级别文本 */
    private String awardLevelText;

    /** 是否为组织者行 */
    private Integer isOrganizer;

    /** 指导老师得分JSON */
    private String advisorScoreData;
}
