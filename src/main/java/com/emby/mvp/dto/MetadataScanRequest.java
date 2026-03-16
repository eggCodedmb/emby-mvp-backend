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

    /**
     * 新版表格勾选请求：每一项可独立指定 scanField。
     */
    private List<MetadataScanItemRequest> items;
}
