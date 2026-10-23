package com.prismprd.prd.document;

import lombok.Data;

import java.util.UUID;

@Data
public class PrdMemberView {

    private UUID userId;
    private String role;
    private String username;
    private String displayName;
    private String email;
}
