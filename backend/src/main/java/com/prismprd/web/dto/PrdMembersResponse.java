package com.prismprd.web.dto;

import java.util.List;
import java.util.UUID;

public record PrdMembersResponse(MemberView owner, List<MemberView> members) {

    public record MemberView(UUID userId, String role, String username, String displayName, String email) {
    }
}
