package Agent.AgentTest.Dto;

import lombok.Data;

@Data
public class ChatRequest {
    private String message;
    private String userId;
    // 无参构造（Spring 反序列化 JSON 时需要）
    public ChatRequest() {
    }

    // 有参构造（方便测试）
    public ChatRequest(String message) {
        this.message = message;
    }
}
