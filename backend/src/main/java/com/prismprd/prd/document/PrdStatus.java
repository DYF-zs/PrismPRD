package com.prismprd.prd.document;

public enum PrdStatus {

    DRAFT, REVIEWING, NEED_CHANGES, APPROVED, ARCHIVED;

    public static boolean isValid(String value) {
        if (value == null) {
            return false;
        }
        for (PrdStatus status : values()) {
            if (status.name().equals(value)) {
                return true;
            }
        }
        return false;
    }
}
