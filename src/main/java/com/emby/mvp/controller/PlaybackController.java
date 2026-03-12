package com.emby.mvp.controller;

import com.emby.mvp.common.ApiResponse;
import com.emby.mvp.dto.ProgressRequest;
import com.emby.mvp.service.PlaybackService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/playback")
public class PlaybackController {
    private final PlaybackService playbackService;

    public PlaybackController(PlaybackService playbackService) {
        this.playbackService = playbackService;
    }

    @GetMapping("/{mediaId}/progress")
    public ApiResponse<Map<String, Integer>> getProgress(@PathVariable Long mediaId, Authentication auth) {
        Long userId = Long.parseLong(auth.getName());
        var data = playbackService.getProgress(userId, mediaId);
        int pos = data == null ? 0 : data.getPositionSec();
        return ApiResponse.ok(Map.of("positionSec", pos));
    }

    @PostMapping("/{mediaId}/progress")
    public ApiResponse<Map<String, Integer>> saveProgress(@PathVariable Long mediaId,
                                                           @Valid @RequestBody ProgressRequest request,
                                                           Authentication auth) {
        Long userId = Long.parseLong(auth.getName());
        var saved = playbackService.saveProgress(userId, mediaId, request.getPositionSec());
        return ApiResponse.ok(Map.of("positionSec", saved.getPositionSec()));
    }
}
