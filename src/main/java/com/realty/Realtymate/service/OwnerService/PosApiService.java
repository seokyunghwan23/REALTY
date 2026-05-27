package com.realty.Realtymate.service.OwnerService;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Service
public class PosApiService {

    private final WebClient webClient;

    public PosApiService(WebClient.Builder webClientBuilder) {
        // 버퍼 크기를 10MB로 증가
        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(configurer -> configurer
                        .defaultCodecs()
                        .maxInMemorySize(10 * 1024 * 1024)) // 10MB
                .build();

        this.webClient = webClientBuilder
                .exchangeStrategies(strategies)
                .build();
    }

    /**
     * 전화번호 포맷팅
     */
    private String formatPhoneNumber(String number) {
        if (number == null || number.length() < 11) {
            return number;
        }
        return number.substring(0, 3) + "-" + number.substring(3, 7) + "-" + number.substring(7);
    }

    /**
     * 부동산포스 소유자 정보 조회
     * @param url 네이버 부동산 URL
     * @param type 매물 타입 (사용하지 않음)
     * @return [owner_name, owner_contact]
     */
    public Mono<String[]> getPosOwnerInfo(String url, String type) {
        return Mono.fromCallable(() -> {
            // URL에서 naverItemNo 추출
            String naverItemNo = url.split("UID=")[1];

            // 부동산포스 API URL
            String apiUrl = String.format(
                    "https://kakao.menddang.net:8440/SendNaver.php?Iring=1&NarticlePK_ID=%s&Auth_4=B75CC0E68990226EC7305D9EED378F60",
                    naverItemNo
            );

            // HTTP 요청
            String response = webClient.get()
                    .uri(apiUrl)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            // HTML 파싱
            Document doc = Jsoup.parse(response);
            String ownerName = doc.getElementById("basicFd1").attr("value");
            String ownerContact = doc.getElementById("basicFd2").attr("value");

            return new String[]{ownerName, formatPhoneNumber(ownerContact)};
        });
    }
}
