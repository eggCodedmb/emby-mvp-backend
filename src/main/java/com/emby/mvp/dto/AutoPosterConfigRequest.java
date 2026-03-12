package com.emby.mvp.dto;

import lombok.Data;

@Data
public class AutoPosterConfigRequest {
    private Boolean enabled;
    private Integer intervalMinutes;
}
