package com.jgh.ghairouter.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import com.jgh.ghairouter.exception.BusinessException;
import com.jgh.ghairouter.exception.ErrorCode;
import com.jgh.ghairouter.mapper.*;
import com.jgh.ghairouter.model.constants.TrainingScoringConstants;
import com.jgh.ghairouter.model.dto.competition.TrainingGuidanceQueryRequest;
import com.jgh.ghairouter.model.entity.*;
import com.jgh.ghairouter.model.enums.ReviewStatusEnum;
import com.jgh.ghairouter.model.vo.TrainingGuidanceRecordVO;
import com.jgh.ghairouter.model.vo.TrainingGuidanceScoreVO;
import com.jgh.ghairouter.service.AiReviewService;
import com.jgh.ghairouter.service.TrainingGuidanceRecordService;
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
 * 指导实训记录服务实现（v4）
 */
@Slf4j
@Service
public class TrainingGuidanceRecordServiceImpl
        extends ServiceImpl<TrainingGuidanceRecordMapper, TrainingGuidanceRecord>
        implements TrainingGuidanceRecordService {

    @Resource
    private AiReviewService aiReviewService;
    @Resource
    private UserMapper userMapper;
    @Resource
    private TeacherCompetitionAuditRecordMapper auditMapper;
    @Resource
    private TrainingGuidanceScoreMapper trainingScoreMapper;

    private static final String DEFAULT_TYPE_NAME = "指导实训";

    @Override
    public Long addRecord(Long userId,
                          String semester, String trainingName,
                          String responsibleTeachers, String participatingTeachers,
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

        TrainingGuidanceRecord record = new TrainingGuidanceRecord();
        record.setUserId(userId);
        record.setTypeName(DEFAULT_TYPE_NAME);
        record.setSemester(semester);
        record.setTrainingName(trainingName);
        record.setResponsibleTeachers(responsibleTeachers);
        record.setParticipatingTeachers(participatingTeachers);
        record.setProofImageData(base64);

        boolean saved = this.save(record);
        if (!saved) throw new BusinessException(ErrorCode.OPERATION_ERROR, "提交失败");

        // 创建审核记录（复用统一的审核表）
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
                aiReviewService.autoReview(record.getId(), record.getTypeName(), trainingName, base64, mimeType);
            } catch (Exception e) {
                log.error("AI审核触发失败", e);
            }
        }
        return record.getId();
    }

    // ==================== 保存得分明细 ====================

    private void saveTrainingScores(Long recordId, String responsibleTeachers, String participatingTeachers) {
        LocalDateTime now = LocalDateTime.now();

        // 保存负责教师得分（2分/人）
        if (StrUtil.isNotBlank(responsibleTeachers)) {
            try {
                JSONArray arr = new JSONArray(responsibleTeachers);
                for (int i = 0; i < arr.size(); i++) {
                    JSONObject entry = arr.getJSONObject(i);
                    String teacherName = entry.getStr("teacherName");
                    if (StrUtil.isBlank(teacherName)) continue;

                    TrainingGuidanceScore score = new TrainingGuidanceScore();
                    score.setRecordId(recordId);
                    score.setTeacherName(teacherName);
                    score.setScore(TrainingScoringConstants.RESPONSIBLE_SCORE);
                    score.setRoleType(TrainingScoringConstants.ROLE_RESPONSIBLE);

                    // 尝试按姓名反查用户ID
                    if (entry.containsKey("userId")) {
                        score.setUserId(entry.getLong("userId"));
                    }
                    if (score.getUserId() == null) {
                        User teacher = userMapper.selectOneByQuery(
                                QueryWrapper.create().eq("user_name", teacherName));
                        if (teacher != null) {
                            score.setUserId(teacher.getId());
                        }
                    }

                    score.setCreateTime(now);
                    score.setUpdateTime(now);
                    trainingScoreMapper.insert(score);
                }
            } catch (Exception e) {
                log.error("解析负责教师JSON失败: {}", responsibleTeachers, e);
            }
        }

        // 保存参与教师得分（1分/人）
        if (StrUtil.isNotBlank(participatingTeachers)) {
            try {
                JSONArray arr = new JSONArray(participatingTeachers);
                for (int i = 0; i < arr.size(); i++) {
                    JSONObject entry = arr.getJSONObject(i);
                    String teacherName = entry.getStr("teacherName");
                    if (StrUtil.isBlank(teacherName)) continue;

                    TrainingGuidanceScore score = new TrainingGuidanceScore();
                    score.setRecordId(recordId);
                    score.setTeacherName(teacherName);
                    score.setScore(TrainingScoringConstants.PARTICIPATING_SCORE);
                    score.setRoleType(TrainingScoringConstants.ROLE_PARTICIPATING);

                    if (entry.containsKey("userId")) {
                        score.setUserId(entry.getLong("userId"));
                    }
                    if (score.getUserId() == null) {
                        User teacher = userMapper.selectOneByQuery(
                                QueryWrapper.create().eq("user_name", teacherName));
                        if (teacher != null) {
                            score.setUserId(teacher.getId());
                        }
                    }

                    score.setCreateTime(now);
                    score.setUpdateTime(now);
                    trainingScoreMapper.insert(score);
                }
            } catch (Exception e) {
                log.error("解析参与教师JSON失败: {}", participatingTeachers, e);
            }
        }
    }

    // ==================== 查询 ====================

    @Override
    public QueryWrapper getQueryWrapper(TrainingGuidanceQueryRequest req) {
        if (req == null) throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        QueryWrapper wrapper = QueryWrapper.create()
                .eq("id", req.getId())
                .eq("user_id", req.getUserId())
                .like("training_name", req.getTrainingName());
        if (StrUtil.isNotBlank(req.getSortField())) {
            wrapper.orderBy(req.getSortField(), "ascend".equals(req.getSortOrder()));
        }
        wrapper.orderBy("create_time", false);
        return wrapper;
    }

    @Override
    public TrainingGuidanceRecordVO getRecordVO(TrainingGuidanceRecord record) {
        if (record == null) return null;
        TrainingGuidanceRecordVO vo = new TrainingGuidanceRecordVO();
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

        // 得分明细
        List<TrainingGuidanceScore> scoreList = trainingScoreMapper.selectListByQuery(
                QueryWrapper.create().eq("record_id", record.getId()));
        if (CollUtil.isNotEmpty(scoreList)) {
            vo.setScores(scoreList.stream()
                    .map(s -> TrainingGuidanceScoreVO.builder()
                            .userId(s.getUserId())
                            .teacherName(s.getTeacherName())
                            .score(s.getScore())
                            .roleType(s.getRoleType())
                            .roleTypeText(TrainingScoringConstants.ROLE_RESPONSIBLE.equals(s.getRoleType())
                                    ? TrainingScoringConstants.ROLE_RESPONSIBLE_TEXT
                                    : TrainingScoringConstants.ROLE_PARTICIPATING_TEXT)
                            .build())
                    .collect(Collectors.toList()));
        }

        return vo;
    }

    @Override
    public Page<TrainingGuidanceRecordVO> pageRecords(TrainingGuidanceQueryRequest req) {
        long pageNum = req.getPageNum();
        long pageSize = req.getPageSize();

        QueryWrapper wrapper = getQueryWrapper(req);
        if (StrUtil.isNotBlank(req.getAdminReviewStatus())) {
            Page<TrainingGuidanceRecord> recordPage = this.page(Page.of(pageNum, pageSize), wrapper);
            List<TrainingGuidanceRecordVO> voList = recordPage.getRecords().stream()
                    .map(this::getRecordVO)
                    .filter(vo -> req.getAdminReviewStatus().equals(vo.getAdminReviewStatus()))
                    .collect(Collectors.toList());
            Page<TrainingGuidanceRecordVO> voPage = new Page<>(pageNum, pageSize, recordPage.getTotalRow());
            voPage.setRecords(voList);
            return voPage;
        }
        Page<TrainingGuidanceRecord> recordPage = this.page(Page.of(pageNum, pageSize), wrapper);
        List<TrainingGuidanceRecordVO> voList = recordPage.getRecords().stream()
                .map(this::getRecordVO)
                .collect(Collectors.toList());
        Page<TrainingGuidanceRecordVO> voPage = new Page<>(pageNum, pageSize, recordPage.getTotalRow());
        voPage.setRecords(voList);
        return voPage;
    }

    @Override
    public Page<TrainingGuidanceRecordVO> pageMyRelatedRecords(Long userId, TrainingGuidanceQueryRequest req) {
        long pageNum = req.getPageNum();
        long pageSize = req.getPageSize();

        // 提交人 OR 在得分表中被分配了得分的教师，均可看到记录
        QueryWrapper wrapper = QueryWrapper.create()
                .eq("id", req.getId())
                .like("training_name", req.getTrainingName())
                .where("(user_id = ? OR id IN (SELECT record_id FROM training_guidance_score WHERE user_id = ? AND is_delete = 0))",
                       userId, userId);
        wrapper.orderBy("create_time", false);

        Page<TrainingGuidanceRecord> recordPage = this.page(Page.of(pageNum, pageSize), wrapper);
        List<TrainingGuidanceRecordVO> voList = recordPage.getRecords().stream()
                .map(record -> {
                    TrainingGuidanceRecordVO vo = getRecordVO(record);
                    if (vo.getScores() != null) {
                        // 查找当前用户的得分
                        BigDecimal myTotal = vo.getScores().stream()
                                .filter(s -> userId.equals(s.getUserId()))
                                .map(TrainingGuidanceScoreVO::getScore)
                                .reduce(BigDecimal.ZERO, BigDecimal::add);
                        if (myTotal.compareTo(BigDecimal.ZERO) > 0) {
                            vo.setMyScoreDisplay(myTotal.stripTrailingZeros().toPlainString());
                        }
                    }
                    return vo;
                })
                .filter(vo -> StrUtil.isBlank(req.getAdminReviewStatus())
                        || req.getAdminReviewStatus().equals(vo.getAdminReviewStatus()))
                .collect(Collectors.toList());
        Page<TrainingGuidanceRecordVO> voPage = new Page<>(pageNum, pageSize, recordPage.getTotalRow());
        voPage.setRecords(voList);
        return voPage;
    }

    @Override
    public BigDecimal getMyTotalScore(Long userId) {
        List<TrainingGuidanceScore> scores = trainingScoreMapper.selectListByQuery(
                QueryWrapper.create().eq("user_id", userId));
        if (CollUtil.isEmpty(scores)) return BigDecimal.ZERO;
        return scores.stream()
                .map(TrainingGuidanceScore::getScore)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(3, RoundingMode.HALF_UP);
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

        TrainingGuidanceRecord record = this.getById(recordId);
        if (record == null) throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "记录不存在");

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
            trainingScoreMapper.deleteByQuery(
                    QueryWrapper.create().eq("record_id", recordId));

            saveTrainingScores(recordId, record.getResponsibleTeachers(), record.getParticipatingTeachers());
        }
    }

    // ==================== 导出Excel ====================

    @Override
    public byte[] exportRecordsToExcel() {
        try (org.apache.poi.xssf.usermodel.XSSFWorkbook workbook =
                     new org.apache.poi.xssf.usermodel.XSSFWorkbook()) {

            // 表头：时间、实训名称、负责教师、参与教师
            String[] headers = {"序号", "时间", "实训名称", "负责教师", "参与教师"};

            org.apache.poi.ss.usermodel.Sheet sheet = workbook.createSheet("指导实训");

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

            // 查询所有记录
            List<TrainingGuidanceRecord> allRecords = this.list(
                    QueryWrapper.create().orderBy("semester", true).orderBy("create_time", true));

            int rowIdx = 1;
            int seq = 1;

            for (TrainingGuidanceRecord record : allRecords) {
                org.apache.poi.ss.usermodel.Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(seq++);
                row.createCell(1).setCellValue(record.getSemester() != null ? record.getSemester() : "");

                // 实训名称
                row.createCell(2).setCellValue(record.getTrainingName() != null ? record.getTrainingName() : "");

                // 负责教师：格式化为 "张三（2）、李四（2）"
                row.createCell(3).setCellValue(formatTeacherNames(record.getResponsibleTeachers(),
                        TrainingScoringConstants.RESPONSIBLE_SCORE));

                // 参与教师：格式化为 "王五（1）、赵六（1）"
                row.createCell(4).setCellValue(formatTeacherNames(record.getParticipatingTeachers(),
                        TrainingScoringConstants.PARTICIPATING_SCORE));
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
     * 格式化教师姓名列表，附加得分
     * 例如：[{"teacherName":"秦小旭"},{"teacherName":"方锦文"}] + 2分 → "秦小旭（2）、方锦文（2）"
     */
    private String formatTeacherNames(String teacherJson, BigDecimal score) {
        if (StrUtil.isBlank(teacherJson)) return "";
        try {
            JSONArray arr = new JSONArray(teacherJson);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < arr.size(); i++) {
                if (i > 0) sb.append("、");
                JSONObject entry = arr.getJSONObject(i);
                String name = entry.getStr("teacherName", "");
                sb.append(name).append("（")
                        .append(score.stripTrailingZeros().toPlainString())
                        .append("）");
            }
            return sb.toString();
        } catch (Exception e) {
            log.warn("解析教师JSON失败: {}", teacherJson, e);
            return teacherJson;
        }
    }
}
