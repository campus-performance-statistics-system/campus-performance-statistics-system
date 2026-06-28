package com.jgh.ghairouter.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import com.jgh.ghairouter.exception.BusinessException;
import com.jgh.ghairouter.exception.ErrorCode;
import com.jgh.ghairouter.mapper.*;
import com.jgh.ghairouter.model.constants.InnovationScoringConstants;
import com.jgh.ghairouter.model.constants.PartTimeClassAdvisorScoringConstants;
import com.jgh.ghairouter.model.constants.ResearchScoringConstants;
import com.jgh.ghairouter.model.constants.SportsScoringConstants;
import com.jgh.ghairouter.model.dto.competition.CompetitionQueryRequest;
import com.jgh.ghairouter.model.entity.*;
import com.jgh.ghairouter.model.enums.ReviewStatusEnum;
import com.jgh.ghairouter.model.vo.AdvisorScoreVO;
import com.jgh.ghairouter.model.vo.CompetitionRecordVO;
import com.jgh.ghairouter.model.vo.StudentCompetitionRecordVO;
import com.jgh.ghairouter.model.vo.TeacherScoreVO;
import com.jgh.ghairouter.service.AiReviewService;
import com.jgh.ghairouter.service.StudentCompetitionRecordService;
import com.jgh.ghairouter.service.TeacherCompetitionRecordService;
import com.jgh.ghairouter.service.TrainingGuidanceRecordService;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
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
    @Resource
    private StudentCompetitionRecordService studentCompetitionRecordService;
    @Resource
    private TrainingGuidanceRecordService trainingGuidanceRecordService;
    @Resource
    private ResearchAchievementRecordMapper researchRecordMapper;
    @Resource
    private ResearchAchievementScoreMapper researchScoreMapper;
    @Resource
    private InnovationEntrepreneurshipRecordMapper innovationRecordMapper;
    @Resource
    private InnovationEntrepreneurshipScoreMapper innovationScoreMapper;
    @Resource
    private com.jgh.ghairouter.mapper.TeachingReformRecordMapper teachingReformRecordMapper;
    @Resource
    private com.jgh.ghairouter.mapper.ThesisRecordMapper thesisRecordMapper;
    @Resource
    private com.jgh.ghairouter.mapper.SportsEventRecordMapper sportsRecordMapper;
    @Resource
    private com.jgh.ghairouter.mapper.SportsEventScoreMapper sportsScoreMapper;
    @Resource
    private com.jgh.ghairouter.mapper.PartTimeClassAdvisorRecordMapper partTimeAdvisorRecordMapper;
    @Resource
    private com.jgh.ghairouter.mapper.PartTimeClassAdvisorScoreMapper partTimeAdvisorScoreMapper;

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
                          List<Long> otherAuthorIds, MultipartFile file,
                          String scoreData) {
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
        record.setScoreData(scoreData);

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
                aiReviewService.autoReview(record.getId(), record.getTypeName(), competitionName, base64, mimeType);
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
     * 保存教师得分明细（服务端计算，作为无 scoreData 时的回退方案）。
     * 只存储获奖加分部分（不含负责人基础2分），基础2分在 getRecordVO 展示时动态添加。
     * @param baseScore 负责人总得分（基础2分 + 获奖加分）
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
            // 单人：负责人获得全部加分（不含基础2分）
            scores.add(buildScore(record.getId(), firstAuthorId,
                    actualBonus.setScale(3, RoundingMode.HALF_UP), 1, now));
        } else if (memberNum == 2) {
            // 两人：负责人70%，另一人30%
            scores.add(buildScore(record.getId(), firstAuthorId,
                    actualBonus.multiply(RATIO_2_LEADER).setScale(3, RoundingMode.HALF_UP), 1, now));
            if (CollUtil.isNotEmpty(otherAuthorIds) && !otherAuthorIds.get(0).equals(firstAuthorId)) {
                scores.add(buildScore(record.getId(), otherAuthorIds.get(0),
                        actualBonus.multiply(RATIO_2_MEMBER).setScale(3, RoundingMode.HALF_UP), 0, now));
            }
        } else if (memberNum == 3) {
            // 三人：负责人60%，其余两人各20%
            scores.add(buildScore(record.getId(), firstAuthorId,
                    actualBonus.multiply(RATIO_3_LEADER).setScale(3, RoundingMode.HALF_UP), 1, now));
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
                    actualBonus.multiply(RATIO_N_LEADER).setScale(3, RoundingMode.HALF_UP), 1, now));
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
        // 未获奖：负责人加分部分为0，基础2分在 getRecordVO 展示时动态添加
        teacherScoreMapper.insert(buildScore(record.getId(), firstAuthorId, BigDecimal.ZERO, 1, now));
        if (CollUtil.isNotEmpty(otherAuthorIds)) {
            for (Long otherId : otherAuthorIds) {
                if (!otherId.equals(firstAuthorId)) {
                    teacherScoreMapper.insert(buildScore(record.getId(), otherId, BigDecimal.ZERO, 0, now));
                }
            }
        }
    }

    /**
     * 从前端提交的 scoreData JSON 解析并保存得分明细。
     * scoreData 格式: [{"userId":1,"score":0.75},{"userId":2,"score":0.25}]
     * score 字段是获奖加分（不含负责人基础2分），第一项始终是负责人。
     */
    private void saveScoresFromData(Long recordId, String scoreData) {
        try {
            JSONArray arr = new JSONArray(scoreData);
            LocalDateTime now = LocalDateTime.now();
            for (int i = 0; i < arr.size(); i++) {
                JSONObject entry = arr.getJSONObject(i);
                long userId = entry.getLong("userId");
                BigDecimal bonusScore = BigDecimal.valueOf(entry.getDouble("score"))
                        .setScale(3, RoundingMode.HALF_UP);
                int isLeader = (i == 0) ? 1 : 0;
                teacherScoreMapper.insert(buildScore(recordId, userId, bonusScore, isLeader, now));
            }
        } catch (Exception e) {
            log.error("解析 scoreData 失败: {}", scoreData, e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "得分数据解析失败");
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

        // 从审计表获取审核信息（按 record_id + record_type 联合定位）
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

        // 查询得分明细：personal_score 存储的是获奖加分部分，负责人展示时 +2 基础分
        List<TeacherCompetitionScore> scoreList = teacherScoreMapper.selectListByQuery(
                QueryWrapper.create().eq("record_id", record.getId()));
        if (CollUtil.isNotEmpty(scoreList)) {
            List<TeacherScoreVO> scoreVOs = scoreList.stream()
                    .map(ts -> {
                        User u = userMapper.selectOneById(ts.getTeacherUserId());
                        BigDecimal displayScore = ts.getPersonalScore();
                        // 负责人在展示时加上基础2分
                        if (ts.getIsLeader() != null && ts.getIsLeader() == 1) {
                            displayScore = displayScore.add(new BigDecimal("2"));
                        }
                        return TeacherScoreVO.builder()
                                .userId(ts.getTeacherUserId())
                                .userName(u != null ? u.getUserName() : String.valueOf(ts.getTeacherUserId()))
                                .personalScore(displayScore)
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
                .map(s -> {
                    BigDecimal score = s.getPersonalScore();
                    // 负责人在汇总时加上基础2分
                    if (s.getIsLeader() != null && s.getIsLeader() == 1) {
                        score = score.add(new BigDecimal("2"));
                    }
                    return score;
                })
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

        // 审核通过后保存个人得分
        if (statusEnum == ReviewStatusEnum.PASSED) {
            teacherScoreMapper.deleteByQuery(
                    QueryWrapper.create().eq("record_id", recordId));

            // 优先使用前端提交的 scoreData，否则回退到服务端计算
            if (StrUtil.isNotBlank(record.getScoreData())) {
                saveScoresFromData(recordId, record.getScoreData());
            } else {
                // 无 scoreData 的旧记录或管理员录入：服务端计算
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
    }

    // ==================== 导出Excel ====================

    // ==================== 导出Excel ====================

    /** 硬编码的比赛分类列表（后续可扩展） */
    private static final List<String> EXPORT_TYPE_NAMES = List.of("教师获奖");

    @Override
    public byte[] exportRecordsToExcel() {
        try (org.apache.poi.xssf.usermodel.XSSFWorkbook workbook =
                     new org.apache.poi.xssf.usermodel.XSSFWorkbook()) {

            org.apache.poi.ss.usermodel.CellStyle headerStyle = workbook.createCellStyle();
            org.apache.poi.ss.usermodel.Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);

            // ========== Sheet 1：教师获奖 ==========
            {
                String[] headers = {"序号", "竞赛名称", "颁奖单位", "获奖级别", "等级", "获奖教师及得分"};
                org.apache.poi.ss.usermodel.Sheet sheet = workbook.createSheet("1-教师获奖");

                org.apache.poi.ss.usermodel.Row headerRow = sheet.createRow(0);
                for (int i = 0; i < headers.length; i++) {
                    org.apache.poi.ss.usermodel.Cell cell = headerRow.createCell(i);
                    cell.setCellValue(headers[i]);
                    cell.setCellStyle(headerStyle);
                }

                int rowIdx = 1;
                int seq = 1;
                for (String typeName : EXPORT_TYPE_NAMES) {
                    List<TeacherCompetitionRecord> typeRecords = this.list(
                            QueryWrapper.create()
                                    .eq("type_name", typeName)
                                    .orderBy("create_time", true));
                    for (TeacherCompetitionRecord record : typeRecords) {
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
                }
                for (int i = 0; i < headers.length; i++) {
                    sheet.autoSizeColumn(i);
                }
            }

            // ========== Sheet 2：指导学生科技竞赛 ==========
            {
                String[] headers = {"序号", "竞赛名称", "主办单位", "参赛题目", "参赛队员姓名", "指导老师", "获奖级别"};
                org.apache.poi.ss.usermodel.Sheet sheet = workbook.createSheet("2-指导学生科技竞赛");

                org.apache.poi.ss.usermodel.Row headerRow = sheet.createRow(0);
                for (int i = 0; i < headers.length; i++) {
                    org.apache.poi.ss.usermodel.Cell cell = headerRow.createCell(i);
                    cell.setCellValue(headers[i]);
                    cell.setCellStyle(headerStyle);
                }

                List<StudentCompetitionRecord> allRecords = studentCompetitionRecordService.list(
                        com.mybatisflex.core.query.QueryWrapper.create()
                                .orderBy("competition_name", true)
                                .orderBy("create_time", true));

                int rowIdx = 1;
                int seq = 1;
                for (StudentCompetitionRecord record : allRecords) {
                    StudentCompetitionRecordVO vo = studentCompetitionRecordService.getRecordVO(record);
                    if (vo == null) continue;

                    org.apache.poi.ss.usermodel.Row row = sheet.createRow(rowIdx++);
                    row.createCell(0).setCellValue(seq++);
                    row.createCell(1).setCellValue(vo.getCompetitionName() != null ? vo.getCompetitionName() : "");
                    row.createCell(2).setCellValue(vo.getSponsorUnit() != null ? vo.getSponsorUnit() : "");
                    row.createCell(3).setCellValue(vo.getCompetitionTopic() != null ? vo.getCompetitionTopic() : "");

                    // 参赛队员姓名：组织者行显示"姓名（分数）"，指导者行显示学生姓名
                    String studentNameCol;
                    String advisorCol;
                    if (vo.getIsOrganizer() != null && vo.getIsOrganizer() == 1) {
                        studentNameCol = vo.getStudentNames() != null ? vo.getStudentNames() : "";
                        if (vo.getAdvisorScores() != null && !vo.getAdvisorScores().isEmpty()) {
                            AdvisorScoreVO score = vo.getAdvisorScores().get(0);
                            studentNameCol += "（" + score.getTotalScore().stripTrailingZeros().toPlainString() + "）";
                        }
                        advisorCol = "";
                    } else {
                        studentNameCol = vo.getStudentNames() != null ? vo.getStudentNames() : "";
                        // 格式化指导老师得分
                        StringBuilder sb = new StringBuilder();
                        if (vo.getAdvisorScores() != null && !vo.getAdvisorScores().isEmpty()) {
                            for (int i = 0; i < vo.getAdvisorScores().size(); i++) {
                                if (i > 0) sb.append("、");
                                AdvisorScoreVO score = vo.getAdvisorScores().get(i);
                                sb.append(score.getTeacherName()).append("（");
                                BigDecimal base = score.getBaseScore();
                                BigDecimal bonus = score.getBonusScore();
                                if (base.compareTo(BigDecimal.ZERO) > 0 && bonus.compareTo(BigDecimal.ZERO) > 0) {
                                    sb.append(base.stripTrailingZeros().toPlainString())
                                            .append("+奖").append(bonus.stripTrailingZeros().toPlainString());
                                } else if (bonus.compareTo(BigDecimal.ZERO) > 0) {
                                    sb.append("奖").append(bonus.stripTrailingZeros().toPlainString());
                                } else if (base.compareTo(BigDecimal.ZERO) > 0) {
                                    sb.append(base.stripTrailingZeros().toPlainString());
                                } else {
                                    sb.append("0");
                                }
                                sb.append("）");
                            }
                        }
                        advisorCol = sb.toString();
                    }
                    row.createCell(4).setCellValue(studentNameCol);
                    row.createCell(5).setCellValue(advisorCol);
                    row.createCell(6).setCellValue(vo.getAwardLevelText() != null ? vo.getAwardLevelText()
                            : (vo.getGradeName() != null ? vo.getGradeName() : ""));
                }
                for (int i = 0; i < headers.length; i++) {
                    sheet.autoSizeColumn(i);
                }
            }

            // ========== Sheet 3：指导实训 ==========
            {
                String[] headers = {"序号", "时间", "实训名称", "负责教师", "参与教师"};
                org.apache.poi.ss.usermodel.Sheet sheet = workbook.createSheet("3-指导实训");

                org.apache.poi.ss.usermodel.Row headerRow = sheet.createRow(0);
                for (int i = 0; i < headers.length; i++) {
                    org.apache.poi.ss.usermodel.Cell cell = headerRow.createCell(i);
                    cell.setCellValue(headers[i]);
                    cell.setCellStyle(headerStyle);
                }

                List<TrainingGuidanceRecord> trainingRecords = trainingGuidanceRecordService.list(
                        com.mybatisflex.core.query.QueryWrapper.create()
                                .orderBy("semester", true)
                                .orderBy("create_time", true));

                int rowIdx = 1;
                int seq = 1;
                for (TrainingGuidanceRecord record : trainingRecords) {
                    com.jgh.ghairouter.model.vo.TrainingGuidanceRecordVO vo =
                            trainingGuidanceRecordService.getRecordVO(record);
                    if (vo == null) continue;

                    org.apache.poi.ss.usermodel.Row row = sheet.createRow(rowIdx++);
                    row.createCell(0).setCellValue(seq++);
                    row.createCell(1).setCellValue(record.getSemester() != null ? record.getSemester() : "");
                    row.createCell(2).setCellValue(record.getTrainingName() != null ? record.getTrainingName() : "");
                    row.createCell(3).setCellValue(formatTrainingTeachers(record.getResponsibleTeachers(), "2"));
                    row.createCell(4).setCellValue(formatTrainingTeachers(record.getParticipatingTeachers(), "1"));
                }
                for (int i = 0; i < headers.length; i++) {
                    sheet.autoSizeColumn(i);
                }
            }

            // ========== Sheet 4：横向科研项目及专利教材业绩（合并） ==========
            {
                org.apache.poi.ss.usermodel.Sheet sheet = workbook.createSheet("4-横向科研项目及专利教材业绩");

                // 章节标题样式
                org.apache.poi.ss.usermodel.CellStyle sectionStyle = workbook.createCellStyle();
                sectionStyle.setFont(headerFont);
                sectionStyle.setAlignment(org.apache.poi.ss.usermodel.HorizontalAlignment.CENTER);
                sectionStyle.setVerticalAlignment(org.apache.poi.ss.usermodel.VerticalAlignment.CENTER);

                int colCount = 6;
                int rowIdx = 0;

                // ---- 横向科研项目 ----
                org.apache.poi.ss.usermodel.Row sectionTitle1 = sheet.createRow(rowIdx++);
                org.apache.poi.ss.usermodel.Cell titleCell1 = sectionTitle1.createCell(0);
                titleCell1.setCellValue("横向科研项目");
                titleCell1.setCellStyle(sectionStyle);
                sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(rowIdx - 1, rowIdx - 1, 0, colCount - 1));

                String[] hHeaders = {"序号", "项目名称", "项目来源", "到位经费(万元)", "项目组成员及得分", "业绩分"};
                org.apache.poi.ss.usermodel.Row hHeaderRow = sheet.createRow(rowIdx++);
                for (int i = 0; i < hHeaders.length; i++) {
                    org.apache.poi.ss.usermodel.Cell cell = hHeaderRow.createCell(i);
                    cell.setCellValue(hHeaders[i]);
                    cell.setCellStyle(headerStyle);
                }

                List<ResearchAchievementRecord> hRecords = researchRecordMapper.selectListByQuery(
                        QueryWrapper.create().eq("sub_type", ResearchScoringConstants.SUB_TYPE_HORIZONTAL_PROJECT)
                                .orderBy("create_time", true));
                int seq = 1;
                for (ResearchAchievementRecord record : hRecords) {
                    org.apache.poi.ss.usermodel.Row row = sheet.createRow(rowIdx++);
                    row.createCell(0).setCellValue(seq++);
                    row.createCell(1).setCellValue(record.getAchievementName() != null ? record.getAchievementName() : "");
                    row.createCell(2).setCellValue(record.getProjectSource() != null ? record.getProjectSource() : "");
                    row.createCell(3).setCellValue(record.getFundingAmount() != null
                            ? record.getFundingAmount().stripTrailingZeros().toPlainString() : "0");
                    row.createCell(4).setCellValue(formatResearchMemberNames(record.getMemberData()));
                    row.createCell(5).setCellValue(formatResearchScoreTotal(record.getId()));
                }

                // 空两行
                rowIdx += 2;

                // ---- 专利 ----
                org.apache.poi.ss.usermodel.Row sectionTitle2 = sheet.createRow(rowIdx++);
                org.apache.poi.ss.usermodel.Cell titleCell2 = sectionTitle2.createCell(0);
                titleCell2.setCellValue("专利");
                titleCell2.setCellStyle(sectionStyle);
                sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(rowIdx - 1, rowIdx - 1, 0, colCount - 1));

                String[] pHeaders = {"序号", "专利名称", "专利号", "专利类别", "发明成员及得分", "业绩分"};
                org.apache.poi.ss.usermodel.Row pHeaderRow = sheet.createRow(rowIdx++);
                for (int i = 0; i < pHeaders.length; i++) {
                    org.apache.poi.ss.usermodel.Cell cell = pHeaderRow.createCell(i);
                    cell.setCellValue(pHeaders[i]);
                    cell.setCellStyle(headerStyle);
                }

                List<ResearchAchievementRecord> pRecords = researchRecordMapper.selectListByQuery(
                        QueryWrapper.create().eq("sub_type", ResearchScoringConstants.SUB_TYPE_PATENT)
                                .orderBy("create_time", true));
                seq = 1;
                for (ResearchAchievementRecord record : pRecords) {
                    org.apache.poi.ss.usermodel.Row row = sheet.createRow(rowIdx++);
                    row.createCell(0).setCellValue(seq++);
                    row.createCell(1).setCellValue(record.getAchievementName() != null ? record.getAchievementName() : "");
                    row.createCell(2).setCellValue(record.getPatentNumber() != null ? record.getPatentNumber() : "");
                    row.createCell(3).setCellValue(ResearchScoringConstants.getPatentTypeText(record.getPatentType()));
                    row.createCell(4).setCellValue(formatResearchMemberNames(record.getMemberData()));
                    row.createCell(5).setCellValue(formatResearchScoreTotal(record.getId()));
                }

                // 空两行
                rowIdx += 2;

                // ---- 教材及自编讲义 ----
                org.apache.poi.ss.usermodel.Row sectionTitle3 = sheet.createRow(rowIdx++);
                org.apache.poi.ss.usermodel.Cell titleCell3 = sectionTitle3.createCell(0);
                titleCell3.setCellValue("教材及自编讲义");
                titleCell3.setCellStyle(sectionStyle);
                sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(rowIdx - 1, rowIdx - 1, 0, colCount - 1));

                String[] tHeaders = {"序号", "教材及自编讲义名称", "字数(万)", "教材类型", "参编人员及得分", "业绩分"};
                org.apache.poi.ss.usermodel.Row tHeaderRow = sheet.createRow(rowIdx++);
                for (int i = 0; i < tHeaders.length; i++) {
                    org.apache.poi.ss.usermodel.Cell cell = tHeaderRow.createCell(i);
                    cell.setCellValue(tHeaders[i]);
                    cell.setCellStyle(headerStyle);
                }

                List<ResearchAchievementRecord> tRecords = researchRecordMapper.selectListByQuery(
                        QueryWrapper.create().eq("sub_type", ResearchScoringConstants.SUB_TYPE_TEXTBOOK)
                                .orderBy("create_time", true));
                seq = 1;
                for (ResearchAchievementRecord record : tRecords) {
                    org.apache.poi.ss.usermodel.Row row = sheet.createRow(rowIdx++);
                    row.createCell(0).setCellValue(seq++);
                    row.createCell(1).setCellValue(record.getAchievementName() != null ? record.getAchievementName() : "");
                    row.createCell(2).setCellValue(record.getWordCount() != null
                            ? record.getWordCount().stripTrailingZeros().toPlainString() : "0");
                    row.createCell(3).setCellValue(ResearchScoringConstants.getTextbookTypeText(record.getTextbookType()));
                    row.createCell(4).setCellValue(formatResearchMemberNames(record.getMemberData()));
                    row.createCell(5).setCellValue(formatResearchScoreTotal(record.getId()));
                }

                for (int i = 0; i < colCount; i++) {
                    sheet.autoSizeColumn(i);
                }
            }

            // ========== Sheet 5：大创业绩（v6） ==========
            {
                org.apache.poi.ss.usermodel.Sheet sheet = workbook.createSheet("5-大创业绩");

                org.apache.poi.ss.usermodel.CellStyle sectionStyle2 = workbook.createCellStyle();
                sectionStyle2.setFont(headerFont);
                sectionStyle2.setAlignment(org.apache.poi.ss.usermodel.HorizontalAlignment.CENTER);
                sectionStyle2.setVerticalAlignment(org.apache.poi.ss.usermodel.VerticalAlignment.CENTER);

                String[] ieHeaders = {"序号", "项目编号", "项目名称", "级别", "项目类型", "项目负责人", "指导教师及得分"};
                int ieColCount = ieHeaders.length;

                List<InnovationEntrepreneurshipRecord> ieAllRecords = innovationRecordMapper.selectListByQuery(
                        QueryWrapper.create().orderBy("create_time", true));

                // 按项目状态分组
                List<InnovationEntrepreneurshipRecord> ieConcluded = new ArrayList<>();
                List<InnovationEntrepreneurshipRecord> ieNew = new ArrayList<>();
                List<InnovationEntrepreneurshipRecord> ieOther = new ArrayList<>();
                for (InnovationEntrepreneurshipRecord r : ieAllRecords) {
                    if (InnovationScoringConstants.STATUS_CONCLUDED.equals(r.getProjectStatus())) {
                        ieConcluded.add(r);
                    } else if (InnovationScoringConstants.STATUS_NEWLY_ADDED.equals(r.getProjectStatus())) {
                        ieNew.add(r);
                    } else {
                        ieOther.add(r);
                    }
                }

                int ieRowIdx = 0;

                // ---- 结题项目 ----
                if (!ieConcluded.isEmpty() || !ieOther.isEmpty()) {
                    org.apache.poi.ss.usermodel.Row secTitle1 = sheet.createRow(ieRowIdx++);
                    org.apache.poi.ss.usermodel.Cell secCell1 = secTitle1.createCell(0);
                    secCell1.setCellValue("一、结题的创新创业训练计划项目");
                    secCell1.setCellStyle(sectionStyle2);
                    sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(ieRowIdx - 1, ieRowIdx - 1, 0, ieColCount - 1));

                    org.apache.poi.ss.usermodel.Row ieHdr1 = sheet.createRow(ieRowIdx++);
                    for (int i = 0; i < ieHeaders.length; i++) {
                        org.apache.poi.ss.usermodel.Cell cell = ieHdr1.createCell(i);
                        cell.setCellValue(ieHeaders[i]);
                        cell.setCellStyle(headerStyle);
                    }

                    List<InnovationEntrepreneurshipRecord> sec1 = new ArrayList<>();
                    sec1.addAll(ieConcluded);
                    sec1.addAll(ieOther);
                    int seq = 1;
                    for (InnovationEntrepreneurshipRecord record : sec1) {
                        org.apache.poi.ss.usermodel.Row row = sheet.createRow(ieRowIdx++);
                        row.createCell(0).setCellValue(seq++);
                        row.createCell(1).setCellValue(record.getProjectNumber() != null ? record.getProjectNumber() : "");
                        row.createCell(2).setCellValue(record.getProjectName() != null ? record.getProjectName() : "");
                        row.createCell(3).setCellValue(InnovationScoringConstants.getLevelText(record.getProjectLevel()));
                        row.createCell(4).setCellValue(InnovationScoringConstants.getProjectTypeText(record.getProjectType()));
                        row.createCell(5).setCellValue(record.getStudentLeader() != null ? record.getStudentLeader() : "");
                        row.createCell(6).setCellValue(formatInnovationTeacherScores(record.getMemberData(), record.getProjectLevel()));
                    }
                    ieRowIdx += 2;
                }

                // ---- 新增项目 ----
                if (!ieNew.isEmpty()) {
                    org.apache.poi.ss.usermodel.Row secTitle2 = sheet.createRow(ieRowIdx++);
                    org.apache.poi.ss.usermodel.Cell secCell2 = secTitle2.createCell(0);
                    secCell2.setCellValue("二、新增的创新创业训练计划项目");
                    secCell2.setCellStyle(sectionStyle2);
                    sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(ieRowIdx - 1, ieRowIdx - 1, 0, ieColCount - 1));

                    org.apache.poi.ss.usermodel.Row ieHdr2 = sheet.createRow(ieRowIdx++);
                    for (int i = 0; i < ieHeaders.length; i++) {
                        org.apache.poi.ss.usermodel.Cell cell = ieHdr2.createCell(i);
                        cell.setCellValue(ieHeaders[i]);
                        cell.setCellStyle(headerStyle);
                    }

                    int seq = 1;
                    for (InnovationEntrepreneurshipRecord record : ieNew) {
                        org.apache.poi.ss.usermodel.Row row = sheet.createRow(ieRowIdx++);
                        row.createCell(0).setCellValue(seq++);
                        row.createCell(1).setCellValue(record.getProjectNumber() != null ? record.getProjectNumber() : "");
                        row.createCell(2).setCellValue(record.getProjectName() != null ? record.getProjectName() : "");
                        row.createCell(3).setCellValue(InnovationScoringConstants.getLevelText(record.getProjectLevel()));
                        row.createCell(4).setCellValue(InnovationScoringConstants.getProjectTypeText(record.getProjectType()));
                        row.createCell(5).setCellValue(record.getStudentLeader() != null ? record.getStudentLeader() : "");
                        row.createCell(6).setCellValue(formatInnovationTeacherScores(record.getMemberData(), record.getProjectLevel()));
                    }
                }

                for (int i = 0; i < ieHeaders.length; i++) {
                    sheet.autoSizeColumn(i);
                }
            }

            // ========== Sheet 6：教改科研项目业绩（v7） ==========
            writeTeachingReformSheet(workbook, headerStyle);

            // ========== Sheet 7：论文业绩（v8） ==========
            writeThesisSheet(workbook, headerStyle);

            // ========== Sheet 8：体育比赛业绩（v9） ==========
            writeSportsEventSheet(workbook, headerStyle);

            // ========== Sheet 9：兼职班主任（v10） ==========
            writePartTimeClassAdvisorSheet(workbook, headerStyle);

            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            workbook.write(bos);
            return bos.toByteArray();
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "Excel生成失败: " + e.getMessage());
        }
    }

    /**
     * 写入教改科研项目业绩Sheet，严格参照Excel示例格式。
     * 分为"新增"和"结题"两部分，按项目类型分组。
     */
    private void writeTeachingReformSheet(org.apache.poi.xssf.usermodel.XSSFWorkbook workbook,
                                          org.apache.poi.ss.usermodel.CellStyle headerStyle) {
        org.apache.poi.ss.usermodel.Sheet sheet = workbook.createSheet("6-教改科研项目业绩");
        String[] headers = {"序号", "项目名称", "项目类型", "项目组成员及排名"};

        List<com.jgh.ghairouter.model.entity.TeachingReformRecord> allRecords =
                teachingReformRecordMapper.selectListByQuery(
                        QueryWrapper.create().orderBy("create_time", true));

        // 分组：新增（approved）、结题（concluded + not_approved + pending_decision）
        List<com.jgh.ghairouter.model.entity.TeachingReformRecord> newList = new ArrayList<>();
        List<com.jgh.ghairouter.model.entity.TeachingReformRecord> concludedList = new ArrayList<>();
        for (com.jgh.ghairouter.model.entity.TeachingReformRecord r : allRecords) {
            if ("approved".equals(r.getProjectStatus())) {
                newList.add(r);
            } else {
                concludedList.add(r);
            }
        }

        int rowIdx = 0;

        // ===== 一、新增项目 =====
        if (!newList.isEmpty()) {
            rowIdx = writeReformSection(sheet, headerStyle, headers,
                    "信息工程学院新增教改科研项目统计（教务汇总）",
                    newList, rowIdx);
            rowIdx += 2; // 空两行
        }

        // ===== 二、结题项目 =====
        if (!concludedList.isEmpty()) {
            rowIdx = writeReformSection(sheet, headerStyle, headers,
                    "信息工程学院结题教改科研项目统计（教务汇总）",
                    concludedList, rowIdx);
        }

        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    /**
     * 写入一个章节（新增或结题），按项目类型分组。
     * 返回写入后的行号。
     */
    private int writeReformSection(org.apache.poi.ss.usermodel.Sheet sheet,
                                   org.apache.poi.ss.usermodel.CellStyle headerStyle,
                                   String[] headers,
                                   String sectionTitle,
                                   List<com.jgh.ghairouter.model.entity.TeachingReformRecord> records,
                                   int rowIdx) {
        // 章节标题
        org.apache.poi.ss.usermodel.Row titleRow = sheet.createRow(rowIdx++);
        org.apache.poi.ss.usermodel.Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue(sectionTitle);
        titleCell.setCellStyle(headerStyle);
        sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(
                rowIdx - 1, rowIdx - 1, 0, headers.length - 1));

        // 按项目类型分组
        java.util.Map<String, List<com.jgh.ghairouter.model.entity.TeachingReformRecord>> grouped =
                new java.util.LinkedHashMap<>();
        // 保持插入顺序
        String[] typeOrder = {"provincial_education_reform", "young_teacher_basic",
                "university_research", "university_course_ideology"};
        for (String t : typeOrder) grouped.put(t, new ArrayList<>());
        for (com.jgh.ghairouter.model.entity.TeachingReformRecord r : records) {
            String pt = r.getProjectType() != null ? r.getProjectType() : "";
            grouped.computeIfAbsent(pt, k -> new ArrayList<>()).add(r);
        }

        int globalSeq = 1;
        for (java.util.Map.Entry<String, List<com.jgh.ghairouter.model.entity.TeachingReformRecord>> entry :
                grouped.entrySet()) {
            List<com.jgh.ghairouter.model.entity.TeachingReformRecord> group = entry.getValue();
            if (group.isEmpty()) continue;

            // 表头行
            org.apache.poi.ss.usermodel.Row headerRow = sheet.createRow(rowIdx++);
            for (int i = 0; i < headers.length; i++) {
                org.apache.poi.ss.usermodel.Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // 数据行
            for (com.jgh.ghairouter.model.entity.TeachingReformRecord record : group) {
                org.apache.poi.ss.usermodel.Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(globalSeq++);
                row.createCell(1).setCellValue(record.getProjectName() != null ? record.getProjectName() : "");

                // 项目类型（所有状态均显示）
                row.createCell(2).setCellValue(
                        com.jgh.ghairouter.model.constants.TeachingReformScoringConstants
                                .getProjectTypeText(record.getProjectType()));

                // 成员及得分
                row.createCell(3).setCellValue(formatTeachingReformMemberScores(
                        record.getMemberData(), record.getProjectType(), record.getProjectStatus()));
            }
        }
        return rowIdx;
    }

    @Override
    public byte[] exportAttachmentsToZip() {
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(bos)) {

            int typeIndex = 0;
            for (String typeName : EXPORT_TYPE_NAMES) {
                typeIndex++;
                List<TeacherCompetitionRecord> typeRecords = this.list(
                        QueryWrapper.create()
                                .eq("type_name", typeName)
                                .orderBy("create_time", true));

                int seq = 1;
                for (TeacherCompetitionRecord record : typeRecords) {
                    if (StrUtil.isBlank(record.getProofImageData())) {
                        seq++;
                        continue;
                    }

                    CompetitionRecordVO vo = getRecordVO(record);
                    if (vo == null) {
                        seq++;
                        continue;
                    }

                    // 文件名：序号-比赛名称-提交记录的用户名.png
                    String competitionName = sanitizeFilename(
                            StrUtil.isNotBlank(record.getCompetitionName())
                                    ? record.getCompetitionName() : "未知比赛");
                    String userName = sanitizeFilename(
                            vo.getUserName() != null ? vo.getUserName() : "未知用户");
                    String fileName = seq + "-" + competitionName + "-" + userName + ".png";

                    // ZIP 路径：所有附件/分类名/文件名
                    String zipPath = "所有附件/" + typeName + "/" + fileName;

                    // 解码 base64 图片
                    byte[] imageBytes;
                    try {
                        imageBytes = java.util.Base64.getDecoder().decode(record.getProofImageData());
                    } catch (IllegalArgumentException e) {
                        log.warn("附件 base64 解码失败: recordId={}", record.getId());
                        seq++;
                        continue;
                    }

                    ZipEntry entry = new ZipEntry(zipPath);
                    zos.putNextEntry(entry);
                    zos.write(imageBytes);
                    zos.closeEntry();

                    seq++;
                }
            }

            zos.finish();
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "附件压缩包生成失败: " + e.getMessage());
        }
        return bos.toByteArray();
    }

    /** 清理文件名中的非法字符 */
    private String sanitizeFilename(String name) {
        if (StrUtil.isBlank(name)) return "未知";
        return name.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
    }

    /** 根据用户ID获取用户名 */
    private String getUserName(Long userId) {
        if (userId == null) return "";
        User user = userMapper.selectOneById(userId);
        return user != null && StrUtil.isNotBlank(user.getUserName()) ? user.getUserName() : "";
    }

    /**
     * 格式化指导实训教师姓名（用于导出Excel）
     * 例如：[{"teacherName":"秦小旭"},{"teacherName":"方锦文"}] → "秦小旭（2）、方锦文（2）"
     */
    private String formatTrainingTeachers(String teacherJson, String scoreStr) {
        if (StrUtil.isBlank(teacherJson)) return "";
        try {
            cn.hutool.json.JSONArray arr = new cn.hutool.json.JSONArray(teacherJson);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < arr.size(); i++) {
                if (i > 0) sb.append("、");
                cn.hutool.json.JSONObject entry = arr.getJSONObject(i);
                String name = entry.getStr("teacherName", "");
                sb.append(name).append("（").append(scoreStr).append("）");
            }
            return sb.toString();
        } catch (Exception e) {
            log.warn("解析教师JSON失败: {}", teacherJson, e);
            return teacherJson;
        }
    }

    /** 格式化科研业绩成员姓名为显示字符串 */
    private String formatResearchMemberNames(String memberData) {
        if (StrUtil.isBlank(memberData)) return "";
        try {
            cn.hutool.json.JSONArray arr = new cn.hutool.json.JSONArray(memberData);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < arr.size(); i++) {
                if (i > 0) sb.append("、");
                cn.hutool.json.JSONObject entry = arr.getJSONObject(i);
                String name = entry.getStr("teacherName", "");
                sb.append(name);
            }
            return sb.toString();
        } catch (Exception e) {
            return memberData;
        }
    }

    /** 获取科研业绩记录的总得分 */
    private String formatResearchScoreTotal(Long recordId) {
        List<ResearchAchievementScore> scores = researchScoreMapper.selectListByQuery(
                QueryWrapper.create().eq("record_id", recordId));
        if (CollUtil.isEmpty(scores)) {
            return "0";
        }
        BigDecimal total = scores.stream()
                .map(ResearchAchievementScore::getScore)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return total.stripTrailingZeros().toPlainString();
    }

    /**
     * 格式化大创业绩指导教师及得分
     * 格式：教师名得分、教师名得分
     */
    private String formatInnovationTeacherScores(String memberData, String projectLevel) {
        if (StrUtil.isBlank(memberData)) return "";
        try {
            cn.hutool.json.JSONArray arr = new cn.hutool.json.JSONArray(memberData);
            int memberCount = arr.size();
            if (memberCount == 0) return "";

            BigDecimal totalScore = InnovationScoringConstants.calcProjectScore(projectLevel);
            if (totalScore.compareTo(BigDecimal.ZERO) <= 0) return "";

            int leaderIndex = -1;
            for (int i = 0; i < arr.size(); i++) {
                cn.hutool.json.JSONObject entry = arr.getJSONObject(i);
                if (entry.getBool("isLeader", false)) {
                    leaderIndex = i;
                    break;
                }
            }

            List<BigDecimal> distributed = InnovationScoringConstants.distributeScore(totalScore, memberCount, leaderIndex);

            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < arr.size(); i++) {
                if (i > 0) sb.append("、");
                cn.hutool.json.JSONObject entry = arr.getJSONObject(i);
                String name = entry.getStr("teacherName", "");
                sb.append(name);
                sb.append(distributed.get(i).stripTrailingZeros().toPlainString());
            }
            return sb.toString();
        } catch (Exception e) {
            log.error("格式化大创业绩指导教师得分失败: {}", memberData, e);
            return memberData;
        }
    }

    /**
     * 格式化教改科研项目成员及得分。
     * 未获批/未下文：显示为 "未获批：姓名（申报人）2" 或 "未下文：姓名（申报人）2"
     * 获批：显示为 "姓名分数、姓名分数" 格式（如 "蒋红梅7、黄鹏0.38"）
     */
    private String formatTeachingReformMemberScores(String memberData, String projectType, String projectStatus) {
        // 结题/未获批/未下文：显示申报人信息
        if ("not_approved".equals(projectStatus) || "pending_decision".equals(projectStatus)
                || "concluded".equals(projectStatus)) {
            String label;
            if ("not_approved".equals(projectStatus)) label = "未获批：";
            else if ("pending_decision".equals(projectStatus)) label = "未下文：";
            else label = "结题：";
            if (StrUtil.isBlank(memberData)) return label + "2";
            try {
                cn.hutool.json.JSONArray arr = new cn.hutool.json.JSONArray(memberData);
                if (arr.size() > 0) {
                    String name = arr.getJSONObject(0).getStr("teacherName", "");
                    if (StrUtil.isNotBlank(name)) {
                        return label + name + "（申报人）2";
                    }
                }
            } catch (Exception ignored) {}
            return label + "2";
        }

        // 获批项目：格式化成员及得分
        if (StrUtil.isBlank(memberData)) return "";
        try {
            cn.hutool.json.JSONArray arr = new cn.hutool.json.JSONArray(memberData);
            int memberCount = arr.size();
            if (memberCount == 0) return "";

            BigDecimal totalScore = com.jgh.ghairouter.model.constants.TeachingReformScoringConstants
                    .calcProjectScore(projectType, projectStatus);
            if (totalScore.compareTo(BigDecimal.ZERO) <= 0) return "";

            int leaderIndex = -1;
            for (int i = 0; i < arr.size(); i++) {
                cn.hutool.json.JSONObject entry = arr.getJSONObject(i);
                if (entry.getBool("isLeader", false)) {
                    leaderIndex = i;
                    break;
                }
            }

            List<BigDecimal> distributed = com.jgh.ghairouter.model.constants.TeachingReformScoringConstants
                    .distributeScore(totalScore, memberCount, leaderIndex);

            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < arr.size(); i++) {
                if (i > 0) sb.append("、");
                cn.hutool.json.JSONObject entry = arr.getJSONObject(i);
                String name = entry.getStr("teacherName", "");
                sb.append(name);
                sb.append(distributed.get(i).stripTrailingZeros().toPlainString());
            }
            return sb.toString();
        } catch (Exception e) {
            log.error("格式化教改科研项目成员得分失败: {}", memberData, e);
            return memberData;
        }
    }

    // ==================== 论文业绩 Sheet 7 ====================

    /**
     * 写入论文业绩Sheet（v8），严格参照Excel示例格式。
     */
    private void writeThesisSheet(org.apache.poi.xssf.usermodel.XSSFWorkbook workbook,
                                  org.apache.poi.ss.usermodel.CellStyle headerStyle) {
        org.apache.poi.ss.usermodel.Sheet sheet = workbook.createSheet("7-论文业绩");

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

        List<ThesisRecord> allRecords = thesisRecordMapper.selectListByQuery(
                QueryWrapper.create().orderBy("create_time", true));

        int rowIdx = 0;

        // ========== 标题行 ==========
        org.apache.poi.ss.usermodel.Row titleRow = sheet.createRow(rowIdx++);
        org.apache.poi.ss.usermodel.Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue("2023年信息工程学院论文工作量统计表（教务汇总）");
        titleCell.setCellStyle(titleStyle);
        sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(
                rowIdx - 1, rowIdx - 1, 0, colCount - 1));

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
            row.createCell(3).setCellValue(
                    com.jgh.ghairouter.model.constants.ThesisScoringConstants.getLevelText(record.getThesisLevel()));
            row.createCell(4).setCellValue(formatThesisAuthorsScores(record.getAuthorsData(), record.getThesisLevel()));
            row.createCell(5).setCellValue("");
            row.createCell(6).setCellValue(getUserName(record.getUserId()));
            row.createCell(7).setCellValue(formatThesisSubmitterScore(record));
        }

        // ========== 空行和注脚 ==========
        rowIdx += 3;
        org.apache.poi.ss.usermodel.Row noteRow = sheet.createRow(rowIdx);
        org.apache.poi.ss.usermodel.Cell noteCell = noteRow.createCell(0);
        noteCell.setCellValue("注： 发表论文: 一级 12分/篇；二级 9分/篇；三级 6分/篇；四级 3分/篇    \n"
                + "两人完成，按7:3分配；三人完成，按6:2:2分配；四人及以上完成，主持者分配 50%；参与者平均分配 50%");
        noteCell.setCellStyle(noteStyle);
        sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(
                rowIdx, rowIdx, 0, colCount - 1));

        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    // ==================== Sheet 8：体育比赛业绩（v9） ====================
    private void writeSportsEventSheet(org.apache.poi.xssf.usermodel.XSSFWorkbook workbook,
                                        org.apache.poi.ss.usermodel.CellStyle headerStyle) {
        org.apache.poi.ss.usermodel.Sheet sheet = workbook.createSheet("8-体育比赛业绩");

        // 标题样式
        org.apache.poi.ss.usermodel.CellStyle titleStyle = workbook.createCellStyle();
        org.apache.poi.ss.usermodel.Font titleFont = workbook.createFont();
        titleFont.setBold(true);
        titleFont.setFontHeightInPoints((short) 14);
        titleStyle.setFont(titleFont);
        titleStyle.setAlignment(org.apache.poi.ss.usermodel.HorizontalAlignment.CENTER);
        titleStyle.setVerticalAlignment(org.apache.poi.ss.usermodel.VerticalAlignment.CENTER);

        // 数据样式
        org.apache.poi.ss.usermodel.CellStyle dataStyle = workbook.createCellStyle();
        dataStyle.setVerticalAlignment(org.apache.poi.ss.usermodel.VerticalAlignment.CENTER);
        dataStyle.setWrapText(true);

        // 注脚样式
        org.apache.poi.ss.usermodel.CellStyle noteStyle = workbook.createCellStyle();
        noteStyle.setWrapText(true);

        // 设置列宽
        sheet.setColumnWidth(0, 20 * 256);  // A: 比赛项目
        sheet.setColumnWidth(1, 3 * 256);   // B: 空
        sheet.setColumnWidth(2, 55 * 256);  // C: 选手及成绩
        sheet.setColumnWidth(3, 3 * 256);   // D: 空
        sheet.setColumnWidth(4, 3 * 256);   // E: 空
        sheet.setColumnWidth(5, 3 * 256);   // F: 空
        sheet.setColumnWidth(6, 12 * 256);  // G: 姓名
        sheet.setColumnWidth(7, 10 * 256);  // H: 业绩分

        // 获取所有体育比赛记录（按创建时间排序）
        List<SportsEventRecord> allRecords = sportsRecordMapper.selectListByQuery(
                QueryWrapper.create().orderBy("create_time", true));

        // 获取所有得分记录，按人汇总
        List<SportsEventScore> allScores = sportsScoreMapper.selectListByQuery(
                QueryWrapper.create().orderBy("score", false));

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
        org.apache.poi.ss.usermodel.Row titleRow = sheet.createRow(rowIdx++);
        org.apache.poi.ss.usermodel.Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue(java.time.Year.now().getValue() + "年参加院体育比赛汇总");
        titleCell.setCellStyle(titleStyle);
        sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(0, 0, 0, 7));

        // ========== 表头行 ==========
        org.apache.poi.ss.usermodel.Row headerRow = sheet.createRow(rowIdx++);
        org.apache.poi.ss.usermodel.Cell h0 = headerRow.createCell(0);
        h0.setCellValue("比赛项目");
        h0.setCellStyle(headerStyle);
        org.apache.poi.ss.usermodel.Cell h2 = headerRow.createCell(2);
        h2.setCellValue("选手及成绩");
        h2.setCellStyle(headerStyle);
        org.apache.poi.ss.usermodel.Cell h6 = headerRow.createCell(6);
        h6.setCellValue("姓名");
        h6.setCellStyle(headerStyle);
        org.apache.poi.ss.usermodel.Cell h7 = headerRow.createCell(7);
        h7.setCellValue("业绩分");
        h7.setCellStyle(headerStyle);

        // ========== 左侧数据：比赛项目及选手成绩 ==========
        int dataStartRow = rowIdx;
        for (SportsEventRecord record : allRecords) {
            org.apache.poi.ss.usermodel.Row row = sheet.createRow(rowIdx++);

            // A: 比赛项目
            org.apache.poi.ss.usermodel.Cell cellA = row.createCell(0);
            cellA.setCellValue(record.getEventName() != null ? record.getEventName() : "");
            cellA.setCellStyle(dataStyle);

            // C: 选手及成绩
            org.apache.poi.ss.usermodel.Cell cellC = row.createCell(2);
            cellC.setCellValue(formatSportsParticipants(record));
            cellC.setCellStyle(dataStyle);
        }

        int dataEndRow = rowIdx - 1;

        // ========== 右侧：个人得分汇总 ==========
        int personRow = dataStartRow;
        for (Map.Entry<String, BigDecimal> entry : sortedPersons) {
            org.apache.poi.ss.usermodel.Row row;
            if (personRow <= dataEndRow) {
                row = sheet.getRow(personRow);
                if (row == null) row = sheet.createRow(personRow);
            } else {
                row = sheet.createRow(personRow);
            }

            // G: 姓名
            org.apache.poi.ss.usermodel.Cell cellG = row.createCell(6);
            cellG.setCellValue(entry.getKey());
            cellG.setCellStyle(dataStyle);

            // H: 业绩分
            org.apache.poi.ss.usermodel.Cell cellH = row.createCell(7);
            cellH.setCellValue(entry.getValue().stripTrailingZeros().toPlainString());
            cellH.setCellStyle(dataStyle);

            personRow++;
        }

        rowIdx = Math.max(rowIdx, personRow);

        // ========== 空行 + 注释 ==========
        rowIdx++;
        org.apache.poi.ss.usermodel.Row noteRow1 = sheet.createRow(rowIdx++);
        org.apache.poi.ss.usermodel.Cell noteCell1 = noteRow1.createCell(0);
        noteCell1.setCellValue("注");
        noteCell1.setCellStyle(noteStyle);

        org.apache.poi.ss.usermodel.Row noteRow2 = sheet.createRow(rowIdx++);
        org.apache.poi.ss.usermodel.Cell noteCell2 = noteRow2.createCell(0);
        noteCell2.setCellValue("参加运动会及球类项目：1分/项类");
        noteCell2.setCellStyle(noteStyle);

        org.apache.poi.ss.usermodel.Row noteRow3 = sheet.createRow(rowIdx++);
        org.apache.poi.ss.usermodel.Cell noteCell3 = noteRow3.createCell(0);
        noteCell3.setCellValue("获奖增加：运动会前四名 1分/项；后四名 0.5分/项；球类 1.5分/项");
        noteCell3.setCellStyle(noteStyle);

        // 设置行高
        for (int i = 0; i < rowIdx; i++) {
            org.apache.poi.ss.usermodel.Row r = sheet.getRow(i);
            if (r != null) {
                r.setHeight((short) (22 * 20));
            }
        }
        titleRow.setHeight((short) (30 * 20));
    }

    /**
     * 格式化体育比赛参与者信息为显示字符串。
     * 格式：姓名第N名(得分)、姓名(得分)...
     */
    private String formatSportsParticipants(SportsEventRecord record) {
        if (StrUtil.isBlank(record.getMemberData())) return "";

        try {
            JSONArray arr = new JSONArray(record.getMemberData());
            if (arr.size() == 0) return "";

            StringBuilder sb = new StringBuilder();

            if (SportsScoringConstants.EVENT_TYPE_BALL_GAME.equals(record.getEventType())) {
                // 球类项目：列出所有参与者
                BigDecimal perScore = SportsScoringConstants.calcBallGameScore(record.getEventResult());
                String scoreStr = perScore.stripTrailingZeros().toPlainString();

                for (int i = 0; i < arr.size(); i++) {
                    if (i > 0) sb.append("、");
                    JSONObject entry = arr.getJSONObject(i);
                    sb.append(entry.getStr("teacherName", ""));
                }

                String resultText = SportsScoringConstants.getEventResultText(record.getEventResult());
                if (StrUtil.isNotBlank(resultText)) {
                    sb.append("（").append(resultText);
                    sb.append("，").append(scoreStr).append("）");
                }
            } else {
                // 运动会项目：每人独立显示名次和得分
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
            log.error("格式化体育比赛参与者信息失败: {}", record.getMemberData(), e);
            return record.getMemberData();
        }
    }

    /**
     * 格式化论文作者及得分为显示字符串。
     * 格式：作者名得分、作者名得分...
     */
    private String formatThesisAuthorsScores(String authorsData, String thesisLevel) {
        if (StrUtil.isBlank(authorsData)) return "";
        try {
            cn.hutool.json.JSONArray arr = new cn.hutool.json.JSONArray(authorsData);
            int authorCount = arr.size();
            if (authorCount == 0) return "";

            BigDecimal totalScore = com.jgh.ghairouter.model.constants.ThesisScoringConstants
                    .calcThesisScore(thesisLevel);
            if (totalScore.compareTo(BigDecimal.ZERO) <= 0) return "";

            int firstAuthorIndex = -1;
            for (int i = 0; i < arr.size(); i++) {
                cn.hutool.json.JSONObject entry = arr.getJSONObject(i);
                if (entry.getBool("isFirstAuthor", false)) {
                    firstAuthorIndex = i;
                    break;
                }
            }

            List<BigDecimal> distributed = com.jgh.ghairouter.model.constants.ThesisScoringConstants
                    .distributeScore(totalScore, authorCount, firstAuthorIndex);

            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < arr.size(); i++) {
                if (i > 0) sb.append("，");
                cn.hutool.json.JSONObject entry = arr.getJSONObject(i);
                String name = entry.getStr("teacherName", "");
                sb.append(name);
                sb.append(distributed.get(i).stripTrailingZeros().toPlainString());
            }
            return sb.toString();
        } catch (Exception e) {
            log.error("格式化论文作者得分失败: {}", authorsData, e);
            return authorsData;
        }
    }

    /**
     * 获取提交人在该论文中的得分。
     */
    private String formatThesisSubmitterScore(ThesisRecord record) {
        if (StrUtil.isBlank(record.getAuthorsData())) return "";
        try {
            cn.hutool.json.JSONArray arr = new cn.hutool.json.JSONArray(record.getAuthorsData());
            int authorCount = arr.size();
            if (authorCount == 0) return "";

            BigDecimal totalScore = com.jgh.ghairouter.model.constants.ThesisScoringConstants
                    .calcThesisScore(record.getThesisLevel());
            if (totalScore.compareTo(BigDecimal.ZERO) <= 0) return "";

            int firstAuthorIndex = -1;
            for (int i = 0; i < arr.size(); i++) {
                cn.hutool.json.JSONObject entry = arr.getJSONObject(i);
                if (entry.getBool("isFirstAuthor", false)) {
                    firstAuthorIndex = i;
                    break;
                }
            }

            List<BigDecimal> distributed = com.jgh.ghairouter.model.constants.ThesisScoringConstants
                    .distributeScore(totalScore, authorCount, firstAuthorIndex);

            String submitterName = getUserName(record.getUserId());
            BigDecimal submitterTotal = BigDecimal.ZERO;
            for (int i = 0; i < arr.size(); i++) {
                cn.hutool.json.JSONObject entry = arr.getJSONObject(i);
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

    // ==================== Sheet 9：兼职班主任（v10） ====================
    private void writePartTimeClassAdvisorSheet(org.apache.poi.xssf.usermodel.XSSFWorkbook workbook,
                                                 org.apache.poi.ss.usermodel.CellStyle headerStyle) {
        org.apache.poi.ss.usermodel.Sheet sheet = workbook.createSheet("9-兼职班主任");

        // 标题样式
        org.apache.poi.ss.usermodel.CellStyle titleStyle = workbook.createCellStyle();
        org.apache.poi.ss.usermodel.Font titleFont = workbook.createFont();
        titleFont.setBold(true);
        titleFont.setFontHeightInPoints((short) 14);
        titleStyle.setFont(titleFont);
        titleStyle.setAlignment(org.apache.poi.ss.usermodel.HorizontalAlignment.CENTER);
        titleStyle.setVerticalAlignment(org.apache.poi.ss.usermodel.VerticalAlignment.CENTER);

        // 注释样式
        org.apache.poi.ss.usermodel.CellStyle noteStyle = workbook.createCellStyle();
        noteStyle.setVerticalAlignment(org.apache.poi.ss.usermodel.VerticalAlignment.CENTER);
        noteStyle.setWrapText(true);
        org.apache.poi.ss.usermodel.Font noteFont = workbook.createFont();
        noteFont.setFontHeightInPoints((short) 10);
        noteStyle.setFont(noteFont);

        // 数据样式
        org.apache.poi.ss.usermodel.CellStyle dataStyle = workbook.createCellStyle();
        dataStyle.setVerticalAlignment(org.apache.poi.ss.usermodel.VerticalAlignment.CENTER);
        dataStyle.setAlignment(org.apache.poi.ss.usermodel.HorizontalAlignment.CENTER);
        dataStyle.setBorderTop(org.apache.poi.ss.usermodel.BorderStyle.THIN);
        dataStyle.setBorderBottom(org.apache.poi.ss.usermodel.BorderStyle.THIN);
        dataStyle.setBorderLeft(org.apache.poi.ss.usermodel.BorderStyle.THIN);
        dataStyle.setBorderRight(org.apache.poi.ss.usermodel.BorderStyle.THIN);

        // 设置列宽（与示例文件一致）
        sheet.setColumnWidth(0, (int) (15.44 * 256));   // A: 姓名
        sheet.setColumnWidth(1, (int) (11.11 * 256));   // B: 负责班级
        sheet.setColumnWidth(2, (int) (8.44 * 256));    // C: 学风建设-工作要求
        sheet.setColumnWidth(3, (int) (8.11 * 256));    // D: 效果评估
        sheet.setColumnWidth(4, (int) (8.66 * 256));    // E: 安全教育-工作要求
        sheet.setColumnWidth(5, (int) (8.33 * 256));    // F: 效果评估
        sheet.setColumnWidth(6, (int) (13.00 * 256));   // G: 后进生帮扶-工作要求
        sheet.setColumnWidth(7, (int) (8.44 * 256));    // H: 效果评估
        sheet.setColumnWidth(8, (int) (9.00 * 256));    // I: 安全稳定
        sheet.setColumnWidth(9, (int) (9.78 * 256));    // J: 学风建设
        sheet.setColumnWidth(10, (int) (9.66 * 256));   // K: 后进生帮扶
        sheet.setColumnWidth(11, (int) (5.89 * 256));   // L: 总得分
        sheet.setColumnWidth(12, (int) (12.22 * 256));  // M: 换算最终得分
        sheet.setColumnWidth(13, (int) (12.22 * 256));  // N: 合计
        sheet.setColumnWidth(14, (int) (13.00 * 256));  // O: 平均分
        sheet.setColumnWidth(15, (int) (13.00 * 256));  // P: 平均分折合分
        sheet.setColumnWidth(16, (int) (13.00 * 256));  // Q: 行政班分
        sheet.setColumnWidth(17, (int) (12.22 * 256));  // R: 总分

        // 获取所有审核通过的记录，按教师姓名分组
        List<PartTimeClassAdvisorRecord> allRecords = partTimeAdvisorRecordMapper.selectListByQuery(
                QueryWrapper.create().orderBy("teacher_name", true).orderBy("class_id", true));

        Map<String, List<PartTimeClassAdvisorRecord>> teacherGroups = new LinkedHashMap<>();
        for (PartTimeClassAdvisorRecord r : allRecords) {
            String name = r.getTeacherName();
            teacherGroups.computeIfAbsent(name, k -> new ArrayList<>()).add(r);
        }

        String year = String.valueOf(java.time.Year.now().getValue());
        int rowIdx = 0;

        // ========== 第1行：标题 ==========
        org.apache.poi.ss.usermodel.Row titleRow = sheet.createRow(rowIdx++);
        titleRow.setHeight((short) (18 * 20));
        org.apache.poi.ss.usermodel.Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue(year + "年度信息工程学院兼职班主任考核评分表");
        titleCell.setCellStyle(titleStyle);
        sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(0, 0, 0, 12));

        // ========== 第2行：分值转换标准说明 ==========
        org.apache.poi.ss.usermodel.Row noteRow = sheet.createRow(rowIdx++);
        noteRow.setHeight((short) (44 * 20));
        org.apache.poi.ss.usermodel.Cell noteCell = noteRow.createCell(0);
        noteCell.setCellValue("分值转换标准：0至50━0分；51至60━1分；61至70━2分；71至80━3分，81至90━4分；91至100━5分。每带一个行政班有1分，然后总得分+行政班分得最终分（新生和毕业班需除2）。\n");
        noteCell.setCellStyle(noteStyle);
        sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(1, 1, 0, 12));

        // ========== 第3行：一级表头 ==========
        org.apache.poi.ss.usermodel.Row headerRow1 = sheet.createRow(rowIdx++);
        headerRow1.setHeight((short) (18 * 20));

        createHeaderCell(headerRow1, 0, "姓名", headerStyle);
        createHeaderCell(headerRow1, 1, "负责班级", headerStyle);
        createHeaderCell(headerRow1, 2, "学风建设（30分）", headerStyle);
        createHeaderCell(headerRow1, 4, "安全教育（30分）", headerStyle);
        createHeaderCell(headerRow1, 6, "后进生帮扶（30分）", headerStyle);
        createHeaderCell(headerRow1, 8, "育人成果附加分（10分）", headerStyle);
        createHeaderCell(headerRow1, 11, "总得分", headerStyle);
        createHeaderCell(headerRow1, 12, "换算最终得分", headerStyle);
        // N3-R3 留空
        for (int c = 13; c <= 17; c++) {
            createHeaderCell(headerRow1, c, "", headerStyle);
        }

        // 合并一级表头
        sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(2, 3, 1, 1));   // B3:B4
        sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(2, 2, 2, 3));   // C3:D3
        sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(2, 2, 4, 5));   // E3:F3
        sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(2, 2, 6, 7));   // G3:H3
        sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(2, 2, 8, 10));  // I3:K3
        sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(2, 3, 0, 0));   // A3:A4

        // ========== 第4行：二级表头 ==========
        org.apache.poi.ss.usermodel.Row headerRow2 = sheet.createRow(rowIdx++);
        headerRow2.setHeight((short) (28 * 20));

        createHeaderCell(headerRow2, 0, "", headerStyle);
        createHeaderCell(headerRow2, 1, "", headerStyle);
        createHeaderCell(headerRow2, 2, "工作要求（20分）", headerStyle);
        createHeaderCell(headerRow2, 3, "效果评估（10分）", headerStyle);
        createHeaderCell(headerRow2, 4, "工作要求（20分）", headerStyle);
        createHeaderCell(headerRow2, 5, "效果评估（10分）", headerStyle);
        createHeaderCell(headerRow2, 6, "工作要求（20分）", headerStyle);
        createHeaderCell(headerRow2, 7, "效果评估（10分）", headerStyle);
        createHeaderCell(headerRow2, 8, "安全稳定（3分）", headerStyle);
        createHeaderCell(headerRow2, 9, "学风建设（3分）", headerStyle);
        createHeaderCell(headerRow2, 10, "后进生帮扶（4分）", headerStyle);
        createHeaderCell(headerRow2, 11, "", headerStyle);
        createHeaderCell(headerRow2, 12, "", headerStyle);
        createHeaderCell(headerRow2, 13, "", headerStyle);
        createHeaderCell(headerRow2, 14, "平均分", headerStyle);
        createHeaderCell(headerRow2, 15, "平均分折合分", headerStyle);
        createHeaderCell(headerRow2, 16, "行政班分", headerStyle);
        createHeaderCell(headerRow2, 17, "总分", headerStyle);

        // ========== 数据行 ==========
        for (Map.Entry<String, List<PartTimeClassAdvisorRecord>> entry : teacherGroups.entrySet()) {
            List<PartTimeClassAdvisorRecord> records = entry.getValue();
            int groupSize = records.size();

            for (int i = 0; i < groupSize; i++) {
                PartTimeClassAdvisorRecord r = records.get(i);
                org.apache.poi.ss.usermodel.Row row = sheet.createRow(rowIdx);
                row.setHeight((short) (22 * 20));

                // A: 姓名
                org.apache.poi.ss.usermodel.Cell cellA = row.createCell(0);
                cellA.setCellValue(r.getTeacherName() != null ? r.getTeacherName() : "");
                cellA.setCellStyle(dataStyle);

                // B: 负责班级
                org.apache.poi.ss.usermodel.Cell cellB = row.createCell(1);
                cellB.setCellValue(r.getClassId() != null ? r.getClassId() : "");
                cellB.setCellStyle(dataStyle);

                // C-K: 各项得分
                setCellValue(row, 2, r.getStudyStyleWorkReq(), dataStyle);
                setCellValue(row, 3, r.getStudyStyleEffect(), dataStyle);
                setCellValue(row, 4, r.getSafetyEduWorkReq(), dataStyle);
                setCellValue(row, 5, r.getSafetyEduEffect(), dataStyle);
                setCellValue(row, 6, r.getStrugglingStudentWorkReq(), dataStyle);
                setCellValue(row, 7, r.getStrugglingStudentEffect(), dataStyle);
                setCellValue(row, 8, r.getAchievementSafety(), dataStyle);
                setCellValue(row, 9, r.getAchievementStudyStyle(), dataStyle);
                setCellValue(row, 10, r.getAchievementStruggling(), dataStyle);

                // L: 总得分
                BigDecimal rowTotal = PartTimeClassAdvisorScoringConstants.calcRowTotal(
                        r.getStudyStyleWorkReq(), r.getStudyStyleEffect(),
                        r.getSafetyEduWorkReq(), r.getSafetyEduEffect(),
                        r.getStrugglingStudentWorkReq(), r.getStrugglingStudentEffect(),
                        r.getAchievementSafety(), r.getAchievementStudyStyle(),
                        r.getAchievementStruggling());
                setCellValue(row, 11, rowTotal, dataStyle);

                // M: 换算最终得分（仅第一行显示）
                if (i == 0) {
                    BigDecimal converted = PartTimeClassAdvisorScoringConstants.convertScore(rowTotal);
                    setCellValue(row, 12, converted, dataStyle);
                } else {
                    createCell(row, 12, dataStyle);
                }

                // N: 合计、O: 平均分、P: 平均分折合分（仅最后一行显示）
                if (i == groupSize - 1) {
                    BigDecimal sumTotal = BigDecimal.ZERO;
                    for (PartTimeClassAdvisorRecord gr : records) {
                        sumTotal = sumTotal.add(PartTimeClassAdvisorScoringConstants.calcRowTotal(
                                gr.getStudyStyleWorkReq(), gr.getStudyStyleEffect(),
                                gr.getSafetyEduWorkReq(), gr.getSafetyEduEffect(),
                                gr.getStrugglingStudentWorkReq(), gr.getStrugglingStudentEffect(),
                                gr.getAchievementSafety(), gr.getAchievementStudyStyle(),
                                gr.getAchievementStruggling()));
                    }
                    setCellValue(row, 13, sumTotal, dataStyle);

                    BigDecimal avg = sumTotal.divide(new BigDecimal(groupSize), 2, RoundingMode.HALF_UP);
                    setCellValue(row, 14, avg, dataStyle);

                    BigDecimal avgConverted = PartTimeClassAdvisorScoringConstants.convertScore(avg);
                    setCellValue(row, 15, avgConverted, dataStyle);
                } else {
                    createCell(row, 13, dataStyle);
                    createCell(row, 14, dataStyle);
                    createCell(row, 15, dataStyle);
                }

                // Q: 行政班分
                setCellValue(row, 16, r.getAdminClassScore(), dataStyle);

                // R: 总分（仅第一行显示）
                if (i == 0) {
                    BigDecimal totalAdminClassScore = BigDecimal.ZERO;
                    BigDecimal sumTotalForAvg = BigDecimal.ZERO;
                    for (PartTimeClassAdvisorRecord gr : records) {
                        totalAdminClassScore = totalAdminClassScore.add(
                                gr.getAdminClassScore() != null ? gr.getAdminClassScore() : BigDecimal.ZERO);
                        sumTotalForAvg = sumTotalForAvg.add(PartTimeClassAdvisorScoringConstants.calcRowTotal(
                                gr.getStudyStyleWorkReq(), gr.getStudyStyleEffect(),
                                gr.getSafetyEduWorkReq(), gr.getSafetyEduEffect(),
                                gr.getStrugglingStudentWorkReq(), gr.getStrugglingStudentEffect(),
                                gr.getAchievementSafety(), gr.getAchievementStudyStyle(),
                                gr.getAchievementStruggling()));
                    }
                    BigDecimal avgForConvert = sumTotalForAvg.divide(
                            new BigDecimal(groupSize), 2, RoundingMode.HALF_UP);
                    BigDecimal avgConvScore = PartTimeClassAdvisorScoringConstants.convertScore(avgForConvert);
                    BigDecimal finalScore = avgConvScore.add(totalAdminClassScore);
                    setCellValue(row, 17, finalScore, dataStyle);
                } else {
                    createCell(row, 17, dataStyle);
                }

                rowIdx++;
            }
        }

        // 设置行高
        for (int i = 0; i < rowIdx; i++) {
            org.apache.poi.ss.usermodel.Row r = sheet.getRow(i);
            if (r != null) {
                r.setHeight((short) (22 * 20));
            }
        }
        titleRow.setHeight((short) (18 * 20));
    }

    private void createHeaderCell(org.apache.poi.ss.usermodel.Row row, int col, String value,
                                   org.apache.poi.ss.usermodel.CellStyle style) {
        org.apache.poi.ss.usermodel.Cell cell = row.createCell(col);
        cell.setCellValue(value);
        cell.setCellStyle(style);
    }

    private void setCellValue(org.apache.poi.ss.usermodel.Row row, int col, BigDecimal value,
                               org.apache.poi.ss.usermodel.CellStyle style) {
        org.apache.poi.ss.usermodel.Cell cell = row.createCell(col);
        if (value != null) {
            cell.setCellValue(value.doubleValue());
        }
        cell.setCellStyle(style);
    }

    private void createCell(org.apache.poi.ss.usermodel.Row row, int col,
                             org.apache.poi.ss.usermodel.CellStyle style) {
        org.apache.poi.ss.usermodel.Cell cell = row.createCell(col);
        cell.setCellStyle(style);
    }
}
