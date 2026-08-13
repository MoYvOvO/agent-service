package Agent.AgentTest.Util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.Key;

public class JwtUtil {

    // ============ 密钥直接写死在这里 ============
    private static final String SECRET_KEY = "your-256-bit-secret-key-change-in-production";

    private static SecretKey getKey() {
        return Keys.hmacShaKeyFor(SECRET_KEY.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 解析 Token
     */
    public static Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(getKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * 从 Token 中获取 userId
     */
    public static Long getUserId(String token) {
        return parseToken(token).get("userId", Long.class);
    }

    /**
     * 从 Token 中获取角色
     */
    public static String getRole(String token) {
        return parseToken(token).get("role", String.class);
    }
}