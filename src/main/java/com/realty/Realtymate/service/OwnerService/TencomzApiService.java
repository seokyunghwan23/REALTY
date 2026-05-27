package com.realty.Realtymate.service.OwnerService;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import reactor.netty.http.client.HttpClient;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;

@Service
public class TencomzApiService {

    private final WebClient webClient;

    public TencomzApiService(WebClient.Builder webClientBuilder) {
        // SSL 검증 우회 설정 (텐컴즈 서버 인증서 문제 대응)
        try {
            io.netty.handler.ssl.SslContext sslContext = SslContextBuilder.forClient()
                    .trustManager(InsecureTrustManagerFactory.INSTANCE)
                    .build();

            HttpClient httpClient = HttpClient.create()
                    .secure(sslContextSpec -> sslContextSpec.sslContext(sslContext));

            // 버퍼 크기를 10MB로 증가
            ExchangeStrategies strategies = ExchangeStrategies.builder()
                    .codecs(configurer -> configurer
                            .defaultCodecs()
                            .maxInMemorySize(10 * 1024 * 1024)) // 10MB
                    .build();

            this.webClient = webClientBuilder
                    .clientConnector(new ReactorClientHttpConnector(httpClient))
                    .exchangeStrategies(strategies)
                    .build();
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize TencomzApiService with SSL bypass", e);
        }
    }

    /**
     * 텐컴즈 리다이렉트 URL에서 AtclNo 추출
     * @param secondUrl 텐컴즈 리다이렉트 URL
     * @return AtclNo
     */
    private Mono<String> extractAtclNoFromUrl(String secondUrl) {
        return Mono.fromCallable(() -> {
            byte[] bytes = webClient.get()
                    .uri(secondUrl)
                    .retrieve()
                    .bodyToMono(byte[].class)
                    .block();

            String html = new String(bytes, "UTF-8");

            // AtclNo= 다음의 값 추출
            if (html.contains("AtclNo=")) {
                String[] parts = html.split("AtclNo=");
                if (parts.length > 1) {
                    String atclNo = parts[1].split("&")[0];
                    System.out.println("[DEBUG] AtclNo 추출 성공: " + atclNo);
                    return atclNo;
                }
            }

            System.err.println("[ERROR] AtclNo 추출 실패");
            return null;
        });
    }

    /**
     * 텐컴즈 메모 조회
     * @param secondUrl 텐컴즈 리다이렉트 URL
     * @return [memo, detailAddr]
     */
    public Mono<String[]> getTencomzMemo(String secondUrl) {
        return Mono.fromCallable(() -> {
            // 1. AtclNo 추출
            String atclNo = extractAtclNoFromUrl(secondUrl).block();

            if (atclNo == null || atclNo.isEmpty()) {
                return new String[]{"", ""};
            }

            // 2. MaemulDetail.aspx 조회
            String detailUrl = "http://nhpadmin.tencom.co.kr/Pages/Maemul/MaemulDetail.aspx?atclNo=" + atclNo;

            String response = webClient.get()
                    .uri(detailUrl)
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8")
                    .header("Accept-Language", "ko-KR,ko;q=0.9,en-US;q=0.8,en;q=0.7")
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/144.0.0.0 Safari/537.36")
                    .header("Cookie", "ASP.NET_SessionId=cwm5g1sgn3f331gui4egjlhk; _MaemulStep2=step2; castingWindowNPopUP_375008=done; _searchStatus=H")
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            Document doc = Jsoup.parse(response);

            // 메모 파싱 (CSS Selector)
            String memo = "";
            Element memoElem = doc.selectFirst("#container > div:nth-child(69) > div > div > div");
            if (memoElem != null) {
                memo = memoElem.text().trim();
            }

            // 상세주소 파싱 (CSS Selector)
            String detailAddr = "";
            Element detailAddrElem = doc.selectFirst("#container > div:nth-child(18) > div > div > div");
            if (detailAddrElem != null) {
                detailAddr = detailAddrElem.text().trim();
            }

            return new String[]{memo, detailAddr};
        });
    }

    /**
     * 텐컴즈 소유자 정보 조회
     * @param secondUrl 텐컴즈 리다이렉트 URL
     * @param verificationTypeName 검증 타입 (사용 안 함)
     * @return [owner_name, owner_phone, memo, detailAddr, verfMthdNm, mccpNm, gender]
     */
    public Mono<String[]> getTencomzOwnerInfo(String secondUrl, String verificationTypeName) {
        return Mono.fromCallable(() -> {
            // 1. AtclNo 추출
            String atclNo = extractAtclNoFromUrl(secondUrl).block();

            if (atclNo == null || atclNo.isEmpty()) {
                return new String[]{"", "", "", "", "", "", ""};
            }

            // 2. MaemulDetail.aspx 조회
            String detailUrl = "http://nhpadmin.tencom.co.kr/Pages/Maemul/MaemulDetail.aspx?atclNo=" + atclNo;

            String response = webClient.get()
                    .uri(detailUrl)
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8")
                    .header("Accept-Language", "ko-KR,ko;q=0.9,en-US;q=0.8,en;q=0.7")
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/144.0.0.0 Safari/537.36")
                    .header("Cookie", "ASP.NET_SessionId=cwm5g1sgn3f331gui4egjlhk; _MaemulStep2=step2; castingWindowNPopUP_375008=done; _searchStatus=H")
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            Document doc = Jsoup.parse(response);

            String owner = "";
            String contact = "";

            // TrOwner ID로 소유자 정보 찾기
            Element trOwner = doc.selectFirst("#TrOwner");
            if (trOwner != null) {
                Elements contentDivs = trOwner.select("div.form_ty01_02 div.col-md-12");
                if (contentDivs.size() >= 2) {
                    owner = contentDivs.get(0).text().trim();
                    contact = contentDivs.get(1).text().trim();
                }
            }

            // 메모 파싱
            String memo = "";
            Element memoElem = doc.selectFirst("#container > div:nth-child(69) > div > div > div");
            if (memoElem != null) {
                memo = memoElem.text().trim();
            }

            // 상세주소 파싱
            String detailAddr = "";
            Element detailAddrElem = doc.selectFirst("#container > div:nth-child(18) > div > div > div");
            if (detailAddrElem != null) {
                detailAddr = detailAddrElem.text().trim();
            }

            String verfMthdNm = "";

            // 소유자 정보가 없으면 MaemulStep2.aspx에서 추가 조회
            if (contact.isEmpty() && owner.isEmpty()) {
                String step2Url = "http://nhpadmin.tencom.co.kr/Pages/Maemul/MaemulStep2.aspx?atclNo=" + atclNo;

                String step2Response = webClient.get()
                        .uri(step2Url)
                        .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8")
                        .header("Accept-Language", "ko-KR,ko;q=0.9,en-US;q=0.8,en;q=0.7")
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/144.0.0.0 Safari/537.36")
                        .header("Cookie", "ASP.NET_SessionId=cwm5g1sgn3f331gui4egjlhk; _MaemulStep2=step2; castingWindowNPopUP_375008=done; _searchStatus=H")
                        .retrieve()
                        .bodyToMono(String.class)
                        .block();

                Document step2Doc = Jsoup.parse(step2Response);

                Element txtOwner = step2Doc.getElementById("txtOwner");
                if (txtOwner != null) {
                    owner = txtOwner.attr("value");
                }

                Element txtOrel = step2Doc.getElementById("txtOrel");
                if (txtOrel != null) {
                    contact = txtOrel.attr("value");
                }

                // 메모가 없으면 MaemulModify.aspx에서 조회
                if (memo.isEmpty()) {
                    String modifyUrl = "http://nhpadmin.tencom.co.kr/Pages/Maemul/MaemulModify.aspx?atclNo=" + atclNo + "#1";

                    String modifyResponse = webClient.get()
                            .uri(modifyUrl)
                            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8")
                            .header("Accept-Language", "ko-KR,ko;q=0.9,en-US;q=0.8,en;q=0.7")
                            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/144.0.0.0 Safari/537.36")
                            .header("Cookie", "ASP.NET_SessionId=cwm5g1sgn3f331gui4egjlhk; _MaemulStep2=step2; castingWindowNPopUP_375008=done; _searchStatus=H")
                            .retrieve()
                            .bodyToMono(String.class)
                            .block();

                    Document modifyDoc = Jsoup.parse(modifyResponse);
                    Element txtRealtorMemom = modifyDoc.getElementById("txtRealtorMemom");
                    if (txtRealtorMemom != null) {
                        memo = txtRealtorMemom.text();
                    }
                }
            }

            // 전화번호 포맷팅 (010-1234-5678)
            if (contact != null && !contact.isEmpty() && contact.matches("\\d+")) {
                if (contact.length() == 11) {  // 010-XXXX-XXXX
                    contact = contact.substring(0, 3) + "-" + contact.substring(3, 7) + "-" + contact.substring(7);
                } else if (contact.length() == 10) {  // 010-XXX-XXXX
                    contact = contact.substring(0, 3) + "-" + contact.substring(3, 6) + "-" + contact.substring(6);
                }
            }

            return new String[]{
                    owner != null ? owner : "",
                    contact != null ? contact : "",
                    memo != null ? memo : "",
                    detailAddr != null ? detailAddr : "",
                    verfMthdNm,
                    "", // mccpNm (통신사 정보 없음)
                    ""  // gender (성별 정보 없음)
            };
        });
    }
}
