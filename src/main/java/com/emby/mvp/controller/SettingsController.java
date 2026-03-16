package com.emby.mvp.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.emby.mvp.common.ApiResponse;
import com.emby.mvp.common.BizException;
import com.emby.mvp.dto.AutoPosterConfigRequest;
import com.emby.mvp.dto.MetadataScanRequest;
import com.emby.mvp.entity.MediaItem;
import com.emby.mvp.service.PosterService;
import com.emby.mvp.service.impl.JavMetadataServiceImpl;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/settings/posters")
public class SettingsController {
    private final PosterService posterService;
    private final JavMetadataServiceImpl javMetadataService;

    public SettingsController(PosterService posterService, JavMetadataServiceImpl javMetadataService) {
        this.posterService = posterService;
        this.javMetadataService = javMetadataService;
    }

    private void checkAdmin(Authentication auth) {
        boolean isAdmin = auth.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        if (!isAdmin) throw new BizException(4030, "forbidden");
    }

    @GetMapping("/missing")
    public ApiResponse<Page<MediaItem>> missing(@RequestParam(defaultValue = "1") int page,
                                                @RequestParam(defaultValue = "20") int size,
                                                Authentication auth) {
        checkAdmin(auth);
        return ApiResponse.ok(posterService.listMissing(page, size));
    }

    @PostMapping("/fetch-missing")
    public ApiResponse<Map<String, Integer>> fetchMissing(@RequestParam(defaultValue = "50") int limit,
                                                          Authentication auth) {
        checkAdmin(auth);
        int success = posterService.fetchMissing(limit);
        return ApiResponse.ok(Map.of("success", success));
    }

    @GetMapping("/auto")
    public ApiResponse<Map<String, Object>> autoConfig(Authentication auth) {
        checkAdmin(auth);
        return ApiResponse.ok(posterService.getAutoConfig());
    }

    @PostMapping("/auto")
    public ApiResponse<Void> saveAuto(@RequestBody AutoPosterConfigRequest req, Authentication auth) {
        checkAdmin(auth);
        boolean enabled = req.getEnabled() != null && req.getEnabled();
        int interval = req.getIntervalMinutes() == null ? 60 : req.getIntervalMinutes();
        posterService.updateAutoConfig(enabled, interval);
        return ApiResponse.ok();
    }

    @GetMapping("/metadata/candidates")
    public ApiResponse<java.util.List<Map<String, Object>>> metadataCandidates(@RequestParam(defaultValue = "200") int limit,
                                                                                Authentication auth) {
        checkAdmin(auth);
        return ApiResponse.ok(javMetadataService.listScanCandidates(limit));
    }

    @PostMapping("/metadata/scan")
    public ApiResponse<Map<String, Integer>> scanMetadata(@RequestBody(required = false) MetadataScanRequest req,
                                                          Authentication auth) {
        checkAdmin(auth);

        int limit = req == null || req.getLimit() == null ? 50 : req.getLimit();
        boolean scanAll = req == null || req.getScanAll() == null || req.getScanAll();
        String scanField = req == null ? null : req.getScanField();

        if (!scanAll && req != null && req.getMediaIds() != null && !req.getMediaIds().isEmpty()) {
            return ApiResponse.ok(javMetadataService.scanAndSaveByIds(req.getMediaIds(), scanField));
        }
        return ApiResponse.ok(javMetadataService.scanAndSave(limit, scanField));
    }
}
