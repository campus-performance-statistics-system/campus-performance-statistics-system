package com.jgh.ghairouter.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import com.jgh.ghairouter.exception.BusinessException;
import com.jgh.ghairouter.exception.ErrorCode;
import com.jgh.ghairouter.mapper.*;
import com.jgh.ghairouter.model.constants.StudentScoringConstants;
import com.jgh.ghairouter.model.dto.competition.StudentCompetitionQueryRequest;
import com.jgh.ghairouter.model.entity.*;
import com.jgh.ghairouter.model.enums.ReviewStatusEnum;
import com.jgh.ghairouter.model.vo.AdvisorScoreVO;
import com.jgh.ghairouter.model.vo.StudentCompetitionRecordVO;
import com.jgh.ghairouter.service.AiReviewService;
import com.jgh.ghairouter.service.StudentCompetitionRecordService;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 指导学生科技竞赛记录服务实现（v3）
 */
@Slf4j
@Service
public class StudentCompetitionRecordServiceImpl
        extends ServiceImpl<StudentCompetitionRecordMapper, StudentCompetitionRecord>
        implements StudentCompetitionRecordService {

    @Resource
    private AiReviewService aiReviewService;
    @Resource
    private UserMapper userMapper;
    @Resource
    private TeacherCompetitionAuditRecordMapper auditMapper;
    @Resource
    private AdvisorScoreMapper advisorScoreMapper;

    private static final String DEFAULT_TYPE_NAME = "指导学生科技竞赛";

    @Override
    public Long addRecord(Long userId,
                          String competitionName, String sponsorUnit,
                          String competitionTopic, String studentNames,
                          String competitionRank, String gradeName, String awardLevelText,
                          String awardDetails,
                          Integer isOrganizer, String advisorScoreData,
                          MultipartFile file) {

        // 读取文件 base64
        String base64 = null;
        if (file != null && !file.isEmpty()) {
            try {
                base64 = Base64.getEncoder().encodeToString(file.getBytes());
            } catch (IOException e) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "读取文件失败");
            }
        }

        StudentCompetitionRecord record = new StudentCompetitionRecord();
        record.setUserId(userId);
        record.setTypeName(DEFAULT_TYPE_NAME);
        record.setCompetitionName(competitionName);
        record.setSponsorUnit(sponsorUnit);
        record.setCompetitionTopic(competitionTopic);
        record.setStudentNames(studentNames);

        // 组织者类别时，竞赛等级应填 null
        if (isOrganizer != null && isOrganizer == 1) {
            record.setCompetitionRank(null);
            record.setGradeName(null);
        } else {
            // 指导者：取最高级别奖项作为 competitionRank / gradeName（用于计分）
            String[] best = resolveBestAward(awardDetails, competitionRank, gradeName);
            record.setCompetitionRank(best[0]);
            record.setGradeName(best[1]);
        }
        record.setAwardLevelText(awardLevelText);
        record.setAwardDetails(awardDetails);
        record.setIsOrganizer(isOrganizer != null ? isOrganizer : 0);
        record.setAdvisorScoreData(advisorScoreData);
        record.setProofImageData(base64);

        boolean saved = this.save(record);
        if (!saved) throw new BusinessException(ErrorCode.OPERATION_ERROR, "提交失败");

        // 创建审核记录
        TeacherCompetitionAuditRecord audit = new TeacherCompetitionAuditRecord();
        audit.setRecordId(record.getId());
        audit.setRecordType(record.getTypeName());
        audit.setAutoReviewStatus(ReviewStatusEnum.PENDING.getValue());
        audit.setAdminReviewStatus(ReviewStatusEnum.PENDING.getValue());
        LocalDateTime now = LocalDateTime.now();
        audit.setCreateTime(now);
        audit.setUpdateTime(now);
        auditMapper.insert(audit);

        // 触发AI审核
        if (file != null && !file.isEmpty()) {
            String mimeType = file.getContentType();
            if (StrUtil.isBlank(mimeType)) mimeType = "image/png";
            try {
                aiReviewService.autoReview(record.getId(), record.getTypeName(), competitionName, base64, mimeType);
            } catch (Exception e) {
                log.error("AI审核触发失败", e);
            }
        }
        return record.getId();
    }

    /**
     * 从 awardDetails JSON 数组中找出最高级别的奖项。
     * 返回 [competitionRank, gradeName]。
     * 若 awardDetails 为空，回退到传入的 competitionRank / gradeName。
     */
    private String[] resolveBestAward(String awardDetails, String fallbackRank, String fallbackGrade) {
        if (StrUtil.isBlank(awardDetails)) {
            return new String[]{fallbackRank, fallbackGrade};
        }
        try {
            JSONArray arr = new JSONArray(awardDetails);
            String bestRank = null;
            String bestGrade = null;
            int bestRankOrder = -1;
            int bestGradeOrder = -1;
            for (int i = 0; i < arr.size(); i++) {
                JSONObject entry = arr.getJSONObject(i);
                String rank = entry.getStr("rank");
                String grade = entry.getStr("grade");
                int rankOrder = StudentScoringConstants.getRankOrder(rank);
                int gradeOrder = StudentScoringConstants.getGradeOrder(grade);
                if (rankOrder > bestRankOrder || (rankOrder == bestRankOrder && gradeOrder > bestGradeOrder)) {
                    bestRank = rank;
                    bestGrade = grade;
                    bestRankOrder = rankOrder;
                    bestGradeOrder = gradeOrder;
                }
            }
            if (bestRank != null) {
                return new String[]{bestRank, bestGrade};
            }
        } catch (Exception e) {
            log.warn("解析 awardDetails 失败: {}", awardDetails, e);
        }
        return new String[]{fallbackRank, fallbackGrade};
    }

    // ==================== 保存得分明细 ====================

    private void saveAdvisorScores(Long recordId, String advisorScoreData, String competitionRank) {
        if (StrUtil.isBlank(advisorScoreData)) return;

        try {
            JSONArray arr = new JSONArray(advisorScoreData);
            LocalDateTime now = LocalDateTime.now();
            for (int i = 0; i < arr.size(); i++) {
                JSONObject entry = arr.getJSONObject(i);
                AdvisorScore score = new AdvisorScore();
                score.setRecordId(recordId);

                // 支持两种格式：user ID 或 teacher name
                if (entry.containsKey("userId")) {
                    score.setTeacherUserId(entry.getLong("userId"));
                }
                String teacherName = entry.getStr("teacherName");
                score.setTeacherName(teacherName);

                // 如果前端没传 userId，尝试按姓名反查用户ID（组织者行通常只传姓名）
                if (score.getTeacherUserId() == null && StrUtil.isNotBlank(teacherName)) {
                    User teacher = userMapper.selectOneByQuery(
                            QueryWrapper.create().eq("user_name", teacherName));
                    if (teacher != null) {
                        score.setTeacherUserId(teacher.getId());
                    }
                }

                BigDecimal baseScore = entry.getBigDecimal("baseScore");
                BigDecimal bonusScore = entry.getBigDecimal("bonusScore");
                BigDecimal totalScore = entry.getBigDecimal("totalScore");
                if (baseScore == null) baseScore = BigDecimal.ZERO;
                if (bonusScore == null) bonusScore = BigDecimal.ZERO;
                if (totalScore == null) totalScore = baseScore.add(bonusScore);

                score.setBaseScore(baseScore);
                score.setBonusScore(bonusScore);
                score.setTotalScore(totalScore.setScale(3, RoundingMode.HALF_UP));
                score.setIsLeader(entry.getBool("isLeader", false) ? 1 : 0);
                score.setCreateTime(now);
                score.setUpdateTime(now);

                advisorScoreMapper.insert(score);
            }
        } catch (Exception e) {
            log.error("解析 advisorScoreData 失败: {}", advisorScoreData, e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "指导老师得分数据解析失败");
        }
    }

    // ==================== 查询 ====================

    @Override
    public QueryWrapper getQueryWrapper(StudentCompetitionQueryRequest req) {
        if (req == null) throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        QueryWrapper wrapper = QueryWrapper.create()
                .eq("id", req.getId())
                .eq("user_id", req.getUserId())
                .like("competition_name", req.getCompetitionName());
        if (StrUtil.isNotBlank(req.getSortField())) {
            wrapper.orderBy(req.getSortField(), "ascend".equals(req.getSortOrder()));
        }
        wrapper.orderBy("create_time", false);
        return wrapper;
    }

    @Override
    public StudentCompetitionRecordVO getRecordVO(StudentCompetitionRecord record) {
        if (record == null) return null;
        StudentCompetitionRecordVO vo = new StudentCompetitionRecordVO();
        BeanUtil.copyProperties(record, vo);

        // 提交人姓名
        if (record.getUserId() != null) {
            User u = userMapper.selectOneById(record.getUserId());
            if (u != null) vo.setUserName(u.getUserName());
        }

        // 审核信息（按 record_id + record_type 联合定位）
        TeacherCompetitionAuditRecord audit = auditMapper.selectOneByQuery(
                QueryWrapper.create().eq("record_id", record.getId()).eq("record_type", record.getTypeName()));
        if (audit != null) {
            vo.setAutoReviewStatus(audit.getAutoReviewStatus());
            vo.setAutoReviewComment(audit.getAutoReviewComment());
            vo.setAdminReviewStatus(audit.getAdminReviewStatus());
            vo.setAdminReviewComment(audit.getAdminReviewComment());
            if (audit.getAdminId() != null) {
                User admin = userMapper.selectOneById(audit.getAdminId());
                if (admin != null) vo.setAdminName(admin.getUserName());
            }
            vo.setAdminReviewTime(audit.getAdminReviewTime());
        }

        // 指导老师得分明细
        List<AdvisorScore> scoreList = advisorScoreMapper.selectListByQuery(
                QueryWrapper.create().eq("record_id", record.getId()));
        if (CollUtil.isNotEmpty(scoreList)) {
            vo.setAdvisorScores(scoreList.stream()
                    .map(s -> AdvisorScoreVO.builder()
                            .userId(s.getTeacherUserId())
                            .teacherName(s.getTeacherName())
                            .baseScore(s.getBaseScore())
                            .bonusScore(s.getBonusScore())
                            .totalScore(s.getTotalScore())
                            .isLeader(s.getIsLeader())
                            .build())
                    .collect(Collectors.toList()));
        }

        return vo;
    }

    @Override
    public Page<StudentCompetitionRecordVO> pageRecords(StudentCompetitionQueryRequest req) {
        long pageNum = req.getPageNum();
        long pageSize = req.getPageSize();

        QueryWrapper wrapper = getQueryWrapper(req);
        if (StrUtil.isNotBlank(req.getAdminReviewStatus())) {
            Page<StudentCompetitionRecord> recordPage = this.page(Page.of(pageNum, pageSize), wrapper);
            List<StudentCompetitionRecordVO> voList = recordPage.getRecords().stream()
                    .map(this::getRecordVO)
                    .filter(vo -> req.getAdminReviewStatus().equals(vo.getAdminReviewStatus()))
                    .collect(Collectors.toList());
            Page<StudentCompetitionRecordVO> voPage = new Page<>(pageNum, pageSize, recordPage.getTotalRow());
            voPage.setRecords(voList);
            return voPage;
        }
        Page<StudentCompetitionRecord> recordPage = this.page(Page.of(pageNum, pageSize), wrapper);
        List<StudentCompetitionRecordVO> voList = recordPage.getRecords().stream()
                .map(this::getRecordVO)
                .collect(Collectors.toList());
        Page<StudentCompetitionRecordVO> voPage = new Page<>(pageNum, pageSize, recordPage.getTotalRow());
        voPage.setRecords(voList);
        return voPage;
    }

    @Override
    public Page<StudentCompetitionRecordVO> pageMyRelatedRecords(Long userId, StudentCompetitionQueryRequest req) {
        long pageNum = req.getPageNum();
        long pageSize = req.getPageSize();

        // 提交人 OR 在得分表中被分配了得分的指导老师，均可看到记录
        QueryWrapper wrapper = QueryWrapper.create()
                .eq("id", req.getId())
                .like("competition_name", req.getCompetitionName())
                .where("(user_id = ? OR id IN (SELECT record_id FROM advisor_score WHERE user_id = ? AND is_delete = 0))",
                       userId, userId);
        wrapper.orderBy("create_time", false);

        Page<StudentCompetitionRecord> recordPage = this.page(Page.of(pageNum, pageSize), wrapper);
        List<StudentCompetitionRecordVO> voList = recordPage.getRecords().stream()
                .map(record -> {
                    StudentCompetitionRecordVO vo = getRecordVO(record);
                    if (vo.getAdvisorScores() != null) {
                        vo.getAdvisorScores().stream()
                                .filter(s -> userId.equals(s.getUserId()))
                                .findFirst()
                                .ifPresent(s -> vo.setMyScoreDisplay(
                                        s.getTotalScore().stripTrailingZeros().toPlainString()));
                    }
                    return vo;
                })
                .collect(Collectors.toList());
        Page<StudentCompetitionRecordVO> voPage = new Page<>(pageNum, pageSize, recordPage.getTotalRow());
        voPage.setRecords(voList);
        return voPage;
    }

    @Override
    public BigDecimal getMyTotalScore(Long userId) {
        List<AdvisorScore> scores = advisorScoreMapper.selectListByQuery(
                QueryWrapper.create().eq("user_id", userId));
        if (CollUtil.isEmpty(scores)) return BigDecimal.ZERO;
        return scores.stream()
                .map(AdvisorScore::getTotalScore)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // ==================== 审核 ====================

    @Override
    public void adminReview(Long recordId, String reviewStatus, String reviewComment, Long adminId) {
        if (recordId == null) throw new BusinessException(ErrorCode.PARAMS_ERROR, "记录ID不能为空");
        if (StrUtil.isBlank(reviewStatus)) throw new BusinessException(ErrorCode.PARAMS_ERROR, "审核状态不能为空");

        ReviewStatusEnum statusEnum = ReviewStatusEnum.getEnumByValue(reviewStatus);
        if (statusEnum == null) throw new BusinessException(ErrorCode.PARAMS_ERROR, "无效的审核状态");
        if (statusEnum == ReviewStatusEnum.PENDING)
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "审核状态不能为待审核");

        StudentCompetitionRecord record = this.getById(recordId);
        if (record == null) throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "比赛记录不存在");

        TeacherCompetitionAuditRecord audit = auditMapper.selectOneByQuery(
                QueryWrapper.create().eq("record_id", recordId).eq("record_type", record.getTypeName()));
        if (audit == null) throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "审核记录不存在");

        if (statusEnum == ReviewStatusEnum.PASSED && StrUtil.isBlank(reviewComment))
            reviewComment = "审核通过";

        audit.setAdminReviewStatus(reviewStatus);
        audit.setAdminReviewComment(reviewComment);
        audit.setAdminId(adminId);
        audit.setAdminReviewTime(LocalDateTime.now());

        if (auditMapper.update(audit) <= 0)
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "审核失败");

        // 审核通过后保存得分明细
        if (statusEnum == ReviewStatusEnum.PASSED) {
            advisorScoreMapper.deleteByQuery(
                    QueryWrapper.create().eq("record_id", recordId));

            if (StrUtil.isNotBlank(record.getAdvisorScoreData())) {
                saveAdvisorScores(recordId, record.getAdvisorScoreData(), record.getCompetitionRank());
            }
        }
    }

    // ==================== 导出Excel ====================

    @Override
    public byte[] exportRecordsToExcel() {
        try (org.apache.poi.xssf.usermodel.XSSFWorkbook workbook =
                     new org.apache.poi.xssf.usermodel.XSSFWorkbook()) {

            // 表头（按v3格式）
            String[] headers = {"序号", "竞赛名称", "主办单位", "参赛题目", "参赛队员姓名", "指导老师", "获奖级别"};

            org.apache.poi.ss.usermodel.Sheet sheet = workbook.createSheet("指导学生科技竞赛");

            // 表头样式
            org.apache.poi.ss.usermodel.CellStyle headerStyle = workbook.createCellStyle();
            org.apache.poi.ss.usermodel.Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);

            org.apache.poi.ss.usermodel.Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                org.apache.poi.ss.usermodel.Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // 查询所有记录，按竞赛名称分组
            List<StudentCompetitionRecord> allRecords = this.list(
                    QueryWrapper.create().orderBy("competition_name", true).orderBy("create_time", true));

            int rowIdx = 1;
            int seq = 1;

            for (StudentCompetitionRecord record : allRecords) {
                StudentCompetitionRecordVO vo = getRecordVO(record);
                if (vo == null) continue;

                int currentRow = rowIdx;
                org.apache.poi.ss.usermodel.Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(seq++);
                row.createCell(1).setCellValue(vo.getCompetitionName() != null ? vo.getCompetitionName() : "");
                row.createCell(2).setCellValue(vo.getSponsorUnit() != null ? vo.getSponsorUnit() : "");

                if (vo.getIsOrganizer() != null && vo.getIsOrganizer() == 1) {
                    // 组织者行：参赛题目+参赛队员姓名 合并显示"组织者"
                    row.createCell(3).setCellValue("组织者");
                    row.createCell(4).setCellValue("");
                    // 组织者行：指导老师+获奖级别 合并显示组织者姓名（分数）
                    row.createCell(5).setCellValue(formatOrganizerStudentName(vo));
                    row.createCell(6).setCellValue("");

                    sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(currentRow, currentRow, 3, 4));
                    sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(currentRow, currentRow, 5, 6));
                } else {
                    // 指导者行
                    row.createCell(3).setCellValue(vo.getCompetitionTopic() != null ? vo.getCompetitionTopic() : "");
                    row.createCell(4).setCellValue(vo.getStudentNames() != null ? vo.getStudentNames() : "");
                    row.createCell(5).setCellValue(formatAdvisorScores(vo));
                    row.createCell(6).setCellValue(vo.getAwardLevelText() != null ? vo.getAwardLevelText() :
                            (vo.getGradeName() != null ? vo.getGradeName() : ""));
                }
            }

            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            workbook.write(bos);
            return bos.toByteArray();
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "Excel生成失败: " + e.getMessage());
        }
    }

    /**
     * 格式化组织者行的参赛队员姓名列，按v3示例格式：
     * 秦小旭2  或  高玮（2）
     * 组织者得分从 advisor_scores 中获取
     */
    private String formatOrganizerStudentName(StudentCompetitionRecordVO vo) {
        String name = vo.getStudentNames() != null ? vo.getStudentNames() : "";
        if (vo.getAdvisorScores() != null && !vo.getAdvisorScores().isEmpty()) {
            AdvisorScoreVO score = vo.getAdvisorScores().get(0);
            BigDecimal total = score.getTotalScore();
            return name + "（" + total.stripTrailingZeros().toPlainString() + "）";
        }
        return name;
    }

    /**
     * 格式化指导老师得分列，按v3示例格式：
     * 秦小旭（2+奖7）、欧阳玉梅（2+奖3）
     * 秦小旭（奖5.6）、莫永华（2+奖2.4）
     * 秦小旭（2.1）
     */
    private String formatAdvisorScores(StudentCompetitionRecordVO vo) {
        if (vo.getAdvisorScores() == null || vo.getAdvisorScores().isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < vo.getAdvisorScores().size(); i++) {
            if (i > 0) sb.append("、");
            AdvisorScoreVO score = vo.getAdvisorScores().get(i);
            sb.append(score.getTeacherName()).append("（");

            BigDecimal base = score.getBaseScore();
            BigDecimal bonus = score.getBonusScore();

            if (base.compareTo(BigDecimal.ZERO) > 0 && bonus.compareTo(BigDecimal.ZERO) > 0) {
                // 有基础分和获奖加分：2+奖7
                sb.append(base.stripTrailingZeros().toPlainString())
                        .append("+奖")
                        .append(bonus.stripTrailingZeros().toPlainString());
            } else if (bonus.compareTo(BigDecimal.ZERO) > 0) {
                // 只有获奖加分：奖5.6
                sb.append("奖").append(bonus.stripTrailingZeros().toPlainString());
            } else if (base.compareTo(BigDecimal.ZERO) > 0) {
                // 只有基础分：2
                sb.append(base.stripTrailingZeros().toPlainString());
            } else {
                sb.append("0");
            }

            sb.append("）");
        }
        return sb.toString();
    }
}
