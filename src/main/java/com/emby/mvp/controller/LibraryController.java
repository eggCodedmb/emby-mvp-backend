package com.emby.mvp.controller;

import com.emby.mvp.common.ApiResponse;
import com.emby.mvp.common.BizException;
import com.emby.mvp.service.LibraryService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/library")
public class LibraryController {
    private final LibraryService libraryService;

    public LibraryController(LibraryService libraryService) {
        this.libraryService = libraryService;
    }

    @PostMapping("/scan")
    public ApiResponse<Object> scan(Authentication authentication) {
        boolean isAdmin = authentication.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        if (!isAdmin) throw new BizException(4030, "forbidden");
        return ApiResponse.ok(libraryService.scan());
    }
}
