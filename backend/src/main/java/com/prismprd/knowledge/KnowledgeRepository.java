package com.prismprd.knowledge;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import lombok.RequiredArgsConstructor;
import java.util.*;
import static com.prismprd.knowledge.KnowledgeTypes.*;

@Repository
@RequiredArgsConstructor
public class KnowledgeRepository {
    private final JdbcTemplate jdbc;
    public JdbcTemplate jdbc() { return jdbc; }
    private static final String VISIBLE = """
        (kb.created_by=? OR EXISTS (SELECT 1 FROM prd_knowledge_binding b
        JOIN prd_document p ON p.id=b.prd_id WHERE b.knowledge_base_id=kb.id
        AND (p.owner_id=? OR EXISTS (SELECT 1 FROM prd_member m WHERE m.prd_id=p.id AND m.user_id=?))))
        """;

    public List<Base> bases(UUID user, UUID prd) {
        return jdbc.query("SELECT kb.id,kb.name,kb.description,kb.created_by=? AS owned,"
                + "EXISTS(SELECT 1 FROM prd_knowledge_binding b WHERE b.knowledge_base_id=kb.id AND b.prd_id=?) AS bound "
                + "FROM knowledge_base kb WHERE kb.enabled AND " + VISIBLE + " ORDER BY kb.created_at DESC LIMIT 100",
                (rs, n) -> new Base(rs.getObject("id", UUID.class), rs.getString("name"), rs.getString("description"),
                        rs.getBoolean("owned"), rs.getBoolean("bound")), user, prd, user, user, user);
    }

    public boolean visible(UUID base, UUID user) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM knowledge_base kb WHERE kb.id=? AND kb.enabled AND " + VISIBLE + ")",
                Boolean.class, base, user, user, user));
    }
    public boolean owned(UUID base, UUID user) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM knowledge_base WHERE id=? AND created_by=? AND enabled)", Boolean.class, base, user));
    }
    public UUID baseOf(UUID document) {
        return jdbc.query("SELECT knowledge_base_id FROM knowledge_document WHERE id=? AND processing_status<>'DELETED'",
                (rs,n) -> rs.getObject(1, UUID.class), document).stream().findFirst().orElse(null);
    }

    public List<Document> documents(UUID base) {
        return jdbc.query("""
            SELECT d.*, (SELECT count(*) FROM knowledge_chunk c WHERE c.knowledge_document_id=d.id) AS chunk_count
            FROM knowledge_document d WHERE d.knowledge_base_id=? AND d.processing_status<>'DELETED' ORDER BY d.created_at DESC LIMIT 200
            """, (rs, n) -> new Document(rs.getObject("id", UUID.class), rs.getObject("knowledge_base_id", UUID.class),
                rs.getString("title"), rs.getString("mime_type"), rs.getLong("file_size"), rs.getString("processing_status"),
                rs.getString("error_message"), rs.getInt("chunk_count"), rs.getString("embedding_model"),
                rs.getObject("created_at", java.time.OffsetDateTime.class)), base);
    }

    public List<Hit> search(UUID prd, double[] query, String model, int limit) {
        return jdbc.query("""
            SELECT d.id AS document_id, c.id, d.title, c.section_path, c.chunk_text,
              (SELECT sum(v.a*v.b) FROM unnest(c.embedding, CAST(? AS double precision[])) AS v(a,b)) AS score
            FROM knowledge_chunk c JOIN knowledge_document d ON d.id=c.knowledge_document_id
            JOIN knowledge_base kb ON kb.id=d.knowledge_base_id
            JOIN prd_knowledge_binding b ON b.knowledge_base_id=kb.id
            WHERE b.prd_id=? AND kb.enabled AND d.processing_status='READY'
              AND d.embedding_model=? AND d.embedding_dimensions=?
            ORDER BY score DESC, c.id LIMIT ?
            """, (rs, n) -> new Hit(rs.getObject("document_id", UUID.class), rs.getObject("id", UUID.class),
                    rs.getString("title"), rs.getString("section_path"), rs.getString("chunk_text"), rs.getDouble("score")),
                OllamaEmbeddingClient.literal(query), prd, model, query.length, limit);
    }

    public boolean hasBoundBases(UUID prd) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM prd_knowledge_binding WHERE prd_id=?)", Boolean.class, prd));
    }
}
