package com.ruoyi.common.utils.file;

import java.io.File;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;
import com.ruoyi.common.config.RuoYiConfig;

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
}
