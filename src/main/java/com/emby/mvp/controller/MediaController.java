package com.emby.mvp.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.emby.mvp.common.ApiResponse;
import com.emby.mvp.entity.MediaItem;
import com.emby.mvp.service.MediaService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/media")
public class MediaController {
    private final MediaService mediaService;

    public MediaController(MediaService mediaService) {
        this.mediaService = mediaService;
    }

    @GetMapping
    public ApiResponse<Page<MediaItem>> list(@RequestParam(defaultValue = "1") int page,
                                             @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(mediaService.page(page, size));
    }

    @GetMapping("/{id}")
    public ApiResponse<MediaItem> detail(@PathVariable Long id) {
        return ApiResponse.ok(mediaService.getById(id));
    }
}
