package com.emby.mvp.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.emby.mvp.common.ApiResponse;
import com.emby.mvp.dto.FeedbackRequest;
import com.emby.mvp.dto.HistoryItemResponse;
import com.emby.mvp.dto.HistoryUpsertRequest;
import com.emby.mvp.dto.ShareRequest;
import com.emby.mvp.entity.MediaItem;
import com.emby.mvp.service.UserActionService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/user")
public class UserActionController {
    private final UserActionService userActionService;

    public UserActionController(UserActionService userActionService) {
        this.userActionService = userActionService;
    }

    @GetMapping("/favorites/{mediaId}/exists")
    public ApiResponse<Map<String, Boolean>> favoriteExists(@PathVariable Long mediaId, Authentication auth) {
        Long userId = Long.parseLong(auth.getName());
        return ApiResponse.ok(Map.of("favorite", userActionService.isFavorite(userId, mediaId)));
    }

    @PostMapping("/favorites/{mediaId}")
    public ApiResponse<Map<String, Boolean>> addFavorite(@PathVariable Long mediaId, Authentication auth) {
        Long userId = Long.parseLong(auth.getName());
        userActionService.addFavorite(userId, mediaId);
        return ApiResponse.ok(Map.of("favorite", true));
    }

    @DeleteMapping("/favorites/{mediaId}")
    public ApiResponse<Map<String, Boolean>> removeFavorite(@PathVariable Long mediaId, Authentication auth) {
        Long userId = Long.parseLong(auth.getName());
        userActionService.removeFavorite(userId, mediaId);
        return ApiResponse.ok(Map.of("favorite", false));
    }

    @GetMapping("/favorites")
    public ApiResponse<Page<MediaItem>> listFavorites(@RequestParam(defaultValue = "1") int page,
                                                      @RequestParam(defaultValue = "20") int size,
                                                      Authentication auth) {
        Long userId = Long.parseLong(auth.getName());
        return ApiResponse.ok(userActionService.listFavorites(userId, page, size));
    }

    @PutMapping("/history/{mediaId}")
    public ApiResponse<Map<String, Integer>> upsertHistory(@PathVariable Long mediaId,
                                                           @Valid @RequestBody HistoryUpsertRequest request,
                                                           Authentication auth) {
        Long userId = Long.parseLong(auth.getName());
        var history = userActionService.upsertHistory(userId, mediaId, request.getLastPositionSec(), request.getDurationSec());
        return ApiResponse.ok(Map.of("lastPositionSec", history.getLastPositionSec()));
    }

    @GetMapping("/history")
    public ApiResponse<Page<HistoryItemResponse>> listHistory(@RequestParam(defaultValue = "1") int page,
                                                              @RequestParam(defaultValue = "20") int size,
                                                              Authentication auth) {
        Long userId = Long.parseLong(auth.getName());
        return ApiResponse.ok(userActionService.listHistory(userId, page, size));
    }

    @PostMapping("/feedback")
    public ApiResponse<Void> submitFeedback(@Valid @RequestBody FeedbackRequest request, Authentication auth) {
        Long userId = Long.parseLong(auth.getName());
        userActionService.submitFeedback(userId, request);
        return ApiResponse.ok();
    }

    @PostMapping("/share/{mediaId}")
    public ApiResponse<Void> logShare(@PathVariable Long mediaId,
                                      @RequestBody(required = false) ShareRequest request,
                                      Authentication auth) {
        Long userId = Long.parseLong(auth.getName());
        userActionService.logShare(userId, mediaId, request == null ? null : request.getChannel());
        return ApiResponse.ok();
    }
}
