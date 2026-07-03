package com.jgh.ghairouter.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import com.jgh.ghairouter.exception.BusinessException;
import com.jgh.ghairouter.exception.ErrorCode;
import com.jgh.ghairouter.mapper.*;
import com.jgh.ghairouter.model.constants.InnovationScoringConstants;
import com.jgh.ghairouter.model.dto.competition.InnovationEntrepreneurshipQueryRequest;
import com.jgh.ghairouter.model.entity.*;
import com.jgh.ghairouter.model.enums.ReviewStatusEnum;
import com.jgh.ghairouter.model.vo.InnovationEntrepreneurshipRecordVO;
import com.jgh.ghairouter.model.vo.InnovationEntrepreneurshipScoreVO;
import com.jgh.ghairouter.service.AiReviewService;
import com.jgh.ghairouter.service.InnovationEntrepreneurshipRecordService;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 大创业绩记录服务实现（v6）
 */
@Slf4j
@Service
public class InnovationEntrepreneurshipRecordServiceImpl
        extends ServiceImpl<InnovationEntrepreneurshipRecordMapper, InnovationEntrepreneurshipRecord>
        implements InnovationEntrepreneurshipRecordService {

    @Resource
    private AiReviewService aiReviewService;
    @Resource
    private UserMapper userMapper;
    @Resource
    private TeacherCompetitionAuditRecordMapper auditMapper;
    @Resource
    private InnovationEntrepreneurshipScoreMapper innovationScoreMapper;

    private static final String DEFAULT_TYPE_NAME = "大创业绩";

    @Transactional(rollbackFor = Exception.class)
    @Override
    public Long addRecord(Long userId,
                          String projectNumber,
                          String projectName,
                          String projectLevel,
                          String projectType,
                          String projectStatus,
                          String studentLeader,
                          String memberData,
                          String scoreData,
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

        InnovationEntrepreneurshipRecord record = new InnovationEntrepreneurshipRecord();
        record.setUserId(userId);
        record.setTypeName(DEFAULT_TYPE_NAME);
        record.setProjectNumber(projectNumber);
        record.setProjectName(projectName);
        record.setProjectLevel(projectLevel);
        record.setProjectType(projectType);
        record.setProjectStatus(projectStatus);
        record.setStudentLeader(studentLeader);
        record.setMemberData(memberData);
        record.setScoreData(scoreData);
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
                aiReviewService.autoReview(record.getId(), record.getTypeName(), projectName, base64, mimeType);
            } catch (Exception e) {
                log.error("AI审核触发失败", e);
            }
        }
        return record.getId();
    }

    // ==================== 保存得分明细 ====================

    private void saveInnovationScores(Long recordId, String memberData, String projectLevel) {
        if (StrUtil.isBlank(memberData)) return;

        LocalDateTime now = LocalDateTime.now();
        try {
            JSONArray arr = new JSONArray(memberData);
            int memberCount = arr.size();
            if (memberCount == 0) return;

            // 计算总分
            BigDecimal totalScore = InnovationScoringConstants.calcProjectScore(projectLevel);
            if (totalScore.compareTo(BigDecimal.ZERO) <= 0) return;

            // 确定 leaderIndex
            int leaderIndex = -1;
            for (int i = 0; i < arr.size(); i++) {
                JSONObject entry = arr.getJSONObject(i);
                if (entry.getBool("isLeader", false)) {
                    leaderIndex = i;
                    break;
                }
            }

            // 分配得分
            List<BigDecimal> distributed = InnovationScoringConstants.distributeScore(totalScore, memberCount, leaderIndex);

            for (int i = 0; i < arr.size(); i++) {
                JSONObject entry = arr.getJSONObject(i);
                String teacherName = entry.getStr("teacherName");
                if (StrUtil.isBlank(teacherName)) continue;

                InnovationEntrepreneurshipScore score = new InnovationEntrepreneurshipScore();
                score.setRecordId(recordId);
                score.setTeacherName(teacherName);
                score.setScore(distributed.get(i));
                score.setIsLeader(entry.getBool("isLeader", false) ? 1 : 0);

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
                innovationScoreMapper.insert(score);
            }
        } catch (Exception e) {
            log.error("解析成员数据JSON失败: {}", memberData, e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "保存得分失败");
        }
    }

    // ==================== 查询 ====================

    @Override
    public QueryWrapper getQueryWrapper(InnovationEntrepreneurshipQueryRequest req) {
        if (req == null) throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        QueryWrapper wrapper = QueryWrapper.create()
                .eq("id", req.getId())
                .eq("user_id", req.getUserId())
                .eq("project_level", req.getProjectLevel())
                .like("project_name", req.getProjectName());
        if (StrUtil.isNotBlank(req.getSortField())) {
            wrapper.orderBy(req.getSortField(), "ascend".equals(req.getSortOrder()));
        }
        wrapper.orderBy("create_time", false);
        return wrapper;
    }

    @Override
    public InnovationEntrepreneurshipRecordVO getRecordVO(InnovationEntrepreneurshipRecord record) {
        if (record == null) return null;
        InnovationEntrepreneurshipRecordVO vo = new InnovationEntrepreneurshipRecordVO();
        BeanUtil.copyProperties(record, vo);

        // 级别显示名
        vo.setProjectLevelText(InnovationScoringConstants.getLevelText(record.getProjectLevel()));
        // 项目类型显示名
        vo.setProjectTypeText(InnovationScoringConstants.getProjectTypeText(record.getProjectType()));
        // 项目状态显示名
        vo.setProjectStatusText(InnovationScoringConstants.getProjectStatusText(record.getProjectStatus()));

        // 提交人姓名
        if (record.getUserId() != null) {
            User u = userMapper.selectOneById(record.getUserId());
            if (u != null) vo.setUserName(u.getUserName());
        }

        // 审核信息
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
        List<InnovationEntrepreneurshipScore> scoreList = innovationScoreMapper.selectListByQuery(
                QueryWrapper.create().eq("record_id", record.getId()));
        if (CollUtil.isNotEmpty(scoreList)) {
            vo.setScores(scoreList.stream()
                    .map(s -> InnovationEntrepreneurshipScoreVO.builder()
                            .userId(s.getUserId())
                            .teacherName(s.getTeacherName())
                            .score(s.getScore())
                            .isLeader(s.getIsLeader())
                            .build())
                    .collect(Collectors.toList()));
        }

        return vo;
    }

    @Override
    public Page<InnovationEntrepreneurshipRecordVO> pageRecords(InnovationEntrepreneurshipQueryRequest req) {
        long pageNum = req.getPageNum();
        long pageSize = req.getPageSize();

        QueryWrapper wrapper = getQueryWrapper(req);
        if (StrUtil.isNotBlank(req.getAdminReviewStatus())) {
            Page<InnovationEntrepreneurshipRecord> recordPage = this.page(Page.of(pageNum, pageSize), wrapper);
            List<InnovationEntrepreneurshipRecordVO> voList = recordPage.getRecords().stream()
                    .map(this::getRecordVO)
                    .filter(vo -> req.getAdminReviewStatus().equals(vo.getAdminReviewStatus()))
                    .collect(Collectors.toList());
            Page<InnovationEntrepreneurshipRecordVO> voPage = new Page<>(pageNum, pageSize, recordPage.getTotalRow());
            voPage.setRecords(voList);
            return voPage;
        }
        Page<InnovationEntrepreneurshipRecord> recordPage = this.page(Page.of(pageNum, pageSize), wrapper);
        List<InnovationEntrepreneurshipRecordVO> voList = recordPage.getRecords().stream()
                .map(this::getRecordVO)
                .collect(Collectors.toList());
        Page<InnovationEntrepreneurshipRecordVO> voPage = new Page<>(pageNum, pageSize, recordPage.getTotalRow());
        voPage.setRecords(voList);
        return voPage;
    }

    @Override
    public Page<InnovationEntrepreneurshipRecordVO> pageMyRelatedRecords(Long userId, InnovationEntrepreneurshipQueryRequest req) {
        long pageNum = req.getPageNum();
        long pageSize = req.getPageSize();

        // 提交人 OR 在得分表中被分配了得分的成员，均可看到记录
        QueryWrapper wrapper = QueryWrapper.create()
                .eq("id", req.getId())
                .eq("project_level", req.getProjectLevel())
                .like("project_name", req.getProjectName())
                .where("(user_id = ? OR id IN (SELECT record_id FROM innovation_entrepreneurship_score WHERE user_id = ? AND is_delete = 0))",
                       userId, userId);
        wrapper.orderBy("create_time", false);

        Page<InnovationEntrepreneurshipRecord> recordPage = this.page(Page.of(pageNum, pageSize), wrapper);
        List<InnovationEntrepreneurshipRecordVO> voList = recordPage.getRecords().stream()
                .map(record -> {
                    InnovationEntrepreneurshipRecordVO vo = getRecordVO(record);
                    if (vo.getScores() != null) {
                        BigDecimal myTotal = vo.getScores().stream()
                                .filter(s -> userId.equals(s.getUserId()))
                                .map(InnovationEntrepreneurshipScoreVO::getScore)
                                .reduce(BigDecimal.ZERO, BigDecimal::add);
                        if (myTotal.compareTo(BigDecimal.ZERO) > 0) {
                            vo.setMyScoreDisplay(myTotal.stripTrailingZeros().toPlainString());
                        }
                    }
                    return vo;
                })
                .collect(Collectors.toList());
        Page<InnovationEntrepreneurshipRecordVO> voPage = new Page<>(pageNum, pageSize, recordPage.getTotalRow());
        voPage.setRecords(voList);
        return voPage;
    }

    @Override
    public BigDecimal getMyTotalScore(Long userId) {
        List<InnovationEntrepreneurshipScore> scores = innovationScoreMapper.selectListByQuery(
                QueryWrapper.create().eq("user_id", userId));
        if (CollUtil.isEmpty(scores)) return BigDecimal.ZERO;
        return scores.stream()
                .map(InnovationEntrepreneurshipScore::getScore)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(3, RoundingMode.HALF_UP);
    }

    // ==================== 审核 ====================

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void adminReview(Long recordId, String reviewStatus, String reviewComment, Long adminId) {
        if (recordId == null) throw new BusinessException(ErrorCode.PARAMS_ERROR, "记录ID不能为空");
        if (StrUtil.isBlank(reviewStatus)) throw new BusinessException(ErrorCode.PARAMS_ERROR, "审核状态不能为空");

        ReviewStatusEnum statusEnum = ReviewStatusEnum.getEnumByValue(reviewStatus);
        if (statusEnum == null) throw new BusinessException(ErrorCode.PARAMS_ERROR, "无效的审核状态");
        if (statusEnum == ReviewStatusEnum.PENDING)
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "审核状态不能为待审核");

        InnovationEntrepreneurshipRecord record = this.getById(recordId);
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
            innovationScoreMapper.deleteByQuery(
                    QueryWrapper.create().eq("record_id", recordId));

            saveInnovationScores(recordId, record.getMemberData(), record.getProjectLevel());
        }
    }

    // ==================== 导出Excel ====================

    @Override
    public byte[] exportRecordsToExcel() {
        try (org.apache.poi.xssf.usermodel.XSSFWorkbook workbook =
                     new org.apache.poi.xssf.usermodel.XSSFWorkbook()) {

            org.apache.poi.ss.usermodel.CellStyle headerStyle = workbook.createCellStyle();
            org.apache.poi.ss.usermodel.Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);

            // 章节标题样式
            org.apache.poi.ss.usermodel.CellStyle sectionStyle = workbook.createCellStyle();
            sectionStyle.setFont(headerFont);
            sectionStyle.setAlignment(org.apache.poi.ss.usermodel.HorizontalAlignment.CENTER);
            sectionStyle.setVerticalAlignment(org.apache.poi.ss.usermodel.VerticalAlignment.CENTER);

            String[] headers = {"序号", "项目编号", "项目名称", "级别", "项目类型", "项目负责人", "指导教师及得分"};
            int colCount = headers.length;

            org.apache.poi.ss.usermodel.Sheet sheet = workbook.createSheet("大创业绩");

            List<InnovationEntrepreneurshipRecord> allRecords = this.list(
                    QueryWrapper.create().orderBy("create_time", true));

            // 按项目状态分组
            List<InnovationEntrepreneurshipRecord> concludedRecords = allRecords.stream()
                    .filter(r -> InnovationScoringConstants.STATUS_CONCLUDED.equals(r.getProjectStatus()))
                    .collect(Collectors.toList());
            List<InnovationEntrepreneurshipRecord> newRecords = allRecords.stream()
                    .filter(r -> InnovationScoringConstants.STATUS_NEWLY_ADDED.equals(r.getProjectStatus()))
                    .collect(Collectors.toList());
            // 没有状态的老数据归入结题
            List<InnovationEntrepreneurshipRecord> otherRecords = allRecords.stream()
                    .filter(r -> r.getProjectStatus() == null
                            || (!InnovationScoringConstants.STATUS_CONCLUDED.equals(r.getProjectStatus())
                                && !InnovationScoringConstants.STATUS_NEWLY_ADDED.equals(r.getProjectStatus())))
                    .collect(Collectors.toList());

            int rowIdx = 0;

            // ========== 一、结题项目 ==========
            if (!concludedRecords.isEmpty() || !otherRecords.isEmpty()) {
                org.apache.poi.ss.usermodel.Row sectionTitle1 = sheet.createRow(rowIdx++);
                org.apache.poi.ss.usermodel.Cell titleCell1 = sectionTitle1.createCell(0);
                titleCell1.setCellValue("一、结题的创新创业训练计划项目");
                titleCell1.setCellStyle(sectionStyle);
                sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(rowIdx - 1, rowIdx - 1, 0, colCount - 1));

                // 表头行
                org.apache.poi.ss.usermodel.Row headerRow1 = sheet.createRow(rowIdx++);
                for (int i = 0; i < headers.length; i++) {
                    org.apache.poi.ss.usermodel.Cell cell = headerRow1.createCell(i);
                    cell.setCellValue(headers[i]);
                    cell.setCellStyle(headerStyle);
                }

                // 合并结题和旧数据
                List<InnovationEntrepreneurshipRecord> section1 = new ArrayList<>();
                section1.addAll(concludedRecords);
                section1.addAll(otherRecords);

                int seq = 1;
                for (InnovationEntrepreneurshipRecord record : section1) {
                    org.apache.poi.ss.usermodel.Row row = sheet.createRow(rowIdx++);
                    row.createCell(0).setCellValue(seq++);
                    row.createCell(1).setCellValue(record.getProjectNumber() != null ? record.getProjectNumber() : "");
                    row.createCell(2).setCellValue(record.getProjectName() != null ? record.getProjectName() : "");
                    row.createCell(3).setCellValue(InnovationScoringConstants.getLevelText(record.getProjectLevel()));
                    row.createCell(4).setCellValue(InnovationScoringConstants.getProjectTypeText(record.getProjectType()));
                    row.createCell(5).setCellValue(record.getStudentLeader() != null ? record.getStudentLeader() : "");
                    row.createCell(6).setCellValue(formatTeacherScores(record.getMemberData(), record.getProjectLevel()));
                }

                // 空两行
                rowIdx += 2;
            }

            // ========== 二、新增项目 ==========
            if (!newRecords.isEmpty()) {
                org.apache.poi.ss.usermodel.Row sectionTitle2 = sheet.createRow(rowIdx++);
                org.apache.poi.ss.usermodel.Cell titleCell2 = sectionTitle2.createCell(0);
                titleCell2.setCellValue("二、新增的创新创业训练计划项目");
                titleCell2.setCellStyle(sectionStyle);
                sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(rowIdx - 1, rowIdx - 1, 0, colCount - 1));

                // 表头行
                org.apache.poi.ss.usermodel.Row headerRow2 = sheet.createRow(rowIdx++);
                for (int i = 0; i < headers.length; i++) {
                    org.apache.poi.ss.usermodel.Cell cell = headerRow2.createCell(i);
                    cell.setCellValue(headers[i]);
                    cell.setCellStyle(headerStyle);
                }

                int seq = 1;
                for (InnovationEntrepreneurshipRecord record : newRecords) {
                    org.apache.poi.ss.usermodel.Row row = sheet.createRow(rowIdx++);
                    row.createCell(0).setCellValue(seq++);
                    row.createCell(1).setCellValue(record.getProjectNumber() != null ? record.getProjectNumber() : "");
                    row.createCell(2).setCellValue(record.getProjectName() != null ? record.getProjectName() : "");
                    row.createCell(3).setCellValue(InnovationScoringConstants.getLevelText(record.getProjectLevel()));
                    row.createCell(4).setCellValue(InnovationScoringConstants.getProjectTypeText(record.getProjectType()));
                    row.createCell(5).setCellValue(record.getStudentLeader() != null ? record.getStudentLeader() : "");
                    row.createCell(6).setCellValue(formatTeacherScores(record.getMemberData(), record.getProjectLevel()));
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
     * 格式化指导教师及得分为显示字符串
     * 格式：教师名得分、教师名得分...
     * 如：龙丹2.8、刘欣1.2
     */
    private String formatTeacherScores(String memberData, String projectLevel) {
        if (StrUtil.isBlank(memberData)) return "";
        try {
            JSONArray arr = new JSONArray(memberData);
            int memberCount = arr.size();
            if (memberCount == 0) return "";

            BigDecimal totalScore = InnovationScoringConstants.calcProjectScore(projectLevel);
            if (totalScore.compareTo(BigDecimal.ZERO) <= 0) return "";

            int leaderIndex = -1;
            for (int i = 0; i < arr.size(); i++) {
                JSONObject entry = arr.getJSONObject(i);
                if (entry.getBool("isLeader", false)) {
                    leaderIndex = i;
                    break;
                }
            }

            List<BigDecimal> distributed = InnovationScoringConstants.distributeScore(totalScore, memberCount, leaderIndex);

            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < arr.size(); i++) {
                if (i > 0) sb.append("、");
                JSONObject entry = arr.getJSONObject(i);
                String name = entry.getStr("teacherName", "");
                sb.append(name);
                BigDecimal score = distributed.get(i);
                String scoreStr = score.stripTrailingZeros().toPlainString();
                sb.append(scoreStr);
            }
            return sb.toString();
        } catch (Exception e) {
            log.error("格式化指导教师得分失败: {}", memberData, e);
            return memberData;
        }
    }
}
