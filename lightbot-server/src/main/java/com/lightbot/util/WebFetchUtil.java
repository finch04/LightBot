package com.lightbot.util;

import com.lightbot.common.BizException;
import com.lightbot.enums.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.safety.Safelist;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Web URL 内容抓取工具（多策略正文提取 + 预览 HTML）
 *
 * @author finch
 * @since 2026-05-24
 */
@Slf4j
@Component
public class WebFetchUtil {

    private static final int TIMEOUT_MS = 30000;
    private static final int MAX_CONTENT_LENGTH = 500000;
    private static final int MAX_PREVIEW_HTML_LENGTH = 200000;

    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36";

    private static final String[] CONTENT_SELECTORS = {
            "article",
            "main",
            "[role=main]",
            "#content",
            "#main",
            "#article",
            "#post",
            ".article",
            ".post-content",
            ".article-content",
            ".entry-content",
            ".content",
            ".main",
            ".post",
            ".markdown-body",
            ".rich-text",
            ".detail-content",
            ".news-content",
            ".text-content"
    };

    private static final Pattern BOILERPLATE_CLASS = Pattern.compile(
            "nav|menu|sidebar|footer|header|advert|ads|banner|social|share|comment|related|breadcrumb|toolbar|popup|modal|cookie",
            Pattern.CASE_INSENSITIVE);

    /**
     * 抓取 URL 内容
     *
     * @param url URL 地址
     * @return 抓取结果
     */
    public FetchResult fetch(String url) {
        return fetch(url, null);
    }

    /**
     * 抓取 URL 内容（支持自定义请求头）
     *
     * @param url            URL 地址
     * @param customHeaders  自定义请求头（可为 null）
     * @return 抓取结果
     */
    public FetchResult fetch(String url, Map<String, String> customHeaders) {
        log.info("[WebFetch] 开始抓取: url={}", url);

        if (!isValidUrl(url)) {
            throw new IllegalArgumentException("无效的 URL 格式: " + url);
        }

        Document doc = loadDocument(url, customHeaders);
        String title = extractTitle(doc);
        String description = extractDescription(doc);
        Element mainContent = locateMainContent(doc);
        String content = extractStructuredText(mainContent != null ? mainContent : doc.body());
        String previewHtml = buildPreviewHtml(mainContent != null ? mainContent : doc.body());

        if (content == null || content.isBlank()) {
            throw new BizException(ErrorCode.DOCUMENT_URL_NO_CONTENT);
        }

        if (content.length() > MAX_CONTENT_LENGTH) {
            content = content.substring(0, MAX_CONTENT_LENGTH) + "\n\n...(内容过长已截断)";
        }
        if (previewHtml.length() > MAX_PREVIEW_HTML_LENGTH) {
            previewHtml = previewHtml.substring(0, MAX_PREVIEW_HTML_LENGTH) + "<p>...(预览内容过长已截断)</p>";
        }

        log.info("[WebFetch] 抓取完成: url={}, title={}, contentLength={}", url, title, content.length());
        return new FetchResult(url, title, content, previewHtml, description);
    }

    private Document loadDocument(String url, Map<String, String> customHeaders) {
        try {
            var conn = Jsoup.connect(url)
                    .timeout(TIMEOUT_MS)
                    .userAgent(USER_AGENT)
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                    .referrer(url)
                    .followRedirects(true)
                    .ignoreHttpErrors(true)
                    .ignoreContentType(true)
                    .maxBodySize(MAX_CONTENT_LENGTH * 4);
            // 叠加用户自定义 headers（覆盖默认值）
            if (customHeaders != null && !customHeaders.isEmpty()) {
                customHeaders.forEach(conn::header);
            }
            return conn.get();
        } catch (IOException e) {
            log.error("[WebFetch] 抓取失败: url={}, error={}", url, e.getMessage());
            throw new RuntimeException("网页抓取失败: " + e.getMessage());
        }
    }

    private static final Set<String> ALLOWED_SCHEMES = Set.of("http", "https");

    /**
     * 校验 URL 合法性：协议/端口白名单 + DNS 解析后拒绝内网/私有 IP
     */
    private boolean isValidUrl(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        try {
            URI uri = new URI(url.trim());
            String scheme = uri.getScheme();
            if (scheme == null || !ALLOWED_SCHEMES.contains(scheme.toLowerCase())) {
                return false;
            }
            int port = uri.getPort();
            if (port != -1 && port != 80 && port != 443) {
                log.warn("[WebFetch] 拒绝非标准端口: port={}", port);
                return false;
            }
            String host = uri.getHost();
            if (host == null || host.isBlank()) {
                return false;
            }
            // DNS 解析后校验 IP 是否为内网/私有地址
            validateNotInternalIp(host);
            return true;
        } catch (URISyntaxException e) {
            return false;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.warn("[WebFetch] URL 校验异常: {}", e.getMessage());
            return false;
        }
    }

    /**
     * DNS 解析后拒绝内网/私有/保留 IP，防止 SSRF
     */
    private void validateNotInternalIp(String host) {
        try {
            InetAddress addr = InetAddress.getByName(host);
            if (addr.isLoopbackAddress() || addr.isSiteLocalAddress()
                    || addr.isLinkLocalAddress() || addr.isAnyLocalAddress()) {
                log.warn("[WebFetch] 拒绝内网地址: host={}, ip={}", host, addr.getHostAddress());
                throw new BizException(ErrorCode.SSRF_BLOCKED);
            }
            byte[] ip = addr.getAddress();
            // 169.254.x.x — 云元数据端点（AWS/GCP/Azure）
            if (ip.length == 4 && (ip[0] & 0xFF) == 169 && (ip[1] & 0xFF) == 254) {
                log.warn("[WebFetch] 拒绝元数据地址: host={}, ip={}", host, addr.getHostAddress());
                throw new BizException(ErrorCode.SSRF_BLOCKED);
            }
        } catch (UnknownHostException e) {
            log.warn("[WebFetch] DNS 解析失败: host={}", host);
            throw new BizException(ErrorCode.DOCUMENT_URL_NO_CONTENT);
        }
    }

    private String extractTitle(Document doc) {
        Element ogTitle = doc.selectFirst("meta[property=og:title]");
        if (ogTitle != null && !ogTitle.attr("content").isBlank()) {
            return ogTitle.attr("content").trim();
        }
        String title = doc.title();
        if (title != null && !title.isBlank()) {
            return title.trim();
        }
        Element h1 = doc.selectFirst("h1");
        if (h1 != null && !h1.text().isBlank()) {
            return h1.text().trim();
        }
        return "未命名网页";
    }

    private String extractDescription(Document doc) {
        Element meta = doc.selectFirst("meta[name=description], meta[property=og:description]");
        if (meta != null && !meta.attr("content").isBlank()) {
            return meta.attr("content").trim();
        }
        return null;
    }

  /**
     * 定位正文容器：优先语义标签，其次按文本密度评分
     */
    private Element locateMainContent(Document doc) {
        removeBoilerplate(doc);

        for (String selector : CONTENT_SELECTORS) {
            Elements found = doc.select(selector);
            for (Element el : found) {
                if (el.text().trim().length() > 80) {
                    return el.clone();
                }
            }
        }

        Element body = doc.body();
        if (body == null) {
            return null;
        }

        Element best = null;
        int bestScore = 0;
        for (Element candidate : body.select("div, section, article, main")) {
            if (isBoilerplate(candidate)) {
                continue;
            }
            int score = scoreContentElement(candidate);
            if (score > bestScore) {
                bestScore = score;
                best = candidate;
            }
        }
        return best != null ? best.clone() : body.clone();
    }

    private void removeBoilerplate(Document doc) {
        doc.select("script, style, nav, header, footer, aside, iframe, noscript, form, svg, canvas, video, audio").remove();
        doc.select("[class*=comment], [id*=comment], [class*=sidebar], [id*=sidebar]").remove();
    }

    private boolean isBoilerplate(Element el) {
        String cls = el.className();
        String id = el.id();
        return BOILERPLATE_CLASS.matcher(cls).find() || BOILERPLATE_CLASS.matcher(id).find();
    }

    /** 文本密度评分：正文越长、链接占比越低得分越高 */
    private int scoreContentElement(Element el) {
        String text = el.text().trim();
        if (text.length() < 50) {
            return 0;
        }
        int linkLen = 0;
        for (Element a : el.select("a")) {
            linkLen += a.text().length();
        }
        return text.length() - linkLen * 2;
    }

    /**
     * 将 DOM 转为保留结构的纯文本（标题/段落/列表）
     */
    private String extractStructuredText(Element root) {
        if (root == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        appendStructuredNode(root, sb, 0);
        return normalizeTextBlock(sb.toString());
    }

    private void appendStructuredNode(Node node, StringBuilder sb, int depth) {
        if (node instanceof TextNode textNode) {
            String t = textNode.text();
            if (!t.isBlank()) {
                sb.append(t);
            }
            return;
        }
        if (!(node instanceof Element el)) {
            return;
        }
        String tag = el.tagName().toLowerCase();
        switch (tag) {
            case "h1" -> appendBlock(sb, "# " + el.text().trim(), depth);
            case "h2" -> appendBlock(sb, "## " + el.text().trim(), depth);
            case "h3" -> appendBlock(sb, "### " + el.text().trim(), depth);
            case "h4", "h5", "h6" -> appendBlock(sb, "#### " + el.text().trim(), depth);
            case "p", "div", "section", "article", "blockquote" -> {
                String block = el.text().trim();
                if (!block.isBlank()) {
                    appendBlock(sb, block, depth);
                }
            }
            case "li" -> appendBlock(sb, "- " + el.text().trim(), depth);
            case "br" -> sb.append('\n');
            case "tr" -> {
                List<String> cells = new ArrayList<>();
                for (Element cell : el.select("th, td")) {
                    cells.add(cell.text().trim());
                }
                if (!cells.isEmpty()) {
                    appendBlock(sb, String.join(" | ", cells), depth);
                }
            }
            case "table" -> {
                for (Element child : el.children()) {
                    appendStructuredNode(child, sb, depth);
                }
                sb.append('\n');
            }
            default -> {
                for (Node child : el.childNodes()) {
                    appendStructuredNode(child, sb, depth);
                }
            }
        }
    }

    private void appendBlock(StringBuilder sb, String text, int depth) {
        if (text == null || text.isBlank()) {
            return;
        }
        if (!sb.isEmpty() && sb.charAt(sb.length() - 1) != '\n') {
            sb.append('\n');
        }
        sb.append(text.trim()).append("\n\n");
    }

    private String normalizeTextBlock(String text) {
        return text.replace("\r\n", "\n")
                .replaceAll("[ \\t\\f\\r]+", " ")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }

    private String buildPreviewHtml(Element root) {
        if (root == null) {
            return "";
        }
        Element clone = root.clone();
        clone.select("script, style, iframe, form, input, button, select, textarea, svg").remove();
        String raw = clone.html();
        return Jsoup.clean(raw, Safelist.relaxed()
                .addTags("article", "section", "header", "footer", "figure", "figcaption")
                .addAttributes(":all", "class", "id"));
    }

    /**
     * 抓取结果
     */
    public static class FetchResult {
        private final String url;
        private final String title;
        private final String content;
        private final String previewHtml;
        private final String description;
        private final long fetchedAt;

        public FetchResult(String url, String title, String content, String previewHtml, String description) {
            this.url = url;
            this.title = title;
            this.content = content;
            this.previewHtml = previewHtml;
            this.description = description;
            this.fetchedAt = System.currentTimeMillis();
        }

        public String getUrl() {
            return url;
        }

        public String getTitle() {
            return title;
        }

        public String getContent() {
            return content;
        }

        public String getPreviewHtml() {
            return previewHtml;
        }

        public String getDescription() {
            return description;
        }

        public long getFetchedAt() {
            return fetchedAt;
        }

        public String generateFileName() {
            String name = title;
            if (name == null || name.isBlank() || name.equals("未命名网页")) {
                try {
                    URI uri = new URI(url);
                    String path = uri.getPath();
                    if (path != null && !path.isBlank() && !path.equals("/")) {
                        name = path.replaceAll("^/+|/$", "").replaceAll("/", "_");
                    } else {
                        name = uri.getHost();
                    }
                } catch (URISyntaxException e) {
                    name = "web_content";
                }
            }
            name = name.replaceAll("[\\\\/:*?\"<>|]", "_");
            if (name.length() > 100) {
                name = name.substring(0, 100);
            }
            return name + ".txt";
        }
    }
}
