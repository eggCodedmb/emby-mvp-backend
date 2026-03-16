package com.emby.mvp.dto;

import lombok.Data;

import java.util.List;

@Data
public class MetadataScanRequest {
    private Integer limit;
    private Boolean scanAll;
    private List<Long> mediaIds;
    /**
     * 可选值：title / code
     */
    private String scanField;
}
