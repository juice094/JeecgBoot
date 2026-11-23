package com.lanou.wordsync.webdav;

import com.lanou.wordsync.document.entity.Document;
import com.lanou.wordsync.document.service.DocumentService;
import com.lanou.wordsync.version.entity.DocumentVersion;
import com.lanou.wordsync.version.service.VersionService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

/**
 * WebDAV 协议补充 Filter。
 *
 * Spring MVC 不能直接映射 PROPFIND / LOCK / UNLOCK，所以在这里处理：
 * - PROPFIND：返回 Depth 0/1 的资源属性（大小、时间、类型、ETag）；
 * - LOCK：Redis SET NX + lockdiscovery XML；
 * - UNLOCK：校验 Lock-Token 后释放。
 *
 * GET/PUT/OPTIONS 继续交给 WebDavController。
 */
@Component
public class WebDavFilter extends OncePerRequestFilter {

    private static final String LOCK_PREFIX = "webdav:lock:";
    private static final Duration DEFAULT_LOCK_TTL = Duration.ofMinutes(30);
    private static final DateTimeFormatter HTTP_DATE =
            DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.ENGLISH);

    private final StringRedisTemplate redis;
    private final DocumentService documentService;
    private final VersionService versionService;

    public WebDavFilter(StringRedisTemplate redis,
                        DocumentService documentService,
                        VersionService versionService) {
        this.redis = redis;
        this.documentService = documentService;
        this.versionService = versionService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {
        String uri = request.getRequestURI();
        String webdavPath = request.getContextPath() + "/word-sync/webdav/";
        if (!uri.startsWith(webdavPath)) {
            filterChain.doFilter(request, response);
            return;
        }

        String method = request.getMethod();
        String rawDocId = uri.substring(webdavPath.length());
        if (rawDocId.isBlank() || rawDocId.contains("/")) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        if ("PROPFIND".equalsIgnoreCase(method)) {
            handlePropfind(rawDocId, request, response);
            return;
        }
        if ("LOCK".equalsIgnoreCase(method)) {
            handleLock(rawDocId, request, response);
            return;
        }
        if ("UNLOCK".equalsIgnoreCase(method)) {
            handleUnlock(rawDocId, request, response);
            return;
        }

        // GET / PUT / OPTIONS 交给 Controller
        filterChain.doFilter(request, response);
    }

    private void handlePropfind(String rawDocId,
                                HttpServletRequest request,
                                HttpServletResponse response) throws IOException {
        Long docId;
        try {
            docId = Long.parseLong(rawDocId);
        } catch (NumberFormatException e) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }

        Document document;
        try {
            document = documentService.getById(docId);
        } catch (Exception e) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        String etag = versionService.getCurrentSha256(docId);
        long size = 0L;
        Integer current = document.getCurrentVersion();
        if (current != null && current > 0) {
            try {
                DocumentVersion version = versionService.get(docId, current);
                if (version.getSize() != null) {
                    size = version.getSize();
                }
                if (etag == null) {
                    etag = version.getSha256();
                }
            } catch (Exception ignored) {
                // 版本记录不存在时仍返回文档属性
            }
        }

        String href = request.getContextPath() + "/word-sync/webdav/" + docId;
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<d:multistatus xmlns:d=\"DAV:\">"
                + "<d:response>"
                + "<d:href>" + escapeXml(href) + "</d:href>"
                + "<d:propstat><d:prop>"
                + "<d:displayname>" + escapeXml(document.getTitle()) + "</d:displayname>"
                + "<d:getcontentlength>" + size + "</d:getcontentlength>"
                + "<d:getlastmodified>" + formatDate(document.getUpdatedAt()) + "</d:getlastmodified>"
                + "<d:getcontenttype>" + VersionService.DOCX_CONTENT_TYPE + "</d:getcontenttype>"
                + "<d:getetag>\"" + escapeXml(etag == null ? "" : etag) + "\"</d:getetag>"
                + "<d:resourcetype/>"
                + "</d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat>"
                + "</d:response>"
                + "</d:multistatus>";

        response.setStatus(207);
        response.setHeader("DAV", "1,2");
        response.setContentType("application/xml;charset=UTF-8");
        response.getWriter().write(xml);
    }

    private void handleLock(String rawDocId,
                            HttpServletRequest request,
                            HttpServletResponse response) throws IOException {
        String token = "opaquelocktoken:" + UUID.randomUUID();
        Duration ttl = parseTimeout(request.getHeader("Timeout"));
        Boolean ok = redis.opsForValue()
                .setIfAbsent(LOCK_PREFIX + rawDocId, token, ttl);
        if (Boolean.FALSE.equals(ok)) {
            response.setStatus(423); // Locked
            response.setContentType("application/xml;charset=UTF-8");
            response.getWriter().write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                    + "<d:prop xmlns:d=\"DAV:\"><d:lockdiscovery/></d:prop>");
            return;
        }

        String href = request.getContextPath() + "/word-sync/webdav/" + rawDocId;
        String timeout = "Second-" + ttl.getSeconds();
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<d:prop xmlns:d=\"DAV:\">"
                + "<d:lockdiscovery><d:activelock>"
                + "<d:locktype><d:write/></d:locktype>"
                + "<d:lockscope><d:exclusive/></d:lockscope>"
                + "<d:depth>infinity</d:depth>"
                + "<d:owner><d:href>" + escapeXml(request.getRemoteUser() == null ? "word-sync" : request.getRemoteUser()) + "</d:href></d:owner>"
                + "<d:timeout>" + timeout + "</d:timeout>"
                + "<d:locktoken><d:href>" + escapeXml(token) + "</d:href></d:locktoken>"
                + "<d:lockroot><d:href>" + escapeXml(href) + "</d:href></d:lockroot>"
                + "</d:activelock></d:lockdiscovery></d:prop>";

        response.setHeader("Lock-Token", "<" + token + ">");
        response.setHeader("Timeout", timeout);
        response.setStatus(200);
        response.setContentType("application/xml;charset=UTF-8");
        response.getWriter().write(xml);
    }

    private void handleUnlock(String rawDocId,
                              HttpServletRequest request,
                              HttpServletResponse response) throws IOException {
        String token = request.getHeader("Lock-Token");
        String saved = redis.opsForValue().get(LOCK_PREFIX + rawDocId);
        if (token != null && saved != null && token.contains(saved)) {
            redis.delete(LOCK_PREFIX + rawDocId);
            response.setStatus(204);
        } else {
            response.setStatus(409);
        }
    }

    private Duration parseTimeout(String header) {
        if (header == null || header.isBlank()) {
            return DEFAULT_LOCK_TTL;
        }
        for (String part : header.split(",")) {
            String value = part.trim();
            if (value.startsWith("Second-")) {
                try {
                    long seconds = Long.parseLong(value.substring("Second-".length()));
                    if (seconds > 0) {
                        return Duration.ofSeconds(Math.min(seconds, 86400));
                    }
                } catch (NumberFormatException ignored) {
                    // 使用默认值
                }
            }
        }
        return DEFAULT_LOCK_TTL;
    }

    private String formatDate(LocalDateTime value) {
        if (value == null) {
            return "";
        }
        ZonedDateTime zoned = value.atZone(ZoneId.systemDefault())
                .withZoneSameInstant(ZoneOffset.UTC);
        return HTTP_DATE.format(zoned);
    }

    private String escapeXml(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
