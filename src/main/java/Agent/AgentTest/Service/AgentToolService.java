package Agent.AgentTest.Service;

import Agent.AgentTest.Controller.AiController;
import Agent.AgentTest.Dto.Product;
import Agent.AgentTest.Dto.ProductListResult;
import Agent.AgentTest.Dto.Result;
import Agent.AgentTest.Feign.StockFeignClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class AgentToolService {
    private static final Logger log = LoggerFactory.getLogger(AiController.class);
    @Autowired
    private StockFeignClient stockFeignClient;
    @Autowired
    private VectorStore vectorStore;
    private final ChatClient chatClient;

    public AgentToolService(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    // ========== 工具1：查询库存 ==========
    @Tool(description = "根据商品ID查询实时库存，返回库存数量。用户问库存时调用此工具。")
    public String queryStock(String productId) {
        try {
            String stock = stockFeignClient.getStock(productId);
            return "商品 " + productId + " 当前库存为 " + stock + " 件";
        } catch (Exception e) {
            return "库存服务暂时不可用，请稍后再试。";
        }
    }

    // ========== 工具2：查询商品 ==========
    @Tool(description = "根据关键词查询商品信息，返回商品列表（含id、name、price、stock）。用户问'有什么商品'、'有哪些耳机'时调用。")
    public String queryProduct(String keyword) {
        try {
            ProductListResult result = stockFeignClient.getAllProducts();
            if (result.getCode() == 200 && result.getData() != null) {
                List<Product> productList = result.getData().getData();  // ← 注意：两层 data
                if (productList == null || productList.isEmpty()) {
                    return "未找到相关商品";
                }
                // 按关键词过滤
                List<Product> filtered = productList.stream()
                        .filter(p -> p.getName() != null && p.getName().contains(keyword))
                        .collect(Collectors.toList());
                if (filtered.isEmpty()) {
                    return "未找到与 '" + keyword + "' 相关的商品";
                }
                return new ObjectMapper().writeValueAsString(filtered);
            }
            return "商品服务暂时不可用";
        } catch (Exception e) {
            log.error("查询商品失败", e);
            return "商品服务暂时不可用";
        }
    }

    // ========== 工具3：查询价格 ==========
    @Tool(description = "根据商品ID查询商品价格。用户问价格时调用此工具。")
    public String queryPrice(String productId) {
        try {
            Result<Product> result = stockFeignClient.getProductById(productId);
            if (result.getCode() == 200 && result.getData() != null) {
                return String.valueOf(result.getData().getPrice());
            }
            return "未知商品";
        } catch (Exception e) {
            log.error("查询价格失败", e);
            return "价格服务暂时不可用";
        }
    }
    // ========== 工具3：rag ==========
    @Tool(description = "从知识库中检索相关信息。当用户询问商品详情、使用说明、售后政策、秒杀规则等业务知识时调用此工具。")
    public String searchKnowledge(String query) {
        try {
            // 1. 向量检索
            SearchRequest searchRequest = SearchRequest.builder()
                    .query(query)
                    .topK(3)
                    .build();
            List<Document> documents = vectorStore.similaritySearch(searchRequest);

            if (documents == null || documents.isEmpty()) {
                return "未找到相关知识";
            }

            // 2. 拼接上下文
            String context = documents.stream()
                    .map(Document::getText)
                    .collect(Collectors.joining("\n\n---\n\n"));

            // 3. 调用大模型总结
            String systemPrompt = "你是一个基于知识库回答的助手。请根据以下参考资料回答用户的问题。" +
                    "如果参考资料中没有相关信息，直接回答'资料中未提及'，不要编造。\n\n【参考资料】\n" + context;

            String answer = chatClient.prompt()
                    .system(systemPrompt)
                    .user(query)
                    .call()
                    .content();

            return answer;
        } catch (Exception e) {
            log.error("RAG 检索失败", e);
            return "知识库服务暂时不可用";
        }
    }
}