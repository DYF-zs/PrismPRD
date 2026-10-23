package com.prismprd.web;

import com.prismprd.common.ApiResponse;
import com.prismprd.common.PageResult;
import com.prismprd.prd.document.PrdDocumentSummary;
import com.prismprd.prd.document.PrdService;
import com.prismprd.prd.document.PrdDeletionService;
import com.prismprd.security.CurrentUserService;
import org.springframework.web.bind.annotation.DeleteMapping;
import com.prismprd.web.dto.CreatePrdRequest;
import com.prismprd.web.dto.CreatePrdResponse;
import com.prismprd.web.dto.ImportRequest;
import com.prismprd.web.dto.PrdDetailResponse;
import com.prismprd.web.dto.SaveDraftRequest;
import com.prismprd.web.dto.SaveDraftResponse;
import com.prismprd.web.dto.TemplateResponse;
import com.prismprd.web.dto.UpdatePrdRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class PrdController {

    private final PrdService prdService;
    private final PrdDeletionService deletionService;
    private final CurrentUserService currentUserService;

    @DeleteMapping("/prds/{prdId}")
    public ApiResponse<Void> delete(@PathVariable UUID prdId) {
        deletionService.deleteDocument(prdId, currentUserService.currentUserId());
        return ApiResponse.ok();
    }

    @PostMapping("/prds")
    public ApiResponse<CreatePrdResponse> create(@Valid @RequestBody CreatePrdRequest request) {
        return ApiResponse.ok(prdService.create(request));
    }

    @GetMapping("/prds")
    public ApiResponse<PageResult<PrdDocumentSummary>> list(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(prdService.list(query, status, page, size));
    }

    @GetMapping("/prd-templates")
    public ApiResponse<List<TemplateResponse>> templates() {
        return ApiResponse.ok(prdService.listTemplates());
    }

    @GetMapping("/prds/{prdId}")
    public ApiResponse<PrdDetailResponse> detail(@PathVariable UUID prdId) {
        return ApiResponse.ok(prdService.getDetail(prdId));
    }

    @PatchMapping("/prds/{prdId}")
    public ApiResponse<PrdDetailResponse> update(@PathVariable UUID prdId,
                                                 @Valid @RequestBody UpdatePrdRequest request) {
        return ApiResponse.ok(prdService.updateMeta(prdId, request));
    }

    @PutMapping("/prds/{prdId}/draft")
    public ApiResponse<SaveDraftResponse> saveDraft(@PathVariable UUID prdId,
                                                    @Valid @RequestBody SaveDraftRequest request) {
        return ApiResponse.ok(prdService.saveDraft(prdId, request));
    }

    @PostMapping("/prds/{prdId}/import")
    public ApiResponse<SaveDraftResponse> importMarkdown(@PathVariable UUID prdId,
                                                         @Valid @RequestBody ImportRequest request) {
        return ApiResponse.ok(prdService.importMarkdown(prdId, request));
    }
}
