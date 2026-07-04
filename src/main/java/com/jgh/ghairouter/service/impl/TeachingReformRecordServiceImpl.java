package com.jgh.ghairouter.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import com.jgh.ghairouter.exception.BusinessException;
import com.jgh.ghairouter.exception.ErrorCode;
import com.jgh.ghairouter.mapper.*;
import com.jgh.ghairouter.model.constants.TeachingReformScoringConstants;
import com.jgh.ghairouter.model.dto.competition.TeachingReformQueryRequest;
import com.jgh.ghairouter.model.entity.*;
import com.jgh.ghairouter.model.enums.ReviewStatusEnum;
import com.jgh.ghairouter.model.vo.TeachingReformRecordVO;
import com.jgh.ghairouter.model.vo.TeachingReformScoreVO;
import com.jgh.ghairouter.service.AiReviewService;
import com.jgh.ghairouter.service.TeachingReformRecordService;
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
 * 教改科研项目业绩记录服务实现（v7）
 */
@Slf4j
@Service
public class TeachingReformRecordServiceImpl
        extends ServiceImpl<TeachingReformRecordMapper, TeachingReformRecord>
        implements TeachingReformRecordService {

    @Resource
    private AiReviewService aiReviewService;
    @Resource
    private UserMapper userMapper;
    @Resource
    private TeacherCompetitionAuditRecordMapper auditMapper;
    @Resource
    private TeachingReformScoreMapper teachingReformScoreMapper;

    private static final String DEFAULT_TYPE_NAME = "教改科研项目业绩";

    // ==================== 删除（代码层面软删除级联） ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removeById(Serializable id) {
        // 1. 软删除子表得分明细记录
        teachingReformScoreMapper.deleteByQuery(
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
                          String projectName,
                          String projectType,
                          String projectStatus,
                          String projectLeader,
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

        TeachingReformRecord record = new TeachingReformRecord();
        record.setUserId(userId);
        record.setTypeName(DEFAULT_TYPE_NAME);
        record.setProjectName(projectName);
        record.setProjectType(projectType);
        record.setProjectStatus(projectStatus);
        record.setProjectLeader(projectLeader);
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

    private void saveTeachingReformScores(Long recordId, String memberData, String projectType, String projectStatus) {
        if (StrUtil.isBlank(memberData)) return;

        LocalDateTime now = LocalDateTime.now();
        try {
            JSONArray arr = new JSONArray(memberData);
            int memberCount = arr.size();
            if (memberCount == 0) return;

            // 计算总分
            BigDecimal totalScore = TeachingReformScoringConstants.calcProjectScore(projectType, projectStatus);
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
            List<BigDecimal> distributed = TeachingReformScoringConstants.distributeScore(totalScore, memberCount, leaderIndex);

            for (int i = 0; i < arr.size(); i++) {
                JSONObject entry = arr.getJSONObject(i);
                String teacherName = entry.getStr("teacherName");
                if (StrUtil.isBlank(teacherName)) continue;

                TeachingReformScore score = new TeachingReformScore();
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
                teachingReformScoreMapper.insert(score);
            }
        } catch (Exception e) {
            log.error("解析成员数据JSON失败: {}", memberData, e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "保存得分失败");
        }
    }

    // ==================== 查询 ====================

    @Override
    public QueryWrapper getQueryWrapper(TeachingReformQueryRequest req) {
        if (req == null) throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        QueryWrapper wrapper = QueryWrapper.create()
                .eq("id", req.getId())
                .eq("user_id", req.getUserId())
                .eq("project_type", req.getProjectType())
                .like("project_name", req.getProjectName());
        if (StrUtil.isNotBlank(req.getSortField())) {
            wrapper.orderBy(req.getSortField(), "ascend".equals(req.getSortOrder()));
        }
        wrapper.orderBy("create_time", false);
        return wrapper;
    }

    @Override
    public TeachingReformRecordVO getRecordVO(TeachingReformRecord record) {
        if (record == null) return null;
        TeachingReformRecordVO vo = new TeachingReformRecordVO();
        BeanUtil.copyProperties(record, vo);

        // 项目类型显示名
        vo.setProjectTypeText(TeachingReformScoringConstants.getProjectTypeText(record.getProjectType()));
        // 项目状态显示名
        vo.setProjectStatusText(TeachingReformScoringConstants.getProjectStatusText(record.getProjectStatus()));

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
        List<TeachingReformScore> scoreList = teachingReformScoreMapper.selectListByQuery(
                QueryWrapper.create().eq("record_id", record.getId()));
        if (CollUtil.isNotEmpty(scoreList)) {
            vo.setScores(scoreList.stream()
                    .map(s -> TeachingReformScoreVO.builder()
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
    public Page<TeachingReformRecordVO> pageRecords(TeachingReformQueryRequest req) {
        long pageNum = req.getPageNum();
        long pageSize = req.getPageSize();

        QueryWrapper wrapper = getQueryWrapper(req);
        if (StrUtil.isNotBlank(req.getAdminReviewStatus())) {
            Page<TeachingReformRecord> recordPage = this.page(Page.of(pageNum, pageSize), wrapper);
            List<TeachingReformRecordVO> voList = recordPage.getRecords().stream()
                    .map(this::getRecordVO)
                    .filter(vo -> req.getAdminReviewStatus().equals(vo.getAdminReviewStatus()))
                    .collect(Collectors.toList());
            Page<TeachingReformRecordVO> voPage = new Page<>(pageNum, pageSize, recordPage.getTotalRow());
            voPage.setRecords(voList);
            return voPage;
        }
        Page<TeachingReformRecord> recordPage = this.page(Page.of(pageNum, pageSize), wrapper);
        List<TeachingReformRecordVO> voList = recordPage.getRecords().stream()
                .map(this::getRecordVO)
                .collect(Collectors.toList());
        Page<TeachingReformRecordVO> voPage = new Page<>(pageNum, pageSize, recordPage.getTotalRow());
        voPage.setRecords(voList);
        return voPage;
    }

    @Override
    public Page<TeachingReformRecordVO> pageMyRelatedRecords(Long userId, TeachingReformQueryRequest req) {
        long pageNum = req.getPageNum();
        long pageSize = req.getPageSize();

        // 提交人 OR 在得分表中被分配了得分的成员，均可看到记录
        QueryWrapper wrapper = QueryWrapper.create()
                .eq("id", req.getId())
                .eq("project_type", req.getProjectType())
                .like("project_name", req.getProjectName())
                .where("(user_id = ? OR id IN (SELECT record_id FROM teaching_reform_score WHERE user_id = ? AND is_delete = 0))",
                       userId, userId);
        wrapper.orderBy("create_time", false);

        Page<TeachingReformRecord> recordPage = this.page(Page.of(pageNum, pageSize), wrapper);
        List<TeachingReformRecordVO> voList = recordPage.getRecords().stream()
                .map(record -> {
                    TeachingReformRecordVO vo = getRecordVO(record);
                    if (vo.getScores() != null) {
                        BigDecimal myTotal = vo.getScores().stream()
                                .filter(s -> userId.equals(s.getUserId()))
                                .map(TeachingReformScoreVO::getScore)
                                .reduce(BigDecimal.ZERO, BigDecimal::add);
                        if (myTotal.compareTo(BigDecimal.ZERO) > 0) {
                            vo.setMyScoreDisplay(myTotal.stripTrailingZeros().toPlainString());
                        }
                    }
                    return vo;
                })
                .collect(Collectors.toList());
        Page<TeachingReformRecordVO> voPage = new Page<>(pageNum, pageSize, recordPage.getTotalRow());
        voPage.setRecords(voList);
        return voPage;
    }

    @Override
    public BigDecimal getMyTotalScore(Long userId) {
        List<TeachingReformScore> scores = teachingReformScoreMapper.selectListByQuery(
                QueryWrapper.create().eq("user_id", userId));
        if (CollUtil.isEmpty(scores)) return BigDecimal.ZERO;
        return scores.stream()
                .map(TeachingReformScore::getScore)
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

        TeachingReformRecord record = this.getById(recordId);
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
            teachingReformScoreMapper.deleteByQuery(
                    QueryWrapper.create().eq("record_id", recordId));

            saveTeachingReformScores(recordId, record.getMemberData(), record.getProjectType(), record.getProjectStatus());
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

            String[] headers = {"序号", "项目名称", "项目类型", "项目状态", "项目负责人", "项目组成员及得分"};
            int colCount = headers.length;

            org.apache.poi.ss.usermodel.Sheet sheet = workbook.createSheet("教改科研项目业绩");

            List<TeachingReformRecord> allRecords = this.list(
                    QueryWrapper.create().orderBy("create_time", true));

            // 按项目状态分组
            List<TeachingReformRecord> approvedRecords = allRecords.stream()
                    .filter(r -> TeachingReformScoringConstants.STATUS_APPROVED.equals(r.getProjectStatus()))
                    .collect(Collectors.toList());
            List<TeachingReformRecord> notApprovedRecords = allRecords.stream()
                    .filter(r -> TeachingReformScoringConstants.STATUS_NOT_APPROVED.equals(r.getProjectStatus()))
                    .collect(Collectors.toList());
            List<TeachingReformRecord> pendingRecords = allRecords.stream()
                    .filter(r -> TeachingReformScoringConstants.STATUS_PENDING_DECISION.equals(r.getProjectStatus()))
                    .collect(Collectors.toList());
            // 其他状态归入获批
            List<TeachingReformRecord> otherRecords = allRecords.stream()
                    .filter(r -> r.getProjectStatus() == null
                            || (!TeachingReformScoringConstants.STATUS_APPROVED.equals(r.getProjectStatus())
                                && !TeachingReformScoringConstants.STATUS_NOT_APPROVED.equals(r.getProjectStatus())
                                && !TeachingReformScoringConstants.STATUS_PENDING_DECISION.equals(r.getProjectStatus())))
                    .collect(Collectors.toList());

            int rowIdx = 0;

            // ========== 一、获批立项项目 ==========
            List<TeachingReformRecord> section1 = new ArrayList<>();
            section1.addAll(approvedRecords);
            section1.addAll(otherRecords);
            if (!section1.isEmpty()) {
                rowIdx = writeSection(sheet, headerStyle, sectionStyle, headers, colCount,
                        "一、获批立项的教改科研项目", section1, rowIdx);
            }

            // ========== 二、未下文项目 ==========
            if (!pendingRecords.isEmpty()) {
                rowIdx = writeSection(sheet, headerStyle, sectionStyle, headers, colCount,
                        "二、未下文的教改科研项目", pendingRecords, rowIdx);
            }

            // ========== 三、未获批项目 ==========
            if (!notApprovedRecords.isEmpty()) {
                rowIdx = writeSection(sheet, headerStyle, sectionStyle, headers, colCount,
                        "三、未获批的教改科研项目", notApprovedRecords, rowIdx);
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

    private int writeSection(org.apache.poi.ss.usermodel.Sheet sheet,
                             org.apache.poi.ss.usermodel.CellStyle headerStyle,
                             org.apache.poi.ss.usermodel.CellStyle sectionStyle,
                             String[] headers, int colCount,
                             String sectionTitle,
                             List<TeachingReformRecord> records,
                             int rowIdx) {
        // 章节标题行
        org.apache.poi.ss.usermodel.Row sectionTitleRow = sheet.createRow(rowIdx++);
        org.apache.poi.ss.usermodel.Cell titleCell = sectionTitleRow.createCell(0);
        titleCell.setCellValue(sectionTitle);
        titleCell.setCellStyle(sectionStyle);
        sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(rowIdx - 1, rowIdx - 1, 0, colCount - 1));

        // 表头行
        org.apache.poi.ss.usermodel.Row headerRow = sheet.createRow(rowIdx++);
        for (int i = 0; i < headers.length; i++) {
            org.apache.poi.ss.usermodel.Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        // 数据行
        int seq = 1;
        for (TeachingReformRecord record : records) {
            org.apache.poi.ss.usermodel.Row row = sheet.createRow(rowIdx++);
            row.createCell(0).setCellValue(seq++);
            row.createCell(1).setCellValue(record.getProjectName() != null ? record.getProjectName() : "");
            row.createCell(2).setCellValue(TeachingReformScoringConstants.getProjectTypeText(record.getProjectType()));
            row.createCell(3).setCellValue(TeachingReformScoringConstants.getProjectStatusText(record.getProjectStatus()));
            row.createCell(4).setCellValue(record.getProjectLeader() != null ? record.getProjectLeader() : "");
            row.createCell(5).setCellValue(formatMemberScores(record.getMemberData(), record.getProjectType(), record.getProjectStatus()));
        }

        // 空两行
        rowIdx += 2;
        return rowIdx;
    }

    /**
     * 格式化项目组成员及得分为显示字符串
     * 格式：教师名得分、教师名得分...
     */
    private String formatMemberScores(String memberData, String projectType, String projectStatus) {
        if (StrUtil.isBlank(memberData)) return "";
        try {
            JSONArray arr = new JSONArray(memberData);
            int memberCount = arr.size();
            if (memberCount == 0) return "";

            BigDecimal totalScore = TeachingReformScoringConstants.calcProjectScore(projectType, projectStatus);
            if (totalScore.compareTo(BigDecimal.ZERO) <= 0) return "";

            int leaderIndex = -1;
            for (int i = 0; i < arr.size(); i++) {
                JSONObject entry = arr.getJSONObject(i);
                if (entry.getBool("isLeader", false)) {
                    leaderIndex = i;
                    break;
                }
            }

            List<BigDecimal> distributed = TeachingReformScoringConstants.distributeScore(totalScore, memberCount, leaderIndex);

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
            log.error("格式化成员得分失败: {}", memberData, e);
            return memberData;
        }
    }
}
