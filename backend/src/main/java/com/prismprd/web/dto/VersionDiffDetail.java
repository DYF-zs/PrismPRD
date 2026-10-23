package com.prismprd.web.dto;

import com.prismprd.prd.version.VersionTextDiff;
import java.util.List;
import java.util.UUID;

public record VersionDiffDetail(UUID leftId, UUID rightId, int leftVersionNo, int rightVersionNo,
        long addedLines, long deletedLines, List<VersionTextDiff.Line> lines,
        List<PatchDetail> acceptedPatches) {}
