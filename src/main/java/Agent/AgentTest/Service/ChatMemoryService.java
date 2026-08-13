package Agent.AgentTest.Service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class ChatMemoryService {

    @Autowired
    private StringRedisTemplate redisTemplate;

    private static final String KEY_PREFIX = "chat:session:";
    private static final int MAX_HISTORY = 10;   // 保留最近 10 轮
    private static final long TIMEOUT = 1800;    // 30 分钟过期（秒）

    /**
     * 获取历史记录
     */
    public List<Map<String, String>> getHistory(String sessionId) {
        String key = KEY_PREFIX + sessionId;
        List<String> list = redisTemplate.opsForList().range(key, 0, -1);
        if (list == null || list.isEmpty()) {
            return new ArrayList<>();
        }
        return list.stream()
                .map(this::parseMessage)
                .collect(Collectors.toList());
    }

    /**
     * 添加一条消息（用户或助手）
     */
    public void addMessage(String sessionId, String role, String content) {
        String key = KEY_PREFIX + sessionId;
        String msgJson = "{\"role\":\"" + role + "\",\"content\":\"" + escapeJson(content) + "\"}";
        redisTemplate.opsForList().rightPush(key, msgJson);
        redisTemplate.opsForList().trim(key, -MAX_HISTORY, -1);
        redisTemplate.expire(key, TIMEOUT, TimeUnit.SECONDS);
    }

    /**
     * 清空会话
     */
    public void clear(String sessionId) {
        redisTemplate.delete(KEY_PREFIX + sessionId);
    }

    // ========== 私有辅助方法 ==========

    /**
     * 解析 JSON 字符串为 Map
     */
    private Map<String, String> parseMessage(String json) {
        Map<String, String> map = new HashMap<>();
        try {
            // 去掉大括号
            String content = json.trim();
            if (content.startsWith("{") && content.endsWith("}")) {
                content = content.substring(1, content.length() - 1);
            }
            // 按逗号分割键值对
            String[] pairs = content.split(",");
            for (String pair : pairs) {
                String[] kv = pair.split(":", 2);
                if (kv.length == 2) {
                    String key = kv[0].trim().replaceAll("^\"|\"$", "");
                    String value = kv[1].trim().replaceAll("^\"|\"$", "");
                    map.put(key, value);
                }
            }
        } catch (Exception e) {
            // 解析失败返回空 Map
            return new HashMap<>();
        }
        return map;
    }

    /**
     * 转义 JSON 字符串中的特殊字符
     */
    private String escapeJson(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}