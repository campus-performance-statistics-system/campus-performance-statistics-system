package com.jgh.ghairouter.service.impl;

import com.jgh.ghairouter.model.vo.UserScoreStatisticsVO;
import com.jgh.ghairouter.service.StatisticsService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 统计管理服务实现。
 * 根据分类从对应的得分表中汇总用户得分。
 */
@Slf4j
@Service
public class StatisticsServiceImpl implements StatisticsService {

    @Resource
    private JdbcTemplate jdbcTemplate;

    /**
     * 教师获奖总分（teacher_competition_score 表，负责人 +2 基础分）
     */
    private static final String TEACHER_SCORE_SQL = """
            SELECT
              u.id AS user_id,
              u.user_name,
              COALESCE(SUM(
                tcs.personal_score + CASE WHEN tcs.is_leader = 1 THEN 2 ELSE 0 END
              ), 0) AS total_score
            FROM user u
            INNER JOIN teacher_competition_score tcs ON u.id = tcs.user_id AND tcs.is_delete = 0
            WHERE u.is_delete = 0
            GROUP BY u.id, u.user_name
            """;

    /**
     * 学生科技竞赛总分（advisor_score 表）
     */
    private static final String STUDENT_SCORE_SQL = """
            SELECT
              u.id AS user_id,
              u.user_name,
              COALESCE(SUM(ascore.total_score), 0) AS total_score
            FROM user u
            INNER JOIN advisor_score ascore ON u.id = ascore.user_id AND ascore.is_delete = 0
            WHERE u.is_delete = 0
            GROUP BY u.id, u.user_name
            """;

    /**
     * 所有比赛总分（teacher_competition_score + advisor_score 合并）
     */
    private static final String ALL_SCORE_SQL = """
            SELECT
              u.id AS user_id,
              u.user_name,
              COALESCE(tcs.teacher_score, 0) + COALESCE(ascore.student_score, 0) AS total_score
            FROM user u
            LEFT JOIN (
              SELECT
                user_id,
                SUM(personal_score + CASE WHEN is_leader = 1 THEN 2 ELSE 0 END) AS teacher_score
              FROM teacher_competition_score
              WHERE is_delete = 0
              GROUP BY user_id
            ) tcs ON u.id = tcs.user_id
            LEFT JOIN (
              SELECT
                user_id,
                SUM(total_score) AS student_score
              FROM advisor_score
              WHERE is_delete = 0
              GROUP BY user_id
            ) ascore ON u.id = ascore.user_id
            WHERE u.is_delete = 0
              AND (tcs.teacher_score IS NOT NULL OR ascore.student_score IS NOT NULL)
            """;

    @Override
    public List<UserScoreStatisticsVO> getUserScoreStatistics(String type, String sortOrder) {
        String sql;
        if ("teacher".equals(type)) {
            sql = TEACHER_SCORE_SQL;
        } else if ("student".equals(type)) {
            sql = STUDENT_SCORE_SQL;
        } else {
            // "all" — 合并所有
            sql = ALL_SCORE_SQL;
        }

        boolean asc = "ascend".equals(sortOrder);
        sql += " ORDER BY total_score " + (asc ? "ASC" : "DESC");

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);
        List<UserScoreStatisticsVO> result = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            result.add(UserScoreStatisticsVO.builder()
                    .userId(toLong(row.get("user_id")))
                    .userName((String) row.get("user_name"))
                    .totalScore(toBigDecimal(row.get("total_score")))
                    .build());
        }
        return result;
    }

    private Long toLong(Object val) {
        if (val == null) return null;
        if (val instanceof Long l) return l;
        if (val instanceof Number n) return n.longValue();
        return Long.valueOf(val.toString());
    }

    private BigDecimal toBigDecimal(Object val) {
        if (val == null) return BigDecimal.ZERO;
        if (val instanceof BigDecimal bd) return bd;
        if (val instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        return new BigDecimal(val.toString());
    }
}
