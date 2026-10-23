package com.prismprd.web;

import com.prismprd.common.ApiResponse;
import com.prismprd.prd.patch.PatchService;
import com.prismprd.security.CurrentUserService;
import com.prismprd.web.dto.PatchDetail;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class PatchController {

    private final PatchService patchService;
    private final CurrentUserService currentUserService;

    @PostMapping("/api/findings/{findingId}/fix")
    public ApiResponse<PatchDetail> createFix(@PathVariable UUID findingId) {
        return ApiResponse.ok(patchService.createFix(findingId, currentUserService.currentUserId()));
    }

    @PostMapping("/api/patches/{patchId}/apply")
    public ApiResponse<PatchDetail> apply(@PathVariable UUID patchId) {
        return ApiResponse.ok(patchService.applyPatch(patchId, currentUserService.currentUserId()));
    }

    @PostMapping("/api/patches/{patchId}/reject")
    public ApiResponse<PatchDetail> reject(@PathVariable UUID patchId) {
        return ApiResponse.ok(patchService.rejectPatch(patchId, currentUserService.currentUserId()));
    }

    @GetMapping("/api/prds/{prdId}/patches")
    public ApiResponse<List<PatchDetail>> listByPrd(@PathVariable UUID prdId) {
        return ApiResponse.ok(patchService.listByPrd(prdId, currentUserService.currentUserId()));
    }
}
