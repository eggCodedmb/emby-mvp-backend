package com.emby.mvp.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class MediaUpdateRequest {
    @NotBlank
    @Size(max = 255)
    private String title;

    @Size(max = 64)
    private String codec;

    @Min(1)
    private Integer width;

    @Min(1)
    private Integer height;

    @Min(0)
    private Integer durationSec;

    @Min(0)
    private Integer bitrateKbps;

    // allow null explicitly; when present, length will be validated in service
    private String posterUrl;
}
