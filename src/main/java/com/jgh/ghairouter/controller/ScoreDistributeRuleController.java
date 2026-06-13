package com.jgh.ghairouter.controller;

import com.jgh.ghairouter.annotation.AuthCheck;
import com.jgh.ghairouter.common.BaseResponse;
import com.jgh.ghairouter.common.DeleteRequest;
import com.jgh.ghairouter.common.ResultUtils;
import com.jgh.ghairouter.constant.UserConstant;
import com.jgh.ghairouter.exception.BusinessException;
import com.jgh.ghairouter.exception.ErrorCode;
import com.jgh.ghairouter.exception.ThrowUtils;
import com.jgh.ghairouter.model.entity.ScoreDistributeRule;
import com.jgh.ghairouter.service.ScoreDistributeRuleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/distribute-rule")
@Tag(name = "分数分配规则管理")
public class ScoreDistributeRuleController {

    @Resource
    private ScoreDistributeRuleService scoreDistributeRuleService;

    @GetMapping("/list")
    @Operation(summary = "获取分配规则列表")
    public BaseResponse<List<ScoreDistributeRule>> list() {
        return ResultUtils.success(scoreDistributeRuleService.listAll());
    }

    @GetMapping("/get/{id}")
    @Operation(summary = "获取分配规则详情")
    public BaseResponse<ScoreDistributeRule> getById(@PathVariable Long id) {
        ThrowUtils.throwIf(id == null || id <= 0, ErrorCode.PARAMS_ERROR);
        ScoreDistributeRule entity = scoreDistributeRuleService.getById(id);
        ThrowUtils.throwIf(entity == null, ErrorCode.NOT_FOUND_ERROR);
        return ResultUtils.success(entity);
    }

    @PostMapping("/add")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @Operation(summary = "新增分配规则")
    public BaseResponse<Long> add(@RequestBody ScoreDistributeRule entity) {
        ThrowUtils.throwIf(entity == null, ErrorCode.PARAMS_ERROR);
        scoreDistributeRuleService.save(entity);
        return ResultUtils.success(entity.getId());
    }

    @PostMapping("/update")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @Operation(summary = "更新分配规则")
    public BaseResponse<Boolean> update(@RequestBody ScoreDistributeRule entity) {
        ThrowUtils.throwIf(entity == null || entity.getId() == null, ErrorCode.PARAMS_ERROR);
        boolean result = scoreDistributeRuleService.updateById(entity);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    @PostMapping("/delete")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @Operation(summary = "删除分配规则")
    public BaseResponse<Boolean> delete(@RequestBody DeleteRequest deleteRequest) {
        if (deleteRequest == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        return ResultUtils.success(scoreDistributeRuleService.removeById(deleteRequest.getId()));
    }
}
