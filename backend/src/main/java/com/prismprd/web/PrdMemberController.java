package com.prismprd.web;

import com.prismprd.common.ApiResponse;
import com.prismprd.prd.document.PrdMemberService;
import com.prismprd.web.dto.PrdMembersResponse;
import com.prismprd.web.dto.SetMembersRequest;
import com.prismprd.web.dto.TransferOwnerRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/prds/{prdId}")
@RequiredArgsConstructor
public class PrdMemberController {

    private final PrdMemberService prdMemberService;

    @GetMapping("/members")
    public ApiResponse<PrdMembersResponse> listMembers(@PathVariable UUID prdId) {
        return ApiResponse.ok(prdMemberService.listMembers(prdId));
    }

    @PutMapping("/members")
    public ApiResponse<PrdMembersResponse> setMembers(@PathVariable UUID prdId,
                                                      @Valid @RequestBody SetMembersRequest request) {
        return ApiResponse.ok(prdMemberService.setMembers(prdId, request));
    }

    @PutMapping("/owner")
    public ApiResponse<PrdMembersResponse> transferOwner(@PathVariable UUID prdId,
                                                         @Valid @RequestBody TransferOwnerRequest request) {
        return ApiResponse.ok(prdMemberService.transferOwner(prdId, request));
    }
}
