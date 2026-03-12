package com.emby.mvp.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("media_items")
public class MediaItem {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String title;
    private String filePath;
    private Long fileSize;
    private Integer durationSec;
    private Integer width;
    private Integer height;
    private String codec;
    private Integer bitrateKbps;
    private String posterUrl;
    private String fileHash;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
