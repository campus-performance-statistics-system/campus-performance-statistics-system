package com.jgh.ghairouter.controller;

import com.jgh.ghairouter.common.BaseResponse;
import com.jgh.ghairouter.common.ResultUtils;
import com.jgh.ghairouter.service.FileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文件上传控制器
 */
@RestController
@RequestMapping("/file")
@Tag(name = "文件上传")
public class FileController {

    @Resource
    private FileService fileService;

    /**
     * 上传图片
     */
    @PostMapping("/upload")
    @Operation(summary = "上传图片")
    public BaseResponse<String> uploadImage(@RequestParam("file") MultipartFile file) {
        String url = fileService.uploadImage(file);
        return ResultUtils.success(url);
    }
}
