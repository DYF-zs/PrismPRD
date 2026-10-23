package com.prismprd.web;

import com.prismprd.common.ApiResponse;
import com.prismprd.security.AppUser;
import com.prismprd.security.CurrentUserService;
import com.prismprd.web.dto.MeResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class MeController {

    private final CurrentUserService currentUserService;

    @GetMapping("/api/me")
    public ApiResponse<MeResponse> me() {
        AppUser user = currentUserService.currentUser();
        return ApiResponse.ok(new MeResponse(user.getId(), user.getUsername(),
                user.getDisplayName(), user.getEmail(), user.getAuthSource()));
    }
}
