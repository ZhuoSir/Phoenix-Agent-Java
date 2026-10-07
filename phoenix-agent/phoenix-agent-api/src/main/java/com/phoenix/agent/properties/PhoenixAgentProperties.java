package com.phoenix.agent.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties("spring.ai.phoenix")
public class PhoenixAgentProperties {
    private String modelPath;
    // v1.7.0 T-02：删死配置 skillPath（全仓零引用，BL-11）
    private Embedding embedding;
    private Skill skill = new Skill();

    @Data
    public static class Embedding {
        private String baseUrl;
        private String apiKey;
        private String model;
        private Integer dimensions = 512;
    }

    /**
     * 技能相关配置（R-05 显式执行上限，防单轮注入 token 爆炸）
     */
    @Data
    public static class Skill {
        /** 单轮显式勾选技能数量上限 */
        private Integer maxExplicit = 3;
    }
}


