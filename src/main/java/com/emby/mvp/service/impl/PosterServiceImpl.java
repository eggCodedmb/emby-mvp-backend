package com.emby.mvp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.emby.mvp.entity.MediaItem;
import com.emby.mvp.mapper.MediaItemMapper;
import com.emby.mvp.service.PosterService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

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
    private final JavMetadataServiceImpl javMetadataService;

    @Value("${app.media.poster-dir}")
    private String posterDir;


    private final AtomicBoolean autoEnabled = new AtomicBoolean(false);
    private final AtomicInteger intervalMinutes = new AtomicInteger(60);

    public PosterServiceImpl(MediaItemMapper mediaItemMapper, JavMetadataServiceImpl javMetadataService) {
        this.mediaItemMapper = mediaItemMapper;
        this.javMetadataService = javMetadataService;
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
                boolean ok = javMetadataService.enrichFromJavApi(item);
                if (!ok && javMetadataService.resolvePosterPath(item.getId()) == null) {
                    continue;
                }
                item.setPosterUrl("/api/media/" + item.getId() + "/poster");
                item.setUpdatedAt(LocalDateTime.now());
                mediaItemMapper.updateById(item);
                success++;
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

}
