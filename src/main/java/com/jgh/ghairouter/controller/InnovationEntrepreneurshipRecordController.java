package com.jgh.ghairouter.controller;

import com.jgh.ghairouter.annotation.AuthCheck;
import com.jgh.ghairouter.common.BaseResponse;
import com.jgh.ghairouter.common.DeleteRequest;
import com.jgh.ghairouter.common.ResultUtils;
import com.jgh.ghairouter.constant.UserConstant;
import com.jgh.ghairouter.exception.BusinessException;
import com.jgh.ghairouter.exception.ErrorCode;
import com.jgh.ghairouter.exception.ThrowUtils;
import com.jgh.ghairouter.model.constants.InnovationScoringConstants;
import com.jgh.ghairouter.model.dto.competition.AdminReviewRequest;
import com.jgh.ghairouter.model.dto.competition.InnovationEntrepreneurshipQueryRequest;
import com.jgh.ghairouter.model.entity.InnovationEntrepreneurshipRecord;
import com.jgh.ghairouter.model.entity.User;
import com.jgh.ghairouter.model.vo.InnovationEntrepreneurshipRecordVO;
import com.jgh.ghairouter.service.InnovationEntrepreneurshipRecordService;
import com.jgh.ghairouter.service.UserService;
import com.mybatisflex.core.paginate.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/innovation-entrepreneurship")
@Tag(name = "大创业绩记录管理")
public class InnovationEntrepreneurshipRecordController {

    @Resource
    private InnovationEntrepreneurshipRecordService innovationEntrepreneurshipRecordService;
    @Resource
    private UserService userService;

    @PostMapping("/add")
    @Operation(summary = "提交大创业绩记录")
    public BaseResponse<Long> addRecord(
            @RequestParam("projectNumber") String projectNumber,
            @RequestParam("projectName") String projectName,
            @RequestParam("projectLevel") String projectLevel,
            @RequestParam("projectType") String projectType,
            @RequestParam("projectStatus") String projectStatus,
            @RequestParam("studentLeader") String studentLeader,
            @RequestParam(value = "memberData", required = false) String memberData,
            @RequestParam(value = "scoreData", required = false) String scoreData,
            @RequestParam(value = "file", required = false) MultipartFile file,
            HttpServletRequest httpRequest) {
        User loginUser = userService.getLoginUser(httpRequest);
        Long recordId = innovationEntrepreneurshipRecordService.addRecord(
                loginUser.getId(),
                projectNumber, projectName,
                projectLevel, projectType, projectStatus, studentLeader,
                memberData, scoreData,
                file);
        return ResultUtils.success(recordId);
    }

    @PostMapping("/my/list/page")
    @Operation(summary = "查看我的记录")
    public BaseResponse<Page<InnovationEntrepreneurshipRecordVO>> listMyRecords(
            @RequestBody InnovationEntrepreneurshipQueryRequest queryRequest,
            HttpServletRequest httpRequest) {
        ThrowUtils.throwIf(queryRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(httpRequest);
        return ResultUtils.success(
                innovationEntrepreneurshipRecordService.pageMyRelatedRecords(loginUser.getId(), queryRequest));
    }

    @GetMapping("/my/total-score")
    @Operation(summary = "获取我的总得分")
    public BaseResponse<BigDecimal> getMyTotalScore(HttpServletRequest httpRequest) {
        User loginUser = userService.getLoginUser(httpRequest);
        return ResultUtils.success(innovationEntrepreneurshipRecordService.getMyTotalScore(loginUser.getId()));
    }

    @GetMapping("/get/{id}")
    @Operation(summary = "查看记录详情")
    public BaseResponse<InnovationEntrepreneurshipRecordVO> getRecordById(@PathVariable Long id) {
        ThrowUtils.throwIf(id == null || id <= 0, ErrorCode.PARAMS_ERROR);
        InnovationEntrepreneurshipRecord record = innovationEntrepreneurshipRecordService.getById(id);
        InnovationEntrepreneurshipRecordVO vo = innovationEntrepreneurshipRecordService.getRecordVO(record);
        ThrowUtils.throwIf(vo == null, ErrorCode.NOT_FOUND_ERROR);
        return ResultUtils.success(vo);
    }

    @PostMapping("/delete")
    @Operation(summary = "删除记录")
    public BaseResponse<Boolean> deleteRecord(@RequestBody DeleteRequest deleteRequest, HttpServletRequest httpRequest) {
        if (deleteRequest == null || deleteRequest.getId() <= 0)
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(httpRequest);
        InnovationEntrepreneurshipRecord record = innovationEntrepreneurshipRecordService.getById(deleteRequest.getId());
        if (record == null) throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "记录不存在");
        if (!loginUser.getId().equals(record.getUserId()) && !UserConstant.ADMIN_ROLE.equals(loginUser.getUserRole()))
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权删除该记录");
        return ResultUtils.success(innovationEntrepreneurshipRecordService.removeById(deleteRequest.getId()));
    }

    @PostMapping("/admin/list/page")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @Operation(summary = "管理员查看所有记录")
    public BaseResponse<Page<InnovationEntrepreneurshipRecordVO>> adminListRecords(
            @RequestBody InnovationEntrepreneurshipQueryRequest queryRequest) {
        ThrowUtils.throwIf(queryRequest == null, ErrorCode.PARAMS_ERROR);
        return ResultUtils.success(innovationEntrepreneurshipRecordService.pageRecords(queryRequest));
    }

    @PostMapping("/admin/review")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @Operation(summary = "管理员审核")
    public BaseResponse<Boolean> adminReview(@RequestBody AdminReviewRequest request,
                                              HttpServletRequest httpRequest) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(httpRequest);
        innovationEntrepreneurshipRecordService.adminReview(request.getId(), request.getReviewStatus(),
                request.getReviewComment(), loginUser.getId());
        return ResultUtils.success(true);
    }

    @GetMapping("/admin/export")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @Operation(summary = "导出大创业绩得分Excel")
    public void exportRecords(HttpServletResponse response) {
        byte[] excelData = innovationEntrepreneurshipRecordService.exportRecordsToExcel();
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition",
                "attachment; filename=innovation_entrepreneurship_scores.xlsx");
        response.setContentLength(excelData.length);
        try {
            response.getOutputStream().write(excelData);
            response.getOutputStream().flush();
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "导出失败: " + e.getMessage());
        }
    }

    @GetMapping("/scoring-rules")
    @Operation(summary = "获取得分规则")
    public BaseResponse<Map<String, Object>> getScoringRules() {
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("levels", InnovationScoringConstants.getAvailableLevels());
        result.put("projectTypes", InnovationScoringConstants.getAvailableProjectTypes());
        result.put("statuses", InnovationScoringConstants.getAvailableStatuses());
        result.put("rules", InnovationScoringConstants.getAllScoringRules());
        result.put("nationalScore", InnovationScoringConstants.SCORE_NATIONAL);
        result.put("regionalScore", InnovationScoringConstants.SCORE_REGIONAL);
        return ResultUtils.success(result);
    }
}
