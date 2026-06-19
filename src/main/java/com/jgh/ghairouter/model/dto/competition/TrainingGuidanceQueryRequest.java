package com.jgh.ghairouter.model.dto.competition;

import lombok.Data;

/**
 * 指导实训记录 查询请求（v4）
 */
@Data
public class TrainingGuidanceQueryRequest {

    /** 页码 */
    private long pageNum = 1;

    /** 每页大小 */
    private long pageSize = 10;

    /** 排序字段 */
    private String sortField;

    /** 排序方向：ascend / descend */
    private String sortOrder;

    /** 记录ID */
    private Long id;

    /** 用户ID */
    private Long userId;

    /** 实训名称（模糊搜索） */
    private String trainingName;

    /** 管理员审核状态筛选 */
    private String adminReviewStatus;
}
