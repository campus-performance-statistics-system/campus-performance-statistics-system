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
 * 指导实训记录 实体类（v4 新增）。
 * 记录教师指导实训课程的情况。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("training_guidance_record")
public class TrainingGuidanceRecord implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id(keyType = KeyType.Auto)
    private Long id;

    /** 记录类型名称（固定为"指导实训"） */
    @Column("type_name")
    private String typeName;

    /** 填报用户ID */
    @Column("user_id")
    private Long userId;

    /** 学期，如 "2022-2023（2）" */
    @Column("semester")
    private String semester;

    /** 实训名称 */
    @Column("training_name")
    private String trainingName;

    /** 负责教师JSON数组：[{"teacherName":"秦小旭"},...] */
    @Column("responsible_teachers")
    private String responsibleTeachers;

    /** 参与教师JSON数组：[{"teacherName":"李志平"},...] */
    @Column("participating_teachers")
    private String participatingTeachers;

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
