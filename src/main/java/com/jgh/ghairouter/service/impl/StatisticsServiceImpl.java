package com.jgh.ghairouter.service.impl;

import cn.hutool.core.util.StrUtil;
import com.jgh.ghairouter.exception.BusinessException;
import com.jgh.ghairouter.exception.ErrorCode;
import com.jgh.ghairouter.mapper.InnovationEntrepreneurshipRecordMapper;
import com.jgh.ghairouter.mapper.InvigilationRecordMapper;
import com.jgh.ghairouter.mapper.OnlineEvaluationRecordMapper;
import com.jgh.ghairouter.mapper.PartTimeClassAdvisorRecordMapper;
import com.jgh.ghairouter.mapper.PartTimeClassAdvisorScoreMapper;
import com.jgh.ghairouter.mapper.CooperativeEnterpriseRecordMapper;
import com.jgh.ghairouter.mapper.RecommendedEmploymentRecordMapper;
import com.jgh.ghairouter.mapper.ExcellentGraduationProjectRecordMapper;
import com.jgh.ghairouter.mapper.SchoolEnterpriseTrainingRecordMapper;
import com.jgh.ghairouter.mapper.YoungTeacherGuidanceRecordMapper;
import com.jgh.ghairouter.mapper.ResearchAchievementRecordMapper;
import com.jgh.ghairouter.mapper.StudentCompetitionRecordMapper;
import com.jgh.ghairouter.mapper.TeacherCompetitionRecordMapper;
import com.jgh.ghairouter.mapper.SportsEventRecordMapper;
import com.jgh.ghairouter.mapper.SportsEventScoreMapper;
import com.jgh.ghairouter.mapper.TeachingReformRecordMapper;
import com.jgh.ghairouter.mapper.ThesisRecordMapper;
import com.jgh.ghairouter.mapper.TrainingGuidanceRecordMapper;
import com.jgh.ghairouter.mapper.UserMapper;
import com.jgh.ghairouter.model.entity.CooperativeEnterpriseRecord;
import com.jgh.ghairouter.model.entity.ExcellentGraduationProjectRecord;
import com.jgh.ghairouter.model.entity.SchoolEnterpriseTrainingRecord;
import com.jgh.ghairouter.model.entity.YoungTeacherGuidanceRecord;
import com.jgh.ghairouter.model.entity.InnovationEntrepreneurshipRecord;
import com.jgh.ghairouter.model.entity.InvigilationRecord;
import com.jgh.ghairouter.model.entity.OnlineEvaluationRecord;
import com.jgh.ghairouter.model.entity.PartTimeClassAdvisorRecord;
import com.jgh.ghairouter.model.entity.RecommendedEmploymentRecord;
import com.jgh.ghairouter.model.entity.ResearchAchievementRecord;
import com.jgh.ghairouter.model.entity.SportsEventRecord;
import com.jgh.ghairouter.model.entity.StudentCompetitionRecord;
import com.jgh.ghairouter.model.entity.TeacherCompetitionRecord;
import com.jgh.ghairouter.model.entity.TeachingReformRecord;
import com.jgh.ghairouter.model.entity.ThesisRecord;
import com.jgh.ghairouter.model.entity.TrainingGuidanceRecord;
import com.jgh.ghairouter.model.entity.User;
import com.jgh.ghairouter.model.vo.UserScoreStatisticsVO;
import com.jgh.ghairouter.service.*;
import com.mybatisflex.core.query.QueryWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
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
    private RecommendedEmploymentRecordMapper recommendedEmploymentRecordMapper;

    @Resource
    private CooperativeEnterpriseRecordMapper cooperativeEnterpriseRecordMapper;

    @Resource
    private YoungTeacherGuidanceRecordMapper youngTeacherGuidanceRecordMapper;

    @Resource
    private ExcellentGraduationProjectRecordMapper excellentGraduationProjectRecordMapper;

    @Resource
    private SchoolEnterpriseTrainingRecordMapper schoolEnterpriseTrainingRecordMapper;

    @Resource
    private InvigilationRecordMapper invigilationRecordMapper;

    @Resource
    private OnlineEvaluationRecordMapper onlineEvaluationRecordMapper;

    @Resource
    private UserMapper userMapper;

    // ==================== 各分类服务（用于统一Excel导出） ====================
    @Resource
    private TeacherCompetitionRecordService teacherRecordService;
    @Resource
    private StudentCompetitionRecordService studentRecordService;
    @Resource
    private TrainingGuidanceRecordService trainingRecordService;
    @Resource
    private ResearchAchievementRecordService researchRecordService;
    @Resource
    private InnovationEntrepreneurshipRecordService innovationRecordService;
    @Resource
    private TeachingReformRecordService teachingReformRecordService;
    @Resource
    private ThesisRecordService thesisRecordService;
    @Resource
    private SportsEventRecordService sportsRecordService;
    @Resource
    private PartTimeClassAdvisorRecordService advisorRecordService;
    @Resource
    private InvigilationRecordService invigilationRecordService;
    @Resource
    private OnlineEvaluationRecordService onlineEvaluationRecordService;

    @Resource
    private RecommendedEmploymentRecordService recommendedEmploymentRecordService;

    @Resource
    private CooperativeEnterpriseRecordService cooperativeEnterpriseRecordService;

    @Resource
    private YoungTeacherGuidanceRecordService youngTeacherGuidanceRecordService;

    @Resource
    private ExcellentGraduationProjectRecordService excellentGraduationProjectRecordService;

    @Resource
    private SchoolEnterpriseTrainingRecordService schoolEnterpriseTrainingRecordService;

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

            // ---- 1-教师获奖 ----
            String dir1 = "所有附件/1-教师获奖/";
            ensureZipDir(zos, dir1);
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
                String zipPath = dir1 + fileName;

                byte[] imageBytes = decodeBase64(record.getProofImageData(), record.getId());
                if (imageBytes == null) continue;

                java.util.zip.ZipEntry entry = new java.util.zip.ZipEntry(zipPath);
                zos.putNextEntry(entry);
                zos.write(imageBytes);
                zos.closeEntry();
                seq++;
            }

            // ---- 2-指导学生科技竞赛 ----
            String dir2 = "所有附件/2-指导学生科技竞赛/";
            ensureZipDir(zos, dir2);
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
                String zipPath = dir2 + fileName;

                byte[] imageBytes = decodeBase64(record.getProofImageData(), record.getId());
                if (imageBytes == null) continue;

                java.util.zip.ZipEntry entry = new java.util.zip.ZipEntry(zipPath);
                zos.putNextEntry(entry);
                zos.write(imageBytes);
                zos.closeEntry();
                seq++;
            }

            // ---- 3-指导实训 ----
            String dir3 = "所有附件/3-指导实训/";
            ensureZipDir(zos, dir3);
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
                String zipPath = dir3 + fileName;

                byte[] imageBytes = decodeBase64(record.getProofImageData(), record.getId());
                if (imageBytes == null) continue;

                java.util.zip.ZipEntry entry = new java.util.zip.ZipEntry(zipPath);
                zos.putNextEntry(entry);
                zos.write(imageBytes);
                zos.closeEntry();
                seq++;
            }

            // ---- 4-科研及教材业绩 ----
            String dir4 = "所有附件/4-科研及教材业绩/";
            ensureZipDir(zos, dir4);
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
                String zipPath = dir4 + fileName;

                byte[] imageBytes = decodeBase64(record.getProofImageData(), record.getId());
                if (imageBytes == null) continue;

                java.util.zip.ZipEntry entry = new java.util.zip.ZipEntry(zipPath);
                zos.putNextEntry(entry);
                zos.write(imageBytes);
                zos.closeEntry();
                seq++;
            }

            // ---- 5-大创业绩 ----
            String dir5 = "所有附件/5-大创业绩/";
            ensureZipDir(zos, dir5);
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
                String zipPath = dir5 + fileName;

                byte[] imageBytes = decodeBase64(record.getProofImageData(), record.getId());
                if (imageBytes == null) continue;

                java.util.zip.ZipEntry entry = new java.util.zip.ZipEntry(zipPath);
                zos.putNextEntry(entry);
                zos.write(imageBytes);
                zos.closeEntry();
                seq++;
            }

            // ---- 6-教改科研项目业绩 ----
            String dir6 = "所有附件/6-教改科研项目业绩/";
            ensureZipDir(zos, dir6);
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
                String zipPath = dir6 + fileName;

                byte[] imageBytes = decodeBase64(record.getProofImageData(), record.getId());
                if (imageBytes == null) continue;

                java.util.zip.ZipEntry entry = new java.util.zip.ZipEntry(zipPath);
                zos.putNextEntry(entry);
                zos.write(imageBytes);
                zos.closeEntry();
                seq++;
            }

            // ---- 7-论文业绩 ----
            String dir7 = "所有附件/7-论文业绩/";
            ensureZipDir(zos, dir7);
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
                String zipPath = dir7 + fileName;

                byte[] imageBytes = decodeBase64(record.getProofImageData(), record.getId());
                if (imageBytes == null) continue;

                java.util.zip.ZipEntry entry = new java.util.zip.ZipEntry(zipPath);
                zos.putNextEntry(entry);
                zos.write(imageBytes);
                zos.closeEntry();
                seq++;
            }

            // ---- 8-体育比赛业绩 ----
            String dir8 = "所有附件/8-体育比赛业绩/";
            ensureZipDir(zos, dir8);
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
                String zipPath = dir8 + fileName;

                byte[] imageBytes = decodeBase64(record.getProofImageData(), record.getId());
                if (imageBytes == null) continue;

                java.util.zip.ZipEntry entry = new java.util.zip.ZipEntry(zipPath);
                zos.putNextEntry(entry);
                zos.write(imageBytes);
                zos.closeEntry();
                seq++;
            }

            // ---- 9-兼职班主任 ----
            String dir9 = "所有附件/9-兼职班主任/";
            ensureZipDir(zos, dir9);
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
                String zipPath = dir9 + fileName;

                byte[] imageBytes = decodeBase64(record.getProofImageData(), record.getId());
                if (imageBytes == null) continue;

                java.util.zip.ZipEntry entry = new java.util.zip.ZipEntry(zipPath);
                zos.putNextEntry(entry);
                zos.write(imageBytes);
                zos.closeEntry();
                seq++;
            }

            // ---- 10-监考次数统计 ----
            String dir10 = "所有附件/10-监考次数统计/";
            ensureZipDir(zos, dir10);
            List<InvigilationRecord> invigilationRecords = invigilationRecordMapper.selectListByQuery(
                    QueryWrapper.create().orderBy("create_time", true));
            seq = 1;
            for (InvigilationRecord record : invigilationRecords) {
                if (StrUtil.isBlank(record.getProofImageData())) {
                    continue;
                }
                String teacherName = sanitizeFilename(
                        StrUtil.isNotBlank(record.getTeacherName())
                                ? record.getTeacherName() : "未知教师");
                String countInfo = record.getInvigilationCount() != null
                        ? "-" + record.getInvigilationCount() + "次" : "";
                String userName = sanitizeFilename(getUserName(record.getUserId()));
                String fileName = seq + "-" + teacherName + countInfo + "-" + userName + ".png";
                String zipPath = dir10 + fileName;

                byte[] imageBytes = decodeBase64(record.getProofImageData(), record.getId());
                if (imageBytes == null) continue;

                java.util.zip.ZipEntry entry = new java.util.zip.ZipEntry(zipPath);
                zos.putNextEntry(entry);
                zos.write(imageBytes);
                zos.closeEntry();
                seq++;
            }

            // ---- 11-网上评教 ----
            String dir11 = "所有附件/11-网上评教/";
            ensureZipDir(zos, dir11);
            List<OnlineEvaluationRecord> onlineEvaluationRecords = onlineEvaluationRecordMapper.selectListByQuery(
                    QueryWrapper.create().orderBy("create_time", true));
            seq = 1;
            for (OnlineEvaluationRecord record : onlineEvaluationRecords) {
                if (StrUtil.isBlank(record.getProofImageData())) {
                    continue;
                }
                String teacherName = sanitizeFilename(
                        StrUtil.isNotBlank(record.getTeacherName())
                                ? record.getTeacherName() : "未知教师");
                String yearAndSemester = "";
                if (StrUtil.isNotBlank(record.getAcademicYear())) {
                    yearAndSemester += "-" + sanitizeFilename(record.getAcademicYear());
                }
                if (StrUtil.isNotBlank(record.getSemester())) {
                    yearAndSemester += "-" + sanitizeFilename(record.getSemester());
                }
                String userName = sanitizeFilename(getUserName(record.getUserId()));
                String fileName = seq + "-" + teacherName + yearAndSemester + "-" + userName + ".png";
                String zipPath = dir11 + fileName;

                byte[] imageBytes = decodeBase64(record.getProofImageData(), record.getId());
                if (imageBytes == null) continue;

                java.util.zip.ZipEntry entry = new java.util.zip.ZipEntry(zipPath);
                zos.putNextEntry(entry);
                zos.write(imageBytes);
                zos.closeEntry();
                seq++;
            }

            // ---- 12-推荐学院学生签约就业 ----
            String dir12 = "所有附件/12-推荐学院学生签约就业/";
            ensureZipDir(zos, dir12);
            List<RecommendedEmploymentRecord> recommendedEmploymentRecords = recommendedEmploymentRecordMapper.selectListByQuery(
                    QueryWrapper.create().orderBy("create_time", true));
            seq = 1;
            for (RecommendedEmploymentRecord record : recommendedEmploymentRecords) {
                if (StrUtil.isBlank(record.getProofImageData())) {
                    continue;
                }
                String teacherName = sanitizeFilename(
                        StrUtil.isNotBlank(record.getTeacherName())
                                ? record.getTeacherName() : "未知教师");
                String companyName = sanitizeFilename(
                        StrUtil.isNotBlank(record.getCompanyName())
                                ? record.getCompanyName() : "未知单位");
                String fileName = seq + "-" + companyName + "-" + teacherName + ".png";
                String zipPath = dir12 + fileName;

                byte[] imageBytes = decodeBase64(record.getProofImageData(), record.getId());
                if (imageBytes == null) continue;

                java.util.zip.ZipEntry entry = new java.util.zip.ZipEntry(zipPath);
                zos.putNextEntry(entry);
                zos.write(imageBytes);
                zos.closeEntry();
                seq++;
            }

            // ---- 13-签订合作企业 ----
            String dir13 = "所有附件/13-签订合作企业/";
            ensureZipDir(zos, dir13);
            List<CooperativeEnterpriseRecord> cooperativeEnterpriseRecords = cooperativeEnterpriseRecordMapper.selectListByQuery(
                    QueryWrapper.create().orderBy("create_time", true));
            seq = 1;
            for (CooperativeEnterpriseRecord record : cooperativeEnterpriseRecords) {
                if (StrUtil.isBlank(record.getProofImageData())) {
                    continue;
                }
                String teacherName = sanitizeFilename(
                        StrUtil.isNotBlank(record.getTeacherName())
                                ? record.getTeacherName() : "未知教师");
                String enterpriseName = sanitizeFilename(
                        StrUtil.isNotBlank(record.getEnterpriseName())
                                ? record.getEnterpriseName() : "未知企业");
                String fileName = seq + "-" + enterpriseName + "-" + teacherName + ".png";
                String zipPath = dir13 + fileName;

                byte[] imageBytes = decodeBase64(record.getProofImageData(), record.getId());
                if (imageBytes == null) continue;

                java.util.zip.ZipEntry entry = new java.util.zip.ZipEntry(zipPath);
                zos.putNextEntry(entry);
                zos.write(imageBytes);
                zos.closeEntry();
                seq++;
            }

            // ---- 14-指导青年教师 ----
            String dir14 = "所有附件/14-指导青年教师/";
            ensureZipDir(zos, dir14);
            List<YoungTeacherGuidanceRecord> youngTeacherGuidanceRecords = youngTeacherGuidanceRecordMapper.selectListByQuery(
                    QueryWrapper.create().orderBy("create_time", true));
            seq = 1;
            for (YoungTeacherGuidanceRecord record : youngTeacherGuidanceRecords) {
                if (StrUtil.isBlank(record.getProofImageData())) {
                    continue;
                }
                String youngTeacherName = sanitizeFilename(
                        StrUtil.isNotBlank(record.getYoungTeacherName())
                                ? record.getYoungTeacherName() : "未知青年教师");
                String mentorNames = sanitizeFilename(
                        StrUtil.isNotBlank(record.getMentorNames())
                                ? record.getMentorNames() : "未知指导教师");
                String fileName = seq + "-" + mentorNames + "-" + youngTeacherName + ".png";
                String zipPath = dir14 + fileName;

                byte[] imageBytes = decodeBase64(record.getProofImageData(), record.getId());
                if (imageBytes == null) continue;

                java.util.zip.ZipEntry entry = new java.util.zip.ZipEntry(zipPath);
                zos.putNextEntry(entry);
                zos.write(imageBytes);
                zos.closeEntry();
                seq++;
            }

            // ---- 15-优秀毕设 ----
            String dir15 = "所有附件/15-优秀毕设/";
            ensureZipDir(zos, dir15);
            List<ExcellentGraduationProjectRecord> excellentGraduationProjectRecords = excellentGraduationProjectRecordMapper.selectListByQuery(
                    QueryWrapper.create().orderBy("create_time", true));
            seq = 1;
            for (ExcellentGraduationProjectRecord record : excellentGraduationProjectRecords) {
                if (StrUtil.isBlank(record.getProofImageData())) {
                    continue;
                }
                String studentName = sanitizeFilename(
                        StrUtil.isNotBlank(record.getStudentName())
                                ? record.getStudentName() : "未知学生");
                String projectTitle = sanitizeFilename(
                        StrUtil.isNotBlank(record.getProjectTitle())
                                ? "-" + record.getProjectTitle() : "");
                String fileName = seq + "-" + studentName + projectTitle + ".png";
                String zipPath = dir15 + fileName;

                byte[] imageBytes = decodeBase64(record.getProofImageData(), record.getId());
                if (imageBytes == null) continue;

                java.util.zip.ZipEntry entry = new java.util.zip.ZipEntry(zipPath);
                zos.putNextEntry(entry);
                zos.write(imageBytes);
                zos.closeEntry();
                seq++;
            }

            // ---- 16-校企联合培养 ----
            String dir16 = "所有附件/16-校企联合培养/";
            ensureZipDir(zos, dir16);
            List<SchoolEnterpriseTrainingRecord> schoolEnterpriseTrainingRecords = schoolEnterpriseTrainingRecordMapper.selectListByQuery(
                    QueryWrapper.create().orderBy("create_time", true));
            seq = 1;
            for (SchoolEnterpriseTrainingRecord record : schoolEnterpriseTrainingRecords) {
                if (StrUtil.isBlank(record.getProofImageData())) {
                    continue;
                }
                String studentName = sanitizeFilename(
                        StrUtil.isNotBlank(record.getStudentName())
                                ? record.getStudentName() : "未知学生");
                String companyName = sanitizeFilename(
                        StrUtil.isNotBlank(record.getCompanyName())
                                ? "-" + record.getCompanyName() : "");
                String fileName = seq + "-" + studentName + companyName + ".png";
                String zipPath = dir16 + fileName;

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

    /**
     * 确保ZIP中存在目录条目（空文件夹）。
     * 重复调用同一个目录不会产生副作用，ZIP解压时会自动合并。
     */
    private void ensureZipDir(java.util.zip.ZipOutputStream zos, String dirPath) throws IOException {
        java.util.zip.ZipEntry dirEntry = new java.util.zip.ZipEntry(dirPath);
        zos.putNextEntry(dirEntry);
        zos.closeEntry();
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

    // ==================== 多Sheet Excel导出 ====================

    @Override
    public byte[] exportAllToExcel() {
        try (XSSFWorkbook combinedWorkbook = new XSSFWorkbook()) {

            // 按顺序导出各分类Sheet
            copySheetFromService(combinedWorkbook, () -> teacherRecordService.exportRecordsToExcel(), "1-教师获奖");
            copySheetFromService(combinedWorkbook, () -> studentRecordService.exportRecordsToExcel(), "2-指导学生科技竞赛");
            copySheetFromService(combinedWorkbook, () -> trainingRecordService.exportRecordsToExcel(), "3-指导实训");
            copySheetFromService(combinedWorkbook, () -> researchRecordService.exportRecordsToExcel(), "4-科研及教材业绩");
            copySheetFromService(combinedWorkbook, () -> innovationRecordService.exportRecordsToExcel(), "5-大创业绩");
            copySheetFromService(combinedWorkbook, () -> teachingReformRecordService.exportRecordsToExcel(), "6-教改科研项目业绩");
            copySheetFromService(combinedWorkbook, () -> thesisRecordService.exportRecordsToExcel(), "7-论文业绩");
            copySheetFromService(combinedWorkbook, () -> sportsRecordService.exportRecordsToExcel(), "8-体育比赛业绩");
            copySheetFromService(combinedWorkbook, () -> advisorRecordService.exportRecordsToExcel(), "9-兼职班主任");
            copySheetFromService(combinedWorkbook, () -> invigilationRecordService.exportRecordsToExcel(), "10-监考次数统计");
            copySheetFromService(combinedWorkbook, () -> onlineEvaluationRecordService.exportRecordsToExcel(), "11-网上评教");
            copySheetFromService(combinedWorkbook, () -> recommendedEmploymentRecordService.exportRecordsToExcel(), "12-推荐学院学生签约就业");
            copySheetFromService(combinedWorkbook, () -> cooperativeEnterpriseRecordService.exportRecordsToExcel(), "13-签订合作企业");
            copySheetFromService(combinedWorkbook, () -> youngTeacherGuidanceRecordService.exportRecordsToExcel(), "14-指导青年教师");
            copySheetFromService(combinedWorkbook, () -> excellentGraduationProjectRecordService.exportRecordsToExcel(), "15-优秀毕设");
            copySheetFromService(combinedWorkbook, () -> schoolEnterpriseTrainingRecordService.exportRecordsToExcel(), "16-校企联合培养");

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            combinedWorkbook.write(bos);
            return bos.toByteArray();
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "Excel生成失败: " + e.getMessage());
        }
    }

    /**
     * 从各分类服务导出Excel并复制Sheet到合并工作簿
     */
    private void copySheetFromService(XSSFWorkbook combined, java.util.function.Supplier<byte[]> exportFunc, String sheetName) {
        try {
            byte[] data = exportFunc.get();
            if (data == null || data.length == 0) {
                log.warn("导出数据为空: sheetName={}", sheetName);
                // 数据为空时仍然创建空Sheet页，保证导出结构完整
                combined.createSheet(sheetName);
                return;
            }
            try (Workbook sourceWorkbook = WorkbookFactory.create(new ByteArrayInputStream(data))) {
                Sheet sourceSheet = sourceWorkbook.getSheetAt(0);
                if (sourceSheet == null) {
                    log.warn("源Sheet为空: sheetName={}", sheetName);
                    combined.createSheet(sheetName);
                    return;
                }
                Sheet newSheet = combined.createSheet(sheetName);
                copySheet(sourceSheet, newSheet);
                log.info("Sheet复制成功: sheetName={}, rows={}", sheetName, sourceSheet.getLastRowNum() + 1);
            }
        } catch (Exception e) {
            log.error("复制Sheet失败，创建空Sheet占位: sheetName={}, error={}", sheetName, e.getMessage());
            // 即使导出失败也创建空Sheet页，保证导出结构完整
            try {
                combined.createSheet(sheetName);
            } catch (Exception ignored) {
                log.error("创建空Sheet也失败: sheetName={}", sheetName);
            }
        }
    }

    /**
     * 复制Sheet内容（行列数据、列宽、合并单元格）。
     * 注意：不复制跨Workbook的样式（会抛异常），仅复制数据和结构。
     */
    private void copySheet(Sheet source, Sheet target) {
        int lastRowNum = source.getLastRowNum();
        if (lastRowNum < 0) return;

        // 复制列宽
        int maxCol = 0;
        for (int i = 0; i <= lastRowNum; i++) {
            org.apache.poi.ss.usermodel.Row row = source.getRow(i);
            if (row != null && row.getLastCellNum() > maxCol) {
                maxCol = row.getLastCellNum();
            }
        }
        for (int i = 0; i < maxCol; i++) {
            int w = source.getColumnWidth(i);
            if (w > 0) target.setColumnWidth(i, w);
        }

        // 复制行和单元格（仅复制值，不复制跨Workbook样式）
        for (int i = 0; i <= lastRowNum; i++) {
            org.apache.poi.ss.usermodel.Row sourceRow = source.getRow(i);
            if (sourceRow == null) continue;
            org.apache.poi.ss.usermodel.Row targetRow = target.createRow(i);
            targetRow.setHeight(sourceRow.getHeight());

            for (int j = 0; j < sourceRow.getLastCellNum(); j++) {
                org.apache.poi.ss.usermodel.Cell sourceCell = sourceRow.getCell(j);
                if (sourceCell == null) continue;
                org.apache.poi.ss.usermodel.Cell targetCell = targetRow.createCell(j);

                switch (sourceCell.getCellType()) {
                    case STRING:
                        targetCell.setCellValue(sourceCell.getStringCellValue());
                        break;
                    case NUMERIC:
                        targetCell.setCellValue(sourceCell.getNumericCellValue());
                        break;
                    case BOOLEAN:
                        targetCell.setCellValue(sourceCell.getBooleanCellValue());
                        break;
                    case FORMULA:
                        targetCell.setCellFormula(sourceCell.getCellFormula());
                        break;
                    default:
                        break;
                }
            }
        }

        // 复制合并单元格（跳过重复/冲突的区域）
        for (int i = 0; i < source.getNumMergedRegions(); i++) {
            try {
                target.addMergedRegion(source.getMergedRegion(i));
            } catch (Exception ignored) {
                // 合并区域冲突时跳过（如源Sheet中存在重叠区域）
            }
        }
    }
}
