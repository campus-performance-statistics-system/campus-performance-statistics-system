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
 * 大创业绩记录 实体类（v6 新增）。
 * 包含大学生创新创业训练计划项目（国家级/区级，创新训练/创业训练/创业实践）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("innovation_entrepreneurship_record")
public class InnovationEntrepreneurshipRecord implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id(keyType = KeyType.Auto)
    private Long id;

    /** 记录类型名称（固定为"大创业绩"） */
    @Column("type_name")
    private String typeName;

    /** 填报用户ID */
    @Column("user_id")
    private Long userId;

    /** 项目编号 */
    @Column("project_number")
    private String projectNumber;

    /** 项目名称 */
    @Column("project_name")
    private String projectName;

    /** 项目级别：national-国家级, regional-区级 */
    @Column("project_level")
    private String projectLevel;

    /** 项目类型：innovation_training-创新训练, entrepreneurship_training-创业训练, entrepreneurship_practice-创业实践 */
    @Column("project_type")
    private String projectType;

    /** 项目状态：concluded-结题, newly_added-新增 */
    @Column("project_status")
    private String projectStatus;

    /** 项目负责人（学生姓名） */
    @Column("student_leader")
    private String studentLeader;

    /** 指导教师成员及得分分配JSON数组 */
    @Column("member_data")
    private String memberData;

    /** 得分明细JSON数组 */
    @Column("score_data")
    private String scoreData;

    /** 证明图片（base64数据） */
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
