package com.jgh.ghairouter.model.dto.competition;

import com.jgh.ghairouter.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 指导青年教师查询请求（v15 新增）
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class YoungTeacherGuidanceQueryRequest extends PageRequest {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long userId;
    private String college;
    private String mentorNames;
    private String youngTeacherName;
    private String adminReviewStatus;
}
