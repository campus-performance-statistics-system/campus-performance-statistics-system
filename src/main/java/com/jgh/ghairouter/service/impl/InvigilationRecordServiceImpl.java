package com.jgh.ghairouter.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.jgh.ghairouter.exception.BusinessException;
import com.jgh.ghairouter.exception.ErrorCode;
import com.jgh.ghairouter.mapper.InvigilationRecordMapper;
import com.jgh.ghairouter.mapper.TeacherCompetitionAuditRecordMapper;
import com.jgh.ghairouter.mapper.UserMapper;
import com.jgh.ghairouter.model.constants.InvigilationScoringConstants;
import com.jgh.ghairouter.model.dto.competition.InvigilationQueryRequest;
import com.jgh.ghairouter.model.entity.InvigilationRecord;
import com.jgh.ghairouter.model.entity.TeacherCompetitionAuditRecord;
import com.jgh.ghairouter.model.entity.User;
import com.jgh.ghairouter.model.enums.ReviewStatusEnum;
import com.jgh.ghairouter.model.vo.InvigilationRecordVO;
import com.jgh.ghairouter.service.InvigilationRecordService;
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
import java.time.Year;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 监考次数统计记录服务实现（v11）。
 * 纯数据记录，不涉及计分。
 */
@Slf4j
@Service
public class InvigilationRecordServiceImpl
        extends ServiceImpl<InvigilationRecordMapper, InvigilationRecord>
        implements InvigilationRecordService {

    @Resource
    private UserMapper userMapper;
    @Resource
    private TeacherCompetitionAuditRecordMapper auditMapper;

    private static final String DEFAULT_TYPE_NAME = InvigilationScoringConstants.TYPE_NAME;

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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long addRecord(Long userId,
                          String teacherName,
                          Integer invigilationCount,
                          MultipartFile file) {

        String base64 = null;
        if (file != null && !file.isEmpty()) {
            try {
                base64 = Base64.getEncoder().encodeToString(file.getBytes());
            } catch (IOException e) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "读取文件失败");
            }
        }

        InvigilationRecord record = new InvigilationRecord();
        record.setUserId(userId);
        record.setTypeName(DEFAULT_TYPE_NAME);
        record.setTeacherName(teacherName);
        record.setInvigilationCount(invigilationCount != null ? invigilationCount : 0);
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
    public QueryWrapper getQueryWrapper(InvigilationQueryRequest req) {
        if (req == null) throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        QueryWrapper wrapper = QueryWrapper.create()
                .eq("id", req.getId())
                .eq("user_id", req.getUserId())
                .like("teacher_name", req.getTeacherName());
        if (StrUtil.isNotBlank(req.getSortField())) {
            wrapper.orderBy(req.getSortField(), "ascend".equals(req.getSortOrder()));
        }
        wrapper.orderBy("create_time", false);
        return wrapper;
    }

    @Override
    public InvigilationRecordVO getRecordVO(InvigilationRecord record) {
        if (record == null) return null;
        InvigilationRecordVO vo = new InvigilationRecordVO();
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
    public Page<InvigilationRecordVO> pageRecords(InvigilationQueryRequest req) {
        long pageNum = req.getPageNum();
        long pageSize = req.getPageSize();

        QueryWrapper wrapper = getQueryWrapper(req);
        if (StrUtil.isNotBlank(req.getAdminReviewStatus())) {
            Page<InvigilationRecord> recordPage = this.page(Page.of(pageNum, pageSize), wrapper);
            List<InvigilationRecordVO> voList = recordPage.getRecords().stream()
                    .map(this::getRecordVO)
                    .filter(vo -> req.getAdminReviewStatus().equals(vo.getAdminReviewStatus()))
                    .collect(Collectors.toList());
            Page<InvigilationRecordVO> voPage = new Page<>(pageNum, pageSize, recordPage.getTotalRow());
            voPage.setRecords(voList);
            return voPage;
        }
        Page<InvigilationRecord> recordPage = this.page(Page.of(pageNum, pageSize), wrapper);
        List<InvigilationRecordVO> voList = recordPage.getRecords().stream()
                .map(this::getRecordVO)
                .collect(Collectors.toList());
        Page<InvigilationRecordVO> voPage = new Page<>(pageNum, pageSize, recordPage.getTotalRow());
        voPage.setRecords(voList);
        return voPage;
    }

    @Override
    public Page<InvigilationRecordVO> pageMyRelatedRecords(Long userId, InvigilationQueryRequest req) {
        long pageNum = req.getPageNum();
        long pageSize = req.getPageSize();

        // 提交人 OR 被记录的监考老师本人，均可看到记录
        QueryWrapper wrapper = QueryWrapper.create()
                .eq("id", req.getId())
                .like("teacher_name", req.getTeacherName())
                .where("(user_id = ? OR teacher_name = (SELECT user_name FROM user WHERE id = ? AND is_delete = 0))",
                       userId, userId);
        wrapper.orderBy("create_time", false);

        Page<InvigilationRecord> recordPage = this.page(Page.of(pageNum, pageSize), wrapper);
        List<InvigilationRecordVO> voList = recordPage.getRecords().stream()
                .map(this::getRecordVO)
                .collect(Collectors.toList());
        Page<InvigilationRecordVO> voPage = new Page<>(pageNum, pageSize, recordPage.getTotalRow());
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

        InvigilationRecord record = this.getById(recordId);
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

            Sheet sheet = workbook.createSheet("10-监考次数统计");

            // 列宽（与示例文件一致）
            sheet.setColumnWidth(0, (int) (6.5 * 256));
            sheet.setColumnWidth(1, (int) (11.22 * 256));
            sheet.setColumnWidth(2, (int) (10.5 * 256));

            // 获取所有记录，按教师姓名汇总监考次数
            List<InvigilationRecord> allRecords = this.list(
                    QueryWrapper.create().orderBy("teacher_name", true));

            // 按教师姓名汇总监考次数
            Map<String, Integer> teacherCountMap = new LinkedHashMap<>();
            for (InvigilationRecord r : allRecords) {
                String name = r.getTeacherName();
                int count = r.getInvigilationCount() != null ? r.getInvigilationCount() : 0;
                teacherCountMap.merge(name, count, Integer::sum);
            }

            // 按监考次数降序排列
            List<Map.Entry<String, Integer>> sortedEntries = new ArrayList<>(teacherCountMap.entrySet());
            sortedEntries.sort((a, b) -> b.getValue().compareTo(a.getValue()));

            String year = String.valueOf(Year.now().getValue());
            int rowIdx = 0;

            // 第1行：标题（合并A1:C1）
            Row titleRow = sheet.createRow(rowIdx++);
            titleRow.setHeight((short) (20.4 * 20));
            Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue(year + "年教务二次分配的课程监考次数统计");
            titleCell.setCellStyle(titleStyle);
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 2));

            // 第2行：表头
            Row headerRow = sheet.createRow(rowIdx++);
            headerRow.setHeight((short) (17.4 * 20));

            Cell cellA2 = headerRow.createCell(0);
            cellA2.setCellValue("序号");
            cellA2.setCellStyle(headerStyle);

            Cell cellB2 = headerRow.createCell(1);
            cellB2.setCellValue("监考老师");
            cellB2.setCellStyle(headerStyle);

            Cell cellC2 = headerRow.createCell(2);
            cellC2.setCellValue("监考次数");
            cellC2.setCellStyle(headerStyle);

            // 数据行
            int seq = 1;
            for (Map.Entry<String, Integer> entry : sortedEntries) {
                Row dataRow = sheet.createRow(rowIdx++);
                dataRow.setHeight((short) (17.4 * 20));

                Cell cellA = dataRow.createCell(0);
                cellA.setCellValue(seq++);
                cellA.setCellStyle(dataStyle);

                Cell cellB = dataRow.createCell(1);
                cellB.setCellValue(entry.getKey());
                cellB.setCellStyle(dataStyle);

                Cell cellC = dataRow.createCell(2);
                cellC.setCellValue(entry.getValue());
                cellC.setCellStyle(dataStyle);
            }

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            workbook.write(bos);
            return bos.toByteArray();
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "Excel生成失败: " + e.getMessage());
        }
    }
}
