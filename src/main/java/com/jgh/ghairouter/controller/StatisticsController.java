package com.jgh.ghairouter.controller;

import com.jgh.ghairouter.annotation.AuthCheck;
import com.jgh.ghairouter.common.BaseResponse;
import com.jgh.ghairouter.common.ResultUtils;
import com.jgh.ghairouter.constant.UserConstant;
import com.jgh.ghairouter.model.vo.UserScoreStatisticsVO;
import com.jgh.ghairouter.service.StatisticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/statistics")
@Tag(name = "统计管理")
public class StatisticsController {

    @Resource
    private StatisticsService statisticsService;

    @GetMapping("/total-scores")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @Operation(summary = "获取所有用户总得分统计")
    public BaseResponse<List<UserScoreStatisticsVO>> getUserScoreStatistics(
            @RequestParam(value = "type", defaultValue = "all") String type,
            @RequestParam(value = "sortOrder", defaultValue = "descend") String sortOrder) {
        return ResultUtils.success(statisticsService.getUserScoreStatistics(type, sortOrder));
    }
}
