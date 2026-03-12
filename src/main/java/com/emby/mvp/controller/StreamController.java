package com.emby.mvp.controller;

import com.emby.mvp.common.BizException;
import com.emby.mvp.entity.MediaItem;
import com.emby.mvp.service.MediaService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
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

    public StreamController(MediaService mediaService) {
        this.mediaService = mediaService;
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

    @GetMapping("/{id}/stream")
    public void stream(@PathVariable Long id, HttpServletRequest request, HttpServletResponse response) throws IOException {
        MediaItem item = mediaService.getById(id);
        Path root = Paths.get(mediaRoot).normalize().toAbsolutePath();
        Path file = root.resolve(item.getFilePath()).normalize().toAbsolutePath();
        if (!file.startsWith(root)) {
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
