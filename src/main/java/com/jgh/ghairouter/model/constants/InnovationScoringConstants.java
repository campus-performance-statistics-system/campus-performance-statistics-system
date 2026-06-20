package com.jgh.ghairouter.model.constants;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * 大创业绩得分规则常量（v6）。
 *
 * 创新创业训练计划项目（大创项目）：
 *   国家级项目：4分/项
 *   区级项目：2分/项
 *
 * 分配规则（与教师获奖一致）：
 *   单人：100%
 *   两人：7:3（负责人70%，参与者30%）
 *   三人：6:2:2（负责人60%，其余20%）
 *   四人及以上：负责人50%，其余平均分配50%
 *
 * 项目类型（不影响得分）：
 *   创新训练、创业训练、创业实践
 */
public final class InnovationScoringConstants {

    private InnovationScoringConstants() {
    }

    // ==================== 级别 ====================
    public static final String LEVEL_NATIONAL = "national";
    public static final String LEVEL_REGIONAL = "regional";

    public static final String LEVEL_TEXT_NATIONAL = "国家级";
    public static final String LEVEL_TEXT_REGIONAL = "区级";

    /** 国家级项目总分 */
    public static final BigDecimal SCORE_NATIONAL = new BigDecimal("4");
    /** 区级项目总分 */
    public static final BigDecimal SCORE_REGIONAL = new BigDecimal("2");

    // ==================== 项目类型 ====================
    public static final String PROJECT_TYPE_INNOVATION = "innovation_training";
    public static final String PROJECT_TYPE_ENTREPRENEURSHIP = "entrepreneurship_training";
    public static final String PROJECT_TYPE_PRACTICE = "entrepreneurship_practice";

    public static final String PROJECT_TYPE_TEXT_INNOVATION = "创新训练";
    public static final String PROJECT_TYPE_TEXT_ENTREPRENEURSHIP = "创业训练";
    public static final String PROJECT_TYPE_TEXT_PRACTICE = "创业实践";

    // ==================== 项目状态 ====================
    public static final String STATUS_CONCLUDED = "concluded";
    public static final String STATUS_NEWLY_ADDED = "newly_added";

    public static final String STATUS_TEXT_CONCLUDED = "结题";
    public static final String STATUS_TEXT_NEWLY_ADDED = "新增";

    // ==================== 分配比例 ====================
    /** 两人：负责人 70% */
    public static final BigDecimal RATIO_2_LEADER = new BigDecimal("0.70");
    /** 两人：成员 30% */
    public static final BigDecimal RATIO_2_MEMBER = new BigDecimal("0.30");
    /** 三人：负责人 60% */
    public static final BigDecimal RATIO_3_LEADER = new BigDecimal("0.60");
    /** 三人：成员各 20% */
    public static final BigDecimal RATIO_3_MEMBER = new BigDecimal("0.20");
    /** 四人及以上：负责人 50% */
    public static final BigDecimal RATIO_N_LEADER = new BigDecimal("0.50");

    // ==================== 计算方法 ====================

    /**
     * 根据级别计算项目总得分
     */
    public static BigDecimal calcProjectScore(String level) {
        if (LEVEL_NATIONAL.equals(level)) {
            return SCORE_NATIONAL;
        }
        if (LEVEL_REGIONAL.equals(level)) {
            return SCORE_REGIONAL;
        }
        return BigDecimal.ZERO;
    }

    /**
     * 分配得分给各指导教师
     * 两人：7:3；三人：6:2:2；四人及以上：主持者50%，其余平分50%
     */
    public static List<BigDecimal> distributeScore(BigDecimal totalScore, int memberCount, int leaderIndex) {
        List<BigDecimal> result = new ArrayList<>();
        if (memberCount <= 0 || totalScore.compareTo(BigDecimal.ZERO) <= 0) {
            for (int i = 0; i < memberCount; i++) {
                result.add(BigDecimal.ZERO);
            }
            return result;
        }

        if (memberCount == 1) {
            result.add(totalScore.setScale(3, RoundingMode.HALF_UP));
            return result;
        }

        if (memberCount == 2) {
            // 两人：7:3
            if (leaderIndex >= 0 && leaderIndex < 2) {
                BigDecimal leaderShare = totalScore.multiply(RATIO_2_LEADER).setScale(3, RoundingMode.HALF_UP);
                BigDecimal memberShare = totalScore.subtract(leaderShare);
                for (int i = 0; i < 2; i++) {
                    result.add(i == leaderIndex ? leaderShare : memberShare);
                }
            } else {
                result.add(totalScore.setScale(3, RoundingMode.HALF_UP));
                result.add(BigDecimal.ZERO);
            }
            return result;
        }

        if (memberCount == 3) {
            // 三人：6:2:2
            if (leaderIndex >= 0 && leaderIndex < 3) {
                BigDecimal leaderShare = totalScore.multiply(RATIO_3_LEADER).setScale(3, RoundingMode.HALF_UP);
                BigDecimal rest = totalScore.subtract(leaderShare);
                BigDecimal memberShare = rest.divide(new BigDecimal("2"), 3, RoundingMode.HALF_UP);
                for (int i = 0; i < 3; i++) {
                    if (i == leaderIndex) {
                        result.add(leaderShare);
                    } else {
                        result.add(memberShare);
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

        // 四人及以上：主持者50%，其余平分50%
        if (leaderIndex >= 0 && leaderIndex < memberCount) {
            BigDecimal leaderShare = totalScore.multiply(RATIO_N_LEADER).setScale(3, RoundingMode.HALF_UP);
            BigDecimal rest = totalScore.subtract(leaderShare);
            int otherCount = memberCount - 1;
            BigDecimal perOther = rest.divide(BigDecimal.valueOf(otherCount), 3, RoundingMode.HALF_UP);
            for (int i = 0; i < memberCount; i++) {
                if (i == leaderIndex) {
                    result.add(leaderShare);
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
            BigDecimal per = totalScore.divide(BigDecimal.valueOf(memberCount), 3, RoundingMode.HALF_UP);
            for (int i = 0; i < memberCount; i++) result.add(per);
        }
        return result;
    }

    // ==================== 显示名映射 ====================

    public static String getLevelText(String level) {
        if (level == null) return null;
        return switch (level) {
            case LEVEL_NATIONAL -> LEVEL_TEXT_NATIONAL;
            case LEVEL_REGIONAL -> LEVEL_TEXT_REGIONAL;
            default -> level;
        };
    }

    public static String getProjectTypeText(String projectType) {
        if (projectType == null) return null;
        return switch (projectType) {
            case PROJECT_TYPE_INNOVATION -> PROJECT_TYPE_TEXT_INNOVATION;
            case PROJECT_TYPE_ENTREPRENEURSHIP -> PROJECT_TYPE_TEXT_ENTREPRENEURSHIP;
            case PROJECT_TYPE_PRACTICE -> PROJECT_TYPE_TEXT_PRACTICE;
            default -> projectType;
        };
    }

    public static String getProjectStatusText(String projectStatus) {
        if (projectStatus == null) return null;
        return switch (projectStatus) {
            case STATUS_CONCLUDED -> STATUS_TEXT_CONCLUDED;
            case STATUS_NEWLY_ADDED -> STATUS_TEXT_NEWLY_ADDED;
            default -> projectStatus;
        };
    }

    public static List<String> getAvailableLevels() {
        return List.of(LEVEL_NATIONAL, LEVEL_REGIONAL);
    }

    public static List<String> getAvailableStatuses() {
        return List.of(STATUS_CONCLUDED, STATUS_NEWLY_ADDED);
    }

    public static List<String> getAvailableProjectTypes() {
        return List.of(PROJECT_TYPE_INNOVATION, PROJECT_TYPE_ENTREPRENEURSHIP, PROJECT_TYPE_PRACTICE);
    }

    /**
     * 获取得分规则列表（供前端 API 返回）
     */
    public static List<Map<String, Object>> getAllScoringRules() {
        List<Map<String, Object>> rules = new ArrayList<>();

        Map<String, Object> rule = new LinkedHashMap<>();
        rule.put("type", "大创业绩");
        rule.put("nationalScore", SCORE_NATIONAL);
        rule.put("regionalScore", SCORE_REGIONAL);
        rule.put("distribution", "1人:100%; 2人:7:3; 3人:6:2:2; 4人及以上:主持者50%,其余平分50%");
        rule.put("note", "国家级项目4分/项，区级项目2分/项。项目类型（创新训练/创业训练/创业实践）不影响得分。");
        rules.add(rule);

        return rules;
    }
}
