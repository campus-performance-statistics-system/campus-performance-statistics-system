package com.jgh.ghairouter.service.impl;

import cn.hutool.core.util.StrUtil;
import com.jgh.ghairouter.exception.BusinessException;
import com.jgh.ghairouter.exception.ErrorCode;
import com.jgh.ghairouter.mapper.InnovationEntrepreneurshipRecordMapper;
import com.jgh.ghairouter.mapper.PartTimeClassAdvisorRecordMapper;
import com.jgh.ghairouter.mapper.PartTimeClassAdvisorScoreMapper;
import com.jgh.ghairouter.mapper.ResearchAchievementRecordMapper;
import com.jgh.ghairouter.mapper.StudentCompetitionRecordMapper;
import com.jgh.ghairouter.mapper.TeacherCompetitionRecordMapper;
import com.jgh.ghairouter.mapper.SportsEventRecordMapper;
import com.jgh.ghairouter.mapper.SportsEventScoreMapper;
import com.jgh.ghairouter.mapper.TeachingReformRecordMapper;
import com.jgh.ghairouter.mapper.ThesisRecordMapper;
import com.jgh.ghairouter.mapper.TrainingGuidanceRecordMapper;
import com.jgh.ghairouter.mapper.UserMapper;
import com.jgh.ghairouter.model.entity.InnovationEntrepreneurshipRecord;
import com.jgh.ghairouter.model.entity.PartTimeClassAdvisorRecord;
import com.jgh.ghairouter.model.entity.ResearchAchievementRecord;
import com.jgh.ghairouter.model.entity.SportsEventRecord;
import com.jgh.ghairouter.model.entity.StudentCompetitionRecord;
import com.jgh.ghairouter.model.entity.TeacherCompetitionRecord;
import com.jgh.ghairouter.model.entity.TeachingReformRecord;
import com.jgh.ghairouter.model.entity.ThesisRecord;
import com.jgh.ghairouter.model.entity.TrainingGuidanceRecord;
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
    private TrainingGuidanceRecordMapper trainingRecordMapper;

    @Resource
    private ResearchAchievementRecordMapper researchRecordMapper;

    @Resource
    private InnovationEntrepreneurshipRecordMapper innovationRecordMapper;

    @Resource
    private TeachingReformRecordMapper teachingReformRecordMapper;

    @Resource
    private ThesisRecordMapper thesisRecordMapper;

    @Resource
    private SportsEventRecordMapper sportsRecordMapper;

    @Resource
    private SportsEventScoreMapper sportsScoreMapper;

    @Resource
    private PartTimeClassAdvisorRecordMapper partTimeAdvisorRecordMapper;

    @Resource
    private PartTimeClassAdvisorScoreMapper partTimeAdvisorScoreMapper;

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
     * 指导实训总分（training_guidance_score 表）
     */
    private static final String TRAINING_SCORE_SQL = """
            SELECT
              u.id AS user_id,
              u.user_name,
              COALESCE(SUM(tscore.score), 0) AS total_score
            FROM user u
            INNER JOIN training_guidance_score tscore ON u.id = tscore.user_id AND tscore.is_delete = 0
            WHERE u.is_delete = 0
            GROUP BY u.id, u.user_name
            """;

    /**
     * 科研及教材业绩总分（research_achievement_score 表）
     */
    private static final String RESEARCH_SCORE_SQL = """
            SELECT
              u.id AS user_id,
              u.user_name,
              COALESCE(SUM(rscore.score), 0) AS total_score
            FROM user u
            INNER JOIN research_achievement_score rscore ON u.id = rscore.user_id AND rscore.is_delete = 0
            WHERE u.is_delete = 0
            GROUP BY u.id, u.user_name
            """;

    /**
     * 大创业绩总分（innovation_entrepreneurship_score 表）
     */
    private static final String INNOVATION_SCORE_SQL = """
            SELECT
              u.id AS user_id,
              u.user_name,
              COALESCE(SUM(iscore.score), 0) AS total_score
            FROM user u
            INNER JOIN innovation_entrepreneurship_score iscore ON u.id = iscore.user_id AND iscore.is_delete = 0
            WHERE u.is_delete = 0
            GROUP BY u.id, u.user_name
            """;

    /**
     * 教改科研项目业绩总分（teaching_reform_score 表）
     */
    private static final String TEACHING_REFORM_SCORE_SQL = """
            SELECT
              u.id AS user_id,
              u.user_name,
              COALESCE(SUM(trscore.score), 0) AS total_score
            FROM user u
            INNER JOIN teaching_reform_score trscore ON u.id = trscore.user_id AND trscore.is_delete = 0
            WHERE u.is_delete = 0
            GROUP BY u.id, u.user_name
            """;

    /**
     * 论文业绩总分（thesis_score 表）
     */
    private static final String THESIS_SCORE_SQL = """
            SELECT
              u.id AS user_id,
              u.user_name,
              COALESCE(SUM(tscore.score), 0) AS total_score
            FROM user u
            INNER JOIN thesis_score tscore ON u.id = tscore.user_id AND tscore.is_delete = 0
            WHERE u.is_delete = 0
            GROUP BY u.id, u.user_name
            """;

    /**
     * 体育比赛业绩总分（sports_event_score 表）
     */
    private static final String SPORTS_SCORE_SQL = """
            SELECT
              u.id AS user_id,
              u.user_name,
              COALESCE(SUM(sscore.score), 0) AS total_score
            FROM user u
            INNER JOIN sports_event_score sscore ON u.id = sscore.user_id AND sscore.is_delete = 0
            WHERE u.is_delete = 0
            GROUP BY u.id, u.user_name
            """;

    /**
     * 兼职班主任业绩总分（part_time_class_advisor_score 表）
     */
    private static final String ADVISOR_SCORE_SQL = """
            SELECT
              u.id AS user_id,
              u.user_name,
              COALESCE(SUM(ascore.score), 0) AS total_score
            FROM user u
            INNER JOIN part_time_class_advisor_score ascore ON u.id = ascore.user_id AND ascore.is_delete = 0
            WHERE u.is_delete = 0
            GROUP BY u.id, u.user_name
            """;

    /**
     * 所有比赛总分（九类得分合并）
     */
    private static final String ALL_SCORE_SQL = """
            SELECT
              u.id AS user_id,
              u.user_name,
              COALESCE(tcs.teacher_score, 0) + COALESCE(ascore.student_score, 0) + COALESCE(tscore.training_score, 0) + COALESCE(rscore.research_score, 0) + COALESCE(iscore.innovation_score, 0) + COALESCE(trscore.teaching_reform_score, 0) + COALESCE(thscore.thesis_score, 0) + COALESCE(sscore.sports_score, 0) + COALESCE(advscore.advisor_score, 0) AS total_score
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
            LEFT JOIN (
              SELECT
                user_id,
                SUM(score) AS training_score
              FROM training_guidance_score
              WHERE is_delete = 0
              GROUP BY user_id
            ) tscore ON u.id = tscore.user_id
            LEFT JOIN (
              SELECT
                user_id,
                SUM(score) AS research_score
              FROM research_achievement_score
              WHERE is_delete = 0
              GROUP BY user_id
            ) rscore ON u.id = rscore.user_id
            LEFT JOIN (
              SELECT
                user_id,
                SUM(score) AS innovation_score
              FROM innovation_entrepreneurship_score
              WHERE is_delete = 0
              GROUP BY user_id
            ) iscore ON u.id = iscore.user_id
            LEFT JOIN (
              SELECT
                user_id,
                SUM(score) AS teaching_reform_score
              FROM teaching_reform_score
              WHERE is_delete = 0
              GROUP BY user_id
            ) trscore ON u.id = trscore.user_id
            LEFT JOIN (
              SELECT
                user_id,
                SUM(score) AS thesis_score
              FROM thesis_score
              WHERE is_delete = 0
              GROUP BY user_id
            ) thscore ON u.id = thscore.user_id
            LEFT JOIN (
              SELECT
                user_id,
                SUM(score) AS sports_score
              FROM sports_event_score
              WHERE is_delete = 0
              GROUP BY user_id
            ) sscore ON u.id = sscore.user_id
            LEFT JOIN (
              SELECT
                user_id,
                SUM(score) AS advisor_score
              FROM part_time_class_advisor_score
              WHERE is_delete = 0
              GROUP BY user_id
            ) advscore ON u.id = advscore.user_id
            WHERE u.is_delete = 0
              AND (tcs.teacher_score IS NOT NULL OR ascore.student_score IS NOT NULL OR tscore.training_score IS NOT NULL OR rscore.research_score IS NOT NULL OR iscore.innovation_score IS NOT NULL OR trscore.teaching_reform_score IS NOT NULL OR thscore.thesis_score IS NOT NULL OR sscore.sports_score IS NOT NULL OR advscore.advisor_score IS NOT NULL)
            """;

    @Override
    public List<UserScoreStatisticsVO> getUserScoreStatistics(String type, String sortOrder, String userName) {
        String sql;
        if ("teacher".equals(type)) {
            sql = TEACHER_SCORE_SQL;
        } else if ("student".equals(type)) {
            sql = STUDENT_SCORE_SQL;
        } else if ("training".equals(type)) {
            sql = TRAINING_SCORE_SQL;
        } else if ("research".equals(type)) {
            sql = RESEARCH_SCORE_SQL;
        } else if ("innovation".equals(type)) {
            sql = INNOVATION_SCORE_SQL;
        } else if ("teachingReform".equals(type)) {
            sql = TEACHING_REFORM_SCORE_SQL;
        } else if ("thesis".equals(type)) {
            sql = THESIS_SCORE_SQL;
        } else if ("sports".equals(type)) {
            sql = SPORTS_SCORE_SQL;
        } else if ("advisor".equals(type)) {
            sql = ADVISOR_SCORE_SQL;
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
                String topic = StrUtil.isNotBlank(record.getCompetitionTopic())
                        ? "-" + sanitizeFilename(record.getCompetitionTopic()) : "";
                String userName = sanitizeFilename(getUserName(record.getUserId()));
                String fileName = seq + "-" + competitionName + topic + "-" + userName + ".png";
                String zipPath = "所有附件/指导学生科技竞赛/" + fileName;

                byte[] imageBytes = decodeBase64(record.getProofImageData(), record.getId());
                if (imageBytes == null) continue;

                java.util.zip.ZipEntry entry = new java.util.zip.ZipEntry(zipPath);
                zos.putNextEntry(entry);
                zos.write(imageBytes);
                zos.closeEntry();
                seq++;
            }

            // ---- 科研及教材业绩 ----
            List<ResearchAchievementRecord> researchRecords = researchRecordMapper.selectListByQuery(
                    QueryWrapper.create().orderBy("create_time", true));
            seq = 1;
            for (ResearchAchievementRecord record : researchRecords) {
                if (StrUtil.isBlank(record.getProofImageData())) {
                    continue;
                }
                String achievementName = sanitizeFilename(
                        StrUtil.isNotBlank(record.getAchievementName())
                                ? record.getAchievementName() : "未知成果");
                String userName = sanitizeFilename(getUserName(record.getUserId()));
                String fileName = seq + "-" + achievementName + "-" + userName + ".png";
                String zipPath = "所有附件/科研及教材业绩/" + fileName;

                byte[] imageBytes = decodeBase64(record.getProofImageData(), record.getId());
                if (imageBytes == null) continue;

                java.util.zip.ZipEntry entry = new java.util.zip.ZipEntry(zipPath);
                zos.putNextEntry(entry);
                zos.write(imageBytes);
                zos.closeEntry();
                seq++;
            }

            // ---- 指导实训 ----
            List<TrainingGuidanceRecord> trainingRecords = trainingRecordMapper.selectListByQuery(
                    QueryWrapper.create().orderBy("create_time", true));
            seq = 1;
            for (TrainingGuidanceRecord record : trainingRecords) {
                if (StrUtil.isBlank(record.getProofImageData())) {
                    continue;
                }
                String trainingName = sanitizeFilename(
                        StrUtil.isNotBlank(record.getTrainingName())
                                ? record.getTrainingName() : "未知实训");
                String semester = StrUtil.isNotBlank(record.getSemester())
                        ? "-" + sanitizeFilename(record.getSemester()) : "";
                String userName = sanitizeFilename(getUserName(record.getUserId()));
                String fileName = seq + "-" + trainingName + semester + "-" + userName + ".png";
                String zipPath = "所有附件/指导实训/" + fileName;

                byte[] imageBytes = decodeBase64(record.getProofImageData(), record.getId());
                if (imageBytes == null) continue;

                java.util.zip.ZipEntry entry = new java.util.zip.ZipEntry(zipPath);
                zos.putNextEntry(entry);
                zos.write(imageBytes);
                zos.closeEntry();
                seq++;
            }

            // ---- 大创业绩（v6） ----
            List<InnovationEntrepreneurshipRecord> innovationRecords = innovationRecordMapper.selectListByQuery(
                    QueryWrapper.create().orderBy("create_time", true));
            seq = 1;
            for (InnovationEntrepreneurshipRecord record : innovationRecords) {
                if (StrUtil.isBlank(record.getProofImageData())) {
                    continue;
                }
                String projectName = sanitizeFilename(
                        StrUtil.isNotBlank(record.getProjectName())
                                ? record.getProjectName() : "未知项目");
                String userName = sanitizeFilename(getUserName(record.getUserId()));
                String fileName = seq + "-" + projectName + "-" + userName + ".png";
                String zipPath = "所有附件/大创业绩/" + fileName;

                byte[] imageBytes = decodeBase64(record.getProofImageData(), record.getId());
                if (imageBytes == null) continue;

                java.util.zip.ZipEntry entry = new java.util.zip.ZipEntry(zipPath);
                zos.putNextEntry(entry);
                zos.write(imageBytes);
                zos.closeEntry();
                seq++;
            }

            // ---- 教改科研项目业绩（v7） ----
            List<TeachingReformRecord> teachingReformRecords = teachingReformRecordMapper.selectListByQuery(
                    QueryWrapper.create().orderBy("create_time", true));
            seq = 1;
            for (TeachingReformRecord record : teachingReformRecords) {
                if (StrUtil.isBlank(record.getProofImageData())) {
                    continue;
                }
                String projectName = sanitizeFilename(
                        StrUtil.isNotBlank(record.getProjectName())
                                ? record.getProjectName() : "未知项目");
                String userName = sanitizeFilename(getUserName(record.getUserId()));
                String fileName = seq + "-" + projectName + "-" + userName + ".png";
                String zipPath = "所有附件/教改科研项目业绩/" + fileName;

                byte[] imageBytes = decodeBase64(record.getProofImageData(), record.getId());
                if (imageBytes == null) continue;

                java.util.zip.ZipEntry entry = new java.util.zip.ZipEntry(zipPath);
                zos.putNextEntry(entry);
                zos.write(imageBytes);
                zos.closeEntry();
                seq++;
            }

            // ---- 论文业绩（v8） ----
            List<ThesisRecord> thesisRecords = thesisRecordMapper.selectListByQuery(
                    QueryWrapper.create().orderBy("create_time", true));
            seq = 1;
            for (ThesisRecord record : thesisRecords) {
                if (StrUtil.isBlank(record.getProofImageData())) {
                    continue;
                }
                String thesisName = sanitizeFilename(
                        StrUtil.isNotBlank(record.getThesisName())
                                ? record.getThesisName() : "未知论文");
                String userName = sanitizeFilename(getUserName(record.getUserId()));
                String fileName = seq + "-" + thesisName + "-" + userName + ".png";
                String zipPath = "所有附件/论文业绩/" + fileName;

                byte[] imageBytes = decodeBase64(record.getProofImageData(), record.getId());
                if (imageBytes == null) continue;

                java.util.zip.ZipEntry entry = new java.util.zip.ZipEntry(zipPath);
                zos.putNextEntry(entry);
                zos.write(imageBytes);
                zos.closeEntry();
                seq++;
            }

            // ---- 体育比赛业绩（v9） ----
            List<SportsEventRecord> sportsRecords = sportsRecordMapper.selectListByQuery(
                    QueryWrapper.create().orderBy("create_time", true));
            seq = 1;
            for (SportsEventRecord record : sportsRecords) {
                if (StrUtil.isBlank(record.getProofImageData())) {
                    continue;
                }
                String eventName = sanitizeFilename(
                        StrUtil.isNotBlank(record.getEventName())
                                ? record.getEventName() : "未知比赛项目");
                String userName = sanitizeFilename(getUserName(record.getUserId()));
                String fileName = seq + "-" + eventName + "-" + userName + ".png";
                String zipPath = "所有附件/体育比赛业绩/" + fileName;

                byte[] imageBytes = decodeBase64(record.getProofImageData(), record.getId());
                if (imageBytes == null) continue;

                java.util.zip.ZipEntry entry = new java.util.zip.ZipEntry(zipPath);
                zos.putNextEntry(entry);
                zos.write(imageBytes);
                zos.closeEntry();
                seq++;
            }

            // ---- 兼职班主任（v10） ----
            List<PartTimeClassAdvisorRecord> advisorRecords = partTimeAdvisorRecordMapper.selectListByQuery(
                    QueryWrapper.create().orderBy("create_time", true));
            seq = 1;
            for (PartTimeClassAdvisorRecord record : advisorRecords) {
                if (StrUtil.isBlank(record.getProofImageData())) {
                    continue;
                }
                String teacherName = sanitizeFilename(
                        StrUtil.isNotBlank(record.getTeacherName())
                                ? record.getTeacherName() : "未知教师");
                String classId = sanitizeFilename(
                        StrUtil.isNotBlank(record.getClassId())
                                ? "-" + record.getClassId() : "");
                String userName = sanitizeFilename(getUserName(record.getUserId()));
                String fileName = seq + "-" + teacherName + classId + "-" + userName + ".png";
                String zipPath = "所有附件/兼职班主任/" + fileName;

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
