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
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 大创业绩得分明细（v6 新增）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("innovation_entrepreneurship_score")
public class InnovationEntrepreneurshipScore implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id(keyType = KeyType.Auto)
    private Long id;

    /** 关联记录ID */
    @Column("record_id")
    private Long recordId;

    /** 教师用户ID */
    @Column("user_id")
    private Long userId;

    /** 教师姓名（冗余，方便导出） */
    @Column("teacher_name")
    private String teacherName;

    /** 得分 */
    @Column("score")
    private BigDecimal score;

    /** 是否负责人：1是0否 */
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
