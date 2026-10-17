package com.ruoyi.system.mapper;

import com.ruoyi.system.domain.EduKnowledgeChunk;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 教育知识库切片 数据层
 *
 * @author edu-agent
 */
public interface EduKnowledgeChunkMapper
{
    /**
     * 查询知识切片列表
     *
     * @param chunk 查询条件
     * @return 知识切片集合
     */
    public List<EduKnowledgeChunk> selectKnowledgeChunkList(EduKnowledgeChunk chunk);

    /**
     * 按关键词召回知识切片
     *
     * @param keywords 关键词
     * @param limit 召回数量
     * @return 知识切片集合
     */
    public List<EduKnowledgeChunk> searchKnowledgeChunks(@Param("keywords") List<String> keywords,
                                                         @Param("limit") Integer limit);

    /**
     * 查询启用的知识切片，用于本地向量相似度重排。
     *
     * @param limit 最大候选数量
     * @return 知识切片集合
     */
    public List<EduKnowledgeChunk> selectEnabledKnowledgeChunks(@Param("limit") Integer limit);

    /**
     * 新增知识切片
     *
     * @param chunk 知识切片
     * @return 结果
     */
    public int insertKnowledgeChunk(EduKnowledgeChunk chunk);
}
