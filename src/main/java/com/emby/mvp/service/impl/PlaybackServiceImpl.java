package com.emby.mvp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.emby.mvp.entity.PlaybackProgress;
import com.emby.mvp.entity.UserWatchHistory;
import com.emby.mvp.mapper.PlaybackProgressMapper;
import com.emby.mvp.mapper.UserWatchHistoryMapper;
import com.emby.mvp.service.PlaybackService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class PlaybackServiceImpl implements PlaybackService {
    private final PlaybackProgressMapper playbackProgressMapper;
    private final UserWatchHistoryMapper userWatchHistoryMapper;

    public PlaybackServiceImpl(PlaybackProgressMapper playbackProgressMapper,
                               UserWatchHistoryMapper userWatchHistoryMapper) {
        this.playbackProgressMapper = playbackProgressMapper;
        this.userWatchHistoryMapper = userWatchHistoryMapper;
    }

    @Override
    public PlaybackProgress getProgress(Long userId, Long mediaId) {
        PlaybackProgress progress = playbackProgressMapper.selectOne(new LambdaQueryWrapper<PlaybackProgress>()
                .eq(PlaybackProgress::getUserId, userId)
                .eq(PlaybackProgress::getMediaId, mediaId)
                .last("limit 1"));

        UserWatchHistory history = userWatchHistoryMapper.selectOne(new LambdaQueryWrapper<UserWatchHistory>()
                .eq(UserWatchHistory::getUserId, userId)
                .eq(UserWatchHistory::getMediaId, mediaId)
                .last("limit 1"));

        if (progress == null && history == null) {
            return null;
        }

        if (history == null) {
            return progress;
        }

        if (progress == null ||
                (history.getUpdatedAt() != null && (progress.getUpdatedAt() == null || history.getUpdatedAt().isAfter(progress.getUpdatedAt())))) {
            PlaybackProgress synced = progress == null ? new PlaybackProgress() : progress;
            synced.setUserId(userId);
            synced.setMediaId(mediaId);
            synced.setPositionSec(history.getLastPositionSec() == null ? 0 : history.getLastPositionSec());
            synced.setUpdatedAt(history.getUpdatedAt() == null ? LocalDateTime.now() : history.getUpdatedAt());
            if (synced.getId() == null) {
                playbackProgressMapper.insert(synced);
            } else {
                playbackProgressMapper.updateById(synced);
            }
            return synced;
        }

        return progress;
    }

    @Override
    public PlaybackProgress saveProgress(Long userId, Long mediaId, Integer positionSec) {
        PlaybackProgress current = getProgress(userId, mediaId);
        if (current == null) {
            current = new PlaybackProgress();
            current.setUserId(userId);
            current.setMediaId(mediaId);
        }
        current.setPositionSec(positionSec);
        current.setUpdatedAt(LocalDateTime.now());
        if (current.getId() == null) playbackProgressMapper.insert(current);
        else playbackProgressMapper.updateById(current);
        return current;
    }
}
