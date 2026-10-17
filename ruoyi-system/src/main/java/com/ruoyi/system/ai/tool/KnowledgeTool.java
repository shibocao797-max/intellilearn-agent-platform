package com.ruoyi.system.ai.tool;

import com.ruoyi.system.domain.EduKnowledgeChunk;
import com.ruoyi.system.mapper.EduKnowledgeChunkMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 知识库检索工具，为生成与审核 Agent 提供可信依据片段。
 *
 * 优先使用 LangChain4j 本地 embedding 向量检索；向量模型不可用时自动降级为
 * MySQL 关键词召回，保证比赛现场稳定性。
 */
@Component
public class KnowledgeTool
{
    private static final Logger log = LoggerFactory.getLogger(KnowledgeTool.class);

    private static final int DEFAULT_TOP_K = 5;
    private static final int MAX_TOP_K = 8;
    private static final int MAX_SNIPPET_LENGTH = 360;

    @Autowired
    private EduKnowledgeChunkMapper knowledgeChunkMapper;

    @Autowired(required = false)
    private KnowledgeVectorSearchService vectorSearchService;

    /**
     * 检索知识片段并格式化为可直接注入 Prompt 的依据块。
     *
     * @param query 查询文本
     * @param topK 召回数量
     * @return Markdown 依据块
     */
    public String retrieveForPrompt(String query, int topK)
    {
        List<KnowledgeVectorSearchService.SearchHit> hits = searchHits(query, topK);
        if (hits.isEmpty())
        {
            return "知识库暂无命中片段。生成内容必须标注“知识库未命中，需人工核验关键事实”。";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("以下为知识库召回的可信片段，生成和审核时优先依据这些内容：\n");
        int index = 1;
        for (KnowledgeVectorSearchService.SearchHit hit : hits)
        {
            EduKnowledgeChunk chunk = hit.chunk();
            sb.append("[").append(index++).append("] ")
                    .append(nullToEmpty(chunk.getTitle()))
                    .append(" | 来源: ").append(nullToEmpty(chunk.getSourceName()))
                    .append(" | 类型: ").append(nullToEmpty(chunk.getSourceType()))
                    .append(" | 可信度: ").append(nullToEmpty(chunk.getTrustLevel()))
                    .append(" | 检索: ").append(hit.mode())
                    .append(" | 相似度: ").append(round4(hit.score()))
                    .append("\n");
            sb.append(trim(chunk.getChunkText(), MAX_SNIPPET_LENGTH)).append("\n");
            if (hasText(chunk.getSourceUrl()))
            {
                sb.append("链接: ").append(chunk.getSourceUrl()).append("\n");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    /**
     * 返回结构化证据列表，便于资源和审核结果记录“用了哪些依据”。
     */
    public List<java.util.Map<String, Object>> retrieveEvidence(String query, int topK)
    {
        List<KnowledgeVectorSearchService.SearchHit> hits = searchHits(query, topK);
        List<java.util.Map<String, Object>> evidence = new ArrayList<>();
        for (KnowledgeVectorSearchService.SearchHit hit : hits)
        {
            EduKnowledgeChunk chunk = hit.chunk();
            java.util.Map<String, Object> item = new java.util.LinkedHashMap<>();
            item.put("chunkId", chunk.getId());
            item.put("title", nullToEmpty(chunk.getTitle()));
            item.put("sourceName", nullToEmpty(chunk.getSourceName()));
            item.put("sourceType", nullToEmpty(chunk.getSourceType()));
            item.put("trustLevel", nullToEmpty(chunk.getTrustLevel()));
            item.put("sourceUrl", nullToEmpty(chunk.getSourceUrl()));
            item.put("snippet", trim(chunk.getChunkText(), MAX_SNIPPET_LENGTH));
            item.put("retrievalMode", hit.mode());
            item.put("similarityScore", round4(hit.score()));
            evidence.add(item);
        }
        return evidence;
    }

    /**
     * 检索知识片段。
     *
     * @param query 查询文本
     * @param topK 召回数量
     * @return 知识片段
     */
    public List<EduKnowledgeChunk> search(String query, int topK)
    {
        return searchHits(query, topK).stream()
                .map(KnowledgeVectorSearchService.SearchHit::chunk)
                .toList();
    }

    private List<KnowledgeVectorSearchService.SearchHit> searchHits(String query, int topK)
    {
        if (!hasText(query))
        {
            return List.of();
        }
        int limit = Math.max(1, Math.min(MAX_TOP_K, topK <= 0 ? DEFAULT_TOP_K : topK));
        if (vectorSearchService != null)
        {
            List<KnowledgeVectorSearchService.SearchHit> vectorHits = vectorSearchService.search(query, limit);
            if (!vectorHits.isEmpty())
            {
                return vectorHits;
            }
        }
        return keywordSearch(query, limit);
    }

    private List<KnowledgeVectorSearchService.SearchHit> keywordSearch(String query, int limit)
    {
        List<String> keywords = extractKeywords(query);
        if (keywords.isEmpty())
        {
            return List.of();
        }
        try
        {
            List<EduKnowledgeChunk> chunks = knowledgeChunkMapper.searchKnowledgeChunks(keywords, limit);
            List<KnowledgeVectorSearchService.SearchHit> hits = new ArrayList<>();
            for (EduKnowledgeChunk chunk : chunks)
            {
                hits.add(new KnowledgeVectorSearchService.SearchHit(chunk, 0.12, "keyword_fallback"));
            }
            return hits;
        }
        catch (Exception e)
        {
            log.warn("关键词知识库检索失败，降级为空依据: query={}, error={}", trim(query, 120), e.getMessage());
            return List.of();
        }
    }

    private List<String> extractKeywords(String query)
    {
        if (!hasText(query))
        {
            return List.of();
        }
        Set<String> keywords = new LinkedHashSet<>();
        String text = query.trim();

        String[] knownTerms = {
                "Python", "Java", "C语言", "SQL", "机器学习", "二叉树", "冒泡排序", "指针",
                "数组", "链表", "栈", "队列", "递归", "排序", "算法", "多线程", "线程", "进程",
                "数据库", "神经网络", "回归", "分类", "监督学习", "无监督学习"
        };
        for (String term : knownTerms)
        {
            if (text.contains(term))
            {
                keywords.add(term);
            }
        }

        String normalized = text.replaceAll("[，。！？；：、,.!?;:\\[\\]{}()（）\"'`\\r\\n\\t]", " ");
        for (String token : normalized.split("\\s+"))
        {
            String value = token.trim();
            if (value.length() >= 2 && value.length() <= 24)
            {
                keywords.add(value);
            }
        }

        if (keywords.isEmpty() && text.length() <= 24)
        {
            keywords.add(text);
        }
        return new ArrayList<>(keywords);
    }

    private double round4(double value)
    {
        return Math.round(value * 10000.0) / 10000.0;
    }

    private boolean hasText(String value)
    {
        return value != null && !value.isBlank();
    }

    private String nullToEmpty(String value)
    {
        return value == null ? "" : value;
    }

    private String trim(String text, int max)
    {
        if (text == null)
        {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max) + "...";
    }
}
