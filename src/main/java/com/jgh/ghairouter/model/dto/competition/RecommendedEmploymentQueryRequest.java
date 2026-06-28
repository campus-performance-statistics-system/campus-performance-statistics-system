package com.jgh.ghairouter.model.dto.competition;

import com.jgh.ghairouter.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 推荐学院学生签约就业查询请求（v13 新增）
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class RecommendedEmploymentQueryRequest extends PageRequest {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long userId;
    private String teacherName;
    private String companyName;
    private String adminReviewStatus;
}
