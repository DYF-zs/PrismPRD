package com.prismprd.model;

import com.prismprd.common.BizException;
import com.prismprd.security.CurrentUserService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.DefaultToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientResponseException;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class ModelClientRegistry {
    public record Selected(ChatModel model, String profile) {
        public ChatClient client() { return ChatClient.create(model); }
        public <T> T entity(String system, String user, Class<T> type) {
            try { return client().prompt().system(system).user(user).call().entity(type); }
            catch (BizException e) { throw e; }
            catch (Exception e) { throw new BizException("模型返回的内容不符合结构化要求，请重试或更换模型"); }
        }
    }
    public record TestResult(boolean structuredOutput, boolean toolCalling, String message) {}
    private record ConnectionCheck(String status) {}

    private final ModelSettingsService settings;
    private final CurrentUserService users;
    private final Map<String, ModelAdapter> adapters;
    public ModelClientRegistry(ModelSettingsService settings, CurrentUserService users, List<ModelAdapter> adapters) {
        this.settings=settings; this.users=users;
        this.adapters=adapters.stream().collect(Collectors.toUnmodifiableMap(ModelAdapter::provider, Function.identity()));
    }
    public Selected current() { return ModelScope.current(() -> resolve(users.currentUserId())); }
    Selected resolve(UUID owner) { return create(settings.selected(owner)); }
    private Selected create(ModelSettingsService.Credential credential) {
        var summary = credential.summary;
        var adapter = adapters.get(summary.provider());
        if (adapter==null) throw new BizException("模型平台尚未注册");
        ChatModel delegate = adapter.create(summary.model(),summary.baseUrl(),settings.apiKey(credential));
        ChatModel safe = new ChatModel() {
            public ChatResponse call(Prompt prompt) {
                try { return delegate.call(prompt); } catch (Exception e) { throw safeFailure(e); }
            }
            public Flux<ChatResponse> stream(Prompt prompt) {
                try { return delegate.stream(prompt).onErrorMap(ModelClientRegistry::safeFailure); }
                catch (Exception e) { return Flux.error(safeFailure(e)); }
            }
            public ChatOptions getDefaultOptions() { return delegate.getDefaultOptions(); }
        };
        return new Selected(safe,summary.provider()+"/"+summary.model());
    }

    public TestResult test(UUID owner, UUID id, ModelSettingsService.Input input) {
        Selected selected = create(settings.forTest(owner,id,input));
        ConnectionCheck check;
        try {
            check=selected.client().prompt().system("Return a JSON object with a single field status equal to connected.")
                    .user("Check the connection.").call().entity(ConnectionCheck.class);
        } catch (Exception e) { throw safeFailure(e); }
        if (check==null || !"connected".equals(check.status()))
            throw new BizException("模型已响应，但未通过结构化输出测试，请检查模型名称和兼容性");
        ToolCallback tool = new ToolCallback() {
            public ToolDefinition getToolDefinition() {
                return ToolDefinition.builder().name("prism_connection_check").description("Check connection")
                        .inputSchema("{\"type\":\"object\",\"properties\":{},\"additionalProperties\":false}").build();
            }
            public String call(String input) { throw new IllegalStateException("Connection check tools are not executed"); }
        };
        ChatResponse response = selected.model().call(new Prompt(
                "You must call the prism_connection_check tool exactly once, using an empty JSON object as arguments.",
                DefaultToolCallingChatOptions.builder().toolCallbacks(tool).internalToolExecutionEnabled(false).build()));
        boolean supported = response!=null && response.getResult()!=null && response.getResult().getOutput()!=null
                && response.getResult().getOutput().getToolCalls().stream().anyMatch(t -> "prism_connection_check".equals(t.name()));
        return new TestResult(true,supported,supported ? "连接正常，结构化输出与工具调用验证通过"
                : "结构化输出正常，工具调用未通过验证；AI 对话中的正文修改和知识检索可能不可用");
    }

    static BizException safeFailure(Throwable error) {
        for (Throwable current=error; current!=null; current=current.getCause()) {
            if (current instanceof BizException biz) return new BizException(biz.getMessage());
            if (current instanceof RestClientResponseException response) {
                return switch (response.getStatusCode().value()) {
                    case 401,403 -> new BizException("模型服务拒绝访问，请检查 API Key 与账户权限");
                    case 404 -> new BizException("模型接口或模型名称不存在，请检查 API 地址与模型名称");
                    case 429 -> new BizException("模型服务额度不足或请求过于频繁，请稍后重试");
                    default -> new BizException("模型服务请求失败，请检查平台状态与配置");
                };
            }
        }
        return new BizException("模型调用失败，请检查连接、模型能力与账户额度后重试");
    }
}

