package com.ruoyi.framework.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.ruoyi.common.utils.file.FileStorageService;
import com.ruoyi.common.utils.file.LocalFileStorageServiceImpl;

/**
 * 文件存储配置 —— 根据 file-storage.type 条件装配对应的存储实现
 * <p>
 * type=local（默认）：使用 LocalFileStorageServiceImpl，本地磁盘存储
 * type=minio：使用 MinioFileStorageServiceImpl，MinIO 对象存储
 * <p>
 * MinIO 检测从 @ConditionalOnClass 改为 Bean 方法内 Class.forName()，
 * 绕过 Spring Boot DevTools RestartClassLoader 下 OnClassCondition 类加载委托异常问题（SB 4.x）。
 *
 * @author edu-agent
 */
@Configuration
public class FileStorageConfig
{
    private static final Logger log = LoggerFactory.getLogger(FileStorageConfig.class);

    /**
     * MinIO 存储 —— 仅当 file-storage.type=minio 时装配
     * <p>
     * 在方法内用 Class.forName 而非 @ConditionalOnClass 检测 MinIO SDK 是否可用，
     * 因为 SB 4.x + DevTools RestartClassLoader 下 OnClassCondition 的类加载委托路径异常，
     * 会导致 io.minio.MinioClient 检测为"不存在"而跳过此 Bean。
     * Class.forName 走标准类加载委派链，不受该问题影响。
     */
    @Bean
    @ConditionalOnProperty(name = "file-storage.type", havingValue = "minio")
    public FileStorageService minioFileStorageService(
            @Value("${file-storage.minio.endpoint}") String endpoint,
            @Value("${file-storage.minio.access-key}") String accessKey,
            @Value("${file-storage.minio.secret-key}") String secretKey,
            @Value("${file-storage.minio.bucket}") String bucket,
            @Value("${file-storage.minio.public-read-prefix:ai-generated}") String publicReadPrefix)
    {
        try
        {
            // 运行时检测 MinIO SDK 是否在 classpath —— 走正常类加载委派链
            Class.forName("io.minio.MinioClient");
            // 全限定名引用 MinioFileStorageServiceImpl，避免直接 import 触发类加载
            return new com.ruoyi.common.utils.file.MinioFileStorageServiceImpl(endpoint, accessKey, secretKey, bucket, publicReadPrefix);
        }
        catch (ClassNotFoundException e)
        {
            // MinIO SDK 不在 classpath —— 降级为本地存储
            log.warn("========================================");
            log.warn("配置了 file-storage.type=minio，但 MinIO SDK 不在 classpath");
            log.warn("降级为本地磁盘存储模式（LocalFileStorage）");
            log.warn("如需使用 MinIO，请确保：");
            log.warn("  1. pom.xml 中已添加 io.minio:minio 依赖");
            log.warn("  2. IDE 中已刷新 Maven 依赖（Reload Maven Project）");
            log.warn("========================================");
            return new LocalFileStorageServiceImpl();
        }
    }

    /**
     * 本地存储 —— type=local 时装配（同时也是 type 属性缺失时的默认值）
     * <p>
     * matchIfMissing=true 确保不配置 file-storage.type 时默认使用本地存储。
     * 与 minioFileStorageService 的条件互斥，不会同时激活两个 Bean。
     */
    @Bean
    @ConditionalOnProperty(name = "file-storage.type", havingValue = "local", matchIfMissing = true)
    public FileStorageService localFileStorageService()
    {
        log.info("使用本地磁盘存储模式（LocalFileStorage）");
        return new LocalFileStorageServiceImpl();
    }
}
