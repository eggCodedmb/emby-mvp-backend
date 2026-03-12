package com.emby.mvp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.emby.mvp.entity.LibraryScanJob;
import com.emby.mvp.entity.MediaItem;
import com.emby.mvp.mapper.LibraryScanJobMapper;
import com.emby.mvp.mapper.MediaItemMapper;
import com.emby.mvp.service.LibraryService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

@Service
public class LibraryServiceImpl implements LibraryService {
    private final LibraryScanJobMapper jobMapper;
    private final MediaItemMapper mediaItemMapper;

    @Value("${app.media.root-path}")
    private String mediaRoot;

    public LibraryServiceImpl(LibraryScanJobMapper jobMapper, MediaItemMapper mediaItemMapper) {
        this.jobMapper = jobMapper;
        this.mediaItemMapper = mediaItemMapper;
    }

    @Override
    public LibraryScanJob scan() {
        LibraryScanJob job = new LibraryScanJob();
        job.setStatus("RUNNING");
        job.setStartedAt(LocalDateTime.now());
        jobMapper.insert(job);

        AtomicInteger total = new AtomicInteger();
        AtomicInteger ok = new AtomicInteger();
        AtomicInteger fail = new AtomicInteger();

        Path root = Paths.get(mediaRoot).normalize().toAbsolutePath();
        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(Files::isRegularFile)
                    .filter(p -> p.toString().toLowerCase().endsWith(".mp4"))
                    .forEach(p -> {
                        total.incrementAndGet();
                        try {
                            String relative = root.relativize(p.toAbsolutePath().normalize()).toString().replace('\\', '/');
                            var existing = mediaItemMapper.selectOne(new LambdaQueryWrapper<MediaItem>()
                                    .eq(MediaItem::getFilePath, relative).last("limit 1"));
                            MediaItem item = existing == null ? new MediaItem() : existing;
                            item.setTitle(p.getFileName().toString());
                            item.setFilePath(relative);
                            item.setFileSize(p.toFile().length());
                            item.setUpdatedAt(LocalDateTime.now());
                            if (item.getCreatedAt() == null) item.setCreatedAt(LocalDateTime.now());
                            if (item.getId() == null) mediaItemMapper.insert(item);
                            else mediaItemMapper.updateById(item);
                            ok.incrementAndGet();
                        } catch (Exception e) {
                            fail.incrementAndGet();
                        }
                    });
            job.setStatus("DONE");
        } catch (Exception e) {
            job.setStatus("FAILED");
        }

        job.setFinishedAt(LocalDateTime.now());
        job.setTotalFiles(total.get());
        job.setSuccessCount(ok.get());
        job.setFailCount(fail.get());
        jobMapper.updateById(job);
        return job;
    }
}
