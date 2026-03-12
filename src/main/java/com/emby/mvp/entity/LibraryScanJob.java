package com.emby.mvp.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("library_scan_jobs")
public class LibraryScanJob {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String status;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private Integer totalFiles;
    private Integer successCount;
    private Integer failCount;
}
