package com.prismprd.web.dto;

import java.util.List;
import java.util.UUID;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SetMembersRequest(@NotNull @Size(max = 100) List<@NotNull @Valid MemberInput> members) {

    public record MemberInput(@NotNull UUID userId, @NotBlank String role) {
    }
}
