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
 * 指导老师得分明细（v3 新增）。
 * 记录每位指导老师在每项学生科技竞赛中的得分。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("advisor_score")
public class AdvisorScore implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id(keyType = KeyType.Generator, value = KeyGenerators.snowFlakeId)
    private Long id;

    /** 关联记录ID */
    @Column("record_id")
    private Long recordId;

    /** 教师用户ID */
    @Column("user_id")
    private Long teacherUserId;

    /** 教师姓名（冗余，方便导出） */
    @Column("teacher_name")
    private String teacherName;

    /** 基础分（组织者/指导者基础分） */
    @Column("base_score")
    private BigDecimal baseScore;

    /** 获奖加分 */
    @Column("bonus_score")
    private BigDecimal bonusScore;

    /** 总得分 = baseScore + bonusScore */
    @Column("total_score")
    private BigDecimal totalScore;

    /** 是否主持者/第一负责人 */
    @Column("is_leader")
    private Integer isLeader;

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
