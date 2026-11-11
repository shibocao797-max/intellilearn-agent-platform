package com.ruoyi.system.service.impl;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.file.FileStorageService;
import com.ruoyi.system.domain.EduChatAttachment;
import com.ruoyi.system.mapper.ConversationHistoryMapper;
import com.ruoyi.system.mapper.ConversationSessionMapper;
import com.ruoyi.system.mapper.EduChatAttachmentMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EduChatAttachmentSecurityTest
{
    @Test
    void rejectsUploadForUnknownOrAnotherUsersSession() throws Exception
    {
        RecordingStorage storage = new RecordingStorage();
        EduChatAttachmentServiceImpl service = service(storage, 0, 0, 1);

        assertThrows(ServiceException.class,
                () -> service.upload(42L, "not-owned-session", textFile()));
        assertEquals(0, storage.uploadDirectories.size());
    }

    @Test
    void clientSessionIdCannotChooseTheStorageDirectory() throws Exception
    {
        RecordingStorage storage = new RecordingStorage();
        EduChatAttachmentServiceImpl service = service(storage, 1, 0, 1);

        EduChatAttachment attachment = service.upload(42L, "../../outside", textFile());

        assertEquals("../../outside", attachment.getSessionId());
        assertEquals(1, storage.uploadDirectories.size());
        String directory = storage.uploadDirectories.get(0);
        assertTrue(directory.startsWith("edu-chat/private/42/"));
        assertFalse(directory.contains(".."));
        assertEquals("storage://private/1", attachment.getStoragePath());
    }

    @Test
    void removesUploadedObjectWhenAttachmentMetadataIsNotSaved() throws Exception
    {
        RecordingStorage storage = new RecordingStorage();
        EduChatAttachmentServiceImpl service = service(storage, 1, 0, 0);

        assertThrows(ServiceException.class,
                () -> service.upload(42L, "owned-session", textFile()));

        assertEquals(1, storage.uploadDirectories.size());
        assertEquals(List.of("storage://private/1"), storage.deletedPaths);
    }

    private EduChatAttachmentServiceImpl service(RecordingStorage storage, int ownedSession,
                                                  int historySession, int insertRows) throws Exception
    {
        EduChatAttachmentServiceImpl service = new EduChatAttachmentServiceImpl();
        set(service, "fileStorageService", storage);
        set(service, "sessionMapper", proxy(ConversationSessionMapper.class,
                (method, args) -> ownedSession));
        set(service, "historyMapper", proxy(ConversationHistoryMapper.class,
                (method, args) -> historySession));
        set(service, "attachmentMapper", proxy(EduChatAttachmentMapper.class, (method, args) -> {
            if ("insert".equals(method))
            {
                storage.savedAttachment = (EduChatAttachment) args[0];
                return insertRows;
            }
            return 0;
        }));
        return service;
    }

    private MultipartFile textFile()
    {
        byte[] content = "hello attachment".getBytes(StandardCharsets.UTF_8);
        return (MultipartFile) Proxy.newProxyInstance(MultipartFile.class.getClassLoader(),
                new Class<?>[] { MultipartFile.class }, (proxy, method, args) -> switch (method.getName())
                {
                    case "getOriginalFilename" -> "notes.txt";
                    case "getContentType" -> "text/plain";
                    case "getSize" -> (long) content.length;
                    case "isEmpty" -> false;
                    case "getInputStream" -> new ByteArrayInputStream(content);
                    case "getBytes" -> content;
                    case "getName" -> "file";
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private <T> T proxy(Class<T> type, ProxyCall call)
    {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type },
                (proxy, method, args) -> call.invoke(method.getName(), args)));
    }

    private void set(Object target, String name, Object value) throws ReflectiveOperationException
    {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    @FunctionalInterface
    private interface ProxyCall
    {
        Object invoke(String method, Object[] args);
    }

    private static final class RecordingStorage implements FileStorageService
    {
        private final List<String> uploadDirectories = new ArrayList<>();
        private final List<String> deletedPaths = new ArrayList<>();
        private EduChatAttachment savedAttachment;

        @Override
        public String upload(String baseDir, MultipartFile file, String[] allowedExtension) {
            uploadDirectories.add(baseDir);
            return "storage://private/" + uploadDirectories.size();
        }

        @Override
        public String upload(String baseDir, MultipartFile file, String[] allowedExtension, boolean useCustomNaming) {
            return upload(baseDir, file, allowedExtension);
        }

        @Override
        public String uploadBytes(String baseDir, String fileName, byte[] content, String contentType) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean delete(String filePath) {
            deletedPaths.add(filePath);
            return true;
        }

        @Override
        public void download(String filePath, jakarta.servlet.http.HttpServletResponse response) {
            throw new UnsupportedOperationException();
        }
    }
}
