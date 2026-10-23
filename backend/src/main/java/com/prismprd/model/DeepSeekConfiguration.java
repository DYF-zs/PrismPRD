package com.prismprd.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.ai.deepseek.api.DeepSeekApi;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.model.deepseek.autoconfigure.DeepSeekChatProperties;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** DeepSeek 工具调用配置，使用非思考模式生成响应。 */
@Configuration
@ConditionalOnProperty(name = "spring.ai.model.chat", havingValue = "deepseek", matchIfMissing = true)
public class DeepSeekConfiguration {
    @Bean
    DeepSeekChatModel deepSeekChatModel(DeepSeekApi api, DeepSeekChatProperties properties,
                                      ToolCallingManager tools, RetryTemplate retry) {
        return DeepSeekChatModel.builder().deepSeekApi(api).defaultOptions(properties.getOptions())
                .toolCallingManager(tools).retryTemplate(retry).build();
    }

    static ClientHttpRequestInterceptor nonThinking(ObjectMapper mapper) {
        return (request, body, execution) -> {
            ObjectNode json = (ObjectNode) mapper.readTree(body);
            json.putObject("thinking").put("type", "disabled");
            byte[] updated = mapper.writeValueAsBytes(json);
            request.getHeaders().setContentLength(updated.length);
            return execution.execute(request, updated);
        };
    }

    @Bean
    DeepSeekApi deepSeekApi(ObjectMapper mapper,
                           @Value("${spring.ai.deepseek.api-key}") String key,
                           @Value("${spring.ai.deepseek.base-url}") String baseUrl) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(15_000);
        factory.setReadTimeout(180_000);
        return DeepSeekApi.builder().apiKey(key).baseUrl(baseUrl)
                .restClientBuilder(RestClient.builder().requestFactory(factory)
                        .requestInterceptor(nonThinking(mapper))).build();
    }
}
