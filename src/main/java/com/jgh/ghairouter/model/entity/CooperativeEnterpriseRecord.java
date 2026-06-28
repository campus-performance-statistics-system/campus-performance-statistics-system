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
 * 签订合作企业记录 实体类（v14 新增）。
 * 每条记录对应一个教师签订合作企业的情况。
 * 纯数据记录，不涉及计分。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("cooperative_enterprise_record")
public class CooperativeEnterpriseRecord implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id(keyType = KeyType.Auto)
    private Long id;

    /** 记录类型名称（固定为"签订合作企业"） */
    @Column("type_name")
    private String typeName;

    /** 填报用户ID */
    @Column("user_id")
    private Long userId;

    /** 学院 */
    @Column("college")
    private String college;

    /** 企业名称 */
    @Column("enterprise_name")
    private String enterpriseName;

    /** 签订合作企业老师 */
    @Column("teacher_name")
    private String teacherName;

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
