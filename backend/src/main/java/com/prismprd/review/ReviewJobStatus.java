package com.prismprd.review;

public enum ReviewJobStatus {

    PENDING, RUNNING, SUCCESS, FAILED;

    public boolean isTerminal() {
        return this == SUCCESS || this == FAILED;
    }
}
