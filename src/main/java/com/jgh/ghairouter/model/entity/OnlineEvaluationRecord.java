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
 * 网上评教记录 实体类（v12 新增）。
 * 每条记录对应一个教师的一门课程的学生网上评教数据。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("online_evaluation_record")
public class OnlineEvaluationRecord implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id(keyType = KeyType.Auto)
    private Long id;

    /** 记录类型名称（固定为"网上评教"） */
    @Column("type_name")
    private String typeName;

    /** 填报用户ID */
    @Column("user_id")
    private Long userId;

    /** 教师姓名 */
    @Column("teacher_name")
    private String teacherName;

    /** 教师类型：专任教师/外聘教师 */
    @Column("teacher_type")
    private String teacherType;

    /** 学年（如"2022-2023"） */
    @Column("academic_year")
    private String academicYear;

    /** 学期（如"第一学期"/"第二学期"） */
    @Column("semester")
    private String semester;

    /** 课程序号 */
    @Column("course_code")
    private String courseCode;

    /** 课程名称 */
    @Column("course_name")
    private String courseName;

    /** 参评人数 */
    @Column("participant_count")
    private Integer participantCount;

    /** 平均分 */
    @Column("average_score")
    private BigDecimal averageScore;

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
