package com.realty.Realtymate.service;

import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import reactor.core.publisher.Mono;

import java.util.Map;

@Service
public class PropertyRecommendService {
    private static final Logger logger = LoggerFactory.getLogger(PropertyRecommendService.class);

    private static final String APPS_SCRIPT_URL = "https://script.google.com/macros/s/AKfycbx-EQI99zeAsDolANsHBaN78JcO0mlbjLCgAmR9ogNRyZjkR3acmhEQ-8QA0uWNdeGcPA/exec";

    private final RestTemplate restTemplate;

    public PropertyRecommendService() {
        // 리다이렉트 자동 처리하는 HttpClient 사용
        CloseableHttpClient httpClient = HttpClients.custom()
                .setRedirectStrategy(new org.apache.hc.client5.http.impl.DefaultRedirectStrategy())
                .build();
        HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory(httpClient);
        this.restTemplate = new RestTemplate(factory);
    }

    public Mono<String> getRecommendedProperties(String propertyId, String mode) {
        return Mono.fromCallable(() -> {
            System.out.println("========================================");
            System.out.println("사용자 입력 매물번호: " + propertyId);
            System.out.println("조회 모드: " + mode);
            System.out.println("========================================");

            Map<String, String> requestBody = Map.of(
                "targetId", propertyId,
                "mode", mode
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, String>> entity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(APPS_SCRIPT_URL, entity, String.class);

            String body = response.getBody();

            // 응답 개수 출력
            int count = 0;
            if (body != null && body.startsWith("[")) {
                // JSON 배열인 경우 개수 계산
                count = body.split("\\},\\s*\\{").length;
            } else if (body != null && body.startsWith("{")) {
                count = 1;
            }

            System.out.println("========================================");
            System.out.println("응답 매물 개수: " + count + "개");
            System.out.println("========================================");

            logger.info("매물 조회 응답 수신 완료 - mode: {}, count: {}, status: {}", mode, count, response.getStatusCode());
            return body;
        }).doOnError(error -> logger.error("매물 조회 요청 실패: {}", error.getMessage()));
    }
}
