package com.realty.Realtymate.service.OwnerService;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class PeterpanApiService {

    private final WebClient webClient;

    // 정규표현식 패턴들
    private static final Pattern NAME_PATTERN = Pattern.compile("seller_name\"\\s*[:=]\\s*\"([^\"]*)\"");
    private static final Pattern PHONE_PATTERN = Pattern.compile("seller_phone\"\\s*[:=]\\s*\"([^\"]*)\"");
    private static final Pattern MEMO_PATTERN = Pattern.compile("memo\"\\s*[:=]\\s*\"([^\"]*)\"");
    private static final Pattern DETAIL_ADDR_PATTERN = Pattern.compile("address3\"\\s*[:=]\\s*\"([^\"]*)\"");

    public PeterpanApiService(WebClient.Builder webClientBuilder) {
        // 버퍼 크기를 10MB로 증가
        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(configurer -> configurer
                        .defaultCodecs()
                        .maxInMemorySize(10 * 1024 * 1024)) // 10MB
                .build();

        // HttpClient 설정: 리디렉션 자동 처리 활성화 (Python의 allow_redirects=True와 동일)
        HttpClient httpClient = HttpClient.create()
                .followRedirect(true)  // 301, 302 등 리디렉션 자동 처리
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 30000)
                .responseTimeout(Duration.ofSeconds(30))
                .doOnConnected(conn ->
                        conn.addHandlerLast(new ReadTimeoutHandler(30, TimeUnit.SECONDS))
                                .addHandlerLast(new WriteTimeoutHandler(30, TimeUnit.SECONDS)));

        this.webClient = webClientBuilder
                .exchangeStrategies(strategies)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }

    /**
     * 피터팬의 좋은방 소유자 정보 조회
     * @param secondUrl 매물 상세 URL
     * @param verificationTypeName 검증 타입
     * @return [owner_name, owner_phone, memo, detailAddr]
     */
    public Mono<String[]> getPeterpanzOwnerInfo(String secondUrl, String verificationTypeName) {
        return Mono.fromCallable(() -> {
            // HTTP GET 요청
            String responseText = webClient.get()
                    .uri(secondUrl)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            // 정규표현식으로 데이터 추출
            String ownerName = extractAndDecode(responseText, NAME_PATTERN);
            String ownerPhone = extractAndDecode(responseText, PHONE_PATTERN);
            String memo = extractAndDecode(responseText, MEMO_PATTERN);
            String detailAddr = extractAndDecode(responseText, DETAIL_ADDR_PATTERN);

            return new String[]{ownerName, ownerPhone, memo, detailAddr};
        });
    }

    private String[] getRedirectedUrl(String url) {
        String response = webClient.get()
                .uri(url)
                .retrieve()
                .bodyToMono(String.class)
                .block();
        System.out.println(response);
        Pattern pattern = Pattern.compile("window\\.location\\.replace\\('(.+?)'\\)");
        Matcher matcher = pattern.matcher(response);

        if (matcher.find()) {
            String redirectedUrl = matcher.group(1).replace("http", "https");
            String bankItemNo = redirectedUrl.split("offerings_cd=")[1].split("_")[1].split("&")[0];
            return new String[]{redirectedUrl, bankItemNo};
        }
        return null;
    }

    /**
     * 피터팬의 좋은방 메모 조회
     * @param secondUrl 매물 상세 URL
     * @return [memo, detailAddr]
     */
    public Mono<String[]> getPeterpanzMemo(String secondUrl) {
        return Mono.fromCallable(() -> {
            // HTTP GET 요청
            String responseText = webClient.get()
                    .uri(secondUrl)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            // 정규표현식으로 데이터 추출
            String memo = extractAndDecode(responseText, MEMO_PATTERN);
            String detailAddr = extractAndDecode(responseText, DETAIL_ADDR_PATTERN);

            return new String[]{memo, detailAddr};
        });
    }

    /**
     * 정규표현식으로 텍스트 추출 및 디코딩
     */
    private String extractAndDecode(String text, Pattern pattern) {
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            String extracted = matcher.group(1);
            return decodeText(extracted);
        }
        return null;
    }

    /**
     * 유니코드 이스케이프 및 \/ 처리
     * Python의 decode("unicode_escape") 동작을 Java로 구현
     */
    private String decodeText(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        StringBuilder decoded = new StringBuilder();
        int i = 0;
        while (i < text.length()) {
            if (text.charAt(i) == '\\' && i + 1 < text.length()) {
                char nextChar = text.charAt(i + 1);

                if (nextChar == 'u' && i + 5 < text.length()) {
                    try {
                        String unicode = text.substring(i + 2, i + 6);
                        int codePoint = Integer.parseInt(unicode, 16);
                        decoded.append((char) codePoint);
                        i += 6;
                        continue;
                    } catch (NumberFormatException e) {
                        // unicode 파싱 실패시 그대로 추가
                        decoded.append(text.charAt(i));
                        i++;
                        continue;
                    }
                }

                // \/ 처리
                if (nextChar == '/') {
                    decoded.append('/');
                    i += 2;
                    continue;
                }

                // 기타 이스케이프 문자 처리
                switch (nextChar) {
                    case 'n':
                        decoded.append('\n');
                        i += 2;
                        break;
                    case 'r':
                        decoded.append('\r');
                        i += 2;
                        break;
                    case 't':
                        decoded.append('\t');
                        i += 2;
                        break;
                    case '\\':
                        decoded.append('\\');
                        i += 2;
                        break;
                    case '"':
                        decoded.append('"');
                        i += 2;
                        break;
                    default:
                        decoded.append(text.charAt(i));
                        i++;
                        break;
                }
            } else {
                decoded.append(text.charAt(i));
                i++;
            }
        }

        return decoded.toString();
    }
}