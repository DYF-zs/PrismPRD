package com.prismprd.knowledge;

import com.prismprd.common.*;
import com.prismprd.security.*;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import java.nio.file.*;
import java.util.*;
import static com.prismprd.knowledge.KnowledgeTypes.*;

/** Ports JChatMind's document upload -> sections -> embeddings -> retrieval pipeline
 * into PRD-scoped authorization and atomic indexing. Source attribution in THIRD_PARTY_NOTICES.md.
 */
@Service
@DependsOnDatabaseInitialization
public class KnowledgeService {
    private final KnowledgeRepository repo;
    private final CurrentUserService current;
    private final PermissionService permissions;
    private final DocumentFileParser parser;
    private final MarkdownSectionSplitter splitter;
    private final OllamaEmbeddingClient embeddings;
    private final TransactionTemplate tx;
    private final ThreadPoolTaskExecutor executor;
    private final Path root;

    public KnowledgeService(KnowledgeRepository repo, CurrentUserService current, PermissionService permissions,
            DocumentFileParser parser, MarkdownSectionSplitter splitter, OllamaEmbeddingClient embeddings,
            TransactionTemplate tx, @Qualifier("knowledgeExecutor") ThreadPoolTaskExecutor executor,
            @Value("${prism.knowledge.storage-root:./data/knowledge}") String root) {
        this.repo=repo; this.current=current; this.permissions=permissions; this.parser=parser;
        this.splitter=splitter; this.embeddings=embeddings; this.tx=tx; this.executor=executor;
        this.root=Path.of(root).toAbsolutePath().normalize();
    }

    @PostConstruct public void recover() {
        repo.jdbc().update("UPDATE knowledge_document SET processing_status='FAILED',error_message='本地服务中断，请重新索引',updated_at=now() WHERE processing_status='PROCESSING'");
    }

    public List<Base> list(UUID prd) {
        UUID user=current.currentUserId(); permissions.requireView(prd,user);
        return repo.bases(user, prd);
    }

    public Base create(UUID prd, String name, String description) {
        UUID user=current.currentUserId(); permissions.requireEdit(prd,user);
        UUID id=UUID.randomUUID();
        tx.executeWithoutResult(status -> {
            repo.jdbc().queryForObject("SELECT id FROM prd_document WHERE id=? FOR UPDATE",UUID.class,prd);
            if (repo.jdbc().queryForObject("SELECT count(*) FROM prd_knowledge_binding WHERE prd_id=?",Integer.class,prd)>=10)
                throw new BizException("每份 PRD 最多绑定 10 个知识库");
            if (repo.jdbc().queryForObject("SELECT count(*) FROM knowledge_base WHERE created_by=?",Integer.class,user)>=100)
                throw new BizException("每位用户最多创建 100 个知识库");
            repo.jdbc().update("INSERT INTO knowledge_base(id,name,description,created_by) VALUES(?,?,?,?)",id,name.strip(),description==null?"":description.strip(),user);
            repo.jdbc().update("INSERT INTO prd_knowledge_binding(id,prd_id,knowledge_base_id,created_by) VALUES(?,?,?,?)",UUID.randomUUID(),prd,id,user);
        });
        return new Base(id,name.strip(),description==null?"":description.strip(),true,true);
    }

    public void bind(UUID prd, UUID base, boolean enabled) {
        UUID user=current.currentUserId(); permissions.requireEdit(prd,user);
        // Only a KB owner may introduce a new sharing relationship; PRD editors can unbind.
        if (enabled && !repo.owned(base,user)) throw new NotFoundException("只能绑定自己管理的知识库");
        tx.executeWithoutResult(status -> {
            repo.jdbc().queryForObject("SELECT id FROM prd_document WHERE id=? FOR UPDATE",UUID.class,prd);
            if (enabled) {
                if (repo.jdbc().queryForObject("SELECT count(*) FROM prd_knowledge_binding WHERE prd_id=?",Integer.class,prd)>=10)
                    throw new BizException("每份 PRD 最多绑定 10 个知识库");
                repo.jdbc().update("INSERT INTO prd_knowledge_binding(id,prd_id,knowledge_base_id,created_by) VALUES(?,?,?,?) ON CONFLICT(prd_id,knowledge_base_id) DO NOTHING",UUID.randomUUID(),prd,base,user);
            } else repo.jdbc().update("DELETE FROM prd_knowledge_binding WHERE prd_id=? AND knowledge_base_id=?",prd,base);
        });
    }

    public List<Document> documents(UUID base) {
        if (!repo.visible(base,current.currentUserId())) throw new NotFoundException("知识库不存在或无权访问");
        return repo.documents(base);
    }

    public UUID upload(UUID base, MultipartFile file) {
        UUID user=current.currentUserId(); requireOwner(base,user);
        String name=parser.filename(file.getOriginalFilename());
        String ext=parser.extension(name);
        if (!Set.of("md","markdown","txt","docx","pdf").contains(ext)) throw new BizException("不支持的文件类型");
        byte[] bytes=parser.read(file);
        UUID id=UUID.randomUUID();
        String key=base+"/"+id+"."+ext;
        Path path=safePath(key);
        try { Files.createDirectories(path.getParent()); Files.write(path,bytes,StandardOpenOption.CREATE_NEW); }
        catch (java.io.IOException e) { throw new BizException(503,"无法保存文件，请检查磁盘空间和目录权限"); }
        try {
            tx.executeWithoutResult(status -> {
                repo.jdbc().queryForObject("SELECT id FROM knowledge_base WHERE id=? FOR UPDATE",UUID.class,base);
                if (repo.jdbc().queryForObject("SELECT count(*) FROM knowledge_document WHERE knowledge_base_id=? AND processing_status<>'DELETED'",Integer.class,base)>=200)
                    throw new BizException("每个知识库最多 200 个文档");
                repo.jdbc().update("INSERT INTO knowledge_document(id,knowledge_base_id,title,storage_key,file_size,created_by) VALUES(?,?,?,?,?,?)",id,base,name,key,bytes.length,user);
            });
        } catch (RuntimeException e) {
            try { Files.deleteIfExists(path); } catch (java.io.IOException ignored) {}
            throw e;
        }
        submit(id);
        return id;
    }

    public void retry(UUID id) {
        UUID base=repo.baseOf(id); requireOwner(base,current.currentUserId());
        if (repo.jdbc().update("UPDATE knowledge_document SET processing_status='PROCESSING',error_message=NULL,updated_at=now() WHERE id=? AND processing_status IN ('FAILED','READY')",id)==0)
            throw new ConflictException("文档正在处理，请勿重复提交");
        submit(id);
    }

    public void archive(UUID id) {
        UUID base=repo.baseOf(id); requireOwner(base,current.currentUserId());
        tx.executeWithoutResult(status -> {
            repo.jdbc().update("UPDATE knowledge_document SET processing_status='DELETED',updated_at=now() WHERE id=?",id);
            repo.jdbc().update("DELETE FROM knowledge_chunk WHERE knowledge_document_id=?",id);
        });
    }

    private void submit(UUID id) {
        try { executor.execute(() -> index(id)); }
        catch (org.springframework.core.task.TaskRejectedException e) { failed(id,"索引任务队列已满，请稍后重新索引"); }
    }

    private void index(UUID id) {
        try {
            var records=repo.jdbc().queryForList("SELECT title,storage_key FROM knowledge_document WHERE id=? AND processing_status='PROCESSING'",id);
            if (records.isEmpty()) return;
            var row=records.get(0);
            Path path=safePath((String)row.get("storage_key"));
            if (Files.size(path)>DocumentFileParser.MAX_FILE_BYTES) throw new BizException("文件大小不合法");
            ParsedFile parsed=parser.parse((String)row.get("title"),Files.readAllBytes(path),true);
            List<Chunk> chunks=splitter.split(parsed.markdown());
            List<double[]> vectors=new ArrayList<>();
            for (int i=0;i<chunks.size();i+=8) {
                if (Thread.currentThread().isInterrupted()) throw new BizException("索引任务已中断");
                vectors.addAll(embeddings.embed(chunks.subList(i,Math.min(chunks.size(),i+8)).stream()
                        .map(c -> c.sectionPath()+"\n"+c.content()).toList()));
            }
            tx.executeWithoutResult(status -> {
                String state=repo.jdbc().queryForObject("SELECT processing_status FROM knowledge_document WHERE id=? FOR UPDATE",String.class,id);
                if (!"PROCESSING".equals(state)) return;
                repo.jdbc().update("DELETE FROM knowledge_chunk WHERE knowledge_document_id=?",id);
                for (int i=0;i<chunks.size();i++) {
                    Chunk chunk=chunks.get(i);
                    repo.jdbc().update("INSERT INTO knowledge_chunk(id,knowledge_document_id,chunk_text,section_path,chunk_index,embedding) VALUES(?,?,?,?,?,CAST(? AS double precision[]))",
                            UUID.randomUUID(),id,chunk.content(),chunk.sectionPath(),i,OllamaEmbeddingClient.literal(vectors.get(i)));
                }
                repo.jdbc().update("UPDATE knowledge_document SET parsed_text=?,mime_type=?,processing_status='READY',error_message=NULL,embedding_model=?,embedding_dimensions=?,updated_at=now() WHERE id=?",
                        parsed.markdown(),parsed.mimeType(),embeddings.model(),embeddings.dimensions(),id);
            });
        } catch (Exception e) { failed(id,e instanceof BizException?e.getMessage():"文件处理失败，请检查格式、存储和向量服务后重试"); }
    }

    private void failed(UUID id,String message) {
        repo.jdbc().update("UPDATE knowledge_document SET processing_status='FAILED',error_message=?,updated_at=now() WHERE id=? AND processing_status='PROCESSING'",message,id);
    }

    public List<Hit> search(UUID prd, UUID user, String query, int limit) {
        permissions.requireView(prd,user);
        if (query==null || query.isBlank() || query.length()>4000) throw new BizException("检索问题需为 1–4000 字符");
        if (!repo.hasBoundBases(prd)) return List.of();
        Integer pending=repo.jdbc().queryForObject("SELECT count(*) FROM knowledge_document d JOIN prd_knowledge_binding b ON b.knowledge_base_id=d.knowledge_base_id WHERE b.prd_id=? AND d.processing_status IN ('PROCESSING','FAILED')",Integer.class,prd);
        if (pending!=null && pending>0) throw new ConflictException("绑定知识库有未完成或失败的文档，请在知识面板处理后重试");
        Integer incompatible=repo.jdbc().queryForObject("SELECT count(*) FROM knowledge_document d JOIN prd_knowledge_binding b ON b.knowledge_base_id=d.knowledge_base_id WHERE b.prd_id=? AND d.processing_status='READY' AND (d.embedding_model IS DISTINCT FROM ? OR d.embedding_dimensions IS DISTINCT FROM ?)",Integer.class,prd,embeddings.model(),embeddings.dimensions());
        if (incompatible!=null && incompatible>0) throw new ConflictException("向量模型配置已变化，请重新索引绑定文档");
        Integer count=repo.jdbc().queryForObject("SELECT count(*) FROM knowledge_chunk c JOIN knowledge_document d ON d.id=c.knowledge_document_id JOIN prd_knowledge_binding b ON b.knowledge_base_id=d.knowledge_base_id WHERE b.prd_id=? AND d.processing_status='READY'",Integer.class,prd);
        if (count==null || count==0) return List.of();
        if (count>5000) throw new BizException("本地精确检索上限为 5000 个分块，请减少绑定知识库");
        double[] vector=embeddings.embed(List.of(query)).get(0);
        // Recheck authorization after the external embedding call.
        permissions.requireView(prd,user);
        List<Hit> hits=repo.search(prd,vector,embeddings.model(),Math.min(Math.max(limit,1),8));
        double cutoff=hits.isEmpty()?1:Math.max(0.45,hits.get(0).score()-0.18);
        return hits.stream().filter(h -> h.score()>=cutoff).toList();
    }

    public String content(UUID id) {
        UUID base=repo.baseOf(id);
        if (base==null || !repo.visible(base,current.currentUserId())) throw new NotFoundException("文档不存在或无权访问");
        return repo.jdbc().queryForObject("SELECT parsed_text FROM knowledge_document WHERE id=?",String.class,id);
    }

    private void requireOwner(UUID base,UUID user) {
        if (base==null || !repo.owned(base,user)) throw new NotFoundException("知识库不存在或需要管理权限");
    }

    private Path safePath(String key) {
        Path path=root.resolve(key).normalize();
        if (!path.startsWith(root) || path.equals(root)) throw new BizException("存储路径不合法");
        return path;
    }
}
