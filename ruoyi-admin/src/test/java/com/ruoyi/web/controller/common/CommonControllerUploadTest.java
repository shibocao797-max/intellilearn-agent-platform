package com.ruoyi.web.controller.common;

import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.annotation.RateLimiter;
import com.ruoyi.common.enums.LimitType;
import com.ruoyi.common.utils.file.FileStorageService;
import com.ruoyi.common.utils.file.FileUploadUtils;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommonControllerUploadTest
{
    @Test
    void protectsDestructiveFileOperationsWithAttachmentPermission() throws Exception
    {
        Method download = CommonController.class.getMethod(
                "fileDownload", String.class, Boolean.class,
                jakarta.servlet.http.HttpServletResponse.class,
                jakarta.servlet.http.HttpServletRequest.class);
        Method delete = CommonController.class.getMethod("fileDelete", String.class);

        assertEquals("#p1 != true or @ss.hasPermi('system:attachment:list')",
                download.getAnnotation(PreAuthorize.class).value());
        assertEquals("@ss.hasPermi('system:attachment:list')",
                delete.getAnnotation(PreAuthorize.class).value());
    }

    @Test
    void limitsCommonUploadEndpointsByClientIp() throws Exception
    {
        Method singleUpload = CommonController.class.getMethod("uploadFile", MultipartFile.class);
        Method batchUpload = CommonController.class.getMethod("uploadFiles", List.class);
        RateLimiter singleLimit = singleUpload.getAnnotation(RateLimiter.class);
        RateLimiter batchLimit = batchUpload.getAnnotation(RateLimiter.class);

        assertNotNull(singleLimit);
        assertNotNull(batchLimit);
        assertEquals(LimitType.IP, singleLimit.limitType());
        assertEquals(20, singleLimit.count());
        assertEquals(LimitType.IP, batchLimit.limitType());
        assertEquals(5, batchLimit.count());
    }

    @Test
    void rejectsAnInvalidBatchBeforeWritingAnyFiles() throws Exception
    {
        RecordingFileStorageService storage = new RecordingFileStorageService(0);
        CommonController controller = controllerWith(storage);

        AjaxResult result = controller.uploadFiles(List.of(
                multipartFile("first.pdf", 1024),
                multipartFile("too-large.pdf", FileUploadUtils.DEFAULT_MAX_SIZE + 1)));

        assertTrue(result.isError());
        assertEquals(0, storage.uploadAttempts);
    }

    @Test
    void removesEarlierFilesWhenALaterBatchUploadFails() throws Exception
    {
        RecordingFileStorageService storage = new RecordingFileStorageService(2);
        CommonController controller = controllerWith(storage);

        AjaxResult result = controller.uploadFiles(List.of(
                multipartFile("first.pdf", 1024),
                multipartFile("second.pdf", 1024)));

        assertTrue(result.isError());
        assertEquals(2, storage.uploadAttempts);
        assertEquals(List.of("https://storage.test/upload/1"), storage.deletedPaths);
    }

    private CommonController controllerWith(FileStorageService storage) throws Exception
    {
        CommonController controller = new CommonController();
        Field field = CommonController.class.getDeclaredField("fileStorageService");
        field.setAccessible(true);
        field.set(controller, storage);
        return controller;
    }

    private MultipartFile multipartFile(String filename, long size)
    {
        return (MultipartFile) Proxy.newProxyInstance(
                MultipartFile.class.getClassLoader(),
                new Class<?>[] { MultipartFile.class },
                (proxy, method, args) -> switch (method.getName())
                {
                    case "getName" -> "file";
                    case "getOriginalFilename" -> filename;
                    case "getContentType" -> "application/pdf";
                    case "getSize" -> size;
                    case "isEmpty" -> size == 0;
                    case "getBytes" -> new byte[0];
                    case "getInputStream" -> new ByteArrayInputStream(new byte[0]);
                    case "transferTo" -> null;
                    case "toString" -> filename;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private static final class RecordingFileStorageService implements FileStorageService
    {
        private final int failOnUploadAttempt;
        private final List<String> deletedPaths = new ArrayList<>();
        private int uploadAttempts;

        private RecordingFileStorageService(int failOnUploadAttempt)
        {
            this.failOnUploadAttempt = failOnUploadAttempt;
        }

        @Override
        public String upload(String baseDir, MultipartFile file, String[] allowedExtension) throws Exception
        {
            uploadAttempts++;
            if (uploadAttempts == failOnUploadAttempt)
            {
                throw new java.io.IOException("simulated storage failure");
            }
            return "https://storage.test/upload/" + uploadAttempts;
        }

        @Override
        public String upload(String baseDir, MultipartFile file, String[] allowedExtension, boolean useCustomNaming)
                throws Exception
        {
            return upload(baseDir, file, allowedExtension);
        }

        @Override
        public String uploadBytes(String baseDir, String fileName, byte[] content, String contentType)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean delete(String filePath)
        {
            deletedPaths.add(filePath);
            return true;
        }

        @Override
        public void download(String filePath, HttpServletResponse response)
        {
            throw new UnsupportedOperationException();
        }
    }
}
