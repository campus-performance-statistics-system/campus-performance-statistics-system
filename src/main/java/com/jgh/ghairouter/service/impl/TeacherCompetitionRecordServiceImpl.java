package com.jgh.ghairouter.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.jgh.ghairouter.exception.BusinessException;
import com.jgh.ghairouter.exception.ErrorCode;
import com.jgh.ghairouter.mapper.*;
import com.jgh.ghairouter.model.dto.competition.CompetitionQueryRequest;
import com.jgh.ghairouter.model.entity.*;
import com.jgh.ghairouter.model.enums.ReviewStatusEnum;
import com.jgh.ghairouter.model.vo.CompetitionRecordVO;
import com.jgh.ghairouter.model.vo.TeacherScoreVO;
import com.jgh.ghairouter.service.AiReviewService;
import com.jgh.ghairouter.service.TeacherCompetitionRecordService;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 教师比赛记录服务实现（v2 重构）。
 * 分数分配规则（硬编码）：
 * - 单人：100%
 * - 两人：负责人70%，另一人30%
 * - 三人：负责人60%，其余两人各20%
 * - 四人及以上：负责人50%，其余人平分50%
 * - 未获奖：只有负责人得分
 */
@Slf4j
@Service
public class TeacherCompetitionRecordServiceImpl
        extends ServiceImpl<TeacherCompetitionRecordMapper, TeacherCompetitionRecord>
        implements TeacherCompetitionRecordService {

    @Resource
    private AiReviewService aiReviewService;
    @Resource
    private UserMapper userMapper;
    @Resource
    private TeacherCompetitionAuditRecordMapper auditMapper;
    @Resource
    private TeacherCompetitionScoreMapper teacherScoreMapper;

    // ==================== 分数分配规则（硬编码） ====================

    /** 两人：负责人70%，成员30% */
    private static final BigDecimal RATIO_2_LEADER = new BigDecimal("0.70");
    private static final BigDecimal RATIO_2_MEMBER = new BigDecimal("0.30");

    /** 三人：负责人60%，成员各20% */
    private static final BigDecimal RATIO_3_LEADER = new BigDecimal("0.60");
    private static final BigDecimal RATIO_3_MEMBER = new BigDecimal("0.20");

    /** 四人及以上：负责人50%，其余平分50% */
    private static final BigDecimal RATIO_N_LEADER = new BigDecimal("0.50");
    private static final BigDecimal RATIO_N_REST = new BigDecimal("0.50");

    /** 默认记录类型 */
    private static final String DEFAULT_TYPE_NAME = "教师获奖";

    // ==================== 用户提交比赛记录 ====================

    @Override
    public Long addRecord(Long userId, String typeName,
                          String competitionName, String sponsorUnit,
                          String competitionRank, String gradeName, BigDecimal baseScore,
                          Integer teamMemberNum, Long firstAuthorId,
                          List<Long> otherAuthorIds, MultipartFile file) {
        int memberNum = teamMemberNum != null && teamMemberNum > 0 ? teamMemberNum : 1;

        // 读取文件 base64
        String base64;
        try {
            base64 = file != null && !file.isEmpty()
                    ? Base64.getEncoder().encodeToString(file.getBytes()) : null;
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "读取文件失败");
        }

        // 构建记录
        TeacherCompetitionRecord record = new TeacherCompetitionRecord();
        record.setUserId(userId);
        record.setTypeName(StrUtil.isBlank(typeName) ? DEFAULT_TYPE_NAME : typeName);
        record.setCompetitionName(competitionName);
        record.setSponsorUnit(sponsorUnit);
        record.setCompetitionRank(competitionRank);
        record.setGradeName(gradeName);
        record.setBaseScore(baseScore);
        record.setTeamMemberNum(memberNum);
        record.setFirstAuthorId(firstAuthorId);
        if (CollUtil.isNotEmpty(otherAuthorIds)) {
            record.setOtherAuthorIds(otherAuthorIds.stream()
                    .map(String::valueOf).collect(Collectors.joining(",")));
        }
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
                aiReviewService.autoReview(record.getId(), competitionName, base64, mimeType);
            } catch (Exception e) {
                log.error("AI审核触发失败", e);
            }
        }
        return record.getId();
    }

    // ==================== 管理员添加比赛记录 ====================

    @Override
    public Long adminAddRecord(Long adminId, String typeName,
                                String competitionName, String sponsorUnit,
                                String competitionRank, String gradeName, BigDecimal baseScore,
                                Integer teamMemberNum, Long firstAuthorId,
                                List<Long> otherAuthorIds, MultipartFile file) {
        int memberNum = teamMemberNum != null && teamMemberNum > 0 ? teamMemberNum : 1;

        String base64 = null;
        if (file != null && !file.isEmpty()) {
            try {
                base64 = Base64.getEncoder().encodeToString(file.getBytes());
            } catch (IOException e) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "读取文件失败");
            }
        }

        TeacherCompetitionRecord record = new TeacherCompetitionRecord();
        record.setUserId(adminId);
        record.setTypeName(StrUtil.isBlank(typeName) ? DEFAULT_TYPE_NAME : typeName);
        record.setCompetitionName(competitionName);
        record.setSponsorUnit(sponsorUnit);
        record.setCompetitionRank(competitionRank);
        record.setGradeName(gradeName);
        record.setBaseScore(baseScore);
        record.setTeamMemberNum(memberNum);
        record.setFirstAuthorId(firstAuthorId);
        if (CollUtil.isNotEmpty(otherAuthorIds)) {
            record.setOtherAuthorIds(otherAuthorIds.stream()
                    .map(String::valueOf).collect(Collectors.joining(",")));
        }
        record.setProofImageData(base64);

        boolean saved = this.save(record);
        if (!saved) throw new BusinessException(ErrorCode.OPERATION_ERROR, "添加失败");

        // 创建审核记录（管理员直接通过）
        TeacherCompetitionAuditRecord audit = new TeacherCompetitionAuditRecord();
        audit.setRecordId(record.getId());
        audit.setRecordType(record.getTypeName());
        audit.setAutoReviewStatus(ReviewStatusEnum.PASSED.getValue());
        audit.setAdminReviewStatus(ReviewStatusEnum.PASSED.getValue());
        audit.setAdminId(adminId);
        LocalDateTime auditNow = LocalDateTime.now();
        audit.setAdminReviewTime(auditNow);
        audit.setAdminReviewComment("管理员直接录入");
        audit.setCreateTime(auditNow);
        audit.setUpdateTime(auditNow);
        auditMapper.insert(audit);

        // 计算并保存个人得分明细
        if ("未获奖".equals(gradeName)) {
            saveNoAwardScores(record, baseScore, firstAuthorId, otherAuthorIds);
        } else {
            saveTeacherScores(record, baseScore, memberNum, firstAuthorId, otherAuthorIds);
        }

        return record.getId();
    }

    // ==================== 分数计算 ====================

    /**
     * 保存教师得分明细。
     * @param baseScore 负责人总得分（基础2分 + 获奖加分），由前端 getLeaderTotalScore 计算提交
     */
    private void saveTeacherScores(TeacherCompetitionRecord record, BigDecimal baseScore,
                                    int memberNum, Long firstAuthorId,
                                    List<Long> otherAuthorIds) {
        List<TeacherCompetitionScore> scores = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        final BigDecimal LEADER_BASE = new BigDecimal("2");

        // 提取获奖加分部分：baseScore = LEADER_BASE + actualBonus
        BigDecimal actualBonus = baseScore.subtract(LEADER_BASE);
        if (actualBonus.compareTo(BigDecimal.ZERO) < 0) {
            actualBonus = BigDecimal.ZERO;
        }

        if (memberNum == 1) {
            // 单人：负责人获得全部 = 基础2分 + 加分
            scores.add(buildScore(record.getId(), firstAuthorId, baseScore, 1, now));
        } else if (memberNum == 2) {
            // 两人：负责人70%，另一人30%
            scores.add(buildScore(record.getId(), firstAuthorId,
                    LEADER_BASE.add(actualBonus.multiply(RATIO_2_LEADER)).setScale(3, RoundingMode.HALF_UP), 1, now));
            if (CollUtil.isNotEmpty(otherAuthorIds) && !otherAuthorIds.get(0).equals(firstAuthorId)) {
                scores.add(buildScore(record.getId(), otherAuthorIds.get(0),
                        actualBonus.multiply(RATIO_2_MEMBER).setScale(3, RoundingMode.HALF_UP), 0, now));
            }
        } else if (memberNum == 3) {
            // 三人：负责人60%，其余两人各20%
            scores.add(buildScore(record.getId(), firstAuthorId,
                    LEADER_BASE.add(actualBonus.multiply(RATIO_3_LEADER)).setScale(3, RoundingMode.HALF_UP), 1, now));
            BigDecimal perMember = actualBonus.multiply(RATIO_3_MEMBER).setScale(3, RoundingMode.HALF_UP);
            if (CollUtil.isNotEmpty(otherAuthorIds)) {
                for (Long otherId : otherAuthorIds) {
                    if (!otherId.equals(firstAuthorId)) {
                        scores.add(buildScore(record.getId(), otherId, perMember, 0, now));
                    }
                }
            }
        } else {
            // 四人及以上：负责人50%，其余人平分50%
            scores.add(buildScore(record.getId(), firstAuthorId,
                    LEADER_BASE.add(actualBonus.multiply(RATIO_N_LEADER)).setScale(3, RoundingMode.HALF_UP), 1, now));
            int otherCount = memberNum - 1;
            if (otherCount > 0 && CollUtil.isNotEmpty(otherAuthorIds)) {
                BigDecimal restBonus = actualBonus.multiply(RATIO_N_REST);
                BigDecimal perMember = restBonus.divide(BigDecimal.valueOf(otherCount), 3, RoundingMode.HALF_UP);
                for (Long otherId : otherAuthorIds) {
                    if (!otherId.equals(firstAuthorId)) {
                        scores.add(buildScore(record.getId(), otherId, perMember, 0, now));
                    }
                }
            }
        }

        for (TeacherCompetitionScore ts : scores) {
            teacherScoreMapper.insert(ts);
        }
    }

    private void saveNoAwardScores(TeacherCompetitionRecord record, BigDecimal baseScore,
                                    Long firstAuthorId, List<Long> otherAuthorIds) {
        LocalDateTime now = LocalDateTime.now();
        teacherScoreMapper.insert(buildScore(record.getId(), firstAuthorId, new BigDecimal("2"), 1, now));
        if (CollUtil.isNotEmpty(otherAuthorIds)) {
            for (Long otherId : otherAuthorIds) {
                if (!otherId.equals(firstAuthorId)) {
                    teacherScoreMapper.insert(buildScore(record.getId(), otherId, BigDecimal.ZERO, 0, now));
                }
            }
        }
    }

    private TeacherCompetitionScore buildScore(Long recordId, Long userId,
                                                BigDecimal score, int isLeader, LocalDateTime now) {
        TeacherCompetitionScore ts = new TeacherCompetitionScore();
        ts.setRecordId(recordId);
        ts.setTeacherUserId(userId);
        ts.setPersonalScore(score);
        ts.setIsLeader(isLeader);
        ts.setCreateTime(now);
        ts.setUpdateTime(now);
        return ts;
    }

    // ==================== 查询 ====================

    @Override
    public QueryWrapper getQueryWrapper(CompetitionQueryRequest req) {
        if (req == null) throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        QueryWrapper wrapper = QueryWrapper.create()
                .eq("id", req.getId())
                .eq("user_id", req.getUserId())
                .eq("type_name", req.getTypeName())
                .like("competition_name", req.getCompetitionName());
        if (StrUtil.isNotBlank(req.getSortField())) {
            wrapper.orderBy(req.getSortField(), "ascend".equals(req.getSortOrder()));
        }
        wrapper.orderBy("create_time", false);
        return wrapper;
    }

    @Override
    public CompetitionRecordVO getRecordVO(TeacherCompetitionRecord record) {
        if (record == null) return null;
        CompetitionRecordVO vo = new CompetitionRecordVO();
        BeanUtil.copyProperties(record, vo);

        if (record.getUserId() != null) {
            User u = userMapper.selectOneById(record.getUserId());
            if (u != null) vo.setUserName(u.getUserName());
        }
        if (record.getFirstAuthorId() != null) {
            User leader = userMapper.selectOneById(record.getFirstAuthorId());
            if (leader != null) vo.setFirstAuthorName(leader.getUserName());
        }
        if (StrUtil.isNotBlank(record.getOtherAuthorIds())) {
            List<String> names = Arrays.stream(record.getOtherAuthorIds().split(","))
                    .filter(StrUtil::isNotBlank)
                    .map(idStr -> {
                        User u = userMapper.selectOneById(Long.valueOf(idStr.trim()));
                        return u != null ? u.getUserName() : idStr;
                    }).collect(Collectors.toList());
            vo.setOtherAuthorNames(String.join("、", names));
        }

        // 从审计表获取审核信息
        TeacherCompetitionAuditRecord audit = auditMapper.selectOneByQuery(
                QueryWrapper.create().eq("record_id", record.getId()));
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

        // 查询得分明细
        List<TeacherCompetitionScore> scoreList = teacherScoreMapper.selectListByQuery(
                QueryWrapper.create().eq("record_id", record.getId()));
        if (CollUtil.isNotEmpty(scoreList)) {
            List<TeacherScoreVO> scoreVOs = scoreList.stream()
                    .map(ts -> {
                        User u = userMapper.selectOneById(ts.getTeacherUserId());
                        return TeacherScoreVO.builder()
                                .userId(ts.getTeacherUserId())
                                .userName(u != null ? u.getUserName() : String.valueOf(ts.getTeacherUserId()))
                                .personalScore(ts.getPersonalScore())
                                .isLeader(ts.getIsLeader())
                                .build();
                    })
                    .collect(Collectors.toList());
            vo.setTeacherScores(scoreVOs);
        }
        return vo;
    }

    @Override
    public Page<CompetitionRecordVO> pageRecords(CompetitionQueryRequest req) {
        long pageNum = req.getPageNum();
        long pageSize = req.getPageSize();
        // 如果是管理员查询，需要通过 join 过滤审核状态
        QueryWrapper wrapper = getQueryWrapper(req);
        if (StrUtil.isNotBlank(req.getAdminReviewStatus())) {
            // 需要关联审计表过滤
            Page<TeacherCompetitionRecord> recordPage = this.page(Page.of(pageNum, pageSize), wrapper);
            List<CompetitionRecordVO> voList = recordPage.getRecords().stream()
                    .map(this::getRecordVO)
                    .filter(vo -> req.getAdminReviewStatus().equals(vo.getAdminReviewStatus()))
                    .collect(Collectors.toList());
            Page<CompetitionRecordVO> voPage = new Page<>(pageNum, pageSize, recordPage.getTotalRow());
            voPage.setRecords(voList);
            return voPage;
        }
        Page<TeacherCompetitionRecord> recordPage = this.page(Page.of(pageNum, pageSize), wrapper);
        List<CompetitionRecordVO> voList = recordPage.getRecords().stream()
                .map(this::getRecordVO)
                .collect(Collectors.toList());
        Page<CompetitionRecordVO> voPage = new Page<>(pageNum, pageSize, recordPage.getTotalRow());
        voPage.setRecords(voList);
        return voPage;
    }

    @Override
    public Page<CompetitionRecordVO> pageMyRelatedRecords(Long userId, CompetitionQueryRequest req) {
        long pageNum = req.getPageNum();
        long pageSize = req.getPageSize();

        QueryWrapper wrapper = QueryWrapper.create()
                .eq("id", req.getId())
                .eq("type_name", req.getTypeName())
                .like("competition_name", req.getCompetitionName())
                .where("(user_id = ? OR first_author_id = ? OR other_author_ids LIKE ?)",
                        userId, userId, "%" + userId + "%");
        wrapper.orderBy("create_time", false);

        Page<TeacherCompetitionRecord> recordPage = this.page(Page.of(pageNum, pageSize), wrapper);
        List<CompetitionRecordVO> voList = recordPage.getRecords().stream()
                .map(record -> {
                    CompetitionRecordVO vo = getRecordVO(record);
                    if (vo.getTeacherScores() != null) {
                        vo.getTeacherScores().stream()
                                .filter(ts -> userId.equals(ts.getUserId()))
                                .findFirst()
                                .ifPresent(ts -> vo.setMyScore(ts.getPersonalScore()));
                    }
                    return vo;
                })
                .collect(Collectors.toList());
        Page<CompetitionRecordVO> voPage = new Page<>(pageNum, pageSize, recordPage.getTotalRow());
        voPage.setRecords(voList);
        return voPage;
    }

    @Override
    public BigDecimal getMyTotalScore(Long userId) {
        List<TeacherCompetitionScore> scores = teacherScoreMapper.selectListByQuery(
                QueryWrapper.create().eq("user_id", userId));
        if (CollUtil.isEmpty(scores)) return BigDecimal.ZERO;
        return scores.stream()
                .map(TeacherCompetitionScore::getPersonalScore)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
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

        TeacherCompetitionRecord record = this.getById(recordId);
        if (record == null) throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "比赛记录不存在");

        TeacherCompetitionAuditRecord audit = auditMapper.selectOneByQuery(
                QueryWrapper.create().eq("record_id", recordId));
        if (audit == null) throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "审核记录不存在");

        if (statusEnum == ReviewStatusEnum.PASSED && StrUtil.isBlank(reviewComment))
            reviewComment = "审核通过";

        audit.setAdminReviewStatus(reviewStatus);
        audit.setAdminReviewComment(reviewComment);
        audit.setAdminId(adminId);
        audit.setAdminReviewTime(LocalDateTime.now());

        if (auditMapper.update(audit) <= 0)
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "审核失败");

        // 审核通过后计算并保存个人得分
        if (statusEnum == ReviewStatusEnum.PASSED) {
            teacherScoreMapper.deleteByQuery(
                    QueryWrapper.create().eq("record_id", recordId));

            List<Long> otherAuthorIds = new ArrayList<>();
            if (StrUtil.isNotBlank(record.getOtherAuthorIds())) {
                for (String idStr : record.getOtherAuthorIds().split(",")) {
                    if (StrUtil.isNotBlank(idStr.trim())) {
                        otherAuthorIds.add(Long.valueOf(idStr.trim()));
                    }
                }
            }

            int memberNum = record.getTeamMemberNum() != null && record.getTeamMemberNum() > 0
                    ? record.getTeamMemberNum() : 1;
            BigDecimal score = record.getBaseScore() != null
                    ? record.getBaseScore() : BigDecimal.ZERO;

            if ("未获奖".equals(record.getGradeName())) {
                saveNoAwardScores(record, score, record.getFirstAuthorId(), otherAuthorIds);
            } else {
                saveTeacherScores(record, score, memberNum, record.getFirstAuthorId(), otherAuthorIds);
            }
        }
    }

    // ==================== 导出Excel ====================

    @Override
    public byte[] exportRecordsToExcel() {
        List<TeacherCompetitionRecord> records = this.list(QueryWrapper.create().orderBy("create_time", true));

        try (org.apache.poi.xssf.usermodel.XSSFWorkbook workbook =
                     new org.apache.poi.xssf.usermodel.XSSFWorkbook()) {
            org.apache.poi.ss.usermodel.Sheet sheet = workbook.createSheet("比赛得分详情");

            org.apache.poi.ss.usermodel.Row headerRow = sheet.createRow(0);
            String[] headers = {"序号", "竞赛名称", "颁奖单位", "获奖级别", "等级", "获奖教师及得分"};
            org.apache.poi.ss.usermodel.CellStyle headerStyle = workbook.createCellStyle();
            org.apache.poi.ss.usermodel.Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            for (int i = 0; i < headers.length; i++) {
                org.apache.poi.ss.usermodel.Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            int rowIdx = 1;
            int seq = 1;
            for (TeacherCompetitionRecord record : records) {
                CompetitionRecordVO vo = getRecordVO(record);
                if (vo == null) continue;

                org.apache.poi.ss.usermodel.Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(seq++);
                row.createCell(1).setCellValue(vo.getCompetitionName() != null ? vo.getCompetitionName() : "");
                row.createCell(2).setCellValue(vo.getSponsorUnit() != null ? vo.getSponsorUnit() : "");
                row.createCell(3).setCellValue(vo.getCompetitionRank() != null ? vo.getCompetitionRank() : "");
                row.createCell(4).setCellValue(vo.getGradeName() != null ? vo.getGradeName() : "");

                StringBuilder sb = new StringBuilder();
                if (vo.getTeacherScores() != null && !vo.getTeacherScores().isEmpty()) {
                    for (int i = 0; i < vo.getTeacherScores().size(); i++) {
                        if (i > 0) sb.append("、");
                        TeacherScoreVO ts = vo.getTeacherScores().get(i);
                        sb.append(ts.getUserName()).append("（")
                                .append(ts.getPersonalScore().stripTrailingZeros().toPlainString())
                                .append("）");
                    }
                }
                row.createCell(5).setCellValue(sb.toString());
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
}
