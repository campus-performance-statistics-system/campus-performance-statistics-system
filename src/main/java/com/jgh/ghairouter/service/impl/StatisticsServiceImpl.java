package com.jgh.ghairouter.service.impl;

import cn.hutool.core.util.StrUtil;
import com.jgh.ghairouter.exception.BusinessException;
import com.jgh.ghairouter.exception.ErrorCode;
import com.jgh.ghairouter.mapper.StudentCompetitionRecordMapper;
import com.jgh.ghairouter.mapper.TeacherCompetitionRecordMapper;
import com.jgh.ghairouter.mapper.UserMapper;
import com.jgh.ghairouter.model.entity.StudentCompetitionRecord;
import com.jgh.ghairouter.model.entity.TeacherCompetitionRecord;
import com.jgh.ghairouter.model.entity.User;
import com.jgh.ghairouter.model.vo.UserScoreStatisticsVO;
import com.jgh.ghairouter.service.StatisticsService;
import com.mybatisflex.core.query.QueryWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
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

    @Resource
    private TeacherCompetitionRecordMapper teacherRecordMapper;

    @Resource
    private StudentCompetitionRecordMapper studentRecordMapper;

    @Resource
    private UserMapper userMapper;

    /**
     * 教师获奖总分（user_competition_score 表，负责人 +2 基础分）
     */
    private static final String TEACHER_SCORE_SQL = """
            SELECT
              u.id AS user_id,
              u.user_name,
              COALESCE(SUM(
                tcs.personal_score + CASE WHEN tcs.is_leader = 1 THEN 2 ELSE 0 END
              ), 0) AS total_score
            FROM user u
            INNER JOIN user_competition_score tcs ON u.id = tcs.user_id AND tcs.is_delete = 0
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
     * 所有比赛总分（user_competition_score + advisor_score 合并）
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
              FROM user_competition_score
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
    public List<UserScoreStatisticsVO> getUserScoreStatistics(String type, String sortOrder, String userName) {
        String sql;
        if ("teacher".equals(type)) {
            sql = TEACHER_SCORE_SQL;
        } else if ("student".equals(type)) {
            sql = STUDENT_SCORE_SQL;
        } else {
            // "all" — 合并所有
            sql = ALL_SCORE_SQL;
        }

        // 参数列表（用于模糊查询用户名）
        List<Object> params = new ArrayList<>();

        // 如果指定了用户名，在 WHERE 条件后追加模糊匹配
        if (userName != null && !userName.isBlank()) {
            sql = sql.replace("WHERE u.is_delete = 0",
                    "WHERE u.is_delete = 0 AND u.user_name LIKE ?");
            params.add("%" + userName.trim() + "%");
        }

        boolean asc = "ascend".equals(sortOrder);
        sql += " ORDER BY total_score " + (asc ? "ASC" : "DESC");

        List<Map<String, Object>> rows;
        if (params.isEmpty()) {
            rows = jdbcTemplate.queryForList(sql);
        } else {
            rows = jdbcTemplate.queryForList(sql, params.toArray());
        }

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

    // ==================== 导出附件ZIP ====================

    @Override
    public byte[] exportAllAttachmentsToZip() {
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        try (java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(bos)) {

            // ---- 教师获奖 ----
            List<TeacherCompetitionRecord> teacherRecords = teacherRecordMapper.selectListByQuery(
                    QueryWrapper.create().orderBy("create_time", true));
            int seq = 1;
            for (TeacherCompetitionRecord record : teacherRecords) {
                if (StrUtil.isBlank(record.getProofImageData())) {
                    continue;
                }
                String competitionName = sanitizeFilename(
                        StrUtil.isNotBlank(record.getCompetitionName())
                                ? record.getCompetitionName() : "未知比赛");
                String userName = sanitizeFilename(getUserName(record.getUserId()));
                String fileName = seq + "-" + competitionName + "-" + userName + ".png";
                String zipPath = "所有附件/教师获奖/" + fileName;

                byte[] imageBytes = decodeBase64(record.getProofImageData(), record.getId());
                if (imageBytes == null) continue;

                java.util.zip.ZipEntry entry = new java.util.zip.ZipEntry(zipPath);
                zos.putNextEntry(entry);
                zos.write(imageBytes);
                zos.closeEntry();
                seq++;
            }

            // ---- 指导学生科技竞赛 ----
            List<StudentCompetitionRecord> studentRecords = studentRecordMapper.selectListByQuery(
                    QueryWrapper.create().orderBy("create_time", true));
            seq = 1;
            for (StudentCompetitionRecord record : studentRecords) {
                if (StrUtil.isBlank(record.getProofImageData())) {
                    continue;
                }
                String competitionName = sanitizeFilename(
                        StrUtil.isNotBlank(record.getCompetitionName())
                                ? record.getCompetitionName() : "未知比赛");
                String userName = sanitizeFilename(getUserName(record.getUserId()));
                String fileName = seq + "-" + competitionName + "-" + userName + ".png";
                String zipPath = "所有附件/指导学生科技竞赛/" + fileName;

                byte[] imageBytes = decodeBase64(record.getProofImageData(), record.getId());
                if (imageBytes == null) continue;

                java.util.zip.ZipEntry entry = new java.util.zip.ZipEntry(zipPath);
                zos.putNextEntry(entry);
                zos.write(imageBytes);
                zos.closeEntry();
                seq++;
            }

            zos.finish();
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "附件压缩包生成失败: " + e.getMessage());
        }
        return bos.toByteArray();
    }

    private String getUserName(Long userId) {
        if (userId == null) return "未知用户";
        User user = userMapper.selectOneById(userId);
        return user != null && StrUtil.isNotBlank(user.getUserName()) ? user.getUserName() : "未知用户";
    }

    private byte[] decodeBase64(String data, Long recordId) {
        try {
            return java.util.Base64.getDecoder().decode(data);
        } catch (IllegalArgumentException e) {
            log.warn("附件 base64 解码失败: recordId={}", recordId);
            return null;
        }
    }

    private String sanitizeFilename(String name) {
        if (StrUtil.isBlank(name)) return "未知";
        return name.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
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
