package com.jgh.ghairouter.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import com.jgh.ghairouter.exception.BusinessException;
import com.jgh.ghairouter.exception.ErrorCode;
import com.jgh.ghairouter.mapper.*;
import com.jgh.ghairouter.model.constants.SportsScoringConstants;
import com.jgh.ghairouter.model.dto.competition.SportsEventQueryRequest;
import com.jgh.ghairouter.model.entity.*;
import com.jgh.ghairouter.model.enums.ReviewStatusEnum;
import com.jgh.ghairouter.model.vo.SportsEventRecordVO;
import com.jgh.ghairouter.model.vo.SportsEventScoreVO;
import com.jgh.ghairouter.service.AiReviewService;
import com.jgh.ghairouter.service.SportsEventRecordService;
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
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.Year;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 体育比赛业绩记录服务实现（v9）
 */
@Slf4j
@Service
public class SportsEventRecordServiceImpl
        extends ServiceImpl<SportsEventRecordMapper, SportsEventRecord>
        implements SportsEventRecordService {

    @Resource
    private AiReviewService aiReviewService;
    @Resource
    private UserMapper userMapper;
    @Resource
    private TeacherCompetitionAuditRecordMapper auditMapper;
    @Resource
    private SportsEventScoreMapper sportsScoreMapper;

    private static final String DEFAULT_TYPE_NAME = "体育比赛业绩";

    @Transactional(rollbackFor = Exception.class)
    @Override
    public Long addRecord(Long userId,
                          String eventName,
                          String eventType,
                          String eventResult,
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

        SportsEventRecord record = new SportsEventRecord();
        record.setUserId(userId);
        record.setTypeName(DEFAULT_TYPE_NAME);
        record.setEventName(eventName);
        record.setEventType(eventType);
        record.setEventResult(eventResult);
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
                aiReviewService.autoReview(record.getId(), record.getTypeName(), eventName, base64, mimeType);
            } catch (Exception e) {
                log.error("AI审核触发失败", e);
            }
        }
        return record.getId();
    }

    // ==================== 保存得分明细 ====================

    private void saveSportsScores(Long recordId, String memberData, String eventType, String eventResult) {
        if (StrUtil.isBlank(memberData)) return;

        LocalDateTime now = LocalDateTime.now();
        try {
            JSONArray arr = new JSONArray(memberData);
            if (arr.size() == 0) return;

            for (int i = 0; i < arr.size(); i++) {
                JSONObject entry = arr.getJSONObject(i);
                String teacherName = entry.getStr("teacherName");
                if (StrUtil.isBlank(teacherName)) continue;

                BigDecimal score;
                Integer placement = null;

                if (SportsScoringConstants.EVENT_TYPE_TRACK_FIELD.equals(eventType)) {
                    // 运动会项目：按名次计算
                    placement = entry.getInt("placement");
                    // JSONObject.getInt may return 0 for null
                    if (placement != null && placement == 0 && !entry.containsKey("placement")) {
                        placement = null;
                    }
                    score = SportsScoringConstants.calcTrackFieldScore(
                            (placement != null && placement > 0) ? placement : null);
                } else {
                    // 球类项目：按结果计算
                    score = SportsScoringConstants.calcBallGameScore(eventResult);
                }

                SportsEventScore scoreEntity = new SportsEventScore();
                scoreEntity.setRecordId(recordId);
                scoreEntity.setTeacherName(teacherName);
                scoreEntity.setScore(score);

                if (entry.containsKey("userId")) {
                    scoreEntity.setUserId(entry.getLong("userId"));
                }
                if (scoreEntity.getUserId() == null) {
                    User teacher = userMapper.selectOneByQuery(
                            QueryWrapper.create().eq("user_name", teacherName));
                    if (teacher != null) {
                        scoreEntity.setUserId(teacher.getId());
                    }
                }

                scoreEntity.setCreateTime(now);
                scoreEntity.setUpdateTime(now);
                sportsScoreMapper.insert(scoreEntity);
            }
        } catch (Exception e) {
            log.error("解析成员数据JSON失败: {}", memberData, e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "保存得分失败");
        }
    }

    // ==================== 查询 ====================

    @Override
    public QueryWrapper getQueryWrapper(SportsEventQueryRequest req) {
        if (req == null) throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        QueryWrapper wrapper = QueryWrapper.create()
                .eq("id", req.getId())
                .eq("user_id", req.getUserId())
                .eq("event_type", req.getEventType())
                .like("event_name", req.getEventName());
        if (StrUtil.isNotBlank(req.getSortField())) {
            wrapper.orderBy(req.getSortField(), "ascend".equals(req.getSortOrder()));
        }
        wrapper.orderBy("create_time", false);
        return wrapper;
    }

    @Override
    public SportsEventRecordVO getRecordVO(SportsEventRecord record) {
        if (record == null) return null;
        SportsEventRecordVO vo = new SportsEventRecordVO();
        BeanUtil.copyProperties(record, vo);

        // 项目类型显示名
        vo.setEventTypeText(SportsScoringConstants.getEventTypeText(record.getEventType()));
        // 球类结果显示名
        vo.setEventResultText(SportsScoringConstants.getEventResultText(record.getEventResult()));

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
        List<SportsEventScore> scoreList = sportsScoreMapper.selectListByQuery(
                QueryWrapper.create().eq("record_id", record.getId()));
        if (CollUtil.isNotEmpty(scoreList)) {
            vo.setScores(scoreList.stream()
                    .map(s -> SportsEventScoreVO.builder()
                            .userId(s.getUserId())
                            .teacherName(s.getTeacherName())
                            .score(s.getScore())
                            .build())
                    .collect(Collectors.toList()));
        }

        return vo;
    }

    @Override
    public Page<SportsEventRecordVO> pageRecords(SportsEventQueryRequest req) {
        long pageNum = req.getPageNum();
        long pageSize = req.getPageSize();

        QueryWrapper wrapper = getQueryWrapper(req);
        if (StrUtil.isNotBlank(req.getAdminReviewStatus())) {
            Page<SportsEventRecord> recordPage = this.page(Page.of(pageNum, pageSize), wrapper);
            List<SportsEventRecordVO> voList = recordPage.getRecords().stream()
                    .map(this::getRecordVO)
                    .filter(vo -> req.getAdminReviewStatus().equals(vo.getAdminReviewStatus()))
                    .collect(Collectors.toList());
            Page<SportsEventRecordVO> voPage = new Page<>(pageNum, pageSize, recordPage.getTotalRow());
            voPage.setRecords(voList);
            return voPage;
        }
        Page<SportsEventRecord> recordPage = this.page(Page.of(pageNum, pageSize), wrapper);
        List<SportsEventRecordVO> voList = recordPage.getRecords().stream()
                .map(this::getRecordVO)
                .collect(Collectors.toList());
        Page<SportsEventRecordVO> voPage = new Page<>(pageNum, pageSize, recordPage.getTotalRow());
        voPage.setRecords(voList);
        return voPage;
    }

    @Override
    public Page<SportsEventRecordVO> pageMyRelatedRecords(Long userId, SportsEventQueryRequest req) {
        long pageNum = req.getPageNum();
        long pageSize = req.getPageSize();

        // 提交人 OR 在得分表中被分配了得分的参与者，均可看到记录
        QueryWrapper wrapper = QueryWrapper.create()
                .eq("id", req.getId())
                .eq("event_type", req.getEventType())
                .like("event_name", req.getEventName())
                .where("(user_id = ? OR id IN (SELECT record_id FROM sports_event_score WHERE user_id = ? AND is_delete = 0))",
                       userId, userId);
        wrapper.orderBy("create_time", false);

        Page<SportsEventRecord> recordPage = this.page(Page.of(pageNum, pageSize), wrapper);
        List<SportsEventRecordVO> voList = recordPage.getRecords().stream()
                .map(record -> {
                    SportsEventRecordVO vo = getRecordVO(record);
                    if (vo.getScores() != null) {
                        BigDecimal myTotal = vo.getScores().stream()
                                .filter(s -> userId.equals(s.getUserId()))
                                .map(SportsEventScoreVO::getScore)
                                .reduce(BigDecimal.ZERO, BigDecimal::add);
                        if (myTotal.compareTo(BigDecimal.ZERO) > 0) {
                            vo.setMyScoreDisplay(myTotal.stripTrailingZeros().toPlainString());
                        }
                    }
                    return vo;
                })
                .collect(Collectors.toList());
        Page<SportsEventRecordVO> voPage = new Page<>(pageNum, pageSize, recordPage.getTotalRow());
        voPage.setRecords(voList);
        return voPage;
    }

    @Override
    public BigDecimal getMyTotalScore(Long userId) {
        List<SportsEventScore> scores = sportsScoreMapper.selectListByQuery(
                QueryWrapper.create().eq("user_id", userId));
        if (CollUtil.isEmpty(scores)) return BigDecimal.ZERO;
        return scores.stream()
                .map(SportsEventScore::getScore)
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

        SportsEventRecord record = this.getById(recordId);
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
            sportsScoreMapper.deleteByQuery(
                    QueryWrapper.create().eq("record_id", recordId));

            saveSportsScores(recordId, record.getMemberData(), record.getEventType(), record.getEventResult());
        }
    }

    // ==================== 导出Excel ====================

    @Override
    public byte[] exportRecordsToExcel() {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {

            // 样式
            CellStyle titleStyle = workbook.createCellStyle();
            Font titleFont = workbook.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 14);
            titleStyle.setFont(titleFont);
            titleStyle.setAlignment(HorizontalAlignment.CENTER);
            titleStyle.setVerticalAlignment(VerticalAlignment.CENTER);

            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);

            CellStyle dataStyle = workbook.createCellStyle();
            dataStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            dataStyle.setWrapText(true);

            CellStyle noteStyle = workbook.createCellStyle();
            noteStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            noteStyle.setWrapText(true);

            Sheet sheet = workbook.createSheet("8-体育比赛业绩");

            // 设置列宽
            sheet.setColumnWidth(0, 20 * 256);  // A: 比赛项目
            sheet.setColumnWidth(1, 3 * 256);   // B: 空
            sheet.setColumnWidth(2, 55 * 256);  // C: 选手及成绩
            sheet.setColumnWidth(3, 3 * 256);   // D: 空
            sheet.setColumnWidth(4, 3 * 256);   // E: 空
            sheet.setColumnWidth(5, 3 * 256);   // F: 空
            sheet.setColumnWidth(6, 12 * 256);  // G: 姓名
            sheet.setColumnWidth(7, 10 * 256);  // H: 业绩分

            // 获取所有审核通过的记录
            List<SportsEventRecord> allRecords = this.list(
                    QueryWrapper.create().orderBy("create_time", true));

            // 获取所有得分记录
            List<SportsEventScore> allScores = sportsScoreMapper.selectListByQuery(
                    QueryWrapper.create().orderBy("score", false));

            // 按人汇总得分（右侧表格数据）
            Map<String, BigDecimal> personScores = new LinkedHashMap<>();
            for (SportsEventScore score : allScores) {
                String name = score.getTeacherName();
                personScores.merge(name, score.getScore(), BigDecimal::add);
            }

            // 按总分降序排序
            List<Map.Entry<String, BigDecimal>> sortedPersons = new ArrayList<>(personScores.entrySet());
            sortedPersons.sort((a, b) -> b.getValue().compareTo(a.getValue()));

            int rowIdx = 0;

            // ========== 标题行 ==========
            Row titleRow = sheet.createRow(rowIdx++);
            Cell titleCell = titleRow.createCell(0);
            String year = String.valueOf(Year.now().getValue());
            titleCell.setCellValue(year + "年参加院体育比赛汇总");
            titleCell.setCellStyle(titleStyle);
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 7));  // A1:H1

            // ========== 表头行 ==========
            Row headerRow = sheet.createRow(rowIdx++);
            Cell h0 = headerRow.createCell(0);
            h0.setCellValue("比赛项目");
            h0.setCellStyle(headerStyle);
            // B,C,D,E are empty
            Cell h5 = headerRow.createCell(5); // F column - empty visually
            Cell h6 = headerRow.createCell(6);
            h6.setCellValue("姓名");
            h6.setCellStyle(headerStyle);
            Cell h7 = headerRow.createCell(7);
            h7.setCellValue("业绩分");
            h7.setCellStyle(headerStyle);

            // ========== 数据行 ==========
            int dataStartRow = rowIdx;

            for (SportsEventRecord record : allRecords) {
                Row row = sheet.createRow(rowIdx++);

                // A: 比赛项目
                Cell cellA = row.createCell(0);
                cellA.setCellValue(record.getEventName() != null ? record.getEventName() : "");
                cellA.setCellStyle(dataStyle);

                // C: 选手及成绩
                Cell cellC = row.createCell(2);
                cellC.setCellValue(formatParticipants(record));
                cellC.setCellStyle(dataStyle);
            }

            int dataEndRow = rowIdx - 1;

            // ========== 右侧：个人得分汇总 ==========
            int personRow = dataStartRow;
            for (Map.Entry<String, BigDecimal> entry : sortedPersons) {
                Row row;
                if (personRow <= dataEndRow) {
                    row = sheet.getRow(personRow);
                } else {
                    row = sheet.createRow(personRow);
                }
                if (row == null) {
                    row = sheet.createRow(personRow);
                }

                // G: 姓名
                Cell cellG = row.createCell(6);
                cellG.setCellValue(entry.getKey());
                cellG.setCellStyle(dataStyle);

                // H: 业绩分
                Cell cellH = row.createCell(7);
                cellH.setCellValue(entry.getValue().stripTrailingZeros().toPlainString());
                cellH.setCellStyle(dataStyle);

                personRow++;
            }

            // 确保 rowIdx 覆盖了所有数据行
            rowIdx = Math.max(rowIdx, personRow);

            // 空行
            rowIdx++;

            // ========== 注释行 ==========
            Row noteRow1 = sheet.createRow(rowIdx++);
            Cell noteCell1 = noteRow1.createCell(0);
            noteCell1.setCellValue("注");
            noteCell1.setCellStyle(noteStyle);
            Row noteRow2 = sheet.createRow(rowIdx++);
            Cell noteCell2 = noteRow2.createCell(0);
            noteCell2.setCellValue("参加运动会及球类项目：1分/项类");
            noteCell2.setCellStyle(noteStyle);
            Row noteRow3 = sheet.createRow(rowIdx++);
            Cell noteCell3 = noteRow3.createCell(0);
            noteCell3.setCellValue("获奖增加：运动会前四名 1分/项；后四名 0.5分/项；球类 1.5分/项");
            noteCell3.setCellStyle(noteStyle);

            // 设置行高
            for (int i = 0; i < rowIdx; i++) {
                Row r = sheet.getRow(i);
                if (r != null) {
                    r.setHeight((short) (22 * 20)); // 22pt
                }
            }
            titleRow.setHeight((short) (30 * 20));

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            workbook.write(bos);
            return bos.toByteArray();
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "Excel生成失败: " + e.getMessage());
        }
    }

    /**
     * 格式化参与者信息为显示字符串
     * 格式：姓名第N名(得分)、姓名(得分)...
     * 如：覃伟峰第2名(2)、薛航第7名(1.5)、郑可意(1)
     */
    private String formatParticipants(SportsEventRecord record) {
        if (StrUtil.isBlank(record.getMemberData())) return "";

        try {
            JSONArray arr = new JSONArray(record.getMemberData());
            if (arr.size() == 0) return "";

            StringBuilder sb = new StringBuilder();

            if (SportsScoringConstants.EVENT_TYPE_BALL_GAME.equals(record.getEventType())) {
                // 球类项目：列出所有参与者，得分一致
                BigDecimal perScore = SportsScoringConstants.calcBallGameScore(record.getEventResult());
                String scoreStr = perScore.stripTrailingZeros().toPlainString();

                for (int i = 0; i < arr.size(); i++) {
                    if (i > 0) sb.append("、");
                    JSONObject entry = arr.getJSONObject(i);
                    sb.append(entry.getStr("teacherName", ""));
                }

                // 追加球类结果说明
                String resultText = SportsScoringConstants.getEventResultText(record.getEventResult());
                if (StrUtil.isNotBlank(resultText)) {
                    sb.append("（").append(resultText);
                    sb.append("，").append(scoreStr).append("）");
                }
            } else {
                // 运动会项目：每人独立得分
                for (int i = 0; i < arr.size(); i++) {
                    if (i > 0) sb.append("、");
                    JSONObject entry = arr.getJSONObject(i);
                    String name = entry.getStr("teacherName", "");
                    sb.append(name);

                    Integer placement = entry.getInt("placement");
                    if (placement != null && placement > 0) {
                        sb.append("第").append(placement).append("名");
                    }

                    BigDecimal score = SportsScoringConstants.calcTrackFieldScore(
                            (placement != null && placement > 0) ? placement : null);
                    sb.append("(").append(score.stripTrailingZeros().toPlainString()).append(")");
                }
            }

            return sb.toString();
        } catch (Exception e) {
            log.error("格式化参与者信息失败: {}", record.getMemberData(), e);
            return record.getMemberData();
        }
    }
}
