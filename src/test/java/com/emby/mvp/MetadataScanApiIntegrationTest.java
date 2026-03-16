package com.emby.mvp;

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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MetadataScanApiIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private MediaItemMapper mediaItemMapper;

    @Test
    void shouldRequestMetadataScanApiWithDass286Code() {
        // 准备一条可用于扫描的媒体记录，识别码为 DASS-286
        MediaItem item = new MediaItem();
        item.setTitle("Test title for DASS-286");
        item.setFilePath("/tmp/dass-286-metadata-test.mp4");
        item.setCode("DASS-286");
        item.setCreatedAt(LocalDateTime.now());
        item.setUpdatedAt(LocalDateTime.now());
        mediaItemMapper.insert(item);
        Assertions.assertNotNull(item.getId());

        try {
            String base = "http://localhost:" + port;

            // 登录拿 token
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

            // 调用“获取并扫描元数据”接口（按 code 识别）
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(token);
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> req = Map.of(
                    "limit", 1,
                    "scanAll", false,
                    "mediaIds", List.of(item.getId()),
                    "scanField", "code"
            );

            ResponseEntity<Map> scanResp = restTemplate.exchange(
                    base + "/api/settings/posters/metadata/scan",
                    HttpMethod.POST,
                    new HttpEntity<>(req, headers),
                    Map.class
            );

            Assertions.assertEquals(HttpStatus.OK, scanResp.getStatusCode(), "接口请求应成功返回 200");
            Assertions.assertNotNull(scanResp.getBody());
            Assertions.assertEquals(0, ((Number) scanResp.getBody().get("code")).intValue(), "业务状态码应为 0");

            Object dataObj = scanResp.getBody().get("data");
            Assertions.assertTrue(dataObj instanceof Map, "data 应是统计结果对象");
            Map<?, ?> data = (Map<?, ?>) dataObj;

            int total = ((Number) data.get("total")).intValue();
            int success = ((Number) data.get("success")).intValue();
            int failed = ((Number) data.get("failed")).intValue();

            Assertions.assertEquals(1, total, "应只扫描 1 条记录");
            Assertions.assertEquals(total, success + failed, "成功+失败总和应等于 total");
        } finally {
            if (item.getId() != null) {
                mediaItemMapper.deleteById(item.getId());
            }
        }
    }
}
