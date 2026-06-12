package com.jgh.ghairouter.service;

import org.springframework.web.multipart.MultipartFile;

/**
 * 文件服务
 */
public interface FileService {

    /**
     * 上传图片文件，返回可访问的URL
     */
    String uploadImage(MultipartFile file);
}
