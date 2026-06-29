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
 * 优秀毕设记录 实体类（v16 新增）。
 * 每条记录对应一个优秀毕业设计。
 * 纯数据记录，不涉及计分。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("excellent_graduation_project_record")
public class ExcellentGraduationProjectRecord implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id(keyType = KeyType.Auto)
    private Long id;

    /** 记录类型名称（固定为"优秀毕设"） */
    @Column("type_name")
    private String typeName;

    /** 填报用户ID */
    @Column("user_id")
    private Long userId;

    /** 专业 */
    @Column("major")
    private String major;

    /** 学号 */
    @Column("student_id")
    private String studentId;

    /** 学生姓名 */
    @Column("student_name")
    private String studentName;

    /** 毕设题目 */
    @Column("project_title")
    private String projectTitle;

    /** 指导教师 */
    @Column("advisor_name")
    private String advisorName;

    /** 名次 */
    @Column("rank")
    private Integer rank;

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
