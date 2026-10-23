package com.prismprd.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.ai.deepseek.api.DeepSeekApi;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.zhipuai.ZhiPuAiChatModel;
import org.springframework.ai.zhipuai.ZhiPuAiChatOptions;
import org.springframework.ai.zhipuai.api.ZhiPuAiApi;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.HttpMethod;
import com.prismprd.common.BizException;
import java.net.URI;
import java.io.IOException;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
public class ModelAdapterConfiguration {
    private static ResponseErrorHandler errors() {
        return new ResponseErrorHandler() {
            @Override public boolean hasError(ClientHttpResponse response) throws IOException {
                return !response.getStatusCode().is2xxSuccessful();
            }
            @Override public void handleError(URI uri, HttpMethod method, ClientHttpResponse response) throws IOException {
                throw new BizException(switch (response.getStatusCode().value()) {
                    case 401,403 -> "模型服务拒绝访问，请检查 API Key 与账户权限";
                    case 404 -> "模型接口或模型名称不存在，请检查 API 地址与模型名称";
                    case 429 -> "模型服务额度不足或请求过于频繁，请稍后重试";
                    default -> "模型服务请求失败，请检查平台状态与配置";
                });
            }
        };
    }
    private static RetryTemplate retry() { return RetryTemplate.builder().maxAttempts(1).fixedBackoff(1).build(); }
    private static RestClient.Builder rest(boolean deepseek, ObjectMapper mapper) {
        var http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NEVER).build();
        var factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofSeconds(120));
        var rest = RestClient.builder().requestFactory(factory).requestInterceptor((request, body, execution) -> {
            ModelEndpointPolicy.check(request.getURI());
            return execution.execute(request,body);
        });
        if (deepseek) rest.requestInterceptor(DeepSeekConfiguration.nonThinking(mapper));
        return rest;
    }

    @Bean ModelAdapter deepseekAdapter(ObjectMapper mapper, ToolCallingManager tools) {
        return new ModelAdapter() {
            public String provider() { return "deepseek"; }
            public org.springframework.ai.chat.model.ChatModel create(String model, String url, String key) {
                return DeepSeekChatModel.builder().deepSeekApi(DeepSeekApi.builder().baseUrl(url).apiKey(key)
                        .responseErrorHandler(errors())
                        .restClientBuilder(rest(true,mapper)).build())
                        .defaultOptions(DeepSeekChatOptions.builder().model(model).temperature(0.2).build())
                        .toolCallingManager(tools).retryTemplate(retry()).build();
            }
        };
    }
    @Bean ModelAdapter zhipuaiAdapter(ObjectMapper mapper, ToolCallingManager tools) {
        return new ModelAdapter() {
            public String provider() { return "zhipuai"; }
            public org.springframework.ai.chat.model.ChatModel create(String model, String url, String key) {
                return new ZhiPuAiChatModel(ZhiPuAiApi.builder().baseUrl(url).apiKey(key)
                        .responseErrorHandler(errors())
                        .restClientBuilder(rest(false,mapper)).build(),
                        ZhiPuAiChatOptions.builder().model(model).temperature(0.2).build(),
                        tools,retry(),ObservationRegistry.NOOP);
            }
        };
    }
    @Bean ModelAdapter openaiAdapter(ObjectMapper mapper, ToolCallingManager tools) {
        return new ModelAdapter() {
            public String provider() { return "openai"; }
            public org.springframework.ai.chat.model.ChatModel create(String model, String url, String key) {
                return OpenAiChatModel.builder().openAiApi(OpenAiApi.builder().baseUrl(url).apiKey(key)
                        .responseErrorHandler(errors())
                        .completionsPath("/chat/completions")
                        .restClientBuilder(rest(false,mapper)).build())
                        .defaultOptions(OpenAiChatOptions.builder().model(model).temperature(0.2).build())
                        .toolCallingManager(tools).retryTemplate(retry()).build();
            }
        };
    }
}

