package com.jgh.ghairouter.controller;

import com.jgh.ghairouter.annotation.AuthCheck;
import com.jgh.ghairouter.common.BaseResponse;
import com.jgh.ghairouter.common.DeleteRequest;
import com.jgh.ghairouter.common.ResultUtils;
import com.jgh.ghairouter.constant.UserConstant;
import com.jgh.ghairouter.exception.BusinessException;
import com.jgh.ghairouter.exception.ErrorCode;
import com.jgh.ghairouter.exception.ThrowUtils;
import com.jgh.ghairouter.model.entity.AwardGrade;
import com.jgh.ghairouter.service.AwardGradeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/award-grade")
@Tag(name = "获奖等级管理")
public class AwardGradeController {

    @Resource
    private AwardGradeService awardGradeService;

    @GetMapping("/list")
    @Operation(summary = "获取获奖等级列表")
    public BaseResponse<List<AwardGrade>> list() {
        return ResultUtils.success(awardGradeService.listAll());
    }

    @GetMapping("/get/{id}")
    @Operation(summary = "获取获奖等级详情")
    public BaseResponse<AwardGrade> getById(@PathVariable Long id) {
        ThrowUtils.throwIf(id == null || id <= 0, ErrorCode.PARAMS_ERROR);
        AwardGrade entity = awardGradeService.getById(id);
        ThrowUtils.throwIf(entity == null, ErrorCode.NOT_FOUND_ERROR);
        return ResultUtils.success(entity);
    }

    @PostMapping("/add")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @Operation(summary = "新增获奖等级")
    public BaseResponse<Long> add(@RequestBody AwardGrade entity) {
        ThrowUtils.throwIf(entity == null, ErrorCode.PARAMS_ERROR);
        awardGradeService.save(entity);
        return ResultUtils.success(entity.getId());
    }

    @PostMapping("/update")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @Operation(summary = "更新获奖等级")
    public BaseResponse<Boolean> update(@RequestBody AwardGrade entity) {
        ThrowUtils.throwIf(entity == null || entity.getId() == null, ErrorCode.PARAMS_ERROR);
        boolean result = awardGradeService.updateById(entity);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    @PostMapping("/delete")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @Operation(summary = "删除获奖等级")
    public BaseResponse<Boolean> delete(@RequestBody DeleteRequest deleteRequest) {
        if (deleteRequest == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        return ResultUtils.success(awardGradeService.removeById(deleteRequest.getId()));
    }
}
