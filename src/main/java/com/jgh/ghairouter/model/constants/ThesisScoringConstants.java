package com.jgh.ghairouter.model.constants;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * 论文业绩得分规则常量（v8）。
 *
 * 论文等级：
 *   一级：12分/篇
 *   二级：9分/篇
 *   三级：6分/篇
 *   四级：3分/篇
 *
 * 分配规则（与教师获奖一致）：
 *   单人：100%
 *   两人：7:3（第一作者70%，第二作者30%）
 *   三人：6:2:2（第一作者60%，其余各20%）
 *   四人及以上：第一作者50%，其余平均分配50%
 */
public final class ThesisScoringConstants {

    private ThesisScoringConstants() {
    }

    // ==================== 论文等级 ====================
    public static final String LEVEL_1 = "level_1";
    public static final String LEVEL_2 = "level_2";
    public static final String LEVEL_3 = "level_3";
    public static final String LEVEL_4 = "level_4";

    public static final String LEVEL_TEXT_1 = "一级";
    public static final String LEVEL_TEXT_2 = "二级";
    public static final String LEVEL_TEXT_3 = "三级";
    public static final String LEVEL_TEXT_4 = "四级";

    // ==================== 论文等级总分 ====================
    /** 一级论文总分 */
    public static final BigDecimal SCORE_LEVEL_1 = new BigDecimal("12");
    /** 二级论文总分 */
    public static final BigDecimal SCORE_LEVEL_2 = new BigDecimal("9");
    /** 三级论文总分 */
    public static final BigDecimal SCORE_LEVEL_3 = new BigDecimal("6");
    /** 四级论文总分 */
    public static final BigDecimal SCORE_LEVEL_4 = new BigDecimal("3");

    // ==================== 分配比例 ====================
    /** 两人：第一作者 70% */
    public static final BigDecimal RATIO_2_FIRST = new BigDecimal("0.70");
    /** 三人：第一作者 60% */
    public static final BigDecimal RATIO_3_FIRST = new BigDecimal("0.60");
    /** 四人及以上：第一作者 50% */
    public static final BigDecimal RATIO_N_FIRST = new BigDecimal("0.50");

    // ==================== 计算方法 ====================

    /**
     * 根据论文等级计算论文总得分
     */
    public static BigDecimal calcThesisScore(String thesisLevel) {
        if (thesisLevel == null) return BigDecimal.ZERO;
        return switch (thesisLevel) {
            case LEVEL_1 -> SCORE_LEVEL_1;
            case LEVEL_2 -> SCORE_LEVEL_2;
            case LEVEL_3 -> SCORE_LEVEL_3;
            case LEVEL_4 -> SCORE_LEVEL_4;
            default -> BigDecimal.ZERO;
        };
    }

    /**
     * 分配得分给各作者
     * 两人：7:3；三人：6:2:2；四人及以上：第一作者50%，其余平分50%
     */
    public static List<BigDecimal> distributeScore(BigDecimal totalScore, int authorCount, int firstAuthorIndex) {
        List<BigDecimal> result = new ArrayList<>();
        if (authorCount <= 0 || totalScore.compareTo(BigDecimal.ZERO) <= 0) {
            for (int i = 0; i < authorCount; i++) {
                result.add(BigDecimal.ZERO);
            }
            return result;
        }

        if (authorCount == 1) {
            result.add(totalScore.setScale(3, RoundingMode.HALF_UP));
            return result;
        }

        if (authorCount == 2) {
            // 两人：7:3
            if (firstAuthorIndex >= 0 && firstAuthorIndex < 2) {
                BigDecimal firstShare = totalScore.multiply(RATIO_2_FIRST).setScale(3, RoundingMode.HALF_UP);
                BigDecimal secondShare = totalScore.subtract(firstShare);
                for (int i = 0; i < 2; i++) {
                    result.add(i == firstAuthorIndex ? firstShare : secondShare);
                }
            } else {
                result.add(totalScore.setScale(3, RoundingMode.HALF_UP));
                result.add(BigDecimal.ZERO);
            }
            return result;
        }

        if (authorCount == 3) {
            // 三人：6:2:2
            if (firstAuthorIndex >= 0 && firstAuthorIndex < 3) {
                BigDecimal firstShare = totalScore.multiply(RATIO_3_FIRST).setScale(3, RoundingMode.HALF_UP);
                BigDecimal rest = totalScore.subtract(firstShare);
                BigDecimal otherShare = rest.divide(new BigDecimal("2"), 3, RoundingMode.HALF_UP);
                for (int i = 0; i < 3; i++) {
                    if (i == firstAuthorIndex) {
                        result.add(firstShare);
                    } else {
                        result.add(otherShare);
                    }
                }
                // 修正浮点误差
                BigDecimal sum = result.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
                BigDecimal diff = totalScore.subtract(sum);
                if (diff.compareTo(BigDecimal.ZERO) != 0) {
                    int maxIdx = 0;
                    for (int i = 1; i < result.size(); i++) {
                        if (result.get(i).compareTo(result.get(maxIdx)) > 0) maxIdx = i;
                    }
                    result.set(maxIdx, result.get(maxIdx).add(diff));
                }
            } else {
                BigDecimal per = totalScore.divide(new BigDecimal("3"), 3, RoundingMode.HALF_UP);
                for (int i = 0; i < 3; i++) result.add(per);
            }
            return result;
        }

        // 四人及以上：第一作者50%，其余平分50%
        if (firstAuthorIndex >= 0 && firstAuthorIndex < authorCount) {
            BigDecimal firstShare = totalScore.multiply(RATIO_N_FIRST).setScale(3, RoundingMode.HALF_UP);
            BigDecimal rest = totalScore.subtract(firstShare);
            int otherCount = authorCount - 1;
            BigDecimal perOther = rest.divide(BigDecimal.valueOf(otherCount), 3, RoundingMode.HALF_UP);
            for (int i = 0; i < authorCount; i++) {
                if (i == firstAuthorIndex) {
                    result.add(firstShare);
                } else {
                    result.add(perOther);
                }
            }
            // 修正浮点误差
            BigDecimal sum = result.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal diff = totalScore.subtract(sum);
            if (diff.compareTo(BigDecimal.ZERO) != 0) {
                int maxIdx = 0;
                for (int i = 1; i < result.size(); i++) {
                    if (result.get(i).compareTo(result.get(maxIdx)) > 0) maxIdx = i;
                }
                result.set(maxIdx, result.get(maxIdx).add(diff));
            }
        } else {
            BigDecimal per = totalScore.divide(BigDecimal.valueOf(authorCount), 3, RoundingMode.HALF_UP);
            for (int i = 0; i < authorCount; i++) result.add(per);
        }
        return result;
    }

    // ==================== 显示名映射 ====================

    public static String getLevelText(String level) {
        if (level == null) return null;
        return switch (level) {
            case LEVEL_1 -> LEVEL_TEXT_1;
            case LEVEL_2 -> LEVEL_TEXT_2;
            case LEVEL_3 -> LEVEL_TEXT_3;
            case LEVEL_4 -> LEVEL_TEXT_4;
            default -> level;
        };
    }

    public static List<String> getAvailableLevels() {
        return List.of(LEVEL_1, LEVEL_2, LEVEL_3, LEVEL_4);
    }

    /**
     * 获取得分规则列表（供前端 API 返回）
     */
    public static List<Map<String, Object>> getAllScoringRules() {
        List<Map<String, Object>> rules = new ArrayList<>();

        Map<String, Object> rule = new LinkedHashMap<>();
        rule.put("type", "论文业绩");
        rule.put("level1Score", SCORE_LEVEL_1);
        rule.put("level2Score", SCORE_LEVEL_2);
        rule.put("level3Score", SCORE_LEVEL_3);
        rule.put("level4Score", SCORE_LEVEL_4);
        rule.put("distribution", "1人:100%; 2人:7:3; 3人:6:2:2; 4人及以上:第一作者50%,其余平分50%");
        rule.put("note", "发表论文: 一级12分/篇；二级9分/篇；三级6分/篇；四级3分/篇。");
        rules.add(rule);

        return rules;
    }
}
