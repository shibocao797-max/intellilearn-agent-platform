package com.ruoyi.system.ai.tool;

import com.ruoyi.system.domain.EduKnowledgeChunk;
import com.ruoyi.system.mapper.EduKnowledgeChunkMapper;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Lightweight in-process vector retrieval for the education knowledge base.
 *
 * It keeps the MySQL knowledge table as the source of truth and lazily builds
 * an in-memory vector index. Any non-fatal failure should degrade to keyword
 * retrieval instead of breaking the main chat workflow.
 */
@Component
public class KnowledgeVectorSearchService
{
    private static final Logger log = LoggerFactory.getLogger(KnowledgeVectorSearchService.class);

    @Autowired
    private EduKnowledgeChunkMapper knowledgeChunkMapper;

    @Value("${edu.ai.rag.vector-enabled:true}")
    private boolean vectorEnabled;

    @Value("${edu.ai.rag.vector-candidate-limit:80}")
    private int vectorCandidateLimit;

    @Value("${edu.ai.rag.vector-min-score:0.18}")
    private double vectorMinScore;

    private volatile List<VectorEntry> index = List.of();
    private volatile boolean initialized = false;
    private volatile boolean unavailable = false;
    private EmbeddingModel embeddingModel;

    public List<SearchHit> search(String query, int topK)
    {
        try
        {
            if (!vectorEnabled || !hasText(query) || topK <= 0)
            {
                return List.of();
            }
            ensureIndex();
            if (unavailable || index.isEmpty())
            {
                return List.of();
            }

            float[] queryVector = embed(query);
            List<String> keywords = extractKeywords(query);
            List<SearchHit> hits = new ArrayList<>();
            for (VectorEntry entry : index)
            {
                double score = cosine(queryVector, entry.vector);
                double hybridScore = score * 0.78
                        + keywordScore(entry.searchText, keywords) * 0.16
                        + trustBoost(entry.chunk.getTrustLevel());
                if (score >= vectorMinScore)
                {
                    hits.add(new SearchHit(entry.chunk, hybridScore, "vector_hybrid"));
                }
            }
            return hits.stream()
                    .sorted(Comparator.comparingDouble(SearchHit::score).reversed())
                    .limit(topK)
                    .toList();
        }
        catch (Exception e)
        {
            log.warn("Vector search failed, fallback to keyword retrieval: {}", e.getMessage());
            return List.of();
        }
        catch (Throwable t)
        {
            if (isFatalJvmError(t))
            {
                throw t;
            }
            unavailable = true;
            index = List.of();
            log.warn("Vector search hit non-Exception throwable, fallback to keyword retrieval: {}",
                    summarizeThrowable(t));
            return List.of();
        }
    }

    private void ensureIndex()
    {
        if (initialized || unavailable)
        {
            return;
        }
        synchronized (this)
        {
            if (initialized || unavailable)
            {
                return;
            }
            try
            {
                embeddingModel = new AllMiniLmL6V2EmbeddingModel();
                EduKnowledgeChunk query = new EduKnowledgeChunk();
                query.setEnabled("0");
                List<EduKnowledgeChunk> chunks = knowledgeChunkMapper.selectKnowledgeChunkList(query);
                if (chunks.size() > vectorCandidateLimit)
                {
                    chunks = chunks.subList(0, vectorCandidateLimit);
                }

                List<VectorEntry> entries = new ArrayList<>();
                Set<Long> seen = new LinkedHashSet<>();
                for (EduKnowledgeChunk chunk : chunks)
                {
                    if (chunk.getId() == null || seen.contains(chunk.getId()) || !hasText(chunk.getChunkText()))
                    {
                        continue;
                    }
                    seen.add(chunk.getId());
                    String searchText = toEmbeddingText(chunk);
                    entries.add(new VectorEntry(chunk, embed(searchText), searchText));
                }
                index = List.copyOf(entries);
                initialized = true;
                log.info("Knowledge vector index initialized with {} chunks", index.size());
            }
            catch (Exception e)
            {
                unavailable = true;
                index = List.of();
                log.warn("Knowledge vector index initialization failed, fallback to keyword retrieval: {}",
                        e.getMessage());
            }
            catch (Throwable t)
            {
                if (isFatalJvmError(t))
                {
                    throw t;
                }
                unavailable = true;
                index = List.of();
                log.warn("Knowledge vector index hit non-Exception throwable, fallback to keyword retrieval: {}",
                        summarizeThrowable(t));
            }
        }
    }

    private float[] embed(String text)
    {
        Embedding embedding = embeddingModel.embed(text).content();
        float[] vector = embedding.vector();
        normalize(vector);
        return vector;
    }

    private String toEmbeddingText(EduKnowledgeChunk chunk)
    {
        return nullToEmpty(chunk.getTitle()) + "\n"
                + nullToEmpty(chunk.getSubject()) + "\n"
                + nullToEmpty(chunk.getTags()) + "\n"
                + nullToEmpty(chunk.getChunkText());
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
                "Python", "Java", "C", "SQL", "machine learning", "binary tree", "bubble sort", "pointer",
                "array", "linked list", "tree", "queue", "recursion", "sorting", "algorithm",
                "multithreading", "thread", "process", "database", "neural network", "regression",
                "classification", "supervised learning", "unsupervised learning"
        };
        for (String term : knownTerms)
        {
            if (text.contains(term))
            {
                keywords.add(term);
            }
        }
        String normalized = text.replaceAll("[，。！？；：,.!?;:\\[\\]{}()（）\"'`\\r\\n\\t]", " ");
        for (String token : normalized.split("\\s+"))
        {
            String value = token.trim();
            if (value.length() >= 2 && value.length() <= 24)
            {
                keywords.add(value);
            }
        }
        return new ArrayList<>(keywords);
    }

    private double keywordScore(String text, List<String> keywords)
    {
        if (!hasText(text) || keywords == null || keywords.isEmpty())
        {
            return 0.0;
        }
        String lower = text.toLowerCase();
        int hit = 0;
        for (String keyword : keywords)
        {
            if (hasText(keyword) && lower.contains(keyword.toLowerCase()))
            {
                hit++;
            }
        }
        return Math.min(1.0, hit / Math.max(3.0, keywords.size()));
    }

    private double trustBoost(String trustLevel)
    {
        if ("high".equalsIgnoreCase(trustLevel))
        {
            return 0.06;
        }
        if ("medium".equalsIgnoreCase(trustLevel))
        {
            return 0.03;
        }
        return 0.0;
    }

    private double cosine(float[] a, float[] b)
    {
        if (a == null || b == null || a.length == 0 || a.length != b.length)
        {
            return 0.0;
        }
        double dot = 0.0;
        for (int i = 0; i < a.length; i++)
        {
            dot += a[i] * b[i];
        }
        return dot;
    }

    private void normalize(float[] vector)
    {
        double norm = 0.0;
        for (float v : vector)
        {
            norm += v * v;
        }
        norm = Math.sqrt(norm);
        if (norm == 0.0)
        {
            return;
        }
        for (int i = 0; i < vector.length; i++)
        {
            vector[i] = (float) (vector[i] / norm);
        }
    }

    private boolean hasText(String value)
    {
        return value != null && !value.isBlank();
    }

    private String nullToEmpty(String value)
    {
        return value == null ? "" : value;
    }

    private boolean isFatalJvmError(Throwable t)
    {
        return t instanceof VirtualMachineError || t instanceof ThreadDeath;
    }

    private String summarizeThrowable(Throwable t)
    {
        String message = t.getMessage();
        if (message == null || message.isBlank())
        {
            return t.getClass().getName();
        }
        return t.getClass().getName() + ": " + message;
    }

    private record VectorEntry(EduKnowledgeChunk chunk, float[] vector, String searchText) {}

    public record SearchHit(EduKnowledgeChunk chunk, double score, String mode) {}
}
