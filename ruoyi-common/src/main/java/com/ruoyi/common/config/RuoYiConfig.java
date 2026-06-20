package com.ruoyi.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 读取项目相关配置
 * 
 * @author ruoyi
 */
@Component
@ConfigurationProperties(prefix = "ruoyi")
public class RuoYiConfig
{
    /** 项目名称 */
    private String name;

    /** 版本 */
    private String version;

    /** 版权年份 */
    private String copyrightYear;

    /** 上传路径 */
    private static String profile;

    /** 获取地址开关 */
    private static boolean addressEnabled;

    /** 验证码类型 */
    private static String captchaType;

    /** 文件存储类型 local / minio */
    private static String storageType;

    /** MinIO 端点 */
    private static String minioEndpoint;

    /** MinIO 访问密钥 */
    private static String minioAccessKey;

    /** MinIO 秘密密钥 */
    private static String minioSecretKey;

    /** MinIO 存储桶 */
    private static String minioBucket;

    public String getName()
    {
        return name;
    }

    public void setName(String name)
    {
        this.name = name;
    }

    public String getVersion()
    {
        return version;
    }

    public void setVersion(String version)
    {
        this.version = version;
    }

    public String getCopyrightYear()
    {
        return copyrightYear;
    }

    public void setCopyrightYear(String copyrightYear)
    {
        this.copyrightYear = copyrightYear;
    }

    public static String getProfile()
    {
        return profile;
    }

    public void setProfile(String profile)
    {
        RuoYiConfig.profile = profile;
    }

    public static boolean isAddressEnabled()
    {
        return addressEnabled;
    }

    public void setAddressEnabled(boolean addressEnabled)
    {
        RuoYiConfig.addressEnabled = addressEnabled;
    }

    public static String getCaptchaType() {
        return captchaType;
    }

    public void setCaptchaType(String captchaType) {
        RuoYiConfig.captchaType = captchaType;
    }

    public static String getStorageType() {
        return storageType;
    }

    @Value("${file-storage.type:local}")
    public void setStorageType(String storageType) {
        RuoYiConfig.storageType = storageType;
    }

    public static String getMinioEndpoint() {
        return minioEndpoint;
    }

    @Value("${file-storage.minio.endpoint:}")
    public void setMinioEndpoint(String minioEndpoint) {
        RuoYiConfig.minioEndpoint = minioEndpoint;
    }

    public static String getMinioAccessKey() {
        return minioAccessKey;
    }

    @Value("${file-storage.minio.access-key:}")
    public void setMinioAccessKey(String minioAccessKey) {
        RuoYiConfig.minioAccessKey = minioAccessKey;
    }

    public static String getMinioSecretKey() {
        return minioSecretKey;
    }

    @Value("${file-storage.minio.secret-key:}")
    public void setMinioSecretKey(String minioSecretKey) {
        RuoYiConfig.minioSecretKey = minioSecretKey;
    }

    public static String getMinioBucket() {
        return minioBucket;
    }

    @Value("${file-storage.minio.bucket:}")
    public void setMinioBucket(String minioBucket) {
        RuoYiConfig.minioBucket = minioBucket;
    }

    /**
     * 获取导入上传路径
     */
    public static String getImportPath()
    {
        return getProfile() + "/import";
    }

    /**
     * 获取头像上传路径
     */
    public static String getAvatarPath()
    {
        return getProfile() + "/avatar";
    }

    /**
     * 获取下载路径
     */
    public static String getDownloadPath()
    {
        return getProfile() + "/download/";
    }

    /**
     * 获取上传路径
     */
    public static String getUploadPath()
    {
        return getProfile() + "/upload";
    }
}
