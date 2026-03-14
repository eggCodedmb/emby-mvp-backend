package com.emby.mvp.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.emby.mvp.dto.FeedbackRequest;
import com.emby.mvp.dto.HistoryItemResponse;
import com.emby.mvp.entity.MediaItem;
import com.emby.mvp.entity.UserWatchHistory;

public interface UserActionService {
    boolean isFavorite(Long userId, Long mediaId);

    void addFavorite(Long userId, Long mediaId);

    void removeFavorite(Long userId, Long mediaId);

    Page<MediaItem> listFavorites(Long userId, int page, int size);

    UserWatchHistory upsertHistory(Long userId, Long mediaId, Integer lastPositionSec, Integer durationSec);

    Page<HistoryItemResponse> listHistory(Long userId, int page, int size);

    void submitFeedback(Long userId, FeedbackRequest request);

    void logShare(Long userId, Long mediaId, String channel);
}
