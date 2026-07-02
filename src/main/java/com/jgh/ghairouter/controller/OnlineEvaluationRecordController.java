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
import com.jgh.ghairouter.model.dto.competition.OnlineEvaluationQueryRequest;
import com.jgh.ghairouter.model.entity.OnlineEvaluationRecord;
import com.jgh.ghairouter.model.entity.User;
import com.jgh.ghairouter.model.vo.OnlineEvaluationRecordVO;
import com.jgh.ghairouter.service.OnlineEvaluationRecordService;
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

@RestController
@RequestMapping("/online-evaluation")
@Tag(name = "网上评教记录管理")
public class OnlineEvaluationRecordController {

    @Resource
    private OnlineEvaluationRecordService onlineEvaluationRecordService;
    @Resource
    private UserService userService;

    @PostMapping("/add")
    @Operation(summary = "提交网上评教记录")
    public BaseResponse<Long> addRecord(
            @RequestParam("teacherName") String teacherName,
            @RequestParam("teacherType") String teacherType,
            @RequestParam("academicYear") String academicYear,
            @RequestParam("semester") String semester,
            @RequestParam("courseCode") String courseCode,
            @RequestParam("courseName") String courseName,
            @RequestParam("participantCount") Integer participantCount,
            @RequestParam("averageScore") BigDecimal averageScore,
            @RequestParam(value = "file", required = false) MultipartFile file,
            HttpServletRequest httpRequest) {
        User loginUser = userService.getLoginUser(httpRequest);
        Long recordId = onlineEvaluationRecordService.addRecord(
                loginUser.getId(),
                teacherName,
                teacherType,
                academicYear,
                semester,
                courseCode,
                courseName,
                participantCount,
                averageScore,
                file);
        return ResultUtils.success(recordId);
    }

    @PostMapping("/my/list/page")
    @Operation(summary = "查看我的记录")
    public BaseResponse<Page<OnlineEvaluationRecordVO>> listMyRecords(
            @RequestBody OnlineEvaluationQueryRequest queryRequest,
            HttpServletRequest httpRequest) {
        ThrowUtils.throwIf(queryRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(httpRequest);
        return ResultUtils.success(
                onlineEvaluationRecordService.pageMyRelatedRecords(loginUser.getId(), queryRequest));
    }

    @GetMapping("/get/{id}")
    @Operation(summary = "查看记录详情")
    public BaseResponse<OnlineEvaluationRecordVO> getRecordById(@PathVariable Long id) {
        ThrowUtils.throwIf(id == null || id <= 0, ErrorCode.PARAMS_ERROR);
        OnlineEvaluationRecord record = onlineEvaluationRecordService.getById(id);
        OnlineEvaluationRecordVO vo = onlineEvaluationRecordService.getRecordVO(record);
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
        OnlineEvaluationRecord record = onlineEvaluationRecordService.getById(deleteRequest.getId());
        if (record == null) throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "记录不存在");
        if (!loginUser.getId().equals(record.getUserId())
                && !UserConstant.ADMIN_ROLE.equals(loginUser.getUserRole()))
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权删除该记录");
        return ResultUtils.success(onlineEvaluationRecordService.removeById(deleteRequest.getId()));
    }

    @PostMapping("/admin/list/page")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @Operation(summary = "管理员查看所有记录")
    public BaseResponse<Page<OnlineEvaluationRecordVO>> adminListRecords(
            @RequestBody OnlineEvaluationQueryRequest queryRequest) {
        ThrowUtils.throwIf(queryRequest == null, ErrorCode.PARAMS_ERROR);
        return ResultUtils.success(onlineEvaluationRecordService.pageRecords(queryRequest));
    }

    @PostMapping("/admin/review")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @Operation(summary = "管理员审核")
    public BaseResponse<Boolean> adminReview(@RequestBody AdminReviewRequest request,
                                              HttpServletRequest httpRequest) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(httpRequest);
        onlineEvaluationRecordService.adminReview(request.getId(), request.getReviewStatus(),
                request.getReviewComment(), loginUser.getId());
        return ResultUtils.success(true);
    }

    @GetMapping("/admin/export")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @Operation(summary = "导出网上评教Excel")
    public void exportRecords(HttpServletResponse response) {
        byte[] excelData = onlineEvaluationRecordService.exportRecordsToExcel();
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition",
                "attachment; filename=online_evaluation.xlsx");
        response.setContentLength(excelData.length);
        try {
            response.getOutputStream().write(excelData);
            response.getOutputStream().flush();
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "导出失败: " + e.getMessage());
        }
    }

    @GetMapping("/my/total-score")
    @Operation(summary = "获取我的网上评教总得分（加权平均分）")
    public BaseResponse<BigDecimal> getMyTotalScore(HttpServletRequest httpRequest) {
        User loginUser = userService.getLoginUser(httpRequest);
        return ResultUtils.success(onlineEvaluationRecordService.getMyTotalScore(loginUser.getId()));
    }
}
