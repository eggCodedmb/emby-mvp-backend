package com.emby.mvp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.emby.mvp.common.BizException;
import com.emby.mvp.entity.LibraryScanJob;
import com.emby.mvp.entity.MediaItem;
import com.emby.mvp.mapper.LibraryScanJobMapper;
import com.emby.mvp.mapper.MediaItemMapper;
import com.emby.mvp.service.LibraryService;
import com.emby.mvp.service.LogService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

@Service
public class LibraryServiceImpl implements LibraryService {
    private final LibraryScanJobMapper jobMapper;
    private final MediaItemMapper mediaItemMapper;
    private final LogService logService;

    @Value("${app.media.root-path}")
    private String mediaRoot;

    @Value("${app.media.poster-dir}")
    private String posterDir;

    @Value("${app.tmdb.api-key:}")
    private String tmdbApiKey;

    @Value("${app.tmdb.image-base-url:https://image.tmdb.org/t/p/w500}")
    private String tmdbImageBaseUrl;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ExecutorService scanExecutor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean scanRunning = new AtomicBoolean(false);

    public LibraryServiceImpl(LibraryScanJobMapper jobMapper, MediaItemMapper mediaItemMapper, LogService logService) {
        this.jobMapper = jobMapper;
        this.mediaItemMapper = mediaItemMapper;
        this.logService = logService;
    }

    @Override
    public LibraryScanJob startScanAsync(String folderPath, Integer depth) {
        if (!scanRunning.compareAndSet(false, true)) {
            throw new BizException(4091, "scan already running");
        }

        LibraryScanJob job = new LibraryScanJob();
        job.setStatus("RUNNING");
        job.setStartedAt(LocalDateTime.now());
        job.setTotalFiles(0);
        job.setSuccessCount(0);
        job.setFailCount(0);
        jobMapper.insert(job);

        Long jobId = job.getId();
        logService.write("SCAN", "开始扫描任务 jobId=" + jobId + ", folder=" + (folderPath == null ? "" : folderPath));
        scanExecutor.submit(() -> {
            try {
                runScan(jobId, folderPath, depth);
            } finally {
                scanRunning.set(false);
            }
        });

        return job;
    }

    @Override
    public LibraryScanJob getScanJob(Long jobId) {
        LibraryScanJob job = jobMapper.selectById(jobId);
        if (job == null) throw new BizException(4045, "scan job not found");
        return job;
    }

    @Override
    public LibraryScanJob scan(String folderPath, Integer depth) {
        LibraryScanJob job = new LibraryScanJob();
        job.setStatus("RUNNING");
        job.setStartedAt(LocalDateTime.now());
        job.setTotalFiles(0);
        job.setSuccessCount(0);
        job.setFailCount(0);
        jobMapper.insert(job);

        runScan(job.getId(), folderPath, depth);
        return getScanJob(job.getId());
    }

    private void runScan(Long jobId, String folderPath, Integer depth) {
        LibraryScanJob job = getScanJob(jobId);
        AtomicInteger total = new AtomicInteger();
        AtomicInteger ok = new AtomicInteger();
        AtomicInteger fail = new AtomicInteger();

        Path root;
        if (folderPath == null || folderPath.isBlank()) {
            root = Paths.get(mediaRoot).normalize().toAbsolutePath();
        } else {
            Path input = Paths.get(folderPath);
            root = input.normalize().toAbsolutePath();
        }
        if (!Files.isDirectory(root)) {
            job.setStatus("FAILED");
            job.setFinishedAt(LocalDateTime.now());
            jobMapper.updateById(job);
            logService.write("SCAN", "扫描失败 jobId=" + jobId + "，目录不存在");
            throw new BizException(4044, "scan folder not found");
        }
        int walkDepth = (depth == null || depth <= 0) ? Integer.MAX_VALUE : Math.min(depth, 50);

        Path posterRoot = Paths.get(posterDir).normalize().toAbsolutePath();
        try {
            Files.createDirectories(posterRoot);
        } catch (Exception ignored) {
        }

        try (Stream<Path> paths = Files.walk(root, walkDepth)) {
            paths.filter(Files::isRegularFile)
                    .filter(p -> p.toString().toLowerCase().endsWith(".mp4"))
                    .forEach(p -> {
                        total.incrementAndGet();
                        try {
                            String relative = p.toAbsolutePath().normalize().toString().replace('\\', '/');
                            long fileSize = p.toFile().length();

                            var existing = mediaItemMapper.selectOne(new LambdaQueryWrapper<MediaItem>()
                                    .eq(MediaItem::getFilePath, relative).last("limit 1"));

                            if (existing != null && existing.getFileSize() != null && existing.getFileSize() == fileSize) {
                                ok.incrementAndGet();
                                if (total.get() % 20 == 0) updateJobProgress(job, total.get(), ok.get(), fail.get());
                                return;
                            }

                            String hash = sha256(p);
                            if (existing == null && hash != null) {
                                var duplicated = mediaItemMapper.selectOne(new LambdaQueryWrapper<MediaItem>()
                                        .eq(MediaItem::getFileHash, hash).last("limit 1"));
                                if (duplicated != null) {
                                    ok.incrementAndGet();
                                    if (total.get() % 20 == 0) updateJobProgress(job, total.get(), ok.get(), fail.get());
                                    return;
                                }
                            }

                            MediaItem item = existing == null ? new MediaItem() : existing;
                            item.setTitle(normalizeTitle(p.getFileName().toString()));
                            item.setFilePath(relative);
                            item.setFileHash(hash);
                            item.setFileSize(fileSize);

                            Map<String, String> meta = probeMediaMeta(p);
                            if (meta.get("width") != null) item.setWidth(parseInt(meta.get("width")));
                            if (meta.get("height") != null) item.setHeight(parseInt(meta.get("height")));
                            if (meta.get("codec_name") != null) item.setCodec(meta.get("codec_name"));
                            Integer durationSec = parseSeconds(meta.get("duration"));
                            if (durationSec != null) item.setDurationSec(durationSec);
                            String bitRate = meta.get("bit_rate");
                            if (bitRate != null) {
                                Integer bps = parseInt(bitRate);
                                if (bps != null) item.setBitrateKbps(Math.max(1, bps / 1000));
                            }

                            item.setUpdatedAt(LocalDateTime.now());
                            if (item.getCreatedAt() == null) item.setCreatedAt(LocalDateTime.now());

                            if (item.getId() == null) mediaItemMapper.insert(item);
                            else mediaItemMapper.updateById(item);

                            Path posterPath = posterRoot.resolve(item.getId() + ".jpg");
                            if (!Files.exists(posterPath)) {
                                boolean tmdbOk = downloadPosterFromTmdb(item.getTitle(), posterPath);
                                if (!tmdbOk) {
                                    extractPoster(p, posterPath);
                                }
                            }
                            if (Files.exists(posterPath)) {
                                item.setPosterUrl("/api/media/" + item.getId() + "/poster");
                                mediaItemMapper.updateById(item);
                            }

                            ok.incrementAndGet();
                        } catch (Exception e) {
                            fail.incrementAndGet();
                        }

                        if (total.get() % 20 == 0) {
                            updateJobProgress(job, total.get(), ok.get(), fail.get());
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

        logService.write("SCAN", "扫描结束 jobId=" + jobId + ", status=" + job.getStatus() + ", total=" + total.get() + ", success=" + ok.get() + ", fail=" + fail.get());
    }

    private void updateJobProgress(LibraryScanJob job, int total, int ok, int fail) {
        job.setTotalFiles(total);
        job.setSuccessCount(ok);
        job.setFailCount(fail);
        jobMapper.updateById(job);
    }

    private String sha256(Path file) {
        try (InputStream in = Files.newInputStream(file)) {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) > 0) {
                md.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(md.digest());
        } catch (Exception e) {
            return null;
        }
    }

    private Map<String, String> probeMediaMeta(Path mediaPath) {
        Map<String, String> map = new HashMap<>();
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "ffprobe", "-v", "error",
                    "-show_entries", "stream=width,height,codec_name,bit_rate",
                    "-show_entries", "format=duration,bit_rate",
                    "-of", "default=noprint_wrappers=1",
                    mediaPath.toString()
            );
            pb.redirectErrorStream(true);
            Process p = pb.start();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = br.readLine()) != null) {
                    int idx = line.indexOf('=');
                    if (idx > 0 && idx < line.length() - 1) {
                        map.putIfAbsent(line.substring(0, idx), line.substring(idx + 1));
                    }
                }
            }
            p.waitFor();
        } catch (Exception ignored) {
        }
        return map;
    }

    private Integer parseInt(String s) {
        try {
            return Integer.parseInt(s);
        } catch (Exception e) {
            return null;
        }
    }

    private Integer parseSeconds(String s) {
        try {
            return (int) Math.round(Double.parseDouble(s));
        } catch (Exception e) {
            return null;
        }
    }

    private String normalizeTitle(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot > 0) return fileName.substring(0, dot);
        return fileName;
    }

    private boolean downloadPosterFromTmdb(String title, Path posterPath) {
        try {
            if (tmdbApiKey == null || tmdbApiKey.isBlank()) return false;

            String searchUrl = UriComponentsBuilder.fromHttpUrl("https://api.themoviedb.org/3/search/movie")
                    .queryParam("api_key", tmdbApiKey)
                    .queryParam("query", title)
                    .queryParam("language", "zh-CN")
                    .queryParam("include_adult", false)
                    .build(true)
                    .toUriString();

            Map<?, ?> result = restTemplate.getForObject(searchUrl, Map.class);
            if (result == null || result.get("results") == null) return false;

            var list = (java.util.List<?>) result.get("results");
            if (list.isEmpty()) return false;
            var first = (Map<?, ?>) list.get(0);
            Object posterPathObj = first.get("poster_path");
            if (posterPathObj == null) return false;

            String url = tmdbImageBaseUrl + posterPathObj.toString();
            byte[] bytes = restTemplate.getForObject(url, byte[].class);
            if (bytes == null || bytes.length == 0) return false;
            Files.write(posterPath, bytes);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void extractPoster(Path mediaPath, Path posterPath) {
        // Try hardware decode first, then fallback to software decode
        List<List<String>> commands = Arrays.asList(
                Arrays.asList(
                        "ffmpeg", "-y",
                        "-hwaccel", "cuda",
                        "-ss", "00:00:03",
                        "-i", mediaPath.toString(),
                        "-frames:v", "1",
                        "-q:v", "2",
                        posterPath.toString()
                ),
                Arrays.asList(
                        "ffmpeg", "-y",
                        "-hwaccel", "qsv",
                        "-ss", "00:00:03",
                        "-i", mediaPath.toString(),
                        "-frames:v", "1",
                        "-q:v", "2",
                        posterPath.toString()
                ),
                Arrays.asList(
                        "ffmpeg", "-y",
                        "-hwaccel", "d3d11va",
                        "-ss", "00:00:03",
                        "-i", mediaPath.toString(),
                        "-frames:v", "1",
                        "-q:v", "2",
                        posterPath.toString()
                ),
                Arrays.asList(
                        "ffmpeg", "-y",
                        "-ss", "00:00:03",
                        "-i", mediaPath.toString(),
                        "-frames:v", "1",
                        "-q:v", "2",
                        posterPath.toString()
                )
        );

        for (List<String> cmd : commands) {
            if (runFfmpeg(cmd, posterPath)) {
                return;
            }
        }

        logService.write("SCAN", "抽帧封面失败，已尝试硬件/软件解码: " + mediaPath);
    }

    private boolean runFfmpeg(List<String> command, Path posterPath) {
        try {
            try {
                Files.deleteIfExists(posterPath);
            } catch (Exception ignored) {
            }
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            process.waitFor();
            return process.exitValue() == 0 && Files.exists(posterPath) && Files.size(posterPath) > 0;
        } catch (Exception ignored) {
            return false;
        }
    }
}
