package com.jgh.ghairouter.model.constants;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * 指导学生科技竞赛得分规则常量（v3）。
 *
 * 基础分：
 *   院级比赛：组织者 1分；指导者 1分
 *   区级以上比赛：组织者 2分；指导者 2分
 *
 * 获奖加分（指导老师）：
 *   院级/校级：一等奖 1.5分；其它等级奖 1分；优秀奖 0.5分
 *   区级/自治区级：一等奖 5分；二等奖 3分；三等奖 2.5分；优秀奖 2分
 *   国家级：一等奖 12分；二等奖 10分；三等奖 8分；优秀奖 6分
 *
 * 行业性全国奖 = 自治区级奖 × 1.8
 * 行业性省级奖 = 院级奖 × 1.8
 *
 * 分配规则（指导老师之间）：
 *   同项比赛获奖取最高级（含国家级）
 *   两人完成按 7:3 分配
 *   四人及以上完成：主持者分配 50%；参与者平均分配 50%
 */
public final class StudentScoringConstants {

    private StudentScoringConstants() {
    }

    // ==================== 竞赛等级 ====================
    public static final String RANK_COLLEGE = "院级";
    public static final String RANK_SCHOOL = "校级";
    public static final String RANK_PROVINCIAL = "自治区级";
    public static final String RANK_REGIONAL = "区级";
    public static final String RANK_NATIONAL = "国家级";
    public static final String RANK_INDUSTRY_NATIONAL = "行业性全国";
    public static final String RANK_INDUSTRY_PROVINCIAL = "行业性省级";

    // ==================== 获奖等级 ====================
    public static final String GRADE_FIRST = "一等奖";
    public static final String GRADE_SECOND = "二等奖";
    public static final String GRADE_THIRD = "三等奖";
    public static final String GRADE_OTHER = "其它等级奖";
    public static final String GRADE_EXCELLENCE = "优秀奖";
    public static final String GRADE_NO_AWARD = "未获奖";
    public static final String GRADE_PENDING = "奖项未出";

    /** 行业性奖系数 */
    public static final BigDecimal INDUSTRY_MULTIPLIER = new BigDecimal("1.8");

    // ==================== 基础分 ====================
    /** 院级/校级 组织者基础分 */
    public static final BigDecimal ORG_BASE_COLLEGE = new BigDecimal("1");
    /** 院级/校级 指导者基础分 */
    public static final BigDecimal ADVISOR_BASE_COLLEGE = new BigDecimal("1");
    /** 区级以上 组织者基础分 */
    public static final BigDecimal ORG_BASE_HIGHER = new BigDecimal("2");
    /** 区级以上 指导者基础分 */
    public static final BigDecimal ADVISOR_BASE_HIGHER = new BigDecimal("2");

    // ==================== 获奖加分（指导老师） ====================
    /** 院级/校级 获奖加分 */
    private static final BigDecimal COLLEGE_FIRST = new BigDecimal("1.5");
    private static final BigDecimal COLLEGE_OTHER = new BigDecimal("1");
    private static final BigDecimal COLLEGE_EXCELLENCE = new BigDecimal("0.5");

    /** 区级/自治区级 获奖加分 */
    private static final BigDecimal PROVINCIAL_FIRST = new BigDecimal("5");
    private static final BigDecimal PROVINCIAL_SECOND = new BigDecimal("3");
    private static final BigDecimal PROVINCIAL_THIRD = new BigDecimal("2.5");
    private static final BigDecimal PROVINCIAL_EXCELLENCE = new BigDecimal("2");

    /** 国家级 获奖加分 */
    private static final BigDecimal NATIONAL_FIRST = new BigDecimal("12");
    private static final BigDecimal NATIONAL_SECOND = new BigDecimal("10");
    private static final BigDecimal NATIONAL_THIRD = new BigDecimal("8");
    private static final BigDecimal NATIONAL_EXCELLENCE = new BigDecimal("6");

    // ==================== 分配比例 ====================
    /** 两人：负责人 70% */
    public static final BigDecimal RATIO_2_LEADER = new BigDecimal("0.70");
    /** 两人：另一人 30% */
    public static final BigDecimal RATIO_2_MEMBER = new BigDecimal("0.30");
    /** 四人及以上：负责人 50% */
    public static final BigDecimal RATIO_N_LEADER = new BigDecimal("0.50");
    /** 四人及以上：其余人平分 50% */
    public static final BigDecimal RATIO_N_REST = new BigDecimal("0.50");

    // ==================== 计算方法 ====================

    /**
     * 判断是否为区级以上级别
     */
    public static boolean isHigherLevel(String competitionRank) {
        if (competitionRank == null) return false;
        return switch (competitionRank) {
            case RANK_REGIONAL, RANK_PROVINCIAL, RANK_NATIONAL, RANK_INDUSTRY_NATIONAL -> true;
            default -> false;
        };
    }

    /**
     * 获取组织者基础分
     */
    public static BigDecimal getOrganizerBase(String competitionRank) {
        if (competitionRank == null) return BigDecimal.ZERO;
        return isHigherLevel(competitionRank) ? ORG_BASE_HIGHER : ORG_BASE_COLLEGE;
    }

    /**
     * 获取指导者基础分
     */
    public static BigDecimal getAdvisorBase(String competitionRank) {
        if (competitionRank == null) return BigDecimal.ZERO;
        return isHigherLevel(competitionRank) ? ADVISOR_BASE_HIGHER : ADVISOR_BASE_COLLEGE;
    }

    /**
     * 获取得奖加分（不含基础分，不含行业系数）。
     * 行业系数在调用方乘以 1.8。
     */
    public static BigDecimal getAwardBonus(String competitionRank, String gradeName) {
        if (competitionRank == null || gradeName == null) return BigDecimal.ZERO;
        if (GRADE_NO_AWARD.equals(gradeName) || GRADE_PENDING.equals(gradeName)) return BigDecimal.ZERO;

        return switch (competitionRank) {
            case RANK_COLLEGE, RANK_SCHOOL -> switch (gradeName) {
                case GRADE_FIRST -> COLLEGE_FIRST;
                case GRADE_SECOND, GRADE_THIRD, GRADE_OTHER -> COLLEGE_OTHER;
                case GRADE_EXCELLENCE -> COLLEGE_EXCELLENCE;
                default -> BigDecimal.ZERO;
            };
            case RANK_REGIONAL, RANK_PROVINCIAL -> switch (gradeName) {
                case GRADE_FIRST -> PROVINCIAL_FIRST;
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
            case RANK_INDUSTRY_NATIONAL -> {
                // 行业性全国 = 自治区级 × 1.8
                BigDecimal provincialBonus = switch (gradeName) {
                    case GRADE_FIRST -> PROVINCIAL_FIRST;
                    case GRADE_SECOND -> PROVINCIAL_SECOND;
                    case GRADE_THIRD -> PROVINCIAL_THIRD;
                    case GRADE_EXCELLENCE -> PROVINCIAL_EXCELLENCE;
                    default -> BigDecimal.ZERO;
                };
                yield provincialBonus.multiply(INDUSTRY_MULTIPLIER).setScale(3, RoundingMode.HALF_UP);
            }
            case RANK_INDUSTRY_PROVINCIAL -> {
                // 行业性省级 = 院级 × 1.8
                BigDecimal collegeBonus = switch (gradeName) {
                    case GRADE_FIRST -> COLLEGE_FIRST;
                    case GRADE_SECOND, GRADE_THIRD, GRADE_OTHER -> COLLEGE_OTHER;
                    case GRADE_EXCELLENCE -> COLLEGE_EXCELLENCE;
                    default -> BigDecimal.ZERO;
                };
                yield collegeBonus.multiply(INDUSTRY_MULTIPLIER).setScale(3, RoundingMode.HALF_UP);
            }
            default -> BigDecimal.ZERO;
        };
    }

    /**
     * 计算分数分配（获奖加分部分，不含基础分）。
     * 两人：7:3；四人及以上：负责人50%，其余平分50%。
     *
     * @param totalBonus 待分配的获奖加分总额
     * @param advisorCount 指导老师人数
     * @param leaderIndex 主持者在列表中的索引（0-based），-1 表示无主持者则平均分配
     * @return 每人分配到的获奖加分
     */
    public static List<BigDecimal> distributeBonus(BigDecimal totalBonus, int advisorCount, int leaderIndex) {
        List<BigDecimal> result = new ArrayList<>();
        if (advisorCount <= 0 || totalBonus.compareTo(BigDecimal.ZERO) <= 0) {
            for (int i = 0; i < advisorCount; i++) {
                result.add(BigDecimal.ZERO);
            }
            return result;
        }

        if (advisorCount == 1) {
            result.add(totalBonus.setScale(3, RoundingMode.HALF_UP));
            return result;
        }

        if (advisorCount == 2) {
            BigDecimal leaderShare = totalBonus.multiply(RATIO_2_LEADER).setScale(3, RoundingMode.HALF_UP);
            BigDecimal memberShare = totalBonus.multiply(RATIO_2_MEMBER).setScale(3, RoundingMode.HALF_UP);
            if (leaderIndex == 0) {
                result.add(leaderShare);
                result.add(memberShare);
            } else if (leaderIndex == 1) {
                result.add(memberShare);
                result.add(leaderShare);
            } else {
                // 无主持者，平均分配
                BigDecimal half = totalBonus.divide(new BigDecimal("2"), 3, RoundingMode.HALF_UP);
                result.add(half);
                result.add(totalBonus.subtract(half));
            }
            return result;
        }

        // 三人：按 6:2:2 分配
        if (advisorCount == 3) {
            if (leaderIndex >= 0 && leaderIndex < 3) {
                BigDecimal leaderShare = totalBonus.multiply(new BigDecimal("0.60")).setScale(3, RoundingMode.HALF_UP);
                BigDecimal rest = totalBonus.subtract(leaderShare);
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
                BigDecimal diff = totalBonus.subtract(sum);
                if (diff.compareTo(BigDecimal.ZERO) != 0) {
                    int maxIdx = 0;
                    for (int i = 1; i < result.size(); i++) {
                        if (result.get(i).compareTo(result.get(maxIdx)) > 0) maxIdx = i;
                    }
                    result.set(maxIdx, result.get(maxIdx).add(diff));
                }
                return result;
            } else {
                BigDecimal per = totalBonus.divide(new BigDecimal("3"), 3, RoundingMode.HALF_UP);
                for (int i = 0; i < 3; i++) result.add(per);
                return result;
            }
        }

        // 四人及以上：负责人 50%，其余平分 50%
        if (leaderIndex >= 0 && leaderIndex < advisorCount) {
            BigDecimal leaderShare = totalBonus.multiply(RATIO_N_LEADER).setScale(3, RoundingMode.HALF_UP);
            BigDecimal rest = totalBonus.subtract(leaderShare);
            int otherCount = advisorCount - 1;
            BigDecimal perOther = rest.divide(BigDecimal.valueOf(otherCount), 3, RoundingMode.HALF_UP);
            for (int i = 0; i < advisorCount; i++) {
                if (i == leaderIndex) {
                    result.add(leaderShare);
                } else {
                    result.add(perOther);
                }
            }
            // 修正浮点误差
            BigDecimal sum = result.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal diff = totalBonus.subtract(sum);
            if (diff.compareTo(BigDecimal.ZERO) != 0) {
                int maxIdx = 0;
                for (int i = 1; i < result.size(); i++) {
                    if (result.get(i).compareTo(result.get(maxIdx)) > 0) maxIdx = i;
                }
                result.set(maxIdx, result.get(maxIdx).add(diff));
            }
        } else {
            BigDecimal per = totalBonus.divide(BigDecimal.valueOf(advisorCount), 3, RoundingMode.HALF_UP);
            for (int i = 0; i < advisorCount; i++) result.add(per);
        }
        return result;
    }

    /**
     * 获取所有可选的竞赛等级
     */
    public static List<String> getAvailableRanks() {
        return List.of(RANK_COLLEGE, RANK_SCHOOL, RANK_REGIONAL, RANK_PROVINCIAL,
                RANK_NATIONAL, RANK_INDUSTRY_NATIONAL, RANK_INDUSTRY_PROVINCIAL);
    }

    /**
     * 获取指定竞赛等级下的可选获奖等级
     */
    public static List<String> getAvailableGrades(String rank) {
        if (rank == null) return List.of();
        return switch (rank) {
            case RANK_COLLEGE, RANK_SCHOOL ->
                List.of(GRADE_FIRST, GRADE_SECOND, GRADE_THIRD, GRADE_OTHER, GRADE_EXCELLENCE, GRADE_NO_AWARD);
            case RANK_REGIONAL, RANK_PROVINCIAL, RANK_INDUSTRY_NATIONAL ->
                List.of(GRADE_FIRST, GRADE_SECOND, GRADE_THIRD, GRADE_EXCELLENCE, GRADE_NO_AWARD, GRADE_PENDING);
            case RANK_NATIONAL ->
                List.of(GRADE_FIRST, GRADE_SECOND, GRADE_THIRD, GRADE_EXCELLENCE, GRADE_NO_AWARD);
            case RANK_INDUSTRY_PROVINCIAL ->
                List.of(GRADE_FIRST, GRADE_SECOND, GRADE_THIRD, GRADE_OTHER, GRADE_EXCELLENCE, GRADE_NO_AWARD);
            default -> List.of();
        };
    }

    /**
     * 获取得分规则列表（供前端 API 返回）
     */
    public static List<Map<String, Object>> getAllScoringRules() {
        List<Map<String, Object>> rules = new ArrayList<>();

        // 基础分规则
        Map<String, Object> orgRuleCollege = new LinkedHashMap<>();
        orgRuleCollege.put("type", "组织者基础分");
        orgRuleCollege.put("competitionRank", "院级/校级");
        orgRuleCollege.put("baseScore", ORG_BASE_COLLEGE);
        rules.add(orgRuleCollege);

        Map<String, Object> orgRuleHigher = new LinkedHashMap<>();
        orgRuleHigher.put("type", "组织者基础分");
        orgRuleHigher.put("competitionRank", "区级以上");
        orgRuleHigher.put("baseScore", ORG_BASE_HIGHER);
        rules.add(orgRuleHigher);

        Map<String, Object> advisorRuleCollege = new LinkedHashMap<>();
        advisorRuleCollege.put("type", "指导者基础分");
        advisorRuleCollege.put("competitionRank", "院级/校级");
        advisorRuleCollege.put("baseScore", ADVISOR_BASE_COLLEGE);
        rules.add(advisorRuleCollege);

        Map<String, Object> advisorRuleHigher = new LinkedHashMap<>();
        advisorRuleHigher.put("type", "指导者基础分");
        advisorRuleHigher.put("competitionRank", "区级以上");
        advisorRuleHigher.put("baseScore", ADVISOR_BASE_HIGHER);
        rules.add(advisorRuleHigher);

        // 获奖加分规则
        String[] ranks = {RANK_COLLEGE, RANK_REGIONAL, RANK_NATIONAL};
        for (String rank : ranks) {
            for (String grade : getAvailableGrades(rank)) {
                if (GRADE_NO_AWARD.equals(grade) || GRADE_PENDING.equals(grade)) continue;
                BigDecimal bonus = getAwardBonus(rank, grade);
                if (bonus.compareTo(BigDecimal.ZERO) > 0) {
                    Map<String, Object> rule = new LinkedHashMap<>();
                    rule.put("type", "指导老师获奖加分");
                    rule.put("competitionRank", rank);
                    rule.put("gradeName", grade);
                    rule.put("bonus", bonus);
                    rules.add(rule);
                }
            }
        }

        // 行业系数
        Map<String, Object> industryRule = new LinkedHashMap<>();
        industryRule.put("type", "行业系数");
        industryRule.put("note", "行业性全国奖按自治区级奖1.8倍计算；行业性省级奖按院级奖1.8倍计算");
        industryRule.put("multiplier", INDUSTRY_MULTIPLIER);
        rules.add(industryRule);

        return rules;
    }

    // ==================== 多奖项支持 ====================

    /** 竞赛等级排序值（数值越大级别越高） */
    private static final Map<String, Integer> RANK_ORDER = Map.of(
        RANK_COLLEGE, 1,
        RANK_SCHOOL, 2,
        RANK_INDUSTRY_PROVINCIAL, 3,
        RANK_REGIONAL, 4,
        RANK_PROVINCIAL, 5,
        RANK_INDUSTRY_NATIONAL, 6,
        RANK_NATIONAL, 7
    );

    /** 获奖等级排序值（数值越大等级越高） */
    private static final Map<String, Integer> GRADE_ORDER = Map.of(
        GRADE_EXCELLENCE, 1,
        GRADE_OTHER, 2,
        GRADE_THIRD, 3,
        GRADE_SECOND, 4,
        GRADE_FIRST, 5
    );

    /**
     * 获取竞赛等级的排序值
     */
    public static int getRankOrder(String rank) {
        return RANK_ORDER.getOrDefault(rank, 0);
    }

    /**
     * 获取获奖等级的排序值
     */
    public static int getGradeOrder(String grade) {
        return GRADE_ORDER.getOrDefault(grade, 0);
    }
}
