package com.jgh.ghairouter.model.enums;

import cn.hutool.core.util.ObjUtil;
import lombok.Getter;

/**
 * 审核状态枚举（自动审核和管理员审核共用）
 */
@Getter
public enum ReviewStatusEnum {

    PENDING("待审核", "PENDING"),
    PASSED("审核通过", "PASSED"),
    FAILED("审核失败", "FAILED");

    private final String text;
    private final String value;

    ReviewStatusEnum(String text, String value) {
        this.text = text;
        this.value = value;
    }

    /**
     * 根据 value 获取枚举
     */
    public static ReviewStatusEnum getEnumByValue(String value) {
        if (ObjUtil.isEmpty(value)) {
            return null;
        }
        for (ReviewStatusEnum anEnum : ReviewStatusEnum.values()) {
            if (anEnum.value.equals(value)) {
                return anEnum;
            }
        }
        return null;
    }
}
