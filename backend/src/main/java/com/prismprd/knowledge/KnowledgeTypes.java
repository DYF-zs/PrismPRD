package com.prismprd.knowledge;

import java.util.UUID;
import java.time.OffsetDateTime;

public final class KnowledgeTypes {
    private KnowledgeTypes() {}
    public record Base(UUID id, String name, String description, boolean owned, boolean bound) {}
    public record Document(UUID id, UUID knowledgeBaseId, String title, String mimeType, long fileSize,
                           String processingStatus, String errorMessage, int chunkCount,
                           String embeddingModel, OffsetDateTime createdAt) {}
    public record Hit(UUID knowledgeDocumentId, UUID chunkId, String documentTitle,
                      String sectionPath, String content, double score) {}
    public record Chunk(String sectionPath, String content) {}
    public record ParsedFile(String filename, String mimeType, String markdown) {}
}
