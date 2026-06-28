package com.jgh.ghairouter.model.dto.competition;

import com.jgh.ghairouter.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 监考次数统计查询请求（v11 新增）
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class InvigilationQueryRequest extends PageRequest {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long userId;
    private String teacherName;
    private String adminReviewStatus;
}
