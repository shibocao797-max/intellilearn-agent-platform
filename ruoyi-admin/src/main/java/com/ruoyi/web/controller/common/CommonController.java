package com.ruoyi.web.controller.common;

import java.util.ArrayList;
import java.util.List;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import com.ruoyi.common.annotation.RateLimiter;
import com.ruoyi.common.config.RuoYiConfig;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.enums.LimitType;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.common.utils.file.FileStorageService;
import com.ruoyi.common.utils.file.FileUploadUtils;
import com.ruoyi.common.utils.file.FileUtils;
import com.ruoyi.common.utils.file.MimeTypeUtils;
import com.ruoyi.framework.config.ServerConfig;

/**
 * 通用请求处理
 * 
 * @author ruoyi
 */
@RestController
@RequestMapping("/common")
public class CommonController
{
    private static final Logger log = LoggerFactory.getLogger(CommonController.class);
    private static final int MAX_BATCH_UPLOAD_FILES = 10;

    @Autowired
    private ServerConfig serverConfig;

    @Autowired
    private FileStorageService fileStorageService;

    private static final String FILE_DELIMITER = ",";

    /**
     * 通用下载请求
     * 
     * @param fileName 文件名称
     * @param delete 是否删除
     */
    @PreAuthorize("#p1 != true or @ss.hasPermi('system:attachment:list')")
    @GetMapping("/download")
    public void fileDownload(String fileName, Boolean delete, HttpServletResponse response, HttpServletRequest request)
    {
        try
        {
            if (!FileUtils.checkAllowDownload(fileName))
            {
                throw new Exception(StringUtils.format("文件名称({})非法，不允许下载。 ", fileName));
            }
            String realFileName = System.currentTimeMillis() + fileName.substring(fileName.indexOf("_") + 1);
            response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
            FileUtils.setAttachmentResponseHeader(response, realFileName);
            // 通过 FileStorageService 下载（本地 / MinIO 自动切换）
            fileStorageService.download(fileName, response);
            if (Boolean.TRUE.equals(delete))
            {
                fileStorageService.delete(fileName);
            }
        }
        catch (Exception e)
        {
            log.error("下载文件失败", e);
        }
    }

    /**
     * 通用上传请求（单个）
     */
    @RateLimiter(time = 60, count = 20, limitType = LimitType.IP)
    @PostMapping("/upload")
    public AjaxResult uploadFile(MultipartFile file) throws Exception
    {
        String validationError = validateUpload(file);
        if (validationError != null)
        {
            return AjaxResult.error(validationError);
        }
        try
        {
            // 通过 FileStorageService 上传（本地 / MinIO 自动切换）
            String fileName = fileStorageService.upload(RuoYiConfig.getUploadPath(), file, MimeTypeUtils.DEFAULT_ALLOWED_EXTENSION);
            // MinIO 返回完整 HTTP URL，本地返回 /profile/... 相对路径
            String url = fileName.startsWith("http") ? fileName : serverConfig.getUrl() + fileName;
            AjaxResult ajax = AjaxResult.success();
            ajax.put("url", url);
            ajax.put("fileName", fileName);
            ajax.put("newFileName", FileUtils.getName(fileName));
            ajax.put("originalFilename", file.getOriginalFilename());
            return ajax;
        }
        catch (Exception e)
        {
            return AjaxResult.error(e.getMessage());
        }
    }

    /**
     * 通用上传请求（多个）
     */
    @RateLimiter(time = 60, count = 5, limitType = LimitType.IP)
    @PostMapping("/uploads")
    public AjaxResult uploadFiles(List<MultipartFile> files) throws Exception
    {
        if (files == null || files.isEmpty())
        {
            return AjaxResult.error("请选择要上传的文件");
        }
        if (files.size() > MAX_BATCH_UPLOAD_FILES)
        {
            return AjaxResult.error("批量上传最多支持" + MAX_BATCH_UPLOAD_FILES + "个文件");
        }
        for (MultipartFile file : files)
        {
            String validationError = validateUpload(file);
            if (validationError != null)
            {
                return AjaxResult.error(validationError);
            }
        }

        List<String> fileNames = new ArrayList<String>();
        try
        {
            // 上传文件路径
            String filePath = RuoYiConfig.getUploadPath();
            List<String> urls = new ArrayList<String>();
            List<String> newFileNames = new ArrayList<String>();
            List<String> originalFilenames = new ArrayList<String>();
            for (MultipartFile file : files)
            {
                // 通过 FileStorageService 上传
                String fileName = fileStorageService.upload(filePath, file, MimeTypeUtils.DEFAULT_ALLOWED_EXTENSION);
                // MinIO 返回完整 HTTP URL，本地返回 /profile/... 相对路径
                String url = fileName.startsWith("http") ? fileName : serverConfig.getUrl() + fileName;
                urls.add(url);
                fileNames.add(fileName);
                newFileNames.add(FileUtils.getName(fileName));
                originalFilenames.add(file.getOriginalFilename());
            }
            AjaxResult ajax = AjaxResult.success();
            ajax.put("urls", StringUtils.join(urls, FILE_DELIMITER));
            ajax.put("fileNames", StringUtils.join(fileNames, FILE_DELIMITER));
            ajax.put("newFileNames", StringUtils.join(newFileNames, FILE_DELIMITER));
            ajax.put("originalFilenames", StringUtils.join(originalFilenames, FILE_DELIMITER));
            return ajax;
        }
        catch (Exception e)
        {
            cleanupUploadedFiles(fileNames);
            return AjaxResult.error(e.getMessage());
        }
    }

    private String validateUpload(MultipartFile file)
    {
        if (file == null || file.isEmpty())
        {
            return "上传文件不能为空";
        }
        if (file.getSize() > FileUploadUtils.DEFAULT_MAX_SIZE)
        {
            long maxSizeMb = FileUploadUtils.DEFAULT_MAX_SIZE / 1024 / 1024;
            return StringUtils.format("单个文件不能超过 {} MB", maxSizeMb);
        }
        return null;
    }

    private void cleanupUploadedFiles(List<String> fileNames)
    {
        for (String fileName : fileNames)
        {
            try
            {
                if (!fileStorageService.delete(fileName))
                {
                    log.warn("Failed to clean up a file from an incomplete batch upload");
                }
            }
            catch (Exception cleanupError)
            {
                log.warn("Failed to clean up a file from an incomplete batch upload", cleanupError);
            }
        }
    }

    /**
     * 本地资源通用下载
     */
    @GetMapping("/download/resource")
    public void resourceDownload(String resource, HttpServletRequest request, HttpServletResponse response)
            throws Exception
    {
        try
        {
            if (!FileUtils.checkAllowDownload(resource))
            {
                throw new Exception(StringUtils.format("资源文件({})非法，不允许下载。 ", resource));
            }
            // 下载名称
            String downloadName = StringUtils.substringAfterLast(resource, "/");
            response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
            FileUtils.setAttachmentResponseHeader(response, downloadName);
            // 通过 FileStorageService 下载（本地 / MinIO 自动切换）
            fileStorageService.download(resource, response);
        }
        catch (Exception e)
        {
            log.error("下载文件失败", e);
        }
    }

    /**
     * 通用文件删除请求
     */
    @PreAuthorize("@ss.hasPermi('system:attachment:list')")
    @DeleteMapping("/file")
    public AjaxResult fileDelete(String fileName)
    {
        try
        {
            if (!FileUtils.checkAllowDownload(fileName))
            {
                return AjaxResult.error(StringUtils.format("文件名称({})非法，不允许删除。", fileName));
            }
            boolean deleted = fileStorageService.delete(fileName);
            if (deleted)
            {
                return AjaxResult.success();
            }
            return AjaxResult.error("删除文件失败");
        }
        catch (Exception e)
        {
            log.error("删除文件失败", e);
            return AjaxResult.error(e.getMessage());
        }
    }
}
