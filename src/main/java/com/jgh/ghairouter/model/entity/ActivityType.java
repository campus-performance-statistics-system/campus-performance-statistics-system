package com.jgh.ghairouter.model.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import com.mybatisflex.core.keygen.KeyGenerators;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 活动类型 实体类（具体比赛/活动类型，隶属于某个分类）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("activity_type")
public class ActivityType implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * id
     */
    @Id(keyType = KeyType.Generator, value = KeyGenerators.snowFlakeId)
    private Long id;

    /**
     * 活动类型名称
     */
    @Column("name")
    private String name;

    /**
     * 活动类型描述
     */
    @Column("description")
    private String description;

    /**
     * 所属分类ID
     */
    @Column("category_id")
    private Long categoryId;

    /**
     * 排序
     */
    @Column("sort_order")
    private Integer sortOrder;

    /**
     * 创建时间
     */
    @Column("create_time")
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @Column("update_time")
    private LocalDateTime updateTime;

    /**
     * 是否删除
     */
    @Column(value = "is_delete", isLogicDelete = true)
    private Integer isDelete;
}
