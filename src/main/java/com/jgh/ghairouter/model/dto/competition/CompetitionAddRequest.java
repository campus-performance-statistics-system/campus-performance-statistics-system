package com.jgh.ghairouter.model.dto.competition;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 提交比赛记录请求
 */
@Data
public class CompetitionAddRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 分类ID
     */
    private Long categoryId;

    /**
     * 比赛名称
     */
    private String competitionName;

    /**
     * 参赛/获奖证明图片URL（先上传图片获取URL后再提交）
     */
    private String proofImageUrl;
}
