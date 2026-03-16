package com.emby.mvp.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.emby.mvp.common.ApiResponse;
import com.emby.mvp.dto.MediaUpdateRequest;
import com.emby.mvp.entity.MediaItem;
import com.emby.mvp.service.MediaService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
                                             @RequestParam(defaultValue = "20") int size,
                                             @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(mediaService.page(page, size, keyword));
    }

    @GetMapping("/{id}")
    public ApiResponse<java.util.Map<String, Object>> detail(@PathVariable Long id) {
        return ApiResponse.ok(mediaService.getDetail(id));
    }

    @PutMapping("/{id}")
    public ApiResponse<MediaItem> update(@PathVariable Long id,
                                         @Valid @RequestBody MediaUpdateRequest request) {
        return ApiResponse.ok(mediaService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        mediaService.delete(id);
        return ApiResponse.ok();
    }
}
