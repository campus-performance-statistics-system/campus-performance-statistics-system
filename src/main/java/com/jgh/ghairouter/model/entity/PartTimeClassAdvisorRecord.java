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
 * 兼职班主任业绩记录 实体类（v10 新增）。
 * 每条记录对应一个教师负责一个行政班的考核评分。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("part_time_class_advisor_record")
public class PartTimeClassAdvisorRecord implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id(keyType = KeyType.Auto)
    private Long id;

    /** 记录类型名称（固定为"兼职班主任"） */
    @Column("type_name")
    private String typeName;

    /** 填报用户ID */
    @Column("user_id")
    private Long userId;

    /** 教师姓名 */
    @Column("teacher_name")
    private String teacherName;

    /** 负责班级编号 */
    @Column("class_id")
    private String classId;

    // ==================== 学风建设（30分） ====================

    /** 学风建设-工作要求（满分20） */
    @Column("study_style_work_req")
    private BigDecimal studyStyleWorkReq;

    /** 学风建设-效果评估（满分10） */
    @Column("study_style_effect")
    private BigDecimal studyStyleEffect;

    // ==================== 安全教育（30分） ====================

    /** 安全教育-工作要求（满分20） */
    @Column("safety_edu_work_req")
    private BigDecimal safetyEduWorkReq;

    /** 安全教育-效果评估（满分10） */
    @Column("safety_edu_effect")
    private BigDecimal safetyEduEffect;

    // ==================== 后进生帮扶（30分） ====================

    /** 后进生帮扶-工作要求（满分20） */
    @Column("struggling_student_work_req")
    private BigDecimal strugglingStudentWorkReq;

    /** 后进生帮扶-效果评估（满分10） */
    @Column("struggling_student_effect")
    private BigDecimal strugglingStudentEffect;

    // ==================== 育人成果附加分（10分） ====================

    /** 育人成果-安全稳定（满分3） */
    @Column("achievement_safety")
    private BigDecimal achievementSafety;

    /** 育人成果-学风建设（满分3） */
    @Column("achievement_study_style")
    private BigDecimal achievementStudyStyle;

    /** 育人成果-后进生帮扶（满分4） */
    @Column("achievement_struggling")
    private BigDecimal achievementStruggling;

    // ==================== 行政班分 ====================

    /** 行政班分（1.0 或 0.5，新生和毕业班为0.5） */
    @Column("admin_class_score")
    private BigDecimal adminClassScore;

    /** 是否新生或毕业班 */
    @Column("is_freshmen_or_graduating")
    private Integer isFreshmenOrGraduating;

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
