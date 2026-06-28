package com.jgh.ghairouter.model.dto.competition;

import lombok.Data;

/**
 * 兼职班主任业绩记录 查询请求（v10）
 */
@Data
public class PartTimeClassAdvisorQueryRequest {

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

    /** 教师姓名（模糊搜索） */
    private String teacherName;

    /** 班级编号（模糊搜索） */
    private String classId;

    /** 管理员审核状态筛选 */
    private String adminReviewStatus;
}
