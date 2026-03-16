package com.emby.mvp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.emby.mvp.entity.Actor;
import com.emby.mvp.entity.Category;
import com.emby.mvp.entity.MediaActor;
import com.emby.mvp.entity.MediaCategory;
import com.emby.mvp.entity.MediaItem;
import com.emby.mvp.mapper.ActorMapper;
import com.emby.mvp.mapper.CategoryMapper;
import com.emby.mvp.mapper.MediaActorMapper;
import com.emby.mvp.mapper.MediaCategoryMapper;
import com.emby.mvp.mapper.MediaItemMapper;
import com.emby.mvp.service.LogService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class JavMetadataServiceImpl {
    private static final Pattern CODE_PATTERN = Pattern.compile("([A-Z]{2,8})[-_\\s]?(\\d{2,6})", Pattern.CASE_INSENSITIVE);

    private final RestTemplate restTemplate = new RestTemplate();
    private final MediaItemMapper mediaItemMapper;
    private final ActorMapper actorMapper;
    private final CategoryMapper categoryMapper;
    private final MediaActorMapper mediaActorMapper;
    private final MediaCategoryMapper mediaCategoryMapper;
    private final LogService logService;

    @Value("${app.media.poster-dir}")
    private String posterDir;

    @Value("${app.jav-meta.base-url:https://tools.miku.ac}")
    private String baseUrl;

    public JavMetadataServiceImpl(MediaItemMapper mediaItemMapper,
                                  ActorMapper actorMapper,
                                  CategoryMapper categoryMapper,
                                  MediaActorMapper mediaActorMapper,
                                  MediaCategoryMapper mediaCategoryMapper,
                                  LogService logService) {
        this.mediaItemMapper = mediaItemMapper;
        this.actorMapper = actorMapper;
        this.categoryMapper = categoryMapper;
        this.mediaActorMapper = mediaActorMapper;
        this.mediaCategoryMapper = mediaCategoryMapper;
        this.logService = logService;
    }

    public Map<String, Integer> scanAndSave(int limit) {
        return scanAndSave(limit, null);
    }

    public Map<String, Integer> scanAndSave(int limit, String scanField) {
        int realLimit = Math.max(1, Math.min(limit, 500));
        List<MediaItem> items = mediaItemMapper.selectList(new LambdaQueryWrapper<MediaItem>()
                .and(w -> w.isNull(MediaItem::getCode)
                        .or().eq(MediaItem::getCode, "")
                        .or().isNull(MediaItem::getIssueDate))
                .orderByDesc(MediaItem::getUpdatedAt)
                .last("limit " + realLimit));

        return scanItems(items, scanField);
    }

    public Map<String, Integer> scanAndSaveByIds(List<Long> mediaIds, String scanField) {
        if (mediaIds == null || mediaIds.isEmpty()) {
            return Map.of("total", 0, "success", 0, "failed", 0);
        }
        List<MediaItem> items = mediaItemMapper.selectList(new LambdaQueryWrapper<MediaItem>()
                .in(MediaItem::getId, mediaIds));
        return scanItems(items, scanField);
    }

    public List<Map<String, Object>> listScanCandidates(int limit) {
        int realLimit = Math.max(1, Math.min(limit, 1000));
        List<MediaItem> items = mediaItemMapper.selectList(new LambdaQueryWrapper<MediaItem>()
                .select(MediaItem::getId, MediaItem::getTitle, MediaItem::getCode)
                .orderByDesc(MediaItem::getUpdatedAt)
                .last("limit " + realLimit));

        List<Map<String, Object>> out = new ArrayList<>();
        for (MediaItem item : items) {
            out.add(Map.of(
                    "id", item.getId(),
                    "title", item.getTitle() == null ? "" : item.getTitle(),
                    "code", item.getCode() == null ? "" : item.getCode()
            ));
        }
        return out;
    }

    private Map<String, Integer> scanItems(List<MediaItem> items, String scanField) {
        AtomicInteger ok = new AtomicInteger();
        AtomicInteger fail = new AtomicInteger();
        for (MediaItem item : items) {
            try {
                if (enrichFromJavApi(item, scanField)) ok.incrementAndGet();
                else fail.incrementAndGet();
            } catch (Exception e) {
                fail.incrementAndGet();
            }
        }
        return Map.of(
                "total", items.size(),
                "success", ok.get(),
                "failed", fail.get()
        );
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean enrichFromJavApi(MediaItem item) {
        return enrichFromJavApi(item, null);
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean enrichFromJavApi(MediaItem item, String scanField) {
        if (item == null || item.getId() == null) return false;

        String code = resolveCodeByScanField(item, scanField);
        if (code == null) return false;

        Map<String, Object> infoData = fetchInfoData(code);
        if (infoData == null) return false;

        String apiTitle = asString(infoData.get("title"));
        if (apiTitle != null && !apiTitle.isBlank()) {
            item.setTitle(apiTitle);
        }
        item.setCode(readCodeValue(infoData, code));

        String issueDate = readIssueDateValue(infoData);
        if (issueDate != null) {
            try {
                item.setIssueDate(LocalDate.parse(issueDate));
            } catch (Exception ignored) {}
        }

        Long firstActorId = syncActors(item.getId(), infoData);
        if (firstActorId != null) item.setActorId(firstActorId);
        syncCategories(item.getId(), infoData);

        if (downloadPoster(item.getId(), asString(infoData.get("cover")))) {
            item.setPosterUrl("/api/media/" + item.getId() + "/poster");
        }

        item.setUpdatedAt(LocalDateTime.now());
        mediaItemMapper.updateById(item);
        return true;
    }

    private Map<String, Object> fetchInfoData(String code) {
        List<String> urls = List.of(
                UriComponentsBuilder.fromHttpUrl(baseUrl + "/api/t/jav-search/info")
                        .queryParam("id", code)
                        .queryParam("source", "jp")
                        .build(true)
                        .toUriString(),
                UriComponentsBuilder.fromHttpUrl(baseUrl + "/api/t/jav-search/info")
                        .queryParam("id", code)
                        .queryParam("source", "jp")
                        .queryParam("type", "censored")
                        .build(true)
                        .toUriString()
        );

        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.USER_AGENT, "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36");
        headers.set(HttpHeaders.ACCEPT, "application/json,text/plain,*/*");
        headers.set(HttpHeaders.ACCEPT_LANGUAGE, "zh-CN,zh;q=0.9,en;q=0.8");
        headers.set(HttpHeaders.REFERER, "https://tools.miku.ac/");
        headers.set(HttpHeaders.ORIGIN, "https://tools.miku.ac");

        for (String url : urls) {
            try {
                ResponseEntity<Map> resp = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), Map.class);
                if (!resp.getStatusCode().is2xxSuccessful() || resp.getBody() == null) continue;
                Object data = resp.getBody().get("data");
                if (data instanceof Map<?, ?> dataMap) {
                    return (Map<String, Object>) dataMap;
                }
            } catch (Exception e) {
                logService.write("JAV_META", "抓取失败 code=" + code + ", url=" + url + ", msg=" + e.getMessage());
            }
        }
        return null;
    }

    private Long syncActors(Long mediaId, Map<String, Object> infoData) {
        mediaActorMapper.delete(new LambdaQueryWrapper<MediaActor>().eq(MediaActor::getMediaId, mediaId));

        List<Map<String, Object>> actors = asMapList(infoData.get("actor"));
        Long firstActorId = null;
        for (Map<String, Object> row : actors) {
            String name = asString(row.get("name"));
            if (name == null || name.isBlank()) continue;

            Actor actor = actorMapper.selectOne(new LambdaQueryWrapper<Actor>()
                    .eq(Actor::getName, name)
                    .last("limit 1"));
            if (actor == null) {
                actor = new Actor();
                actor.setName(name);
                actor.setCreatedAt(LocalDateTime.now());
            }
            String avatar = normalizeUrl(asString(row.get("avatar")));
            if (avatar != null && !avatar.isBlank()) actor.setAvatarUrl(avatar);
            actor.setUpdatedAt(LocalDateTime.now());

            if (actor.getId() == null) actorMapper.insert(actor);
            else actorMapper.updateById(actor);

            MediaActor rel = new MediaActor();
            rel.setMediaId(mediaId);
            rel.setActorId(actor.getId());
            rel.setCreatedAt(LocalDateTime.now());
            mediaActorMapper.insert(rel);

            if (firstActorId == null) firstActorId = actor.getId();
        }
        return firstActorId;
    }

    private void syncCategories(Long mediaId, Map<String, Object> infoData) {
        mediaCategoryMapper.delete(new LambdaQueryWrapper<MediaCategory>().eq(MediaCategory::getMediaId, mediaId));

        String categoriesText = readCategoryValue(infoData);
        if (categoriesText == null || categoriesText.isBlank()) return;
        List<String> names = splitCategories(categoriesText);

        for (String name : names) {
            Category category = categoryMapper.selectOne(new LambdaQueryWrapper<Category>()
                    .eq(Category::getName, name)
                    .last("limit 1"));
            if (category == null) {
                category = new Category();
                category.setName(name);
                category.setCreatedAt(LocalDateTime.now());
            }
            category.setUpdatedAt(LocalDateTime.now());
            if (category.getId() == null) categoryMapper.insert(category);
            else categoryMapper.updateById(category);

            MediaCategory rel = new MediaCategory();
            rel.setMediaId(mediaId);
            rel.setCategoryId(category.getId());
            rel.setCreatedAt(LocalDateTime.now());
            mediaCategoryMapper.insert(rel);
        }
    }

    private boolean downloadPoster(Long mediaId, String cover) {
        String coverUrl = normalizeUrl(cover);
        if (coverUrl == null || coverUrl.isBlank()) return false;

        try {
            byte[] bytes = restTemplate.getForObject(coverUrl, byte[].class);
            if (bytes == null || bytes.length == 0) return false;
            Path root = Paths.get(posterDir).toAbsolutePath().normalize();
            Files.createDirectories(root);
            Files.write(root.resolve(mediaId + ".jpg"), bytes);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private String readInfoValue(Map<String, Object> infoData, String name, String fallback) {
        List<Map<String, Object>> infos = asMapList(infoData.get("info"));
        for (Map<String, Object> row : infos) {
            if (name.equals(asString(row.get("name")))) {
                String content = asString(row.get("content"));
                return content == null || content.isBlank() ? fallback : content.trim();
            }
        }
        return fallback;
    }

    private String readCodeValue(Map<String, Object> infoData, String fallback) {
        String exact = readInfoValue(infoData, "識別碼", null);
        if (exact != null && !exact.isBlank()) return exact;

        for (Map<String, Object> row : asMapList(infoData.get("info"))) {
            String name = asString(row.get("name"));
            String content = asString(row.get("content"));
            if (content == null || content.isBlank()) continue;
            if (name != null && (name.contains("識別") || name.contains("识别") || name.toLowerCase(Locale.ROOT).contains("id"))) {
                return content.trim();
            }
            String extracted = extractCode(content);
            if (extracted != null) return extracted;
        }
        return fallback;
    }

    private String readIssueDateValue(Map<String, Object> infoData) {
        String exact = readInfoValue(infoData, "發行日期", null);
        if (exact != null && !exact.isBlank()) return exact;

        Pattern datePattern = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");
        for (Map<String, Object> row : asMapList(infoData.get("info"))) {
            String name = asString(row.get("name"));
            String content = asString(row.get("content"));
            if (content == null) continue;
            if (name != null && (name.contains("發行") || name.contains("发行") || name.contains("日期") || name.toLowerCase(Locale.ROOT).contains("date"))) {
                Matcher m = datePattern.matcher(content);
                if (m.find()) return m.group();
            }
            Matcher m = datePattern.matcher(content);
            if (m.find()) return m.group();
        }
        return null;
    }

    private String readCategoryValue(Map<String, Object> infoData) {
        String exact = readInfoValue(infoData, "類別", null);
        if (exact != null && !exact.isBlank()) return exact;

        for (Map<String, Object> row : asMapList(infoData.get("info"))) {
            String name = asString(row.get("name"));
            String content = asString(row.get("content"));
            if (content == null || content.isBlank()) continue;
            if (name != null && (name.contains("類別") || name.contains("类别") || name.contains("分類") || name.contains("分类") || name.toLowerCase(Locale.ROOT).contains("category"))) {
                return content.trim();
            }
        }
        return null;
    }

    private List<String> splitCategories(String text) {
        if (text == null || text.isBlank()) return List.of();
        String normalized = text.replace('、', ' ').replace(',', ' ').replace('，', ' ').trim();
        List<String> list = new ArrayList<>();
        for (String part : normalized.split("\\s+")) {
            String p = part == null ? "" : part.trim();
            if (!p.isEmpty()) list.add(p);
        }
        return list;
    }

    private String extractCode(String... texts) {
        if (texts == null) return null;
        for (String text : texts) {
            if (text == null) continue;
            Matcher m = CODE_PATTERN.matcher(text.toUpperCase(Locale.ROOT));
            if (m.find()) return m.group(1) + "-" + m.group(2);
        }
        return null;
    }

    private String resolveCodeByScanField(MediaItem item, String scanField) {
        String field = scanField == null ? "" : scanField.trim().toLowerCase(Locale.ROOT);
        if ("title".equals(field)) {
            return extractCode(item.getTitle());
        }
        if ("code".equals(field)) {
            return extractCode(item.getCode());
        }
        return extractCode(item.getCode(), item.getTitle(), item.getFilePath());
    }

    private String normalizeUrl(String url) {
        if (url == null || url.isBlank()) return null;
        if (url.startsWith("http://") || url.startsWith("https://")) return url;
        if (url.startsWith("/")) return baseUrl + url;
        return baseUrl + "/" + url;
    }

    private String asString(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private List<Map<String, Object>> asMapList(Object obj) {
        if (!(obj instanceof List<?> list)) return List.of();
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> m) {
                out.add((Map<String, Object>) m);
            }
        }
        return out;
    }
}
