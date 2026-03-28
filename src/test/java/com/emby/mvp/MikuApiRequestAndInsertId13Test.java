package com.emby.mvp;

import com.emby.mvp.entity.MediaItem;
import com.emby.mvp.mapper.MediaItemMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@SpringBootTest
class MikuApiRequestAndInsertId13Test {

    @Autowired
    private MediaItemMapper mediaItemMapper;

    @Test
    void shouldRequestMikuApiAndInsertToMediaId13() {
        String url = "https://tools.miku.ac/api/t/jav-search/info?id=DASS-286&source=jp";
        RestTemplate restTemplate = new RestTemplate();

        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.USER_AGENT, "Mozilla/5.0");

        ResponseEntity<Map> resp = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), Map.class);
        Assertions.assertTrue(resp.getStatusCode().is2xxSuccessful(), "元数据接口请求失败");
        Assertions.assertNotNull(resp.getBody(), "接口响应为空");

        Object dataObj = resp.getBody().get("data");
        Assertions.assertTrue(dataObj instanceof Map, "接口 data 为空或结构异常");
        Map<?, ?> data = (Map<?, ?>) dataObj;

        Object titleObj = data.get("title");
        String title = titleObj == null ? "DASS-286" : String.valueOf(titleObj);
        String code = "DASS-286";
        LocalDate issueDate = null;

        Object infoObj = data.get("info");
        if (infoObj instanceof List<?> infos) {
            for (Object rowObj : infos) {
                if (!(rowObj instanceof Map<?, ?> row)) continue;
                String content = String.valueOf(row.get("content"));
                if (content != null && content.matches("\\d{4}-\\d{2}-\\d{2}")) {
                    issueDate = LocalDate.parse(content);
                    break;
                }
            }
        }

        MediaItem media13 = mediaItemMapper.selectById(13L);
        Assertions.assertNotNull(media13, "media_items 中不存在 id=13");

        media13.setCode(code);
        if (title != null && !title.isBlank() && !"null".equalsIgnoreCase(title)) {
            media13.setTitle(title);
        }
        if (issueDate != null) {
            media13.setIssueDate(issueDate);
        }
        media13.setUpdatedAt(LocalDateTime.now());

        int updated = mediaItemMapper.updateById(media13);
        Assertions.assertEquals(1, updated, "更新 id=13 失败");

        MediaItem after = mediaItemMapper.selectById(13L);
        Assertions.assertNotNull(after);
        Assertions.assertEquals("DASS-286", after.getCode(), "id=13 的 code 未成功写入 DASS-286");
    }
}
