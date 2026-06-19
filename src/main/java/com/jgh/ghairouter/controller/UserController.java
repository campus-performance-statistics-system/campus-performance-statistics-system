package com.jgh.ghairouter.controller;

import cn.hutool.core.bean.BeanUtil;
import com.jgh.ghairouter.annotation.AuthCheck;
import com.jgh.ghairouter.common.BaseResponse;
import com.jgh.ghairouter.common.DeleteRequest;
import com.jgh.ghairouter.common.ResultUtils;
import com.jgh.ghairouter.constant.UserConstant;
import com.jgh.ghairouter.exception.BusinessException;
import com.jgh.ghairouter.exception.ErrorCode;
import com.jgh.ghairouter.exception.ThrowUtils;
import com.jgh.ghairouter.model.dto.user.*;
import com.jgh.ghairouter.model.entity.User;
import com.jgh.ghairouter.model.enums.UserRoleEnum;
import com.jgh.ghairouter.model.vo.LoginUserVO;
import com.jgh.ghairouter.model.vo.UserVO;
import com.jgh.ghairouter.service.UserService;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 用户控制层
 *
 */
@RestController
@RequestMapping("/user")
public class UserController {

    @Resource
    private UserService userService;

    /**
     * 用户注册
     *
     * @param userRegisterRequest 用户注册请求
     * @return 注册结果
     */
    @PostMapping("/register")
    public BaseResponse<Long> userRegister(@RequestBody UserRegisterRequest userRegisterRequest) {
        ThrowUtils.throwIf(userRegisterRequest == null, ErrorCode.PARAMS_ERROR);
        String userAccount = userRegisterRequest.getUserAccount();
        String userPassword = userRegisterRequest.getUserPassword();
        String checkPassword = userRegisterRequest.getCheckPassword();
        long result = userService.userRegister(userAccount, userPassword, checkPassword);
        return ResultUtils.success(result);
    }

    /**
     * 用户登录
     *
     * @param userLoginRequest 用户登录请求
     * @param request          请求对象
     * @return 脱敏后的用户登录信息
     */
    @PostMapping("/login")
    public BaseResponse<LoginUserVO> userLogin(@RequestBody UserLoginRequest userLoginRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(userLoginRequest == null, ErrorCode.PARAMS_ERROR);
        String userAccount = userLoginRequest.getUserAccount();
        String userPassword = userLoginRequest.getUserPassword();
        LoginUserVO loginUserVO = userService.userLogin(userAccount, userPassword, request);
        return ResultUtils.success(loginUserVO);
    }

    @GetMapping("/get/login")
    public BaseResponse<LoginUserVO> getLoginUser(HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(userService.getLoginUserVO(loginUser));
    }

    /**
     * 用户注销
     *
     * @param request 请求对象
     * @return
     */
    @PostMapping("/logout")
    public BaseResponse<Boolean> userLogout(HttpServletRequest request) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        boolean result = userService.userLogout(request);
        return ResultUtils.success(result);
    }

    /**
     * 创建用户
     */
    @PostMapping("/add")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Long> addUser(@RequestBody UserAddRequest userAddRequest) {
        ThrowUtils.throwIf(userAddRequest == null, ErrorCode.PARAMS_ERROR);
        User user = new User();
        BeanUtil.copyProperties(userAddRequest, user);
        // 默认密码 12345678
        final String DEFAULT_PASSWORD = "12345678";
        String encryptPassword = userService.getEncryptPassword(DEFAULT_PASSWORD);
        user.setUserPassword(encryptPassword);
        boolean result = userService.save(user);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(user.getId());
    }

    /**
     * 批量导入用户（管理员）
     * Excel格式：第一列工号（账号），第二列姓名
     */
    @PostMapping("/batch-import")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @Operation(summary = "批量导入用户")
    public BaseResponse<BatchImportResult> batchImportUsers(
            @RequestParam("file") MultipartFile file) {
        ThrowUtils.throwIf(file == null || file.isEmpty(), ErrorCode.PARAMS_ERROR, "请上传文件");

        BatchImportResult result = BatchImportResult.empty();
        final String DEFAULT_PASSWORD = "xky12345678";
        String encryptPassword = userService.getEncryptPassword(DEFAULT_PASSWORD);

        String filename = file.getOriginalFilename();
        if (filename == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件名不能为空");
        }

        try {
            if (filename.endsWith(".csv")) {
                importFromCsv(file.getInputStream(), encryptPassword, result);
            } else {
                importFromExcel(file.getInputStream(), encryptPassword, result);
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "文件解析失败: " + e.getMessage());
        }

        return ResultUtils.success(result);
    }

    /**
     * 从Excel (.xlsx) 导入
     */
    private void importFromExcel(InputStream is, String encryptPassword,
                                  BatchImportResult result) throws Exception {
        try (Workbook workbook = new XSSFWorkbook(is)) {
            Sheet sheet = workbook.getSheetAt(0);
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                Cell accountCell = row.getCell(0);
                if (accountCell == null) continue;
                String userAccount = getCellStringValue(accountCell).trim();
                if (userAccount.isEmpty()) continue;

                Cell nameCell = row.getCell(1);
                String userName = nameCell != null ? getCellStringValue(nameCell).trim() : userAccount;

                createUserIfNotExists(userAccount, userName, encryptPassword, result, i + 1);
            }
        }
    }

    /**
     * 从CSV导入
     */
    private void importFromCsv(InputStream is, String encryptPassword,
                                BatchImportResult result) throws Exception {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            int rowNum = 1;
            boolean firstRow = true;
            while ((line = reader.readLine()) != null) {
                if (firstRow) { firstRow = false; rowNum++; continue; } // 跳过标题行
                if (line.trim().isEmpty()) { rowNum++; continue; }

                // 处理BOM
                if (rowNum == 2 && line.charAt(0) == '﻿') {
                    line = line.substring(1);
                }

                String[] parts = line.split(",", 2);
                String userAccount = parts.length > 0 ? parts[0].trim() : "";
                if (userAccount.isEmpty()) { rowNum++; continue; }

                String userName = parts.length > 1 ? parts[1].trim() : userAccount;
                // 去掉可能存在的引号
                userName = userName.replaceAll("^\"|\"$", "");

                createUserIfNotExists(userAccount, userName, encryptPassword, result, rowNum);
                rowNum++;
            }
        }
    }

    /**
     * 创建用户（如果账号不存在）
     */
    private void createUserIfNotExists(String userAccount, String userName,
                                        String encryptPassword,
                                        BatchImportResult result, int rowNum) {
        try {
            long count = userService.count(
                    QueryWrapper.create().eq("user_account", userAccount));
            if (count > 0) {
                result.setSkipCount(result.getSkipCount() + 1);
                return;
            }
            User user = new User();
            user.setUserAccount(userAccount);
            user.setUserPassword(encryptPassword);
            user.setUserName(userName);
            user.setUserRole(UserRoleEnum.USER.getValue());
            if (userService.save(user)) {
                result.setSuccessCount(result.getSuccessCount() + 1);
            } else {
                result.getErrors().add("第" + rowNum + "行: " + userAccount + " 插入失败");
            }
        } catch (Exception e) {
            result.getErrors().add("第" + rowNum + "行处理异常: " + e.getMessage());
        }
    }

    /**
     * 获取单元格字符串值
     */
    private String getCellStringValue(Cell cell) {
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> {
                // 避免科学计数法，工号通常是整数
                double val = cell.getNumericCellValue();
                if (val == Math.floor(val) && !Double.isInfinite(val)) {
                    yield String.valueOf((long) val);
                }
                yield String.valueOf(val);
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> {
                try {
                    yield cell.getStringCellValue();
                } catch (Exception e) {
                    yield String.valueOf(cell.getNumericCellValue());
                }
            }
            default -> "";
        };
    }

    /**
     * 根据 id 获取用户（仅管理员）
     */
    @GetMapping("/get")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<User> getUserById(long id) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
        User user = userService.getById(id);
        ThrowUtils.throwIf(user == null, ErrorCode.NOT_FOUND_ERROR);
        return ResultUtils.success(user);
    }

    /**
     * 根据 id 获取包装类
     */
    @GetMapping("/get/vo")
    public BaseResponse<UserVO> getUserVOById(long id) {
        BaseResponse<User> response = getUserById(id);
        User user = response.getData();
        return ResultUtils.success(userService.getUserVO(user));
    }

    /**
     * 删除用户
     */
    @PostMapping("/delete")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> deleteUser(@RequestBody DeleteRequest deleteRequest) {
        if (deleteRequest == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        boolean b = userService.removeById(deleteRequest.getId());
        return ResultUtils.success(b);
    }

    /**
     * 更新用户
     */
    @PostMapping("/update")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> updateUser(@RequestBody UserUpdateRequest userUpdateRequest) {
        if (userUpdateRequest == null || userUpdateRequest.getId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User user = new User();
        BeanUtil.copyProperties(userUpdateRequest, user);
        boolean result = userService.updateById(user);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    /**
     * 分页获取用户封装列表（仅管理员）
     *
     * @param userQueryRequest 查询请求参数
     */
    /**
     * 获取可参赛用户列表（不含管理员），供提交比赛记录时选择团队成员
     */
    @GetMapping("/list/members")
    @Operation(summary = "获取参赛成员列表")
    public BaseResponse<List<UserVO>> listMembers() {
        List<User> users = userService.list(
                QueryWrapper.create()
                        .eq("user_role", UserRoleEnum.USER.getValue())
                        .orderBy("id", true));
        return ResultUtils.success(userService.getUserVOList(users));
    }

    @PostMapping("/list/page/vo")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<UserVO>> listUserVOByPage(@RequestBody UserQueryRequest userQueryRequest) {
        ThrowUtils.throwIf(userQueryRequest == null, ErrorCode.PARAMS_ERROR);
        long pageNum = userQueryRequest.getPageNum();
        long pageSize = userQueryRequest.getPageSize();
        Page<User> userPage = userService.page(Page.of(pageNum, pageSize),
                userService.getQueryWrapper(userQueryRequest));
        // 数据脱敏
        Page<UserVO> userVOPage = new Page<>(pageNum, pageSize, userPage.getTotalRow());
        List<UserVO> userVOList = userService.getUserVOList(userPage.getRecords());
        userVOPage.setRecords(userVOList);
        return ResultUtils.success(userVOPage);
    }

    // region 配额管理



    /**
     * 用户使用分析视图对象
     */
    @lombok.Data
    public static class UserAnalysisVO implements java.io.Serializable {
        private Long userId;
        private String userAccount;
        private String userName;
        private String userStatus;
        private String userRole;
        private Long tokenQuota;
        private Long usedTokens;
        private Long remainingQuota;
        private Long totalRequests;
        private Long successRequests;
        private Long totalTokens;
        private BigDecimal totalCost;
        private BigDecimal todayCost;
    }

    /**
     * 配额信息视图对象
     */
    @lombok.Data
    public static class QuotaVO implements java.io.Serializable {
        /**
         * Token配额（-1表示无限制）
         */
        private Long tokenQuota;
        
        /**
         * 已使用Token数
         */
        private Long usedTokens;
        
        /**
         * 剩余配额（-1表示无限制）
         */
        private Long remainingQuota;
    }

    /**
     * 配额更新请求
     */
    @lombok.Data
    public static class QuotaUpdateRequest implements java.io.Serializable {
        /**
         * 用户ID
         */
        private Long userId;
        
        /**
         * Token配额（-1表示无限制）
         */
        private Long tokenQuota;
    }
}
