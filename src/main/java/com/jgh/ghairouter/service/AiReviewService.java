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
     * @param imageUrl        证明图片URL
     */
    void autoReview(Long recordId, String competitionName, String imageUrl);
}
