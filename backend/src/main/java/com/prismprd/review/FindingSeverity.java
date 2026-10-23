package com.prismprd.review;

public enum FindingSeverity {

    CRITICAL, MAJOR, MINOR, SUGGESTION;

    public static FindingSeverity parse(String value) {
        return valueOf(value.trim().toUpperCase());
    }
}
