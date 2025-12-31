package top.qiyuey.book.config.provider;

import io.netty.channel.ChannelOption;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.transport.ProxyProvider;

/**
 * OpenAI WebClient 工厂
 * 支持 HTTP 和 SOCKS5 代理
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OpenAiRestClientFactory {

    private final OpenAiProxyProperties proxyProperties;

    /**
     * 创建带代理的 WebClient.Builder（用于流式调用）
     */
    public WebClient.Builder createWebClientBuilder() {
        WebClient.Builder builder = WebClient.builder();

        if (proxyProperties.isNotConfigured()) {
            log.debug("OpenAI proxy not configured, using direct connection");
            return builder;
        }

        HttpClient httpClient = createProxiedHttpClient();
        ReactorClientHttpConnector connector = new ReactorClientHttpConnector(httpClient);

        log.info("OpenAI proxy configured: type={}, host={}, port={}",
                proxyProperties.getProxyType(),
                proxyProperties.getHost(),
                proxyProperties.getPort());

        return builder.clientConnector(connector);
    }

    /**
     * 创建带代理的 Reactor Netty HttpClient
     */
    private HttpClient createProxiedHttpClient() {
        String host = proxyProperties.getHost();
        int port = proxyProperties.getPort();
        String username = proxyProperties.getUsername();
        String password = proxyProperties.getPassword();

        ProxyProvider.Proxy nettyProxyType = switch (proxyProperties.getProxyType()) {
            case SOCKS5 -> ProxyProvider.Proxy.SOCKS5;
            case HTTP -> ProxyProvider.Proxy.HTTP;
            default -> throw new IllegalStateException("Unsupported proxy type: " + proxyProperties.getProxyType());
        };

        return HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 30000)
                .proxy(proxy -> {
                    var spec = proxy.type(nettyProxyType)
                            .host(host)
                            .port(port);
                    if (username != null && password != null) {
                        spec.username(username).password(_ -> password);
                    }
                });
    }
}
