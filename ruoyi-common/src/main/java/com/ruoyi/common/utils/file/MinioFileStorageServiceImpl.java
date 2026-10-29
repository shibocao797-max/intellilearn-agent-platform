package com.ruoyi.common.utils.file;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;
import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.common.utils.uuid.IdUtils;
import com.ruoyi.common.utils.uuid.Seq;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.SetBucketPolicyArgs;

/**
 * MinIO 对象存储实现 —— 文件存储到 MinIO，返回可直接访问的 HTTP URL
 *
 * @author edu-agent
 */
public class MinioFileStorageServiceImpl implements FileStorageService
{
    private static final Logger log = LoggerFactory.getLogger(MinioFileStorageServiceImpl.class);

    /** MinIO 客户端 */
    private final MinioClient minioClient;

    /** 存储桶名称 */
    private final String bucket;

    /** MinIO 服务端点（用于构造返回 URL） */
    private final String endpoint;

    /** 允许匿名读取的对象前缀，仅用于兼容 AI 生成媒体的浏览器预览 */
    private final String publicReadPrefix;

    /**
     * 构造 MinIO 存储服务，初始化客户端并确保存储桶存在
     */
    public MinioFileStorageServiceImpl(String endpoint, String accessKey, String secretKey, String bucket,
                                       String publicReadPrefix)
    {
        this.endpoint = endpoint;
        this.bucket = bucket;
        this.publicReadPrefix = normalizePath(publicReadPrefix, "ai-generated");
        this.minioClient = MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
        // 启动时检查并创建存储桶
        ensureBucket();
    }

    /**
     * 确保存储桶存在，不存在则创建
     */
    private void ensureBucket()
    {
        try
        {
            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists)
            {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                log.info("MinIO 存储桶 [{}] 创建成功", bucket);
            }
            else
            {
                log.info("MinIO 存储桶 [{}] 已存在", bucket);
            }
            // 仅开放 AI 生成媒体前缀，聊天附件不得通过匿名 URL 读取。
            // 其余对象默认保持私有，避免把整个用户资源桶暴露到公网。
            String policy = "{\"Version\":\"2012-10-17\",\"Statement\":[{\"Effect\":\"Allow\",\"Principal\":{\"AWS\":[\"*\"]},\"Action\":[\"s3:GetObject\"],\"Resource\":[\"arn:aws:s3:::" + bucket + "/" + publicReadPrefix + "/*\"]}]}";
            minioClient.setBucketPolicy(
                SetBucketPolicyArgs.builder().bucket(bucket).config(policy).build());
        }
        catch (Exception e)
        {
            log.error("MinIO 存储桶初始化失败: {}", e.getMessage(), e);
        }
    }

    @Override
    public String upload(String baseDir, MultipartFile file, String[] allowedExtension) throws Exception
    {
        return upload(baseDir, file, allowedExtension, false);
    }

    @Override
    public String upload(String baseDir, MultipartFile file, String[] allowedExtension, boolean useCustomNaming) throws Exception
    {
        // 校验文件扩展名（复用现有校验逻辑）
        FileUploadUtils.assertAllowed(file, allowedExtension);

        // 生成文件名（复用现有命名策略）
        String fileName = useCustomNaming ? uuidFilename(file) : extractFilename(file);

        // 剥离本地 profile 前缀（如 D:/ruoyi/uploadPath），得到干净的 MinIO 对象前缀
        // 本地 baseDir 为 D:/ruoyi/uploadPath/avatar，MinIO 只需要 avatar
        String profile = com.ruoyi.common.config.RuoYiConfig.getProfile();
        String cleanBaseDir = baseDir;
        if (profile != null && !profile.isEmpty() && baseDir.startsWith(profile))
        {
            cleanBaseDir = baseDir.substring(profile.length());
            // 去掉开头的 / 或 \
            while (cleanBaseDir.startsWith("/") || cleanBaseDir.startsWith("\\"))
            {
                cleanBaseDir = cleanBaseDir.substring(1);
            }
        }

        // MinIO 中的对象路径：cleanBaseDir/日期/文件名
        String objectName = cleanBaseDir + "/" + fileName;

        // 上传到 MinIO
        minioClient.putObject(PutObjectArgs.builder()
                .bucket(bucket)
                .object(objectName)
                .contentType(Objects.requireNonNullElse(file.getContentType(), MediaType.APPLICATION_OCTET_STREAM_VALUE))
                .stream(file.getInputStream(), file.getSize(), -1)
                .build());

        // 返回完整 HTTP URL
        return endpoint + "/" + bucket + "/" + objectName;
    }

    @Override
    public String uploadBytes(String baseDir, String fileName, byte[] content, String contentType) throws Exception
    {
        if (content == null || content.length == 0)
        {
            throw new IllegalArgumentException("上传内容不能为空");
        }
        String cleanBaseDir = normalizePath(baseDir, "ai-generated");
        String cleanFileName = normalizePath(fileName, IdUtils.fastSimpleUUID() + extensionOf(contentType));
        String objectName = cleanBaseDir + "/" + cleanFileName;

        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(content))
        {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectName)
                    .contentType(Objects.requireNonNullElse(contentType, MediaType.APPLICATION_OCTET_STREAM_VALUE))
                    .stream(inputStream, content.length, -1)
                    .build());
        }
        return endpoint + "/" + bucket + "/" + objectName;
    }

    @Override
    public boolean delete(String filePath)
    {
        try
        {
            String objectName = parseObjectName(filePath);
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectName)
                    .build());
            return true;
        }
        catch (Exception e)
        {
            log.error("MinIO 删除文件失败: {}", e.getMessage(), e);
            return false;
        }
    }

    @Override
    public void download(String filePath, HttpServletResponse response) throws Exception
    {
        String objectName = parseObjectName(filePath);
        try (InputStream stream = minioClient.getObject(GetObjectArgs.builder()
                .bucket(bucket)
                .object(objectName)
                .build()))
        {
            response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
            FileUtils.setAttachmentResponseHeader(response, FileUtils.getName(filePath));
            byte[] buf = new byte[8192];
            int bytesRead;
            while ((bytesRead = stream.read(buf)) != -1)
            {
                response.getOutputStream().write(buf, 0, bytesRead);
            }
        }
    }

    /**
     * 从完整 MinIO URL 中解析对象路径
     * URL 格式：http://endpoint/bucket/baseDir/date/filename.jpg
     * → 解析出：baseDir/date/filename.jpg
     */
    private String parseObjectName(String filePath)
    {
        // 去除 endpoint 前缀（如果有）
        String path = filePath;
        if (path.startsWith("http"))
        {
            // 找到 /bucket/ 之后的部分
            String bucketPrefix = "/" + bucket + "/";
            int idx = path.indexOf(bucketPrefix);
            if (idx >= 0)
            {
                path = path.substring(idx + bucketPrefix.length());
            }
            else
            {
                // fallback：尝试从 URL 路径中提取
                try
                {
                    java.net.URL url = new java.net.URL(path);
                    path = url.getPath();
                    if (path.startsWith("/" + bucket + "/"))
                    {
                        path = path.substring(bucket.length() + 2);
                    }
                    else if (path.startsWith("/"))
                    {
                        path = path.substring(1);
                    }
                }
                catch (Exception e)
                {
                    log.warn("无法解析 MinIO URL: {}", filePath);
                }
            }
        }
        else if (path.startsWith("/"))
        {
            path = path.substring(1);
        }
        // URL 解码（处理中文等特殊字符）
        return URLDecoder.decode(path, StandardCharsets.UTF_8);
    }

    /**
     * 生成文件名：日期目录 + 原文件名 + 序列 + 后缀（复用 FileUploadUtils 逻辑）
     */
    private String extractFilename(MultipartFile file)
    {
        String extension = FileUploadUtils.getExtension(file);
        String baseName = org.apache.commons.io.FilenameUtils.getBaseName(file.getOriginalFilename());
        return DateUtils.datePath() + "/" + baseName + "_" + Seq.getId(Seq.uploadSeqType) + "." + extension;
    }

    /**
     * 生成 UUID 文件名（复用 FileUploadUtils 逻辑）
     */
    private String uuidFilename(MultipartFile file)
    {
        String extension = FileUploadUtils.getExtension(file);
        return DateUtils.datePath() + "/" + IdUtils.fastSimpleUUID() + "." + extension;
    }

    private String normalizePath(String value, String fallback)
    {
        String path = value == null || value.isBlank() ? fallback : value.trim();
        path = path.replace("\\", "/");
        while (path.startsWith("/"))
        {
            path = path.substring(1);
        }
        while (path.endsWith("/"))
        {
            path = path.substring(0, path.length() - 1);
        }
        if (path.contains(".."))
        {
            throw new IllegalArgumentException("非法文件路径: " + value);
        }
        return path.isBlank() ? fallback : path;
    }

    private String extensionOf(String contentType)
    {
        String extension = MimeTypeUtils.getExtension(contentType);
        return extension == null || extension.isBlank() ? ".bin" : "." + extension;
    }
}
