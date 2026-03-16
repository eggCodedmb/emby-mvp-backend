package com.emby.mvp.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.emby.mvp.dto.MediaUpdateRequest;
import com.emby.mvp.entity.MediaItem;

import java.util.Map;

public interface MediaService {
    Page<MediaItem> page(int page, int size, String keyword);
    MediaItem getById(Long id);
    Map<String, Object> getDetail(Long id);
    MediaItem update(Long id, MediaUpdateRequest request);
    void delete(Long id);
}
