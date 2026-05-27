package com.realty.Realtymate.utils;

import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.http.HttpHeaders;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

@Service
public class KakaoService {

    private final WebClient webClient;

    public KakaoService(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.baseUrl("https://dapi.kakao.com").build();
    }

    public String getKakaoAddress(double x, double y) {
        String url = "/v2/local/geo/coord2address.json?x=" + x + "&y=" + y;

        String response = webClient.get()
                .uri(url)
                .header(HttpHeaders.AUTHORIZATION, "KakaoAK f76bbb9c3889acba031a4499cfbcbef7") // 카카오 API 키 추가
                .retrieve()
                .bodyToMono(String.class)
                .block(); // 동기적으로 결과 받기

        try {
            // JSON 파싱
            JSONParser parser = new JSONParser(); // ✅ 정상 작동
            JSONObject itemInfo = (JSONObject) parser.parse(response);
            JSONArray addrInfo = (JSONArray) itemInfo.get("documents");

            if (addrInfo.isEmpty()) {
                return null; // 주소 정보가 없는 경우
            }

            JSONObject doc = (JSONObject) addrInfo.get(0);
            JSONObject address = (JSONObject) doc.get("address");
            String addressName = (String) address.get("address_name");

            // 공백 이후의 주소 부분 추출
            return addressName.substring(addressName.indexOf(" ") + 1);

        } catch (ParseException e) {
            e.printStackTrace();
            return null;
        }
    }
}
