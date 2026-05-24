package com.lightbot.util;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;

/**
 * Web URL 内容抓取工具
 *
 * @author finch
 * @since 2026-05-24
 */
@Slf4j
@Component
public class WebFetchUtil {

    /** 请求超时时间（毫秒） */
    private static final int TIMEOUT_MS = 30000;

    /** 最大内容长度（字符），防止抓取过大页面 */
    private static final int MAX_CONTENT_LENGTH = 500000;

    /**
     * 抓取 URL 内容，提取正文文本
     *
     * @param url URL 地址
     * @return 抓取结果（含标题、内容、来源URL）
     */
    public FetchResult fetch(String url) {
        log.info("[WebFetch] 开始抓取: url={}", url);

        // 1. 校验 URL 格式
        if (!isValidUrl(url)) {
            throw new IllegalArgumentException("无效的 URL 格式: " + url);
        }

        // 2. 抓取网页
        Document doc;
        try {
            doc = Jsoup.connect(url)
                    .timeout(TIMEOUT_MS)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .followRedirects(true)
                    .maxBodySize(MAX_CONTENT_LENGTH * 2) // 字节数限制
                    .get();
        } catch (IOException e) {
            log.error("[WebFetch] 抓取失败: url={}, error={}", url, e.getMessage());
            throw new RuntimeException("网页抓取失败: " + e.getMessage());
        }

        // 3. 提取标题
        String title = extractTitle(doc);

        // 4. 提取正文内容
        String content = extractContent(doc);

        // 5. 限制内容长度
        if (content.length() > MAX_CONTENT_LENGTH) {
            content = content.substring(0, MAX_CONTENT_LENGTH) + "\n...(内容过长已截断)";
        }

        log.info("[WebFetch] 抓取完成: url={}, title={}, contentLength={}", url, title, content.length());

        return new FetchResult(url, title, content);
    }

    /**
     * 校验 URL 格式
     */
    private boolean isValidUrl(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        try {
            URI uri = new URI(url);
            String scheme = uri.getScheme();
            return scheme != null && (scheme.equals("http") || scheme.equals("https"));
        } catch (URISyntaxException e) {
            return false;
        }
    }

    /**
     * 提取网页标题
     */
    private String extractTitle(Document doc) {
        // 优先使用 <title> 标签
        String title = doc.title();
        if (title != null && !title.isBlank()) {
            return title.trim();
        }

        // 回退：使用 <h1> 标签
        Element h1 = doc.selectFirst("h1");
        if (h1 != null) {
            return h1.text().trim();
        }

        // 回退：使用 og:title meta
        Element ogTitle = doc.selectFirst("meta[property=og:title]");
        if (ogTitle != null) {
            return ogTitle.attr("content").trim();
        }

        return "未命名网页";
    }

    /**
     * 提取正文内容
     * 策略：移除导航、脚本、样式等无关元素，提取正文区域的纯文本
     */
    private String extractContent(Document doc) {
        // 1. 移除无关元素
        doc.select("script, style, nav, header, footer, aside, iframe, noscript, form, button, input, select, textarea, svg, img").remove();

        // 2. 移除导航类 class/id
        doc.select("[class~=nav|menu|sidebar|footer|header|advertisement|ads|banner|social|share|comment|related]").remove();
        doc.select("[id~=nav|menu|sidebar|footer|header|advertisement|ads|banner|social|share|comment|related]").remove();

        // 3. 尝试定位主要内容区域（常见的内容容器 class/id）
        Element contentContainer = doc.selectFirst("article, main, [role=main], #content, #main, .content, .main, .post, .article, .entry, .post-content, .article-content");

        String content;
        if (contentContainer != null) {
            content = contentContainer.text();
        } else {
            // 回退：提取 <body> 下的全部文本
            Element body = doc.body();
            if (body != null) {
                content = body.text();
            } else {
                content = doc.text();
            }
        }

        // 4. 清理多余空白
        content = content.replaceAll("\\s+", " ").trim();

        return content;
    }

    /**
     * 抓取结果
     */
    public static class FetchResult {
        private final String url;
        private final String title;
        private final String content;
        private final long fetchedAt;

        public FetchResult(String url, String title, String content) {
            this.url = url;
            this.title = title;
            this.content = content;
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

        public long getFetchedAt() {
            return fetchedAt;
        }

        /**
         * 生成文件名（从标题或 URL）
         */
        public String generateFileName() {
            String name = title;
            if (name == null || name.isBlank() || name.equals("未命名网页")) {
                // 从 URL 提取路径作为文件名
                try {
                    URI uri = new URI(url);
                    String path = uri.getPath();
                    if (path != null && !path.isBlank() && !path.equals("/")) {
                        // 移除前导斜杠和后缀
                        name = path.replaceAll("^/+|/$", "").replaceAll("/", "_");
                    } else {
                        name = uri.getHost();
                    }
                } catch (URISyntaxException e) {
                    name = "web_content";
                }
            }
            // 清理非法字符
            name = name.replaceAll("[\\\\/:*?\"<>|]", "_");
            // 限制长度
            if (name.length() > 100) {
                name = name.substring(0, 100);
            }
            return name + ".txt";
        }
    }
}