package com.jgh.ghairouter.controller;

import com.jgh.ghairouter.annotation.AuthCheck;
import com.jgh.ghairouter.common.BaseResponse;
import com.jgh.ghairouter.common.DeleteRequest;
import com.jgh.ghairouter.common.ResultUtils;
import com.jgh.ghairouter.constant.UserConstant;
import com.jgh.ghairouter.exception.BusinessException;
import com.jgh.ghairouter.exception.ErrorCode;
import com.jgh.ghairouter.exception.ThrowUtils;
import com.jgh.ghairouter.model.constants.StudentScoringConstants;
import com.jgh.ghairouter.model.dto.competition.AdminReviewRequest;
import com.jgh.ghairouter.model.dto.competition.StudentCompetitionQueryRequest;
import com.jgh.ghairouter.model.entity.StudentCompetitionRecord;
import com.jgh.ghairouter.model.entity.User;
import com.jgh.ghairouter.model.vo.StudentCompetitionRecordVO;
import com.jgh.ghairouter.service.StudentCompetitionRecordService;
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
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/student-competition")
@Tag(name = "指导学生科技竞赛记录管理")
public class StudentCompetitionRecordController {

    @Resource
    private StudentCompetitionRecordService studentCompetitionRecordService;
    @Resource
    private UserService userService;

    @PostMapping("/add")
    @Operation(summary = "提交指导学生科技竞赛记录")
    public BaseResponse<Long> addRecord(
            @RequestParam("competitionName") String competitionName,
            @RequestParam(value = "sponsorUnit", required = false) String sponsorUnit,
            @RequestParam(value = "competitionTopic", required = false) String competitionTopic,
            @RequestParam(value = "studentNames", required = false) String studentNames,
            @RequestParam(value = "competitionRank", required = false) String competitionRank,
            @RequestParam(value = "gradeName", required = false) String gradeName,
            @RequestParam(value = "awardLevelText", required = false) String awardLevelText,
            @RequestParam(value = "isOrganizer", defaultValue = "0") Integer isOrganizer,
            @RequestParam(value = "advisorScoreData", required = false) String advisorScoreData,
            @RequestParam(value = "file", required = false) MultipartFile file,
            HttpServletRequest httpRequest) {
        User loginUser = userService.getLoginUser(httpRequest);
        Long recordId = studentCompetitionRecordService.addRecord(
                loginUser.getId(),
                competitionName, sponsorUnit,
                competitionTopic, studentNames,
                competitionRank, gradeName, awardLevelText,
                isOrganizer, advisorScoreData,
                file);
        return ResultUtils.success(recordId);
    }

    @PostMapping("/my/list/page")
    @Operation(summary = "查看我的记录")
    public BaseResponse<Page<StudentCompetitionRecordVO>> listMyRecords(
            @RequestBody StudentCompetitionQueryRequest queryRequest,
            HttpServletRequest httpRequest) {
        ThrowUtils.throwIf(queryRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(httpRequest);
        return ResultUtils.success(
                studentCompetitionRecordService.pageMyRelatedRecords(loginUser.getId(), queryRequest));
    }

    @GetMapping("/my/total-score")
    @Operation(summary = "获取我的总得分")
    public BaseResponse<BigDecimal> getMyTotalScore(HttpServletRequest httpRequest) {
        User loginUser = userService.getLoginUser(httpRequest);
        return ResultUtils.success(studentCompetitionRecordService.getMyTotalScore(loginUser.getId()));
    }

    @GetMapping("/get/{id}")
    @Operation(summary = "查看记录详情")
    public BaseResponse<StudentCompetitionRecordVO> getRecordById(@PathVariable Long id) {
        ThrowUtils.throwIf(id == null || id <= 0, ErrorCode.PARAMS_ERROR);
        StudentCompetitionRecord record = studentCompetitionRecordService.getById(id);
        StudentCompetitionRecordVO vo = studentCompetitionRecordService.getRecordVO(record);
        ThrowUtils.throwIf(vo == null, ErrorCode.NOT_FOUND_ERROR);
        return ResultUtils.success(vo);
    }

    @PostMapping("/delete")
    @Operation(summary = "删除记录")
    public BaseResponse<Boolean> deleteRecord(@RequestBody DeleteRequest deleteRequest, HttpServletRequest httpRequest) {
        if (deleteRequest == null || deleteRequest.getId() <= 0)
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(httpRequest);
        StudentCompetitionRecord record = studentCompetitionRecordService.getById(deleteRequest.getId());
        if (record == null) throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "记录不存在");
        if (!loginUser.getId().equals(record.getUserId()) && !UserConstant.ADMIN_ROLE.equals(loginUser.getUserRole()))
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权删除该记录");
        return ResultUtils.success(studentCompetitionRecordService.removeById(deleteRequest.getId()));
    }

    @PostMapping("/admin/list/page")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @Operation(summary = "管理员查看所有记录")
    public BaseResponse<Page<StudentCompetitionRecordVO>> adminListRecords(
            @RequestBody StudentCompetitionQueryRequest queryRequest) {
        ThrowUtils.throwIf(queryRequest == null, ErrorCode.PARAMS_ERROR);
        return ResultUtils.success(studentCompetitionRecordService.pageRecords(queryRequest));
    }

    @PostMapping("/admin/review")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @Operation(summary = "管理员审核")
    public BaseResponse<Boolean> adminReview(@RequestBody AdminReviewRequest request,
                                              HttpServletRequest httpRequest) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(httpRequest);
        studentCompetitionRecordService.adminReview(request.getId(), request.getReviewStatus(),
                request.getReviewComment(), loginUser.getId());
        return ResultUtils.success(true);
    }

    @GetMapping("/admin/export")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @Operation(summary = "导出学生竞赛得分Excel（v3格式）")
    public void exportRecords(HttpServletResponse response) {
        byte[] excelData = studentCompetitionRecordService.exportRecordsToExcel();
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition",
                "attachment; filename=student_competition_scores.xlsx");
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
        result.put("availableRanks", StudentScoringConstants.getAvailableRanks());
        result.put("rules", StudentScoringConstants.getAllScoringRules());
        result.put("organizerBaseCollege", StudentScoringConstants.ORG_BASE_COLLEGE);
        result.put("organizerBaseHigher", StudentScoringConstants.ORG_BASE_HIGHER);
        result.put("advisorBaseCollege", StudentScoringConstants.ADVISOR_BASE_COLLEGE);
        result.put("advisorBaseHigher", StudentScoringConstants.ADVISOR_BASE_HIGHER);
        result.put("industryMultiplier", StudentScoringConstants.INDUSTRY_MULTIPLIER);
        return ResultUtils.success(result);
    }

    @GetMapping("/available-grades")
    @Operation(summary = "获取指定竞赛等级的可选获奖等级")
    public BaseResponse<List<String>> getAvailableGrades(@RequestParam("rank") String rank) {
        return ResultUtils.success(StudentScoringConstants.getAvailableGrades(rank));
    }
}
