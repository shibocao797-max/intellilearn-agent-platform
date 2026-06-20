package com.ruoyi.system.ai.tool;

import com.ruoyi.system.domain.EduResource;
import com.ruoyi.system.mapper.EduResourceMapper;
import net.sourceforge.plantuml.SourceStringReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.util.Base64;

/**
 * 思维导图工具 — PlantUML 服务端渲染为 PNG 图片
 *
 * 改造前：LLM 输出 Markmap 格式 Markdown → 前端 CDN 加载 markmap.js 浏览器渲染
 * 改造后：LLM 输出 PlantUML @startmindmap 语法 → 服务端 PlantUML 渲染 PNG → 前端 img 标签展示
 *
 * @author edu-agent
 */
@Component
public class MindmapTool {

    private static final Logger log = LoggerFactory.getLogger(MindmapTool.class);

    @Autowired
    private EduResourceMapper eduResourceMapper;

    /**
     * 将 LLM 生成的 PlantUML 源码渲染为 PNG 并保存到 edu_resource 表
     *
     * @param title        思维导图标题（通常是知识点名称）
     * @param plantUmlCode LLM 生成的 PlantUML 源码（需包含 @startmindmap...@endmindmap）
     * @param userId       用户ID（AI 生成资源归属的用户）
     * @return 资源 ID
     */
    public Long renderAndSave(String title, String plantUmlCode, Long userId) {
        // 1. 从 LLM 输出中提取 @startmindmap...@endmindmap 代码块，容错处理多余文字
        String extracted = extractPlantUml(plantUmlCode);

        // 2. 用 PlantUML 渲染 PNG 图片
        SourceStringReader reader = new SourceStringReader(extracted);
        ByteArrayOutputStream pngStream = new ByteArrayOutputStream();
        try {
            reader.outputImage(pngStream);
        } catch (Exception e) {
            log.error("PlantUML 渲染失败: title={}, userId={}", title, userId, e);
            // 降级：保存原始文本，前端将以文本文档模式展示
            return saveResource(title, plantUmlCode, "text", userId);
        }

        // 3. Base64 编码，前端可直接用 <img src="data:image/png;base64,..."> 展示
        String base64Png = "data:image/png;base64," +
                Base64.getEncoder().encodeToString(pngStream.toByteArray());

        log.info("PlantUML 思维导图渲染成功: title={}, userId={}, size={}KB",
                title, userId, pngStream.size() / 1024);

        return saveResource(title, base64Png, "png_base64", userId);
    }

    /**
     * 从 LLM 输出中提取 @startmindmap...@endmindmap 代码块
     * LLM 可能在前后输出额外说明文字，需要容错提取
     */
    private String extractPlantUml(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        int start = raw.indexOf("@startmindmap");
        int end = raw.indexOf("@endmindmap");
        if (start >= 0 && end > start) {
            return raw.substring(start, end + "@endmindmap".length());
        }
        // 如果没找到标记，可能是格式异常，尝试用原始文本渲染
        log.warn("LLM 输出中未找到 @startmindmap/@endmindmap 标记，使用原始文本");
        return raw;
    }

    /**
     * 保存到 edu_resource 表
     * @param title       资源标题
     * @param content     资源内容（PNG Base64 或 PlantUML 源码）
     * @param contentType 内容类型标识：png_base64=Base64图片 text=纯文本
     * @param userId      用户ID
     */
    private Long saveResource(String title, String content, String contentType, Long userId) {
        EduResource res = new EduResource();
        res.setTitle(title);
        res.setContent(content);
        res.setType("mindmap");
        res.setSource("internal");
        res.setTag(contentType);  // 复用 tag 字段标记内容类型，前端据此判断展示方式
        res.setStatus("0");
        res.setCreateBy("AI");
        res.setUserId(userId);
        eduResourceMapper.insertResource(res);
        return res.getId();
    }
}
