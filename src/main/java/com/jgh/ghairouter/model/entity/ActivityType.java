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

    @Id(keyType = KeyType.Generator, value = KeyGenerators.snowFlakeId)
    private Long id;

    @Column("name")
    private String name;

    @Column("description")
    private String description;

    @Column("category_id")
    private Long categoryId;

    @Column("sponsor_unit")
    private String sponsorUnit;

    @Column("create_time")
    private LocalDateTime createTime;

    @Column("update_time")
    private LocalDateTime updateTime;

    @Column(value = "is_delete", isLogicDelete = true)
    private Integer isDelete;
}
