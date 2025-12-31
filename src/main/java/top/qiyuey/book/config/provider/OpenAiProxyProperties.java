package top.qiyuey.book.config.provider;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.net.URI;

/**
 * OpenAI 代理配置属性
 * 支持 HTTP 和 SOCKS5 代理
 * 配置格式:
 * - HTTP: http://host:port 或 http://user:pass@host:port
 * - SOCKS5: socks5://host:port 或 socks5://user:pass@host:port
 */
@Data
@Component
@ConfigurationProperties(prefix = "spring.ai.openai.proxy")
public class OpenAiProxyProperties {

    /**
     * 代理 URL，支持格式:
     * - http://127.0.0.1:7890
     * - http://user:password@127.0.0.1:7890
     * - socks5://127.0.0.1:1080
     * - socks5://user:password@127.0.0.1:1080
     */
    private String url;

    /**
     * 是否启用代理
     */
    private boolean enabled = true;

    /**
     * 判断是否配置了有效的代理
     */
    public boolean isNotConfigured() {
        return !enabled || url == null || url.isBlank();
    }

    /**
     * 获取代理类型
     */
    public ProxyType getProxyType() {
        if (isNotConfigured()) {
            return ProxyType.NONE;
        }
        String lowerUrl = url.toLowerCase();
        if (lowerUrl.startsWith("socks5://") || lowerUrl.startsWith("socks://")) {
            return ProxyType.SOCKS5;
        }
        return ProxyType.HTTP;
    }

    /**
     * 解析代理主机
     */
    public String getHost() {
        return parseUri().getHost();
    }

    /**
     * 解析代理端口
     */
    public int getPort() {
        return parseUri().getPort();
    }

    /**
     * 解析用户名（如果有）
     */
    public String getUsername() {
        String userInfo = parseUri().getUserInfo();
        if (userInfo != null && userInfo.contains(":")) {
            return userInfo.split(":")[0];
        }
        return userInfo;
    }

    /**
     * 解析密码（如果有）
     */
    public String getPassword() {
        String userInfo = parseUri().getUserInfo();
        if (userInfo != null && userInfo.contains(":")) {
            return userInfo.split(":", 2)[1];
        }
        return null;
    }

    private URI parseUri() {
        if (isNotConfigured()) {
            throw new IllegalStateException("Proxy URL is not configured");
        }
        return URI.create(url);
    }

    public enum ProxyType {
        NONE, HTTP, SOCKS5
    }
}
