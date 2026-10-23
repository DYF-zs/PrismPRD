package com.prismprd.knowledge;

import com.fasterxml.jackson.databind.JsonNode;
import com.prismprd.common.BizException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import java.util.*;
import java.util.concurrent.Semaphore;

/** JChatMind RagServiceImpl's Ollama embedding adapter, with configurable endpoint,
 * current batched /api/embed protocol, bounded concurrency, timeout and vector validation.
 */
@Component
public class OllamaEmbeddingClient {
    private final RestClient client;
    private final String model;
    private final int dimensions;
    private final Semaphore slots = new Semaphore(2);

    public OllamaEmbeddingClient(@Value("${prism.knowledge.ollama-url:http://127.0.0.1:11434}") String url,
            @Value("${prism.knowledge.embedding-model:bge-m3}") String model,
            @Value("${prism.knowledge.embedding-dimensions:1024}") int dimensions) {
        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(5000);
        requestFactory.setReadTimeout(180000);
        this.client = RestClient.builder().baseUrl(url).requestFactory(requestFactory).build();
        this.model = model;
        this.dimensions = dimensions;
    }
    public String model() { return model; }
    public int dimensions() { return dimensions; }

    public List<double[]> embed(List<String> texts) {
        if (texts.isEmpty() || texts.size() > 8) throw new BizException("向量批次应包含 1–8 个文本");
        if (!slots.tryAcquire()) throw new BizException(503, "向量模型正忙，请稍后重试");
        try {
            JsonNode response = client.post().uri("/api/embed")
                    .body(Map.of("model", model, "input", texts, "truncate", false, "keep_alive", "10m"))
                    .retrieve().body(JsonNode.class);
            if (response == null || !response.path("embeddings").isArray() || response.path("embeddings").size() != texts.size())
                throw new BizException(502, "向量模型返回的批次数量不正确");
            List<double[]> vectors = new ArrayList<>();
            for (JsonNode vector : response.path("embeddings")) {
                if (!vector.isArray() || vector.size() != dimensions) throw new BizException(502, "向量维度与配置不一致，需检查模型后重新索引");
                double[] values = new double[dimensions];
                for (int i = 0; i < dimensions; i++) {
                    if (!vector.get(i).isNumber()) throw new BizException(502, "向量包含非数值");
                    values[i] = vector.get(i).doubleValue();
                }
                vectors.add(normalize(values));
            }
            return vectors;
        } catch (BizException e) { throw e; }
        catch (Exception e) { throw new BizException(503, "无法使用本地向量模型，请启动 Ollama 并确认已下载 " + model); }
        finally { slots.release(); }
    }

    static double[] normalize(double[] input) {
        double norm = 0;
        for (double value : input) {
            if (!Double.isFinite(value)) throw new BizException(502, "向量包含非法数值");
            norm += value * value;
        }
        if (!Double.isFinite(norm) || norm < 1e-20) throw new BizException(502, "向量为空或非法");
        double[] normalized = input.clone();
        norm = Math.sqrt(norm);
        for (int i = 0; i < input.length; i++) normalized[i] /= norm;
        return normalized;
    }

    static String literal(double[] vector) {
        return "{" + String.join(",", Arrays.stream(vector).mapToObj(Double::toString).toList()) + "}";
    }
}
