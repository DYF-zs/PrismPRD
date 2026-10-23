package com.prismprd.prd.version;

import com.prismprd.common.BizException;
import com.prismprd.common.NotFoundException;
import com.prismprd.security.CurrentUserService;
import com.prismprd.security.PermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class VersionExportService {
    private final PrdVersionMapper versions;
    private final CurrentUserService users;
    private final PermissionService permissions;
    private final VersionPdfRenderer pdf;

    public record ExportFile(byte[] content, String contentType, String filename) {}

    public ExportFile export(UUID id, String format) {
        if (!"markdown".equals(format) && !"pdf".equals(format)) throw new BizException("暂不支持的导出格式：" + format);
        var version = versions.selectById(id);
        if (version == null) throw new NotFoundException("PRD 版本不存在");
        permissions.requireView(version.getPrdId(), users.currentUserId());
        // No reads of current draft/title: both export formats use the immutable version.
        return "pdf".equals(format)
                ? new ExportFile(pdf.render(version), "application/pdf", "prd-v" + version.getVersionNo() + "-" + id + ".pdf")
                : new ExportFile((version.getMarkdownSnapshot() == null ? "" : version.getMarkdownSnapshot()).getBytes(StandardCharsets.UTF_8),
                        "text/markdown;charset=UTF-8", "prd-version-" + id + ".md");
    }
}
