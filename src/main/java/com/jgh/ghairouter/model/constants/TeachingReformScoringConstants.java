package com.jgh.ghairouter.model.constants;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * 教改科研项目业绩得分规则常量（v7）。
 *
 * 项目类型：
 *   教育厅教改工程项目（省级）：12分/项
 *   中青年教师基础能力提升项目（厅级）：10分/项
 *   校级科研项目：6分/项
 *   校级课程思政项目：6分/项
 *   未获批/未下文：2分/项（仅申报人）
 *
 * 分配规则（与教师获奖一致）：
 *   单人：100%
 *   两人：7:3（负责人70%，参与者30%）
 *   三人：6:2:2（负责人60%，其余20%）
 *   四人及以上：负责人50%，其余平均分配50%
 */
public final class TeachingReformScoringConstants {

    private TeachingReformScoringConstants() {
    }

    // ==================== 项目类型 ====================
    public static final String PROJECT_TYPE_PROVINCIAL_EDUCATION_REFORM = "provincial_education_reform";
    public static final String PROJECT_TYPE_YOUNG_TEACHER_BASIC = "young_teacher_basic";
    public static final String PROJECT_TYPE_UNIVERSITY_RESEARCH = "university_research";
    public static final String PROJECT_TYPE_UNIVERSITY_COURSE_IDEOLOGY = "university_course_ideology";

    public static final String PROJECT_TYPE_TEXT_PROVINCIAL_EDUCATION_REFORM = "教育厅教改工程项目";
    public static final String PROJECT_TYPE_TEXT_YOUNG_TEACHER_BASIC = "中青年教师基础能力提升项目";
    public static final String PROJECT_TYPE_TEXT_UNIVERSITY_RESEARCH = "校级科研项目";
    public static final String PROJECT_TYPE_TEXT_UNIVERSITY_COURSE_IDEOLOGY = "校级课程思政项目";

    // ==================== 项目状态 ====================
    public static final String STATUS_APPROVED = "approved";
    public static final String STATUS_NOT_APPROVED = "not_approved";
    public static final String STATUS_PENDING_DECISION = "pending_decision";

    public static final String STATUS_TEXT_APPROVED = "获批立项";
    public static final String STATUS_TEXT_NOT_APPROVED = "未获批";
    public static final String STATUS_TEXT_PENDING_DECISION = "未下文";

    // ==================== 项目总分 ====================
    /** 教育厅教改工程项目总分 */
    public static final BigDecimal SCORE_PROVINCIAL_EDUCATION_REFORM = new BigDecimal("12");
    /** 中青年教师基础能力提升项目总分 */
    public static final BigDecimal SCORE_YOUNG_TEACHER_BASIC = new BigDecimal("10");
    /** 校级科研项目总分 */
    public static final BigDecimal SCORE_UNIVERSITY_RESEARCH = new BigDecimal("6");
    /** 校级课程思政项目总分 */
    public static final BigDecimal SCORE_UNIVERSITY_COURSE_IDEOLOGY = new BigDecimal("6");
    /** 未获批/未下文项目总分 */
    public static final BigDecimal SCORE_NOT_APPROVED = new BigDecimal("2");

    // ==================== 分配比例 ====================
    /** 两人：负责人 70% */
    public static final BigDecimal RATIO_2_LEADER = new BigDecimal("0.70");
    /** 三人：负责人 60% */
    public static final BigDecimal RATIO_3_LEADER = new BigDecimal("0.60");
    /** 四人及以上：负责人 50% */
    public static final BigDecimal RATIO_N_LEADER = new BigDecimal("0.50");

    // ==================== 计算方法 ====================

    /**
     * 根据项目类型和状态计算项目总得分
     */
    public static BigDecimal calcProjectScore(String projectType, String projectStatus) {
        // 未获批或未下文：固定2分
        if (STATUS_NOT_APPROVED.equals(projectStatus) || STATUS_PENDING_DECISION.equals(projectStatus)) {
            return SCORE_NOT_APPROVED;
        }
        // 获批立项：根据项目类型返回不同总分
        if (PROJECT_TYPE_PROVINCIAL_EDUCATION_REFORM.equals(projectType)) {
            return SCORE_PROVINCIAL_EDUCATION_REFORM;
        }
        if (PROJECT_TYPE_YOUNG_TEACHER_BASIC.equals(projectType)) {
            return SCORE_YOUNG_TEACHER_BASIC;
        }
        if (PROJECT_TYPE_UNIVERSITY_RESEARCH.equals(projectType)) {
            return SCORE_UNIVERSITY_RESEARCH;
        }
        if (PROJECT_TYPE_UNIVERSITY_COURSE_IDEOLOGY.equals(projectType)) {
            return SCORE_UNIVERSITY_COURSE_IDEOLOGY;
        }
        return BigDecimal.ZERO;
    }

    /**
     * 分配得分给各项目成员
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

    public static String getProjectTypeText(String projectType) {
        if (projectType == null) return null;
        return switch (projectType) {
            case PROJECT_TYPE_PROVINCIAL_EDUCATION_REFORM -> PROJECT_TYPE_TEXT_PROVINCIAL_EDUCATION_REFORM;
            case PROJECT_TYPE_YOUNG_TEACHER_BASIC -> PROJECT_TYPE_TEXT_YOUNG_TEACHER_BASIC;
            case PROJECT_TYPE_UNIVERSITY_RESEARCH -> PROJECT_TYPE_TEXT_UNIVERSITY_RESEARCH;
            case PROJECT_TYPE_UNIVERSITY_COURSE_IDEOLOGY -> PROJECT_TYPE_TEXT_UNIVERSITY_COURSE_IDEOLOGY;
            default -> projectType;
        };
    }

    public static String getProjectStatusText(String projectStatus) {
        if (projectStatus == null) return null;
        return switch (projectStatus) {
            case STATUS_APPROVED -> STATUS_TEXT_APPROVED;
            case STATUS_NOT_APPROVED -> STATUS_TEXT_NOT_APPROVED;
            case STATUS_PENDING_DECISION -> STATUS_TEXT_PENDING_DECISION;
            default -> projectStatus;
        };
    }

    public static List<String> getAvailableProjectTypes() {
        return List.of(PROJECT_TYPE_PROVINCIAL_EDUCATION_REFORM, PROJECT_TYPE_YOUNG_TEACHER_BASIC,
                PROJECT_TYPE_UNIVERSITY_RESEARCH, PROJECT_TYPE_UNIVERSITY_COURSE_IDEOLOGY);
    }

    public static List<String> getAvailableStatuses() {
        return List.of(STATUS_APPROVED, STATUS_NOT_APPROVED, STATUS_PENDING_DECISION);
    }

    /**
     * 获取得分规则列表（供前端 API 返回）
     */
    public static List<Map<String, Object>> getAllScoringRules() {
        List<Map<String, Object>> rules = new ArrayList<>();

        Map<String, Object> rule = new LinkedHashMap<>();
        rule.put("type", "教改科研项目业绩");
        rule.put("provincialEducationReformScore", SCORE_PROVINCIAL_EDUCATION_REFORM);
        rule.put("youngTeacherBasicScore", SCORE_YOUNG_TEACHER_BASIC);
        rule.put("universityResearchScore", SCORE_UNIVERSITY_RESEARCH);
        rule.put("universityCourseIdeologyScore", SCORE_UNIVERSITY_COURSE_IDEOLOGY);
        rule.put("notApprovedScore", SCORE_NOT_APPROVED);
        rule.put("distribution", "1人:100%; 2人:7:3; 3人:6:2:2; 4人及以上:主持者50%,其余平分50%");
        rule.put("note", "获批项目按项目类型得分（省级教改12分、中青年10分、校级科研/课程思政6分），未获批/未下文项目2分。");
        rules.add(rule);

        return rules;
    }
}
