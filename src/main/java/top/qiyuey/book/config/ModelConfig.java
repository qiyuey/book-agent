package top.qiyuey.book.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;

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
     * 默认模型ID
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

    /**
     * 根据模型ID获取模型信息
     */
    public ModelInfo getModelById(String modelId) {
        if (modelId == null || available == null) {
            return null;
        }
        return available.stream()
                .filter(m -> m.getId().equals(modelId))
                .findFirst()
                .orElse(null);
    }

    /**
     * 检查模型ID是否有效
     */
    public boolean isValidModel(String modelId) {
        return getModelById(modelId) != null;
    }
}
