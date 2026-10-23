package com.prismprd.model;

import com.prismprd.common.BizException;
import com.prismprd.common.ConflictException;
import com.prismprd.common.NotFoundException;
import jakarta.validation.constraints.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class ModelSettingsService {
    public record Input(@NotBlank @Size(max=80) String name,
                        @NotBlank @Pattern(regexp="deepseek|zhipuai|openai") String provider,
                        @NotBlank @Size(max=160) String model,
                        @NotBlank @Size(max=1024) String baseUrl,
                        @Size(max=4096) String apiKey,
                        @PositiveOrZero Long revision) {
        @Override public String toString() { return "ModelInput[provider=" + provider + ", model=" + model + "]"; }
    }
    public record Summary(UUID id, String name, String provider, String model, String baseUrl,
                          boolean hasApiKey, long revision, boolean active) {}
    public record Settings(List<Summary> configurations, UUID activeId) {}
    static final class Credential {
        final Summary summary;
        final String encryptedKey;
        Credential(Summary summary, String encryptedKey) {
            this.summary = summary;
            this.encryptedKey = encryptedKey;
        }
    }
    private final JdbcTemplate jdbc;
    private final ModelKeyCipher cipher;
    private final String bootstrapKey;
    private final String bootstrapModel;
    private final String bootstrapUrl;

    public ModelSettingsService(JdbcTemplate jdbc, ModelKeyCipher cipher,
            @Value("${spring.ai.deepseek.api-key:}") String key,
            @Value("${spring.ai.deepseek.chat.options.model:deepseek-flash}") String model,
            @Value("${spring.ai.deepseek.base-url:https://api.deepseek.com}") String url) {
        this.jdbc = jdbc; this.cipher = cipher;
        this.bootstrapKey = key; this.bootstrapModel = model; this.bootstrapUrl = url;
    }

    @Transactional
    public Settings list(UUID owner) {
        initialize(owner);
        UUID active = activeId(owner);
        var rows = jdbc.query("SELECT * FROM ai_model_configuration WHERE owner_id=? ORDER BY created_at,id",
                (rs, n) -> new Summary(rs.getObject("id", UUID.class), rs.getString("name"),
                        rs.getString("provider"), rs.getString("model"), rs.getString("base_url"),
                        true, rs.getLong("revision"), rs.getObject("id", UUID.class).equals(active)), owner);
        return new Settings(rows, active);
    }

    @Transactional
    public Summary save(UUID owner, UUID id, Input input) {
        initialize(owner);
        validate(input);
        String encrypted;
        long revision = 0;
        if (id == null) {
            if (jdbc.queryForObject("SELECT count(*) FROM ai_model_configuration WHERE owner_id=?", Integer.class, owner) >= 30)
                throw new BizException("最多保存 30 份模型配置");
            requireKey(input.apiKey());
            encrypted = cipher.encrypt(input.apiKey().trim());
            id = UUID.randomUUID();
        } else {
            Credential old = credential(owner, id);
            if ((!old.summary.provider().equals(input.provider()) || !old.summary.baseUrl().equals(normalizeUrl(input.baseUrl())))
                    && (input.apiKey() == null || input.apiKey().isBlank()))
                throw new BizException("平台或 API 地址变化后，请重新填写 API Key");
            if (input.revision() == null || input.revision() != old.summary.revision())
                throw new ConflictException("配置已更新，请刷新后重试");
            revision = old.summary.revision() + 1;
            encrypted = input.apiKey() == null || input.apiKey().isBlank() ? old.encryptedKey : cipher.encrypt(input.apiKey().trim());
        }
        Integer duplicates = jdbc.queryForObject(
                "SELECT count(*) FROM ai_model_configuration WHERE owner_id=? AND name=? AND id<>?",
                Integer.class, owner, input.name().trim(), id);
        if (duplicates != null && duplicates > 0) throw new ConflictException("配置名称已存在");
        jdbc.update("""
                INSERT INTO ai_model_configuration(id,owner_id,name,provider,model,base_url,api_key_cipher,revision)
                VALUES (?,?,?,?,?,?,?,?)
                ON CONFLICT(id) DO UPDATE SET name=excluded.name,provider=excluded.provider,model=excluded.model,
                base_url=excluded.base_url,api_key_cipher=excluded.api_key_cipher,revision=excluded.revision,updated_at=now()
                """, id, owner, input.name().trim(), input.provider(), input.model().trim(),
                normalizeUrl(input.baseUrl()), encrypted, revision);
        if (activeId(owner) == null)
            jdbc.update("UPDATE ai_model_selection SET configuration_id=? WHERE owner_id=?", id, owner);
        return credential(owner, id).summary;
    }

    @Transactional
    public void activate(UUID owner, UUID id) {
        initialize(owner);
        Credential config = credential(owner, id);
        cipher.decrypt(config.encryptedKey);
        jdbc.update("UPDATE ai_model_selection SET configuration_id=? WHERE owner_id=?", id, owner);
    }

    @Transactional
    public void delete(UUID owner, UUID id, long revision) {
        if (revision < 0) throw new BizException("配置 revision 不能小于 0");
        initialize(owner);
        Credential config = credential(owner, id);
        if (config.summary.revision() != revision) throw new ConflictException("配置已更新，请刷新后重试");
        if (id.equals(activeId(owner)))
            jdbc.update("UPDATE ai_model_selection SET configuration_id=NULL WHERE owner_id=?", owner);
        jdbc.update("DELETE FROM ai_model_configuration WHERE id=? AND owner_id=?", id, owner);
    }

    @Transactional
    public Credential selected(UUID owner) {
        initialize(owner);
        UUID id = activeId(owner);
        if (id == null) throw new BizException("请先在“模型设置”中添加并启用模型");
        return credential(owner, id);
    }

    @Transactional
    public Credential forTest(UUID owner, UUID id, Input input) {
        initialize(owner);
        validate(input);
        String key = input.apiKey();
        if ((key == null || key.isBlank()) && id != null) {
            Credential saved = credential(owner,id);
            if (!saved.summary.provider().equals(input.provider()) || !saved.summary.baseUrl().equals(normalizeUrl(input.baseUrl())))
                throw new BizException("平台或 API 地址变化后，请重新填写 API Key");
            key = cipher.decrypt(saved.encryptedKey);
        }
        requireKey(key);
        return new Credential(new Summary(id, input.name().trim(), input.provider(), input.model().trim(),
                normalizeUrl(input.baseUrl()), true, input.revision() == null ? 0 : input.revision(), false), cipher.encrypt(key.trim()));
    }

    String apiKey(Credential credential) { return cipher.decrypt(credential.encryptedKey); }

    private void initialize(UUID owner) {
        jdbc.queryForObject("SELECT id FROM app_user WHERE id=? FOR UPDATE", UUID.class, owner);
        if (!jdbc.queryForList("SELECT owner_id FROM ai_model_selection WHERE owner_id=?", owner).isEmpty()) return;
        jdbc.update("INSERT INTO ai_model_selection(owner_id) VALUES (?)", owner);
        if (bootstrapKey != null && !bootstrapKey.isBlank() && !"your-api-key".equals(bootstrapKey)) {
            UUID id = UUID.randomUUID();
            jdbc.update("""
                    INSERT INTO ai_model_configuration(id,owner_id,name,provider,model,base_url,api_key_cipher)
                    VALUES (?,?,?,'deepseek',?,?,?)
                    """, id, owner, "DeepSeek", bootstrapModel, normalizeUrl(bootstrapUrl), cipher.encrypt(bootstrapKey.trim()));
            jdbc.update("UPDATE ai_model_selection SET configuration_id=? WHERE owner_id=?", id, owner);
        }
    }

    private UUID activeId(UUID owner) {
        return jdbc.queryForObject("SELECT configuration_id FROM ai_model_selection WHERE owner_id=?", UUID.class, owner);
    }

    private Credential credential(UUID owner, UUID id) {
        var rows = jdbc.query("""
                SELECT c.*, c.id=s.configuration_id AS active FROM ai_model_configuration c
                JOIN ai_model_selection s ON s.owner_id=c.owner_id WHERE c.id=? AND c.owner_id=?
                """, (rs,n) -> new Credential(new Summary(rs.getObject("id",UUID.class),rs.getString("name"),
                rs.getString("provider"),rs.getString("model"),rs.getString("base_url"),true,
                rs.getLong("revision"),rs.getBoolean("active")),rs.getString("api_key_cipher")),id,owner);
        if (rows.isEmpty()) throw new NotFoundException("模型配置不存在");
        return rows.get(0);
    }

    static void validate(Input input) {
        if (input == null || input.name() == null || input.name().isBlank() || input.name().trim().length()>80
                || input.provider() == null || !Set.of("deepseek","zhipuai","openai").contains(input.provider())
                || input.model() == null || input.model().isBlank() || input.model().length()>160
                || input.apiKey()!=null && (input.apiKey().length()>4096 || input.apiKey().chars().anyMatch(c -> c<32 || c==127)))
            throw new BizException("模型配置格式错误");
        normalizeUrl(input.baseUrl());
    }

    static String normalizeUrl(String value) {
        try {
            URI uri = URI.create(value.trim());
            String host = uri.getHost();
            boolean local = host != null && Set.of("localhost","127.0.0.1","[::1]","::1","host.docker.internal").contains(host.toLowerCase());
            if (value.length()>1024 || host==null || uri.getUserInfo()!=null || uri.getQuery()!=null
                    || uri.getFragment()!=null || uri.getPort()==0 || uri.getPort()>65535
                    || !("https".equals(uri.getScheme()) || local && "http".equals(uri.getScheme()))
                    || uri.getRawPath().contains("..") || value.contains("\\"))
                throw new IllegalArgumentException();
            return value.trim().replaceAll("/+$","");
        } catch (Exception e) {
            throw new BizException("API 地址须为 HTTPS；本机服务可使用 HTTP，地址应为 API 根路径");
        }
    }

    private void requireKey(String key) {
        if (key==null || key.isBlank()) throw new BizException("请填写 API Key");
    }
}

