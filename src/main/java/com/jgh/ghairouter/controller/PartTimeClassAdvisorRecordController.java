package com.jgh.ghairouter.controller;

import com.jgh.ghairouter.annotation.AuthCheck;
import com.jgh.ghairouter.common.BaseResponse;
import com.jgh.ghairouter.common.DeleteRequest;
import com.jgh.ghairouter.common.ResultUtils;
import com.jgh.ghairouter.constant.UserConstant;
import com.jgh.ghairouter.exception.BusinessException;
import com.jgh.ghairouter.exception.ErrorCode;
import com.jgh.ghairouter.exception.ThrowUtils;
import com.jgh.ghairouter.model.constants.PartTimeClassAdvisorScoringConstants;
import com.jgh.ghairouter.model.dto.competition.AdminReviewRequest;
import com.jgh.ghairouter.model.dto.competition.PartTimeClassAdvisorQueryRequest;
import com.jgh.ghairouter.model.entity.PartTimeClassAdvisorRecord;
import com.jgh.ghairouter.model.entity.User;
import com.jgh.ghairouter.model.vo.PartTimeClassAdvisorRecordVO;
import com.jgh.ghairouter.service.PartTimeClassAdvisorRecordService;
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
@RequestMapping("/part-time-class-advisor")
@Tag(name = "兼职班主任业绩记录管理")
public class PartTimeClassAdvisorRecordController {

    @Resource
    private PartTimeClassAdvisorRecordService advisorRecordService;
    @Resource
    private UserService userService;

    @PostMapping("/add")
    @Operation(summary = "提交兼职班主任业绩记录")
    public BaseResponse<Long> addRecord(
            @RequestParam("teacherName") String teacherName,
            @RequestParam("classId") String classId,
            @RequestParam(value = "studyStyleWorkReq", required = false) BigDecimal studyStyleWorkReq,
            @RequestParam(value = "studyStyleEffect", required = false) BigDecimal studyStyleEffect,
            @RequestParam(value = "safetyEduWorkReq", required = false) BigDecimal safetyEduWorkReq,
            @RequestParam(value = "safetyEduEffect", required = false) BigDecimal safetyEduEffect,
            @RequestParam(value = "strugglingStudentWorkReq", required = false) BigDecimal strugglingStudentWorkReq,
            @RequestParam(value = "strugglingStudentEffect", required = false) BigDecimal strugglingStudentEffect,
            @RequestParam(value = "achievementSafety", required = false) BigDecimal achievementSafety,
            @RequestParam(value = "achievementStudyStyle", required = false) BigDecimal achievementStudyStyle,
            @RequestParam(value = "achievementStruggling", required = false) BigDecimal achievementStruggling,
            @RequestParam(value = "isFreshmenOrGraduating", required = false) Integer isFreshmenOrGraduating,
            @RequestParam(value = "file", required = false) MultipartFile file,
            HttpServletRequest httpRequest) {
        User loginUser = userService.getLoginUser(httpRequest);
        Long recordId = advisorRecordService.addRecord(
                loginUser.getId(),
                teacherName, classId,
                studyStyleWorkReq, studyStyleEffect,
                safetyEduWorkReq, safetyEduEffect,
                strugglingStudentWorkReq, strugglingStudentEffect,
                achievementSafety, achievementStudyStyle, achievementStruggling,
                isFreshmenOrGraduating,
                file);
        return ResultUtils.success(recordId);
    }

    @PostMapping("/my/list/page")
    @Operation(summary = "查看我的记录")
    public BaseResponse<Page<PartTimeClassAdvisorRecordVO>> listMyRecords(
            @RequestBody PartTimeClassAdvisorQueryRequest queryRequest,
            HttpServletRequest httpRequest) {
        ThrowUtils.throwIf(queryRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(httpRequest);
        return ResultUtils.success(
                advisorRecordService.pageMyRelatedRecords(loginUser.getId(), queryRequest));
    }

    @GetMapping("/my/total-score")
    @Operation(summary = "获取我的总得分")
    public BaseResponse<BigDecimal> getMyTotalScore(HttpServletRequest httpRequest) {
        User loginUser = userService.getLoginUser(httpRequest);
        return ResultUtils.success(advisorRecordService.getMyTotalScore(loginUser.getId()));
    }

    @GetMapping("/get/{id}")
    @Operation(summary = "查看记录详情")
    public BaseResponse<PartTimeClassAdvisorRecordVO> getRecordById(@PathVariable Long id) {
        ThrowUtils.throwIf(id == null || id <= 0, ErrorCode.PARAMS_ERROR);
        PartTimeClassAdvisorRecord record = advisorRecordService.getById(id);
        PartTimeClassAdvisorRecordVO vo = advisorRecordService.getRecordVO(record);
        ThrowUtils.throwIf(vo == null, ErrorCode.NOT_FOUND_ERROR);
        return ResultUtils.success(vo);
    }

    @PostMapping("/delete")
    @Operation(summary = "删除记录")
    public BaseResponse<Boolean> deleteRecord(@RequestBody DeleteRequest deleteRequest,
                                               HttpServletRequest httpRequest) {
        if (deleteRequest == null || deleteRequest.getId() <= 0)
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(httpRequest);
        PartTimeClassAdvisorRecord record = advisorRecordService.getById(deleteRequest.getId());
        if (record == null) throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "记录不存在");
        if (!loginUser.getId().equals(record.getUserId())
                && !UserConstant.ADMIN_ROLE.equals(loginUser.getUserRole()))
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权删除该记录");
        return ResultUtils.success(advisorRecordService.removeById(deleteRequest.getId()));
    }

    @PostMapping("/admin/list/page")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @Operation(summary = "管理员查看所有记录")
    public BaseResponse<Page<PartTimeClassAdvisorRecordVO>> adminListRecords(
            @RequestBody PartTimeClassAdvisorQueryRequest queryRequest) {
        ThrowUtils.throwIf(queryRequest == null, ErrorCode.PARAMS_ERROR);
        return ResultUtils.success(advisorRecordService.pageRecords(queryRequest));
    }

    @PostMapping("/admin/review")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @Operation(summary = "管理员审核")
    public BaseResponse<Boolean> adminReview(@RequestBody AdminReviewRequest request,
                                              HttpServletRequest httpRequest) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(httpRequest);
        advisorRecordService.adminReview(request.getId(), request.getReviewStatus(),
                request.getReviewComment(), loginUser.getId());
        return ResultUtils.success(true);
    }

    @GetMapping("/admin/export")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @Operation(summary = "导出兼职班主任业绩得分Excel")
    public void exportRecords(HttpServletResponse response) {
        byte[] excelData = advisorRecordService.exportRecordsToExcel();
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition",
                "attachment; filename=part_time_class_advisor_scores.xlsx");
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
        result.put("rules", PartTimeClassAdvisorScoringConstants.getAllScoringRules());
        result.put("maxStudyStyleWorkReq", PartTimeClassAdvisorScoringConstants.MAX_STUDY_STYLE_WORK_REQ);
        result.put("maxStudyStyleEffect", PartTimeClassAdvisorScoringConstants.MAX_STUDY_STYLE_EFFECT);
        result.put("maxSafetyEduWorkReq", PartTimeClassAdvisorScoringConstants.MAX_SAFETY_EDU_WORK_REQ);
        result.put("maxSafetyEduEffect", PartTimeClassAdvisorScoringConstants.MAX_SAFETY_EDU_EFFECT);
        result.put("maxStrugglingWorkReq", PartTimeClassAdvisorScoringConstants.MAX_STRUGGLING_WORK_REQ);
        result.put("maxStrugglingEffect", PartTimeClassAdvisorScoringConstants.MAX_STRUGGLING_EFFECT);
        result.put("maxAchievementSafety", PartTimeClassAdvisorScoringConstants.MAX_ACHIEVEMENT_SAFETY);
        result.put("maxAchievementStudyStyle", PartTimeClassAdvisorScoringConstants.MAX_ACHIEVEMENT_STUDY_STYLE);
        result.put("maxAchievementStruggling", PartTimeClassAdvisorScoringConstants.MAX_ACHIEVEMENT_STRUGGLING);
        result.put("adminClassScoreNormal", PartTimeClassAdvisorScoringConstants.ADMIN_CLASS_SCORE_NORMAL);
        result.put("adminClassScoreHalf", PartTimeClassAdvisorScoringConstants.ADMIN_CLASS_SCORE_HALF);
        return ResultUtils.success(result);
    }
}
