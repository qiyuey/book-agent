package top.qiyuey.book.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * 模型配置类
 * 管理可用的 LLM 模型列表
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "app.models")
public class ModelConfig {

    /**
     * 可用模型列表
     */
    private List<ModelInfo> available;

    /**
     * 默认模型 ID
     */
    private String defaultModel;

    @Data
    public static class ModelInfo {
        /**
         * 模型ID（用于API调用）
         */
        private String id;

        /**
         * 模型显示名称
         */
        private String name;

        /**
         * 模型描述
         */
        private String description;
    }

}
