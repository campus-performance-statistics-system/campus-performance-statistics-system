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
 * 统计管理服务实现
 */
@Slf4j
@Service
public class StatisticsServiceImpl implements StatisticsService {

    @Resource
    private JdbcTemplate jdbcTemplate;

    private static final String BASE_SQL = """
            SELECT
              u.id AS user_id,
              u.user_name,
              COALESCE(tcs.teacher_score, 0) AS teacher_score,
              COALESCE(ascore.student_score, 0) AS student_score,
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
            """;

    @Override
    public List<UserScoreStatisticsVO> getUserScoreStatistics(String type, String sortOrder) {
        StringBuilder sql = new StringBuilder(BASE_SQL);

        // 按类别筛选
        if ("teacher".equals(type)) {
            sql.append(" AND tcs.teacher_score IS NOT NULL AND tcs.teacher_score > 0");
        } else if ("student".equals(type)) {
            sql.append(" AND ascore.student_score IS NOT NULL AND ascore.student_score > 0");
        }
        // "all" 不过滤

        // 排序
        boolean asc = "ascend".equals(sortOrder);
        if ("teacher".equals(type)) {
            sql.append(" ORDER BY teacher_score ").append(asc ? "ASC" : "DESC");
        } else if ("student".equals(type)) {
            sql.append(" ORDER BY student_score ").append(asc ? "ASC" : "DESC");
        } else {
            sql.append(" ORDER BY total_score ").append(asc ? "ASC" : "DESC");
        }

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql.toString());
        List<UserScoreStatisticsVO> result = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            result.add(UserScoreStatisticsVO.builder()
                    .userId(toLong(row.get("user_id")))
                    .userName((String) row.get("user_name"))
                    .teacherScore(toBigDecimal(row.get("teacher_score")))
                    .studentScore(toBigDecimal(row.get("student_score")))
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
