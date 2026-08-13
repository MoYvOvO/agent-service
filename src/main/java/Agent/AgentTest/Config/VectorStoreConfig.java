package Agent.AgentTest.Config;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
@Configuration
public class VectorStoreConfig {
    private static final Logger log = LoggerFactory.getLogger(VectorStoreConfig.class);
    @Value("${siliconflow.api-key}")
    private String siliconflowApiKey;

    @Bean
    @Primary
    public VectorStore vectorStore() {
        RestClient restClient = RestClient.builder()
                .baseUrl("https://api.siliconflow.cn/v1")
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + siliconflowApiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();

        return SimpleVectorStore.builder(new EmbeddingModel() {
            @Override
            public float[] embed(String text) {
                return embed(List.of(text)).get(0);
            }

            @Override
            public List<float[]> embed(List<String> texts) {
              //  System.out.println("🔍 调用硅基流动 API，文本长度: " + texts.get(0).length());
                log.info("🔍 调用硅基流动 API，文本长度:{} ",texts.get(0).length());
                String input = texts.get(0);
                Map<String, Object> requestBody = Map.of(
                        "input", input,
                        "model", "BAAI/bge-large-zh-v1.5"
                );

                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> response = restClient.post()
                            .uri("/embeddings")
                            .body(requestBody)
                            .retrieve()
                            .body(Map.class);

                    if (response == null || !response.containsKey("data")) {
                        throw new RuntimeException("Embedding API 响应异常");
                    }

                    List<Map<String, Object>> dataList = (List<Map<String, Object>>) response.get("data");
                    List<float[]> result = dataList.stream()
                            .map(data -> {
                                List<Double> doubles = (List<Double>) data.get("embedding");
                                float[] floats = new float[doubles.size()];
                                for (int i = 0; i < doubles.size(); i++) {
                                    floats[i] = doubles.get(i).floatValue();
                                }
                                return floats;
                            })
                            .collect(Collectors.toList());
                   // System.out.println("✅ 嵌入成功，返回 " + result.size() + " 个向量");
                    log.info("✅ 嵌入成功，返回{}个向量",result.size());
                    return result;
                } catch (Exception e) {
                   // System.err.println("❌ 硅基流动 API 调用失败: " + e.getMessage());
                    log.error("硅基流动 API 调用失败: {}", e.getMessage(), e);
                    e.printStackTrace();
                    throw new RuntimeException(e);
                }
            }

            @Override
            public float[] embed(Document document) {
                String text = document.getText();
               // System.out.println("📄 嵌入文档，内容预览: " + text.substring(0, Math.min(20, text.length())) + "...");
                log.info("📄 嵌入文档，内容预览:{}... ",text.substring(0, Math.min(20, text.length())));
                return embed(text);
            }

            @Override
            public EmbeddingResponse call(EmbeddingRequest request) {
                throw new UnsupportedOperationException("Not used");
            }
        }).build();
    }
}