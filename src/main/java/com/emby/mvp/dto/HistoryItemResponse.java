package com.emby.mvp.dto;

import com.emby.mvp.entity.MediaItem;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class HistoryItemResponse {
    private MediaItem media;
    private Integer lastPositionSec;
    private Integer durationSec;
    private LocalDateTime updatedAt;
}
