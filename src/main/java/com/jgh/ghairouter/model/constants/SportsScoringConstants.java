package com.jgh.ghairouter.model.constants;

import java.math.BigDecimal;
import java.util.*;

/**
 * 体育比赛业绩得分规则常量（v9）。
 *
 * 运动会项目（track_field）：
 *   基础参与分：1分/项
 *   获奖加分：前四名 +1分/项；后四名(5-8名) +0.5分/项
 *
 * 球类项目（ball_game）：
 *   基础参与分：1分/项
 *   获奖加分：1.5分/项
 */
public final class SportsScoringConstants {

    private SportsScoringConstants() {
    }

    // ==================== 项目类型 ====================
    public static final String EVENT_TYPE_TRACK_FIELD = "track_field";
    public static final String EVENT_TYPE_BALL_GAME = "ball_game";

    public static final String EVENT_TYPE_TEXT_TRACK_FIELD = "运动会项目";
    public static final String EVENT_TYPE_TEXT_BALL_GAME = "球类项目";

    // ==================== 球类结果 ====================
    public static final String RESULT_CHAMPION = "champion";
    public static final String RESULT_PLACED = "placed";
    public static final String RESULT_PARTICIPATED = "participated";

    public static final String RESULT_TEXT_CHAMPION = "冠军";
    public static final String RESULT_TEXT_PLACED = "获奖";
    public static final String RESULT_TEXT_PARTICIPATED = "参与";

    // ==================== 得分常量 ====================
    /** 基础参与分 */
    public static final BigDecimal BASE_SCORE = new BigDecimal("1");
    /** 运动会前四名加分 */
    public static final BigDecimal TRACK_TOP4_BONUS = new BigDecimal("1");
    /** 运动会后四名(5-8名)加分 */
    public static final BigDecimal TRACK_BOTTOM4_BONUS = new BigDecimal("0.5");
    /** 球类获奖加分 */
    public static final BigDecimal BALL_GAME_BONUS = new BigDecimal("1.5");

    // ==================== 计算方法 ====================

    /**
     * 计算运动会项目个人得分
     * @param placement 名次（1-8），null表示仅参与无名次
     * @return 个人得分
     */
    public static BigDecimal calcTrackFieldScore(Integer placement) {
        BigDecimal score = BASE_SCORE;
        if (placement != null) {
            if (placement >= 1 && placement <= 4) {
                score = score.add(TRACK_TOP4_BONUS);
            } else if (placement >= 5 && placement <= 8) {
                score = score.add(TRACK_BOTTOM4_BONUS);
            }
        }
        return score;
    }

    /**
     * 计算球类项目个人得分
     * @param result 球类结果：champion/placed/participated
     * @return 个人得分
     */
    public static BigDecimal calcBallGameScore(String result) {
        BigDecimal score = BASE_SCORE;
        if (RESULT_CHAMPION.equals(result) || RESULT_PLACED.equals(result)) {
            score = score.add(BALL_GAME_BONUS);
        }
        return score;
    }

    /**
     * 获取得分规则列表（供前端 API 返回）
     */
    public static List<Map<String, Object>> getAllScoringRules() {
        List<Map<String, Object>> rules = new ArrayList<>();

        Map<String, Object> rule = new LinkedHashMap<>();
        rule.put("type", "体育比赛业绩");
        rule.put("baseScore", BASE_SCORE);
        rule.put("trackTop4Bonus", TRACK_TOP4_BONUS);
        rule.put("trackBottom4Bonus", TRACK_BOTTOM4_BONUS);
        rule.put("ballGameBonus", BALL_GAME_BONUS);
        rule.put("note", "参加运动会及球类项目：1分/项类。" +
                "获奖增加：运动会前四名 1分/项；后四名 0.5分/项；球类 1.5分/项。");
        rules.add(rule);

        return rules;
    }

    // ==================== 显示名映射 ====================

    public static String getEventTypeText(String eventType) {
        if (eventType == null) return null;
        return switch (eventType) {
            case EVENT_TYPE_TRACK_FIELD -> EVENT_TYPE_TEXT_TRACK_FIELD;
            case EVENT_TYPE_BALL_GAME -> EVENT_TYPE_TEXT_BALL_GAME;
            default -> eventType;
        };
    }

    public static String getEventResultText(String result) {
        if (result == null) return null;
        return switch (result) {
            case RESULT_CHAMPION -> RESULT_TEXT_CHAMPION;
            case RESULT_PLACED -> RESULT_TEXT_PLACED;
            case RESULT_PARTICIPATED -> RESULT_TEXT_PARTICIPATED;
            default -> result;
        };
    }

    public static List<String> getAvailableEventTypes() {
        return List.of(EVENT_TYPE_TRACK_FIELD, EVENT_TYPE_BALL_GAME);
    }

    public static List<String> getAvailableEventResults() {
        return List.of(RESULT_CHAMPION, RESULT_PLACED, RESULT_PARTICIPATED);
    }
}
