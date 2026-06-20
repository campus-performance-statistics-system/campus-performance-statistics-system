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
 * 科研及教材业绩记录 实体类（v5 新增）。
 * 包含横向科研项目、专利、教材及自编讲义三类科研业绩。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("research_achievement_record")
public class ResearchAchievementRecord implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id(keyType = KeyType.Auto)
    private Long id;

    /** 记录类型名称（固定为"科研及教材业绩"） */
    @Column("type_name")
    private String typeName;

    /** 子类型：horizontal_project-横向科研项目, patent-专利, textbook-教材及自编讲义 */
    @Column("sub_type")
    private String subType;

    /** 填报用户ID */
    @Column("user_id")
    private Long userId;

    /** 成果名称（项目名称/专利名称/教材名称） */
    @Column("achievement_name")
    private String achievementName;

    // ==================== 横向科研项目字段 ====================

    /** 项目来源（横向科研项目） */
    @Column("project_source")
    private String projectSource;

    /** 到位经费-万元（横向科研项目） */
    @Column("funding_amount")
    private BigDecimal fundingAmount;

    // ==================== 专利字段 ====================

    /** 专利号 */
    @Column("patent_number")
    private String patentNumber;

    /** 专利类别：invention-发明专利, utility_model-实用新型 */
    @Column("patent_type")
    private String patentType;

    // ==================== 教材字段 ====================

    /** 字数-万（教材） */
    @Column("word_count")
    private BigDecimal wordCount;

    /** 教材类型：published-出版教材, first_handout-首次自编讲义, revised_handout-修改讲义 */
    @Column("textbook_type")
    private String textbookType;

    // ==================== 成员与得分 ====================

    /** 项目组成员及得分分配JSON数组 */
    @Column("member_data")
    private String memberData;

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
