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
 * 教改科研项目业绩记录 实体类（v7 新增）。
 * 包含教育厅教改工程项目、中青年教师基础能力提升项目、校级科研项目、校级课程思政项目。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("teaching_reform_record")
public class TeachingReformRecord implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id(keyType = KeyType.Auto)
    private Long id;

    /** 记录类型名称（固定为"教改科研项目业绩"） */
    @Column("type_name")
    private String typeName;

    /** 填报用户ID */
    @Column("user_id")
    private Long userId;

    /** 项目名称 */
    @Column("project_name")
    private String projectName;

    /** 项目类型：provincial_education_reform-教育厅教改工程, young_teacher_basic-中青年教师基础能力提升,
     *  university_research-校级科研, university_course_ideology-校级课程思政 */
    @Column("project_type")
    private String projectType;

    /** 项目状态：approved-获批立项, not_approved-未获批, pending_decision-未下文 */
    @Column("project_status")
    private String projectStatus;

    /** 项目负责人（教师姓名） */
    @Column("project_leader")
    private String projectLeader;

    /** 项目组成员及得分分配JSON数组 */
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
