package com.emby.mvp.dto;

import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class HistoryUpsertRequest {
    @Min(0)
    private Integer lastPositionSec;

    @Min(0)
    private Integer durationSec;
}
