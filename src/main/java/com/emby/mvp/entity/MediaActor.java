package com.emby.mvp.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("media_actor")
public class MediaActor {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long mediaId;
    private Long actorId;
    private LocalDateTime createdAt;
}
