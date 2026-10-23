package com.prismprd.web;

import com.prismprd.common.ApiResponse;
import com.prismprd.common.BizException;
import com.prismprd.review.FindingService;
import com.prismprd.review.FindingStatus;
import com.prismprd.security.CurrentUserService;
import com.prismprd.web.dto.FindingDetail;
import com.prismprd.web.dto.UpdateFindingRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class FindingController {

    private final FindingService findingService;
    private final CurrentUserService currentUserService;

    @PatchMapping("/api/findings/{findingId}")
    public ApiResponse<FindingDetail> updateStatus(@PathVariable UUID findingId,
                                                   @Valid @RequestBody UpdateFindingRequest request) {
        FindingStatus status;
        try {
            status = FindingStatus.parse(request.getStatus());
        } catch (Exception e) {
            throw new BizException("非法的 Finding 状态: " + request.getStatus());
        }
        return ApiResponse.ok(findingService.updateStatus(findingId, status, currentUserService.currentUserId()));
    }
}
