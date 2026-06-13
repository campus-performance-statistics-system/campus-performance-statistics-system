package com.jgh.ghairouter.service;

/**
 * AI自动审核服务（多模态）
 */
public interface AiReviewService {

    /**
     * 自动审核比赛记录
     * 使用多模态模型比对比赛名称和证明图片
     *
     * @param recordId        比赛记录ID
     * @param competitionName 比赛名称
     * @param imageBase64     证明图片的 base64 数据
     * @param mimeType        图片 MIME 类型（如 image/png、image/jpeg）
     */
    void autoReview(Long recordId, String competitionName, String imageBase64, String mimeType);
}
