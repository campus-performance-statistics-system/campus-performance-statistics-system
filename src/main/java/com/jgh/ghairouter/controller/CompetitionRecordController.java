package com.jgh.ghairouter.controller;

import com.jgh.ghairouter.annotation.AuthCheck;
import com.jgh.ghairouter.common.BaseResponse;
import com.jgh.ghairouter.common.DeleteRequest;
import com.jgh.ghairouter.common.ResultUtils;
import com.jgh.ghairouter.constant.UserConstant;
import com.jgh.ghairouter.exception.BusinessException;
import com.jgh.ghairouter.exception.ErrorCode;
import com.jgh.ghairouter.exception.ThrowUtils;
import com.jgh.ghairouter.model.dto.competition.AdminReviewRequest;
import com.jgh.ghairouter.model.dto.competition.CompetitionQueryRequest;
import com.jgh.ghairouter.model.entity.User;
import com.jgh.ghairouter.model.vo.CompetitionRecordVO;
import com.jgh.ghairouter.service.CompetitionRecordService;
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

@RestController
@RequestMapping("/competition")
@Tag(name = "比赛记录管理")
public class CompetitionRecordController {

    @Resource
    private CompetitionRecordService competitionRecordService;
    @Resource
    private UserService userService;

    @PostMapping("/add")
    @Operation(summary = "提交比赛记录")
    public BaseResponse<Long> addRecord(
            @RequestParam(value = "categoryId", required = false) Long categoryId,
            @RequestParam(value = "activityTypeId", required = false) Long activityTypeId,
            @RequestParam("competitionName") String competitionName,
            @RequestParam(value = "sponsorUnit", required = false) String sponsorUnit,
            @RequestParam("competitionRank") String competitionRank,
            @RequestParam("gradeName") String gradeName,
            @RequestParam(value = "baseScore", required = false) BigDecimal baseScore,
            @RequestParam(value = "teamMemberNum", defaultValue = "1") Integer teamMemberNum,
            @RequestParam("firstAuthorId") Long firstAuthorId,
            @RequestParam(value = "otherAuthorIds", required = false) List<Long> otherAuthorIds,
            @RequestParam("file") MultipartFile file,
            HttpServletRequest httpRequest) {
        User loginUser = userService.getLoginUser(httpRequest);
        Long recordId = competitionRecordService.addRecord(
                loginUser.getId(), categoryId, activityTypeId,
                competitionName, sponsorUnit,
                competitionRank, gradeName, baseScore,
                teamMemberNum, firstAuthorId, otherAuthorIds, file);
        return ResultUtils.success(recordId);
    }

    @PostMapping("/my/list/page")
    @Operation(summary = "查看我的比赛记录（含共享记录）")
    public BaseResponse<Page<CompetitionRecordVO>> listMyRecords(
            @RequestBody CompetitionQueryRequest queryRequest,
            HttpServletRequest httpRequest) {
        ThrowUtils.throwIf(queryRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(httpRequest);
        return ResultUtils.success(
                competitionRecordService.pageMyRelatedRecords(loginUser.getId(), queryRequest));
    }

    @GetMapping("/my/total-score")
    @Operation(summary = "获取我的总得分")
    public BaseResponse<BigDecimal> getMyTotalScore(HttpServletRequest httpRequest) {
        User loginUser = userService.getLoginUser(httpRequest);
        return ResultUtils.success(competitionRecordService.getMyTotalScore(loginUser.getId()));
    }

    @GetMapping("/get/{id}")
    @Operation(summary = "查看记录详情")
    public BaseResponse<CompetitionRecordVO> getRecordById(@PathVariable Long id) {
        ThrowUtils.throwIf(id == null || id <= 0, ErrorCode.PARAMS_ERROR);
        CompetitionRecordVO vo = competitionRecordService.getRecordVO(
                competitionRecordService.getById(id));
        ThrowUtils.throwIf(vo == null, ErrorCode.NOT_FOUND_ERROR);
        return ResultUtils.success(vo);
    }

    @PostMapping("/delete")
    @Operation(summary = "删除比赛记录")
    public BaseResponse<Boolean> deleteRecord(@RequestBody DeleteRequest deleteRequest, HttpServletRequest httpRequest) {
        if (deleteRequest == null || deleteRequest.getId() <= 0)
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(httpRequest);
        com.jgh.ghairouter.model.entity.CompetitionRecord record =
                competitionRecordService.getById(deleteRequest.getId());
        if (record == null) throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "记录不存在");
        if (!loginUser.getId().equals(record.getUserId()) && !UserConstant.ADMIN_ROLE.equals(loginUser.getUserRole()))
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权删除该记录");
        return ResultUtils.success(competitionRecordService.removeById(deleteRequest.getId()));
    }

    @PostMapping("/admin/list/page")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @Operation(summary = "管理员查看所有记录")
    public BaseResponse<Page<CompetitionRecordVO>> adminListRecords(
            @RequestBody CompetitionQueryRequest queryRequest) {
        ThrowUtils.throwIf(queryRequest == null, ErrorCode.PARAMS_ERROR);
        return ResultUtils.success(competitionRecordService.pageRecords(queryRequest));
    }

    @PostMapping("/admin/add")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @Operation(summary = "管理员添加比赛记录")
    public BaseResponse<Long> adminAddRecord(
            @RequestParam("competitionName") String competitionName,
            @RequestParam(value = "sponsorUnit", required = false) String sponsorUnit,
            @RequestParam("rankId") Long rankId,
            @RequestParam("gradeName") String gradeName,
            @RequestParam("baseScore") BigDecimal baseScore,
            @RequestParam(value = "teamMemberNum", defaultValue = "1") Integer teamMemberNum,
            @RequestParam("firstAuthorId") Long firstAuthorId,
            @RequestParam(value = "otherAuthorIds", required = false) List<Long> otherAuthorIds,
            @RequestParam(value = "file", required = false) MultipartFile file,
            HttpServletRequest httpRequest) {
        User loginUser = userService.getLoginUser(httpRequest);
        Long recordId = competitionRecordService.adminAddRecord(
                loginUser.getId(), competitionName, sponsorUnit,
                rankId, gradeName, baseScore,
                teamMemberNum, firstAuthorId, otherAuthorIds, file);
        return ResultUtils.success(recordId);
    }

    @PostMapping("/admin/review")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @Operation(summary = "管理员审核")
    public BaseResponse<Boolean> adminReview(@RequestBody AdminReviewRequest request,
                                              HttpServletRequest httpRequest) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(httpRequest);
        competitionRecordService.adminReview(request.getId(), request.getReviewStatus(),
                request.getReviewComment(), loginUser.getId());
        return ResultUtils.success(true);
    }

    @GetMapping("/admin/export")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @Operation(summary = "导出比赛得分详情Excel")
    public void exportRecords(HttpServletResponse response) {
        byte[] excelData = competitionRecordService.exportRecordsToExcel();
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition",
                "attachment; filename=competition_scores.xlsx");
        response.setContentLength(excelData.length);
        try {
            response.getOutputStream().write(excelData);
            response.getOutputStream().flush();
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "导出失败: " + e.getMessage());
        }
    }
}
