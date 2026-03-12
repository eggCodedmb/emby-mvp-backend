package com.emby.mvp.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("playback_progress")
public class PlaybackProgress {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long mediaId;
    private Integer positionSec;
    private LocalDateTime updatedAt;
}
