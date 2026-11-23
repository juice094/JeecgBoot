package com.lanou.wordsync.webdav;

import com.lanou.wordsync.common.exception.BizException;
import com.lanou.wordsync.document.entity.Document;
import com.lanou.wordsync.document.service.DocumentService;
import com.lanou.wordsync.version.service.VersionService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

/**
 * WebDAV 基础接口（MVP）：
 * - GET 下载当前版本；
 * - PUT 上传新文件并生成新版本；
 * - OPTIONS 返回支持的方法。
 *
 * 1. 增加 PROPFIND 返回资源元数据；
 * 2. 增加 LOCK/UNLOCK（可先用 Redis 或数据库锁表实现）；
 * 3. 支持 If-Match / ETag 版本冲突返回 412。
 */
@RestController
@RequestMapping("/word-sync/webdav")
public class WebDavController {

    private final DocumentService documentService;
    private final VersionService versionService;

    public WebDavController(DocumentService documentService,
                            VersionService versionService) {
        this.documentService = documentService;
        this.versionService = versionService;
    }

    @GetMapping("/{docId}")
    public ResponseEntity<byte[]> get(@PathVariable Long docId) {
        byte[] bytes = documentService.download(docId);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(VersionService.DOCX_CONTENT_TYPE));
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"document.docx\"");
        String currentSha = versionService.getCurrentSha256(docId);
        if (currentSha != null) {
            headers.setETag("\"" + currentSha + "\"");
        }
        return new ResponseEntity<>(bytes, headers, HttpStatus.OK);
    }

    @PutMapping("/{docId}")
    public ResponseEntity<Void> put(@PathVariable Long docId,
                                    @RequestBody(required = false) byte[] body,
                                    @RequestHeader(value = "If-Match",required = false) String ifMatch,
                                    @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        if (body == null || body.length == 0) {
            throw new BizException(400, "PUT 文件内容为空");
        }

        Document doc = documentService.getById(docId);

        // If-Match: "*" 表示只要资源存在就允许；
        // 具体 ETag 则和当前版本的 sha256 比较，不匹配返回 412。
        if (ifMatch != null && !ifMatch.isBlank() && !"*".equals(ifMatch.trim())) {
            String currentSha = versionService.getCurrentSha256(docId);
            String expect = ifMatch.trim();
            if (expect.startsWith("W/")) {
                expect = expect.substring(2);
            }
            expect = expect.replace("\"", "").trim();
            if (currentSha == null || !currentSha.equalsIgnoreCase(expect)) {
                return ResponseEntity
                        .status(HttpStatus.PRECONDITION_FAILED)
                        .build();
            }
        }

        var created = versionService.createVersion(docId, doc.getCurrentVersion() + 1, body,
                "document.docx", VersionService.DOCX_CONTENT_TYPE, "WEBDAV",
                userId == null ? 0L : userId);

        return ResponseEntity.status(HttpStatus.CREATED)
                .eTag("\"" + created.getSha256() + "\"")
                .build();
    }

    @RequestMapping(value = "/{docId}", method = RequestMethod.OPTIONS)
    public ResponseEntity<Void> options(@PathVariable Long docId) {
        return ResponseEntity.ok()
                .header("Allow", "OPTIONS, GET, PUT, PROPFIND, LOCK, UNLOCK")
                .header("DAV", "1,2")
                .build();
    }
}
