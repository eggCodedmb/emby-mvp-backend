package com.emby.mvp.controller;

import com.emby.mvp.common.BizException;
import com.emby.mvp.entity.MediaItem;
import com.emby.mvp.entity.MediaSubtitle;
import com.emby.mvp.service.MediaService;
import com.emby.mvp.service.SubtitleService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@RestController
@RequestMapping("/api/media")
public class StreamController {

    @Value("${app.media.root-path}")
    private String mediaRoot;

    @Value("${app.media.poster-dir}")
    private String posterDir;

    private final MediaService mediaService;
    private final SubtitleService subtitleService;

    public StreamController(MediaService mediaService, SubtitleService subtitleService) {
        this.mediaService = mediaService;
        this.subtitleService = subtitleService;
    }

    @GetMapping("/{id}/poster")
    public void poster(@PathVariable Long id, HttpServletResponse response) throws IOException {
        Path poster = Paths.get(posterDir).resolve(id + ".jpg").normalize().toAbsolutePath();
        if (!Files.exists(poster)) {
            throw new BizException(4043, "poster not found");
        }
        response.setContentType(MediaType.IMAGE_JPEG_VALUE);
        Files.copy(poster, response.getOutputStream());
        response.flushBuffer();
    }

    @GetMapping("/{id}/subtitle")
    public void subtitle(@PathVariable Long id,
                         @RequestParam(required = false) String title,
                         @RequestParam(required = false, name = "lang") String lang,
                         Authentication authentication,
                         HttpServletResponse response) throws IOException {
        boolean isAdmin = authentication.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        if (!isAdmin) throw new BizException(4030, "forbidden");

        java.util.List<String> preferredLangs;
        if (lang == null || lang.isBlank()) {
            preferredLangs = java.util.Arrays.asList("Chinese (Simplified)", "Chinese (Traditional)", "Mandarin", "Chinese", "English");
        } else if ("zh".equalsIgnoreCase(lang) || "zh-CN".equalsIgnoreCase(lang) || "cn".equalsIgnoreCase(lang)) {
            preferredLangs = java.util.Arrays.asList("Chinese (Simplified)", "Chinese (Traditional)", "Mandarin", "Chinese", "English");
        } else if ("en".equalsIgnoreCase(lang) || "en-US".equalsIgnoreCase(lang)) {
            preferredLangs = java.util.Arrays.asList("English", "Chinese (Simplified)", "Chinese (Traditional)", "Mandarin", "Chinese");
        } else {
            preferredLangs = java.util.Arrays.asList(lang, "Chinese (Simplified)", "Chinese (Traditional)", "Mandarin", "Chinese", "English");
        }

        MediaSubtitle subtitle = subtitleService.fetchOrDownload(id, title, preferredLangs);
        Path file = Paths.get(subtitle.getFilePath()).toAbsolutePath().normalize();
        if (!Files.exists(file)) throw new BizException(4048, "subtitle file not found");

        String name = file.getFileName().toString().toLowerCase();
        String contentType = name.endsWith(".vtt") ? "text/vtt;charset=UTF-8" : "application/x-subrip;charset=UTF-8";
        response.setContentType(contentType);
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + file.getFileName() + "\"");
        Files.copy(file, response.getOutputStream());
        response.flushBuffer();
    }

    @GetMapping("/{id}/stream")
    public void stream(@PathVariable Long id, HttpServletRequest request, HttpServletResponse response) throws IOException {
        MediaItem item = mediaService.getById(id);
        Path file;
        try {
            Path dbPath = Paths.get(item.getFilePath());
            if (dbPath.isAbsolute()) {
                file = dbPath.normalize().toAbsolutePath();
            } else {
                // 兼容历史数据：如果是相对路径，仍按 mediaRoot 解析
                Path root = Paths.get(mediaRoot).normalize().toAbsolutePath();
                file = root.resolve(dbPath).normalize().toAbsolutePath();
            }
        } catch (Exception e) {
            throw new BizException(4003, "invalid media path");
        }

        if (!file.toFile().exists()) {
            throw new BizException(4042, "media file not found");
        }

        long fileLength = file.toFile().length();
        String range = request.getHeader(HttpHeaders.RANGE);
        response.setHeader(HttpHeaders.ACCEPT_RANGES, "bytes");
        response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);

        long start = 0;
        long end = fileLength - 1;
        if (StringUtils.hasText(range) && range.startsWith("bytes=")) {
            String[] parts = range.substring(6).split("-");
            start = Long.parseLong(parts[0]);
            if (parts.length > 1 && StringUtils.hasText(parts[1])) {
                end = Long.parseLong(parts[1]);
            }
            if (end >= fileLength) end = fileLength - 1;
            if (start > end) {
                response.setStatus(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE.value());
                return;
            }
            response.setStatus(HttpStatus.PARTIAL_CONTENT.value());
            response.setHeader(HttpHeaders.CONTENT_RANGE, "bytes " + start + "-" + end + "/" + fileLength);
        } else {
            response.setStatus(HttpStatus.OK.value());
        }

        long contentLength = end - start + 1;
        response.setHeader(HttpHeaders.CONTENT_LENGTH, String.valueOf(contentLength));

        try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "r")) {
            raf.seek(start);
            byte[] buffer = new byte[8192];
            long remain = contentLength;
            while (remain > 0) {
                int read = raf.read(buffer, 0, (int) Math.min(buffer.length, remain));
                if (read == -1) break;
                response.getOutputStream().write(buffer, 0, read);
                remain -= read;
            }
            response.flushBuffer();
        }
    }
}
