package com.prismprd.web;

import com.prismprd.common.ApiResponse;
import com.prismprd.prd.testcase.TestCaseService;
import com.prismprd.security.CurrentUserService;
import com.prismprd.web.dto.GenerateTestCasesRequest;
import com.prismprd.web.dto.TestCaseDetail;
import com.prismprd.web.dto.TestCaseGenerationResponse;
import com.prismprd.web.dto.PatchDetail;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;
import jakarta.validation.Valid;

@RestController
@RequiredArgsConstructor
public class TestCaseController {

    private final TestCaseService testCaseService;
    private final CurrentUserService currentUserService;

    /** 基于固定 PRD Version 生成测试用例与可测试性分析（同步），生成结果替换该版本已有用例 */
    @PostMapping("/api/prd-versions/{versionId}/test-cases")
    public ApiResponse<TestCaseGenerationResponse> generate(@PathVariable UUID versionId,
                                                            @Valid @RequestBody(required = false) GenerateTestCasesRequest request) {
        return ApiResponse.ok(testCaseService.generate(versionId, request, currentUserService.currentUserId()));
    }

    @GetMapping("/api/prd-versions/{versionId}/test-cases")
    public ApiResponse<List<TestCaseDetail>> list(@PathVariable UUID versionId) {
        return ApiResponse.ok(testCaseService.list(versionId, currentUserService.currentUserId()));
    }

    @GetMapping("/api/prd-versions/{versionId}/test-report")
    public ApiResponse<TestCaseGenerationResponse> report(@PathVariable UUID versionId) {
        return ApiResponse.ok(testCaseService.report(versionId, currentUserService.currentUserId()));
    }

    @PostMapping("/api/prd-versions/{versionId}/acceptance-patches")
    public ApiResponse<PatchDetail> proposeAcceptance(@PathVariable UUID versionId,
            @Valid @RequestBody TestCaseService.AcceptanceRequest request) {
        return ApiResponse.ok(testCaseService.proposeAcceptance(versionId, request, currentUserService.currentUserId()));
    }
}
