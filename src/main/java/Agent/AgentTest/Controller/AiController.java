package Agent.AgentTest.Controller;

import Agent.AgentTest.Config.PromptConfig;
import Agent.AgentTest.Dto.ChatRequest;
import Agent.AgentTest.Dto.Result;
import Agent.AgentTest.Dto.StructuredResult;
import Agent.AgentTest.Feign.StockFeignClient;
import Agent.AgentTest.Service.AgentToolService;
import Agent.AgentTest.Util.JwtUtil;
import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import jakarta.annotation.PostConstruct;
import org.apache.commons.codec.digest.DigestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage.ToolCall;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;
import com.fasterxml.jackson.core.type.TypeReference;   // ✅ 正确
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/ai")
public class AiController {

    private final ChatClient chatClient;
    private static final Logger log = LoggerFactory.getLogger(AiController.class);

    @Autowired
    private VectorStore vectorStore;
    @Autowired
    private StockFeignClient stockFeignClient;
    @Autowired
    private StringRedisTemplate redisTemplate;
    @Autowired
    @Qualifier("aiExecutor")
    private ExecutorService aiExecutor;
    @Autowired
    private AgentToolService agentToolService;
    @Autowired
    private PromptConfig promptConfig;

    public AiController(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    // ========== 2.1 会话记忆 ==========
    @GetMapping("/chat")
    public String chat(@RequestParam String message, @RequestParam String userId) {
        String key = "chat:history:" + userId;
        List<String> history = redisTemplate.opsForList().range(key, 0, -1);
        StringBuilder fullPrompt = new StringBuilder();
        if (history != null) {
            for (String h : history) {
                fullPrompt.append(h).append("\n");
            }
        }
        fullPrompt.append("用户: ").append(message);
        String response = chatClient.prompt()
                .user(fullPrompt.toString())
                .call()
                .content();
        redisTemplate.opsForList().rightPush(key, "用户: " + message);
        redisTemplate.opsForList().rightPush(key, "AI: " + response);
        redisTemplate.opsForList().trim(key, -10, -1);
        redisTemplate.expire(key, 30, TimeUnit.MINUTES);
        return response;
    }

    // ========== RAG 问答 ==========
    @PostMapping("/rag/chat")
    public Result<String> ragChat(@RequestBody ChatRequest request) {
        SearchRequest searchRequest = SearchRequest.builder()
                .query(request.getMessage())
                .topK(3)
                .build();
        List<org.springframework.ai.document.Document> documents = vectorStore.similaritySearch(searchRequest);
        if (documents.isEmpty()) {
            return Result.error("当前知识库中未找到相关信息，请上传相关文档。");
        }
        String context = documents.stream()
                .map(doc -> doc.getText())
                .collect(Collectors.joining("\n\n---\n\n"));
        String systemPrompt = String.format(promptConfig.getRag(), context);
        Prompt prompt = new Prompt(
                new SystemMessage(systemPrompt),
                new UserMessage(request.getMessage())
        );
        String answer = chatClient.prompt(prompt).call().content();
        return Result.success(answer);
    }

//    // ========== 测试库存（Feign） ==========
//    @GetMapping("/stock/{productId}")
//    public String testStock(@PathVariable String productId) {
//        return stockFeignClient.getStock(productId);
//    }

    // ========== 2.2 & 2.3 工具调用 + 异步缓存 ==========
    @PostMapping("/agent/chat")
    public CompletableFuture<Result<String>> agentChat(@RequestBody ChatRequest request) {
        log.info("📥 收到请求，当前Tomcat线程: {}", Thread.currentThread().getName());
        log.info("🔍 aiExecutor 是否为 null: {}", aiExecutor == null);
        String userMessage = request.getMessage();
        String cacheKey = "agent:chat:" + DigestUtils.md5Hex(userMessage);
//        if (userMessage.contains("异常")) {
//            throw new RuntimeException("模拟异常");
//        }
        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            log.info("✅ 命中缓存，直接返回");
            return CompletableFuture.completedFuture(Result.success("【缓存】" + cached));
        }

        return CompletableFuture.supplyAsync(() -> {
            log.info("🚀 实际执行AI调用的线程名: {}", Thread.currentThread().getName());
            try {
                String answer = chatClient.prompt()
                        .user(userMessage)
                        .tools(agentToolService)
                        .call()
                        .content();
                Result<String> result = Result.success(answer);
                if (result.getCode() == 200 && answer != null && !answer.trim().isEmpty()) {
                    redisTemplate.opsForValue().set(cacheKey, answer, 1, TimeUnit.DAYS);
                    log.info("💾 已存入缓存");
                }
                return Result.success(answer);
            } catch (Exception e) {
                log.error("AI 调用失败", e);
                return Result.error("AI 服务繁忙：" + e.getMessage());
            }
        }, aiExecutor);
    }

    // ========== 2.4 结构化输出 ==========
    @PostMapping("/agent/structured")
    public Result<StructuredResult> structuredChat(@RequestBody ChatRequest request) {
        String userMessage = request.getMessage();
//feian
        String systemPrompt = promptConfig.getStructured();
        String aiResponse = chatClient.prompt()
                .system(systemPrompt)
                .user(userMessage)
                .call()
                .content();

        try {
            ObjectMapper mapper = new ObjectMapper();
            StructuredResult result = mapper.readValue(aiResponse, StructuredResult.class);
            return Result.success(result);
        } catch (Exception e) {
            StructuredResult fallback = new StructuredResult();
            fallback.setType("text");
            fallback.setData(Map.of("content", aiResponse));
            fallback.setSummary("无法解析为结构化数据，以下是原始回复");
            return Result.success(fallback);
        }
    }

    // ========== 2.5 工具自动编排（核心亮点） ==========
    @PostMapping("/agent/orchestrate")
    public Result<String> orchestrate(@RequestBody ChatRequest request) {
        String userMessage = request.getMessage();

        ChatResponse response = chatClient.prompt()
                .user(userMessage)
                .tools(agentToolService)
                .call()
                .chatResponse();

        List<ToolCall> toolCalls = response.getResult().getOutput().getToolCalls();
        if (toolCalls != null && !toolCalls.isEmpty()) {
            List<Map<String, Object>> toolResults = new ArrayList<>();
            for (ToolCall call : toolCalls) {
                Object result = executeTool(call);
                // 使用 Jackson 提取工具名
                String toolName = extractToolName(call);
                toolResults.add(Map.of(
                        "toolName", toolName,
                        "result", result
                ));
            }
            String finalAnswer = chatClient.prompt()
                    .user(String.format(promptConfig.getOrchestrate(), userMessage, toolResults))
                    .call()
                    .content();
            return Result.success(finalAnswer);
        }

        return Result.success(response.getResult().getOutput().getText());
    }

    private Object executeTool(ToolCall call) {
        // 使用 Jackson 将 call 转为 Map
        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> callMap = mapper.convertValue(call, new TypeReference<Map<String, Object>>() {});

        // 提取 function 对象（可能嵌套）
        Map<String, Object> functionMap = (Map<String, Object>) callMap.get("function");
        String toolName;
        Map<String, Object> args;

        if (functionMap != null) {
            toolName = (String) functionMap.get("name");
            args = (Map<String, Object>) functionMap.get("arguments");
        } else {
            // 如果直接包含 name 和 arguments
            toolName = (String) callMap.get("name");
            args = (Map<String, Object>) callMap.get("arguments");
        }

        // 根据工具名调用 agentToolService 中的方法
        if ("queryStock".equals(toolName)) {
            String productId = (String) args.get("productId");
            return agentToolService.queryStock(productId);
        } else if ("queryProduct".equals(toolName)) {
            String keyword = (String) args.get("keyword");
            return agentToolService.queryProduct(keyword);
        } else if ("queryPrice".equals(toolName)) {
            String productId = (String) args.get("productId");
            return agentToolService.queryPrice(productId);
        } else {
            return "未知工具：" + toolName;
        }
    }
//    @PostMapping("/chat")
//    public CompletableFuture<Result<String>> unifiedChat(@RequestBody ChatRequest request) {
//        String message = request.getMessage();
//        String userId = request.getUserId() == null ? "anonymous" : request.getUserId();
//
//        if (isToolCall(message)) {
//            return toolChat(message, userId);
////        } else if (isRag(message)) {
////            log.info("🔍 走 RAG 分支（独立接口）");  // 加这行
////            return CompletableFuture.completedFuture(ragChat(request));
//        } else {
//            return CompletableFuture.completedFuture(Result.success(normalChat(message, userId)));
//        }
//    }
    // 辅助方法：提取工具名（用于记录）
    private String extractToolName(ToolCall call) {
        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> callMap = mapper.convertValue(call, new TypeReference<Map<String, Object>>() {});
        Map<String, Object> functionMap = (Map<String, Object>) callMap.get("function");
        if (functionMap != null) {
            return (String) functionMap.get("name");
        }
        return (String) callMap.get("name");
    }
@PostMapping("/chat")
public CompletableFuture<Result<String>> unifiedChat(@RequestBody ChatRequest request) {
    String message = request.getMessage();
    String userId = request.getUserId() == null ? "anonymous" : request.getUserId();
    String cacheKey = "agent:chat:" + DigestUtils.md5Hex(message);
    // 1. 检查 Redis 缓存
    String cached = redisTemplate.opsForValue().get(cacheKey);
    if (cached != null) {
        log.info("✅ 命中缓存，直接返回");
        return CompletableFuture.completedFuture(Result.success("【缓存】" + cached));
    }
    return CompletableFuture.supplyAsync(() -> {
        log.info("🚀 开始调用大模型，线程: {}", Thread.currentThread().getName());
        try {

            String answer = chatClient.prompt()
                    .user(message)
                    .tools(agentToolService)
                    .call()
                    .content();

            // 3. 存入缓存
            if (answer != null && !answer.isEmpty()) {
                redisTemplate.opsForValue().set(cacheKey, answer, 1, TimeUnit.DAYS);
                log.info("💾 已存入缓存");
            }
            return Result.success(answer);

        } catch (Exception e) {
            log.error("AI 调用失败", e);
            // 4. 降级处理
            return Result.error("AI 服务繁忙，请稍后重试：" + e.getMessage());
        }
    }, aiExecutor);
}
    // ========== Sentinel 限流初始化 ==========
    @PostConstruct
    public void initSentinelRules() {
        List<FlowRule> rules = new ArrayList<>();
        FlowRule rule = new FlowRule();
        rule.setResource("/ai/agent/chat");
        rule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        rule.setCount(10);
        rules.add(rule);
        FlowRuleManager.loadRules(rules);
        log.info("✅ Sentinel 限流规则已加载：/ai/agent/chat 接口 QPS 上限为 10");
    }
    //------------------方-法-----------------------------------------
    private boolean isToolCall(String message) {
        String[] keywords = {"库存", "价格", "多少钱", "有什么", "推荐", "查询", "耳机", "手表", "商品","根据资料", "参考文档", "知识库", "手册", "FAQ"};
        for (String kw : keywords) {
            if (message.contains(kw)) {
                return true;
            }
        }
        return false;
    }


    private String normalChat(String message, String userId) {
        String key = "chat:history:" + userId;
        List<String> history = redisTemplate.opsForList().range(key, 0, -1);
        StringBuilder fullPrompt = new StringBuilder();
        if (history != null) {
            for (String h : history) {
                fullPrompt.append(h).append("\n");
            }
        }
        fullPrompt.append("用户: ").append(message);
        String response = chatClient.prompt().user(fullPrompt.toString()).call().content();

        // 存历史
        redisTemplate.opsForList().rightPush(key, "用户: " + message);
        redisTemplate.opsForList().rightPush(key, "AI: " + response);
        redisTemplate.opsForList().trim(key, -10, -1);
        redisTemplate.expire(key, 30, TimeUnit.MINUTES);

        return response;
    }
    private Result<String> RagChat(ChatRequest request) {
        SearchRequest searchRequest = SearchRequest.builder()
                .query(request.getMessage())
                .topK(3)
                .build();
        List<org.springframework.ai.document.Document> documents = vectorStore.similaritySearch(searchRequest);
        if (documents.isEmpty()) {
            return Result.error("当前知识库中未找到相关信息，请上传相关文档。");
        }
        String context = documents.stream()
                .map(doc -> doc.getText())
                .collect(Collectors.joining("\n\n---\n\n"));
        String systemPrompt = String.format(promptConfig.getRag(), context);
        Prompt prompt = new Prompt(
                new SystemMessage(systemPrompt),
                new UserMessage(request.getMessage())
        );
        String answer = chatClient.prompt(prompt).call().content();
        return Result.success(answer);
    }
    private CompletableFuture<Result<String>> toolChat(String message, String userId) {
        String cacheKey = "agent:chat:" + DigestUtils.md5Hex(message);

        // 查缓存
        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            return CompletableFuture.completedFuture(Result.success("【缓存】" + cached));
        }

        // 取历史
        String historyKey = "chat:history:" + userId;
        List<String> history = redisTemplate.opsForList().range(historyKey, 0, -1);
        StringBuilder fullPrompt = new StringBuilder();
        if (history != null) {
            for (String h : history) {
                fullPrompt.append(h).append("\n");
            }
        }
        fullPrompt.append("用户: ").append(message);

        return CompletableFuture.supplyAsync(() -> {
            try {
                String answer = chatClient.prompt()
                        .user(fullPrompt.toString())
                        .tools(agentToolService)
                        .call()
                        .content();

                // 存历史
                redisTemplate.opsForList().rightPush(historyKey, "用户: " + message);
                redisTemplate.opsForList().rightPush(historyKey, "AI: " + answer);
                redisTemplate.opsForList().trim(historyKey, -10, -1);
                redisTemplate.expire(historyKey, 30, TimeUnit.MINUTES);

                // 缓存有效结果
                if (answer != null && !answer.trim().isEmpty()
                        && !answer.contains("暂时不可用") && !answer.contains("失败")) {
                    redisTemplate.opsForValue().set(cacheKey, answer, 1, TimeUnit.DAYS);
                }

                return Result.success(answer);
            } catch (Exception e) {
                log.error("工具调用失败", e);
                return Result.error("AI 服务繁忙：" + e.getMessage());
            }
        }, aiExecutor);
    }
}