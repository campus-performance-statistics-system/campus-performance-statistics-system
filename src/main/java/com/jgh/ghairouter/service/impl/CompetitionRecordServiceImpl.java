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
import com.jgh.ghairouter.service.CompetitionRecordService;
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
 * 比赛记录服务实现。
 * 分数分配规则（硬编码）：
 * - 单人：100%
 * - 两人：负责人70%，另一人30%
 * - 三人：负责人60%，其余两人各20%
 * - 四人及以上：负责人50%，其余人平分50%
 * - 未获奖：只有负责人得分
 */
@Slf4j
@Service
public class CompetitionRecordServiceImpl extends ServiceImpl<CompetitionRecordMapper, CompetitionRecord>
        implements CompetitionRecordService {

    @Resource
    private AiReviewService aiReviewService;
    @Resource
    private UserMapper userMapper;
    @Resource
    private CategoryMapper categoryMapper;
    @Resource
    private ActivityTypeMapper activityTypeMapper;
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

    /**
     * 获取分配规则描述
     */
    public static String getDistributeRuleDesc(int memberCount) {
        return switch (memberCount) {
            case 2 -> "两人完成，负责人70%，另一人30%";
            case 3 -> "三人完成，负责人60%，其余两人各20%";
            default -> "四人及以上，主持人50%，剩余所有人平分50%";
        };
    }

    // ==================== 用户提交比赛记录 ====================

    @Override
    public Long addRecord(Long userId, Long categoryId, Long activityTypeId,
                          String competitionName, String sponsorUnit,
                          String competitionRank, String gradeName, BigDecimal baseScore,
                          Integer teamMemberNum, Long firstAuthorId,
                          List<Long> otherAuthorIds, MultipartFile file) {
        // 校验分类
        if (categoryId != null) {
            Category category = categoryMapper.selectOneById(categoryId);
            if (category == null) throw new BusinessException(ErrorCode.PARAMS_ERROR, "比赛分类不存在");
        }
        // 校验活动类型
        if (activityTypeId != null) {
            ActivityType activityType = activityTypeMapper.selectOneById(activityTypeId);
            if (activityType == null) throw new BusinessException(ErrorCode.PARAMS_ERROR, "活动类型不存在");
        }

        // 团队人数
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
        CompetitionRecord record = new CompetitionRecord();
        record.setUserId(userId);
        record.setCategoryId(categoryId);
        record.setActivityTypeId(activityTypeId);
        record.setCompetitionName(competitionName);
        // 颁奖单位：优先使用传入值，为空则从活动类型自动获取
        String unit = sponsorUnit;
        if (StrUtil.isBlank(unit) && activityTypeId != null) {
            ActivityType at = activityTypeMapper.selectOneById(activityTypeId);
            if (at != null && StrUtil.isNotBlank(at.getSponsorUnit())) {
                unit = at.getSponsorUnit();
            }
        }
        record.setSponsorUnit(unit);
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
        record.setAutoReviewStatus(ReviewStatusEnum.PENDING.getValue());
        record.setAdminReviewStatus(ReviewStatusEnum.PENDING.getValue());

        boolean saved = this.save(record);
        if (!saved) throw new BusinessException(ErrorCode.OPERATION_ERROR, "提交失败");

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
    public Long adminAddRecord(Long adminId, String competitionName, String sponsorUnit,
                                Long rankId, String gradeName, BigDecimal baseScore,
                                Integer teamMemberNum, Long firstAuthorId,
                                List<Long> otherAuthorIds, MultipartFile file) {
        int memberNum = teamMemberNum != null && teamMemberNum > 0 ? teamMemberNum : 1;

        // 读取文件base64（可选）
        String base64 = null;
        if (file != null && !file.isEmpty()) {
            try {
                base64 = Base64.getEncoder().encodeToString(file.getBytes());
            } catch (IOException e) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "读取文件失败");
            }
        }

        // 竞赛等级映射
        String competitionRank;
        if (rankId == 1) competitionRank = "国家级";
        else if (rankId == 2) competitionRank = "区级";
        else if (rankId == 3) competitionRank = "校级";
        else competitionRank = "校级";

        // 构建记录
        CompetitionRecord record = new CompetitionRecord();
        record.setUserId(adminId);
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
        record.setAutoReviewStatus(ReviewStatusEnum.PASSED.getValue());
        record.setAdminReviewStatus(ReviewStatusEnum.PASSED.getValue());
        record.setAdminId(adminId);
        record.setAdminReviewTime(LocalDateTime.now());
        record.setAdminReviewComment("管理员直接录入");

        boolean saved = this.save(record);
        if (!saved) throw new BusinessException(ErrorCode.OPERATION_ERROR, "添加失败");

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
     * 多人团队分数分配。
     * 负责人额外 +2 基础分（参与院级以上比赛：2分/项）。
     * baseScore 为获奖加分（bonus），按团队比例分配。
     */
    private void saveTeacherScores(CompetitionRecord record, BigDecimal bonus,
                                    int memberNum, Long firstAuthorId,
                                    List<Long> otherAuthorIds) {
        List<TeacherCompetitionScore> scores = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        final BigDecimal LEADER_BASE = new BigDecimal("2");

        if (memberNum == 1) {
            // 单人：负责人得2基础分 + 全部加分
            BigDecimal total = LEADER_BASE.add(bonus);
            scores.add(buildScore(record.getId(), firstAuthorId, total, 1, now));
        } else if (memberNum == 2) {
            // 两人：负责人 2 + bonus*70%，成员 bonus*30%
            scores.add(buildScore(record.getId(), firstAuthorId,
                    LEADER_BASE.add(bonus.multiply(RATIO_2_LEADER)).setScale(3, RoundingMode.HALF_UP), 1, now));
            if (CollUtil.isNotEmpty(otherAuthorIds) && !otherAuthorIds.get(0).equals(firstAuthorId)) {
                scores.add(buildScore(record.getId(), otherAuthorIds.get(0),
                        bonus.multiply(RATIO_2_MEMBER).setScale(3, RoundingMode.HALF_UP), 0, now));
            }
        } else if (memberNum == 3) {
            // 三人：负责人 2 + bonus*60%，成员 bonus*20%
            scores.add(buildScore(record.getId(), firstAuthorId,
                    LEADER_BASE.add(bonus.multiply(RATIO_3_LEADER)).setScale(3, RoundingMode.HALF_UP), 1, now));
            BigDecimal perMember = bonus.multiply(RATIO_3_MEMBER).setScale(3, RoundingMode.HALF_UP);
            if (CollUtil.isNotEmpty(otherAuthorIds)) {
                for (Long otherId : otherAuthorIds) {
                    if (!otherId.equals(firstAuthorId)) {
                        scores.add(buildScore(record.getId(), otherId, perMember, 0, now));
                    }
                }
            }
        } else {
            // 四人及以上：负责人 2 + bonus*50%，其余平分 bonus*50%
            scores.add(buildScore(record.getId(), firstAuthorId,
                    LEADER_BASE.add(bonus.multiply(RATIO_N_LEADER)).setScale(3, RoundingMode.HALF_UP), 1, now));
            int otherCount = memberNum - 1;
            if (otherCount > 0 && CollUtil.isNotEmpty(otherAuthorIds)) {
                BigDecimal restBonus = bonus.multiply(RATIO_N_REST);
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

    /**
     * 未获奖团队：只有负责人得分
     */
    private void saveNoAwardScores(CompetitionRecord record, BigDecimal baseScore,
                                    Long firstAuthorId, List<Long> otherAuthorIds) {
        LocalDateTime now = LocalDateTime.now();
        // 未获奖：只有负责人得2分基础分
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
                .eq("category_id", req.getCategoryId())
                .eq("activity_type_id", req.getActivityTypeId())
                .eq("auto_review_status", req.getAutoReviewStatus())
                .eq("admin_review_status", req.getAdminReviewStatus())
                .like("competition_name", req.getCompetitionName());
        if (StrUtil.isNotBlank(req.getSortField())) {
            wrapper.orderBy(req.getSortField(), "ascend".equals(req.getSortOrder()));
        }
        wrapper.orderBy("create_time", false);
        return wrapper;
    }

    @Override
    public CompetitionRecordVO getRecordVO(CompetitionRecord record) {
        if (record == null) return null;
        CompetitionRecordVO vo = new CompetitionRecordVO();
        BeanUtil.copyProperties(record, vo);

        if (record.getUserId() != null) {
            User u = userMapper.selectOneById(record.getUserId());
            if (u != null) vo.setUserName(u.getUserName());
        }
        if (record.getCategoryId() != null) {
            Category c = categoryMapper.selectOneById(record.getCategoryId());
            if (c != null) vo.setCategoryName(c.getName());
        }
        if (record.getActivityTypeId() != null) {
            ActivityType at = activityTypeMapper.selectOneById(record.getActivityTypeId());
            if (at != null) vo.setActivityTypeName(at.getName());
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
        if (record.getAdminId() != null) {
            User admin = userMapper.selectOneById(record.getAdminId());
            if (admin != null) vo.setAdminName(admin.getUserName());
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
        Page<CompetitionRecord> recordPage = this.page(Page.of(pageNum, pageSize), getQueryWrapper(req));
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
                .eq("category_id", req.getCategoryId())
                .eq("activity_type_id", req.getActivityTypeId())
                .eq("auto_review_status", req.getAutoReviewStatus())
                .eq("admin_review_status", req.getAdminReviewStatus())
                .like("competition_name", req.getCompetitionName())
                .where("(user_id = ? OR first_author_id = ? OR other_author_ids LIKE ?)",
                        userId, userId, "%" + userId + "%");
        wrapper.orderBy("create_time", false);

        Page<CompetitionRecord> recordPage = this.page(Page.of(pageNum, pageSize), wrapper);
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

        CompetitionRecord record = this.getById(recordId);
        if (record == null) throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "比赛记录不存在");

        if (statusEnum == ReviewStatusEnum.PASSED && StrUtil.isBlank(reviewComment))
            reviewComment = "审核通过";

        record.setAdminReviewStatus(reviewStatus);
        record.setAdminReviewComment(reviewComment);
        record.setAdminId(adminId);
        record.setAdminReviewTime(LocalDateTime.now());

        if (!this.updateById(record))
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "审核失败");

        // 审核通过后计算并保存个人得分
        if (statusEnum == ReviewStatusEnum.PASSED) {
            // 先清除旧得分记录
            teacherScoreMapper.deleteByQuery(
                    QueryWrapper.create().eq("record_id", recordId));

            // 解析团队成员
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
        List<CompetitionRecord> records = this.list(QueryWrapper.create().orderBy("create_time", true));

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
            for (CompetitionRecord record : records) {
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
