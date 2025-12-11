package top.qiyuey.book.config;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Provides DashScopeApi bean for injection.
 * BookAgentFactory requires DashScopeApi; without this bean, Spring cannot autowire it.
 */
@Configuration
public class DashScopeConfig {

    /**
     * Reads API key from Spring configuration or environment.
     * Priority: spring.ai.dashscope.api-key -> env AI_DASHSCOPE_API_KEY -> env DASHSCOPE_API_KEY
     */
    @Bean
    public DashScopeApi dashScopeApi(
            @Value("${spring.ai.dashscope.api-key:#{null}}") String springConfigApiKey,
            @Value("${AI_DASHSCOPE_API_KEY:#{null}}") String aiEnvApiKey,
            @Value("${DASHSCOPE_API_KEY:#{null}}") String dashscopeEnvApiKey) {

        String apiKey = springConfigApiKey != null ? springConfigApiKey
                : (aiEnvApiKey != null ? aiEnvApiKey : dashscopeEnvApiKey);

        if (apiKey == null || apiKey.isBlank()) {
            // Build without key will fail later at runtime; fail fast with a clear message.
            throw new IllegalStateException("DashScope API key is not configured. Please set 'spring.ai.dashscope.api-key' or environment variable AI_DASHSCOPE_API_KEY/DASHSCOPE_API_KEY.");
        }

        return DashScopeApi.builder()
                .apiKey(apiKey)
                .build();
    }
}
