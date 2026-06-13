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
    private CompetitionRankMapper competitionRankMapper;
    @Resource
    private RankGradeScoreMapper rankGradeScoreMapper;
    @Resource
    private ScoreDistributeRuleMapper distributeRuleMapper;
    @Resource
    private TeacherCompetitionScoreMapper teacherScoreMapper;

    @Override
    public Long addRecord(Long userId, Long categoryId, Long activityTypeId,
                          Long rankGradeScoreId, String competitionName, String sponsorUnit,
                          Integer teamMemberNum, Long firstAuthorId,
                          List<Long> otherAuthorIds, MultipartFile file) {
        // 校验分类
        Category category = categoryMapper.selectOneById(categoryId);
        if (category == null) throw new BusinessException(ErrorCode.PARAMS_ERROR, "比赛分类不存在");

        // 校验活动类型
        ActivityType activityType = activityTypeMapper.selectOneById(activityTypeId);
        if (activityType == null) throw new BusinessException(ErrorCode.PARAMS_ERROR, "活动类型不存在");
        if (!activityType.getCategoryId().equals(categoryId))
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "活动类型不属于所选分类");

        // 校验计分规则
        RankGradeScore scoreRule = rankGradeScoreMapper.selectOneById(rankGradeScoreId);
        if (scoreRule == null) throw new BusinessException(ErrorCode.PARAMS_ERROR, "计分规则不存在");

        // 团队人数
        int memberNum = teamMemberNum != null && teamMemberNum > 0 ? teamMemberNum : 1;

        // 查询分配规则（仅多人团队）
        ScoreDistributeRule distributeRule = null;
        if (memberNum > 1) {
            distributeRule = distributeRuleMapper.selectOneByQuery(
                    QueryWrapper.create().eq("member_count",
                            memberNum >= 4 ? 4 : memberNum));
        }

        // 读取文件 base64
        String base64;
        try {
            base64 = Base64.getEncoder().encodeToString(file.getBytes());
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "读取文件失败");
        }

        // 构建记录
        CompetitionRecord record = new CompetitionRecord();
        record.setUserId(userId);
        record.setCategoryId(categoryId);
        record.setActivityTypeId(activityTypeId);
        record.setRankGradeScoreId(rankGradeScoreId);
        record.setCompetitionName(competitionName);
        record.setSponsorUnit(sponsorUnit);
        record.setTeamMemberNum(memberNum);
        record.setDistributeRuleId(distributeRule != null ? distributeRule.getId() : null);
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

        // 计算并保存个人得分明细
        saveTeacherScores(record, scoreRule.getBaseScore(), distributeRule, firstAuthorId, otherAuthorIds);

        // 触发AI审核
        String mimeType = file.getContentType();
        if (StrUtil.isBlank(mimeType)) mimeType = "image/png";
        try {
            aiReviewService.autoReview(record.getId(), competitionName, base64, mimeType);
        } catch (Exception e) {
            log.error("AI审核触发失败", e);
        }
        return record.getId();
    }

    private void saveTeacherScores(CompetitionRecord record, BigDecimal baseScore,
                                    ScoreDistributeRule distributeRule,
                                    Long firstAuthorId, List<Long> otherAuthorIds) {
        List<TeacherCompetitionScore> scores = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        int memberNum = record.getTeamMemberNum();

        if (memberNum == 1) {
            TeacherCompetitionScore ts = new TeacherCompetitionScore();
            ts.setRecordId(record.getId());
            ts.setTeacherUserId(firstAuthorId);
            ts.setPersonalScore(baseScore);
            ts.setIsLeader(1);
            ts.setCreateTime(now);
            ts.setUpdateTime(now);
            scores.add(ts);
        } else if (distributeRule != null) {
            BigDecimal leaderRatio = distributeRule.getLeaderRatio();
            BigDecimal memberRatio = distributeRule.getMemberRatio();

            BigDecimal leaderScore = baseScore.multiply(leaderRatio).setScale(3, RoundingMode.HALF_UP);

            TeacherCompetitionScore leaderTs = new TeacherCompetitionScore();
            leaderTs.setRecordId(record.getId());
            leaderTs.setTeacherUserId(firstAuthorId);
            leaderTs.setPersonalScore(leaderScore);
            leaderTs.setIsLeader(1);
            leaderTs.setCreateTime(now);
            leaderTs.setUpdateTime(now);
            scores.add(leaderTs);

            if (CollUtil.isNotEmpty(otherAuthorIds)) {
                BigDecimal perMemberScore;
                if (memberNum >= 4) {
                    BigDecimal remainingTotal = baseScore.multiply(new BigDecimal("0.50"));
                    perMemberScore = remainingTotal.divide(BigDecimal.valueOf(memberNum - 1), 3, RoundingMode.HALF_UP);
                } else {
                    perMemberScore = baseScore.multiply(memberRatio).setScale(3, RoundingMode.HALF_UP);
                }
                for (Long otherId : otherAuthorIds) {
                    if (!otherId.equals(firstAuthorId)) {
                        TeacherCompetitionScore memberTs = new TeacherCompetitionScore();
                        memberTs.setRecordId(record.getId());
                        memberTs.setTeacherUserId(otherId);
                        memberTs.setPersonalScore(perMemberScore);
                        memberTs.setIsLeader(0);
                        memberTs.setCreateTime(now);
                        memberTs.setUpdateTime(now);
                        scores.add(memberTs);
                    }
                }
            }
        }

        for (TeacherCompetitionScore ts : scores) {
            teacherScoreMapper.insert(ts);
        }
    }

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
        // 从计分规则解析竞赛等级名和获奖等级名
        if (record.getRankGradeScoreId() != null) {
            RankGradeScore score = rankGradeScoreMapper.selectOneById(record.getRankGradeScoreId());
            if (score != null) {
                vo.setAwardGradeName(score.getGradeName());
                if (score.getRankId() != null) {
                    CompetitionRank rank = competitionRankMapper.selectOneById(score.getRankId());
                    if (rank != null) vo.setCompetitionRankName(rank.getRankName());
                }
            }
        }
        if (record.getDistributeRuleId() != null) {
            ScoreDistributeRule rule = distributeRuleMapper.selectOneById(record.getDistributeRuleId());
            if (rule != null) vo.setDistributeRuleDesc(rule.getRuleDesc());
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
    }
}
