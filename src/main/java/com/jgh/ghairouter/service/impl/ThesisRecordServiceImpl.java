package com.jgh.ghairouter.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import com.jgh.ghairouter.exception.BusinessException;
import com.jgh.ghairouter.exception.ErrorCode;
import com.jgh.ghairouter.mapper.*;
import com.jgh.ghairouter.model.constants.ThesisScoringConstants;
import com.jgh.ghairouter.model.dto.competition.ThesisQueryRequest;
import com.jgh.ghairouter.model.entity.*;
import com.jgh.ghairouter.model.enums.ReviewStatusEnum;
import com.jgh.ghairouter.model.vo.ThesisRecordVO;
import com.jgh.ghairouter.model.vo.ThesisScoreVO;
import com.jgh.ghairouter.service.AiReviewService;
import com.jgh.ghairouter.service.ThesisRecordService;
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
 * 论文业绩记录服务实现（v8）
 */
@Slf4j
@Service
public class ThesisRecordServiceImpl
        extends ServiceImpl<ThesisRecordMapper, ThesisRecord>
        implements ThesisRecordService {

    @Resource
    private AiReviewService aiReviewService;
    @Resource
    private UserMapper userMapper;
    @Resource
    private TeacherCompetitionAuditRecordMapper auditMapper;
    @Resource
    private ThesisScoreMapper thesisScoreMapper;

    private static final String DEFAULT_TYPE_NAME = "论文业绩";

    // ==================== 删除（代码层面软删除级联） ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removeById(Serializable id) {
        // 1. 软删除子表得分明细记录
        thesisScoreMapper.deleteByQuery(
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
                          String thesisName,
                          String journalName,
                          String thesisLevel,
                          String authorsData,
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

        ThesisRecord record = new ThesisRecord();
        record.setUserId(userId);
        record.setTypeName(DEFAULT_TYPE_NAME);
        record.setThesisName(thesisName);
        record.setJournalName(journalName);
        record.setThesisLevel(thesisLevel);
        record.setAuthorsData(authorsData);
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
                aiReviewService.autoReview(record.getId(), record.getTypeName(), thesisName, base64, mimeType);
            } catch (Exception e) {
                log.error("AI审核触发失败", e);
            }
        }
        return record.getId();
    }

    // ==================== 保存得分明细 ====================

    private void saveThesisScores(Long recordId, String authorsData, String thesisLevel) {
        if (StrUtil.isBlank(authorsData)) return;

        LocalDateTime now = LocalDateTime.now();
        try {
            JSONArray arr = new JSONArray(authorsData);
            int authorCount = arr.size();
            if (authorCount == 0) return;

            // 计算总分
            BigDecimal totalScore = ThesisScoringConstants.calcThesisScore(thesisLevel);
            if (totalScore.compareTo(BigDecimal.ZERO) <= 0) return;

            // 确定 firstAuthorIndex
            int firstAuthorIndex = -1;
            for (int i = 0; i < arr.size(); i++) {
                JSONObject entry = arr.getJSONObject(i);
                if (entry.getBool("isFirstAuthor", false)) {
                    firstAuthorIndex = i;
                    break;
                }
            }

            // 分配得分
            List<BigDecimal> distributed = ThesisScoringConstants.distributeScore(totalScore, authorCount, firstAuthorIndex);

            for (int i = 0; i < arr.size(); i++) {
                JSONObject entry = arr.getJSONObject(i);
                String teacherName = entry.getStr("teacherName");
                if (StrUtil.isBlank(teacherName)) continue;

                ThesisScore score = new ThesisScore();
                score.setRecordId(recordId);
                score.setTeacherName(teacherName);
                score.setScore(distributed.get(i));
                score.setIsFirstAuthor(entry.getBool("isFirstAuthor", false) ? 1 : 0);

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
                thesisScoreMapper.insert(score);
            }
        } catch (Exception e) {
            log.error("解析作者数据JSON失败: {}", authorsData, e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "保存得分失败");
        }
    }

    // ==================== 查询 ====================

    @Override
    public QueryWrapper getQueryWrapper(ThesisQueryRequest req) {
        if (req == null) throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        QueryWrapper wrapper = QueryWrapper.create()
                .eq("id", req.getId())
                .eq("user_id", req.getUserId())
                .eq("thesis_level", req.getThesisLevel())
                .like("thesis_name", req.getThesisName());
        if (StrUtil.isNotBlank(req.getSortField())) {
            wrapper.orderBy(req.getSortField(), "ascend".equals(req.getSortOrder()));
        }
        wrapper.orderBy("create_time", false);
        return wrapper;
    }

    @Override
    public ThesisRecordVO getRecordVO(ThesisRecord record) {
        if (record == null) return null;
        ThesisRecordVO vo = new ThesisRecordVO();
        BeanUtil.copyProperties(record, vo);

        // 论文等级显示名
        vo.setThesisLevelText(ThesisScoringConstants.getLevelText(record.getThesisLevel()));

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
        List<ThesisScore> scoreList = thesisScoreMapper.selectListByQuery(
                QueryWrapper.create().eq("record_id", record.getId()));
        if (CollUtil.isNotEmpty(scoreList)) {
            vo.setScores(scoreList.stream()
                    .map(s -> ThesisScoreVO.builder()
                            .userId(s.getUserId())
                            .teacherName(s.getTeacherName())
                            .score(s.getScore())
                            .isFirstAuthor(s.getIsFirstAuthor())
                            .build())
                    .collect(Collectors.toList()));
        }

        return vo;
    }

    @Override
    public Page<ThesisRecordVO> pageRecords(ThesisQueryRequest req) {
        long pageNum = req.getPageNum();
        long pageSize = req.getPageSize();

        QueryWrapper wrapper = getQueryWrapper(req);
        if (StrUtil.isNotBlank(req.getAdminReviewStatus())) {
            Page<ThesisRecord> recordPage = this.page(Page.of(pageNum, pageSize), wrapper);
            List<ThesisRecordVO> voList = recordPage.getRecords().stream()
                    .map(this::getRecordVO)
                    .filter(vo -> req.getAdminReviewStatus().equals(vo.getAdminReviewStatus()))
                    .collect(Collectors.toList());
            Page<ThesisRecordVO> voPage = new Page<>(pageNum, pageSize, recordPage.getTotalRow());
            voPage.setRecords(voList);
            return voPage;
        }
        Page<ThesisRecord> recordPage = this.page(Page.of(pageNum, pageSize), wrapper);
        List<ThesisRecordVO> voList = recordPage.getRecords().stream()
                .map(this::getRecordVO)
                .collect(Collectors.toList());
        Page<ThesisRecordVO> voPage = new Page<>(pageNum, pageSize, recordPage.getTotalRow());
        voPage.setRecords(voList);
        return voPage;
    }

    @Override
    public Page<ThesisRecordVO> pageMyRelatedRecords(Long userId, ThesisQueryRequest req) {
        long pageNum = req.getPageNum();
        long pageSize = req.getPageSize();

        // 提交人 OR 在得分表中被分配了得分的作者，均可看到记录
        QueryWrapper wrapper = QueryWrapper.create()
                .eq("id", req.getId())
                .eq("thesis_level", req.getThesisLevel())
                .like("thesis_name", req.getThesisName())
                .where("(user_id = ? OR id IN (SELECT record_id FROM thesis_score WHERE user_id = ? AND is_delete = 0))",
                       userId, userId);
        wrapper.orderBy("create_time", false);

        Page<ThesisRecord> recordPage = this.page(Page.of(pageNum, pageSize), wrapper);
        List<ThesisRecordVO> voList = recordPage.getRecords().stream()
                .map(record -> {
                    ThesisRecordVO vo = getRecordVO(record);
                    if (vo.getScores() != null) {
                        BigDecimal myTotal = vo.getScores().stream()
                                .filter(s -> userId.equals(s.getUserId()))
                                .map(ThesisScoreVO::getScore)
                                .reduce(BigDecimal.ZERO, BigDecimal::add);
                        if (myTotal.compareTo(BigDecimal.ZERO) > 0) {
                            vo.setMyScoreDisplay(myTotal.stripTrailingZeros().toPlainString());
                        }
                    }
                    return vo;
                })
                .collect(Collectors.toList());
        Page<ThesisRecordVO> voPage = new Page<>(pageNum, pageSize, recordPage.getTotalRow());
        voPage.setRecords(voList);
        return voPage;
    }

    @Override
    public BigDecimal getMyTotalScore(Long userId) {
        List<ThesisScore> scores = thesisScoreMapper.selectListByQuery(
                QueryWrapper.create().eq("user_id", userId));
        if (CollUtil.isEmpty(scores)) return BigDecimal.ZERO;
        return scores.stream()
                .map(ThesisScore::getScore)
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

        ThesisRecord record = this.getById(recordId);
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
            thesisScoreMapper.deleteByQuery(
                    QueryWrapper.create().eq("record_id", recordId));

            saveThesisScores(recordId, record.getAuthorsData(), record.getThesisLevel());
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

            // 标题样式
            org.apache.poi.ss.usermodel.CellStyle titleStyle = workbook.createCellStyle();
            org.apache.poi.ss.usermodel.Font titleFont = workbook.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 14);
            titleStyle.setFont(titleFont);
            titleStyle.setAlignment(org.apache.poi.ss.usermodel.HorizontalAlignment.CENTER);
            titleStyle.setVerticalAlignment(org.apache.poi.ss.usermodel.VerticalAlignment.CENTER);

            // 注脚样式
            org.apache.poi.ss.usermodel.CellStyle noteStyle = workbook.createCellStyle();
            noteStyle.setWrapText(true);

            String[] headers = {"序号", "论文名称", "发表刊物", "论文等级", "作者", "", "姓名", "业绩分"};
            int colCount = headers.length;

            org.apache.poi.ss.usermodel.Sheet sheet = workbook.createSheet("7-论文业绩");

            List<ThesisRecord> allRecords = this.list(
                    QueryWrapper.create().orderBy("create_time", true));

            int rowIdx = 0;

            // ========== 标题行 ==========
            org.apache.poi.ss.usermodel.Row titleRow = sheet.createRow(rowIdx++);
            org.apache.poi.ss.usermodel.Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue("2023年信息工程学院论文工作量统计表（教务汇总）");
            titleCell.setCellStyle(titleStyle);
            sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(rowIdx - 1, rowIdx - 1, 0, colCount - 1));

            // ========== 表头行 ==========
            org.apache.poi.ss.usermodel.Row headerRow = sheet.createRow(rowIdx++);
            for (int i = 0; i < headers.length; i++) {
                org.apache.poi.ss.usermodel.Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // ========== 数据行 ==========
            int seq = 1;
            for (ThesisRecord record : allRecords) {
                org.apache.poi.ss.usermodel.Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(seq++);
                row.createCell(1).setCellValue(record.getThesisName() != null ? record.getThesisName() : "");
                row.createCell(2).setCellValue(record.getJournalName() != null ? record.getJournalName() : "");
                row.createCell(3).setCellValue(ThesisScoringConstants.getLevelText(record.getThesisLevel()));
                row.createCell(4).setCellValue(formatAuthorsScores(record.getAuthorsData(), record.getThesisLevel()));
                row.createCell(5).setCellValue("");
                row.createCell(6).setCellValue(getSubmitterName(record.getUserId()));
                row.createCell(7).setCellValue(formatTotalScoreForSubmitter(record));
            }

            // ========== 空行和注脚 ==========
            rowIdx += 3;
            org.apache.poi.ss.usermodel.Row noteRow = sheet.createRow(rowIdx);
            org.apache.poi.ss.usermodel.Cell noteCell = noteRow.createCell(0);
            noteCell.setCellValue("注： 发表论文: 一级 12分/篇；二级 9分/篇；三级 6分/篇；四级 3分/篇    \n 两人完成，按7:3分配；三人完成，按6:2:2分配；四人及以上完成，主持者分配 50%；参与者平均分配 50%");
            noteCell.setCellStyle(noteStyle);
            sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(rowIdx, rowIdx, 0, colCount - 1));

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
     * 格式化作者及得分为显示字符串
     * 格式：教师名得分、教师名得分...
     */
    private String formatAuthorsScores(String authorsData, String thesisLevel) {
        if (StrUtil.isBlank(authorsData)) return "";
        try {
            JSONArray arr = new JSONArray(authorsData);
            int authorCount = arr.size();
            if (authorCount == 0) return "";

            BigDecimal totalScore = ThesisScoringConstants.calcThesisScore(thesisLevel);
            if (totalScore.compareTo(BigDecimal.ZERO) <= 0) return "";

            int firstAuthorIndex = -1;
            for (int i = 0; i < arr.size(); i++) {
                JSONObject entry = arr.getJSONObject(i);
                if (entry.getBool("isFirstAuthor", false)) {
                    firstAuthorIndex = i;
                    break;
                }
            }

            List<BigDecimal> distributed = ThesisScoringConstants.distributeScore(totalScore, authorCount, firstAuthorIndex);

            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < arr.size(); i++) {
                if (i > 0) sb.append("，");
                JSONObject entry = arr.getJSONObject(i);
                String name = entry.getStr("teacherName", "");
                sb.append(name);
                BigDecimal score = distributed.get(i);
                String scoreStr = score.stripTrailingZeros().toPlainString();
                sb.append(scoreStr);
            }
            return sb.toString();
        } catch (Exception e) {
            log.error("格式化作者得分失败: {}", authorsData, e);
            return authorsData;
        }
    }

    /**
     * 获取提交人姓名
     */
    private String getSubmitterName(Long userId) {
        if (userId == null) return "";
        User user = userMapper.selectOneById(userId);
        return user != null && StrUtil.isNotBlank(user.getUserName()) ? user.getUserName() : "";
    }

    /**
     * 格式化为提交人在该论文中的得分
     */
    private String formatTotalScoreForSubmitter(ThesisRecord record) {
        if (StrUtil.isBlank(record.getAuthorsData())) return "";
        try {
            JSONArray arr = new JSONArray(record.getAuthorsData());
            int authorCount = arr.size();
            if (authorCount == 0) return "";

            BigDecimal totalScore = ThesisScoringConstants.calcThesisScore(record.getThesisLevel());
            if (totalScore.compareTo(BigDecimal.ZERO) <= 0) return "";

            int firstAuthorIndex = -1;
            for (int i = 0; i < arr.size(); i++) {
                JSONObject entry = arr.getJSONObject(i);
                if (entry.getBool("isFirstAuthor", false)) {
                    firstAuthorIndex = i;
                    break;
                }
            }

            List<BigDecimal> distributed = ThesisScoringConstants.distributeScore(totalScore, authorCount, firstAuthorIndex);

            // 查找提交人在作者列表中的位置
            String submitterName = getSubmitterName(record.getUserId());
            BigDecimal submitterTotal = BigDecimal.ZERO;
            for (int i = 0; i < arr.size(); i++) {
                JSONObject entry = arr.getJSONObject(i);
                String name = entry.getStr("teacherName", "");
                if (submitterName.equals(name)) {
                    submitterTotal = submitterTotal.add(distributed.get(i));
                }
            }
            if (submitterTotal.compareTo(BigDecimal.ZERO) > 0) {
                return submitterTotal.stripTrailingZeros().toPlainString();
            }
            return "";
        } catch (Exception e) {
            log.error("格式化提交人得分失败", e);
            return "";
        }
    }
}
