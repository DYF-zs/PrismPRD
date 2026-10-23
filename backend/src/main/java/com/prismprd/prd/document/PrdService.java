package com.prismprd.prd.document;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.prismprd.common.BizException;
import com.prismprd.common.ConflictException;
import com.prismprd.common.NotFoundException;
import com.prismprd.common.PageResult;
import com.prismprd.prd.chat.ChatMessage;
import com.prismprd.prd.chat.ChatMessageMapper;
import com.prismprd.prd.chat.ChatSession;
import com.prismprd.prd.chat.ChatSessionMapper;
import com.prismprd.prd.document.editor.MarkdownTiptapConverter;
import com.prismprd.prd.document.editor.TiptapDocNormalizer;
import com.prismprd.prd.document.editor.TiptapMarkdownSerializer;
import com.prismprd.security.AppUser;
import com.prismprd.security.CurrentUserService;
import com.prismprd.security.PermissionService;
import com.prismprd.web.dto.CreatePrdRequest;
import com.prismprd.web.dto.CreatePrdResponse;
import com.prismprd.web.dto.ImportRequest;
import com.prismprd.web.dto.PrdDetailResponse;
import com.prismprd.web.dto.SaveDraftRequest;
import com.prismprd.web.dto.SaveDraftResponse;
import com.prismprd.web.dto.TemplateResponse;
import com.prismprd.web.dto.UpdatePrdRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PrdService {

    private final PrdDocumentMapper prdDocumentMapper;
    private final ChatSessionMapper chatSessionMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final CurrentUserService currentUserService;
    private final PermissionService permissionService;
    private final ObjectMapper objectMapper;

    @Transactional
    public CreatePrdResponse create(CreatePrdRequest request) {
        AppUser user = currentUserService.currentUser();
        CreationMode mode = parseMode(request.creationMode());
        String title = request.title().trim();

        String editorJson = switch (mode) {
            case BLANK -> MarkdownTiptapConverter.emptyDoc();
            case TEMPLATE -> {
                if (request.templateKey() == null || request.templateKey().isBlank()) {
                    throw new BizException("TEMPLATE 模式必须提供 templateKey");
                }
                yield MarkdownTiptapConverter.convert(PrdTemplates.render(request.templateKey(), title));
            }
            case AI_DRAFT -> {
                if (request.initialRequirement() == null || request.initialRequirement().isBlank()) {
                    throw new BizException("AI_DRAFT 模式必须提供 initialRequirement");
                }
                yield MarkdownTiptapConverter.emptyDoc();
            }
        };

        PrdDocument document = new PrdDocument();
        document.setId(UUID.randomUUID());
        document.setTitle(title);
        document.setStatus(PrdStatus.DRAFT.name());
        document.setOwnerId(user.getId());
        document.setDraftJson(editorJson);
        document.setDraftMarkdown(TiptapMarkdownSerializer.serialize(readTree(editorJson)));
        document.setRevision(0L);
        prdDocumentMapper.insert(document);

        ChatSession session = new ChatSession();
        session.setId(UUID.randomUUID());
        session.setPrdId(document.getId());
        session.setTitle("AI 助手");
        session.setCreatedBy(user.getId());
        chatSessionMapper.insert(session);

        if (mode == CreationMode.AI_DRAFT) {
            ChatMessage message = new ChatMessage();
            message.setId(UUID.randomUUID());
            message.setSessionId(session.getId());
            message.setRole("USER");
            message.setContent(request.initialRequirement());
            chatMessageMapper.insert(message);
        }

        return new CreatePrdResponse(document.getId(), session.getId(), 0L);
    }

    @Transactional
    public CreatePrdResponse createFromMarkdown(String filename, String markdown) {
        int dot = filename.lastIndexOf('.');
        String title = (dot > 0 ? filename.substring(0, dot) : filename).trim();
        if (title.isBlank()) title = "导入 PRD";
        CreatePrdResponse created = create(new CreatePrdRequest("BLANK", title, null, null));
        SaveDraftResponse imported = importMarkdown(created.prdId(), new ImportRequest(created.revision(), markdown));
        return new CreatePrdResponse(created.prdId(), created.defaultSessionId(), imported.revision());
    }

    public PageResult<PrdDocumentSummary> list(String query, String status, int page, int size) {
        UUID userId = currentUserService.currentUserId();
        if (status != null && !status.isBlank() && !PrdStatus.isValid(status)) {
            throw new BizException("非法状态：" + status);
        }
        int safePage = Math.max(1, page);
        int safeSize = Math.min(Math.max(1, size), 100);
        long offset = (long) (safePage - 1) * safeSize;
        String safeQuery = blankToNull(query);
        String safeStatus = blankToNull(status);
        List<PrdDocumentSummary> items = prdDocumentMapper.selectPage(
                userId, safeQuery, safeStatus, safeSize, offset);
        long total = prdDocumentMapper.countPage(userId, safeQuery, safeStatus);
        return new PageResult<>(items, total, safePage, safeSize);
    }

    public PrdDetailResponse getDetail(UUID prdId) {
        UUID userId = currentUserService.currentUserId();
        permissionService.requireView(prdId, userId);
        return toDetail(requireDocument(prdId));
    }

    @Transactional
    public PrdDetailResponse updateMeta(UUID prdId, UpdatePrdRequest request) {
        UUID userId = currentUserService.currentUserId();
        if (request.title() != null && request.title().isBlank()) {
            throw new BizException("标题不能为空");
        }
        if (request.status() != null && !PrdStatus.isValid(request.status())) {
            throw new BizException("非法状态：" + request.status());
        }
        if (PrdStatus.ARCHIVED.name().equals(request.status())) {
            permissionService.requireManage(prdId, userId);
        } else {
            permissionService.requireEdit(prdId, userId);
        }
        prdDocumentMapper.updateMeta(prdId, request.title() == null ? null : request.title().trim(), request.status());
        return getDetail(prdId);
    }

    @Transactional
    public SaveDraftResponse saveDraft(UUID prdId, SaveDraftRequest request) {
        UUID userId = currentUserService.currentUserId();
        permissionService.requireEdit(prdId, userId);
        ObjectNode normalized = TiptapDocNormalizer.normalize(request.editorJson());
        return doSaveDraft(prdId, request.revision(), normalized);
    }

    @Transactional
    public SaveDraftResponse importMarkdown(UUID prdId, ImportRequest request) {
        UUID userId = currentUserService.currentUserId();
        permissionService.requireEdit(prdId, userId);
        ObjectNode converted = TiptapDocNormalizer.normalize(readTree(MarkdownTiptapConverter.convert(request.markdown())));
        return doSaveDraft(prdId, request.revision(), converted);
    }

    public List<TemplateResponse> listTemplates() {
        return PrdTemplates.list().stream()
                .map(t -> new TemplateResponse(t.key(), t.name(), t.description()))
                .toList();
    }

    private SaveDraftResponse doSaveDraft(UUID prdId, long revision, ObjectNode editorJson) {
        String json = editorJson.toString();
        String markdown = TiptapMarkdownSerializer.serialize(editorJson);
        int rows = prdDocumentMapper.updateDraft(prdId, revision, json, markdown);
        if (rows == 0) {
            PrdDocument existing = prdDocumentMapper.selectById(prdId);
            if (existing == null) {
                throw new NotFoundException("PRD 不存在");
            }
            throw new ConflictException("草稿已被修改，当前 revision 为 " + existing.getRevision());
        }
        PrdDocument updated = prdDocumentMapper.selectById(prdId);
        return new SaveDraftResponse(updated.getRevision(), updated.getUpdatedAt());
    }

    private PrdDocument requireDocument(UUID prdId) {
        PrdDocument document = prdDocumentMapper.selectById(prdId);
        if (document == null) {
            throw new NotFoundException("PRD 不存在");
        }
        return document;
    }

    private PrdDetailResponse toDetail(PrdDocument document) {
        return new PrdDetailResponse(
                document.getId(),
                document.getTitle(),
                document.getStatus(),
                document.getOwnerId(),
                document.getOwnerName(),
                document.getDraftJson(),
                document.getDraftMarkdown(),
                document.getRevision(),
                document.getCurrentVersionId(),
                document.getCurrentVersionNo(),
                document.getCurrentScore(),
                document.getCreatedAt(),
                document.getUpdatedAt());
    }

    private CreationMode parseMode(String value) {
        try {
            return CreationMode.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw new BizException("非法 creationMode：" + value);
        }
    }

    private ObjectNode readTree(String json) {
        try {
            JsonNode node = objectMapper.readTree(json);
            if (!node.isObject()) {
                throw new BizException("文档内容格式错误");
            }
            return (ObjectNode) node;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException("文档内容解析失败");
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
