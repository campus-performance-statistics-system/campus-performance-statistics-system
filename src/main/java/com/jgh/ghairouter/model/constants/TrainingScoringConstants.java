package com.jgh.ghairouter.model.constants;

import java.math.BigDecimal;
import java.util.*;

/**
 * 指导实训得分规则常量（v4）。
 *
 * 得分规则：
 *   负责组织并指导实训：2分/门
 *   参与指导实训：1分/门
 */
public final class TrainingScoringConstants {

    private TrainingScoringConstants() {
    }

    /** 负责教师得分 */
    public static final BigDecimal RESPONSIBLE_SCORE = new BigDecimal("2");

    /** 参与教师得分 */
    public static final BigDecimal PARTICIPATING_SCORE = new BigDecimal("1");

    /** 角色类型 */
    public static final String ROLE_RESPONSIBLE = "responsible";
    public static final String ROLE_PARTICIPATING = "participating";

    /** 角色显示名称 */
    public static final String ROLE_RESPONSIBLE_TEXT = "负责教师";
    public static final String ROLE_PARTICIPATING_TEXT = "参与教师";

    /**
     * 获取得分规则列表（供前端 API 返回）
     */
    public static List<Map<String, Object>> getAllScoringRules() {
        List<Map<String, Object>> rules = new ArrayList<>();

        Map<String, Object> responsibleRule = new LinkedHashMap<>();
        responsibleRule.put("type", "负责教师得分");
        responsibleRule.put("role", ROLE_RESPONSIBLE_TEXT);
        responsibleRule.put("score", RESPONSIBLE_SCORE);
        responsibleRule.put("note", "负责组织并指导实训：2分/门");
        rules.add(responsibleRule);

        Map<String, Object> participatingRule = new LinkedHashMap<>();
        participatingRule.put("type", "参与教师得分");
        participatingRule.put("role", ROLE_PARTICIPATING_TEXT);
        participatingRule.put("score", PARTICIPATING_SCORE);
        participatingRule.put("note", "参与指导实训：1分/门");
        rules.add(participatingRule);

        return rules;
    }
}
