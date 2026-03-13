package com.emby.mvp.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("media_subtitles")
public class MediaSubtitle {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long mediaId;
    private String videoTitle;
    private String language;
    private String code;
    private String filePath;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
