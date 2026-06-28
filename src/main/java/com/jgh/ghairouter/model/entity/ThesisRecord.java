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
 * 论文业绩记录 实体类（v8 新增）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("thesis_record")
public class ThesisRecord implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id(keyType = KeyType.Auto)
    private Long id;

    /** 记录类型名称（固定为"论文业绩"） */
    @Column("type_name")
    private String typeName;

    /** 填报用户ID */
    @Column("user_id")
    private Long userId;

    /** 论文名称 */
    @Column("thesis_name")
    private String thesisName;

    /** 发表刊物 */
    @Column("journal_name")
    private String journalName;

    /** 论文等级：level_1-一级, level_2-二级, level_3-三级, level_4-四级 */
    @Column("thesis_level")
    private String thesisLevel;

    /** 作者数据JSON数组（含得分分配） */
    @Column("authors_data")
    private String authorsData;

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
