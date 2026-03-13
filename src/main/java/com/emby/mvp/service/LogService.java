package com.emby.mvp.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.emby.mvp.entity.OperationLog;

import java.time.LocalDateTime;

public interface LogService {
    Page<OperationLog> page(int page, int size, String type, LocalDateTime startAt, LocalDateTime endAt);

    void write(String type, String content);
}
