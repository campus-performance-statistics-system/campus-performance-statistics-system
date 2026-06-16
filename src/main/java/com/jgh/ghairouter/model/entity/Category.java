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
 * 比赛分类 实体类（仅顶层分类：服务类、个人业务类）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("category")
public class Category implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * id
     */
    @Id(keyType = KeyType.Generator, value = KeyGenerators.snowFlakeId)
    private Long id;

    /**
     * 分类名称
     */
    @Column("name")
    private String name;

    /**
     * 分类描述
     */
    @Column("description")
    private String description;

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
