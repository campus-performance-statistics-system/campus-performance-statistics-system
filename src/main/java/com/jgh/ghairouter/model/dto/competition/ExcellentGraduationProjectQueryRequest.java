package com.jgh.ghairouter.model.dto.competition;

import com.jgh.ghairouter.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 优秀毕设查询请求（v16 新增）
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ExcellentGraduationProjectQueryRequest extends PageRequest {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long userId;
    private String major;
    private String studentName;
    private String advisorName;
    private String adminReviewStatus;
}
