package com.emby.mvp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.emby.mvp.entity.OperationLog;
import com.emby.mvp.mapper.OperationLogMapper;
import com.emby.mvp.service.LogService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class LogServiceImpl implements LogService {
    private final OperationLogMapper operationLogMapper;

    public LogServiceImpl(OperationLogMapper operationLogMapper) {
        this.operationLogMapper = operationLogMapper;
    }

    @Override
    public Page<OperationLog> page(int page, int size, String type, LocalDateTime startAt, LocalDateTime endAt) {
        LambdaQueryWrapper<OperationLog> qw = new LambdaQueryWrapper<>();
        if (type != null && !type.isBlank()) qw.eq(OperationLog::getType, type.trim());
        if (startAt != null) qw.ge(OperationLog::getCreatedAt, startAt);
        if (endAt != null) qw.le(OperationLog::getCreatedAt, endAt);
        qw.orderByDesc(OperationLog::getCreatedAt);
        return operationLogMapper.selectPage(new Page<>(page, size), qw);
    }

    @Override
    public void write(String type, String content) {
        OperationLog log = new OperationLog();
        log.setType((type == null || type.isBlank()) ? "SYSTEM" : type.trim().toUpperCase());
        log.setContent(content == null ? "" : content);
        log.setCreatedAt(LocalDateTime.now());
        operationLogMapper.insert(log);
    }
}
