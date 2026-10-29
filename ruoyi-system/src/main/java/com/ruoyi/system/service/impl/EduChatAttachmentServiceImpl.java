package com.ruoyi.system.service.impl;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.file.FileStorageService;
import com.ruoyi.system.domain.EduChatAttachment;
import com.ruoyi.system.mapper.EduChatAttachmentMapper;
import com.ruoyi.system.service.IEduChatAttachmentService;
import org.apache.commons.io.FilenameUtils;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
public class EduChatAttachmentServiceImpl implements IEduChatAttachmentService {
    private static final long MAX_FILE_BYTES = 25L * 1024 * 1024;
    private static final int MAX_EXTRACTED_CHARS = 20000;
    private static final String[] ALLOWED = {"txt","md","csv","json","java","py","js","ts","css","xml","sql","yaml","yml","docx","xlsx","pptx","pdf","jpg","jpeg","png","webp"};
    private static final Set<String> TEXT = Set.of("txt","md","csv","json","java","py","js","ts","css","xml","sql","yaml","yml");
    private static final Set<String> IMAGES = Set.of("jpg","jpeg","png","webp");
    @Autowired private FileStorageService fileStorageService;
    @Autowired private EduChatAttachmentMapper attachmentMapper;

    @Override public EduChatAttachment upload(Long userId, String sessionId, MultipartFile file) {
        if (sessionId == null || sessionId.isBlank()) throw new ServiceException("sessionId cannot be empty");
        if (file == null || file.isEmpty()) throw new ServiceException("file cannot be empty");
        if (file.getSize() > MAX_FILE_BYTES) throw new ServiceException("Attachment exceeds 25MB limit");
        String extension = FilenameUtils.getExtension(Objects.requireNonNullElse(file.getOriginalFilename(), "")).toLowerCase(Locale.ROOT);
        if (!Arrays.asList(ALLOWED).contains(extension)) throw new ServiceException("Unsupported attachment type: " + extension);
        verifyFileSignature(file, extension);
        EduChatAttachment attachment = new EduChatAttachment();
        attachment.setUserId(userId); attachment.setSessionId(sessionId);
        attachment.setOriginalName(safeName(file.getOriginalFilename())); attachment.setFileSize(file.getSize());
        attachment.setMimeType(Objects.requireNonNullElse(file.getContentType(), "application/octet-stream")); attachment.setExtension(extension);
        try {
            Extraction extracted = extract(file, extension);
            attachment.setContentStatus(extracted.status); attachment.setExtractedText(extracted.text); attachment.setErrorMessage(extracted.error);
            attachment.setStoragePath(fileStorageService.upload("edu-chat/" + userId + "/" + sessionId, file, ALLOWED, true));
            attachmentMapper.insert(attachment);
            return attachment;
        } catch (ServiceException e) { throw e;
        } catch (Exception e) { throw new ServiceException("Attachment upload failed: " + e.getMessage()); }
    }
    @Override public EduChatAttachment getOwned(Long userId, Long attachmentId) {
        EduChatAttachment attachment = attachmentMapper.selectByIdAndUserId(attachmentId, userId);
        if (attachment == null) throw new ServiceException("Attachment not found"); return attachment;
    }
    @Override public List<EduChatAttachment> validateForChat(Long userId, String sessionId, List<Long> attachmentIds) {
        if (attachmentIds == null || attachmentIds.isEmpty()) return List.of();
        List<Long> ids = attachmentIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.size() != attachmentIds.size()) throw new ServiceException("attachmentIds contains duplicate or invalid value");
        List<EduChatAttachment> attachments = attachmentMapper.selectByIdsAndUserId(ids, userId);
        if (attachments.size() != ids.size()) throw new ServiceException("Attachment not found or not owned by current user");
        for (EduChatAttachment attachment : attachments) {
            if (!sessionId.equals(attachment.getSessionId())) throw new ServiceException("Attachment does not belong to this session");
            if (!"READY".equals(attachment.getContentStatus()) && !"READY_IMAGE".equals(attachment.getContentStatus()))
                throw new ServiceException("Attachment " + attachment.getOriginalName() + " is " + attachment.getContentStatus() + ": " + Objects.requireNonNullElse(attachment.getErrorMessage(), "not available for chat"));
        }
        return attachments;
    }
    @Override public void delete(Long userId, Long attachmentId) {
        EduChatAttachment attachment = getOwned(userId, attachmentId);
        if (!fileStorageService.delete(attachment.getStoragePath())) throw new ServiceException("Attachment storage deletion failed");
        if (attachmentMapper.deleteByIdAndUserId(attachmentId, userId) != 1) throw new ServiceException("Attachment deletion failed");
    }
    @Override public void deleteSessionAttachments(Long userId, String sessionId) {
        for (EduChatAttachment attachment : attachmentMapper.selectBySessionAndUserId(sessionId, userId)) {
            delete(userId, attachment.getId());
        }
    }
    @Override public Map<String, Object> publicMetadata(EduChatAttachment a) {
        Map<String,Object> result = new LinkedHashMap<>(); result.put("attachmentId", a.getId()); result.put("sessionId", a.getSessionId());
        result.put("fileName", a.getOriginalName()); result.put("fileSize", a.getFileSize()); result.put("mimeType", a.getMimeType());
        result.put("status", a.getContentStatus()); result.put("errorMessage", a.getErrorMessage()); return result;
    }
    private Extraction extract(MultipartFile file, String ext) throws Exception {
        if (IMAGES.contains(ext)) return new Extraction("READY_IMAGE", null, "Image stored; visual recognition is not enabled in the text model pipeline.");
        if (TEXT.contains(ext)) return new Extraction("READY", truncate(new String(file.getBytes(), StandardCharsets.UTF_8)), null);
        if ("docx".equals(ext)) try (InputStream in=file.getInputStream(); XWPFDocument d=new XWPFDocument(in); XWPFWordExtractor e=new XWPFWordExtractor(d)) { return new Extraction("READY", truncate(e.getText()), null); }
        if ("xlsx".equals(ext)) try (InputStream in=file.getInputStream(); XSSFWorkbook wb=new XSSFWorkbook(in)) { StringBuilder b=new StringBuilder(); wb.forEach(s -> s.forEach(r -> r.forEach(c -> b.append(c).append('\t')))); return new Extraction("READY", truncate(b.toString()), null); }
        if ("pptx".equals(ext)) try (InputStream in=file.getInputStream(); XMLSlideShow p=new XMLSlideShow(in)) { StringBuilder b=new StringBuilder(); p.getSlides().forEach(s -> s.getShapes().forEach(shape -> { if (shape instanceof org.apache.poi.xslf.usermodel.XSLFTextShape t) b.append(t.getText()).append('\n'); })); return new Extraction("READY", truncate(b.toString()), null); }
        if ("pdf".equals(ext)) try (InputStream in=file.getInputStream(); PDDocument document=PDDocument.load(in)) { return new Extraction("READY", truncate(new PDFTextStripper().getText(document)), null); }
        return new Extraction("UNSUPPORTED", null, "This file type is stored but is not parsed by the current chat pipeline.");
    }
    private void verifyFileSignature(MultipartFile file, String extension) {
        try (InputStream input = file.getInputStream()) {
            byte[] header = input.readNBytes(16);
            if ("pdf".equals(extension) && !startsWith(header, 0, 0x25, 0x50, 0x44, 0x46, 0x2d)) throw new ServiceException("File content is not a PDF");
            if ("png".equals(extension) && !startsWith(header, 0, 0x89, 0x50, 0x4e, 0x47)) throw new ServiceException("File content is not a PNG image");
            if (("jpg".equals(extension) || "jpeg".equals(extension)) && !startsWith(header, 0, 0xff, 0xd8, 0xff)) throw new ServiceException("File content is not a JPEG image");
            if ("webp".equals(extension) && !(startsWith(header, 0x52, 0x49, 0x46, 0x46) && startsWith(header, 8, 0x57, 0x45, 0x42, 0x50))) throw new ServiceException("File content is not a WebP image");
            if (Set.of("docx", "xlsx", "pptx").contains(extension) && !startsWith(header, 0, 0x50, 0x4b)) throw new ServiceException("Office attachment must be an OOXML document");
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new ServiceException("Unable to verify attachment content");
        }
    }
    private boolean startsWith(byte[] value, int offset, int... expected) {
        if (value.length < offset + expected.length) return false;
        for (int index = 0; index < expected.length; index++) if ((value[offset + index] & 0xff) != expected[index]) return false;
        return true;
    }
    private String truncate(String text) { if (text == null || text.isBlank()) throw new ServiceException("Attachment has no extractable text"); return text.length() > MAX_EXTRACTED_CHARS ? text.substring(0, MAX_EXTRACTED_CHARS) + "\n[truncated]" : text; }
    private String safeName(String name) { String normalized = Objects.requireNonNullElse(name, "attachment").replaceAll("[\\r\\n\\\\/]", "_"); return normalized.length() > 200 ? normalized.substring(0, 200) : normalized; }
    private record Extraction(String status, String text, String error) {}
}
