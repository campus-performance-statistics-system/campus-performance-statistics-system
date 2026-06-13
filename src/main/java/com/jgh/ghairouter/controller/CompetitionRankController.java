package com.jgh.ghairouter.controller;

import com.jgh.ghairouter.annotation.AuthCheck;
import com.jgh.ghairouter.common.BaseResponse;
import com.jgh.ghairouter.common.DeleteRequest;
import com.jgh.ghairouter.common.ResultUtils;
import com.jgh.ghairouter.constant.UserConstant;
import com.jgh.ghairouter.exception.BusinessException;
import com.jgh.ghairouter.exception.ErrorCode;
import com.jgh.ghairouter.exception.ThrowUtils;
import com.jgh.ghairouter.model.entity.CompetitionRank;
import com.jgh.ghairouter.service.CompetitionRankService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/competition-rank")
@Tag(name = "竞赛等级管理")
public class CompetitionRankController {

    @Resource
    private CompetitionRankService competitionRankService;

    @GetMapping("/list")
    @Operation(summary = "获取竞赛等级列表")
    public BaseResponse<List<CompetitionRank>> list() {
        return ResultUtils.success(competitionRankService.listAll());
    }

    @GetMapping("/get/{id}")
    @Operation(summary = "获取竞赛等级详情")
    public BaseResponse<CompetitionRank> getById(@PathVariable Long id) {
        ThrowUtils.throwIf(id == null || id <= 0, ErrorCode.PARAMS_ERROR);
        CompetitionRank entity = competitionRankService.getById(id);
        ThrowUtils.throwIf(entity == null, ErrorCode.NOT_FOUND_ERROR);
        return ResultUtils.success(entity);
    }

    @PostMapping("/add")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @Operation(summary = "新增竞赛等级")
    public BaseResponse<Long> add(@RequestBody CompetitionRank entity) {
        ThrowUtils.throwIf(entity == null, ErrorCode.PARAMS_ERROR);
        competitionRankService.save(entity);
        return ResultUtils.success(entity.getId());
    }

    @PostMapping("/update")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @Operation(summary = "更新竞赛等级")
    public BaseResponse<Boolean> update(@RequestBody CompetitionRank entity) {
        ThrowUtils.throwIf(entity == null || entity.getId() == null, ErrorCode.PARAMS_ERROR);
        boolean result = competitionRankService.updateById(entity);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    @PostMapping("/delete")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @Operation(summary = "删除竞赛等级")
    public BaseResponse<Boolean> delete(@RequestBody DeleteRequest deleteRequest) {
        if (deleteRequest == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        return ResultUtils.success(competitionRankService.removeById(deleteRequest.getId()));
    }
}
