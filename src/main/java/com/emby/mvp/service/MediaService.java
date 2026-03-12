package com.emby.mvp.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.emby.mvp.entity.MediaItem;

public interface MediaService {
    Page<MediaItem> page(int page, int size);
    MediaItem getById(Long id);
}
