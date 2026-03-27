package com.emby.mvp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.emby.mvp.common.BizException;
import com.emby.mvp.entity.MediaItem;
import com.emby.mvp.entity.MediaSubtitle;
import com.emby.mvp.mapper.MediaSubtitleMapper;
import com.emby.mvp.service.LogService;
import com.emby.mvp.service.MediaService;
import com.emby.mvp.service.SubtitleService;
import org.springframework.beans.factory.annotation.Value;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
public class SubtitleServiceImpl implements SubtitleService {
    private static final Pattern CODE_PATTERN = Pattern.compile("([A-Z]{2,8})[-_\\s]?(\\d{2,6})", Pattern.CASE_INSENSITIVE);
    private static final Pattern SUBS_HREF_PATTERN = Pattern.compile("/subs/", Pattern.CASE_INSENSITIVE);

    private final MediaSubtitleMapper mediaSubtitleMapper;
    private final MediaService mediaService;
    private final LogService logService;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${app.subtitle.base-url:https://subtitlecat.com}")
    private String subtitleBaseUrl;

    @Value("${app.subtitle.save-dir:subtitles}")
    private String subtitleSaveDir;

    public SubtitleServiceImpl(MediaSubtitleMapper mediaSubtitleMapper, MediaService mediaService, LogService logService) {
        this.mediaSubtitleMapper = mediaSubtitleMapper;
        this.mediaService = mediaService;
        this.logService = logService;
    }

    @Override
    public MediaSubtitle fetchOrDownload(Long mediaId, String title, List<String> preferredLangs, String langCode) {
        MediaItem media = mediaService.getById(mediaId);

        String requestedLang = normalizeRequestedLang(langCode);

        LambdaQueryWrapper<MediaSubtitle> query = new LambdaQueryWrapper<MediaSubtitle>()
                .eq(MediaSubtitle::getMediaId, mediaId);
        if (!"AUTO".equals(requestedLang)) {
            query.eq(MediaSubtitle::getLanguage, requestedLang);
        }
        MediaSubtitle existing = mediaSubtitleMapper.selectOne(query
                .orderByDesc(MediaSubtitle::getUpdatedAt)
                .last("limit 1"));

        if (existing != null && existing.getFilePath() != null && Files.exists(Paths.get(existing.getFilePath()))) {
            return existing;
        }

        String titleToUse = (title == null || title.isBlank()) ? media.getTitle() : title;
        String code = extractCode(titleToUse);
        if (code == null) throw new BizException(4004, "cannot extract video code from title");

        String detailUrl = findDetailPageUrl(code);
        if (detailUrl == null) throw new BizException(4046, "subtitle detail page not found");

        String detailHtml = fetchText(detailUrl);
        String downloadUrl = findDownloadUrl(detailHtml, preferredLangs);
        if (downloadUrl == null) throw new BizException(4047, "subtitle download link not found");

        DownloadResult result = downloadSubtitle(downloadUrl);
        Path saveDir = Paths.get(subtitleSaveDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(saveDir);
            String ext = result.ext == null ? "srt" : result.ext;
            Path filePath = saveDir.resolve(mediaId + "-" + code + "." + ext);
            Files.write(filePath, result.content);

            MediaSubtitle subtitle = existing == null ? new MediaSubtitle() : existing;
            subtitle.setMediaId(mediaId);
            subtitle.setVideoTitle(titleToUse);
            subtitle.setCode(code);
            subtitle.setLanguage(resolveLanguage(result.language, requestedLang));
            subtitle.setFilePath(filePath.toString());
            subtitle.setUpdatedAt(LocalDateTime.now());
            if (subtitle.getCreatedAt() == null) subtitle.setCreatedAt(LocalDateTime.now());

            if (subtitle.getId() == null) mediaSubtitleMapper.insert(subtitle);
            else mediaSubtitleMapper.updateById(subtitle);

            logService.write("SUBTITLE", "字幕已缓存 mediaId=" + mediaId + ", lang=" + subtitle.getLanguage());
            return subtitle;
        } catch (Exception e) {
            throw new BizException(5004, "save subtitle failed: " + e.getMessage());
        }
    }

    private String extractCode(String title) {
        if (title == null) return null;
        Matcher m = CODE_PATTERN.matcher(title.toUpperCase(Locale.ROOT));
        if (!m.find()) return null;
        return m.group(1) + "-" + m.group(2);
    }

    private String findDetailPageUrl(String code) {
        String url = subtitleBaseUrl + "/index.php?search=" + code;
        String html = fetchText(url);
        Document doc = Jsoup.parse(html, subtitleBaseUrl);

        Element table = doc.selectFirst("table.sub-table");
        String fallback = null;

        if (table != null) {
            for (Element row : table.select("tr")) {
                Element link = row.selectFirst("a[href]");
                if (link == null) continue;
                String href = link.attr("href");
                String text = link.text().trim().toUpperCase(Locale.ROOT);
                if (fallback == null && SUBS_HREF_PATTERN.matcher(href).find()) {
                    fallback = normalizeUrl(href);
                }
                if (text.contains(code)) {
                    return normalizeUrl(href);
                }
            }
        }

        if (fallback != null) return fallback;

        for (Element a : doc.select("a[href*=/subs/], a[href*=subs/]") ) {
            String href = a.attr("href");
            if (href.toLowerCase(Locale.ROOT).contains(code.toLowerCase(Locale.ROOT))) {
                return normalizeUrl(href);
            }
            if (fallback == null) fallback = normalizeUrl(href);
        }
        return fallback;
    }

    private String findDownloadUrl(String detailHtml, List<String> preferredLangs) {
        List<String> langs = preferredLangs == null || preferredLangs.isEmpty()
                ? Arrays.asList("Chinese (Simplified)", "Chinese (Traditional)", "Mandarin", "Chinese", "English")
                : preferredLangs;

        Document doc = Jsoup.parse(detailHtml, subtitleBaseUrl);

        for (String lang : langs) {
            for (Element node : doc.getElementsContainingOwnText(lang)) {
                Element container = node.closest("tr,div,li");
                if (container == null) continue;
                Element btn = container.selectFirst("a:matchesOwn((?i)download)");
                if (btn != null && btn.hasAttr("href")) {
                    return normalizeUrl(btn.attr("href"));
                }
            }
        }

        Element any = doc.selectFirst("a:matchesOwn((?i)download)");
        if (any != null && any.hasAttr("href")) {
            return normalizeUrl(any.attr("href"));
        }
        return null;
    }

    private DownloadResult downloadSubtitle(String downloadUrl) {
        try {
            HttpHeaders headers = buildBrowserHeaders();
            ResponseEntity<byte[]> resp = restTemplate.exchange(URI.create(downloadUrl), HttpMethod.GET, new HttpEntity<>(headers), byte[].class);
            byte[] body = resp.getBody();
            if (body == null || body.length == 0) throw new BizException(4048, "subtitle file empty");

            String contentType = Optional.ofNullable(resp.getHeaders().getContentType()).map(Object::toString).orElse("");
            String ext = guessExt(downloadUrl, contentType);
            if ("zip".equals(ext)) {
                byte[] extracted = unzipFirstSubtitle(body);
                if (extracted == null) throw new BizException(4049, "zip subtitle has no supported file");
                return new DownloadResult(extracted, "srt", detectLanguage(downloadUrl));
            }
            return new DownloadResult(body, ext, detectLanguage(downloadUrl));
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(5005, "download subtitle failed: " + e.getMessage());
        }
    }

    private byte[] unzipFirstSubtitle(byte[] zipBytes) {
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName().toLowerCase(Locale.ROOT);
                if (name.endsWith(".srt") || name.endsWith(".vtt") || name.endsWith(".ass")) {
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    byte[] buf = new byte[8192];
                    int len;
                    while ((len = zis.read(buf)) > 0) out.write(buf, 0, len);
                    return out.toByteArray();
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private String fetchText(String url) {
        try {
            HttpHeaders headers = buildBrowserHeaders();
            ResponseEntity<String> resp = restTemplate.exchange(URI.create(url), HttpMethod.GET, new HttpEntity<>(headers), String.class);
            if (!resp.getStatusCode().is2xxSuccessful() || resp.getBody() == null) {
                throw new BizException(5006, "request subtitle site failed");
            }
            return resp.getBody();
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(5006, "request subtitle site failed: " + e.getMessage());
        }
    }

    private HttpHeaders buildBrowserHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.USER_AGENT, "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36");
        headers.set(HttpHeaders.ACCEPT, "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
        headers.set("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8");
        headers.set("Cache-Control", "no-cache");
        return headers;
    }

    private String normalizeUrl(String href) {
        if (href == null) return null;
        if (href.startsWith("http://") || href.startsWith("https://")) return href;
        if (href.startsWith("/")) return subtitleBaseUrl + href;
        return subtitleBaseUrl + "/" + href;
    }

    private String detectLanguage(String text) {
        String lower = text == null ? "" : text.toLowerCase(Locale.ROOT);
        if (lower.contains("japanese") || lower.contains("日本") || lower.contains("\u65e5\u8a9e") || lower.contains(" ja") || lower.endsWith("ja")) return "JA";
        if (lower.contains("chinese") || lower.contains("mandarin") || lower.contains("zh")) return "ZH";
        if (lower.contains("english") || lower.contains("en")) return "EN";
        return "UNKNOWN";
    }

    private String normalizeRequestedLang(String langCode) {
        String code = langCode == null ? "AUTO" : langCode.trim().toUpperCase(Locale.ROOT);
        if (code.isEmpty()) return "AUTO";
        if (code.startsWith("ZH") || "CN".equals(code)) return "ZH";
        if (code.startsWith("EN")) return "EN";
        if (code.startsWith("JA") || "JP".equals(code)) return "JA";
        if ("AUTO".equals(code)) return "AUTO";
        return code;
    }

    private String resolveLanguage(String detected, String requestedLang) {
        if (detected != null && !detected.isBlank() && !"UNKNOWN".equalsIgnoreCase(detected)) {
            return detected.toUpperCase(Locale.ROOT);
        }
        if (requestedLang != null && !requestedLang.isBlank() && !"AUTO".equalsIgnoreCase(requestedLang)) {
            return requestedLang.toUpperCase(Locale.ROOT);
        }
        return "UNKNOWN";
    }

    private String guessExt(String url, String contentType) {
        String lowerUrl = url == null ? "" : url.toLowerCase(Locale.ROOT);
        String lowerCt = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        if (lowerUrl.endsWith(".zip") || lowerCt.contains("zip")) return "zip";
        if (lowerUrl.endsWith(".vtt") || lowerCt.contains("vtt")) return "vtt";
        if (lowerUrl.endsWith(".ass")) return "ass";
        return "srt";
    }

    private record DownloadResult(byte[] content, String ext, String language) {}
}
