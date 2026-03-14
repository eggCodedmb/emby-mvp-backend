package com.emby.mvp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.emby.mvp.common.BizException;
import com.emby.mvp.dto.FeedbackRequest;
import com.emby.mvp.dto.HistoryItemResponse;
import com.emby.mvp.entity.MediaItem;
import com.emby.mvp.entity.UserFavorite;
import com.emby.mvp.entity.UserFeedback;
import com.emby.mvp.entity.UserShareLog;
import com.emby.mvp.entity.UserWatchHistory;
import com.emby.mvp.mapper.MediaItemMapper;
import com.emby.mvp.mapper.UserFavoriteMapper;
import com.emby.mvp.mapper.UserFeedbackMapper;
import com.emby.mvp.mapper.UserShareLogMapper;
import com.emby.mvp.mapper.UserWatchHistoryMapper;
import com.emby.mvp.service.UserActionService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class UserActionServiceImpl implements UserActionService {
    private final UserFavoriteMapper userFavoriteMapper;
    private final UserWatchHistoryMapper userWatchHistoryMapper;
    private final UserFeedbackMapper userFeedbackMapper;
    private final UserShareLogMapper userShareLogMapper;
    private final MediaItemMapper mediaItemMapper;

    public UserActionServiceImpl(UserFavoriteMapper userFavoriteMapper,
                                 UserWatchHistoryMapper userWatchHistoryMapper,
                                 UserFeedbackMapper userFeedbackMapper,
                                 UserShareLogMapper userShareLogMapper,
                                 MediaItemMapper mediaItemMapper) {
        this.userFavoriteMapper = userFavoriteMapper;
        this.userWatchHistoryMapper = userWatchHistoryMapper;
        this.userFeedbackMapper = userFeedbackMapper;
        this.userShareLogMapper = userShareLogMapper;
        this.mediaItemMapper = mediaItemMapper;
    }

    @Override
    public boolean isFavorite(Long userId, Long mediaId) {
        return userFavoriteMapper.selectCount(new LambdaQueryWrapper<UserFavorite>()
                .eq(UserFavorite::getUserId, userId)
                .eq(UserFavorite::getMediaId, mediaId)) > 0;
    }

    @Override
    public void addFavorite(Long userId, Long mediaId) {
        ensureMediaExists(mediaId);
        if (isFavorite(userId, mediaId)) {
            return;
        }
        UserFavorite favorite = new UserFavorite();
        favorite.setUserId(userId);
        favorite.setMediaId(mediaId);
        favorite.setCreatedAt(LocalDateTime.now());
        userFavoriteMapper.insert(favorite);
    }

    @Override
    public void removeFavorite(Long userId, Long mediaId) {
        userFavoriteMapper.delete(new LambdaQueryWrapper<UserFavorite>()
                .eq(UserFavorite::getUserId, userId)
                .eq(UserFavorite::getMediaId, mediaId));
    }

    @Override
    public Page<MediaItem> listFavorites(Long userId, int page, int size) {
        Page<UserFavorite> favoritePage = userFavoriteMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<UserFavorite>()
                        .eq(UserFavorite::getUserId, userId)
                        .orderByDesc(UserFavorite::getCreatedAt));

        List<Long> mediaIds = favoritePage.getRecords().stream().map(UserFavorite::getMediaId).toList();
        List<MediaItem> mediaList = mediaIds.isEmpty() ? List.of() : mediaItemMapper.selectList(new LambdaQueryWrapper<MediaItem>()
                .in(MediaItem::getId, mediaIds));
        Map<Long, MediaItem> mediaMap = mediaList.stream()
                .collect(Collectors.toMap(MediaItem::getId, Function.identity()));

        List<MediaItem> records = mediaIds.stream().map(mediaMap::get).filter(Objects::nonNull).toList();
        Page<MediaItem> result = new Page<>(page, size, favoritePage.getTotal());
        result.setRecords(records);
        return result;
    }

    @Override
    public UserWatchHistory upsertHistory(Long userId, Long mediaId, Integer lastPositionSec, Integer durationSec) {
        ensureMediaExists(mediaId);
        UserWatchHistory history = userWatchHistoryMapper.selectOne(new LambdaQueryWrapper<UserWatchHistory>()
                .eq(UserWatchHistory::getUserId, userId)
                .eq(UserWatchHistory::getMediaId, mediaId)
                .last("limit 1"));
        if (history == null) {
            history = new UserWatchHistory();
            history.setUserId(userId);
            history.setMediaId(mediaId);
        }
        history.setLastPositionSec(lastPositionSec == null ? 0 : lastPositionSec);
        history.setDurationSec(durationSec);
        history.setUpdatedAt(LocalDateTime.now());
        if (history.getId() == null) {
            userWatchHistoryMapper.insert(history);
        } else {
            userWatchHistoryMapper.updateById(history);
        }
        return history;
    }

    @Override
    public Page<HistoryItemResponse> listHistory(Long userId, int page, int size) {
        Page<UserWatchHistory> historyPage = userWatchHistoryMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<UserWatchHistory>()
                        .eq(UserWatchHistory::getUserId, userId)
                        .orderByDesc(UserWatchHistory::getUpdatedAt));

        List<Long> mediaIds = historyPage.getRecords().stream().map(UserWatchHistory::getMediaId).toList();
        List<MediaItem> mediaList = mediaIds.isEmpty() ? List.of() : mediaItemMapper.selectList(new LambdaQueryWrapper<MediaItem>()
                .in(MediaItem::getId, mediaIds));
        Map<Long, MediaItem> mediaMap = mediaList.stream()
                .collect(Collectors.toMap(MediaItem::getId, Function.identity()));

        List<HistoryItemResponse> records = historyPage.getRecords().stream().map(item -> {
            HistoryItemResponse dto = new HistoryItemResponse();
            dto.setMedia(mediaMap.get(item.getMediaId()));
            dto.setLastPositionSec(item.getLastPositionSec());
            dto.setDurationSec(item.getDurationSec());
            dto.setUpdatedAt(item.getUpdatedAt());
            return dto;
        }).filter(item -> item.getMedia() != null).toList();

        Page<HistoryItemResponse> result = new Page<>(page, size, historyPage.getTotal());
        result.setRecords(records);
        return result;
    }

    @Override
    public void submitFeedback(Long userId, FeedbackRequest request) {
        UserFeedback feedback = new UserFeedback();
        feedback.setUserId(userId);
        feedback.setMediaId(request.getMediaId());
        feedback.setType((request.getType() == null || request.getType().isBlank()) ? "other" : request.getType());
        feedback.setContent(request.getContent());
        feedback.setContact(request.getContact());
        feedback.setCreatedAt(LocalDateTime.now());
        userFeedbackMapper.insert(feedback);
    }

    @Override
    public void logShare(Long userId, Long mediaId, String channel) {
        ensureMediaExists(mediaId);
        UserShareLog shareLog = new UserShareLog();
        shareLog.setUserId(userId);
        shareLog.setMediaId(mediaId);
        shareLog.setChannel((channel == null || channel.isBlank()) ? "copy_link" : channel);
        shareLog.setCreatedAt(LocalDateTime.now());
        userShareLogMapper.insert(shareLog);
    }

    private void ensureMediaExists(Long mediaId) {
        if (mediaId == null || mediaItemMapper.selectById(mediaId) == null) {
            throw new BizException(404, "media not found");
        }
    }
}
