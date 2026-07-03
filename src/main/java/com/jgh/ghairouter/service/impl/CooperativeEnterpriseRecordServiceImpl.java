package com.jgh.ghairouter.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.jgh.ghairouter.exception.BusinessException;
import com.jgh.ghairouter.exception.ErrorCode;
import com.jgh.ghairouter.mapper.CooperativeEnterpriseRecordMapper;
import com.jgh.ghairouter.mapper.TeacherCompetitionAuditRecordMapper;
import com.jgh.ghairouter.mapper.UserMapper;
import com.jgh.ghairouter.model.constants.CooperativeEnterpriseScoringConstants;
import com.jgh.ghairouter.model.dto.competition.CooperativeEnterpriseQueryRequest;
import com.jgh.ghairouter.model.entity.CooperativeEnterpriseRecord;
import com.jgh.ghairouter.model.entity.TeacherCompetitionAuditRecord;
import com.jgh.ghairouter.model.entity.User;
import com.jgh.ghairouter.model.enums.ReviewStatusEnum;
import com.jgh.ghairouter.model.vo.CooperativeEnterpriseRecordVO;
import com.jgh.ghairouter.service.CooperativeEnterpriseRecordService;
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
import java.time.Year;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 签订合作企业记录服务实现（v14）。
 * 纯数据记录，不涉及计分。
 */
@Slf4j
@Service
public class CooperativeEnterpriseRecordServiceImpl
        extends ServiceImpl<CooperativeEnterpriseRecordMapper, CooperativeEnterpriseRecord>
        implements CooperativeEnterpriseRecordService {

    @Resource
    private UserMapper userMapper;
    @Resource
    private TeacherCompetitionAuditRecordMapper auditMapper;

    private static final String DEFAULT_TYPE_NAME = CooperativeEnterpriseScoringConstants.TYPE_NAME;

    @Transactional(rollbackFor = Exception.class)
    @Override
    public Long addRecord(Long userId,
                          String college,
                          String enterpriseName,
                          String teacherName,
                          MultipartFile file) {

        String base64 = null;
        if (file != null && !file.isEmpty()) {
            try {
                base64 = Base64.getEncoder().encodeToString(file.getBytes());
            } catch (IOException e) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "读取文件失败");
            }
        }

        CooperativeEnterpriseRecord record = new CooperativeEnterpriseRecord();
        record.setUserId(userId);
        record.setTypeName(DEFAULT_TYPE_NAME);
        record.setCollege(college);
        record.setEnterpriseName(enterpriseName);
        record.setTeacherName(teacherName);
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
    public QueryWrapper getQueryWrapper(CooperativeEnterpriseQueryRequest req) {
        if (req == null) throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        QueryWrapper wrapper = QueryWrapper.create()
                .eq("id", req.getId())
                .eq("user_id", req.getUserId())
                .like("teacher_name", req.getTeacherName())
                .like("college", req.getCollege())
                .like("enterprise_name", req.getEnterpriseName());
        if (StrUtil.isNotBlank(req.getSortField())) {
            wrapper.orderBy(req.getSortField(), "ascend".equals(req.getSortOrder()));
        }
        wrapper.orderBy("create_time", false);
        return wrapper;
    }

    @Override
    public CooperativeEnterpriseRecordVO getRecordVO(CooperativeEnterpriseRecord record) {
        if (record == null) return null;
        CooperativeEnterpriseRecordVO vo = new CooperativeEnterpriseRecordVO();
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
    public Page<CooperativeEnterpriseRecordVO> pageRecords(CooperativeEnterpriseQueryRequest req) {
        long pageNum = req.getPageNum();
        long pageSize = req.getPageSize();

        QueryWrapper wrapper = getQueryWrapper(req);
        if (StrUtil.isNotBlank(req.getAdminReviewStatus())) {
            Page<CooperativeEnterpriseRecord> recordPage = this.page(Page.of(pageNum, pageSize), wrapper);
            List<CooperativeEnterpriseRecordVO> voList = recordPage.getRecords().stream()
                    .map(this::getRecordVO)
                    .filter(vo -> req.getAdminReviewStatus().equals(vo.getAdminReviewStatus()))
                    .collect(Collectors.toList());
            Page<CooperativeEnterpriseRecordVO> voPage = new Page<>(pageNum, pageSize, recordPage.getTotalRow());
            voPage.setRecords(voList);
            return voPage;
        }
        Page<CooperativeEnterpriseRecord> recordPage = this.page(Page.of(pageNum, pageSize), wrapper);
        List<CooperativeEnterpriseRecordVO> voList = recordPage.getRecords().stream()
                .map(this::getRecordVO)
                .collect(Collectors.toList());
        Page<CooperativeEnterpriseRecordVO> voPage = new Page<>(pageNum, pageSize, recordPage.getTotalRow());
        voPage.setRecords(voList);
        return voPage;
    }

    @Override
    public Page<CooperativeEnterpriseRecordVO> pageMyRelatedRecords(Long userId, CooperativeEnterpriseQueryRequest req) {
        long pageNum = req.getPageNum();
        long pageSize = req.getPageSize();

        // 提交人 OR 签订合作企业的老师本人，均可看到记录
        QueryWrapper wrapper = QueryWrapper.create()
                .eq("id", req.getId())
                .like("teacher_name", req.getTeacherName())
                .like("college", req.getCollege())
                .like("enterprise_name", req.getEnterpriseName())
                .where("(user_id = ? OR teacher_name = (SELECT user_name FROM user WHERE id = ? AND is_delete = 0))",
                       userId, userId);
        wrapper.orderBy("create_time", false);

        Page<CooperativeEnterpriseRecord> recordPage = this.page(Page.of(pageNum, pageSize), wrapper);
        List<CooperativeEnterpriseRecordVO> voList = recordPage.getRecords().stream()
                .map(this::getRecordVO)
                .collect(Collectors.toList());
        Page<CooperativeEnterpriseRecordVO> voPage = new Page<>(pageNum, pageSize, recordPage.getTotalRow());
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

        CooperativeEnterpriseRecord record = this.getById(recordId);
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

            Sheet sheet = workbook.createSheet("13-签订合作企业");

            // 列宽
            sheet.setColumnWidth(0, (int) (8.22 * 256));   // A: 序号
            sheet.setColumnWidth(1, (int) (18.22 * 256));  // B: 学院
            sheet.setColumnWidth(2, (int) (42.00 * 256));  // C: 企业名称
            sheet.setColumnWidth(3, (int) (20.22 * 256));  // D: 签订合作企业老师

            // 获取所有记录，按学院、创建时间排序
            List<CooperativeEnterpriseRecord> allRecords = this.list(
                    QueryWrapper.create().orderBy("college", true)
                            .orderBy("create_time", true));

            // 按学院分组（保持插入顺序）
            Map<String, List<CooperativeEnterpriseRecord>> collegeGroups = new LinkedHashMap<>();
            for (CooperativeEnterpriseRecord r : allRecords) {
                String college = StrUtil.isNotBlank(r.getCollege()) ? r.getCollege() : "未知学院";
                collegeGroups.computeIfAbsent(college, k -> new ArrayList<>()).add(r);
            }

            String year = String.valueOf(Year.now().getValue());
            int rowIdx = 0;

            // 第1行：标题（合并A1:D1）
            Row titleRow = sheet.createRow(rowIdx++);
            titleRow.setHeight((short) (25 * 20));
            Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue(year + "年签订合作企业汇总");
            titleCell.setCellStyle(titleStyle);
            for (int i = 1; i <= 3; i++) {
                Cell c = titleRow.createCell(i);
                c.setCellStyle(titleStyle);
            }
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 3));

            // 第2行：表头
            Row headerRow = sheet.createRow(rowIdx++);
            headerRow.setHeight((short) (20 * 20));

            String[] headers = {"序号", "学院", "企业名称", "签订合作企业老师"};
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // 数据行：按学院分组排列，每行都有序号和学院
            int seq = 1;
            for (Map.Entry<String, List<CooperativeEnterpriseRecord>> entry : collegeGroups.entrySet()) {
                List<CooperativeEnterpriseRecord> records = entry.getValue();

                for (int i = 0; i < records.size(); i++) {
                    CooperativeEnterpriseRecord r = records.get(i);
                    Row dataRow = sheet.createRow(rowIdx++);
                    dataRow.setHeight((short) (20 * 20));

                    // A: 序号（每行一个序号）
                    Cell cellA = dataRow.createCell(0);
                    cellA.setCellValue(seq++);
                    cellA.setCellStyle(dataStyle);

                    // B: 学院（每行都显示学院）
                    Cell cellB = dataRow.createCell(1);
                    cellB.setCellValue(r.getCollege() != null ? r.getCollege() : "");
                    cellB.setCellStyle(dataStyle);

                    // C: 企业名称
                    Cell cellC = dataRow.createCell(2);
                    cellC.setCellValue(r.getEnterpriseName() != null ? r.getEnterpriseName() : "");
                    cellC.setCellStyle(dataStyle);

                    // D: 签订合作企业老师
                    Cell cellD = dataRow.createCell(3);
                    cellD.setCellValue(r.getTeacherName() != null ? r.getTeacherName() : "");
                    cellD.setCellStyle(dataStyle);
                }
            }

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            workbook.write(bos);
            return bos.toByteArray();
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "Excel生成失败: " + e.getMessage());
        }
    }
}
