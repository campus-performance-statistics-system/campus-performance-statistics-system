package com.jgh.ghairouter.model.enums;

import cn.hutool.core.util.ObjUtil;
import lombok.Getter;

/**
 * 比赛分类类型枚举
 */
@Getter
public enum CategoryTypeEnum {

    SERVICE("服务类", "SERVICE"),
    PERSONAL_BUSINESS("个人业务类", "PERSONAL_BUSINESS");

    private final String text;
    private final String value;

    CategoryTypeEnum(String text, String value) {
        this.text = text;
        this.value = value;
    }

    /**
     * 根据 value 获取枚举
     */
    public static CategoryTypeEnum getEnumByValue(String value) {
        if (ObjUtil.isEmpty(value)) {
            return null;
        }
        for (CategoryTypeEnum anEnum : CategoryTypeEnum.values()) {
            if (anEnum.value.equals(value)) {
                return anEnum;
            }
        }
        return null;
    }
}
