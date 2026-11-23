package com.lanou.wordsync.document.service;

import com.lanou.wordsync.audit.service.AuditService;
import com.lanou.wordsync.common.exception.BizException;
import com.lanou.wordsync.common.result.PageResult;
import com.lanou.wordsync.document.entity.Document;
import com.lanou.wordsync.document.mapper.DocumentMapper;
import com.lanou.wordsync.infra.minio.StorageService;
import com.lanou.wordsync.realtime.RealtimePublisher;
import com.lanou.wordsync.version.service.VersionService;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import com.baomidou.dynamic.datasource.annotation.DS;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * 文档服务：上传、列表、详情、下载、软删除。
 *
 * 上传后调用 VersionService 创建 v1 版本；
 * 下载读取“当前版本”对应的 MinIO 文件。
 */
@DS("wordsync")
@Service
public class DocumentService {

    private final DocumentMapper documentMapper;
    private final VersionService versionService;
    private final StorageService storageService;
    private final AuditService auditService;
    private final RealtimePublisher realtimePublisher;
    private final DocumentCacheService documentCacheService;

    public DocumentService(DocumentMapper documentMapper,
                           VersionService versionService,
                           StorageService storageService,
                           AuditService auditService,
                           RealtimePublisher realtimePublisher,
                           DocumentCacheService documentCacheService) {
        this.documentMapper = documentMapper;
        this.versionService = versionService;
        this.storageService = storageService;
        this.auditService = auditService;
        this.realtimePublisher = realtimePublisher;
        this.documentCacheService = documentCacheService;
    }

    public PageResult<Document> page(int page, int size, String keyword) {
        return page(page, size, keyword, null);
    }

    public PageResult<Document> page(int page, int size, String keyword, Long ownerId) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 100);
        int offset = (safePage - 1) * safeSize;
        long total = documentMapper.count(keyword, ownerId);
        List<Document> list = documentMapper.selectPage(keyword, ownerId, offset, safeSize);
        return new PageResult<>(total, list);
    }

    public Document getById(Long documentId) {
        Document cached = documentCacheService.get(documentId);
        if (cached != null && !"DELETED".equals(cached.getStatus())) {
            return cached;
        }
        Document document = documentMapper.selectById(documentId);
        if (document == null || "DELETED".equals(document.getStatus())) {
            throw new BizException(404, "文档不存在");
        }
        documentCacheService.put(document);
        return document;
    }

    /**
     * 上传文档：先插入文档记录，再创建第一个版本。
     */
    @Transactional(rollbackFor = Exception.class)
    public Document upload(MultipartFile file, String title, Long ownerId) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new BizException(400, "上传文件不能为空");
        }
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null
                || !originalFilename.toLowerCase().endsWith(".docx")) {
            throw new BizException(400, "只允许上传 .docx 文件");
        }
        if (file.getSize() > 20L * 1024 * 1024) {
            throw new BizException(400, "文件不能超过 20MB");
        }
        Document document = new Document();
        document.setTitle(title == null || title.isBlank() ? originalFilename : title);
        document.setOwnerId(ownerId == null ? 0L : ownerId);
        document.setCurrentVersion(0);
        document.setStatus("ACTIVE");
        documentMapper.insert(document);

        versionService.createVersion(document.getDocumentId(), 1,
                file.getBytes(), file.getOriginalFilename(), file.getContentType(),
                "UPLOAD", ownerId);

        return getById(document.getDocumentId());
    }

    /**
     * 下载当前版本。
     */
    public byte[] download(Long documentId) {
        return download(documentId, null, null);
    }

    /**
     * 下载当前版本，并记录下载审计。
     */
    public byte[] download(Long documentId, Long userId, String ip) {
        Document document = getById(documentId);
        if (document.getCurrentVersion() == null || document.getCurrentVersion() == 0
                || document.getStorageKey() == null) {
            throw new BizException(404, "文档还没有可下载的版本");
        }
        byte[] bytes = storageService.download(document.getStorageKey());
        auditService.log(documentId, userId, "DOWNLOAD",
                "下载版本 " + document.getCurrentVersion(), ip, MDC.get("traceId"));
        return bytes;
    }

    /**
     * 回收站分页。
     */
    public PageResult<Document> deletedPage(int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 100);
        int offset = (safePage - 1) * safeSize;
        return new PageResult<>(documentMapper.countDeleted(),
                documentMapper.selectDeletedPage(offset, safeSize));
    }

    /**
     * 从回收站恢复。
     */
    @Transactional(rollbackFor = Exception.class)
    public void restore(Long documentId, Long userId) {
        Document document = documentMapper.selectById(documentId);
        if (document == null || !"DELETED".equals(document.getStatus())) {
            throw new BizException(404, "回收站中没有该文档");
        }
        documentMapper.restoreStatus(documentId);
        documentCacheService.evict(documentId);
        auditService.log(documentId, userId, "RESTORE", "从回收站恢复文档",
                null, MDC.get("traceId"));
        realtimePublisher.publish(documentId, null, "RESTORE", userId);
    }

    /**
     * 软删除：只把 status 改成 DELETED，不物理删除历史版本。
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long documentId, Long userId) {
        getById(documentId);
        documentMapper.updateStatus(documentId, "DELETED");
        documentCacheService.evict(documentId);
        auditService.log(documentId, userId, "DELETE", "软删除文档", null, MDC.get("traceId"));
        realtimePublisher.publish(documentId, null, "DELETE", userId);
    }
}
