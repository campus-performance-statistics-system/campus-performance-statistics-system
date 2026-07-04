package com.jgh.ghairouter.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.jgh.ghairouter.exception.BusinessException;
import com.jgh.ghairouter.exception.ErrorCode;
import com.jgh.ghairouter.mapper.OnlineEvaluationRecordMapper;
import com.jgh.ghairouter.mapper.TeacherCompetitionAuditRecordMapper;
import com.jgh.ghairouter.mapper.UserMapper;
import com.jgh.ghairouter.model.constants.OnlineEvaluationScoringConstants;
import com.jgh.ghairouter.model.dto.competition.OnlineEvaluationQueryRequest;
import com.jgh.ghairouter.model.entity.OnlineEvaluationRecord;
import com.jgh.ghairouter.model.entity.TeacherCompetitionAuditRecord;
import com.jgh.ghairouter.model.entity.User;
import com.jgh.ghairouter.model.enums.ReviewStatusEnum;
import com.jgh.ghairouter.model.vo.OnlineEvaluationRecordVO;
import com.jgh.ghairouter.service.OnlineEvaluationRecordService;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 网上评教记录服务实现（v12）。
 * 纯数据记录，不涉及计分。
 */
@Slf4j
@Service
public class OnlineEvaluationRecordServiceImpl
        extends ServiceImpl<OnlineEvaluationRecordMapper, OnlineEvaluationRecord>
        implements OnlineEvaluationRecordService {

    @Resource
    private UserMapper userMapper;
    @Resource
    private TeacherCompetitionAuditRecordMapper auditMapper;

    private static final String DEFAULT_TYPE_NAME = OnlineEvaluationScoringConstants.TYPE_NAME;

    // ==================== 删除（代码层面软删除级联） ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removeById(Serializable id) {
        // 1. 软删除审核记录
        auditMapper.deleteByQuery(
                QueryWrapper.create().eq("record_id", id).eq("record_type", DEFAULT_TYPE_NAME));
        // 2. 软删除主表记录
        return super.removeById(id);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public Long addRecord(Long userId,
                          String teacherName,
                          String teacherType,
                          String academicYear,
                          String semester,
                          String courseCode,
                          String courseName,
                          Integer participantCount,
                          BigDecimal averageScore,
                          MultipartFile file) {

        String base64 = null;
        if (file != null && !file.isEmpty()) {
            try {
                base64 = Base64.getEncoder().encodeToString(file.getBytes());
            } catch (IOException e) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "读取文件失败");
            }
        }

        OnlineEvaluationRecord record = new OnlineEvaluationRecord();
        record.setUserId(userId);
        record.setTypeName(DEFAULT_TYPE_NAME);
        record.setTeacherName(teacherName);
        record.setTeacherType(teacherType != null ? teacherType : OnlineEvaluationScoringConstants.TEACHER_TYPE_FULLTIME);
        record.setAcademicYear(academicYear);
        record.setSemester(semester);
        record.setCourseCode(courseCode);
        record.setCourseName(courseName);
        record.setParticipantCount(participantCount != null ? participantCount : 0);
        record.setAverageScore(averageScore != null ? averageScore : BigDecimal.ZERO);
        record.setProofImageData(base64);

        boolean saved = this.save(record);
        if (!saved) throw new BusinessException(ErrorCode.OPERATION_ERROR, "提交失败");

        // 创建审核记录
        TeacherCompetitionAuditRecord audit = new TeacherCompetitionAuditRecord();
        audit.setRecordId(record.getId());
        audit.setRecordType(DEFAULT_TYPE_NAME);
        audit.setAutoReviewStatus(ReviewStatusEnum.PENDING.getValue());
        audit.setAdminReviewStatus(ReviewStatusEnum.PENDING.getValue());
        LocalDateTime now = LocalDateTime.now();
        audit.setCreateTime(now);
        audit.setUpdateTime(now);
        auditMapper.insert(audit);

        return record.getId();
    }

    @Override
    public QueryWrapper getQueryWrapper(OnlineEvaluationQueryRequest req) {
        if (req == null) throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        QueryWrapper wrapper = QueryWrapper.create()
                .eq("id", req.getId())
                .eq("user_id", req.getUserId())
                .like("teacher_name", req.getTeacherName())
                .eq("teacher_type", req.getTeacherType())
                .eq("academic_year", req.getAcademicYear())
                .eq("semester", req.getSemester())
                .like("course_code", req.getCourseCode())
                .like("course_name", req.getCourseName());
        if (StrUtil.isNotBlank(req.getSortField())) {
            wrapper.orderBy(req.getSortField(), "ascend".equals(req.getSortOrder()));
        }
        wrapper.orderBy("create_time", false);
        return wrapper;
    }

    @Override
    public OnlineEvaluationRecordVO getRecordVO(OnlineEvaluationRecord record) {
        if (record == null) return null;
        OnlineEvaluationRecordVO vo = new OnlineEvaluationRecordVO();
        BeanUtil.copyProperties(record, vo);
        // 网上评教的"我的得分"即为该记录的平均分
        vo.setMyScoreDisplay(record.getAverageScore());

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

        return vo;
    }

    @Override
    public Page<OnlineEvaluationRecordVO> pageRecords(OnlineEvaluationQueryRequest req) {
        long pageNum = req.getPageNum();
        long pageSize = req.getPageSize();

        QueryWrapper wrapper = getQueryWrapper(req);
        if (StrUtil.isNotBlank(req.getAdminReviewStatus())) {
            Page<OnlineEvaluationRecord> recordPage = this.page(Page.of(pageNum, pageSize), wrapper);
            List<OnlineEvaluationRecordVO> voList = recordPage.getRecords().stream()
                    .map(this::getRecordVO)
                    .filter(vo -> req.getAdminReviewStatus().equals(vo.getAdminReviewStatus()))
                    .collect(Collectors.toList());
            Page<OnlineEvaluationRecordVO> voPage = new Page<>(pageNum, pageSize, recordPage.getTotalRow());
            voPage.setRecords(voList);
            return voPage;
        }
        Page<OnlineEvaluationRecord> recordPage = this.page(Page.of(pageNum, pageSize), wrapper);
        List<OnlineEvaluationRecordVO> voList = recordPage.getRecords().stream()
                .map(this::getRecordVO)
                .collect(Collectors.toList());
        Page<OnlineEvaluationRecordVO> voPage = new Page<>(pageNum, pageSize, recordPage.getTotalRow());
        voPage.setRecords(voList);
        return voPage;
    }

    @Override
    public Page<OnlineEvaluationRecordVO> pageMyRelatedRecords(Long userId, OnlineEvaluationQueryRequest req) {
        long pageNum = req.getPageNum();
        long pageSize = req.getPageSize();

        // 提交人 OR 被评教的教师本人，均可看到记录
        QueryWrapper wrapper = QueryWrapper.create()
                .eq("id", req.getId())
                .like("teacher_name", req.getTeacherName())
                .eq("teacher_type", req.getTeacherType())
                .eq("academic_year", req.getAcademicYear())
                .eq("semester", req.getSemester())
                .like("course_code", req.getCourseCode())
                .like("course_name", req.getCourseName())
                .where("(user_id = ? OR teacher_name = (SELECT user_name FROM user WHERE id = ? AND is_delete = 0))",
                       userId, userId);
        wrapper.orderBy("create_time", false);

        Page<OnlineEvaluationRecord> recordPage = this.page(Page.of(pageNum, pageSize), wrapper);
        List<OnlineEvaluationRecordVO> voList = recordPage.getRecords().stream()
                .map(this::getRecordVO)
                .collect(Collectors.toList());
        Page<OnlineEvaluationRecordVO> voPage = new Page<>(pageNum, pageSize, recordPage.getTotalRow());
        voPage.setRecords(voList);
        return voPage;
    }

    // ==================== 总得分 ====================

    @Override
    public BigDecimal getMyTotalScore(Long userId) {
        // 计算用户的加权平均分：SUM(averageScore * participantCount) / SUM(participantCount)
        List<OnlineEvaluationRecord> records = this.list(
                QueryWrapper.create().eq("user_id", userId));
        if (records.isEmpty()) {
            return BigDecimal.ZERO;
        }
        BigDecimal totalWeightedScore = BigDecimal.ZERO;
        int totalParticipants = 0;
        for (OnlineEvaluationRecord r : records) {
            BigDecimal score = r.getAverageScore() != null ? r.getAverageScore() : BigDecimal.ZERO;
            int count = r.getParticipantCount() != null ? r.getParticipantCount() : 0;
            totalWeightedScore = totalWeightedScore.add(score.multiply(BigDecimal.valueOf(count)));
            totalParticipants += count;
        }
        if (totalParticipants > 0) {
            return totalWeightedScore.divide(BigDecimal.valueOf(totalParticipants), 2, RoundingMode.HALF_UP);
        }
        return BigDecimal.ZERO;
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

        OnlineEvaluationRecord record = this.getById(recordId);
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
    }

    // ==================== 导出Excel ====================

    @Override
    public byte[] exportRecordsToExcel() {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {

            // 标题样式：宋体、加粗、16号、居中、thin边框
            CellStyle titleStyle = createTitleStyle(workbook);

            // 表头样式：宋体、14号、居中、thin边框
            CellStyle headerStyle = createHeaderStyle(workbook);

            // 数据样式：宋体、14号、居中、thin边框
            CellStyle dataStyle = createDataStyle(workbook);

            // 小数数据样式：在数据样式基础上增加两位小数格式
            CellStyle decimalStyle = workbook.createCellStyle();
            decimalStyle.cloneStyleFrom(dataStyle);
            decimalStyle.setDataFormat(workbook.createDataFormat().getFormat("0.00"));

            // 注意样式：宋体、14号、left对齐
            CellStyle noteStyle = workbook.createCellStyle();
            Font noteFont = workbook.createFont();
            noteFont.setFontName("宋体");
            noteFont.setFontHeightInPoints((short) 14);
            noteStyle.setFont(noteFont);
            noteStyle.setAlignment(HorizontalAlignment.LEFT);
            noteStyle.setVerticalAlignment(VerticalAlignment.CENTER);

            // 日期/部门样式：宋体、14号、居中
            CellStyle footerStyle = workbook.createCellStyle();
            Font footerFont = workbook.createFont();
            footerFont.setFontName("宋体");
            footerFont.setFontHeightInPoints((short) 14);
            footerStyle.setFont(footerFont);
            footerStyle.setAlignment(HorizontalAlignment.CENTER);
            footerStyle.setVerticalAlignment(VerticalAlignment.CENTER);

            // 获取所有记录，按学年学期和教师类型分组
            List<OnlineEvaluationRecord> allRecords = this.list(
                    QueryWrapper.create().orderBy("academic_year", false)
                            .orderBy("semester", true));

            if (allRecords.isEmpty()) {
                // 无数据时也创建一个空Sheet
                Sheet sheet = workbook.createSheet("11-网上评教");
                return workbookToBytes(workbook);
            }

            // 按学年学期分组
            Map<String, List<OnlineEvaluationRecord>> groupedBySemester = new LinkedHashMap<>();
            for (OnlineEvaluationRecord r : allRecords) {
                String key = (r.getAcademicYear() != null ? r.getAcademicYear() : "")
                        + "_" + (r.getSemester() != null ? r.getSemester() : "");
                groupedBySemester.computeIfAbsent(key, k -> new ArrayList<>()).add(r);
            }

            // 为每个学年学期创建独立的Sheet导出（如果只有一个学期就用一个Sheet）
            // 实际上根据示例，一个Sheet包含一个学年学期的数据，分为专任和外聘两个部分
            Sheet sheet = workbook.createSheet("11-网上评教");

            int rowIdx = 0;

            // 对每个学年学期分组进行处理
            for (Map.Entry<String, List<OnlineEvaluationRecord>> entry : groupedBySemester.entrySet()) {
                List<OnlineEvaluationRecord> records = entry.getValue();
                if (records.isEmpty()) continue;

                // 获取学年学期信息
                String academicYear = records.get(0).getAcademicYear();
                String semester = records.get(0).getSemester();

                // 按教师类型分组
                Map<String, List<OnlineEvaluationRecord>> byTeacherType = new LinkedHashMap<>();
                byTeacherType.put(OnlineEvaluationScoringConstants.TEACHER_TYPE_FULLTIME, new ArrayList<>());
                byTeacherType.put(OnlineEvaluationScoringConstants.TEACHER_TYPE_EXTERNAL, new ArrayList<>());
                for (OnlineEvaluationRecord r : records) {
                    String tt = r.getTeacherType();
                    if (OnlineEvaluationScoringConstants.TEACHER_TYPE_EXTERNAL.equals(tt)) {
                        byTeacherType.get(OnlineEvaluationScoringConstants.TEACHER_TYPE_EXTERNAL).add(r);
                    } else {
                        byTeacherType.get(OnlineEvaluationScoringConstants.TEACHER_TYPE_FULLTIME).add(r);
                    }
                }

                // 先处理专任教师，再处理外聘教师
                String[] teacherTypes = {
                        OnlineEvaluationScoringConstants.TEACHER_TYPE_FULLTIME,
                        OnlineEvaluationScoringConstants.TEACHER_TYPE_EXTERNAL
                };
                String[] typeLabels = {"专任教师", "外聘教师"};

                for (int ti = 0; ti < teacherTypes.length; ti++) {
                    List<OnlineEvaluationRecord> typeRecords = byTeacherType.get(teacherTypes[ti]);
                    if (typeRecords.isEmpty()) continue;

                    // 按教师姓名分组
                    Map<String, List<OnlineEvaluationRecord>> byTeacher = new LinkedHashMap<>();
                    for (OnlineEvaluationRecord r : typeRecords) {
                        byTeacher.computeIfAbsent(r.getTeacherName(), k -> new ArrayList<>()).add(r);
                    }

                    // 计算每位教师的个人总平均分（加权平均）
                    // 个人总平均分 = SUM(平均分 * 参评人数) / SUM(参评人数)
                    Map<String, BigDecimal> teacherWeightedAvg = new LinkedHashMap<>();
                    for (Map.Entry<String, List<OnlineEvaluationRecord>> te : byTeacher.entrySet()) {
                        BigDecimal totalWeightedScore = BigDecimal.ZERO;
                        int totalParticipants = 0;
                        for (OnlineEvaluationRecord r : te.getValue()) {
                            BigDecimal score = r.getAverageScore() != null ? r.getAverageScore() : BigDecimal.ZERO;
                            int count = r.getParticipantCount() != null ? r.getParticipantCount() : 0;
                            totalWeightedScore = totalWeightedScore.add(score.multiply(BigDecimal.valueOf(count)));
                            totalParticipants += count;
                        }
                        if (totalParticipants > 0) {
                            BigDecimal weightedAvg = totalWeightedScore.divide(
                                    BigDecimal.valueOf(totalParticipants), 2, RoundingMode.HALF_UP);
                            teacherWeightedAvg.put(te.getKey(), weightedAvg);
                        } else {
                            teacherWeightedAvg.put(te.getKey(), BigDecimal.ZERO);
                        }
                    }

                    // 按个人总平均分降序排列
                    List<Map.Entry<String, List<OnlineEvaluationRecord>>> sortedTeachers = new ArrayList<>(byTeacher.entrySet());
                    sortedTeachers.sort((a, b) -> {
                        BigDecimal avgB = teacherWeightedAvg.getOrDefault(b.getKey(), BigDecimal.ZERO);
                        BigDecimal avgA = teacherWeightedAvg.getOrDefault(a.getKey(), BigDecimal.ZERO);
                        return avgB.compareTo(avgA);
                    });

                    // 在部分之间留空行
                    if (rowIdx > 0) {
                        // 空5行
                        for (int i = 0; i < 5; i++) {
                            sheet.createRow(rowIdx++);
                        }
                    }

                    // 标题行
                    String titleStr = (academicYear != null ? academicYear : "____")
                            + (semester != null ? semester : "第__学期")
                            + "信息工程学院" + typeLabels[ti] + "学生网上评教结果";
                    Row titleRow = sheet.createRow(rowIdx++);
                    titleRow.setHeight((short) (20.4 * 20));
                    Cell titleCell = titleRow.createCell(0);
                    titleCell.setCellValue(titleStr);
                    titleCell.setCellStyle(titleStyle);
                    sheet.addMergedRegion(new CellRangeAddress(rowIdx - 1, rowIdx - 1, 0, 6));

                    // 表头行
                    Row headerRow = sheet.createRow(rowIdx++);
                    headerRow.setHeight((short) (17.4 * 20));
                    String[] headers = {"排名", "姓名", "课程序号", "课程名称", "参评人数", "平均分", "个人总平均分"};
                    for (int i = 0; i < headers.length; i++) {
                        Cell cell = headerRow.createCell(i);
                        cell.setCellValue(headers[i]);
                        cell.setCellStyle(headerStyle);
                    }

                    // 数据行
                    int rank = 1;
                    for (Map.Entry<String, List<OnlineEvaluationRecord>> te : sortedTeachers) {
                        String teacherName = te.getKey();
                        List<OnlineEvaluationRecord> teacherRecords = te.getValue();
                        BigDecimal weightedAvg = teacherWeightedAvg.get(teacherName);

                        // 按课程序号排序
                        teacherRecords.sort(Comparator.comparing(
                                r -> r.getCourseCode() != null ? r.getCourseCode() : ""));

                        boolean firstRow = true;
                        for (OnlineEvaluationRecord r : teacherRecords) {
                            Row dataRow = sheet.createRow(rowIdx++);
                            dataRow.setHeight((short) (17.4 * 20));

                            // 排名字段（仅第一行）
                            Cell rankCell = dataRow.createCell(0);
                            if (firstRow) {
                                rankCell.setCellValue(rank++);
                            }
                            rankCell.setCellStyle(dataStyle);

                            // 姓名（仅第一行）
                            Cell nameCell = dataRow.createCell(1);
                            if (firstRow) {
                                nameCell.setCellValue(teacherName);
                            }
                            nameCell.setCellStyle(dataStyle);

                            // 课程序号
                            Cell codeCell = dataRow.createCell(2);
                            codeCell.setCellValue(r.getCourseCode() != null ? r.getCourseCode() : "");
                            codeCell.setCellStyle(dataStyle);

                            // 课程名称
                            Cell courseCell = dataRow.createCell(3);
                            courseCell.setCellValue(r.getCourseName() != null ? r.getCourseName() : "");
                            courseCell.setCellStyle(dataStyle);

                            // 参评人数
                            Cell participantsCell = dataRow.createCell(4);
                            participantsCell.setCellValue(r.getParticipantCount() != null ? r.getParticipantCount() : 0);
                            participantsCell.setCellStyle(dataStyle);

                            // 平均分
                            Cell avgCell = dataRow.createCell(5);
                            avgCell.setCellValue(r.getAverageScore() != null ? r.getAverageScore().doubleValue() : 0);
                            avgCell.setCellStyle(dataStyle);

                            // 个人总平均分（仅第一行，使用公式，保留两位小数）
                            Cell totalAvgCell = dataRow.createCell(6);
                            if (firstRow && teacherRecords.size() > 1) {
                                // 使用Excel公式计算加权平均，ROUND 保留两位小数
                                // rowIdx 在 createRow(rowIdx++) 后已指向下一行，
                                // 当前教师的第 j 条记录在第 (rowIdx + j) 个 Excel 行号
                                StringBuilder formula = new StringBuilder("ROUND((");
                                for (int j = 0; j < teacherRecords.size(); j++) {
                                    if (j > 0) formula.append("+");
                                    int rowNum = rowIdx + j;
                                    formula.append("F").append(rowNum).append("*E").append(rowNum);
                                }
                                formula.append(")/(");
                                for (int j = 0; j < teacherRecords.size(); j++) {
                                    if (j > 0) formula.append("+");
                                    int rowNum = rowIdx + j;
                                    formula.append("E").append(rowNum);
                                }
                                formula.append("),2)");
                                totalAvgCell.setCellFormula(formula.toString());
                            } else if (firstRow) {
                                totalAvgCell.setCellValue(weightedAvg.doubleValue());
                            }
                            totalAvgCell.setCellStyle(decimalStyle);

                            firstRow = false;
                        }
                    }

                    // 在专任教师部分末尾添加注释
                    if (ti == 0) {
                        // 空一行
                        rowIdx++;
                        Row noteRow = sheet.createRow(rowIdx++);
                        noteRow.setHeight((short) (17.4 * 20));
                        Cell noteCell = noteRow.createCell(0);
                        noteCell.setCellValue("注：学生评教人数不足15人的不作为有效评教课号。");
                        noteCell.setCellStyle(noteStyle);
                    }

                    // 添加部门/日期信息
                    if (ti == teacherTypes.length - 1) {
                        // 空一行
                        rowIdx++;
                        // 教务科技处
                        Row deptRow = sheet.createRow(rowIdx++);
                        deptRow.setHeight((short) (17.4 * 20));
                        Cell deptCell = deptRow.createCell(4);
                        deptCell.setCellValue("教务科技处");
                        deptCell.setCellStyle(footerStyle);

                        // 日期
                        Row dateRow = sheet.createRow(rowIdx++);
                        dateRow.setHeight((short) (17.4 * 20));
                        Cell dateCell = dateRow.createCell(5);
                        // 提取学年结束年份和月份（如2023.9）
                        if (academicYear != null && academicYear.contains("-")) {
                            String endYear = academicYear.split("-")[1];
                            dateCell.setCellValue(endYear + ".9");
                        } else {
                            dateCell.setCellValue("");
                        }
                        dateCell.setCellStyle(footerStyle);
                    }
                }

                // 只处理第一个学年学期（每个Sheet一个学期，多个学期可以分Sheet）
                // 实际上根据需求，所有数据放在同一个Sheet中
                break;
            }

            // 设置列宽（与示例文件一致）
            sheet.setColumnWidth(0, (int) (6.5 * 256));
            sheet.setColumnWidth(1, (int) (11.22 * 256));
            sheet.setColumnWidth(2, (int) (17.5 * 256));
            sheet.setColumnWidth(3, (int) (35 * 256));
            sheet.setColumnWidth(4, (int) (10.5 * 256));
            sheet.setColumnWidth(5, (int) (10.5 * 256));
            sheet.setColumnWidth(6, (int) (15.5 * 256));

            return workbookToBytes(workbook);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "Excel生成失败: " + e.getMessage());
        }
    }

    private CellStyle createTitleStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setFontName("宋体");
        font.setBold(true);
        font.setFontHeightInPoints((short) 16);
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setWrapText(true);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private CellStyle createHeaderStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setFontName("宋体");
        font.setFontHeightInPoints((short) 14);
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private CellStyle createDataStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setFontName("宋体");
        font.setFontHeightInPoints((short) 14);
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private byte[] workbookToBytes(XSSFWorkbook workbook) {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            workbook.write(bos);
            return bos.toByteArray();
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "Excel生成失败: " + e.getMessage());
        }
    }
}
