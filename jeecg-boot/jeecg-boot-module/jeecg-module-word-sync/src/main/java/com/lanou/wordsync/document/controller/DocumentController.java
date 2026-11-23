package com.lanou.wordsync.document.controller;

import com.lanou.wordsync.common.result.PageResult;
import com.lanou.wordsync.common.result.Result;
import com.lanou.wordsync.document.entity.Document;
import com.lanou.wordsync.document.service.DocumentService;
import com.lanou.wordsync.version.service.VersionService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * 文档接口。
 */
@RestController
@RequestMapping("/word-sync/api/documents")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    /** 分页列表 */
    @GetMapping
    public Result<PageResult<Document>> list(@RequestParam(defaultValue = "1") int page,
                                             @RequestParam(defaultValue = "20") int size,
                                             @RequestParam(required = false) String keyword,
                                             @RequestParam(required = false) Long ownerId) {
        return Result.ok(documentService.page(page, size, keyword, ownerId));
    }

    /** 回收站列表 */
    @GetMapping("/recycle")
    public Result<PageResult<Document>> recycle(@RequestParam(defaultValue = "1") int page,
                                                @RequestParam(defaultValue = "20") int size) {
        return Result.ok(documentService.deletedPage(page, size));
    }

    /** 从回收站恢复 */
    @PostMapping("/{documentId}/restore")
    public Result<Void> restore(@PathVariable Long documentId,
                                @RequestParam(required = false) Long userId) {
        documentService.restore(documentId, userId);
        return Result.ok();
    }

    /** 上传 Word 文档 */
    @PostMapping("/upload")
    public Result<Document> upload(@RequestParam("file") MultipartFile file,
                                   @RequestParam(required = false) String title,
                                   @RequestParam(required = false) Long userId) throws IOException {
        return Result.ok(documentService.upload(file, title, userId));
    }

    /** 文档详情 */
    @GetMapping("/{documentId}")
    public Result<Document> detail(@PathVariable Long documentId) {
        return Result.ok(documentService.getById(documentId));
    }

    /** 下载当前版本 */
    @GetMapping("/{documentId}/download")
    public ResponseEntity<byte[]> download(@PathVariable Long documentId,
                                           @RequestParam(required = false) Long userId,
                                           HttpServletRequest request) {
        byte[] bytes = documentService.download(documentId, userId, request.getRemoteAddr());
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(VersionService.DOCX_CONTENT_TYPE));
        headers.set(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"document-" + documentId + ".docx\"");
        return new ResponseEntity<>(bytes, headers, HttpStatus.OK);
    }

    /** 软删除 */
    @DeleteMapping("/{documentId}")
    public Result<Void> delete(@PathVariable Long documentId,
                               @RequestParam(required = false) Long userId) {
        documentService.delete(documentId, userId);
        return Result.ok();
    }
}
