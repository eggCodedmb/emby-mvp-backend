package com.emby.mvp.controller;

import com.emby.mvp.common.ApiResponse;
import com.emby.mvp.common.BizException;
import com.emby.mvp.dto.ScanRequest;
import com.emby.mvp.service.LibraryService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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

    public LibraryController(LibraryService libraryService) {
        this.libraryService = libraryService;
    }

    @GetMapping("/folders")
    public ApiResponse<Object> folders(Authentication authentication,
                                       @RequestParam(required = false) String path) {
        boolean isAdmin = authentication.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        if (!isAdmin) throw new BizException(4030, "forbidden");

        try {
            if (path == null || path.isBlank()) {
                var roots = java.io.File.listRoots();
                List<Map<String, String>> folders = java.util.Arrays.stream(roots)
                        .map(r -> {
                            String p = r.getAbsolutePath().replace('\\', '/');
                            return Map.of("name", p, "path", p);
                        })
                        .toList();
                return ApiResponse.ok(Map.of(
                        "currentPath", "",
                        "parentPath", "",
                        "folders", folders
                ));
            }

            Path current = Paths.get(path).normalize().toAbsolutePath();
            if (!Files.isDirectory(current)) throw new BizException(4044, "folder not found");

            List<Map<String, String>> folders = Files.list(current)
                    .filter(Files::isDirectory)
                    .sorted(Comparator.comparing(Path::getFileName))
                    .map(p -> Map.of(
                            "name", p.getFileName().toString(),
                            "path", p.toAbsolutePath().normalize().toString().replace('\\', '/')
                    ))
                    .toList();

            Path parent = current.getParent();
            String parentPath = parent == null ? "" : parent.toAbsolutePath().normalize().toString().replace('\\', '/');

            return ApiResponse.ok(Map.of(
                    "currentPath", current.toString().replace('\\', '/'),
                    "parentPath", parentPath,
                    "folders", folders
            ));
        } catch (BizException e) {
            throw e;
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

    @PostMapping("/scan/start")
    public ApiResponse<Object> startScan(Authentication authentication, @RequestBody(required = false) ScanRequest req) {
        boolean isAdmin = authentication.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        if (!isAdmin) throw new BizException(4030, "forbidden");
        String folder = req == null ? null : req.getFolderPath();
        Integer depth = req == null ? null : req.getDepth();
        return ApiResponse.ok(libraryService.startScanAsync(folder, depth));
    }

    @GetMapping("/scan/{jobId}")
    public ApiResponse<Object> scanStatus(Authentication authentication, @PathVariable Long jobId) {
        boolean isAdmin = authentication.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        if (!isAdmin) throw new BizException(4030, "forbidden");
        return ApiResponse.ok(libraryService.getScanJob(jobId));
    }
}
