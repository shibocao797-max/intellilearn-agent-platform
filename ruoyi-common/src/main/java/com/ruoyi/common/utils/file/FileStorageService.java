package com.ruoyi.common.utils.file;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文件存储服务抽象接口 —— 屏蔽底层存储差异（本地磁盘 / MinIO / OSS）
 *
 * @author edu-agent
 */
public interface FileStorageService
{
    /**
     * 上传文件（默认文件名策略）
     *
     * @param baseDir 存储子目录（如 upload、avatar）
     * @param file 上传的文件
     * @param allowedExtension 允许的文件扩展名
     * @return 文件访问路径（本地模式返回 /profile/...，MinIO 模式返回完整 HTTP URL）
     */
    String upload(String baseDir, MultipartFile file, String[] allowedExtension) throws Exception;

    /**
     * 上传文件（可指定文件名策略）
     *
     * @param baseDir 存储子目录
     * @param file 上传的文件
     * @param allowedExtension 允许的文件扩展名
     * @param useCustomNaming true 使用 UUID 文件名，false 使用原文件名+序列
     * @return 文件访问路径
     */
    String upload(String baseDir, MultipartFile file, String[] allowedExtension, boolean useCustomNaming) throws Exception;

    /**
     * 删除文件
     *
     * @param filePath 上传时返回的文件路径
     * @return 是否删除成功
     */
    boolean delete(String filePath);

    /**
     * 下载文件到 HTTP 响应流
     *
     * @param filePath 文件路径
     * @param response HTTP 响应对象
     */
    void download(String filePath, HttpServletResponse response) throws Exception;
}
