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
 * 教师比赛审核记录 实体类（v2 重构：从 competition_record 拆分出的审核字段）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("competition_audit_record")
public class TeacherCompetitionAuditRecord implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id(keyType = KeyType.Generator, value = KeyGenerators.snowFlakeId)
    private Long id;

    /** 关联比赛记录ID */
    @Column("record_id")
    private Long recordId;

    /** 记录类型名称 */
    @Column("record_type")
    private String recordType;

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
