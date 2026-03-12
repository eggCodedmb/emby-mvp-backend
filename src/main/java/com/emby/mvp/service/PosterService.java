package com.emby.mvp.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.emby.mvp.entity.MediaItem;

import java.util.Map;

public interface PosterService {
    Page<MediaItem> listMissing(int page, int size);

    int fetchMissing(int limit);

    Map<String, Object> getAutoConfig();

    void updateAutoConfig(boolean enabled, int intervalMinutes);
}
