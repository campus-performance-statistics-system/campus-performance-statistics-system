package com.jgh.ghairouter.controller;

import com.jgh.ghairouter.annotation.AuthCheck;
import com.jgh.ghairouter.common.BaseResponse;
import com.jgh.ghairouter.common.DeleteRequest;
import com.jgh.ghairouter.common.ResultUtils;
import com.jgh.ghairouter.constant.UserConstant;
import com.jgh.ghairouter.exception.BusinessException;
import com.jgh.ghairouter.exception.ErrorCode;
import com.jgh.ghairouter.exception.ThrowUtils;
import com.jgh.ghairouter.model.constants.TrainingScoringConstants;
import com.jgh.ghairouter.model.dto.competition.AdminReviewRequest;
import com.jgh.ghairouter.model.dto.competition.TrainingGuidanceQueryRequest;
import com.jgh.ghairouter.model.entity.TrainingGuidanceRecord;
import com.jgh.ghairouter.model.entity.User;
import com.jgh.ghairouter.model.vo.TrainingGuidanceRecordVO;
import com.jgh.ghairouter.service.TrainingGuidanceRecordService;
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
@RequestMapping("/training-guidance")
@Tag(name = "指导实训记录管理")
public class TrainingGuidanceRecordController {

    @Resource
    private TrainingGuidanceRecordService trainingGuidanceRecordService;
    @Resource
    private UserService userService;

    @PostMapping("/add")
    @Operation(summary = "提交指导实训记录")
    public BaseResponse<Long> addRecord(
            @RequestParam("semester") String semester,
            @RequestParam("trainingName") String trainingName,
            @RequestParam(value = "responsibleTeachers", required = false) String responsibleTeachers,
            @RequestParam(value = "participatingTeachers", required = false) String participatingTeachers,
            @RequestParam(value = "file", required = false) MultipartFile file,
            HttpServletRequest httpRequest) {
        User loginUser = userService.getLoginUser(httpRequest);
        Long recordId = trainingGuidanceRecordService.addRecord(
                loginUser.getId(),
                semester, trainingName,
                responsibleTeachers, participatingTeachers,
                file);
        return ResultUtils.success(recordId);
    }

    @PostMapping("/my/list/page")
    @Operation(summary = "查看我的记录")
    public BaseResponse<Page<TrainingGuidanceRecordVO>> listMyRecords(
            @RequestBody TrainingGuidanceQueryRequest queryRequest,
            HttpServletRequest httpRequest) {
        ThrowUtils.throwIf(queryRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(httpRequest);
        return ResultUtils.success(
                trainingGuidanceRecordService.pageMyRelatedRecords(loginUser.getId(), queryRequest));
    }

    @GetMapping("/my/total-score")
    @Operation(summary = "获取我的总得分")
    public BaseResponse<BigDecimal> getMyTotalScore(HttpServletRequest httpRequest) {
        User loginUser = userService.getLoginUser(httpRequest);
        return ResultUtils.success(trainingGuidanceRecordService.getMyTotalScore(loginUser.getId()));
    }

    @GetMapping("/get/{id}")
    @Operation(summary = "查看记录详情")
    public BaseResponse<TrainingGuidanceRecordVO> getRecordById(@PathVariable Long id) {
        ThrowUtils.throwIf(id == null || id <= 0, ErrorCode.PARAMS_ERROR);
        TrainingGuidanceRecord record = trainingGuidanceRecordService.getById(id);
        TrainingGuidanceRecordVO vo = trainingGuidanceRecordService.getRecordVO(record);
        ThrowUtils.throwIf(vo == null, ErrorCode.NOT_FOUND_ERROR);
        return ResultUtils.success(vo);
    }

    @PostMapping("/delete")
    @Operation(summary = "删除记录")
    public BaseResponse<Boolean> deleteRecord(@RequestBody DeleteRequest deleteRequest, HttpServletRequest httpRequest) {
        if (deleteRequest == null || deleteRequest.getId() <= 0)
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(httpRequest);
        TrainingGuidanceRecord record = trainingGuidanceRecordService.getById(deleteRequest.getId());
        if (record == null) throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "记录不存在");
        if (!loginUser.getId().equals(record.getUserId()) && !UserConstant.ADMIN_ROLE.equals(loginUser.getUserRole()))
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权删除该记录");
        return ResultUtils.success(trainingGuidanceRecordService.removeById(deleteRequest.getId()));
    }

    @PostMapping("/admin/list/page")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @Operation(summary = "管理员查看所有记录")
    public BaseResponse<Page<TrainingGuidanceRecordVO>> adminListRecords(
            @RequestBody TrainingGuidanceQueryRequest queryRequest) {
        ThrowUtils.throwIf(queryRequest == null, ErrorCode.PARAMS_ERROR);
        return ResultUtils.success(trainingGuidanceRecordService.pageRecords(queryRequest));
    }

    @PostMapping("/admin/review")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @Operation(summary = "管理员审核")
    public BaseResponse<Boolean> adminReview(@RequestBody AdminReviewRequest request,
                                              HttpServletRequest httpRequest) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(httpRequest);
        trainingGuidanceRecordService.adminReview(request.getId(), request.getReviewStatus(),
                request.getReviewComment(), loginUser.getId());
        return ResultUtils.success(true);
    }

    @GetMapping("/admin/export")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @Operation(summary = "导出指导实训得分Excel")
    public void exportRecords(HttpServletResponse response) {
        byte[] excelData = trainingGuidanceRecordService.exportRecordsToExcel();
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition",
                "attachment; filename=training_guidance_scores.xlsx");
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
        result.put("responsibilityScore", TrainingScoringConstants.RESPONSIBLE_SCORE);
        result.put("participationScore", TrainingScoringConstants.PARTICIPATING_SCORE);
        result.put("rules", TrainingScoringConstants.getAllScoringRules());
        return ResultUtils.success(result);
    }

    @GetMapping("/admin/teacher-scores")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @Operation(summary = "获取所有教师总得分汇总")
    public BaseResponse<java.util.List<java.util.Map<String, Object>>> getTeacherTotalScores() {
        return ResultUtils.success(trainingGuidanceRecordService.getTeacherTotalScores());
    }
}
