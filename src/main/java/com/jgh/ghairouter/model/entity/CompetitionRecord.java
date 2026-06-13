package com.jgh.ghairouter.model.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import com.mybatisflex.core.keygen.KeyGenerators;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 比赛记录 实体类
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("competition_record")
public class CompetitionRecord implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id(keyType = KeyType.Generator, value = KeyGenerators.snowFlakeId)
    private Long id;

    /** 填报用户ID */
    @Column("user_id")
    private Long userId;

    /** 比赛大类ID */
    @Column("category_id")
    private Long categoryId;

    /** 活动细分类型ID */
    @Column("activity_type_id")
    private Long activityTypeId;

    /** 竞赛等级+获奖等级计分规则ID */
    @Column("rank_grade_score_id")
    private Long rankGradeScoreId;

    /** 比赛全称 */
    @Column("competition_name")
    private String competitionName;

    /** 颁奖/主办单位 */
    @Column("sponsor_unit")
    private String sponsorUnit;

    /** 参赛总人数 */
    @Column("team_member_num")
    private Integer teamMemberNum;

    /** 分数分配规则ID */
    @Column("distribute_rule_id")
    private Long distributeRuleId;

    /** 第一负责人用户ID */
    @Column("first_author_id")
    private Long firstAuthorId;

    /** 其他参赛教师ID（逗号分隔） */
    @Column("other_author_ids")
    private String otherAuthorIds;

    /** 参赛/获奖证明图片（base64数据） */
    @Column("proof_image_data")
    private String proofImageData;

    /** 自动审核状态 */
    @Column("auto_review_status")
    private String autoReviewStatus;

    /** AI自动审核分析意见 */
    @Column("auto_review_comment")
    private String autoReviewComment;

    /** 管理员审核状态 */
    @Column("admin_review_status")
    private String adminReviewStatus;

    /** 管理员审核意见 */
    @Column("admin_review_comment")
    private String adminReviewComment;

    /** 审核管理员ID */
    @Column("admin_id")
    private Long adminId;

    /** 管理员审核时间 */
    @Column("admin_review_time")
    private LocalDateTime adminReviewTime;

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
