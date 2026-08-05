package com.example.rag.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

/**
 * AI 服务内部调用签名提供器。
 *
 * <p>Java 后端调用 Python AI 服务时，通过 HMAC-SHA256 对
 * {@code method:path:timestamp:nonce} 签名，并把签名与时间戳、nonce
 * 放在请求头中。Python 侧用同一共享密钥（AI_INTERNAL_SECRET）校验，
 * 防止内网中任意进程伪造 AI 服务调用。</p>
 *
 * <p>签名头：</p>
 * <pre>
 *   X-Internal-Timestamp  签发时间（epoch 毫秒）
 *   X-Internal-Nonce      随机数（防重放）
 *   X-Internal-Signature  base64(HMAC-SHA256(method:path:timestamp:nonce, secret))
 * </pre>
 *
 * @author knowledge-rag-team
 */
@Slf4j
@Component
public class AiInternalTokenProvider {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    /** 签名有效期（毫秒），与服务端校验窗口保持一致 */
    private static final long SIGNATURE_TTL_MS = 300_000L;

    /** 共享密钥：与 Python 侧 AI_INTERNAL_SECRET 保持一致 */
    @Value("${ai.internal-secret:dev-internal-secret-change-me}")
    private String internalSecret;

    /**
     * 生成内部调用签名头。
     *
     * @param method HTTP 方法（大写，如 GET / POST）
     * @param path   请求路径（不含查询串，如 /ai/search）
     * @return 签名头键值对
     */
    public java.util.Map<String, String> buildSignatureHeaders(String method, String path) {
        long timestamp = System.currentTimeMillis();
        String nonce = UUID.randomUUID().toString().replace("-", "");
        String payload = method + ":" + path + ":" + timestamp + ":" + nonce;
        String signature = hmacSha256(payload);

        return java.util.Map.of(
                "X-Internal-Timestamp", String.valueOf(timestamp),
                "X-Internal-Nonce", nonce,
                "X-Internal-Signature", signature
        );
    }

    /** 仅供测试/联调：查看当前有效期毫秒数 */
    long signatureTtlMs() {
        return SIGNATURE_TTL_MS;
    }

    private String hmacSha256(String payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(
                    internalSecret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return Base64.getEncoder().encodeToString(
                    mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            log.error("AI 内部签名生成失败", e);
            throw new IllegalStateException("AI 内部签名生成失败", e);
        }
    }
}
