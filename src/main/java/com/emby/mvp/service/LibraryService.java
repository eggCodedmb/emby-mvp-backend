package com.emby.mvp.service;

import com.emby.mvp.entity.LibraryScanJob;

public interface LibraryService {
    LibraryScanJob scan(String folderPath, Integer depth);
}
