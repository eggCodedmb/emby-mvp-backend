package com.emby.mvp.dto;

import lombok.Data;

@Data
public class MetadataScanItemRequest {
    private Long mediaId;
    /**
     * 可选值：title / code
     */
    private String scanField;
}
