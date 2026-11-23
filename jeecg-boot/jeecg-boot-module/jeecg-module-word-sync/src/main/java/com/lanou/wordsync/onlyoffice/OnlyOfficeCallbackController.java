package com.lanou.wordsync.onlyoffice;

import com.lanou.wordsync.audit.service.AuditService;
import com.lanou.wordsync.document.entity.Document;
import com.lanou.wordsync.document.service.DocumentService;
import com.lanou.wordsync.version.entity.DocumentVersion;
import com.lanou.wordsync.version.service.VersionService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * OnlyOffice 保存回调。
 *
 * OnlyOffice 回调状态：
 * - 1：正在编辑；
 * - 2：所有用户关闭文档，可以保存；
 * - 4：关闭但无修改；
 * - 6：强制保存；
 * - 3/7：保存失败。
 *
 * 每次回调写一条审计日志；status=2/6 时下载新文件并生成版本。
 * 如果内容 SHA-256 和当前版本一致，则只记审计，不重复生成版本。
 */
@RestController
public class OnlyOfficeCallbackController {

    private static final Logger log = LoggerFactory.getLogger(OnlyOfficeCallbackController.class);

    private final DocumentService documentService;
    private final VersionService versionService;
    private final AuditService auditService;
    private final RestTemplate restTemplate = new RestTemplate();

    public OnlyOfficeCallbackController(DocumentService documentService,
                                        VersionService versionService,
                                        AuditService auditService) {
        this.documentService = documentService;
        this.versionService = versionService;
        this.auditService = auditService;
    }

    @PostMapping("/word-sync/api/onlyoffice/callback/{docId}")
    public Map<String, Object> callback(@PathVariable Long docId,
                                        @RequestBody Map<String, Object> body,
                                        @RequestParam(required = false) Long userId,
                                        HttpServletRequest request) {
        int status = parseStatus(body.get("status"));
        Long operatorId = resolveUserId(body, userId);
        String ip = request.getRemoteAddr();
        log.info("OnlyOffice callback: docId={}, status={}, userId={}, url={}",
                docId, status, operatorId, body.get("url"));

        // 1) 非保存状态：只记审计，不生成版本
        if (status != 2 && status != 6) {
            auditService.log(docId, operatorId, "ONLYOFFICE_CALLBACK",
                    "status=" + status + ";result=IGNORED", ip, null);
            return Map.of("error", 0);
        }

        String url = body.get("url") instanceof String s ? s : null;
        if (url == null || url.isBlank()) {
            auditService.log(docId, operatorId, "ONLYOFFICE_CALLBACK",
                    "status=" + status + ";result=FAILED;reason=missing_url", ip, null);
            return Map.of("error", 1);
        }

        try {
            byte[] bytes = restTemplate.getForObject(url, byte[].class);
            if (bytes == null || bytes.length == 0) {
                auditService.log(docId, operatorId, "ONLYOFFICE_CALLBACK",
                        "status=" + status + ";result=FAILED;reason=empty_file", ip, null);
                return Map.of("error", 1);
            }

            // 内容没有变化：不生成重复版本
            if (versionService.isSameAsCurrent(docId, bytes)) {
                auditService.log(docId, operatorId, "ONLYOFFICE_CALLBACK",
                        "status=" + status + ";result=DUPLICATE", ip, null);
                return Map.of("error", 0);
            }

            Document document = documentService.getById(docId);
            int nextVersion = document.getCurrentVersion() + 1;
            DocumentVersion version = versionService.createVersion(
                    docId, nextVersion, bytes, "document.docx",
                    VersionService.DOCX_CONTENT_TYPE, "ONLYOFFICE", operatorId, false);

            // 回调链路只写这一条审计（VersionService 内部已关闭审计）
            auditService.log(docId, operatorId, "ONLYOFFICE_SAVE",
                    "status=" + status
                            + ";versionNo=" + version.getVersionNo()
                            + ";sha256=" + version.getSha256()
                            + ";result=SUCCESS",
                    ip, null);
            return Map.of("error", 0);
        } catch (Exception e) {
            log.warn("OnlyOffice callback failed: docId={}, status={}, url={}", docId, status, url, e);
            auditService.log(docId, operatorId, "ONLYOFFICE_CALLBACK",
                    "status=" + status + ";result=FAILED;reason=" + e.getMessage(), ip, null);
            return Map.of("error", 1);
        }
    }

    private int parseStatus(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String s) {
            try {
                return Integer.parseInt(s);
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }

    /**
     * OnlyOffice 回调通常不带我们的 userId，优先用编辑器配置传入的 users 数组。
     */
    private Long resolveUserId(Map<String, Object> body, Long queryUserId) {
        if (queryUserId != null) {
            return queryUserId;
        }
        Object users = body.get("users");
        if (users instanceof List<?> list && !list.isEmpty()) {
            Object first = list.get(0);
            if (first instanceof Number number) {
                return number.longValue();
            }
            if (first instanceof String s) {
                try {
                    return Long.parseLong(s);
                } catch (NumberFormatException ignored) {
                    return 0L;
                }
            }
        }
        return 0L;
    }
}
