package com.prismprd.web;

import com.prismprd.common.ApiResponse;
import com.prismprd.prd.version.VersionService;
import com.prismprd.prd.version.VersionExportService;
import com.prismprd.prd.version.VersionLifecycleService;
import com.prismprd.security.CurrentUserService;
import com.prismprd.web.dto.UseVersionRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import com.prismprd.web.dto.CreateVersionRequest;
import com.prismprd.web.dto.VersionDetail;
import com.prismprd.web.dto.VersionSummary;
import com.prismprd.web.dto.VersionDiffDetail;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class PrdVersionController {

    private final VersionService versionService;
    private final VersionExportService exportService;
    private final VersionLifecycleService lifecycleService;
    private final CurrentUserService currentUserService;

    @PostMapping("/api/prd-versions/{versionId}/use")
    public ApiResponse<Void> use(@PathVariable UUID versionId, @Valid @RequestBody UseVersionRequest request) {
        lifecycleService.useVersion(versionId, request.revision(), currentUserService.currentUserId());
        return ApiResponse.ok();
    }

    @DeleteMapping("/api/prd-versions/{versionId}")
    public ApiResponse<Void> delete(@PathVariable UUID versionId) {
        lifecycleService.deleteVersion(versionId, currentUserService.currentUserId());
        return ApiResponse.ok();
    }

    @PostMapping("/api/prds/{prdId}/versions")
    public ApiResponse<VersionDetail> create(@PathVariable UUID prdId,
                                             @Valid @RequestBody CreateVersionRequest request) {
        return ApiResponse.ok(versionService.createVersion(prdId, request.changeSummary()));
    }

    @GetMapping("/api/prds/{prdId}/versions")
    public ApiResponse<List<VersionSummary>> list(@PathVariable UUID prdId) {
        return ApiResponse.ok(versionService.listVersions(prdId));
    }

    @GetMapping("/api/prd-versions/{versionId}")
    public ApiResponse<VersionDetail> detail(@PathVariable UUID versionId) {
        return ApiResponse.ok(versionService.getVersion(versionId));
    }

    @GetMapping("/api/prd-versions/{versionId}/export")
    public ResponseEntity<byte[]> export(@PathVariable UUID versionId,
                                         @RequestParam(defaultValue = "markdown") String format) {
        var file = exportService.export(versionId, format);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .header("Content-Disposition", "attachment; filename=\"" + file.filename() + "\"")
                .header("Cache-Control", "no-store")
                .header("X-Content-Type-Options", "nosniff")
                .body(file.content());
    }

    @GetMapping("/api/prd-versions/{leftId}/diff/{rightId}")
    public ApiResponse<VersionDiffDetail> compare(@PathVariable UUID leftId, @PathVariable UUID rightId) {
        return ApiResponse.ok(versionService.compareVersions(leftId, rightId));
    }
}
