package com.prismprd.web;

import com.prismprd.common.ApiResponse;
import com.prismprd.review.RubricService;
import com.prismprd.web.dto.RubricDetail;
import com.prismprd.web.dto.RubricSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import jakarta.validation.Valid;
import com.prismprd.web.dto.PublishRubricRequest;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class ReviewRubricController {

    private final RubricService rubricService;

    @GetMapping("/api/review-rubrics")
    public ApiResponse<List<RubricSummary>> list() {
        return ApiResponse.ok(rubricService.listRubrics());
    }

    @GetMapping("/api/review-rubrics/{rubricId}")
    public ApiResponse<RubricDetail> detail(@PathVariable UUID rubricId) {
        return ApiResponse.ok(rubricService.getRubric(rubricId));
    }

    @PostMapping("/api/review-rubrics")
    public ApiResponse<RubricDetail> create(@Valid @RequestBody PublishRubricRequest request) {
        return ApiResponse.ok(rubricService.publishNew(request));
    }
    @PostMapping("/api/review-rubrics/{rubricKey}/versions")
    public ApiResponse<RubricDetail> publishVersion(@PathVariable String rubricKey,@Valid @RequestBody PublishRubricRequest request) {
        return ApiResponse.ok(rubricService.publishVersion(rubricKey,request));
    }
    @GetMapping("/api/review-rubrics/{rubricId}/versions")
    public ApiResponse<List<RubricSummary>> versions(@PathVariable UUID rubricId) {
        return ApiResponse.ok(rubricService.versions(rubricId));
    }
}
