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
 * 指导实训得分明细（v4 新增）。
 * 记录每位教师在每项指导实训中的得分。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("training_guidance_score")
public class TrainingGuidanceScore implements Serializable {

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

    /** 得分（负责教师2分，参与教师1分） */
    @Column("score")
    private BigDecimal score;

    /** 角色类型：responsible-负责教师, participating-参与教师 */
    @Column("role_type")
    private String roleType;

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
