package com.ruoyi.system.ai.tool;

import com.ruoyi.system.domain.EduResource;
import com.ruoyi.system.mapper.EduResourceMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * 资源工具 — 资源存储/检索
 *
 * 已完成持久化改造：saveDocument() 写入 edu_resource 表，
 * searchResources() 调用已有 Mapper 查询。
 * uploadFile() 暂保持桩实现（MinIO 文件上传用于思维导图等大文件场景）。
 *
 * @author edu-agent
 */
@Component
public class ResourceTool {

    private static final Logger log = LoggerFactory.getLogger(ResourceTool.class);

    @Autowired
    private EduResourceMapper eduResourceMapper;

    /**
     * 保存生成的文档资源到 edu_resource 表
     *
     * @param title   资源标题
     * @param content Markdown 正文内容
     * @param tags    标签（逗号分隔）
     * @param userId  用户ID（AI 生成资源归属的用户）
     * @return 资源 ID（自增主键）
     */
    public Long saveDocument(String title, String content, String tags, Long userId) {
        EduResource res = new EduResource();
        res.setTitle(title);
        res.setContent(content);
        res.setType("article");         // 文档类型
        res.setSource("internal");      // AI 内部生成
        res.setTag(tags);
        res.setStatus("0");             // 发布状态
        res.setCreateBy("AI");
        res.setUserId(userId);          // AI 生成资源归属指定用户
        eduResourceMapper.insertResource(res);
        // insertResource 配置了 useGeneratedKeys，id 自动回填
        log.info("文档已保存: id={}, title={}, userId={}, length={}", res.getId(), title, userId,
                content != null ? content.length() : 0);
        return res.getId();
    }

    /**
     * 保存任意类型的资源到 edu_resource 表（通用方法）
     *
     * 各类 Agent 通过此方法统一落库，type 字段区分资源类型。
     *
     * @param title   资源标题
     * @param content 资源内容
     * @param type    资源类型: article / mindmap / code / video / image
     * @param tags    标签
     * @param userId  用户ID（AI 生成资源归属的用户）
     * @return 资源 ID
     */
    public Long saveResource(String title, String content, String type, String tags, Long userId) {
        EduResource res = new EduResource();
        res.setTitle(title);
        res.setContent(content);
        res.setType(type);
        res.setSource("internal");
        res.setTag(tags);
        res.setStatus("0");
        res.setCreateBy("AI");
        res.setUserId(userId);          // AI 生成资源归属指定用户
        eduResourceMapper.insertResource(res);
        log.info("资源已保存: id={}, title={}, type={}, userId={}", res.getId(), title, type, userId);
        return res.getId();
    }

    /**
     * 根据标签和难度检索已有资源（SQL 模糊匹配）
     *
     * @param tag 标签关键词
     * @return 资源列表
     */
    public List<EduResource> searchResources(String tag) {
        if (tag == null || tag.isBlank()) {
            return Collections.emptyList();
        }
        EduResource query = new EduResource();
        query.setTag(tag);
        query.setStatus("0");
        return eduResourceMapper.selectResourceList(query);
    }

    /**
     * 上传文件到 MinIO，返回访问 URL
     *
     * 当前返回桩 URL（MinIO FileStorageService 接口只支持 MultipartFile 入参，
     * 后续需要扩展 byte[] 直接上传的方法后再真实接入）。
     */
    public String uploadFile(String fileName, byte[] content, String contentType) {
        // TODO: 扩展 FileStorageService 接口支持 byte[] 直接上传
        log.info("上传文件: fileName={}, size={}, type={}",
                fileName, content != null ? content.length : 0, contentType);
        return "http://localhost:19000/edu-agent/" + fileName;
    }
}
