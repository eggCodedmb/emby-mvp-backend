package com.emby.mvp.dto;

import lombok.Data;

@Data
public class ScanRequest {
    /**
     * 可选：扫描子目录，支持相对 media root 的路径（如 movies/2025）或绝对路径（必须在 media root 下）
     */
    private String folderPath;

    /**
     * 可选：扫描深度，1=仅当前目录，2=包含一层子目录，<=0 表示不限
     */
    private Integer depth;
}
