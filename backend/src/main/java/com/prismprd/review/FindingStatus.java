package com.prismprd.review;

public enum FindingStatus {

    OPEN, RESOLVED, IGNORED, WONT_FIX;

    public static FindingStatus parse(String value) {
        return valueOf(value.trim().toUpperCase());
    }
}
