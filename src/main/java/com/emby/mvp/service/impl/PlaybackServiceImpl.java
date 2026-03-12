package com.emby.mvp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.emby.mvp.entity.PlaybackProgress;
import com.emby.mvp.mapper.PlaybackProgressMapper;
import com.emby.mvp.service.PlaybackService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class PlaybackServiceImpl implements PlaybackService {
    private final PlaybackProgressMapper playbackProgressMapper;

    public PlaybackServiceImpl(PlaybackProgressMapper playbackProgressMapper) {
        this.playbackProgressMapper = playbackProgressMapper;
    }

    @Override
    public PlaybackProgress getProgress(Long userId, Long mediaId) {
        var wrapper = new LambdaQueryWrapper<PlaybackProgress>()
                .eq(PlaybackProgress::getUserId, userId)
                .eq(PlaybackProgress::getMediaId, mediaId)
                .last("limit 1");
        return playbackProgressMapper.selectOne(wrapper);
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
