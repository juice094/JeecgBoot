package com.lanou.wordsync.onlyoffice;

import com.lanou.wordsync.document.entity.Document;
import com.lanou.wordsync.document.service.DocumentService;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 调用 OnlyOffice Command Service 的 forcesave 命令。
 *
 * 前端“保存新版本”按钮走这里，比直接调用浏览器端 serviceCommand 更可靠：
 * 后端用当前文档 key 请求 OnlyOffice 立即保存，OnlyOffice 再回调 status=6。
 *
 * 当前 OnlyOffice JWT 未开启；如果以后开启 JWT，需要在这里补 token。
 */
@Service
public class OnlyOfficeCommandService {

    private final DocumentService documentService;
    private final OnlyOfficeProperties properties;
    private final RestTemplate restTemplate = new RestTemplate();

    public OnlyOfficeCommandService(DocumentService documentService,
                                    OnlyOfficeProperties properties) {
        this.documentService = documentService;
        this.properties = properties;
    }

    public Map<String, Object> forceSave(Long docId) {
        Document document = documentService.getById(docId);
        String key = "word-" + docId + "-v" + document.getCurrentVersion();
        String base = properties.getServerUrl();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        String url = base + "/coauthoring/CommandService.ashx";

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("c", "forcesave");
        request.put("key", key);

        Map response = restTemplate.postForObject(url, request, Map.class);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("key", key);
        result.put("response", response);
        return result;
    }
}
