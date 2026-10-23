package com.prismprd.security;

public enum PrdRole {

    OWNER, EDITOR, REVIEWER, VIEWER;

    public boolean canEdit() {
        return this == OWNER || this == EDITOR;
    }

    public boolean canReview() {
        return this == OWNER || this == EDITOR || this == REVIEWER;
    }

    public boolean canManage() {
        return this == OWNER;
    }
}
