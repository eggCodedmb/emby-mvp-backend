package com.emby.mvp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.emby.mvp.common.BizException;
import com.emby.mvp.dto.MediaUpdateRequest;
import com.emby.mvp.entity.MediaItem;
import com.emby.mvp.entity.PlaybackProgress;
import com.emby.mvp.mapper.MediaItemMapper;
import com.emby.mvp.mapper.PlaybackProgressMapper;
import com.emby.mvp.service.MediaService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class MediaServiceImpl implements MediaService {
    private final MediaItemMapper mediaItemMapper;
    private final PlaybackProgressMapper playbackProgressMapper;

    public MediaServiceImpl(MediaItemMapper mediaItemMapper, PlaybackProgressMapper playbackProgressMapper) {
        this.mediaItemMapper = mediaItemMapper;
        this.playbackProgressMapper = playbackProgressMapper;
    }

    @Override
    public Page<MediaItem> page(int page, int size) {
        return mediaItemMapper.selectPage(new Page<>(page, size), null);
    }

    @Override
    public MediaItem getById(Long id) {
        MediaItem item = mediaItemMapper.selectById(id);
        if (item == null) throw new BizException(4040, "media not found");
        return item;
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
        item.setPosterUrl(normalizeNullable(request.getPosterUrl()));
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
