package com.ruoyi.system.ai.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * LangGraph4j 配置 — CheckpointSaver 和 Studio 设置
 *
 * @author edu-agent
 */
@Configuration
public class LangGraph4jConfig {

    private static final Logger log = LoggerFactory.getLogger(LangGraph4jConfig.class);

    @Value("${langgraph4j.checkpoint.type:memory}")
    private String checkpointType;

    @Value("${langgraph4j.studio.enabled:false}")
    private boolean studioEnabled;

    @Value("${langgraph4j.studio.port:9090}")
    private int studioPort;

    /**
     * 目前使用内存 CheckpointSaver（后期可切换为 MySQL）。
     * MemorySaver 适合开发调试，生产环境切换为 MySQLSaver。
     */
    @Bean
    public org.bsc.langgraph4j.checkpoint.MemorySaver checkpointSaver() {
        log.info("初始化 MemorySaver（开发模式），checkpoint.type={}", checkpointType);
        return new org.bsc.langgraph4j.checkpoint.MemorySaver();
    }
}
