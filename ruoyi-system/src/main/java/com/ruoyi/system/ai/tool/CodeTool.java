package com.ruoyi.system.ai.tool;

import com.ruoyi.system.domain.EduResource;
import com.ruoyi.system.mapper.EduResourceMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 代码工具 — 代码存储/校验
 *
 * 已完成持久化改造：代码文本直接存入 edu_resource 表（type='code'），
 * 前端通过 highlight.js 高亮渲染。
 *
 * @author edu-agent
 */
@Component
public class CodeTool {

    private static final Logger log = LoggerFactory.getLogger(CodeTool.class);

    @Autowired
    private EduResourceMapper eduResourceMapper;

    /**
     * 保存代码示例到 edu_resource 表
     *
     * @param title    代码标题（如 "Python 实现二分查找"）
     * @param language 编程语言标记（如 "Python"、"Java"）
     * @param code     完整代码文本（含中文注释）
     * @param tags     标签（如 "算法,二分查找"）
     * @param userId   用户ID（AI 生成资源归属的用户）
     * @return 资源 ID
     */
    public Long saveCodeExample(String title, String language, String code, String tags, Long userId) {
        EduResource res = new EduResource();
        // 标题中附带语言标记，方便前端按语言筛选
        res.setTitle(language != null ? "[" + language + "] " + title : title);
        res.setContent(code);
        res.setType("code");
        res.setSource("internal");
        res.setTag(tags);
        res.setStatus(ResourceTool.STATUS_REVIEWING);
        res.setCreateBy("AI");
        res.setUserId(userId);           // AI 生成资源归属指定用户
        eduResourceMapper.insertResource(res);
        log.info("代码示例已保存: id={}, title={}, userId={}, lang={}, length={}",
                res.getId(), title, userId, language, code != null ? code.length() : 0);
        return res.getId();
    }
}
