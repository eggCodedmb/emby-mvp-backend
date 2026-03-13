package com.emby.mvp;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;

import java.nio.charset.StandardCharsets;
import java.util.Map;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SubtitleDownloadIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void shouldDownloadSubtitleFile() {
        int mediaId = Integer.parseInt(System.getProperty("subtitle.test.mediaId", "13"));

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
        Map<?, ?> loginJson = loginResp.getBody();
        Assertions.assertNotNull(loginJson);
        Object dataObj = loginJson.get("data");
        Assertions.assertTrue(dataObj instanceof Map);
        String token = String.valueOf(((Map<?, ?>) dataObj).get("accessToken"));
        Assertions.assertNotNull(token);
        Assertions.assertFalse(token.isBlank());

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setAccept(java.util.List.of(MediaType.ALL));

        String title = "IPX-221";
        String lang = "zh";
        ResponseEntity<byte[]> subtitleResp = restTemplate.exchange(
                base + "/api/media/" + mediaId + "/subtitle?title=" + title + "&lang=" + lang,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                byte[].class
        );

        Assertions.assertEquals(HttpStatus.OK, subtitleResp.getStatusCode());
        byte[] body = subtitleResp.getBody();
        Assertions.assertNotNull(body);
        Assertions.assertTrue(body.length > 0, "subtitle response should not be empty");

        String preview = new String(body, 0, Math.min(body.length, 200), StandardCharsets.UTF_8).toLowerCase();
        Assertions.assertTrue(
                preview.contains("webvtt") || preview.contains("-->") || preview.matches(".*\\d{2}:\\d{2}:\\d{2}.*"),
                "response does not look like subtitle content"
        );
    }
}
