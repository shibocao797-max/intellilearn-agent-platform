package com.ruoyi.system.ai.tool;

import com.ruoyi.system.domain.EduResource;
import com.ruoyi.system.mapper.EduResourceMapper;
import net.sourceforge.plantuml.SourceStringReader;
import net.sourceforge.plantuml.security.SecurityUtils;
import net.sourceforge.plantuml.security.SecurityProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.regex.Pattern;

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
    private static final int MAX_SOURCE_LENGTH = 12000;
    private static final int MAX_NODE_COUNT = 200;
    private static final Pattern MINDMAP_NODE = Pattern.compile("\\*{1,4}\\s+[^\\r\\n]{1,160}");

    static {
        System.setProperty("PLANTUML_SECURITY_PROFILE", "SANDBOX");
    }

    @Autowired
    private EduResourceMapper eduResourceMapper;

    @Autowired
    private ResourceTool resourceTool;

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
        String extracted;
        try {
            if (SecurityUtils.getSecurityProfile() != SecurityProfile.SANDBOX) {
                throw new IllegalStateException("PlantUML sandbox security profile is not active");
            }
            extracted = validatePlantUml(extractPlantUml(plantUmlCode));
        } catch (RuntimeException e) {
            log.warn("拒绝渲染不安全或无效的 PlantUML 输入: title={}, userId={}, reason={}",
                    title, userId, e.getMessage());
            return saveResource(title, plantUmlCode, null, "text", userId);
        }

        // 2. 用 PlantUML 渲染 PNG 图片
        SourceStringReader reader = new SourceStringReader(extracted);
        ByteArrayOutputStream pngStream = new ByteArrayOutputStream();
        try {
            reader.outputImage(pngStream);
        } catch (Exception e) {
            log.error("PlantUML 渲染失败: title={}, userId={}", title, userId, e);
            // 降级：保存原始文本，前端将以文本文档模式展示
            return saveResource(title, plantUmlCode, null, "text", userId);
        }

        // 3. Base64 编码，前端可直接用 <img src="data:image/png;base64,..."> 展示
        String base64Png = "data:image/png;base64," +
                Base64.getEncoder().encodeToString(pngStream.toByteArray());
        String sourceUrl = "";
        try {
            sourceUrl = resourceTool.uploadFile("mindmap-" + userId + ".png", pngStream.toByteArray(), "image/png");
        } catch (Exception e) {
            log.warn("思维导图PNG上传失败，保留Base64展示: title={}, error={}", title, e.getMessage());
        }

        log.info("PlantUML 思维导图渲染成功: title={}, userId={}, size={}KB",
                title, userId, pngStream.size() / 1024);

        return saveResource(title, base64Png, sourceUrl, "png_base64", userId);
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

    static String validatePlantUml(String source) {
        if (source == null || source.length() > MAX_SOURCE_LENGTH) {
            throw new IllegalArgumentException("思维导图源码为空或超过长度限制");
        }

        String[] lines = source.strip().split("\\R");
        if (lines.length < 3 || !"@startmindmap".equals(lines[0].trim())
                || !"@endmindmap".equals(lines[lines.length - 1].trim())) {
            throw new IllegalArgumentException("思维导图格式无效");
        }

        int nodeCount = 0;
        int previousDepth = 0;
        for (int index = 1; index < lines.length - 1; index++) {
            String line = lines[index].trim();
            if (line.isEmpty()) {
                continue;
            }
            if (!MINDMAP_NODE.matcher(line).matches()) {
                throw new IllegalArgumentException("仅支持思维导图节点文本，不允许 PlantUML 指令");
            }

            int depth = 0;
            while (depth < line.length() && line.charAt(depth) == '*') {
                depth++;
            }
            if (++nodeCount > MAX_NODE_COUNT || (nodeCount == 1 && depth != 1)
                    || (nodeCount > 1 && (depth == 1 || depth > previousDepth + 1))) {
                throw new IllegalArgumentException("思维导图节点数量或层级无效");
            }
            previousDepth = depth;
        }
        if (nodeCount == 0) {
            throw new IllegalArgumentException("思维导图至少需要一个节点");
        }
        return String.join("\n", lines);
    }

    /**
     * 保存到 edu_resource 表
     * @param title       资源标题
     * @param content     资源内容（PNG Base64 或 PlantUML 源码）
     * @param contentType 内容类型标识：png_base64=Base64图片 text=纯文本
     * @param userId      用户ID
     */
    private Long saveResource(String title, String content, String sourceUrl, String contentType, Long userId) {
        EduResource res = new EduResource();
        res.setTitle(title);
        res.setContent(content);
        res.setType("mindmap");
        res.setSource("internal");
        if (sourceUrl != null && !sourceUrl.isBlank()) {
            res.setSourceUrl(sourceUrl);
            res.setCoverUrl(sourceUrl);
        }
        res.setTag(contentType);  // 复用 tag 字段标记内容类型，前端据此判断展示方式
        res.setStatus(ResourceTool.STATUS_REVIEWING);
        res.setCreateBy("AI");
        res.setUserId(userId);
        eduResourceMapper.insertResource(res);
        return res.getId();
    }
}
