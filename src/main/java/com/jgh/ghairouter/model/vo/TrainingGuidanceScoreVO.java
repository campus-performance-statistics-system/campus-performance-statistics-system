package com.jgh.ghairouter.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 指导实训得分明细 视图对象（v4）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrainingGuidanceScoreVO {

    /** 教师用户ID */
    private Long userId;

    /** 教师姓名 */
    private String teacherName;

    /** 得分（2或1） */
    private BigDecimal score;

    /** 角色类型：responsible-负责教师, participating-参与教师 */
    private String roleType;

    /** 角色显示名称 */
    private String roleTypeText;
}
