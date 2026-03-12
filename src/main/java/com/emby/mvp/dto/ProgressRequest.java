package com.emby.mvp.dto;

import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class ProgressRequest {
    @Min(0)
    private Integer positionSec;
}
