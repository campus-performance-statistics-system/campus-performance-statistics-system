package com.jgh.ghairouter.model.constants;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * 科研及教材业绩得分规则常量（v5）。
 *
 * 横向科研项目：
 *   项目负责人基础分：1分
 *   经费 ≤ 1万：0.5分/千元
 *   1万 < 经费 ≤ 6万：5分 + 超1万元部分1分/万元
 *   经费 > 6万：10分
 *   总得分 = 负责人基础分(1) + 经费得分
 *
 * 专利：
 *   发明专利：12分/项
 *   实用新型：4分/项
 *   分配规则：3人→6:2:2；4人及以上→主持者50%，参与者平均分配50%
 *
 * 教材及自编讲义：
 *   出版教材：2分/万字
 *   首次自编使用讲义：1分/万字
 *   三年后经申请批准后修改讲义：0.5分/万字
 */
public final class ResearchScoringConstants {

    private ResearchScoringConstants() {
    }

    // ==================== 子类型 ====================
    public static final String SUB_TYPE_HORIZONTAL_PROJECT = "horizontal_project";
    public static final String SUB_TYPE_PATENT = "patent";
    public static final String SUB_TYPE_TEXTBOOK = "textbook";

    public static final String SUB_TYPE_TEXT_HORIZONTAL = "横向科研项目";
    public static final String SUB_TYPE_TEXT_PATENT = "专利";
    public static final String SUB_TYPE_TEXT_TEXTBOOK = "教材及自编讲义";

    // ==================== 横向科研项目 ====================
    /** 项目负责人基础分 */
    public static final BigDecimal PROJECT_LEADER_BASE = new BigDecimal("1");

    /** 经费 ≤ 1万：0.5分/千元 */
    public static final BigDecimal FUNDING_RATE_LOW = new BigDecimal("0.5");
    public static final BigDecimal FUNDING_THRESHOLD_LOW = new BigDecimal("1"); // 万元

    /** 1万 < 经费 ≤ 6万：5分 + 超1万元部分1分/万元 */
    public static final BigDecimal FUNDING_BASE_MID = new BigDecimal("5");
    public static final BigDecimal FUNDING_RATE_MID = new BigDecimal("1");
    public static final BigDecimal FUNDING_THRESHOLD_HIGH = new BigDecimal("6"); // 万元

    /** 经费 > 6万：10分 */
    public static final BigDecimal FUNDING_CAP_HIGH = new BigDecimal("10");

    // ==================== 专利 ====================
    /** 发明专利：12分/项 */
    public static final BigDecimal PATENT_INVENTION = new BigDecimal("12");
    /** 实用新型：4分/项 */
    public static final BigDecimal PATENT_UTILITY_MODEL = new BigDecimal("4");

    public static final String PATENT_TYPE_INVENTION = "invention";
    public static final String PATENT_TYPE_UTILITY_MODEL = "utility_model";
    public static final String PATENT_TYPE_TEXT_INVENTION = "发明专利";
    public static final String PATENT_TYPE_TEXT_UTILITY_MODEL = "实用新型";

    // ==================== 教材 ====================
    /** 出版教材：2分/万字 */
    public static final BigDecimal TEXTBOOK_PUBLISHED = new BigDecimal("2");
    /** 首次自编讲义：1分/万字 */
    public static final BigDecimal TEXTBOOK_FIRST_HANDOUT = new BigDecimal("1");
    /** 修改讲义：0.5分/万字 */
    public static final BigDecimal TEXTBOOK_REVISED_HANDOUT = new BigDecimal("0.5");

    public static final String TEXTBOOK_TYPE_PUBLISHED = "published";
    public static final String TEXTBOOK_TYPE_FIRST_HANDOUT = "first_handout";
    public static final String TEXTBOOK_TYPE_REVISED_HANDOUT = "revised_handout";
    public static final String TEXTBOOK_TYPE_TEXT_PUBLISHED = "出版教材";
    public static final String TEXTBOOK_TYPE_TEXT_FIRST_HANDOUT = "首次自编讲义";
    public static final String TEXTBOOK_TYPE_TEXT_REVISED_HANDOUT = "修改讲义";

    // ==================== 分配比例 ====================
    /** 三人：负责人 60% */
    public static final BigDecimal RATIO_3_LEADER = new BigDecimal("0.60");
    /** 三人：成员各 20% */
    public static final BigDecimal RATIO_3_MEMBER = new BigDecimal("0.20");
    /** 四人及以上：负责人 50% */
    public static final BigDecimal RATIO_N_LEADER = new BigDecimal("0.50");

    // ==================== 计算方法 ====================

    /**
     * 计算横向科研项目总得分
     * @param fundingAmount 到位经费（万元）
     * @return 项目总得分（含负责人基础分1分）
     */
    public static BigDecimal calcHorizontalProjectScore(BigDecimal fundingAmount) {
        if (fundingAmount == null || fundingAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return PROJECT_LEADER_BASE; // 即使无经费，负责人也得1分
        }

        BigDecimal fundingScore;
        if (fundingAmount.compareTo(FUNDING_THRESHOLD_LOW) <= 0) {
            // ≤ 1万：0.5分/千元 = 0.5 * (fundingAmount * 10)
            BigDecimal thousands = fundingAmount.multiply(BigDecimal.TEN);
            fundingScore = FUNDING_RATE_LOW.multiply(thousands);
        } else if (fundingAmount.compareTo(FUNDING_THRESHOLD_HIGH) <= 0) {
            // 1万 < 经费 ≤ 6万：5分 + (fundingAmount - 1) * 1
            BigDecimal excess = fundingAmount.subtract(BigDecimal.ONE);
            fundingScore = FUNDING_BASE_MID.add(excess.multiply(FUNDING_RATE_MID));
        } else {
            // > 6万：10分
            fundingScore = FUNDING_CAP_HIGH;
        }

        return PROJECT_LEADER_BASE.add(fundingScore).setScale(3, RoundingMode.HALF_UP);
    }

    /**
     * 计算专利总得分
     * @param patentType 专利类别
     * @return 专利总得分
     */
    public static BigDecimal calcPatentScore(String patentType) {
        if (PATENT_TYPE_INVENTION.equals(patentType)) {
            return PATENT_INVENTION;
        }
        if (PATENT_TYPE_UTILITY_MODEL.equals(patentType)) {
            return PATENT_UTILITY_MODEL;
        }
        return BigDecimal.ZERO;
    }

    /**
     * 计算教材总得分
     * @param textbookType 教材类型
     * @param wordCount 字数（万）
     * @return 教材总得分
     */
    public static BigDecimal calcTextbookScore(String textbookType, BigDecimal wordCount) {
        if (wordCount == null || wordCount.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal ratePerWan;
        if (TEXTBOOK_TYPE_PUBLISHED.equals(textbookType)) {
            ratePerWan = TEXTBOOK_PUBLISHED;
        } else if (TEXTBOOK_TYPE_FIRST_HANDOUT.equals(textbookType)) {
            ratePerWan = TEXTBOOK_FIRST_HANDOUT;
        } else if (TEXTBOOK_TYPE_REVISED_HANDOUT.equals(textbookType)) {
            ratePerWan = TEXTBOOK_REVISED_HANDOUT;
        } else {
            return BigDecimal.ZERO;
        }

        return wordCount.multiply(ratePerWan).setScale(3, RoundingMode.HALF_UP);
    }

    /**
     * 分配得分给各成员
     * 三人：6:2:2；四人及以上：主持者50%，其余平分50%；两人及以下：全部归第一人
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
            // 两人：全部归第一人（负责人）
            if (leaderIndex >= 0 && leaderIndex < 2) {
                for (int i = 0; i < 2; i++) {
                    result.add(i == leaderIndex ? totalScore.setScale(3, RoundingMode.HALF_UP) : BigDecimal.ZERO);
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

    /**
     * 获取子类型显示名称
     */
    public static String getSubTypeText(String subType) {
        if (subType == null) return null;
        return switch (subType) {
            case SUB_TYPE_HORIZONTAL_PROJECT -> SUB_TYPE_TEXT_HORIZONTAL;
            case SUB_TYPE_PATENT -> SUB_TYPE_TEXT_PATENT;
            case SUB_TYPE_TEXTBOOK -> SUB_TYPE_TEXT_TEXTBOOK;
            default -> subType;
        };
    }

    /**
     * 获取专利类别显示名称
     */
    public static String getPatentTypeText(String patentType) {
        if (patentType == null) return null;
        return switch (patentType) {
            case PATENT_TYPE_INVENTION -> PATENT_TYPE_TEXT_INVENTION;
            case PATENT_TYPE_UTILITY_MODEL -> PATENT_TYPE_TEXT_UTILITY_MODEL;
            default -> patentType;
        };
    }

    /**
     * 获取教材类型显示名称
     */
    public static String getTextbookTypeText(String textbookType) {
        if (textbookType == null) return null;
        return switch (textbookType) {
            case TEXTBOOK_TYPE_PUBLISHED -> TEXTBOOK_TYPE_TEXT_PUBLISHED;
            case TEXTBOOK_TYPE_FIRST_HANDOUT -> TEXTBOOK_TYPE_TEXT_FIRST_HANDOUT;
            case TEXTBOOK_TYPE_REVISED_HANDOUT -> TEXTBOOK_TYPE_TEXT_REVISED_HANDOUT;
            default -> textbookType;
        };
    }

    /**
     * 获取所有子类型列表
     */
    public static List<String> getAvailableSubTypes() {
        return List.of(SUB_TYPE_HORIZONTAL_PROJECT, SUB_TYPE_PATENT, SUB_TYPE_TEXTBOOK);
    }

    /**
     * 获取得分规则列表（供前端 API 返回）
     */
    public static List<Map<String, Object>> getAllScoringRules() {
        List<Map<String, Object>> rules = new ArrayList<>();

        // 横向科研项目规则
        Map<String, Object> horizontalRule = new LinkedHashMap<>();
        horizontalRule.put("type", "横向科研项目");
        horizontalRule.put("leaderBase", PROJECT_LEADER_BASE);
        horizontalRule.put("note", "项目负责人基础分1分；≤1万：0.5分/千元；1-6万：5分+超1万元部分1分/万元；>6万：10分");
        rules.add(horizontalRule);

        // 专利规则
        Map<String, Object> patentRule = new LinkedHashMap<>();
        patentRule.put("type", "专利");
        patentRule.put("inventionScore", PATENT_INVENTION);
        patentRule.put("utilityModelScore", PATENT_UTILITY_MODEL);
        patentRule.put("note", "发明专利12分/项；实用新型4分/项。3人按6:2:2分配；4人及以上主持者50%，其余平分50%");
        rules.add(patentRule);

        // 教材规则
        Map<String, Object> textbookRule = new LinkedHashMap<>();
        textbookRule.put("type", "教材及自编讲义");
        textbookRule.put("publishedRate", TEXTBOOK_PUBLISHED);
        textbookRule.put("firstHandoutRate", TEXTBOOK_FIRST_HANDOUT);
        textbookRule.put("revisedHandoutRate", TEXTBOOK_REVISED_HANDOUT);
        textbookRule.put("note", "出版教材2分/万字；首次自编讲义1分/万字；修改讲义0.5分/万字");
        rules.add(textbookRule);

        return rules;
    }
}
