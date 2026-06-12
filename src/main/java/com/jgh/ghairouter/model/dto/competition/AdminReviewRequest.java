package com.jgh.ghairouter.model.dto.competition;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 管理员审核请求
 */
@Data
public class AdminReviewRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 记录ID
     */
    private Long id;

    /**
     * 审核结果：PASSED/FAILED
     */
    private String reviewStatus;

    /**
     * 审核意见
     */
    private String reviewComment;
}
