package com.jgh.ghairouter.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.jgh.ghairouter.exception.BusinessException;
import com.jgh.ghairouter.exception.ErrorCode;
import com.jgh.ghairouter.mapper.SchoolEnterpriseTrainingRecordMapper;
import com.jgh.ghairouter.mapper.TeacherCompetitionAuditRecordMapper;
import com.jgh.ghairouter.mapper.UserMapper;
import com.jgh.ghairouter.model.constants.SchoolEnterpriseTrainingScoringConstants;
import com.jgh.ghairouter.model.dto.competition.SchoolEnterpriseTrainingQueryRequest;
import com.jgh.ghairouter.model.entity.SchoolEnterpriseTrainingRecord;
import com.jgh.ghairouter.model.entity.TeacherCompetitionAuditRecord;
import com.jgh.ghairouter.model.entity.User;
import com.jgh.ghairouter.model.enums.ReviewStatusEnum;
import com.jgh.ghairouter.model.vo.SchoolEnterpriseTrainingRecordVO;
import com.jgh.ghairouter.service.SchoolEnterpriseTrainingRecordService;
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
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 校企联合培养记录服务实现（v17）。
 * 纯数据记录，不涉及计分。
 */
@Slf4j
@Service
public class SchoolEnterpriseTrainingRecordServiceImpl
        extends ServiceImpl<SchoolEnterpriseTrainingRecordMapper, SchoolEnterpriseTrainingRecord>
        implements SchoolEnterpriseTrainingRecordService {

    @Resource
    private UserMapper userMapper;
    @Resource
    private TeacherCompetitionAuditRecordMapper auditMapper;

    private static final String DEFAULT_TYPE_NAME = SchoolEnterpriseTrainingScoringConstants.TYPE_NAME;

    @Transactional(rollbackFor = Exception.class)
    @Override
    public Long addRecord(Long userId,
                          String studentName,
                          String studentId,
                          String major,
                          String companyName,
                          String remark,
                          String projectCollectionStatus,
                          String advisorName,
                          String counselorName,
                          MultipartFile file) {

        String base64 = null;
        if (file != null && !file.isEmpty()) {
            try {
                base64 = Base64.getEncoder().encodeToString(file.getBytes());
            } catch (IOException e) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "读取文件失败");
            }
        }

        SchoolEnterpriseTrainingRecord record = new SchoolEnterpriseTrainingRecord();
        record.setUserId(userId);
        record.setTypeName(DEFAULT_TYPE_NAME);
        record.setStudentName(studentName);
        record.setStudentId(studentId);
        record.setMajor(major);
        record.setCompanyName(companyName);
        record.setRemark(remark);
        record.setProjectCollectionStatus(projectCollectionStatus);
        record.setAdvisorName(advisorName);
        record.setCounselorName(counselorName);
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
    public QueryWrapper getQueryWrapper(SchoolEnterpriseTrainingQueryRequest req) {
        if (req == null) throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        QueryWrapper wrapper = QueryWrapper.create()
                .eq("id", req.getId())
                .eq("user_id", req.getUserId())
                .like("student_name", req.getStudentName())
                .like("major", req.getMajor())
                .like("company_name", req.getCompanyName())
                .like("advisor_name", req.getAdvisorName());
        if (StrUtil.isNotBlank(req.getSortField())) {
            wrapper.orderBy(req.getSortField(), "ascend".equals(req.getSortOrder()));
        }
        wrapper.orderBy("create_time", false);
        return wrapper;
    }

    @Override
    public SchoolEnterpriseTrainingRecordVO getRecordVO(SchoolEnterpriseTrainingRecord record) {
        if (record == null) return null;
        SchoolEnterpriseTrainingRecordVO vo = new SchoolEnterpriseTrainingRecordVO();
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
    public Page<SchoolEnterpriseTrainingRecordVO> pageRecords(SchoolEnterpriseTrainingQueryRequest req) {
        long pageNum = req.getPageNum();
        long pageSize = req.getPageSize();

        QueryWrapper wrapper = getQueryWrapper(req);
        if (StrUtil.isNotBlank(req.getAdminReviewStatus())) {
            Page<SchoolEnterpriseTrainingRecord> recordPage = this.page(Page.of(pageNum, pageSize), wrapper);
            List<SchoolEnterpriseTrainingRecordVO> voList = recordPage.getRecords().stream()
                    .map(this::getRecordVO)
                    .filter(vo -> req.getAdminReviewStatus().equals(vo.getAdminReviewStatus()))
                    .collect(Collectors.toList());
            Page<SchoolEnterpriseTrainingRecordVO> voPage = new Page<>(pageNum, pageSize, recordPage.getTotalRow());
            voPage.setRecords(voList);
            return voPage;
        }
        Page<SchoolEnterpriseTrainingRecord> recordPage = this.page(Page.of(pageNum, pageSize), wrapper);
        List<SchoolEnterpriseTrainingRecordVO> voList = recordPage.getRecords().stream()
                .map(this::getRecordVO)
                .collect(Collectors.toList());
        Page<SchoolEnterpriseTrainingRecordVO> voPage = new Page<>(pageNum, pageSize, recordPage.getTotalRow());
        voPage.setRecords(voList);
        return voPage;
    }

    @Override
    public Page<SchoolEnterpriseTrainingRecordVO> pageMyRelatedRecords(Long userId, SchoolEnterpriseTrainingQueryRequest req) {
        long pageNum = req.getPageNum();
        long pageSize = req.getPageSize();

        QueryWrapper wrapper = QueryWrapper.create()
                .eq("id", req.getId())
                .like("student_name", req.getStudentName())
                .like("major", req.getMajor())
                .like("company_name", req.getCompanyName())
                .like("advisor_name", req.getAdvisorName())
                .where("user_id = ?", userId);
        wrapper.orderBy("create_time", false);

        Page<SchoolEnterpriseTrainingRecord> recordPage = this.page(Page.of(pageNum, pageSize), wrapper);
        List<SchoolEnterpriseTrainingRecordVO> voList = recordPage.getRecords().stream()
                .map(this::getRecordVO)
                .collect(Collectors.toList());
        Page<SchoolEnterpriseTrainingRecordVO> voPage = new Page<>(pageNum, pageSize, recordPage.getTotalRow());
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

        SchoolEnterpriseTrainingRecord record = this.getById(recordId);
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

            // 标题样式：宋体、加粗、14号、居中、thin边框
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setFontName("宋体");
            headerFont.setBold(true);
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

            Sheet sheet = workbook.createSheet("16-校企联合培养");

            // 列宽
            sheet.setColumnWidth(0, (int) (8.22 * 256));   // A: 序号
            sheet.setColumnWidth(1, (int) (16.00 * 256));  // B: 姓名
            sheet.setColumnWidth(2, (int) (18.00 * 256));  // C: 学号
            sheet.setColumnWidth(3, (int) (25.00 * 256));  // D: 专业
            sheet.setColumnWidth(4, (int) (35.00 * 256));  // E: 公司名称
            sheet.setColumnWidth(5, (int) (20.00 * 256));  // F: 备注
            sheet.setColumnWidth(6, (int) (20.00 * 256));  // G: 企业毕设收集情况
            sheet.setColumnWidth(7, (int) (18.00 * 256));  // H: 校内指导老师
            sheet.setColumnWidth(8, (int) (16.00 * 256));  // I: 校内辅导员

            // 获取所有记录，按公司名称、校内辅导员、创建时间排序（容错：表不存在时返回空列表）
            List<SchoolEnterpriseTrainingRecord> allRecords;
            try {
                allRecords = this.list(
                        QueryWrapper.create().orderBy("company_name", true)
                                .orderBy("counselor_name", true)
                                .orderBy("create_time", true));
            } catch (Exception e) {
                log.warn("查询校企联合培养记录失败（可能表不存在），将导出空表: {}", e.getMessage());
                allRecords = java.util.Collections.emptyList();
            }

            int rowIdx = 0;

            // 第1行：表头（与示例Excel一致，无标题行，直接表头）
            Row headerRow = sheet.createRow(rowIdx++);
            headerRow.setHeight((short) (22 * 20));

            String[] headers = {"序号", "姓名", "学号", "专业", "公司名称", "备注（3+0.5+0.5或3+1）", "企业毕设收集情况", "校内指导老师", "校内辅导员"};
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // 数据行
            int seq = 1;
            for (SchoolEnterpriseTrainingRecord r : allRecords) {
                Row dataRow = sheet.createRow(rowIdx++);
                dataRow.setHeight((short) (22 * 20));

                // A: 序号
                Cell cellA = dataRow.createCell(0);
                cellA.setCellValue(seq++);
                cellA.setCellStyle(dataStyle);

                // B: 姓名
                Cell cellB = dataRow.createCell(1);
                cellB.setCellValue(r.getStudentName() != null ? r.getStudentName() : "");
                cellB.setCellStyle(dataStyle);

                // C: 学号
                Cell cellC = dataRow.createCell(2);
                cellC.setCellValue(r.getStudentId() != null ? r.getStudentId() : "");
                cellC.setCellStyle(dataStyle);

                // D: 专业
                Cell cellD = dataRow.createCell(3);
                cellD.setCellValue(r.getMajor() != null ? r.getMajor() : "");
                cellD.setCellStyle(dataStyle);

                // E: 公司名称
                Cell cellE = dataRow.createCell(4);
                cellE.setCellValue(r.getCompanyName() != null ? r.getCompanyName() : "");
                cellE.setCellStyle(dataStyle);

                // F: 备注
                Cell cellF = dataRow.createCell(5);
                cellF.setCellValue(r.getRemark() != null ? r.getRemark() : "");
                cellF.setCellStyle(dataStyle);

                // G: 企业毕设收集情况
                Cell cellG = dataRow.createCell(6);
                cellG.setCellValue(r.getProjectCollectionStatus() != null ? r.getProjectCollectionStatus() : "");
                cellG.setCellStyle(dataStyle);

                // H: 校内指导老师
                Cell cellH = dataRow.createCell(7);
                cellH.setCellValue(r.getAdvisorName() != null ? r.getAdvisorName() : "");
                cellH.setCellStyle(dataStyle);

                // I: 校内辅导员
                Cell cellI = dataRow.createCell(8);
                cellI.setCellValue(r.getCounselorName() != null ? r.getCounselorName() : "");
                cellI.setCellStyle(dataStyle);
            }

            // 合并校内辅导员列（I列）中相邻相同值的单元格
            // 数据从第2行开始（rowIdx 0=header, 1=first data row）
            int dataStartRow = 1; // 0-based, header is row 0
            int dataEndRow = rowIdx - 1; // last data row index
            if (dataStartRow <= dataEndRow) {
                int mergeStart = dataStartRow;
                String currentValue = getCellStringValue(sheet, mergeStart, 8);

                for (int r = dataStartRow + 1; r <= dataEndRow; r++) {
                    String cellValue = getCellStringValue(sheet, r, 8);
                    if (!cellValue.equals(currentValue)) {
                        // 合并从 mergeStart 到 r-1 的单元格
                        if (r - 1 > mergeStart && currentValue != null && !currentValue.isEmpty()) {
                            sheet.addMergedRegion(new CellRangeAddress(mergeStart, r - 1, 8, 8));
                        }
                        mergeStart = r;
                        currentValue = cellValue;
                    }
                }
                // 处理最后一组
                if (dataEndRow > mergeStart && currentValue != null && !currentValue.isEmpty()) {
                    sheet.addMergedRegion(new CellRangeAddress(mergeStart, dataEndRow, 8, 8));
                }
            }

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            workbook.write(bos);
            return bos.toByteArray();
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "Excel生成失败: " + e.getMessage());
        }
    }

    private String getCellStringValue(Sheet sheet, int rowIndex, int colIndex) {
        Row row = sheet.getRow(rowIndex);
        if (row == null) return "";
        Cell cell = row.getCell(colIndex);
        if (cell == null) return "";
        try {
            return cell.getStringCellValue();
        } catch (Exception e) {
            return "";
        }
    }
}
