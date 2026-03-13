package com.emby.mvp.service;

import com.emby.mvp.entity.LibraryScanJob;

public interface LibraryService {
    LibraryScanJob scan(String folderPath, Integer depth);

    LibraryScanJob startScanAsync(String folderPath, Integer depth);

    LibraryScanJob getScanJob(Long jobId);
}
