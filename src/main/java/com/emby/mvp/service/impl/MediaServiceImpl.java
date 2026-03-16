package com.emby.mvp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.emby.mvp.common.BizException;
import com.emby.mvp.dto.MediaUpdateRequest;
import com.emby.mvp.entity.MediaItem;
import com.emby.mvp.entity.PlaybackProgress;
import com.emby.mvp.mapper.ActorMapper;
import com.emby.mvp.mapper.CategoryMapper;
import com.emby.mvp.mapper.MediaActorMapper;
import com.emby.mvp.mapper.MediaCategoryMapper;
import com.emby.mvp.mapper.MediaItemMapper;
import com.emby.mvp.mapper.PlaybackProgressMapper;
import com.emby.mvp.service.MediaService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class MediaServiceImpl implements MediaService {
    private final MediaItemMapper mediaItemMapper;
    private final PlaybackProgressMapper playbackProgressMapper;
    private final MediaCategoryMapper mediaCategoryMapper;
    private final CategoryMapper categoryMapper;
    private final MediaActorMapper mediaActorMapper;
    private final ActorMapper actorMapper;

    public MediaServiceImpl(MediaItemMapper mediaItemMapper,
                            PlaybackProgressMapper playbackProgressMapper,
                            MediaCategoryMapper mediaCategoryMapper,
                            CategoryMapper categoryMapper,
                            MediaActorMapper mediaActorMapper,
                            ActorMapper actorMapper) {
        this.mediaItemMapper = mediaItemMapper;
        this.playbackProgressMapper = playbackProgressMapper;
        this.mediaCategoryMapper = mediaCategoryMapper;
        this.categoryMapper = categoryMapper;
        this.mediaActorMapper = mediaActorMapper;
        this.actorMapper = actorMapper;
    }

    @Override
    public Page<MediaItem> page(int page, int size, String keyword) {
        var wrapper = new LambdaQueryWrapper<MediaItem>();
        if (keyword != null && !keyword.trim().isEmpty()) {
            wrapper.like(MediaItem::getTitle, keyword.trim());
        }
        wrapper.orderByDesc(MediaItem::getUpdatedAt).orderByDesc(MediaItem::getId);
        return mediaItemMapper.selectPage(new Page<>(page, size), wrapper);
    }

    @Override
    public MediaItem getById(Long id) {
        MediaItem item = mediaItemMapper.selectById(id);
        if (item == null) throw new BizException(4040, "media not found");
        return item;
    }

    @Override
    public Map<String, Object> getDetail(Long id) {
        MediaItem item = getById(id);

        List<Long> categoryIds = mediaCategoryMapper.selectList(new LambdaQueryWrapper<com.emby.mvp.entity.MediaCategory>()
                        .eq(com.emby.mvp.entity.MediaCategory::getMediaId, id)
                        .orderByAsc(com.emby.mvp.entity.MediaCategory::getId))
                .stream()
                .map(com.emby.mvp.entity.MediaCategory::getCategoryId)
                .filter(Objects::nonNull)
                .toList();

        List<String> categories = categoryIds.isEmpty()
                ? List.of()
                : categoryMapper.selectBatchIds(categoryIds).stream()
                .map(com.emby.mvp.entity.Category::getName)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .toList();

        List<Long> actorIds = mediaActorMapper.selectList(new LambdaQueryWrapper<com.emby.mvp.entity.MediaActor>()
                        .eq(com.emby.mvp.entity.MediaActor::getMediaId, id)
                        .orderByAsc(com.emby.mvp.entity.MediaActor::getId))
                .stream()
                .map(com.emby.mvp.entity.MediaActor::getActorId)
                .filter(Objects::nonNull)
                .toList();

        List<Map<String, Object>> actors = actorIds.isEmpty()
                ? List.of()
                : actorMapper.selectBatchIds(actorIds).stream()
                .filter(Objects::nonNull)
                .map(a -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", a.getId());
                    m.put("name", a.getName());
                    m.put("avatarUrl", a.getAvatarUrl());
                    return m;
                })
                .filter(a -> a.get("name") != null && !a.get("name").toString().trim().isEmpty())
                .toList();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", item.getId());
        data.put("title", item.getTitle());
        data.put("filePath", item.getFilePath());
        data.put("fileSize", item.getFileSize());
        data.put("durationSec", item.getDurationSec());
        data.put("width", item.getWidth());
        data.put("height", item.getHeight());
        data.put("codec", item.getCodec());
        data.put("bitrateKbps", item.getBitrateKbps());
        data.put("posterUrl", item.getPosterUrl());
        data.put("code", item.getCode());
        data.put("actorId", item.getActorId());
        data.put("issueDate", item.getIssueDate());
        data.put("fileHash", item.getFileHash());
        data.put("fileMtimeMs", item.getFileMtimeMs());
        data.put("createdAt", item.getCreatedAt());
        data.put("updatedAt", item.getUpdatedAt());

        data.put("categories", categories);
        data.put("categoryList", categories);
        data.put("tags", categories);
        data.put("actors", actors);

        return data;
    }

    @Override
    public MediaItem update(Long id, MediaUpdateRequest request) {
        MediaItem item = getById(id);

        String title = request.getTitle() == null ? "" : request.getTitle().trim();
        if (title.isBlank()) throw new BizException(4002, "title cannot be blank");

        item.setTitle(title);
        item.setCodec(normalizeNullable(request.getCodec()));
        item.setWidth(request.getWidth());
        item.setHeight(request.getHeight());
        item.setDurationSec(request.getDurationSec());
        item.setBitrateKbps(request.getBitrateKbps());

        String posterUrl = normalizeNullable(request.getPosterUrl());
        if (posterUrl != null && posterUrl.length() > 255) {
            throw new BizException(4002, "posterUrl too long");
        }
        item.setPosterUrl(posterUrl);

        item.setUpdatedAt(LocalDateTime.now());

        int affected = mediaItemMapper.updateById(item);
        if (affected != 1) throw new BizException(5002, "update media failed");
        return item;
    }

    @Override
    public void delete(Long id) {
        MediaItem item = mediaItemMapper.selectById(id);
        if (item == null) throw new BizException(4040, "media not found");

        try {
            playbackProgressMapper.delete(new LambdaQueryWrapper<PlaybackProgress>()
                    .eq(PlaybackProgress::getMediaId, id));

            int affected = mediaItemMapper.deleteById(id);
            if (affected != 1) throw new BizException(5003, "delete media failed");
        } catch (DataIntegrityViolationException ex) {
            throw new BizException(4090, "media is referenced and cannot be deleted");
        }
    }

    private String normalizeNullable(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
