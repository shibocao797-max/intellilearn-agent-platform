package com.ruoyi.common.utils.file;

import java.io.File;
import java.io.FileOutputStream;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;
import com.ruoyi.common.config.RuoYiConfig;
import com.ruoyi.common.constant.Constants;
import com.ruoyi.common.utils.uuid.IdUtils;

/**
 * 本地磁盘文件存储实现 —— 封装现有 FileUploadUtils / FileUtils 逻辑，行为与改造前完全一致
 *
 * @author edu-agent
 */
public class LocalFileStorageServiceImpl implements FileStorageService
{
    @Override
    public String upload(String baseDir, MultipartFile file, String[] allowedExtension) throws Exception
    {
        return FileUploadUtils.upload(baseDir, file, allowedExtension, false);
    }

    @Override
    public String upload(String baseDir, MultipartFile file, String[] allowedExtension, boolean useCustomNaming) throws Exception
    {
        return FileUploadUtils.upload(baseDir, file, allowedExtension, useCustomNaming);
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

        File rootDir = new File(RuoYiConfig.getProfile(), cleanBaseDir).getCanonicalFile();
        File targetFile = new File(rootDir, cleanFileName).getCanonicalFile();
        if (!targetFile.getPath().startsWith(rootDir.getPath()))
        {
            throw new IllegalArgumentException("非法文件路径: " + fileName);
        }
        File parent = targetFile.getParentFile();
        if (parent != null && !parent.exists())
        {
            parent.mkdirs();
        }
        try (FileOutputStream fos = new FileOutputStream(targetFile))
        {
            fos.write(content);
        }
        return Constants.RESOURCE_PREFIX + "/" + cleanBaseDir + "/" + cleanFileName.replace("\\", "/");
    }

    @Override
    public boolean delete(String filePath)
    {
        // 构造本地绝对路径：profile + 去掉 /profile 前缀的相对路径
        String absolutePath = RuoYiConfig.getProfile() + FileUtils.stripPrefix(filePath);
        return FileUtils.deleteFile(absolutePath);
    }

    @Override
    public void download(String filePath, HttpServletResponse response) throws Exception
    {
        // 通用下载：如果 filePath 以 /profile 开头，去掉前缀转为本地绝对路径
        String localPath;
        if (filePath.startsWith("/profile"))
        {
            localPath = RuoYiConfig.getProfile() + FileUtils.stripPrefix(filePath);
        }
        else
        {
            localPath = RuoYiConfig.getProfile() + File.separator + filePath;
        }
        response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
        FileUtils.setAttachmentResponseHeader(response, FileUtils.getName(filePath));
        FileUtils.writeBytes(localPath, response.getOutputStream());
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
