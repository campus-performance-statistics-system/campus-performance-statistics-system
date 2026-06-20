package com.jgh.ghairouter.controller;

import com.jgh.ghairouter.annotation.AuthCheck;
import com.jgh.ghairouter.common.BaseResponse;
import com.jgh.ghairouter.common.DeleteRequest;
import com.jgh.ghairouter.common.ResultUtils;
import com.jgh.ghairouter.constant.UserConstant;
import com.jgh.ghairouter.exception.BusinessException;
import com.jgh.ghairouter.exception.ErrorCode;
import com.jgh.ghairouter.exception.ThrowUtils;
import com.jgh.ghairouter.model.constants.TeachingReformScoringConstants;
import com.jgh.ghairouter.model.dto.competition.AdminReviewRequest;
import com.jgh.ghairouter.model.dto.competition.TeachingReformQueryRequest;
import com.jgh.ghairouter.model.entity.TeachingReformRecord;
import com.jgh.ghairouter.model.entity.User;
import com.jgh.ghairouter.model.vo.TeachingReformRecordVO;
import com.jgh.ghairouter.service.TeachingReformRecordService;
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
@RequestMapping("/teaching-reform")
@Tag(name = "教改科研项目业绩记录管理")
public class TeachingReformRecordController {

    @Resource
    private TeachingReformRecordService teachingReformRecordService;
    @Resource
    private UserService userService;

    @PostMapping("/add")
    @Operation(summary = "提交教改科研项目业绩记录")
    public BaseResponse<Long> addRecord(
            @RequestParam("projectName") String projectName,
            @RequestParam("projectType") String projectType,
            @RequestParam("projectStatus") String projectStatus,
            @RequestParam("projectLeader") String projectLeader,
            @RequestParam(value = "memberData", required = false) String memberData,
            @RequestParam(value = "scoreData", required = false) String scoreData,
            @RequestParam(value = "file", required = false) MultipartFile file,
            HttpServletRequest httpRequest) {
        User loginUser = userService.getLoginUser(httpRequest);
        Long recordId = teachingReformRecordService.addRecord(
                loginUser.getId(),
                projectName,
                projectType, projectStatus, projectLeader,
                memberData, scoreData,
                file);
        return ResultUtils.success(recordId);
    }

    @PostMapping("/my/list/page")
    @Operation(summary = "查看我的记录")
    public BaseResponse<Page<TeachingReformRecordVO>> listMyRecords(
            @RequestBody TeachingReformQueryRequest queryRequest,
            HttpServletRequest httpRequest) {
        ThrowUtils.throwIf(queryRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(httpRequest);
        return ResultUtils.success(
                teachingReformRecordService.pageMyRelatedRecords(loginUser.getId(), queryRequest));
    }

    @GetMapping("/my/total-score")
    @Operation(summary = "获取我的总得分")
    public BaseResponse<BigDecimal> getMyTotalScore(HttpServletRequest httpRequest) {
        User loginUser = userService.getLoginUser(httpRequest);
        return ResultUtils.success(teachingReformRecordService.getMyTotalScore(loginUser.getId()));
    }

    @GetMapping("/get/{id}")
    @Operation(summary = "查看记录详情")
    public BaseResponse<TeachingReformRecordVO> getRecordById(@PathVariable Long id) {
        ThrowUtils.throwIf(id == null || id <= 0, ErrorCode.PARAMS_ERROR);
        TeachingReformRecord record = teachingReformRecordService.getById(id);
        TeachingReformRecordVO vo = teachingReformRecordService.getRecordVO(record);
        ThrowUtils.throwIf(vo == null, ErrorCode.NOT_FOUND_ERROR);
        return ResultUtils.success(vo);
    }

    @PostMapping("/delete")
    @Operation(summary = "删除记录")
    public BaseResponse<Boolean> deleteRecord(@RequestBody DeleteRequest deleteRequest, HttpServletRequest httpRequest) {
        if (deleteRequest == null || deleteRequest.getId() <= 0)
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(httpRequest);
        TeachingReformRecord record = teachingReformRecordService.getById(deleteRequest.getId());
        if (record == null) throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "记录不存在");
        if (!loginUser.getId().equals(record.getUserId()) && !UserConstant.ADMIN_ROLE.equals(loginUser.getUserRole()))
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权删除该记录");
        return ResultUtils.success(teachingReformRecordService.removeById(deleteRequest.getId()));
    }

    @PostMapping("/admin/list/page")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @Operation(summary = "管理员查看所有记录")
    public BaseResponse<Page<TeachingReformRecordVO>> adminListRecords(
            @RequestBody TeachingReformQueryRequest queryRequest) {
        ThrowUtils.throwIf(queryRequest == null, ErrorCode.PARAMS_ERROR);
        return ResultUtils.success(teachingReformRecordService.pageRecords(queryRequest));
    }

    @PostMapping("/admin/review")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @Operation(summary = "管理员审核")
    public BaseResponse<Boolean> adminReview(@RequestBody AdminReviewRequest request,
                                              HttpServletRequest httpRequest) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(httpRequest);
        teachingReformRecordService.adminReview(request.getId(), request.getReviewStatus(),
                request.getReviewComment(), loginUser.getId());
        return ResultUtils.success(true);
    }

    @GetMapping("/admin/export")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @Operation(summary = "导出教改科研项目业绩得分Excel")
    public void exportRecords(HttpServletResponse response) {
        byte[] excelData = teachingReformRecordService.exportRecordsToExcel();
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition",
                "attachment; filename=teaching_reform_scores.xlsx");
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
        result.put("projectTypes", TeachingReformScoringConstants.getAvailableProjectTypes());
        result.put("statuses", TeachingReformScoringConstants.getAvailableStatuses());
        result.put("rules", TeachingReformScoringConstants.getAllScoringRules());
        result.put("provincialEducationReformScore", TeachingReformScoringConstants.SCORE_PROVINCIAL_EDUCATION_REFORM);
        result.put("youngTeacherBasicScore", TeachingReformScoringConstants.SCORE_YOUNG_TEACHER_BASIC);
        result.put("universityResearchScore", TeachingReformScoringConstants.SCORE_UNIVERSITY_RESEARCH);
        result.put("universityCourseIdeologyScore", TeachingReformScoringConstants.SCORE_UNIVERSITY_COURSE_IDEOLOGY);
        result.put("notApprovedScore", TeachingReformScoringConstants.SCORE_NOT_APPROVED);
        return ResultUtils.success(result);
    }
}
