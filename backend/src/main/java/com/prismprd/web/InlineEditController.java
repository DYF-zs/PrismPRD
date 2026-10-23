package com.prismprd.web;

import com.prismprd.common.ApiResponse;
import com.prismprd.prd.inline.InlineEditResponse;
import com.prismprd.prd.inline.InlineEditService;
import com.prismprd.security.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;
import jakarta.validation.Valid;

@RestController
@RequiredArgsConstructor
public class InlineEditController {

    private final InlineEditService inlineEditService;
    private final CurrentUserService currentUserService;

    /** 编辑器 Inline AI：生成待确认 Patch，返回持久化 ID 与 TipTap 预览节点 */
    @PostMapping("/api/prds/{prdId}/inline-edit")
    public ApiResponse<InlineEditResponse> generate(@PathVariable UUID prdId,
                                                    @Valid @RequestBody InlineEditService.Request request) {
        return ApiResponse.ok(inlineEditService.generate(prdId, request, currentUserService.currentUserId()));
    }
}
