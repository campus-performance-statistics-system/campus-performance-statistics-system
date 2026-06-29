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
 * 校企联合培养记录 实体类（v17 新增）。
 * 每条记录对应一个校企联合培养学生。
 * 纯数据记录，不涉及计分。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("school_enterprise_training_record")
public class SchoolEnterpriseTrainingRecord implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id(keyType = KeyType.Auto)
    private Long id;

    /** 记录类型名称（固定为"校企联合培养"） */
    @Column("type_name")
    private String typeName;

    /** 填报用户ID */
    @Column("user_id")
    private Long userId;

    /** 学生姓名 */
    @Column("student_name")
    private String studentName;

    /** 学号 */
    @Column("student_id")
    private String studentId;

    /** 专业 */
    @Column("major")
    private String major;

    /** 公司名称 */
    @Column("company_name")
    private String companyName;

    /** 备注（3+0.5+0.5或3+1等） */
    @Column("remark")
    private String remark;

    /** 企业毕设收集情况 */
    @Column("project_collection_status")
    private String projectCollectionStatus;

    /** 校内指导老师 */
    @Column("advisor_name")
    private String advisorName;

    /** 校内辅导员 */
    @Column("counselor_name")
    private String counselorName;

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
