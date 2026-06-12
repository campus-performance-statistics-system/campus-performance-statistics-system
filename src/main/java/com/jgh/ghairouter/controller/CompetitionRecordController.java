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
import com.jgh.ghairouter.model.dto.competition.CompetitionAddRequest;
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
import org.springframework.web.bind.annotation.*;

/**
 * 比赛记录控制器
 */
@RestController
@RequestMapping("/competition")
@Tag(name = "比赛记录管理")
public class CompetitionRecordController {

    @Resource
    private CompetitionRecordService competitionRecordService;

    @Resource
    private UserService userService;

    /**
     * 提交比赛记录（参赛人员）
     */
    @PostMapping("/add")
    @Operation(summary = "提交比赛记录")
    public BaseResponse<Long> addRecord(@RequestBody CompetitionAddRequest request, HttpServletRequest httpRequest) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(httpRequest);

        Long recordId = competitionRecordService.addRecord(
                loginUser.getId(),
                request.getCompetitionName(),
                request.getCategoryId(),
                request.getProofImageUrl()
        );
        return ResultUtils.success(recordId);
    }

    /**
     * 查看自己的比赛记录（分页）
     */
    @PostMapping("/my/list/page")
    @Operation(summary = "查看我的比赛记录")
    public BaseResponse<Page<CompetitionRecordVO>> listMyRecords(
            @RequestBody CompetitionQueryRequest queryRequest,
            HttpServletRequest httpRequest) {
        ThrowUtils.throwIf(queryRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(httpRequest);
        queryRequest.setUserId(loginUser.getId());

        Page<CompetitionRecordVO> page = competitionRecordService.pageRecords(queryRequest);
        return ResultUtils.success(page);
    }

    /**
     * 查看单条记录详情
     */
    @GetMapping("/get/{id}")
    @Operation(summary = "查看记录详情")
    public BaseResponse<CompetitionRecordVO> getRecordById(@PathVariable Long id) {
        ThrowUtils.throwIf(id == null || id <= 0, ErrorCode.PARAMS_ERROR);
        CompetitionRecordVO vo = competitionRecordService.getRecordVO(
                competitionRecordService.getById(id));
        ThrowUtils.throwIf(vo == null, ErrorCode.NOT_FOUND_ERROR);
        return ResultUtils.success(vo);
    }

    /**
     * 删除比赛记录
     */
    @PostMapping("/delete")
    @Operation(summary = "删除比赛记录")
    public BaseResponse<Boolean> deleteRecord(@RequestBody DeleteRequest deleteRequest, HttpServletRequest httpRequest) {
        if (deleteRequest == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser(httpRequest);
        // 简单鉴权：只能删除自己的记录，管理员可以删除任意记录
        com.jgh.ghairouter.model.entity.CompetitionRecord record =
                competitionRecordService.getById(deleteRequest.getId());
        if (record == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "记录不存在");
        }
        if (!loginUser.getId().equals(record.getUserId()) && !UserConstant.ADMIN_ROLE.equals(loginUser.getUserRole())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权删除该记录");
        }
        boolean result = competitionRecordService.removeById(deleteRequest.getId());
        return ResultUtils.success(result);
    }

    /**
     * 管理员查看所有比赛记录（分页）
     */
    @PostMapping("/admin/list/page")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @Operation(summary = "管理员查看所有记录")
    public BaseResponse<Page<CompetitionRecordVO>> adminListRecords(
            @RequestBody CompetitionQueryRequest queryRequest) {
        ThrowUtils.throwIf(queryRequest == null, ErrorCode.PARAMS_ERROR);
        Page<CompetitionRecordVO> page = competitionRecordService.pageRecords(queryRequest);
        return ResultUtils.success(page);
    }

    /**
     * 管理员人工审核
     */
    @PostMapping("/admin/review")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @Operation(summary = "管理员审核")
    public BaseResponse<Boolean> adminReview(@RequestBody AdminReviewRequest request,
                                              HttpServletRequest httpRequest) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(httpRequest);

        competitionRecordService.adminReview(
                request.getId(),
                request.getReviewStatus(),
                request.getReviewComment(),
                loginUser.getId()
        );
        return ResultUtils.success(true);
    }
}
