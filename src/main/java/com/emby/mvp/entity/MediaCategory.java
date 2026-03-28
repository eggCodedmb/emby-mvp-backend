package com.emby.mvp.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("media_category")
public class MediaCategory {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long mediaId;
    private Long categoryId;
    private LocalDateTime createdAt;
}
