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
 * 指导青年教师记录 实体类（v15 新增）。
 * 每条记录对应一个指导青年教师的情况。
 * 纯数据记录，不涉及计分。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("young_teacher_guidance_record")
public class YoungTeacherGuidanceRecord implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id(keyType = KeyType.Auto)
    private Long id;

    /** 记录类型名称（固定为"指导青年教师"） */
    @Column("type_name")
    private String typeName;

    /** 填报用户ID */
    @Column("user_id")
    private Long userId;

    /** 教学单位（学院） */
    @Column("college")
    private String college;

    /** 指导教师-姓名（多个用、分隔） */
    @Column("mentor_names")
    private String mentorNames;

    /** 指导教师-职称（多个用、分隔，与姓名一一对应） */
    @Column("mentor_titles")
    private String mentorTitles;

    /** 青年教师-姓名 */
    @Column("young_teacher_name")
    private String youngTeacherName;

    /** 青年教师-入职时间（格式 YYYY.MM） */
    @Column("young_teacher_entry_time")
    private String youngTeacherEntryTime;

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
