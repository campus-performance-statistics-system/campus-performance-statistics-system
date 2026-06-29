package com.jgh.ghairouter.model.dto.competition;

import com.jgh.ghairouter.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 校企联合培养查询请求（v17 新增）
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SchoolEnterpriseTrainingQueryRequest extends PageRequest {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long userId;
    private String studentName;
    private String major;
    private String companyName;
    private String advisorName;
    private String adminReviewStatus;
}
