package com.emby.mvp.controller;

import com.emby.mvp.common.ApiResponse;
import com.emby.mvp.common.BizException;
import com.emby.mvp.dto.ScanRequest;
import com.emby.mvp.service.LibraryService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/library")
public class LibraryController {
    private final LibraryService libraryService;

    @Value("${app.media.root-path}")
    private String mediaRoot;

    public LibraryController(LibraryService libraryService) {
        this.libraryService = libraryService;
    }

    @GetMapping("/folders")
    public ApiResponse<Object> folders(Authentication authentication,
                                       @RequestParam(required = false) String path) {
        boolean isAdmin = authentication.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        if (!isAdmin) throw new BizException(4030, "forbidden");

        Path base = Paths.get(mediaRoot).normalize().toAbsolutePath();
        Path current;
        if (path == null || path.isBlank()) {
            current = base;
        } else {
            Path input = Paths.get(path);
            current = (input.isAbsolute() ? input : base.resolve(input)).normalize().toAbsolutePath();
        }
        if (!current.startsWith(base)) throw new BizException(4004, "folder must be inside media root");
        if (!Files.isDirectory(current)) throw new BizException(4044, "folder not found");

        try {
            List<Map<String, String>> folders = Files.list(current)
                    .filter(Files::isDirectory)
                    .sorted(Comparator.comparing(Path::getFileName))
                    .map(p -> Map.of(
                            "name", p.getFileName().toString(),
                            "path", base.relativize(p).toString().replace('\\', '/')
                    ))
                    .toList();

            String currentPath = base.equals(current) ? "" : base.relativize(current).toString().replace('\\', '/');
            String parentPath = "";
            if (!base.equals(current)) {
                Path parent = current.getParent();
                if (parent != null && parent.startsWith(base) && !base.equals(current)) {
                    parentPath = base.equals(parent) ? "" : base.relativize(parent).toString().replace('\\', '/');
                }
            }
            return ApiResponse.ok(Map.of(
                    "currentPath", currentPath,
                    "parentPath", parentPath,
                    "folders", folders
            ));
        } catch (Exception e) {
            throw new BizException(5001, "list folders failed: " + e.getMessage());
        }
    }

    @PostMapping("/scan")
    public ApiResponse<Object> scan(Authentication authentication, @RequestBody(required = false) ScanRequest req) {
        boolean isAdmin = authentication.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        if (!isAdmin) throw new BizException(4030, "forbidden");
        String folder = req == null ? null : req.getFolderPath();
        Integer depth = req == null ? null : req.getDepth();
        return ApiResponse.ok(libraryService.scan(folder, depth));
    }
}
