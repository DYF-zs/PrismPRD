package com.prismprd.web;

import com.prismprd.common.ApiResponse;
import com.prismprd.knowledge.*;
import com.prismprd.security.CurrentUserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class KnowledgeController {
    private final KnowledgeService service;
    private final CurrentUserService current;
    public record Create(@NotBlank @Size(max=255) String name, @Size(max=4000) String description) {}
    public record Binding(@NotNull Boolean enabled) {}
    public record Query(@NotBlank @Size(max=4000) String query) {}

    @GetMapping("/prds/{prd}/knowledge-bases")
    public ApiResponse<List<KnowledgeTypes.Base>> list(@PathVariable UUID prd) { return ApiResponse.ok(service.list(prd)); }
    @PostMapping("/prds/{prd}/knowledge-bases")
    public ApiResponse<KnowledgeTypes.Base> create(@PathVariable UUID prd,@Valid @RequestBody Create request) {
        return ApiResponse.ok(service.create(prd,request.name(),request.description()));
    }
    @PutMapping("/prds/{prd}/knowledge-bases/{base}")
    public ApiResponse<Void> bind(@PathVariable UUID prd,@PathVariable UUID base,@Valid @RequestBody Binding request) {
        service.bind(prd,base,request.enabled()); return ApiResponse.ok(null);
    }
    @GetMapping("/knowledge-bases/{base}/documents")
    public ApiResponse<List<KnowledgeTypes.Document>> documents(@PathVariable UUID base) { return ApiResponse.ok(service.documents(base)); }
    @PostMapping(value="/knowledge-bases/{base}/documents",consumes="multipart/form-data")
    public ApiResponse<Map<String,UUID>> upload(@PathVariable UUID base,@RequestParam("file") MultipartFile file) {
        return ApiResponse.ok(Map.of("id",service.upload(base,file)));
    }
    @PostMapping("/knowledge-documents/{id}/reindex")
    public ApiResponse<Void> retry(@PathVariable UUID id) { service.retry(id); return ApiResponse.ok(null); }
    @DeleteMapping("/knowledge-documents/{id}")
    public ApiResponse<Void> archive(@PathVariable UUID id) { service.archive(id); return ApiResponse.ok(null); }
    @GetMapping("/knowledge-documents/{id}/content")
    public ApiResponse<String> content(@PathVariable UUID id) { return ApiResponse.ok(service.content(id)); }
    @PostMapping("/prds/{prd}/knowledge-search")
    public ApiResponse<List<KnowledgeTypes.Hit>> search(@PathVariable UUID prd,@Valid @RequestBody Query query) {
        return ApiResponse.ok(service.search(prd,current.currentUserId(),query.query(),5));
    }
}
