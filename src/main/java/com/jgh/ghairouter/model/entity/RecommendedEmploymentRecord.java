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
 * 推荐学院学生签约就业记录 实体类（v13 新增）。
 * 每条记录对应一个教师联系一家就业单位的情况。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("recommended_employment_record")
public class RecommendedEmploymentRecord implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id(keyType = KeyType.Auto)
    private Long id;

    /** 记录类型名称（固定为"推荐学院学生签约就业"） */
    @Column("type_name")
    private String typeName;

    /** 填报用户ID */
    @Column("user_id")
    private Long userId;

    /** 联系人（教师姓名） */
    @Column("teacher_name")
    private String teacherName;

    /** 单位名称 */
    @Column("company_name")
    private String companyName;

    /** 签约数 */
    @Column("contract_count")
    private Integer contractCount;

    /** 推荐时间（如"2023年3月"） */
    @Column("recommendation_time")
    private String recommendationTime;

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
