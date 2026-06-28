package com.jgh.ghairouter.model.dto.competition;

import com.jgh.ghairouter.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 签订合作企业查询请求（v14 新增）
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class CooperativeEnterpriseQueryRequest extends PageRequest {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long userId;
    private String teacherName;
    private String college;
    private String enterpriseName;
    private String adminReviewStatus;
}
