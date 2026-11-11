package com.ruoyi.common.utils.file;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinioBucketPolicySecurityTest
{
    @Test
    void publicGeneratedMediaPolicyDoesNotIncludePrivateChatAttachments()
    {
        JSONObject policy = JSONObject.parseObject(
                MinioFileStorageServiceImpl.buildBucketPolicy("edu-resources", "ai-generated"));
        JSONArray statements = policy.getJSONArray("Statement");

        assertEquals("Allow", statements.getJSONObject(0).getString("Effect"));
        assertTrue(statements.getJSONObject(0).getJSONArray("Resource")
                .contains("arn:aws:s3:::edu-resources/ai-generated/*"));
        assertEquals(1, statements.size());
    }

    @Test
    void rejectsPublicPrefixesThatWouldExposePrivateChatAttachments()
    {
        assertThrows(IllegalArgumentException.class,
                () -> MinioFileStorageServiceImpl.buildBucketPolicy("edu-resources", "edu-chat"));
        assertThrows(IllegalArgumentException.class,
                () -> MinioFileStorageServiceImpl.buildBucketPolicy("edu-resources", "edu-chat/private"));
        assertThrows(IllegalArgumentException.class,
                () -> MinioFileStorageServiceImpl.buildBucketPolicy("edu-resources", "edu-chat/private/files"));
    }
}
