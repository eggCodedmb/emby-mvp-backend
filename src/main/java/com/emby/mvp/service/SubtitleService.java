package com.emby.mvp.service;

import com.emby.mvp.entity.MediaSubtitle;

import java.util.List;

public interface SubtitleService {
    MediaSubtitle fetchOrDownload(Long mediaId, String title, List<String> preferredLangs);
}
