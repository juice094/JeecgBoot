package com.lanou.wordsync.version.controller;

import com.lanou.wordsync.common.result.Result;
import com.lanou.wordsync.version.entity.DocumentVersion;
import com.lanou.wordsync.version.service.VersionService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 版本管理接口。
 */
@RestController
@RequestMapping("/word-sync/api/documents")
public class VersionController {

    private final VersionService versionService;

    public VersionController(VersionService versionService) {
        this.versionService = versionService;
    }

    /** 版本列表（倒序） */
    @GetMapping("/{docId}/versions")
    public Result<List<DocumentVersion>> list(@PathVariable Long docId) {
        return Result.ok(versionService.list(docId));
    }

    /** 下载指定版本 */
    @GetMapping("/{docId}/versions/{versionNo}/download")
    public ResponseEntity<byte[]> download(@PathVariable Long docId,
                                           @PathVariable Integer versionNo,
                                           @RequestParam(required = false) Long userId,
                                           HttpServletRequest request) {
        byte[] bytes = versionService.download(docId, versionNo, userId, request.getRemoteAddr());
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(VersionService.DOCX_CONTENT_TYPE));
        headers.set(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"document-v" + versionNo + ".docx\"");
        return new ResponseEntity<>(bytes, headers, HttpStatus.OK);
    }

    /** 回滚到指定版本：生成一个 source=ROLLBACK 的新版本 */
    @PostMapping("/{docId}/versions/{versionNo}/rollback")
    public Result<DocumentVersion> rollback(@PathVariable Long docId,
                                             @PathVariable Integer versionNo,
                                             @RequestParam(required = false) Long userId) {
        return Result.ok(versionService.rollback(docId, versionNo, userId));
    }
}
