package com.jgh.ghairouter.model.dto.user;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 批量导入用户结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchImportResult implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 成功导入数量 */
    private int successCount;

    /** 跳过数量（账号已存在） */
    private int skipCount;

    /** 错误信息列表 */
    private List<String> errors;

    public static BatchImportResult empty() {
        return BatchImportResult.builder()
                .successCount(0)
                .skipCount(0)
                .errors(new ArrayList<>())
                .build();
    }
}
