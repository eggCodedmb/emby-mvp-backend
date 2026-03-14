package com.emby.mvp.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class FeedbackRequest {
    private Long mediaId;
    private String type;

    @NotBlank
    private String content;

    private String contact;
}
