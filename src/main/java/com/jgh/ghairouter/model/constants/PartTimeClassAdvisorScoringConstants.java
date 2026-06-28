package com.jgh.ghairouter.model.constants;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * 兼职班主任业绩得分规则常量（v10）。
 *
 * 评分维度（满分100 + 附加分10 = 110）：
 *   学风建设（30分）：工作要求（20分）+ 效果评估（10分）
 *   安全教育（30分）：工作要求（20分）+ 效果评估（10分）
 *   后进生帮扶（30分）：工作要求（20分）+ 效果评估（10分）
 *   育人成果附加分（10分）：安全稳定（3分）+ 学风建设（3分）+ 后进生帮扶（4分）
 *
 * 分值转换标准：
 *   0至50 → 0分；51至60 → 1分；61至70 → 2分；
 *   71至80 → 3分；81至90 → 4分；91至100 → 5分。
 *
 * 每带一个行政班有1分，新生和毕业班需除2（即0.5分）。
 * 最终得分 = 平均分折合分 + 行政班分合计。
 */
public final class PartTimeClassAdvisorScoringConstants {

    private PartTimeClassAdvisorScoringConstants() {
    }

    public static final String TYPE_NAME = "兼职班主任";

    // ==================== 满分常量 ====================
    public static final BigDecimal MAX_STUDY_STYLE_WORK_REQ = new BigDecimal("20");
    public static final BigDecimal MAX_STUDY_STYLE_EFFECT = new BigDecimal("10");
    public static final BigDecimal MAX_SAFETY_EDU_WORK_REQ = new BigDecimal("20");
    public static final BigDecimal MAX_SAFETY_EDU_EFFECT = new BigDecimal("10");
    public static final BigDecimal MAX_STRUGGLING_WORK_REQ = new BigDecimal("20");
    public static final BigDecimal MAX_STRUGGLING_EFFECT = new BigDecimal("10");
    public static final BigDecimal MAX_ACHIEVEMENT_SAFETY = new BigDecimal("3");
    public static final BigDecimal MAX_ACHIEVEMENT_STUDY_STYLE = new BigDecimal("3");
    public static final BigDecimal MAX_ACHIEVEMENT_STRUGGLING = new BigDecimal("4");

    /** 行政班分：正常班级 */
    public static final BigDecimal ADMIN_CLASS_SCORE_NORMAL = new BigDecimal("1");
    /** 行政班分：新生或毕业班（除2） */
    public static final BigDecimal ADMIN_CLASS_SCORE_HALF = new BigDecimal("0.5");

    /**
     * 计算单行总得分（C到K列之和）
     */
    public static BigDecimal calcRowTotal(
            BigDecimal studyStyleWorkReq,
            BigDecimal studyStyleEffect,
            BigDecimal safetyEduWorkReq,
            BigDecimal safetyEduEffect,
            BigDecimal strugglingWorkReq,
            BigDecimal strugglingEffect,
            BigDecimal achievementSafety,
            BigDecimal achievementStudyStyle,
            BigDecimal achievementStruggling) {

        BigDecimal total = BigDecimal.ZERO;
        if (studyStyleWorkReq != null) total = total.add(studyStyleWorkReq);
        if (studyStyleEffect != null) total = total.add(studyStyleEffect);
        if (safetyEduWorkReq != null) total = total.add(safetyEduWorkReq);
        if (safetyEduEffect != null) total = total.add(safetyEduEffect);
        if (strugglingWorkReq != null) total = total.add(strugglingWorkReq);
        if (strugglingEffect != null) total = total.add(strugglingEffect);
        if (achievementSafety != null) total = total.add(achievementSafety);
        if (achievementStudyStyle != null) total = total.add(achievementStudyStyle);
        if (achievementStruggling != null) total = total.add(achievementStruggling);
        return total;
    }

    /**
     * 分值转换：将原始分数（0-110）转换为折合分（0-5）
     * 0至50 → 0；51至60 → 1；61至70 → 2；
     * 71至80 → 3；81至90 → 4；91至100 → 5（超过100也按5）。
     */
    public static BigDecimal convertScore(BigDecimal rawScore) {
        if (rawScore == null) return BigDecimal.ZERO;
        double v = rawScore.doubleValue();
        if (v <= 50) return BigDecimal.ZERO;
        if (v <= 60) return BigDecimal.ONE;
        if (v <= 70) return new BigDecimal("2");
        if (v <= 80) return new BigDecimal("3");
        if (v <= 90) return new BigDecimal("4");
        return new BigDecimal("5");
    }

    /**
     * 获取得分规则列表（供前端 API 返回）
     */
    public static List<Map<String, Object>> getAllScoringRules() {
        List<Map<String, Object>> rules = new ArrayList<>();

        Map<String, Object> rule = new LinkedHashMap<>();
        rule.put("type", TYPE_NAME);
        rule.put("note", "评分维度：学风建设（30分=工作要求20+效果评估10）、" +
                "安全教育（30分=工作要求20+效果评估10）、" +
                "后进生帮扶（30分=工作要求20+效果评估10）、" +
                "育人成果附加分（10分=安全稳定3+学风建设3+后进生帮扶4）。" +
                "分值转换：0-50→0分，51-60→1分，61-70→2分，71-80→3分，81-90→4分，91-100→5分。" +
                "每带一个行政班有1分（新生和毕业班0.5分），最终得分=平均分折合分+行政班分合计。");
        rule.put("maxScores", Map.of(
                "studyStyleWorkReq", MAX_STUDY_STYLE_WORK_REQ,
                "studyStyleEffect", MAX_STUDY_STYLE_EFFECT,
                "safetyEduWorkReq", MAX_SAFETY_EDU_WORK_REQ,
                "safetyEduEffect", MAX_SAFETY_EDU_EFFECT,
                "strugglingWorkReq", MAX_STRUGGLING_WORK_REQ,
                "strugglingEffect", MAX_STRUGGLING_EFFECT,
                "achievementSafety", MAX_ACHIEVEMENT_SAFETY,
                "achievementStudyStyle", MAX_ACHIEVEMENT_STUDY_STYLE,
                "achievementStruggling", MAX_ACHIEVEMENT_STRUGGLING
        ));
        rule.put("adminClassScoreNormal", ADMIN_CLASS_SCORE_NORMAL);
        rule.put("adminClassScoreHalf", ADMIN_CLASS_SCORE_HALF);
        rules.add(rule);

        return rules;
    }
}
