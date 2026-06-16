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
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 活动类型-竞赛等级-获奖等级得分规则
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("activity_rank_grade_score")
public class ActivityRankGradeScore implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id(keyType = KeyType.Generator, value = KeyGenerators.snowFlakeId)
    private Long id;

    @Column("activity_type_id")
    private Long activityTypeId;

    @Column("competition_rank")
    private String competitionRank;

    @Column("grade_name")
    private String gradeName;

    @Column("base_score")
    private BigDecimal baseScore;

    @Column("create_time")
    private LocalDateTime createTime;

    @Column("update_time")
    private LocalDateTime updateTime;

    @Column(value = "is_delete", isLogicDelete = true)
    private Integer isDelete;
}
