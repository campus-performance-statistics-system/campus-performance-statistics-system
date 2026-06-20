package com.jgh.ghairouter.model.dto.competition;

import lombok.Data;

/**
 * 大创业绩记录 查询请求（v6）
 */
@Data
public class InnovationEntrepreneurshipQueryRequest {

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

    /** 项目级别筛选 */
    private String projectLevel;

    /** 项目名称（模糊搜索） */
    private String projectName;

    /** 管理员审核状态筛选 */
    private String adminReviewStatus;
}
