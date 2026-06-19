package com.jgh.ghairouter.model.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 指导学生科技竞赛记录 实体类（v3 新增）。
 * 区别于教师比赛记录，本表记录教师指导学生参加科技竞赛的情况。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("student_competition_record")
public class StudentCompetitionRecord implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id(keyType = KeyType.Auto)
    private Long id;

    /** 记录类型名称（固定为"指导学生科技竞赛"） */
    @Column("type_name")
    private String typeName;

    /** 填报用户ID */
    @Column("user_id")
    private Long userId;

    /** 竞赛名称 */
    @Column("competition_name")
    private String competitionName;

    /** 主办单位 */
    @Column("sponsor_unit")
    private String sponsorUnit;

    /** 参赛题目/赛道（组织者行填"组织者"） */
    @Column("competition_topic")
    private String competitionTopic;

    /** 参赛队员姓名（分隔符根据实际情况，如：黄宇、陈纪光） */
    @Column("student_names")
    private String studentNames;

    /** 竞赛等级：院级/校级/区级/自治区级/国家级/行业性全国/行业性省级 */
    @Column("competition_rank")
    private String competitionRank;

    /** 获奖等级：一等奖/二等奖/三等奖/优秀奖/未获奖/奖项未出 */
    @Column("grade_name")
    private String gradeName;

    /** 完整获奖级别文本（如"国赛二等奖、省赛一等奖"），用于导出展示 */
    @Column("award_level_text")
    private String awardLevelText;

    /** 获奖明细JSON数组：[{"rank":"国家级","grade":"二等奖"},{"rank":"自治区级","grade":"一等奖"}] */
    @Column("award_details")
    private String awardDetails;

    /** 是否为组织者行（组织者独占一行，参赛题目填"组织者"） */
    @Column("is_organizer")
    private Integer isOrganizer;

    /** 指导老师得分JSON数组，格式：[{"teacherName":"秦小旭","baseScore":2,"bonusScore":7,"totalScore":9,"isLeader":true},...] */
    @Column("advisor_score_data")
    private String advisorScoreData;

    /** 参赛/获奖证明图片（base64数据） */
    @Column("proof_image_data")
    private String proofImageData;

    /** 创建时间 */
    @Column("create_time")
    private LocalDateTime createTime;

    /** 更新时间 */
    @Column("update_time")
    private LocalDateTime updateTime;

    /** 是否删除 */
    @Column(value = "is_delete", isLogicDelete = true)
    private Integer isDelete;
}
