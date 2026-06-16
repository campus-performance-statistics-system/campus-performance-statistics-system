package com.jgh.ghairouter.controller;

import com.jgh.ghairouter.annotation.AuthCheck;
import com.jgh.ghairouter.common.BaseResponse;
import com.jgh.ghairouter.common.ResultUtils;
import com.jgh.ghairouter.constant.UserConstant;
import com.jgh.ghairouter.model.entity.ActivityRankGradeScore;
import com.jgh.ghairouter.service.ActivityRankGradeScoreService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/activity-rank-grade-score")
@Tag(name = "活动类型-竞赛等级-获奖等级得分管理")
public class ActivityRankGradeScoreController {

    @Resource
    private ActivityRankGradeScoreService activityRankGradeScoreService;

    @GetMapping("/list")
    @Operation(summary = "获取指定活动类型的得分规则列表")
    public BaseResponse<List<ActivityRankGradeScore>> listByActivityTypeId(
            @RequestParam("activityTypeId") Long activityTypeId) {
        return ResultUtils.success(activityRankGradeScoreService.listByActivityTypeId(activityTypeId));
    }

    @PostMapping("/batch-save")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @Operation(summary = "批量保存活动类型得分规则")
    public BaseResponse<Boolean> batchSave(
            @RequestParam("activityTypeId") Long activityTypeId,
            @RequestBody List<ActivityRankGradeScore> rules) {
        activityRankGradeScoreService.batchSave(activityTypeId, rules);
        return ResultUtils.success(true);
    }
}
