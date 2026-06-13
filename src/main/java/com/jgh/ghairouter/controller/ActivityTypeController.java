package com.jgh.ghairouter.controller;

import com.jgh.ghairouter.annotation.AuthCheck;
import com.jgh.ghairouter.common.BaseResponse;
import com.jgh.ghairouter.common.DeleteRequest;
import com.jgh.ghairouter.common.ResultUtils;
import com.jgh.ghairouter.constant.UserConstant;
import com.jgh.ghairouter.exception.BusinessException;
import com.jgh.ghairouter.exception.ErrorCode;
import com.jgh.ghairouter.exception.ThrowUtils;
import com.jgh.ghairouter.model.entity.ActivityType;
import com.jgh.ghairouter.service.ActivityTypeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 活动类型控制器
 */
@RestController
@RequestMapping("/activity-type")
@Tag(name = "活动类型管理")
public class ActivityTypeController {

    @Resource
    private ActivityTypeService activityTypeService;

    /**
     * 获取活动类型列表（可按分类筛选）
     */
    @GetMapping("/list")
    @Operation(summary = "获取活动类型列表")
    public BaseResponse<List<ActivityType>> list(@RequestParam(required = false) Long categoryId) {
        if (categoryId != null && categoryId > 0) {
            return ResultUtils.success(activityTypeService.listByCategoryId(categoryId));
        }
        return ResultUtils.success(activityTypeService.listAll());
    }

    /**
     * 根据ID获取活动类型
     */
    @GetMapping("/get/{id}")
    @Operation(summary = "获取活动类型详情")
    public BaseResponse<ActivityType> getById(@PathVariable Long id) {
        ThrowUtils.throwIf(id == null || id <= 0, ErrorCode.PARAMS_ERROR);
        ActivityType at = activityTypeService.getById(id);
        ThrowUtils.throwIf(at == null, ErrorCode.NOT_FOUND_ERROR);
        return ResultUtils.success(at);
    }

    /**
     * 新增活动类型（管理员）
     */
    @PostMapping("/add")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @Operation(summary = "新增活动类型")
    public BaseResponse<Long> add(@RequestBody ActivityType activityType) {
        ThrowUtils.throwIf(activityType == null, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(activityType.getCategoryId() == null, ErrorCode.PARAMS_ERROR, "所属分类不能为空");
        activityTypeService.save(activityType);
        return ResultUtils.success(activityType.getId());
    }

    /**
     * 更新活动类型（管理员）
     */
    @PostMapping("/update")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @Operation(summary = "更新活动类型")
    public BaseResponse<Boolean> update(@RequestBody ActivityType activityType) {
        ThrowUtils.throwIf(activityType == null || activityType.getId() == null, ErrorCode.PARAMS_ERROR);
        boolean result = activityTypeService.updateById(activityType);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    /**
     * 删除活动类型（管理员）
     */
    @PostMapping("/delete")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @Operation(summary = "删除活动类型")
    public BaseResponse<Boolean> delete(@RequestBody DeleteRequest deleteRequest) {
        if (deleteRequest == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        boolean result = activityTypeService.removeById(deleteRequest.getId());
        return ResultUtils.success(result);
    }
}
