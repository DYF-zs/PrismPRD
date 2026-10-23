package com.prismprd.web;

import com.prismprd.common.ApiResponse;
import com.prismprd.common.BizException;
import com.prismprd.knowledge.DocumentFileParser;
import com.prismprd.prd.document.PrdService;
import com.prismprd.security.CurrentUserService;
import com.prismprd.security.PermissionService;
import com.prismprd.web.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class PrdFileController {
    private final DocumentFileParser parser;
    private final PrdService prds;
    private final CurrentUserService current;
    private final PermissionService permissions;

    @PostMapping(value="/api/prds/import-file",consumes="multipart/form-data")
    public ApiResponse<CreatePrdResponse> create(@RequestParam("file") MultipartFile file) {
        var parsed = parser.parse(file.getOriginalFilename(), parser.read(file), false);
        return ApiResponse.ok(prds.createFromMarkdown(parsed.filename(), parsed.markdown()));
    }

    @PostMapping(value="/api/prds/{prd}/import-file",consumes="multipart/form-data")
    public ApiResponse<SaveDraftResponse> upload(@PathVariable UUID prd,@RequestParam long revision,@RequestParam("file") MultipartFile file) {
        permissions.requireEdit(prd,current.currentUserId());
        if (revision<0) throw new BizException("revision 不能为负数");
        var parsed=parser.parse(file.getOriginalFilename(),parser.read(file),false);
        return ApiResponse.ok(prds.importMarkdown(prd,new ImportRequest(revision,parsed.markdown())));
    }
}
