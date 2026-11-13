package com.li.socialplatform.recommend;

import java.util.Locale;

/**
 * 首页信息流模式
 */
public enum FeedMode {
    RECOMMEND("recommend"),
    HOT("hot"),
    LATEST("latest");

    private final String code;

    FeedMode(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public static FeedMode from(String raw) {
        if (raw == null || raw.isBlank()) {
            return RECOMMEND;
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        for (FeedMode mode : values()) {
            if (mode.code.equals(normalized)) {
                return mode;
            }
        }
        return RECOMMEND;
    }
}
