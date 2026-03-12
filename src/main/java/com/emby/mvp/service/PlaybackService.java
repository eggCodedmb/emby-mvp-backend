package com.emby.mvp.service;

import com.emby.mvp.entity.PlaybackProgress;

public interface PlaybackService {
    PlaybackProgress getProgress(Long userId, Long mediaId);
    PlaybackProgress saveProgress(Long userId, Long mediaId, Integer positionSec);
}
