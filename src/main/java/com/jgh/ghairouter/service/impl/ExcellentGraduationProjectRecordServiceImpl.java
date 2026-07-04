package com.jgh.ghairouter.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.jgh.ghairouter.exception.BusinessException;
import com.jgh.ghairouter.exception.ErrorCode;
import com.jgh.ghairouter.mapper.ExcellentGraduationProjectRecordMapper;
import com.jgh.ghairouter.mapper.TeacherCompetitionAuditRecordMapper;
import com.jgh.ghairouter.mapper.UserMapper;
import com.jgh.ghairouter.model.constants.ExcellentGraduationProjectScoringConstants;
import com.jgh.ghairouter.model.dto.competition.ExcellentGraduationProjectQueryRequest;
import com.jgh.ghairouter.model.entity.ExcellentGraduationProjectRecord;
import com.jgh.ghairouter.model.entity.TeacherCompetitionAuditRecord;
import com.jgh.ghairouter.model.entity.User;
import com.jgh.ghairouter.model.enums.ReviewStatusEnum;
import com.jgh.ghairouter.model.vo.ExcellentGraduationProjectRecordVO;
import com.jgh.ghairouter.service.ExcellentGraduationProjectRecordService;
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
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 优秀毕设记录服务实现（v16）。
 * 纯数据记录，不涉及计分。
 */
@Slf4j
@Service
public class ExcellentGraduationProjectRecordServiceImpl
        extends ServiceImpl<ExcellentGraduationProjectRecordMapper, ExcellentGraduationProjectRecord>
        implements ExcellentGraduationProjectRecordService {

    @Resource
    private UserMapper userMapper;
    @Resource
    private TeacherCompetitionAuditRecordMapper auditMapper;

    private static final String DEFAULT_TYPE_NAME = ExcellentGraduationProjectScoringConstants.TYPE_NAME;

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
                          String major,
                          String studentId,
                          String studentName,
                          String projectTitle,
                          String advisorName,
                          Integer rank,
                          MultipartFile file) {

        String base64 = null;
        if (file != null && !file.isEmpty()) {
            try {
                base64 = Base64.getEncoder().encodeToString(file.getBytes());
            } catch (IOException e) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "读取文件失败");
            }
        }

        ExcellentGraduationProjectRecord record = new ExcellentGraduationProjectRecord();
        record.setUserId(userId);
        record.setTypeName(DEFAULT_TYPE_NAME);
        record.setMajor(major);
        record.setStudentId(studentId);
        record.setStudentName(studentName);
        record.setProjectTitle(projectTitle);
        record.setAdvisorName(advisorName);
        record.setRank(rank);
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
    public QueryWrapper getQueryWrapper(ExcellentGraduationProjectQueryRequest req) {
        if (req == null) throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        QueryWrapper wrapper = QueryWrapper.create()
                .eq("id", req.getId())
                .eq("user_id", req.getUserId())
                .like("major", req.getMajor())
                .like("student_name", req.getStudentName())
                .like("advisor_name", req.getAdvisorName());
        if (StrUtil.isNotBlank(req.getSortField())) {
            wrapper.orderBy(req.getSortField(), "ascend".equals(req.getSortOrder()));
        }
        wrapper.orderBy("create_time", false);
        return wrapper;
    }

    @Override
    public ExcellentGraduationProjectRecordVO getRecordVO(ExcellentGraduationProjectRecord record) {
        if (record == null) return null;
        ExcellentGraduationProjectRecordVO vo = new ExcellentGraduationProjectRecordVO();
        BeanUtil.copyProperties(record, vo);

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
    public Page<ExcellentGraduationProjectRecordVO> pageRecords(ExcellentGraduationProjectQueryRequest req) {
        long pageNum = req.getPageNum();
        long pageSize = req.getPageSize();

        QueryWrapper wrapper = getQueryWrapper(req);
        if (StrUtil.isNotBlank(req.getAdminReviewStatus())) {
            Page<ExcellentGraduationProjectRecord> recordPage = this.page(Page.of(pageNum, pageSize), wrapper);
            List<ExcellentGraduationProjectRecordVO> voList = recordPage.getRecords().stream()
                    .map(this::getRecordVO)
                    .filter(vo -> req.getAdminReviewStatus().equals(vo.getAdminReviewStatus()))
                    .collect(Collectors.toList());
            Page<ExcellentGraduationProjectRecordVO> voPage = new Page<>(pageNum, pageSize, recordPage.getTotalRow());
            voPage.setRecords(voList);
            return voPage;
        }
        Page<ExcellentGraduationProjectRecord> recordPage = this.page(Page.of(pageNum, pageSize), wrapper);
        List<ExcellentGraduationProjectRecordVO> voList = recordPage.getRecords().stream()
                .map(this::getRecordVO)
                .collect(Collectors.toList());
        Page<ExcellentGraduationProjectRecordVO> voPage = new Page<>(pageNum, pageSize, recordPage.getTotalRow());
        voPage.setRecords(voList);
        return voPage;
    }

    @Override
    public Page<ExcellentGraduationProjectRecordVO> pageMyRelatedRecords(Long userId, ExcellentGraduationProjectQueryRequest req) {
        long pageNum = req.getPageNum();
        long pageSize = req.getPageSize();

        QueryWrapper wrapper = QueryWrapper.create()
                .eq("id", req.getId())
                .like("major", req.getMajor())
                .like("student_name", req.getStudentName())
                .like("advisor_name", req.getAdvisorName())
                .where("user_id = ?", userId);
        wrapper.orderBy("create_time", false);

        Page<ExcellentGraduationProjectRecord> recordPage = this.page(Page.of(pageNum, pageSize), wrapper);
        List<ExcellentGraduationProjectRecordVO> voList = recordPage.getRecords().stream()
                .map(this::getRecordVO)
                .collect(Collectors.toList());
        Page<ExcellentGraduationProjectRecordVO> voPage = new Page<>(pageNum, pageSize, recordPage.getTotalRow());
        voPage.setRecords(voList);
        return voPage;
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

        ExcellentGraduationProjectRecord record = this.getById(recordId);
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
            CellStyle titleStyle = workbook.createCellStyle();
            Font titleFont = workbook.createFont();
            titleFont.setFontName("宋体");
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 16);
            titleStyle.setFont(titleFont);
            titleStyle.setAlignment(HorizontalAlignment.CENTER);
            titleStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            titleStyle.setWrapText(true);
            titleStyle.setBorderTop(BorderStyle.THIN);
            titleStyle.setBorderBottom(BorderStyle.THIN);
            titleStyle.setBorderLeft(BorderStyle.THIN);
            titleStyle.setBorderRight(BorderStyle.THIN);

            // 表头样式：宋体、14号、居中、thin边框
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setFontName("宋体");
            headerFont.setFontHeightInPoints((short) 14);
            headerStyle.setFont(headerFont);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            headerStyle.setBorderTop(BorderStyle.THIN);
            headerStyle.setBorderBottom(BorderStyle.THIN);
            headerStyle.setBorderLeft(BorderStyle.THIN);
            headerStyle.setBorderRight(BorderStyle.THIN);

            // 数据样式：宋体、14号、居中、thin边框
            CellStyle dataStyle = workbook.createCellStyle();
            Font dataFont = workbook.createFont();
            dataFont.setFontName("宋体");
            dataFont.setFontHeightInPoints((short) 14);
            dataStyle.setFont(dataFont);
            dataStyle.setAlignment(HorizontalAlignment.CENTER);
            dataStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            dataStyle.setBorderTop(BorderStyle.THIN);
            dataStyle.setBorderBottom(BorderStyle.THIN);
            dataStyle.setBorderLeft(BorderStyle.THIN);
            dataStyle.setBorderRight(BorderStyle.THIN);

            Sheet sheet = workbook.createSheet("15-优秀毕设");

            // 列宽（与示例Excel一致）
            sheet.setColumnWidth(0, (int) (8.22 * 256));   // A: 序号
            sheet.setColumnWidth(1, (int) (25.00 * 256));  // B: 专业
            sheet.setColumnWidth(2, (int) (18.00 * 256));  // C: 学号
            sheet.setColumnWidth(3, (int) (16.00 * 256));  // D: 姓名
            sheet.setColumnWidth(4, (int) (55.00 * 256));  // E: 毕设题目
            sheet.setColumnWidth(5, (int) (22.00 * 256));  // F: 指导教师
            sheet.setColumnWidth(6, (int) (10.00 * 256));  // G: 名次

            // 获取所有记录，按名次、创建时间排序（容错：表不存在时返回空列表）
            List<ExcellentGraduationProjectRecord> allRecords;
            try {
                allRecords = this.list(
                        QueryWrapper.create().orderBy("rank", true)
                                .orderBy("create_time", true));
            } catch (Exception e) {
                log.warn("查询优秀毕设记录失败（可能表不存在），将导出空表: {}", e.getMessage());
                allRecords = java.util.Collections.emptyList();
            }

            // 根据数据库中第一条记录的创建时间获取年份，若无记录则使用当前年份
            int year = java.time.Year.now().getValue();
            if (!allRecords.isEmpty()) {
                LocalDateTime firstCreateTime = allRecords.get(0).getCreateTime();
                if (firstCreateTime != null) {
                    year = firstCreateTime.getYear();
                }
            }

            int rowIdx = 0;

            // 第1行：标题（合并A1:G1）
            Row titleRow = sheet.createRow(rowIdx++);
            titleRow.setHeight((short) (30 * 20));
            Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue(year + "届毕业设计（论文）校优名次表");
            titleCell.setCellStyle(titleStyle);
            for (int i = 1; i <= 6; i++) {
                Cell c = titleRow.createCell(i);
                c.setCellStyle(titleStyle);
            }
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 6));

            // 第2行：表头
            Row headerRow = sheet.createRow(rowIdx++);
            headerRow.setHeight((short) (22 * 20));

            String[] headers = {"序号", "专业", "学号", "姓名", "毕设题目", "指导教师", "名次"};
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // 数据行：按名次分组排列
            int seq = 1;
            for (ExcellentGraduationProjectRecord r : allRecords) {
                Row dataRow = sheet.createRow(rowIdx++);
                dataRow.setHeight((short) (22 * 20));

                // A: 序号
                Cell cellA = dataRow.createCell(0);
                cellA.setCellValue(seq++);
                cellA.setCellStyle(dataStyle);

                // B: 专业
                Cell cellB = dataRow.createCell(1);
                cellB.setCellValue(r.getMajor() != null ? r.getMajor() : "");
                cellB.setCellStyle(dataStyle);

                // C: 学号
                Cell cellC = dataRow.createCell(2);
                cellC.setCellValue(r.getStudentId() != null ? r.getStudentId() : "");
                cellC.setCellStyle(dataStyle);

                // D: 姓名
                Cell cellD = dataRow.createCell(3);
                cellD.setCellValue(r.getStudentName() != null ? r.getStudentName() : "");
                cellD.setCellStyle(dataStyle);

                // E: 毕设题目
                Cell cellE = dataRow.createCell(4);
                cellE.setCellValue(r.getProjectTitle() != null ? r.getProjectTitle() : "");
                cellE.setCellStyle(dataStyle);

                // F: 指导教师
                Cell cellF = dataRow.createCell(5);
                cellF.setCellValue(r.getAdvisorName() != null ? r.getAdvisorName() : "");
                cellF.setCellStyle(dataStyle);

                // G: 名次
                Cell cellG = dataRow.createCell(6);
                if (r.getRank() != null) {
                    cellG.setCellValue(r.getRank());
                } else {
                    cellG.setCellValue("");
                }
                cellG.setCellStyle(dataStyle);
            }

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            workbook.write(bos);
            return bos.toByteArray();
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "Excel生成失败: " + e.getMessage());
        }
    }
}
