package com.emby.mvp;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.emby.mvp.entity.MediaItem;
import com.emby.mvp.mapper.MediaItemMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ScanNoPosterCodesIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private MediaItemMapper mediaItemMapper;

    @Test
    void shouldScanCodesFromItemsWithoutPoster() {
        int pick = Integer.parseInt(System.getProperty("scan.test.pick", "5"));

        List<MediaItem> targets = mediaItemMapper.selectList(new LambdaQueryWrapper<MediaItem>()
                .isNotNull(MediaItem::getCode)
                .and(w -> w.isNull(MediaItem::getPosterUrl).or().eq(MediaItem::getPosterUrl, ""))
                .orderByDesc(MediaItem::getUpdatedAt)
                .last("limit " + Math.max(1, Math.min(pick, 20))));

        Assertions.assertFalse(targets.isEmpty(), "数据库里没有可测试数据（code 有值且 posterUrl 为空）");

        List<Map<String, Object>> items = targets.stream()
                .map(m -> Map.<String, Object>of(
                        "mediaId", m.getId(),
                        "scanField", "code"
                ))
                .collect(Collectors.toList());

        String base = "http://localhost:" + port;

        HttpHeaders loginHeaders = new HttpHeaders();
        loginHeaders.setContentType(MediaType.APPLICATION_JSON);
        String loginBody = "{\"username\":\"admin\",\"password\":\"password\"}";
        ResponseEntity<Map> loginResp = restTemplate.exchange(
                base + "/api/auth/login",
                HttpMethod.POST,
                new HttpEntity<>(loginBody, loginHeaders),
                Map.class
        );

        Assertions.assertEquals(HttpStatus.OK, loginResp.getStatusCode());
        Assertions.assertNotNull(loginResp.getBody());
        Object loginDataObj = loginResp.getBody().get("data");
        Assertions.assertTrue(loginDataObj instanceof Map);

        String token = String.valueOf(((Map<?, ?>) loginDataObj).get("accessToken"));
        Assertions.assertNotNull(token);
        Assertions.assertFalse(token.isBlank());

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> req = Map.of("items", items);

        ResponseEntity<Map> scanResp = restTemplate.exchange(
                base + "/api/settings/posters/metadata/scan",
                HttpMethod.POST,
                new HttpEntity<>(req, headers),
                Map.class
        );

        Assertions.assertEquals(HttpStatus.OK, scanResp.getStatusCode(), "扫描接口 HTTP 应成功");
        Assertions.assertNotNull(scanResp.getBody());
        Assertions.assertEquals(0, ((Number) scanResp.getBody().get("code")).intValue(), "业务状态码应为 0");

        Object dataObj = scanResp.getBody().get("data");
        Assertions.assertTrue(dataObj instanceof Map, "data 应是统计对象");
        Map<?, ?> data = (Map<?, ?>) dataObj;

        int total = ((Number) data.get("total")).intValue();
        int success = ((Number) data.get("success")).intValue();
        int failed = ((Number) data.get("failed")).intValue();

        Assertions.assertEquals(items.size(), total, "total 应等于本次提交扫描条数");
        Assertions.assertEquals(total, success + failed, "success + failed 应等于 total");

        // 严格断言：每条都必须补到封面（否则判失败）
        List<Long> notCompleted = targets.stream()
                .map(MediaItem::getId)
                .filter(id -> {
                    MediaItem after = mediaItemMapper.selectById(id);
                    if (after == null) return true;
                    String poster = after.getPosterUrl();
                    String title = after.getTitle();
                    return poster == null || poster.isBlank() || title == null || title.isBlank();
                })
                .collect(Collectors.toList());

        Assertions.assertTrue(
                notCompleted.isEmpty(),
                "以下 mediaId 未拿到完整元数据(title/posterUrl): " + notCompleted
        );
    }
}
