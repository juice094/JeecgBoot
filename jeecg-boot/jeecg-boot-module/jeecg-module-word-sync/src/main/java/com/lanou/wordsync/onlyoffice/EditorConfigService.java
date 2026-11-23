package com.lanou.wordsync.onlyoffice;

import com.lanou.wordsync.document.entity.Document;
import com.lanou.wordsync.document.service.DocumentService;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 生成 OnlyOffice 前端初始化配置。
 *
 * 前端拿到返回的 Map 后，调用 OnlyOffice 的 DocsAPI.DocEditor 初始化即可。
 */
@Service
public class EditorConfigService {

    private final DocumentService documentService;
    private final OnlyOfficeProperties properties;

    public EditorConfigService(DocumentService documentService,
                               OnlyOfficeProperties properties) {
        this.documentService = documentService;
        this.properties = properties;
    }

    public Map<String, Object> build(Long docId, Long userId, String userName, String mode) {
        Document document = documentService.getById(docId);

        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("fileType", "docx");
        doc.put("key", "word-" + docId + "-v" + document.getCurrentVersion());
        doc.put("title", document.getTitle());
        // OnlyOffice 容器会通过这个 URL 下载文件，所以要用 host.docker.internal
        doc.put("url", properties.getDocumentBaseUrl() + "/api/documents/" + docId + "/download");

        Map<String, Object> user = new LinkedHashMap<>();
        user.put("id", String.valueOf(userId == null ? 0 : userId));
        user.put("name", userName == null || userName.isBlank() ? "user" : userName);

        Map<String, Object> editorConfig = new LinkedHashMap<>();
        editorConfig.put("callbackUrl", properties.getCallbackBaseUrl()
                + "/api/onlyoffice/callback/" + docId);
        editorConfig.put("lang", "zh-CN");
        editorConfig.put("mode", mode == null || mode.isBlank() ? "edit" : mode);
        editorConfig.put("user", user);

        // 允许前端调用 serviceCommand('forcesave')，每次点击“保存新版本”生成一个版本
        Map<String, Object> customization = new LinkedHashMap<>();
        customization.put("forcesave", true);
        customization.put("autosave", true);
        customization.put("chat", false);
        editorConfig.put("customization", customization);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("document", doc);
        result.put("documentType", "word");
        result.put("editorConfig", editorConfig);
        return result;
    }
}
