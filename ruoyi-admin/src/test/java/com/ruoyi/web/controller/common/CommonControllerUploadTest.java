package com.ruoyi.web.controller.common;

import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.annotation.RateLimiter;
import com.ruoyi.common.enums.LimitType;
import com.ruoyi.common.utils.file.FileStorageService;
import com.ruoyi.common.utils.file.FileUploadUtils;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.common.constant.UserType;
import com.ruoyi.system.domain.SysAttachment;
import com.ruoyi.system.mapper.SysAttachmentMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommonControllerUploadTest
{
    @AfterEach
    void clearSecurityContext()
    {
        SecurityContextHolder.clearContext();
    }

    @Test
    void protectsDestructiveFileOperationsWithAttachmentPermission() throws Exception
    {
        Method download = CommonController.class.getMethod(
                "fileDownload", String.class, Boolean.class,
                jakarta.servlet.http.HttpServletResponse.class,
                jakarta.servlet.http.HttpServletRequest.class);
        Method delete = CommonController.class.getMethod("fileDelete", String.class);

        assertEquals("isAuthenticated()",
                download.getAnnotation(PreAuthorize.class).value());
        assertEquals("@ss.hasPermi('system:attachment:list')",
                delete.getAnnotation(PreAuthorize.class).value());
    }

    @Test
    void permitsOwnerToDownloadAttachment()
    {
        CommonController controller = controllerWith(new RecordingFileStorageService(0),
                new RecordingAttachmentMapper(true));
        authenticate("alice", 2L, UserType.USER);

        assertDoesNotThrow(() -> invokeDownloadAuthorization(controller, "/profile/alice.pdf", false));
    }

    @Test
    void rejectsDownloadOfAnotherUsersAttachment()
    {
        CommonController controller = controllerWith(new RecordingFileStorageService(0),
                new RecordingAttachmentMapper(false));
        authenticate("alice", 2L, UserType.USER);

        InvocationTargetException error = assertThrows(InvocationTargetException.class,
                () -> invokeDownloadAuthorization(controller, "/profile/bob.pdf", false));
        assertEquals(org.springframework.security.access.AccessDeniedException.class,
                error.getCause().getClass());
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
        return controllerWith(storage, null);
    }

    private CommonController controllerWith(FileStorageService storage, SysAttachmentMapper mapper)
    {
        CommonController controller = new CommonController();
        try
        {
            Field storageField = CommonController.class.getDeclaredField("fileStorageService");
            storageField.setAccessible(true);
            storageField.set(controller, storage);
            Field mapperField = CommonController.class.getDeclaredField("attachmentMapper");
            mapperField.setAccessible(true);
            mapperField.set(controller, mapper);
        }
        catch (ReflectiveOperationException e)
        {
            throw new AssertionError(e);
        }
        return controller;
    }

    private void authenticate(String username, long userId, String userType)
    {
        SysUser user = new SysUser();
        user.setUserId(userId);
        user.setUserName(username);
        user.setUserType(userType);
        LoginUser loginUser = new LoginUser(user, java.util.Set.of());
        loginUser.setUserId(userId);
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(loginUser, null));
    }

    private void invokeDownloadAuthorization(CommonController controller, String filePath, boolean delete)
            throws ReflectiveOperationException
    {
        Method method = CommonController.class.getDeclaredMethod(
                "assertDownloadAuthorized", String.class, boolean.class);
        method.setAccessible(true);
        method.invoke(controller, filePath, delete);
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

    private static final class RecordingAttachmentMapper implements SysAttachmentMapper
    {
        private final boolean ownerMatch;

        private RecordingAttachmentMapper(boolean ownerMatch)
        {
            this.ownerMatch = ownerMatch;
        }

        @Override
        public SysAttachment selectAttachmentById(Long id)
        {
            return null;
        }

        @Override
        public SysAttachment selectAttachmentByPathAndOwner(String filePath, String createBy)
        {
            return ownerMatch ? new SysAttachment() : null;
        }

        @Override
        public List<SysAttachment> selectAttachmentList(SysAttachment attachment)
        {
            return List.of();
        }

        @Override
        public int insertAttachment(SysAttachment attachment)
        {
            return 1;
        }

        @Override
        public int deleteAttachmentByIds(Long[] ids)
        {
            return 0;
        }

        @Override
        public int deleteAttachmentByPaths(String[] filePaths)
        {
            return 0;
        }
    }
}
