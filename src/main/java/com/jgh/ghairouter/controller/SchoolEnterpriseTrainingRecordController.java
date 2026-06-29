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
import com.jgh.ghairouter.model.dto.competition.SchoolEnterpriseTrainingQueryRequest;
import com.jgh.ghairouter.model.entity.SchoolEnterpriseTrainingRecord;
import com.jgh.ghairouter.model.entity.User;
import com.jgh.ghairouter.model.vo.SchoolEnterpriseTrainingRecordVO;
import com.jgh.ghairouter.service.SchoolEnterpriseTrainingRecordService;
import com.jgh.ghairouter.service.UserService;
import com.mybatisflex.core.paginate.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/school-enterprise-training")
@Tag(name = "校企联合培养记录管理")
public class SchoolEnterpriseTrainingRecordController {

    @Resource
    private SchoolEnterpriseTrainingRecordService recordService;
    @Resource
    private UserService userService;

    @PostMapping("/add")
    @Operation(summary = "提交校企联合培养记录")
    public BaseResponse<Long> addRecord(
            @RequestParam("studentName") String studentName,
            @RequestParam("studentId") String studentId,
            @RequestParam("major") String major,
            @RequestParam("companyName") String companyName,
            @RequestParam("remark") String remark,
            @RequestParam("projectCollectionStatus") String projectCollectionStatus,
            @RequestParam("advisorName") String advisorName,
            @RequestParam("counselorName") String counselorName,
            @RequestParam(value = "file", required = false) MultipartFile file,
            HttpServletRequest httpRequest) {
        User loginUser = userService.getLoginUser(httpRequest);
        Long recordId = recordService.addRecord(
                loginUser.getId(),
                studentName,
                studentId,
                major,
                companyName,
                remark,
                projectCollectionStatus,
                advisorName,
                counselorName,
                file);
        return ResultUtils.success(recordId);
    }

    @PostMapping("/my/list/page")
    @Operation(summary = "查看我的记录")
    public BaseResponse<Page<SchoolEnterpriseTrainingRecordVO>> listMyRecords(
            @RequestBody SchoolEnterpriseTrainingQueryRequest queryRequest,
            HttpServletRequest httpRequest) {
        ThrowUtils.throwIf(queryRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(httpRequest);
        return ResultUtils.success(
                recordService.pageMyRelatedRecords(loginUser.getId(), queryRequest));
    }

    @GetMapping("/get/{id}")
    @Operation(summary = "查看记录详情")
    public BaseResponse<SchoolEnterpriseTrainingRecordVO> getRecordById(@PathVariable Long id) {
        ThrowUtils.throwIf(id == null || id <= 0, ErrorCode.PARAMS_ERROR);
        SchoolEnterpriseTrainingRecord record = recordService.getById(id);
        SchoolEnterpriseTrainingRecordVO vo = recordService.getRecordVO(record);
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
        SchoolEnterpriseTrainingRecord record = recordService.getById(deleteRequest.getId());
        if (record == null) throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "记录不存在");
        if (!loginUser.getId().equals(record.getUserId())
                && !UserConstant.ADMIN_ROLE.equals(loginUser.getUserRole()))
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权删除该记录");
        return ResultUtils.success(recordService.removeById(deleteRequest.getId()));
    }

    @PostMapping("/admin/list/page")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @Operation(summary = "管理员查看所有记录")
    public BaseResponse<Page<SchoolEnterpriseTrainingRecordVO>> adminListRecords(
            @RequestBody SchoolEnterpriseTrainingQueryRequest queryRequest) {
        ThrowUtils.throwIf(queryRequest == null, ErrorCode.PARAMS_ERROR);
        return ResultUtils.success(recordService.pageRecords(queryRequest));
    }

    @PostMapping("/admin/review")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @Operation(summary = "管理员审核")
    public BaseResponse<Boolean> adminReview(@RequestBody AdminReviewRequest request,
                                              HttpServletRequest httpRequest) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(httpRequest);
        recordService.adminReview(request.getId(), request.getReviewStatus(),
                request.getReviewComment(), loginUser.getId());
        return ResultUtils.success(true);
    }

    @GetMapping("/admin/export")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @Operation(summary = "导出校企联合培养Excel")
    public void exportRecords(HttpServletResponse response) {
        byte[] excelData = recordService.exportRecordsToExcel();
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition",
                "attachment; filename=school_enterprise_training.xlsx");
        response.setContentLength(excelData.length);
        try {
            response.getOutputStream().write(excelData);
            response.getOutputStream().flush();
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "导出失败: " + e.getMessage());
        }
    }
}
