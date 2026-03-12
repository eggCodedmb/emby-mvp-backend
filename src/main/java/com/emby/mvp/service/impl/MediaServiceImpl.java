package com.emby.mvp.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.emby.mvp.common.BizException;
import com.emby.mvp.entity.MediaItem;
import com.emby.mvp.mapper.MediaItemMapper;
import com.emby.mvp.service.MediaService;
import org.springframework.stereotype.Service;

@Service
public class MediaServiceImpl implements MediaService {
    private final MediaItemMapper mediaItemMapper;

    public MediaServiceImpl(MediaItemMapper mediaItemMapper) {
        this.mediaItemMapper = mediaItemMapper;
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
}
