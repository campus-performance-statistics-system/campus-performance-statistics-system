package com.jgh.ghairouter.controller;

import com.jgh.ghairouter.annotation.AuthCheck;
import com.jgh.ghairouter.common.BaseResponse;
import com.jgh.ghairouter.common.DeleteRequest;
import com.jgh.ghairouter.common.ResultUtils;
import com.jgh.ghairouter.constant.UserConstant;
import com.jgh.ghairouter.exception.BusinessException;
import com.jgh.ghairouter.exception.ErrorCode;
import com.jgh.ghairouter.exception.ThrowUtils;
import com.jgh.ghairouter.mapper.CompetitionRankMapper;
import com.jgh.ghairouter.model.entity.CompetitionRank;
import com.jgh.ghairouter.model.entity.RankGradeScore;
import com.jgh.ghairouter.service.RankGradeScoreService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/rank-grade-score")
@Tag(name = "竞赛计分规则管理")
public class RankGradeScoreController {

    @Resource
    private RankGradeScoreService rankGradeScoreService;

    @Resource
    private CompetitionRankMapper competitionRankMapper;

    @GetMapping("/list")
    @Operation(summary = "获取计分规则列表（可按竞赛等级筛选）")
    public BaseResponse<List<RankGradeScore>> list(@RequestParam(required = false) Long rankId) {
        if (rankId != null && rankId > 0) {
            return ResultUtils.success(rankGradeScoreService.listByRankId(rankId));
        }
        return ResultUtils.success(rankGradeScoreService.listAll());
    }

    @GetMapping("/get/{id}")
    @Operation(summary = "获取计分规则详情")
    public BaseResponse<RankGradeScore> getById(@PathVariable Long id) {
        ThrowUtils.throwIf(id == null || id <= 0, ErrorCode.PARAMS_ERROR);
        RankGradeScore entity = rankGradeScoreService.getById(id);
        ThrowUtils.throwIf(entity == null, ErrorCode.NOT_FOUND_ERROR);
        return ResultUtils.success(entity);
    }

    @PostMapping("/add")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @Operation(summary = "新增计分规则")
    public BaseResponse<Long> add(@RequestBody RankGradeScore entity) {
        ThrowUtils.throwIf(entity == null, ErrorCode.PARAMS_ERROR);
        // 自动生成备注：竞赛等级 + 获奖等级 + 分值，如"国家级一等奖15分"
        if (entity.getRankId() != null) {
            CompetitionRank rank = competitionRankMapper.selectOneById(entity.getRankId());
            if (rank != null) {
                entity.setRemark(rank.getRankName() + entity.getGradeName() + entity.getBaseScore() + "分");
            }
        }
        rankGradeScoreService.save(entity);
        return ResultUtils.success(entity.getId());
    }

    @PostMapping("/update")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @Operation(summary = "更新计分规则")
    public BaseResponse<Boolean> update(@RequestBody RankGradeScore entity) {
        ThrowUtils.throwIf(entity == null || entity.getId() == null, ErrorCode.PARAMS_ERROR);
        boolean result = rankGradeScoreService.updateById(entity);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    @PostMapping("/delete")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @Operation(summary = "删除计分规则")
    public BaseResponse<Boolean> delete(@RequestBody DeleteRequest deleteRequest) {
        if (deleteRequest == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        return ResultUtils.success(rankGradeScoreService.removeById(deleteRequest.getId()));
    }
}
