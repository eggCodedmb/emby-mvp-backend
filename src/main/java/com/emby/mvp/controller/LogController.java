package com.emby.mvp.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.emby.mvp.common.ApiResponse;
import com.emby.mvp.common.BizException;
import com.emby.mvp.entity.OperationLog;
import com.emby.mvp.service.LogService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/logs")
public class LogController {
    private final LogService logService;

    public LogController(LogService logService) {
        this.logService = logService;
    }

    private void checkAdmin(Authentication auth) {
        boolean isAdmin = auth.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        if (!isAdmin) throw new BizException(4030, "forbidden");
    }

    @GetMapping
    public ApiResponse<Page<OperationLog>> page(@RequestParam(defaultValue = "1") int page,
                                                @RequestParam(defaultValue = "20") int size,
                                                @RequestParam(required = false) String type,
                                                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startAt,
                                                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endAt,
                                                Authentication auth) {
        checkAdmin(auth);
        return ApiResponse.ok(logService.page(page, size, type, startAt, endAt));
    }
}
