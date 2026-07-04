package com.jgh.ghairouter.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import com.jgh.ghairouter.exception.BusinessException;
import com.jgh.ghairouter.exception.ErrorCode;
import com.jgh.ghairouter.mapper.*;
import com.jgh.ghairouter.model.constants.ResearchScoringConstants;
import com.jgh.ghairouter.model.dto.competition.ResearchAchievementQueryRequest;
import com.jgh.ghairouter.model.entity.*;
import com.jgh.ghairouter.model.enums.ReviewStatusEnum;
import com.jgh.ghairouter.model.vo.ResearchAchievementRecordVO;
import com.jgh.ghairouter.model.vo.ResearchAchievementScoreVO;
import com.jgh.ghairouter.service.AiReviewService;
import com.jgh.ghairouter.service.ResearchAchievementRecordService;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 科研及教材业绩记录服务实现（v5）
 */
@Slf4j
@Service
public class ResearchAchievementRecordServiceImpl
        extends ServiceImpl<ResearchAchievementRecordMapper, ResearchAchievementRecord>
        implements ResearchAchievementRecordService {

    @Resource
    private AiReviewService aiReviewService;
    @Resource
    private UserMapper userMapper;
    @Resource
    private TeacherCompetitionAuditRecordMapper auditMapper;
    @Resource
    private ResearchAchievementScoreMapper researchScoreMapper;

    private static final String DEFAULT_TYPE_NAME = "科研及教材业绩";

    // ==================== 删除（代码层面软删除级联） ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removeById(Serializable id) {
        // 1. 软删除子表得分明细记录
        researchScoreMapper.deleteByQuery(
                QueryWrapper.create().eq("record_id", id));
        // 2. 软删除审核记录
        auditMapper.deleteByQuery(
                QueryWrapper.create().eq("record_id", id).eq("record_type", DEFAULT_TYPE_NAME));
        // 3. 软删除主表记录
        return super.removeById(id);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public Long addRecord(Long userId,
                          String subType,
                          String achievementName,
                          String projectSource,
                          BigDecimal fundingAmount,
                          String patentNumber,
                          String patentType,
                          BigDecimal wordCount,
                          String textbookType,
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

        ResearchAchievementRecord record = new ResearchAchievementRecord();
        record.setUserId(userId);
        record.setTypeName(DEFAULT_TYPE_NAME);
        record.setSubType(subType);
        record.setAchievementName(achievementName);
        record.setProjectSource(projectSource);
        record.setFundingAmount(fundingAmount);
        record.setPatentNumber(patentNumber);
        record.setPatentType(patentType);
        record.setWordCount(wordCount);
        record.setTextbookType(textbookType);
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
                aiReviewService.autoReview(record.getId(), record.getTypeName(), achievementName, base64, mimeType);
            } catch (Exception e) {
                log.error("AI审核触发失败", e);
            }
        }
        return record.getId();
    }

    // ==================== 保存得分明细 ====================

    private void saveResearchScores(Long recordId, String memberData, String subType,
                                     BigDecimal fundingAmount, String patentType,
                                     String textbookType, BigDecimal wordCount) {
        if (StrUtil.isBlank(memberData)) return;

        LocalDateTime now = LocalDateTime.now();
        try {
            JSONArray arr = new JSONArray(memberData);
            int memberCount = arr.size();
            if (memberCount == 0) return;

            // 计算总分
            BigDecimal totalScore;
            if (ResearchScoringConstants.SUB_TYPE_HORIZONTAL_PROJECT.equals(subType)) {
                totalScore = ResearchScoringConstants.calcHorizontalProjectScore(fundingAmount);
            } else if (ResearchScoringConstants.SUB_TYPE_PATENT.equals(subType)) {
                totalScore = ResearchScoringConstants.calcPatentScore(patentType);
            } else if (ResearchScoringConstants.SUB_TYPE_TEXTBOOK.equals(subType)) {
                totalScore = ResearchScoringConstants.calcTextbookScore(textbookType, wordCount);
            } else {
                return;
            }

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
            List<BigDecimal> distributed = ResearchScoringConstants.distributeScore(totalScore, memberCount, leaderIndex);

            for (int i = 0; i < arr.size(); i++) {
                JSONObject entry = arr.getJSONObject(i);
                String teacherName = entry.getStr("teacherName");
                if (StrUtil.isBlank(teacherName)) continue;

                ResearchAchievementScore score = new ResearchAchievementScore();
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
                researchScoreMapper.insert(score);
            }
        } catch (Exception e) {
            log.error("解析成员数据JSON失败: {}", memberData, e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "保存得分失败");
        }
    }

    // ==================== 查询 ====================

    @Override
    public QueryWrapper getQueryWrapper(ResearchAchievementQueryRequest req) {
        if (req == null) throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        QueryWrapper wrapper = QueryWrapper.create()
                .eq("id", req.getId())
                .eq("user_id", req.getUserId())
                .eq("sub_type", req.getSubType())
                .like("achievement_name", req.getAchievementName());
        if (StrUtil.isNotBlank(req.getSortField())) {
            wrapper.orderBy(req.getSortField(), "ascend".equals(req.getSortOrder()));
        }
        wrapper.orderBy("create_time", false);
        return wrapper;
    }

    @Override
    public ResearchAchievementRecordVO getRecordVO(ResearchAchievementRecord record) {
        if (record == null) return null;
        ResearchAchievementRecordVO vo = new ResearchAchievementRecordVO();
        BeanUtil.copyProperties(record, vo);

        // 子类型显示名
        vo.setSubTypeText(ResearchScoringConstants.getSubTypeText(record.getSubType()));
        // 专利类别显示名
        vo.setPatentTypeText(ResearchScoringConstants.getPatentTypeText(record.getPatentType()));
        // 教材类型显示名
        vo.setTextbookTypeText(ResearchScoringConstants.getTextbookTypeText(record.getTextbookType()));

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
        List<ResearchAchievementScore> scoreList = researchScoreMapper.selectListByQuery(
                QueryWrapper.create().eq("record_id", record.getId()));
        if (CollUtil.isNotEmpty(scoreList)) {
            vo.setScores(scoreList.stream()
                    .map(s -> ResearchAchievementScoreVO.builder()
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
    public Page<ResearchAchievementRecordVO> pageRecords(ResearchAchievementQueryRequest req) {
        long pageNum = req.getPageNum();
        long pageSize = req.getPageSize();

        QueryWrapper wrapper = getQueryWrapper(req);
        if (StrUtil.isNotBlank(req.getAdminReviewStatus())) {
            Page<ResearchAchievementRecord> recordPage = this.page(Page.of(pageNum, pageSize), wrapper);
            List<ResearchAchievementRecordVO> voList = recordPage.getRecords().stream()
                    .map(this::getRecordVO)
                    .filter(vo -> req.getAdminReviewStatus().equals(vo.getAdminReviewStatus()))
                    .collect(Collectors.toList());
            Page<ResearchAchievementRecordVO> voPage = new Page<>(pageNum, pageSize, recordPage.getTotalRow());
            voPage.setRecords(voList);
            return voPage;
        }
        Page<ResearchAchievementRecord> recordPage = this.page(Page.of(pageNum, pageSize), wrapper);
        List<ResearchAchievementRecordVO> voList = recordPage.getRecords().stream()
                .map(this::getRecordVO)
                .collect(Collectors.toList());
        Page<ResearchAchievementRecordVO> voPage = new Page<>(pageNum, pageSize, recordPage.getTotalRow());
        voPage.setRecords(voList);
        return voPage;
    }

    @Override
    public Page<ResearchAchievementRecordVO> pageMyRelatedRecords(Long userId, ResearchAchievementQueryRequest req) {
        long pageNum = req.getPageNum();
        long pageSize = req.getPageSize();

        // 先查出当前用户在得分表中关联的 recordId 列表
        List<Long> myScoredRecordIds = researchScoreMapper.selectListByQuery(
                        QueryWrapper.create()
                                .select("record_id")
                                .eq("user_id", userId)
                                .eq("is_delete", 0))
                .stream()
                .map(ResearchAchievementScore::getRecordId)
                .distinct()
                .collect(Collectors.toList());

        // 提交人 OR 在得分表中被分配了得分的成员，均可看到记录
        QueryWrapper wrapper = QueryWrapper.create()
                .eq("id", req.getId())
                .eq("sub_type", req.getSubType())
                .like("achievement_name", req.getAchievementName());
        if (CollUtil.isNotEmpty(myScoredRecordIds)) {
            String ids = myScoredRecordIds.stream()
                    .map(String::valueOf).collect(Collectors.joining(","));
            wrapper.where("(user_id = " + userId + " OR id IN (" + ids + "))");
        } else {
            wrapper.eq("user_id", userId);
        }
        wrapper.orderBy("create_time", false);

        Page<ResearchAchievementRecord> recordPage = this.page(Page.of(pageNum, pageSize), wrapper);
        List<ResearchAchievementRecordVO> voList = recordPage.getRecords().stream()
                .map(record -> {
                    ResearchAchievementRecordVO vo = getRecordVO(record);
                    if (vo.getScores() != null) {
                        BigDecimal myTotal = vo.getScores().stream()
                                .filter(s -> userId.equals(s.getUserId()))
                                .map(ResearchAchievementScoreVO::getScore)
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
        Page<ResearchAchievementRecordVO> voPage = new Page<>(pageNum, pageSize, recordPage.getTotalRow());
        voPage.setRecords(voList);
        return voPage;
    }

    @Override
    public BigDecimal getMyTotalScore(Long userId) {
        List<ResearchAchievementScore> scores = researchScoreMapper.selectListByQuery(
                QueryWrapper.create().eq("user_id", userId));
        if (CollUtil.isEmpty(scores)) return BigDecimal.ZERO;
        return scores.stream()
                .map(ResearchAchievementScore::getScore)
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

        ResearchAchievementRecord record = this.getById(recordId);
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
            researchScoreMapper.deleteByQuery(
                    QueryWrapper.create().eq("record_id", recordId));

            saveResearchScores(recordId, record.getMemberData(), record.getSubType(),
                    record.getFundingAmount(), record.getPatentType(),
                    record.getTextbookType(), record.getWordCount());
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

            // ---- 横向科研项目 Sheet ----
            createHorizontalProjectSheet(workbook, headerStyle);

            // ---- 专利 Sheet ----
            createPatentSheet(workbook, headerStyle);

            // ---- 教材 Sheet ----
            createTextbookSheet(workbook, headerStyle);

            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            workbook.write(bos);
            return bos.toByteArray();
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "Excel生成失败: " + e.getMessage());
        }
    }

    private void createHorizontalProjectSheet(org.apache.poi.xssf.usermodel.XSSFWorkbook workbook,
                                               org.apache.poi.ss.usermodel.CellStyle headerStyle) {
        String[] headers = {"序号", "项目名称", "项目来源", "到位经费(万元)", "项目组成员及得分", "业绩分"};
        org.apache.poi.ss.usermodel.Sheet sheet = workbook.createSheet("横向科研项目");

        org.apache.poi.ss.usermodel.Row headerRow = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            org.apache.poi.ss.usermodel.Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        List<ResearchAchievementRecord> records = this.list(
                QueryWrapper.create().eq("sub_type", ResearchScoringConstants.SUB_TYPE_HORIZONTAL_PROJECT)
                        .orderBy("create_time", true));

        int rowIdx = 1;
        int seq = 1;
        for (ResearchAchievementRecord record : records) {
            org.apache.poi.ss.usermodel.Row row = sheet.createRow(rowIdx++);
            row.createCell(0).setCellValue(seq++);
            row.createCell(1).setCellValue(record.getAchievementName() != null ? record.getAchievementName() : "");
            row.createCell(2).setCellValue(record.getProjectSource() != null ? record.getProjectSource() : "");
            row.createCell(3).setCellValue(record.getFundingAmount() != null
                    ? record.getFundingAmount().stripTrailingZeros().toPlainString() : "0");
            row.createCell(4).setCellValue(formatMemberNames(record.getMemberData()));
            row.createCell(5).setCellValue(formatScoreTotal(record.getId()));
        }

        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private void createPatentSheet(org.apache.poi.xssf.usermodel.XSSFWorkbook workbook,
                                    org.apache.poi.ss.usermodel.CellStyle headerStyle) {
        String[] headers = {"序号", "专利名称", "专利号", "专利类别", "发明成员及得分", "业绩分"};
        org.apache.poi.ss.usermodel.Sheet sheet = workbook.createSheet("专利");

        org.apache.poi.ss.usermodel.Row headerRow = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            org.apache.poi.ss.usermodel.Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        List<ResearchAchievementRecord> records = this.list(
                QueryWrapper.create().eq("sub_type", ResearchScoringConstants.SUB_TYPE_PATENT)
                        .orderBy("create_time", true));

        int rowIdx = 1;
        int seq = 1;
        for (ResearchAchievementRecord record : records) {
            org.apache.poi.ss.usermodel.Row row = sheet.createRow(rowIdx++);
            row.createCell(0).setCellValue(seq++);
            row.createCell(1).setCellValue(record.getAchievementName() != null ? record.getAchievementName() : "");
            row.createCell(2).setCellValue(record.getPatentNumber() != null ? record.getPatentNumber() : "");
            row.createCell(3).setCellValue(ResearchScoringConstants.getPatentTypeText(record.getPatentType()));
            row.createCell(4).setCellValue(formatMemberNames(record.getMemberData()));
            row.createCell(5).setCellValue(formatScoreTotal(record.getId()));
        }

        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private void createTextbookSheet(org.apache.poi.xssf.usermodel.XSSFWorkbook workbook,
                                      org.apache.poi.ss.usermodel.CellStyle headerStyle) {
        String[] headers = {"序号", "教材及自编讲义名称", "字数(万)", "教材类型", "参编人员及得分", "业绩分"};
        org.apache.poi.ss.usermodel.Sheet sheet = workbook.createSheet("教材及自编讲义");

        org.apache.poi.ss.usermodel.Row headerRow = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            org.apache.poi.ss.usermodel.Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        List<ResearchAchievementRecord> records = this.list(
                QueryWrapper.create().eq("sub_type", ResearchScoringConstants.SUB_TYPE_TEXTBOOK)
                        .orderBy("create_time", true));

        int rowIdx = 1;
        int seq = 1;
        for (ResearchAchievementRecord record : records) {
            org.apache.poi.ss.usermodel.Row row = sheet.createRow(rowIdx++);
            row.createCell(0).setCellValue(seq++);
            row.createCell(1).setCellValue(record.getAchievementName() != null ? record.getAchievementName() : "");
            row.createCell(2).setCellValue(record.getWordCount() != null
                    ? record.getWordCount().stripTrailingZeros().toPlainString() : "0");
            row.createCell(3).setCellValue(ResearchScoringConstants.getTextbookTypeText(record.getTextbookType()));
            row.createCell(4).setCellValue(formatMemberNames(record.getMemberData()));
            row.createCell(5).setCellValue(formatScoreTotal(record.getId()));
        }

        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    /**
     * 格式化成员姓名为显示字符串
     */
    private String formatMemberNames(String memberData) {
        if (StrUtil.isBlank(memberData)) return "";
        try {
            JSONArray arr = new JSONArray(memberData);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < arr.size(); i++) {
                if (i > 0) sb.append("、");
                JSONObject entry = arr.getJSONObject(i);
                String name = entry.getStr("teacherName", "");
                sb.append(name);
            }
            return sb.toString();
        } catch (Exception e) {
            return memberData;
        }
    }

    /**
     * 获取记录的总得分
     */
    private String formatScoreTotal(Long recordId) {
        List<ResearchAchievementScore> scores = researchScoreMapper.selectListByQuery(
                QueryWrapper.create().eq("record_id", recordId));
        if (CollUtil.isEmpty(scores)) {
            return "0";
        }
        BigDecimal total = scores.stream()
                .map(ResearchAchievementScore::getScore)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return total.stripTrailingZeros().toPlainString();
    }
}
