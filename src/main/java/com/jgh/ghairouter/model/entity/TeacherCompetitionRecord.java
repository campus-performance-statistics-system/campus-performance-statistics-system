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
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 教师比赛记录 实体类（v2 重构：由 competition_record 重命名而来）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("teacher_competition_record")
public class TeacherCompetitionRecord implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id(keyType = KeyType.Auto)
    private Long id;

    /** 记录类型名称 */
    @Column("type_name")
    private String typeName;

    /** 填报用户ID */
    @Column("user_id")
    private Long userId;

    /** 比赛全称 */
    @Column("competition_name")
    private String competitionName;

    /** 颁奖/主办单位 */
    @Column("sponsor_unit")
    private String sponsorUnit;

    /** 获奖级别：校级/区级/国家级 */
    @Column("competition_rank")
    private String competitionRank;

    /** 等级：一等奖/二等奖/三等奖/优秀奖/未获奖 */
    @Column("grade_name")
    private String gradeName;

    /** 基础总分 */
    @Column("base_score")
    private BigDecimal baseScore;

    /** 参赛总人数 */
    @Column("team_member_num")
    private Integer teamMemberNum;

    /** 第一负责人用户ID */
    @Column("first_author_id")
    private Long firstAuthorId;

    /** 其他参赛教师ID（逗号分隔） */
    @Column("other_author_ids")
    private String otherAuthorIds;

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
