package com.lanou.wordsync.audit.controller;

import com.lanou.wordsync.audit.entity.AuditLog;
import com.lanou.wordsync.audit.service.AuditService;
import com.lanou.wordsync.common.result.PageResult;
import com.lanou.wordsync.common.result.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 审计日志查询接口。
 */
@RestController
@RequestMapping("/word-sync/api/documents")
public class AuditController {

    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    /**
     * 查询某文档的审计日志。
     * 示例：GET /api/documents/1/audit?page=1&size=20
     */
    @GetMapping("/{docId}/audit")
    public Result<PageResult<AuditLog>> list(@PathVariable Long docId,
                                             @RequestParam(defaultValue = "1") int page,
                                             @RequestParam(defaultValue = "20") int size) {
        return Result.ok(auditService.page(docId, page, size));
    }
}
