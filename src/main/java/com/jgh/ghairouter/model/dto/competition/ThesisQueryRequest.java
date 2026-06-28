package com.jgh.ghairouter.model.dto.competition;

import lombok.Data;

/**
 * 论文业绩记录 查询请求（v8）
 */
@Data
public class ThesisQueryRequest {

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

    /** 论文等级筛选 */
    private String thesisLevel;

    /** 论文名称（模糊搜索） */
    private String thesisName;

    /** 管理员审核状态筛选 */
    private String adminReviewStatus;
}
