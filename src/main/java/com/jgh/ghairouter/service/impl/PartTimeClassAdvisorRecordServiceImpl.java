package com.jgh.ghairouter.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.jgh.ghairouter.exception.BusinessException;
import com.jgh.ghairouter.exception.ErrorCode;
import com.jgh.ghairouter.mapper.*;
import com.jgh.ghairouter.model.constants.PartTimeClassAdvisorScoringConstants;
import com.jgh.ghairouter.model.dto.competition.PartTimeClassAdvisorQueryRequest;
import com.jgh.ghairouter.model.entity.*;
import com.jgh.ghairouter.model.enums.ReviewStatusEnum;
import com.jgh.ghairouter.model.vo.PartTimeClassAdvisorRecordVO;
import com.jgh.ghairouter.model.vo.PartTimeClassAdvisorScoreVO;
import com.jgh.ghairouter.service.AiReviewService;
import com.jgh.ghairouter.service.PartTimeClassAdvisorRecordService;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.Year;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 兼职班主任业绩记录服务实现（v10）
 */
@Slf4j
@Service
public class PartTimeClassAdvisorRecordServiceImpl
        extends ServiceImpl<PartTimeClassAdvisorRecordMapper, PartTimeClassAdvisorRecord>
        implements PartTimeClassAdvisorRecordService {

    @Resource
    private AiReviewService aiReviewService;
    @Resource
    private UserMapper userMapper;
    @Resource
    private TeacherCompetitionAuditRecordMapper auditMapper;
    @Resource
    private PartTimeClassAdvisorScoreMapper partTimeAdvisorScoreMapper;

    private static final String DEFAULT_TYPE_NAME = PartTimeClassAdvisorScoringConstants.TYPE_NAME;

    @Override
    public Long addRecord(Long userId,
                          String teacherName,
                          String classId,
                          BigDecimal studyStyleWorkReq,
                          BigDecimal studyStyleEffect,
                          BigDecimal safetyEduWorkReq,
                          BigDecimal safetyEduEffect,
                          BigDecimal strugglingStudentWorkReq,
                          BigDecimal strugglingStudentEffect,
                          BigDecimal achievementSafety,
                          BigDecimal achievementStudyStyle,
                          BigDecimal achievementStruggling,
                          Integer isFreshmenOrGraduating,
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

        // 计算行政班分
        BigDecimal adminClassScore = (isFreshmenOrGraduating != null && isFreshmenOrGraduating == 1)
                ? PartTimeClassAdvisorScoringConstants.ADMIN_CLASS_SCORE_HALF
                : PartTimeClassAdvisorScoringConstants.ADMIN_CLASS_SCORE_NORMAL;

        PartTimeClassAdvisorRecord record = new PartTimeClassAdvisorRecord();
        record.setUserId(userId);
        record.setTypeName(DEFAULT_TYPE_NAME);
        record.setTeacherName(teacherName);
        record.setClassId(classId);
        record.setStudyStyleWorkReq(studyStyleWorkReq);
        record.setStudyStyleEffect(studyStyleEffect);
        record.setSafetyEduWorkReq(safetyEduWorkReq);
        record.setSafetyEduEffect(safetyEduEffect);
        record.setStrugglingStudentWorkReq(strugglingStudentWorkReq);
        record.setStrugglingStudentEffect(strugglingStudentEffect);
        record.setAchievementSafety(achievementSafety);
        record.setAchievementStudyStyle(achievementStudyStyle);
        record.setAchievementStruggling(achievementStruggling);
        record.setAdminClassScore(adminClassScore);
        record.setIsFreshmenOrGraduating(isFreshmenOrGraduating != null ? isFreshmenOrGraduating : 0);
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
                aiReviewService.autoReview(record.getId(), record.getTypeName(),
                        teacherName + "-" + classId, base64, mimeType);
            } catch (Exception e) {
                log.error("AI审核触发失败", e);
            }
        }
        return record.getId();
    }

    // ==================== 保存得分明细 ====================

    /**
     * 重新计算指定教师的总得分。
     * 计算逻辑：
     * 1. 找到该教师所有审核通过的记录
     * 2. 计算所有行总得分之和 → 平均分
     * 3. 换算平均分为折合分
     * 4. 最终得分 = 平均分折合分 + 所有行政班分之和
     * 5. 删除该教师旧得分记录，插入一条新记录
     */
    private void recalculateTeacherScore(String teacherName, PartTimeClassAdvisorRecord currentRecord) {
        LocalDateTime now = LocalDateTime.now();

        // 查找教师用户
        Long teacherUserId = null;
        if (StrUtil.isNotBlank(teacherName)) {
            User teacher = userMapper.selectOneByQuery(
                    QueryWrapper.create().eq("user_name", teacherName));
            if (teacher != null) {
                teacherUserId = teacher.getId();
            }
        }

        // 获取该教师所有审核通过的记录
        List<PartTimeClassAdvisorRecord> allApprovedRecords = this.list(
                QueryWrapper.create()
                        .eq("teacher_name", teacherName));
        // 过滤出审核通过的（通过 audit 表判断）
        List<PartTimeClassAdvisorRecord> approvedRecords = new ArrayList<>();
        for (PartTimeClassAdvisorRecord r : allApprovedRecords) {
            TeacherCompetitionAuditRecord audit = auditMapper.selectOneByQuery(
                    QueryWrapper.create()
                            .eq("record_id", r.getId())
                            .eq("record_type", DEFAULT_TYPE_NAME)
                            .eq("admin_review_status", ReviewStatusEnum.PASSED.getValue()));
            if (audit != null) {
                approvedRecords.add(r);
            }
        }

        if (approvedRecords.isEmpty()) return;

        // 计算所有行总得分之和
        BigDecimal sumTotal = BigDecimal.ZERO;
        BigDecimal totalAdminClassScore = BigDecimal.ZERO;
        for (PartTimeClassAdvisorRecord r : approvedRecords) {
            sumTotal = sumTotal.add(PartTimeClassAdvisorScoringConstants.calcRowTotal(
                    r.getStudyStyleWorkReq(), r.getStudyStyleEffect(),
                    r.getSafetyEduWorkReq(), r.getSafetyEduEffect(),
                    r.getStrugglingStudentWorkReq(), r.getStrugglingStudentEffect(),
                    r.getAchievementSafety(), r.getAchievementStudyStyle(),
                    r.getAchievementStruggling()));
            totalAdminClassScore = totalAdminClassScore.add(
                    r.getAdminClassScore() != null ? r.getAdminClassScore() : BigDecimal.ZERO);
        }

        // 平均分
        BigDecimal avg = sumTotal.divide(new BigDecimal(approvedRecords.size()), 2, RoundingMode.HALF_UP);
        // 平均分折合分
        BigDecimal avgConverted = PartTimeClassAdvisorScoringConstants.convertScore(avg);
        // 最终得分 = 平均分折合分 + 行政班分合计
        BigDecimal finalScore = avgConverted.add(totalAdminClassScore);

        // 删除该教师旧的得分记录
        partTimeAdvisorScoreMapper.deleteByQuery(
                QueryWrapper.create().eq("teacher_name", teacherName));

        // 插入新得分记录
        PartTimeClassAdvisorScore scoreEntity = new PartTimeClassAdvisorScore();
        scoreEntity.setRecordId(currentRecord.getId()); // 关联到最后审核通过的记录
        scoreEntity.setTeacherName(teacherName);
        scoreEntity.setUserId(teacherUserId);
        scoreEntity.setScore(finalScore);
        scoreEntity.setCreateTime(now);
        scoreEntity.setUpdateTime(now);
        partTimeAdvisorScoreMapper.insert(scoreEntity);
    }

    // ==================== 查询 ====================

    @Override
    public QueryWrapper getQueryWrapper(PartTimeClassAdvisorQueryRequest req) {
        if (req == null) throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        QueryWrapper wrapper = QueryWrapper.create()
                .eq("id", req.getId())
                .eq("user_id", req.getUserId())
                .like("teacher_name", req.getTeacherName())
                .like("class_id", req.getClassId());
        if (StrUtil.isNotBlank(req.getSortField())) {
            wrapper.orderBy(req.getSortField(), "ascend".equals(req.getSortOrder()));
        }
        wrapper.orderBy("create_time", false);
        return wrapper;
    }

    @Override
    public PartTimeClassAdvisorRecordVO getRecordVO(PartTimeClassAdvisorRecord record) {
        if (record == null) return null;
        PartTimeClassAdvisorRecordVO vo = new PartTimeClassAdvisorRecordVO();
        BeanUtil.copyProperties(record, vo);

        // 计算行总得分和换算得分
        BigDecimal rowTotal = PartTimeClassAdvisorScoringConstants.calcRowTotal(
                record.getStudyStyleWorkReq(),
                record.getStudyStyleEffect(),
                record.getSafetyEduWorkReq(),
                record.getSafetyEduEffect(),
                record.getStrugglingStudentWorkReq(),
                record.getStrugglingStudentEffect(),
                record.getAchievementSafety(),
                record.getAchievementStudyStyle(),
                record.getAchievementStruggling());
        vo.setRowTotalScore(rowTotal);
        vo.setRowConvertedScore(PartTimeClassAdvisorScoringConstants.convertScore(rowTotal));

        // 提交人姓名
        if (record.getUserId() != null) {
            User u = userMapper.selectOneById(record.getUserId());
            if (u != null) vo.setUserName(u.getUserName());
        }

        // 审核信息
        TeacherCompetitionAuditRecord audit = auditMapper.selectOneByQuery(
                QueryWrapper.create().eq("record_id", record.getId())
                        .eq("record_type", record.getTypeName()));
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

        // 得分明细（按教师姓名查找，因为得分是按教师汇总的）
        List<PartTimeClassAdvisorScore> scoreList = partTimeAdvisorScoreMapper.selectListByQuery(
                QueryWrapper.create().eq("teacher_name", record.getTeacherName()));
        if (CollUtil.isNotEmpty(scoreList)) {
            vo.setScores(scoreList.stream()
                    .map(s -> PartTimeClassAdvisorScoreVO.builder()
                            .userId(s.getUserId())
                            .teacherName(s.getTeacherName())
                            .score(s.getScore())
                            .build())
                    .collect(Collectors.toList()));
        }

        return vo;
    }

    @Override
    public Page<PartTimeClassAdvisorRecordVO> pageRecords(PartTimeClassAdvisorQueryRequest req) {
        long pageNum = req.getPageNum();
        long pageSize = req.getPageSize();

        QueryWrapper wrapper = getQueryWrapper(req);
        if (StrUtil.isNotBlank(req.getAdminReviewStatus())) {
            Page<PartTimeClassAdvisorRecord> recordPage = this.page(Page.of(pageNum, pageSize), wrapper);
            List<PartTimeClassAdvisorRecordVO> voList = recordPage.getRecords().stream()
                    .map(this::getRecordVO)
                    .filter(vo -> req.getAdminReviewStatus().equals(vo.getAdminReviewStatus()))
                    .collect(Collectors.toList());
            Page<PartTimeClassAdvisorRecordVO> voPage = new Page<>(pageNum, pageSize, recordPage.getTotalRow());
            voPage.setRecords(voList);
            return voPage;
        }
        Page<PartTimeClassAdvisorRecord> recordPage = this.page(Page.of(pageNum, pageSize), wrapper);
        List<PartTimeClassAdvisorRecordVO> voList = recordPage.getRecords().stream()
                .map(this::getRecordVO)
                .collect(Collectors.toList());
        Page<PartTimeClassAdvisorRecordVO> voPage = new Page<>(pageNum, pageSize, recordPage.getTotalRow());
        voPage.setRecords(voList);
        return voPage;
    }

    @Override
    public Page<PartTimeClassAdvisorRecordVO> pageMyRelatedRecords(Long userId, PartTimeClassAdvisorQueryRequest req) {
        long pageNum = req.getPageNum();
        long pageSize = req.getPageSize();

        QueryWrapper wrapper = QueryWrapper.create()
                .eq("id", req.getId())
                .like("teacher_name", req.getTeacherName())
                .like("class_id", req.getClassId())
                .where("user_id = ?", userId);
        wrapper.orderBy("create_time", false);

        Page<PartTimeClassAdvisorRecord> recordPage = this.page(Page.of(pageNum, pageSize), wrapper);
        List<PartTimeClassAdvisorRecordVO> voList = recordPage.getRecords().stream()
                .map(record -> {
                    PartTimeClassAdvisorRecordVO vo = getRecordVO(record);
                    if (vo.getScores() != null) {
                        BigDecimal myTotal = vo.getScores().stream()
                                .filter(s -> userId.equals(s.getUserId()))
                                .map(PartTimeClassAdvisorScoreVO::getScore)
                                .reduce(BigDecimal.ZERO, BigDecimal::add);
                        if (myTotal.compareTo(BigDecimal.ZERO) > 0) {
                            vo.setMyScoreDisplay(myTotal.stripTrailingZeros().toPlainString());
                        }
                    }
                    return vo;
                })
                .collect(Collectors.toList());
        Page<PartTimeClassAdvisorRecordVO> voPage = new Page<>(pageNum, pageSize, recordPage.getTotalRow());
        voPage.setRecords(voList);
        return voPage;
    }

    @Override
    public BigDecimal getMyTotalScore(Long userId) {
        List<PartTimeClassAdvisorScore> scores = partTimeAdvisorScoreMapper.selectListByQuery(
                QueryWrapper.create().eq("user_id", userId));
        if (CollUtil.isEmpty(scores)) return BigDecimal.ZERO;
        return scores.stream()
                .map(PartTimeClassAdvisorScore::getScore)
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

        PartTimeClassAdvisorRecord record = this.getById(recordId);
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

        // 审核通过后重新计算该教师得分
        if (statusEnum == ReviewStatusEnum.PASSED) {
            recalculateTeacherScore(record.getTeacherName(), record);
        }
    }

    // ==================== 导出Excel ====================

    @Override
    public byte[] exportRecordsToExcel() {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {

            // ========== 样式 ==========
            CellStyle titleStyle = workbook.createCellStyle();
            Font titleFont = workbook.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 14);
            titleStyle.setFont(titleFont);
            titleStyle.setAlignment(HorizontalAlignment.CENTER);
            titleStyle.setVerticalAlignment(VerticalAlignment.CENTER);

            CellStyle noteStyle = workbook.createCellStyle();
            noteStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            noteStyle.setWrapText(true);
            Font noteFont = workbook.createFont();
            noteFont.setFontHeightInPoints((short) 10);
            noteStyle.setFont(noteFont);

            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setFontHeightInPoints((short) 10);
            headerStyle.setFont(headerFont);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            headerStyle.setBorderTop(BorderStyle.THIN);
            headerStyle.setBorderBottom(BorderStyle.THIN);
            headerStyle.setBorderLeft(BorderStyle.THIN);
            headerStyle.setBorderRight(BorderStyle.THIN);

            CellStyle dataStyle = workbook.createCellStyle();
            dataStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            dataStyle.setAlignment(HorizontalAlignment.CENTER);
            dataStyle.setBorderTop(BorderStyle.THIN);
            dataStyle.setBorderBottom(BorderStyle.THIN);
            dataStyle.setBorderLeft(BorderStyle.THIN);
            dataStyle.setBorderRight(BorderStyle.THIN);

            CellStyle dataStyleLeft = workbook.createCellStyle();
            dataStyleLeft.setVerticalAlignment(VerticalAlignment.CENTER);
            dataStyleLeft.setAlignment(HorizontalAlignment.LEFT);
            dataStyleLeft.setBorderTop(BorderStyle.THIN);
            dataStyleLeft.setBorderBottom(BorderStyle.THIN);
            dataStyleLeft.setBorderLeft(BorderStyle.THIN);
            dataStyleLeft.setBorderRight(BorderStyle.THIN);

            Sheet sheet = workbook.createSheet("9-兼职班主任");

            // ========== 列宽（与示例文件一致） ==========
            sheet.setColumnWidth(0, (int) (15.44 * 256));   // A: 姓名
            sheet.setColumnWidth(1, (int) (11.11 * 256));   // B: 负责班级
            sheet.setColumnWidth(2, (int) (8.44 * 256));    // C: 学风建设-工作要求
            sheet.setColumnWidth(3, (int) (8.11 * 256));    // D: 学风建设-效果评估
            sheet.setColumnWidth(4, (int) (8.66 * 256));    // E: 安全教育-工作要求
            sheet.setColumnWidth(5, (int) (8.33 * 256));    // F: 安全教育-效果评估
            sheet.setColumnWidth(6, (int) (13.00 * 256));   // G: 后进生帮扶-工作要求
            sheet.setColumnWidth(7, (int) (8.44 * 256));    // H: 后进生帮扶-效果评估
            sheet.setColumnWidth(8, (int) (9.00 * 256));    // I: 育人成果-安全稳定
            sheet.setColumnWidth(9, (int) (9.78 * 256));    // J: 育人成果-学风建设
            sheet.setColumnWidth(10, (int) (9.66 * 256));   // K: 育人成果-后进生帮扶
            sheet.setColumnWidth(11, (int) (5.89 * 256));   // L: 总得分
            sheet.setColumnWidth(12, (int) (12.22 * 256));  // M: 换算最终得分
            sheet.setColumnWidth(13, (int) (12.22 * 256));  // N: 合计
            sheet.setColumnWidth(14, (int) (13.00 * 256));  // O: 平均分
            sheet.setColumnWidth(15, (int) (13.00 * 256));  // P: 平均分折合分
            sheet.setColumnWidth(16, (int) (13.00 * 256));  // Q: 行政班分
            sheet.setColumnWidth(17, (int) (12.22 * 256));  // R: 总分

            // ========== 获取所有审核通过的记录 ==========
            List<PartTimeClassAdvisorRecord> allRecords = this.list(
                    QueryWrapper.create().orderBy("teacher_name", true)
                            .orderBy("class_id", true));

            // 按教师姓名分组
            Map<String, List<PartTimeClassAdvisorRecord>> teacherGroups = new LinkedHashMap<>();
            for (PartTimeClassAdvisorRecord r : allRecords) {
                String name = r.getTeacherName();
                teacherGroups.computeIfAbsent(name, k -> new ArrayList<>()).add(r);
            }

            String year = String.valueOf(Year.now().getValue());
            int rowIdx = 0;

            // ========== 第1行：标题 ==========
            Row titleRow = sheet.createRow(rowIdx++);
            titleRow.setHeight((short) (18 * 20));
            Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue(year + "年度信息工程学院兼职班主任考核评分表");
            titleCell.setCellStyle(titleStyle);
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 12)); // A1:M1

            // ========== 第2行：分值转换标准说明 ==========
            Row noteRow = sheet.createRow(rowIdx++);
            noteRow.setHeight((short) (44 * 20));
            Cell noteCell = noteRow.createCell(0);
            noteCell.setCellValue("分值转换标准：0至50━0分；51至60━1分；61至70━2分；71至80━3分，81至90━4分；91至100━5分。每带一个行政班有1分，然后总得分+行政班分得最终分（新生和毕业班需除2）。\n");
            noteCell.setCellStyle(noteStyle);
            sheet.addMergedRegion(new CellRangeAddress(1, 1, 0, 12)); // A2:M2

            // ========== 第3行：一级表头 ==========
            Row headerRow1 = sheet.createRow(rowIdx++);
            headerRow1.setHeight((short) (18 * 20));

            Cell hA3 = headerRow1.createCell(0);
            hA3.setCellValue("姓名");
            hA3.setCellStyle(headerStyle);

            Cell hB3 = headerRow1.createCell(1);
            hB3.setCellValue("负责班级");
            hB3.setCellStyle(headerStyle);

            Cell hC3 = headerRow1.createCell(2);
            hC3.setCellValue("学风建设（30分）");
            hC3.setCellStyle(headerStyle);

            Cell hE3 = headerRow1.createCell(4);
            hE3.setCellValue("安全教育（30分）");
            hE3.setCellStyle(headerStyle);

            Cell hG3 = headerRow1.createCell(6);
            hG3.setCellValue("后进生帮扶（30分）");
            hG3.setCellStyle(headerStyle);

            Cell hI3 = headerRow1.createCell(8);
            hI3.setCellValue("育人成果附加分（10分）");
            hI3.setCellStyle(headerStyle);

            Cell hL3 = headerRow1.createCell(11);
            hL3.setCellValue("总得分");
            hL3.setCellStyle(headerStyle);

            Cell hM3 = headerRow1.createCell(12);
            hM3.setCellValue("换算最终得分");
            hM3.setCellStyle(headerStyle);

            // 合并一级表头
            sheet.addMergedRegion(new CellRangeAddress(2, 3, 0, 0));   // A3:A4 姓名
            sheet.addMergedRegion(new CellRangeAddress(2, 3, 1, 1));   // B3:B4 负责班级
            sheet.addMergedRegion(new CellRangeAddress(2, 2, 2, 3));   // C3:D3 学风建设
            sheet.addMergedRegion(new CellRangeAddress(2, 2, 4, 5));   // E3:F3 安全教育
            sheet.addMergedRegion(new CellRangeAddress(2, 2, 6, 7));   // G3:H3 后进生帮扶
            sheet.addMergedRegion(new CellRangeAddress(2, 2, 8, 10));  // I3:K3 育人成果
            sheet.addMergedRegion(new CellRangeAddress(2, 2, 11, 11)); // L3 总得分
            sheet.addMergedRegion(new CellRangeAddress(2, 2, 12, 12)); // M3 换算最终得分

            // N3-R3 headers
            Cell hN3 = headerRow1.createCell(13);
            hN3.setCellValue("");
            hN3.setCellStyle(headerStyle);
            Cell hO3 = headerRow1.createCell(14);
            hO3.setCellValue("");
            hO3.setCellStyle(headerStyle);
            Cell hP3 = headerRow1.createCell(15);
            hP3.setCellValue("");
            hP3.setCellStyle(headerStyle);
            Cell hQ3 = headerRow1.createCell(16);
            hQ3.setCellValue("");
            hQ3.setCellStyle(headerStyle);
            Cell hR3 = headerRow1.createCell(17);
            hR3.setCellValue("");
            hR3.setCellStyle(headerStyle);

            // ========== 第4行：二级表头 ==========
            Row headerRow2 = sheet.createRow(rowIdx++);
            headerRow2.setHeight((short) (28 * 20));

            // A4 为空（A3:A4合并）
            Cell hA4 = headerRow2.createCell(0);
            hA4.setCellStyle(headerStyle);

            // B4 为空（B3:B4合并）
            Cell hB4 = headerRow2.createCell(1);
            hB4.setCellStyle(headerStyle);

            Cell hC4 = headerRow2.createCell(2);
            hC4.setCellValue("工作要求（20分）");
            hC4.setCellStyle(headerStyle);
            Cell hD4 = headerRow2.createCell(3);
            hD4.setCellValue("效果评估（10分）");
            hD4.setCellStyle(headerStyle);

            Cell hE4 = headerRow2.createCell(4);
            hE4.setCellValue("工作要求（20分）");
            hE4.setCellStyle(headerStyle);
            Cell hF4 = headerRow2.createCell(5);
            hF4.setCellValue("效果评估（10分）");
            hF4.setCellStyle(headerStyle);

            Cell hG4 = headerRow2.createCell(6);
            hG4.setCellValue("工作要求（20分）");
            hG4.setCellStyle(headerStyle);
            Cell hH4 = headerRow2.createCell(7);
            hH4.setCellValue("效果评估（10分）");
            hH4.setCellStyle(headerStyle);

            Cell hI4 = headerRow2.createCell(8);
            hI4.setCellValue("安全稳定（3分）");
            hI4.setCellStyle(headerStyle);
            Cell hJ4 = headerRow2.createCell(9);
            hJ4.setCellValue("学风建设（3分）");
            hJ4.setCellStyle(headerStyle);
            Cell hK4 = headerRow2.createCell(10);
            hK4.setCellValue("后进生帮扶（4分）");
            hK4.setCellStyle(headerStyle);

            Cell hL4 = headerRow2.createCell(11);
            hL4.setCellStyle(headerStyle);
            Cell hM4 = headerRow2.createCell(12);
            hM4.setCellStyle(headerStyle);

            Cell hN4 = headerRow2.createCell(13);
            hN4.setCellStyle(headerStyle);
            Cell hO4 = headerRow2.createCell(14);
            hO4.setCellValue("平均分");
            hO4.setCellStyle(headerStyle);
            Cell hP4 = headerRow2.createCell(15);
            hP4.setCellValue("平均分折合分");
            hP4.setCellStyle(headerStyle);
            Cell hQ4 = headerRow2.createCell(16);
            hQ4.setCellValue("行政班分");
            hQ4.setCellStyle(headerStyle);
            Cell hR4 = headerRow2.createCell(17);
            hR4.setCellValue("总分");
            hR4.setCellStyle(headerStyle);

            // ========== 数据行 ==========
            int dataStartRow = rowIdx; // 记录数据起始行（0-based）

            for (Map.Entry<String, List<PartTimeClassAdvisorRecord>> entry : teacherGroups.entrySet()) {
                List<PartTimeClassAdvisorRecord> records = entry.getValue();
                int groupSize = records.size();

                // 预先计算该组的总分（R）和各统计值
                BigDecimal sumTotalForAvg = BigDecimal.ZERO;
                BigDecimal totalAdminClassScore = BigDecimal.ZERO;
                for (PartTimeClassAdvisorRecord gr : records) {
                    sumTotalForAvg = sumTotalForAvg.add(PartTimeClassAdvisorScoringConstants.calcRowTotal(
                            gr.getStudyStyleWorkReq(), gr.getStudyStyleEffect(),
                            gr.getSafetyEduWorkReq(), gr.getSafetyEduEffect(),
                            gr.getStrugglingStudentWorkReq(), gr.getStrugglingStudentEffect(),
                            gr.getAchievementSafety(), gr.getAchievementStudyStyle(),
                            gr.getAchievementStruggling()));
                    totalAdminClassScore = totalAdminClassScore.add(
                            gr.getAdminClassScore() != null ? gr.getAdminClassScore() : BigDecimal.ZERO);
                }
                BigDecimal avgForConvert = sumTotalForAvg.divide(
                        new BigDecimal(groupSize), 2, RoundingMode.HALF_UP);
                BigDecimal avgConvScore = PartTimeClassAdvisorScoringConstants.convertScore(avgForConvert);
                BigDecimal finalScore = avgConvScore.add(totalAdminClassScore);

                int groupStartRow = rowIdx; // 该组第一行（0-based）

                for (int i = 0; i < groupSize; i++) {
                    PartTimeClassAdvisorRecord r = records.get(i);
                    Row row = sheet.createRow(rowIdx);
                    row.setHeight((short) (22 * 20));

                    // A: 姓名
                    Cell cellA = row.createCell(0);
                    cellA.setCellValue(r.getTeacherName() != null ? r.getTeacherName() : "");
                    cellA.setCellStyle(dataStyle);

                    // B: 负责班级
                    Cell cellB = row.createCell(1);
                    cellB.setCellValue(r.getClassId() != null ? r.getClassId() : "");
                    cellB.setCellStyle(dataStyle);

                    // C-K: 各项得分
                    Cell cellC = row.createCell(2);
                    cellC.setCellValue(toDouble(r.getStudyStyleWorkReq()));
                    cellC.setCellStyle(dataStyle);

                    Cell cellD = row.createCell(3);
                    cellD.setCellValue(toDouble(r.getStudyStyleEffect()));
                    cellD.setCellStyle(dataStyle);

                    Cell cellE = row.createCell(4);
                    cellE.setCellValue(toDouble(r.getSafetyEduWorkReq()));
                    cellE.setCellStyle(dataStyle);

                    Cell cellF = row.createCell(5);
                    cellF.setCellValue(toDouble(r.getSafetyEduEffect()));
                    cellF.setCellStyle(dataStyle);

                    Cell cellG = row.createCell(6);
                    cellG.setCellValue(toDouble(r.getStrugglingStudentWorkReq()));
                    cellG.setCellStyle(dataStyle);

                    Cell cellH = row.createCell(7);
                    cellH.setCellValue(toDouble(r.getStrugglingStudentEffect()));
                    cellH.setCellStyle(dataStyle);

                    Cell cellI = row.createCell(8);
                    cellI.setCellValue(toDouble(r.getAchievementSafety()));
                    cellI.setCellStyle(dataStyle);

                    Cell cellJ = row.createCell(9);
                    cellJ.setCellValue(toDouble(r.getAchievementStudyStyle()));
                    cellJ.setCellStyle(dataStyle);

                    Cell cellK = row.createCell(10);
                    cellK.setCellValue(toDouble(r.getAchievementStruggling()));
                    cellK.setCellStyle(dataStyle);

                    // L: 总得分 = SUM(C:K)
                    BigDecimal rowTotal = PartTimeClassAdvisorScoringConstants.calcRowTotal(
                            r.getStudyStyleWorkReq(), r.getStudyStyleEffect(),
                            r.getSafetyEduWorkReq(), r.getSafetyEduEffect(),
                            r.getStrugglingStudentWorkReq(), r.getStrugglingStudentEffect(),
                            r.getAchievementSafety(), r.getAchievementStudyStyle(),
                            r.getAchievementStruggling());
                    Cell cellL = row.createCell(11);
                    cellL.setCellValue(rowTotal.doubleValue());
                    cellL.setCellStyle(dataStyle);

                    // M: 换算最终得分 = 总分R（仅第一行显示，且合并同姓名的M单元格）
                    Cell cellM = row.createCell(12);
                    if (i == 0) {
                        cellM.setCellValue(finalScore.doubleValue());
                    }
                    cellM.setCellStyle(dataStyle);

                    // N: 合计（仅最后一行显示）
                    Cell cellN = row.createCell(13);
                    Cell cellO = row.createCell(14);
                    Cell cellP = row.createCell(15);

                    if (i == groupSize - 1) {
                        cellN.setCellValue(sumTotalForAvg.doubleValue());

                        // O: 平均分 = N / 班级数
                        cellO.setCellValue(avgForConvert.doubleValue());

                        // P: 平均分折合分
                        cellP.setCellValue(avgConvScore.doubleValue());
                    }
                    cellN.setCellStyle(dataStyle);
                    cellO.setCellStyle(dataStyle);
                    cellP.setCellStyle(dataStyle);

                    // Q: 行政班分
                    Cell cellQ = row.createCell(16);
                    cellQ.setCellValue(toDouble(r.getAdminClassScore()));
                    cellQ.setCellStyle(dataStyle);

                    // R: 总分（仅第一行显示）= 平均分折合分 + 所有行政班分之和
                    Cell cellR = row.createCell(17);
                    if (i == 0) {
                        cellR.setCellValue(finalScore.doubleValue());
                    }
                    cellR.setCellStyle(dataStyle);

                    rowIdx++;
                }

                int groupEndRow = rowIdx - 1; // 该组最后一行（0-based）

                // 如果该教师有多行，合并M列单元格（换算最终得分）
                if (groupSize > 1) {
                    sheet.addMergedRegion(new CellRangeAddress(groupStartRow, groupEndRow, 12, 12));
                }
            }

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            workbook.write(bos);
            return bos.toByteArray();
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "Excel生成失败: " + e.getMessage());
        }
    }

    private double toDouble(BigDecimal v) {
        return v != null ? v.doubleValue() : 0;
    }
}
