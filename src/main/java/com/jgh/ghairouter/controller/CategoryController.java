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
import com.jgh.ghairouter.model.dto.category.CategoryAddRequest;
import com.jgh.ghairouter.model.dto.category.CategoryUpdateRequest;
import com.jgh.ghairouter.model.entity.Category;
import com.jgh.ghairouter.service.CategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 比赛分类控制器
 */
@RestController
@RequestMapping("/category")
@Tag(name = "比赛分类管理")
public class CategoryController {

    @Resource
    private CategoryService categoryService;

    /**
     * 新增分类（管理员）
     */
    @PostMapping("/add")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @Operation(summary = "新增分类")
    public BaseResponse<Long> addCategory(@RequestBody CategoryAddRequest request) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        Category category = new Category();
        BeanUtil.copyProperties(request, category);
        boolean result = categoryService.save(category);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(category.getId());
    }

    /**
     * 删除分类（管理员）
     */
    @PostMapping("/delete")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @Operation(summary = "删除分类")
    public BaseResponse<Boolean> deleteCategory(@RequestBody DeleteRequest deleteRequest) {
        if (deleteRequest == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        boolean result = categoryService.removeById(deleteRequest.getId());
        return ResultUtils.success(result);
    }

    /**
     * 更新分类（管理员）
     */
    @PostMapping("/update")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @Operation(summary = "更新分类")
    public BaseResponse<Boolean> updateCategory(@RequestBody CategoryUpdateRequest request) {
        if (request == null || request.getId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Category category = new Category();
        BeanUtil.copyProperties(request, category);
        boolean result = categoryService.updateById(category);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    /**
     * 获取分类树（所有用户可访问，顶层分类含children嵌套）
     */
    @GetMapping("/list")
    @Operation(summary = "获取分类树")
    public BaseResponse<List<Category>> listCategories() {
        List<Category> tree = categoryService.listTree();
        return ResultUtils.success(tree);
    }

    /**
     * 获取所有子分类（供提交记录时选择）
     */
    @GetMapping("/children")
    @Operation(summary = "获取子分类列表")
    public BaseResponse<List<Category>> listChildren() {
        List<Category> children = categoryService.listChildren();
        return ResultUtils.success(children);
    }

    /**
     * 根据ID获取分类
     */
    @GetMapping("/get/{id}")
    @Operation(summary = "获取分类详情")
    public BaseResponse<Category> getCategoryById(@PathVariable Long id) {
        ThrowUtils.throwIf(id == null || id <= 0, ErrorCode.PARAMS_ERROR);
        Category category = categoryService.getById(id);
        ThrowUtils.throwIf(category == null, ErrorCode.NOT_FOUND_ERROR);
        return ResultUtils.success(category);
    }
}
