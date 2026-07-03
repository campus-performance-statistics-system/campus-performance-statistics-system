package com.jgh.ghairouter.controller;

import com.jgh.ghairouter.annotation.AuthCheck;
import com.jgh.ghairouter.common.BaseResponse;
import com.jgh.ghairouter.common.ResultUtils;
import com.jgh.ghairouter.constant.UserConstant;
import com.jgh.ghairouter.exception.BusinessException;
import com.jgh.ghairouter.exception.ErrorCode;
import com.jgh.ghairouter.model.vo.UserScoreStatisticsVO;
import com.jgh.ghairouter.service.StatisticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
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
    @Operation(summary = "获取用户总得分统计（支持按工号或姓名搜索）")
    public BaseResponse<List<UserScoreStatisticsVO>> getUserScoreStatistics(
            @RequestParam(value = "type", defaultValue = "all") String type,
            @RequestParam(value = "sortOrder", defaultValue = "descend") String sortOrder,
            @RequestParam(value = "keyword", required = false) String keyword) {
        return ResultUtils.success(statisticsService.getUserScoreStatistics(type, sortOrder, keyword));
    }

    @GetMapping("/export-attachments")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @Operation(summary = "导出所有比赛分类的附件为ZIP压缩包")
    public void exportAllAttachments(HttpServletResponse response) {
        byte[] zipData = statisticsService.exportAllAttachmentsToZip();
        response.setContentType("application/zip");
        String encodedFilename = java.net.URLEncoder.encode("所有附件.zip", java.nio.charset.StandardCharsets.UTF_8)
                .replace("+", "%20");
        response.setHeader("Content-Disposition",
                "attachment; filename*=UTF-8''" + encodedFilename);
        response.setContentLength(zipData.length);
        try {
            response.getOutputStream().write(zipData);
            response.getOutputStream().flush();
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "导出失败: " + e.getMessage());
        }
    }

    @GetMapping("/export-all-excel")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @Operation(summary = "导出所有分类为一个多Sheet的Excel文件")
    public void exportAllToExcel(HttpServletResponse response) {
        byte[] excelData = statisticsService.exportAllToExcel();
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        String encodedFilename = java.net.URLEncoder.encode("业绩汇总.xlsx", java.nio.charset.StandardCharsets.UTF_8)
                .replace("+", "%20");
        response.setHeader("Content-Disposition",
                "attachment; filename*=UTF-8''" + encodedFilename);
        response.setContentLength(excelData.length);
        try {
            response.getOutputStream().write(excelData);
            response.getOutputStream().flush();
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "导出失败: " + e.getMessage());
        }
    }

}
