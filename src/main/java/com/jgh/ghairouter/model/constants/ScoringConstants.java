package com.jgh.ghairouter.model.constants;

import java.math.BigDecimal;
import java.util.*;

/**
 * 硬编码得分规则常量（v2 重构）。
 *
 * 教师获奖（服务类）：
 *   参与院级以上比赛：2分/项（负责人）
 *   院级获奖每项增加：一等奖2分；其它等级奖1.5分；优秀奖1分
 *   自治区级获奖每项增加：二等奖5分；三等奖4分；优秀奖3分
 *
 * 个人业务比赛：
 *   国家级：一等奖15分、二等奖12分、三等奖10分、优秀奖8分
 *   自治区级（行业性全国）：一等奖7分（12.6分）
 */
public final class ScoringConstants {

    private ScoringConstants() {
    }

    /** 参与院级以上比赛基础分（仅负责人获得） */
    public static final BigDecimal PARTICIPATION_BASE = new BigDecimal("2");

    // ==================== 竞赛等级 ====================
    public static final String RANK_COLLEGE = "院级";
    public static final String RANK_PROVINCIAL = "自治区级";
    public static final String RANK_NATIONAL = "国家级";

    // ==================== 获奖等级 ====================
    public static final String GRADE_FIRST = "一等奖";
    public static final String GRADE_SECOND = "二等奖";
    public static final String GRADE_THIRD = "三等奖";
    public static final String GRADE_EXCELLENCE = "优秀奖";
    public static final String GRADE_OTHER = "其它等级奖";
    public static final String GRADE_NO_AWARD = "未获奖";

    // ==================== 教师比赛加分（院级/自治区级为基础2分+加分） ====================
    private static final BigDecimal COLLEGE_FIRST = new BigDecimal("2");
    private static final BigDecimal COLLEGE_OTHER = new BigDecimal("1.5");
    private static final BigDecimal COLLEGE_EXCELLENCE = new BigDecimal("1");

    /** 自治区级（服务类）只有二等奖/三等奖/优秀奖 */
    private static final BigDecimal PROVINCIAL_SECOND = new BigDecimal("5");
    private static final BigDecimal PROVINCIAL_THIRD = new BigDecimal("4");
    private static final BigDecimal PROVINCIAL_EXCELLENCE = new BigDecimal("3");

    // ==================== 个人业务比赛得分（直接得分，不加基础分） ====================
    private static final BigDecimal NATIONAL_FIRST = new BigDecimal("15");
    private static final BigDecimal NATIONAL_SECOND = new BigDecimal("12");
    private static final BigDecimal NATIONAL_THIRD = new BigDecimal("10");
    private static final BigDecimal NATIONAL_EXCELLENCE = new BigDecimal("8");
    private static final BigDecimal PROVINCIAL_FIRST = new BigDecimal("7");

    /**
     * 教师比赛：获取得分预览（获奖加分部分，不包含基础2分）
     */
    public static BigDecimal getScorePreview(String competitionRank, String gradeName) {
        if (competitionRank == null || gradeName == null) {
            return BigDecimal.ZERO;
        }
        return switch (competitionRank) {
            case RANK_COLLEGE -> switch (gradeName) {
                case GRADE_FIRST -> COLLEGE_FIRST;
                case GRADE_OTHER -> COLLEGE_OTHER;
                case GRADE_EXCELLENCE -> COLLEGE_EXCELLENCE;
                default -> BigDecimal.ZERO;
            };
            case RANK_PROVINCIAL -> switch (gradeName) {
                case GRADE_SECOND -> PROVINCIAL_SECOND;
                case GRADE_THIRD -> PROVINCIAL_THIRD;
                case GRADE_EXCELLENCE -> PROVINCIAL_EXCELLENCE;
                default -> BigDecimal.ZERO;
            };
            case RANK_NATIONAL -> switch (gradeName) {
                case GRADE_FIRST -> NATIONAL_FIRST;
                case GRADE_SECOND -> NATIONAL_SECOND;
                case GRADE_THIRD -> NATIONAL_THIRD;
                case GRADE_EXCELLENCE -> NATIONAL_EXCELLENCE;
                default -> BigDecimal.ZERO;
            };
            default -> BigDecimal.ZERO;
        };
    }

    /**
     * 教师比赛：获取负责人最终得分 = 基础分(2) + 获奖加分
     */
    public static BigDecimal getLeaderTotalScore(String competitionRank, String gradeName) {
        if (GRADE_NO_AWARD.equals(gradeName)) {
            return PARTICIPATION_BASE;
        }
        return PARTICIPATION_BASE.add(getScorePreview(competitionRank, gradeName));
    }

    /**
     * 个人业务比赛：直接得分（不加基础分）
     */
    public static BigDecimal getPersonalBusinessScore(String competitionRank, String gradeName) {
        if (competitionRank == null || gradeName == null) {
            return BigDecimal.ZERO;
        }
        if (RANK_NATIONAL.equals(competitionRank)) {
            return switch (gradeName) {
                case GRADE_FIRST -> NATIONAL_FIRST;
                case GRADE_SECOND -> NATIONAL_SECOND;
                case GRADE_THIRD -> NATIONAL_THIRD;
                case GRADE_EXCELLENCE -> NATIONAL_EXCELLENCE;
                default -> BigDecimal.ZERO;
            };
        }
        if (RANK_PROVINCIAL.equals(competitionRank) && GRADE_FIRST.equals(gradeName)) {
            return PROVINCIAL_FIRST;
        }
        return BigDecimal.ZERO;
    }

    /**
     * 获取所有可选的竞赛等级
     */
    public static List<String> getAvailableRanks() {
        return List.of(RANK_COLLEGE, RANK_PROVINCIAL, RANK_NATIONAL);
    }

    /**
     * 获取指定竞赛等级下的可选获奖等级
     */
    public static List<String> getAvailableGrades(String rank) {
        return switch (rank) {
            case RANK_COLLEGE -> List.of(GRADE_FIRST, GRADE_OTHER, GRADE_EXCELLENCE, GRADE_NO_AWARD);
            case RANK_PROVINCIAL -> List.of(GRADE_SECOND, GRADE_THIRD, GRADE_EXCELLENCE, GRADE_NO_AWARD);
            case RANK_NATIONAL -> List.of(GRADE_FIRST, GRADE_SECOND, GRADE_THIRD, GRADE_EXCELLENCE, GRADE_NO_AWARD);
            default -> List.of();
        };
    }

    /**
     * 获取得分规则列表（供前端 API 返回）
     */
    public static List<Map<String, Object>> getAllScoringRules() {
        List<Map<String, Object>> rules = new ArrayList<>();
        for (String rank : getAvailableRanks()) {
            for (String grade : getAvailableGrades(rank)) {
                if (GRADE_NO_AWARD.equals(grade)) continue;
                Map<String, Object> rule = new LinkedHashMap<>();
                rule.put("competitionRank", rank);
                rule.put("gradeName", grade);
                rule.put("bonus", getScorePreview(rank, grade));
                rule.put("leaderTotalScore", getLeaderTotalScore(rank, grade));
                rule.put("type", "教师比赛");
                rules.add(rule);
            }
        }
        return rules;
    }
}
