package com.emby.mvp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.emby.mvp.entity.MediaItem;
import com.emby.mvp.mapper.MediaItemMapper;
import com.emby.mvp.service.PosterService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class PosterServiceImpl implements PosterService {
    private final MediaItemMapper mediaItemMapper;

    @Value("${app.media.poster-dir}")
    private String posterDir;

    @Value("${app.tmdb.api-key:}")
    private String tmdbApiKey;

    @Value("${app.tmdb.image-base-url:https://image.tmdb.org/t/p/w500}")
    private String tmdbImageBaseUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    private final AtomicBoolean autoEnabled = new AtomicBoolean(false);
    private final AtomicInteger intervalMinutes = new AtomicInteger(60);

    public PosterServiceImpl(MediaItemMapper mediaItemMapper) {
        this.mediaItemMapper = mediaItemMapper;
    }

    @Override
    public Page<MediaItem> listMissing(int page, int size) {
        LambdaQueryWrapper<MediaItem> qw = new LambdaQueryWrapper<MediaItem>()
                .and(w -> w.isNull(MediaItem::getPosterUrl).or().eq(MediaItem::getPosterUrl, ""))
                .orderByDesc(MediaItem::getUpdatedAt);
        return mediaItemMapper.selectPage(new Page<>(page, size), qw);
    }

    @Override
    public int fetchMissing(int limit) {
        List<MediaItem> items = mediaItemMapper.selectList(new LambdaQueryWrapper<MediaItem>()
                .and(w -> w.isNull(MediaItem::getPosterUrl).or().eq(MediaItem::getPosterUrl, ""))
                .last("limit " + Math.max(1, limit)));

        Path posterRoot = Paths.get(posterDir).normalize().toAbsolutePath();
        try { Files.createDirectories(posterRoot); } catch (Exception ignored) {}

        int success = 0;
        for (MediaItem item : items) {
            try {
                if (downloadPosterFromTmdb(item.getTitle(), posterRoot.resolve(item.getId() + ".jpg"))) {
                    item.setPosterUrl("/api/media/" + item.getId() + "/poster");
                    item.setUpdatedAt(LocalDateTime.now());
                    mediaItemMapper.updateById(item);
                    success++;
                }
            } catch (Exception ignored) {}
        }
        return success;
    }

    @Override
    public Map<String, Object> getAutoConfig() {
        return Map.of(
                "enabled", autoEnabled.get(),
                "intervalMinutes", intervalMinutes.get()
        );
    }

    @Override
    public void updateAutoConfig(boolean enabled, int minutes) {
        autoEnabled.set(enabled);
        intervalMinutes.set(Math.max(1, minutes));
    }

    @Scheduled(fixedDelay = 60000)
    public void autoFetchTick() {
        if (!autoEnabled.get()) return;
        int minute = LocalDateTime.now().getMinute();
        int interval = Math.max(1, intervalMinutes.get());
        if (minute % interval != 0) return;
        fetchMissing(20);
    }

    private boolean downloadPosterFromTmdb(String title, Path posterPath) {
        try {
            if (tmdbApiKey == null || tmdbApiKey.isBlank()) return false;

            String searchUrl = UriComponentsBuilder.fromHttpUrl("https://api.themoviedb.org/3/search/movie")
                    .queryParam("api_key", tmdbApiKey)
                    .queryParam("query", title)
                    .queryParam("language", "zh-CN")
                    .queryParam("include_adult", false)
                    .build(true)
                    .toUriString();

            Map<?, ?> result = restTemplate.getForObject(searchUrl, Map.class);
            if (result == null || result.get("results") == null) return false;

            var list = (List<?>) result.get("results");
            if (list.isEmpty()) return false;
            var first = (Map<?, ?>) list.get(0);
            Object posterPathObj = first.get("poster_path");
            if (posterPathObj == null) return false;

            String url = tmdbImageBaseUrl + posterPathObj.toString();
            byte[] bytes = restTemplate.getForObject(url, byte[].class);
            if (bytes == null || bytes.length == 0) return false;
            Files.write(posterPath, bytes);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
