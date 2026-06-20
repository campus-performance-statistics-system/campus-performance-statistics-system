package com.jgh.ghairouter.controller;

import com.jgh.ghairouter.annotation.AuthCheck;
import com.jgh.ghairouter.common.BaseResponse;
import com.jgh.ghairouter.common.DeleteRequest;
import com.jgh.ghairouter.common.ResultUtils;
import com.jgh.ghairouter.constant.UserConstant;
import com.jgh.ghairouter.exception.BusinessException;
import com.jgh.ghairouter.exception.ErrorCode;
import com.jgh.ghairouter.exception.ThrowUtils;
import com.jgh.ghairouter.model.constants.ResearchScoringConstants;
import com.jgh.ghairouter.model.dto.competition.AdminReviewRequest;
import com.jgh.ghairouter.model.dto.competition.ResearchAchievementQueryRequest;
import com.jgh.ghairouter.model.entity.ResearchAchievementRecord;
import com.jgh.ghairouter.model.entity.User;
import com.jgh.ghairouter.model.vo.ResearchAchievementRecordVO;
import com.jgh.ghairouter.service.ResearchAchievementRecordService;
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
@RequestMapping("/research-achievement")
@Tag(name = "科研及教材业绩记录管理")
public class ResearchAchievementRecordController {

    @Resource
    private ResearchAchievementRecordService researchAchievementRecordService;
    @Resource
    private UserService userService;

    @PostMapping("/add")
    @Operation(summary = "提交科研及教材业绩记录")
    public BaseResponse<Long> addRecord(
            @RequestParam("subType") String subType,
            @RequestParam("achievementName") String achievementName,
            @RequestParam(value = "projectSource", required = false) String projectSource,
            @RequestParam(value = "fundingAmount", required = false) BigDecimal fundingAmount,
            @RequestParam(value = "patentNumber", required = false) String patentNumber,
            @RequestParam(value = "patentType", required = false) String patentType,
            @RequestParam(value = "wordCount", required = false) BigDecimal wordCount,
            @RequestParam(value = "textbookType", required = false) String textbookType,
            @RequestParam(value = "memberData", required = false) String memberData,
            @RequestParam(value = "scoreData", required = false) String scoreData,
            @RequestParam(value = "file", required = false) MultipartFile file,
            HttpServletRequest httpRequest) {
        User loginUser = userService.getLoginUser(httpRequest);
        Long recordId = researchAchievementRecordService.addRecord(
                loginUser.getId(),
                subType, achievementName,
                projectSource, fundingAmount,
                patentNumber, patentType,
                wordCount, textbookType,
                memberData, scoreData,
                file);
        return ResultUtils.success(recordId);
    }

    @PostMapping("/my/list/page")
    @Operation(summary = "查看我的记录")
    public BaseResponse<Page<ResearchAchievementRecordVO>> listMyRecords(
            @RequestBody ResearchAchievementQueryRequest queryRequest,
            HttpServletRequest httpRequest) {
        ThrowUtils.throwIf(queryRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(httpRequest);
        return ResultUtils.success(
                researchAchievementRecordService.pageMyRelatedRecords(loginUser.getId(), queryRequest));
    }

    @GetMapping("/my/total-score")
    @Operation(summary = "获取我的总得分")
    public BaseResponse<BigDecimal> getMyTotalScore(HttpServletRequest httpRequest) {
        User loginUser = userService.getLoginUser(httpRequest);
        return ResultUtils.success(researchAchievementRecordService.getMyTotalScore(loginUser.getId()));
    }

    @GetMapping("/get/{id}")
    @Operation(summary = "查看记录详情")
    public BaseResponse<ResearchAchievementRecordVO> getRecordById(@PathVariable Long id) {
        ThrowUtils.throwIf(id == null || id <= 0, ErrorCode.PARAMS_ERROR);
        ResearchAchievementRecord record = researchAchievementRecordService.getById(id);
        ResearchAchievementRecordVO vo = researchAchievementRecordService.getRecordVO(record);
        ThrowUtils.throwIf(vo == null, ErrorCode.NOT_FOUND_ERROR);
        return ResultUtils.success(vo);
    }

    @PostMapping("/delete")
    @Operation(summary = "删除记录")
    public BaseResponse<Boolean> deleteRecord(@RequestBody DeleteRequest deleteRequest, HttpServletRequest httpRequest) {
        if (deleteRequest == null || deleteRequest.getId() <= 0)
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(httpRequest);
        ResearchAchievementRecord record = researchAchievementRecordService.getById(deleteRequest.getId());
        if (record == null) throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "记录不存在");
        if (!loginUser.getId().equals(record.getUserId()) && !UserConstant.ADMIN_ROLE.equals(loginUser.getUserRole()))
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权删除该记录");
        return ResultUtils.success(researchAchievementRecordService.removeById(deleteRequest.getId()));
    }

    @PostMapping("/admin/list/page")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @Operation(summary = "管理员查看所有记录")
    public BaseResponse<Page<ResearchAchievementRecordVO>> adminListRecords(
            @RequestBody ResearchAchievementQueryRequest queryRequest) {
        ThrowUtils.throwIf(queryRequest == null, ErrorCode.PARAMS_ERROR);
        return ResultUtils.success(researchAchievementRecordService.pageRecords(queryRequest));
    }

    @PostMapping("/admin/review")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @Operation(summary = "管理员审核")
    public BaseResponse<Boolean> adminReview(@RequestBody AdminReviewRequest request,
                                              HttpServletRequest httpRequest) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(httpRequest);
        researchAchievementRecordService.adminReview(request.getId(), request.getReviewStatus(),
                request.getReviewComment(), loginUser.getId());
        return ResultUtils.success(true);
    }

    @GetMapping("/admin/export")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @Operation(summary = "导出科研及教材业绩得分Excel")
    public void exportRecords(HttpServletResponse response) {
        byte[] excelData = researchAchievementRecordService.exportRecordsToExcel();
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition",
                "attachment; filename=research_achievement_scores.xlsx");
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
        result.put("subTypes", ResearchScoringConstants.getAvailableSubTypes());
        result.put("rules", ResearchScoringConstants.getAllScoringRules());
        result.put("projectLeaderBase", ResearchScoringConstants.PROJECT_LEADER_BASE);
        result.put("patentInvention", ResearchScoringConstants.PATENT_INVENTION);
        result.put("patentUtilityModel", ResearchScoringConstants.PATENT_UTILITY_MODEL);
        result.put("textbookPublished", ResearchScoringConstants.TEXTBOOK_PUBLISHED);
        result.put("textbookFirstHandout", ResearchScoringConstants.TEXTBOOK_FIRST_HANDOUT);
        result.put("textbookRevisedHandout", ResearchScoringConstants.TEXTBOOK_REVISED_HANDOUT);
        result.put("ratioThreeLeader", ResearchScoringConstants.RATIO_3_LEADER);
        result.put("ratioNLeader", ResearchScoringConstants.RATIO_N_LEADER);
        return ResultUtils.success(result);
    }
}
