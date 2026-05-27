package com.realty.Realtymate.service.OwnerService;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@Service
public class ServeApiService {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public ServeApiService(WebClient.Builder webClientBuilder, ObjectMapper objectMapper) {
        // 버퍼 크기를 10MB로 증가
        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(configurer -> configurer
                        .defaultCodecs()
                        .maxInMemorySize(10 * 1024 * 1024)) // 10MB
                .build();

        this.webClient = webClientBuilder
                .exchangeStrategies(strategies)
                .build();
        this.objectMapper = objectMapper;
    }


    /**
     * 부동산써브 소유자 정보 조회 (인증 우회 - 메모 포함)
     * @param url 네이버 부동산 URL
     * @param verificationTypeName 검증 타입
     * @return [owner_name, owner_phone, memo, detailAddr, verfMthdNm, mccpNm, gender]
     */
    public Mono<String[]> getServeOwnerInfo(String url, String verificationTypeName) {
        return Mono.fromCallable(() -> {
            // URL에서 naverItemNo 추출
            String naverItemNo = url.split("UID=")[1];

            // 리디렉트 URL 호출
            String redirectUrl = "https://www.serve.co.kr/good/v1/redirect/naverland/" + naverItemNo;
            String redirectResponse = webClient.get()
                    .uri(redirectUrl)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode redirectData = objectMapper.readTree(redirectResponse).get("data");
            String atclNo = redirectData.get("atclNo").asText();

            // 1. CUID 획득을 위한 상세 조회 (로그인 전)
            String cuidUrl = "https://m.serve.co.kr/good/v1/map/getAtclDetail?atclNo=" + atclNo + "&tabNo=4";
            String cuidResponse = webClient.get()
                    .uri(cuidUrl)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/143.0.0.0 Safari/537.36")
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode cuidData = objectMapper.readTree(cuidResponse).get("data");
            JsonNode resultList = cuidData.get("resultList");
            if (resultList == null || resultList.isEmpty()) {
                return new String[]{"", "", "", "", "", "", ""};
            }
            JsonNode firstResult = resultList.get(0);
            String cuid = firstResult.get("cuid").asText();
            String lreaRegNo = firstResult.has("lreaRegNo") ? firstResult.get("lreaRegNo").asText() : "";

            // 특정 번호 차단 (Python 코드 반영)
            if ("11650-2025-00190".equals(lreaRegNo)) {
                return new String[]{"", "", "", "", "", "", ""};
            }

            // 2. Fake Token 생성 (인증 우회)
            Map<String, Object> payloadMap = new HashMap<>();
            payloadMap.put("sub", cuid);
            payloadMap.put("memberType", "GENERAL");
            payloadMap.put("exp", Instant.now().getEpochSecond() + 10800);

            String payloadJson = objectMapper.writeValueAsString(payloadMap);
            String encodedPayload = Base64.getUrlEncoder().withoutPadding().encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8));
            String fakeToken = "eyJhbGciOiJIUzUxMiJ9." + encodedPayload;

            // 3. 인증된 상세 정보 조회 (메모 포함 가능)
            String detailUrl = "https://ma.serve.co.kr/good/v1/actl/detail?atclNo=" + atclNo;
            
            String detailResponse = webClient.get()
                    .uri(detailUrl)
                    .header("Authorization", "Bearer " + fakeToken)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            

            JsonNode data = objectMapper.readTree(detailResponse).get("data");
            JsonNode resultRegistBasic = data.get("resultRegistBasic");
            JsonNode resultRegistDetail = data.get("resultRegistDetail");
            JsonNode verification = resultRegistBasic.get("verification");
            JsonNode realtor = resultRegistBasic.get("realtor");

            // 메모
            String memo = (realtor != null && realtor.has("memoCn")) ? realtor.get("memoCn").asText() : "";

            // 상세주소
            String detailAddr = (resultRegistDetail != null && resultRegistDetail.has("dtlAddr")) ? resultRegistDetail.get("dtlAddr").asText() : "";
            if (detailAddr.isEmpty() && resultRegistDetail != null && resultRegistDetail.has("cmplxPlocAddr")) {
                String cmplxPlocAddr = resultRegistDetail.get("cmplxPlocAddr").asText();
                if (!cmplxPlocAddr.isEmpty()) {
                    String[] parts = cmplxPlocAddr.split("\\s+");
                    if (parts.length >= 3) {
                        detailAddr = parts[parts.length - 3] + " " + parts[parts.length - 2] + " " + parts[parts.length - 1];
                    } else if (parts.length == 2) {
                        detailAddr = parts[parts.length - 2] + " " + parts[parts.length - 1];
                    } else if (parts.length == 1) {
                        detailAddr = parts[parts.length - 1];
                    }
                }
            }

            String verfMthdDvsnCd = verification.has("verfMthdDvsnCd") ? verification.get("verfMthdDvsnCd").asText() : "";
            String name = "", phone = "", mccpNm = null, sexNm = null, verfMthdNm = "기타";

            if ("N".equals(verfMthdDvsnCd)) {
                verfMthdNm = "신홍보확인서";
                name = verification.has("dpslManNm") ? verification.get("dpslManNm").asText() : "";
                phone = verification.has("dpslManTelno") ? verification.get("dpslManTelno").asText() : "";
            } else if ("O".equals(verfMthdDvsnCd)) {
                verfMthdNm = "모바일확인2";
                name = verification.has("ownrNm") ? verification.get("ownrNm").asText() : "";
                phone = verification.has("moblNo") ? verification.get("moblNo").asText() : "";
                mccpNm = convertMccpCode(verification.has("mccpCd") ? verification.get("mccpCd").asText() : "");
                sexNm = convertSexCode(verification.has("sexCd") ? verification.get("sexCd").asText() : "");
            } else if ("M".equals(verfMthdDvsnCd)) {
                verfMthdNm = "모바일확인1";
                name = verification.has("ownrNm") ? verification.get("ownrNm").asText() : "";
                phone = verification.has("moblNo") ? verification.get("moblNo").asText() : "";
                mccpNm = convertMccpCode(verification.has("mccpCd") ? verification.get("mccpCd").asText() : "");
                sexNm = convertSexCode(verification.has("sexCd") ? verification.get("sexCd").asText() : "");
            } else {
                if (mccpNm != null) verfMthdNm = "모바일확인";
                else if (verification.has("dpslManNm")) verfMthdNm = "신홍보확인서";
            }
            
            // 이름과 전화번호가 없는 경우 보완
            if (name.isEmpty() && verification.has("ownrNm")) name = verification.get("ownrNm").asText();
            if (phone.isEmpty() && verification.has("moblNo")) phone = verification.get("moblNo").asText();


            return new String[]{name, phone, memo, detailAddr, verfMthdNm, mccpNm, sexNm};
        });
    }

    /**
     * 내 매물 상세 조회 (Authorization 필요)
     * @param url 네이버 부동산 URL
     * @param authorization Bearer 토큰
     * @return [owner_name, owner_phone, memo, detailAddr, verfMthdNm, mccpNm, gender]
     */
    public Mono<String[]> getMyPropertyDetail(String url, String authorization) {
        return Mono.fromCallable(() -> {
            // URL에서 naverItemNo 추출
            String naverItemNo = url.split("UID=")[1];

            // 리디렉트 URL 호출하여 atclNo 획득
            String redirectUrl = "https://www.serve.co.kr/good/v1/redirect/naverland/" + naverItemNo;
            String redirectResponse = webClient.get()
                    .uri(redirectUrl)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode redirectData = objectMapper.readTree(redirectResponse).get("data");
            String atclNo = redirectData.get("atclNo").asText();

            // 내 매물 상세 정보 조회 (Authorization 헤더 포함)
            String detailUrl = "https://ma.serve.co.kr/good/v1/actl/detail?atclNo=" + atclNo;
            String detailResponse = webClient.get()
                    .uri(detailUrl)
                    .header("Authorization", authorization)
                    .header("memberType", "general")
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode data = objectMapper.readTree(detailResponse).get("data");
            JsonNode resultRegistBasic = data.get("resultRegistBasic");
            JsonNode resultRegistDetail = data.get("resultRegistDetail");
            JsonNode verification = resultRegistBasic.get("verification");
            JsonNode realtor = resultRegistBasic.get("realtor");

            // realtor가 null이면 내 매물이 아님
            if (realtor == null) {
                throw new IllegalStateException("NOT_MY_PROPERTY");
            }

            // 메모
            String memo = realtor.has("memoCn") ? realtor.get("memoCn").asText() : "";

            // 상세주소
            String detailAddr = resultRegistDetail.has("dtlAddr") ? resultRegistDetail.get("dtlAddr").asText() : "";

            // 검증방식 코드
            String verfMthdDvsnCd = verification.has("verfMthdDvsnCd") ? verification.get("verfMthdDvsnCd").asText() : "";

            String name, phone, mccpNm = null, sexNm = null, verfMthdNm;

            if ("N".equals(verfMthdDvsnCd)) {
                // 신홍보확인서
                verfMthdNm = "신홍보확인서";
                name = verification.has("dpslManNm") ? verification.get("dpslManNm").asText() : "";
                phone = verification.has("dpslManTelno") ? verification.get("dpslManTelno").asText() : "";
            } else if ("O".equals(verfMthdDvsnCd)) {
                // 모바일확인 (소유자확인)
                verfMthdNm = "모바일확인";
                name = verification.has("ownrNm") ? verification.get("ownrNm").asText() : "";
                phone = verification.has("moblNo") ? verification.get("moblNo").asText() : "";
                // 통신사 코드 → 명칭 변환
                String mccpCd = verification.has("mccpCd") ? verification.get("mccpCd").asText() : "";
                mccpNm = convertMccpCode(mccpCd);
                // 성별 코드 → 명칭 변환
                String sexCd = verification.has("sexCd") ? verification.get("sexCd").asText() : "";
                sexNm = convertSexCode(sexCd);
            } else {
                verfMthdNm = verfMthdDvsnCd;
                name = "";
                phone = "";
            }

            return new String[]{name, phone, memo, detailAddr, verfMthdNm, mccpNm, sexNm};
        });
    }

    /**
     * 통신사 코드 → 명칭 변환
     */
    private String convertMccpCode(String code) {
        switch (code) {
            case "01": return "SKT";
            case "02": return "KT";
            case "03": return "LGU+";
            case "04": return "SKT(알뜰폰)";
            case "05": return "KT(알뜰폰)";
            case "06": return "LGU+(알뜰폰)";
            default: return code;
        }
    }

    /**
     * 성별 코드 → 명칭 변환
     */
    private String convertSexCode(String code) {
        switch (code) {
            case "M": return "남";
            case "F": return "여";
            default: return code;
        }
    }

    /**
     * 부동산써브 메모 조회 (상세주소만 반환)
     */
    public Mono<String> getServeMemo(String url) {
        return Mono.fromCallable(() -> {
            String naverItemNo = url.split("UID=")[1];

            String redirectUrl = "https://www.serve.co.kr/good/v1/redirect/nland/" + naverItemNo;
            String redirectResponse = webClient.get()
                    .uri(redirectUrl)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode redirectData = objectMapper.readTree(redirectResponse).get("data");
            String atclNo = redirectData.get("atclNo").asText();

            String detailUrl = String.format(
                    "https://ma.serve.co.kr/good/v1/trsm/detail?atclNo=%s&atclStusCd=7&custCenTelno=&prCnfmdFileNm=",
                    atclNo
            );
            String detailResponse = webClient.get()
                    .uri(detailUrl)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode data = objectMapper.readTree(detailResponse).get("data");
            JsonNode resultDetail = data.get("resultDetail");

            return resultDetail.has("plocDtlAddr") ? resultDetail.get("plocDtlAddr").asText() : "";
        });
    }
}
