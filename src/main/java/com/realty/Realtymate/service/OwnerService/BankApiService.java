package com.realty.Realtymate.service.OwnerService;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class BankApiService {

    private final WebClient webClient;

    public BankApiService(WebClient.Builder webClientBuilder) {
        // 버퍼 크기를 10MB로 증가
        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(configurer -> configurer
                        .defaultCodecs()
                        .maxInMemorySize(10 * 1024 * 1024)) // 10MB
                .build();

        // 리다이렉트를 따라가도록 HttpClient 설정
        HttpClient httpClient = HttpClient.create()
                .followRedirect(true);

        this.webClient = webClientBuilder
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .exchangeStrategies(strategies)
                .build();
    }

    /**
     * 리디렉션 URL과 bank_item_no 추출
     */
    private String[] getRedirectedUrl(String url) {
        String response = webClient.get()
                .uri(url)
                .retrieve()
                .bodyToMono(String.class)
                .block();
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
     * ID와 agency_cd 추출
     */
    private String[] getNeonetCookies(String redirectedUrl) {
        String response;
        try {
            response = webClient.get()
                    .uri(redirectedUrl)
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7")
                    .header("Accept-Language", "ko-KR,ko;q=0.9,en-US;q=0.8,en;q=0.7")
                    .header("Cache-Control", "max-age=0")
                    .header("Connection", "keep-alive")
                    .header("Sec-Fetch-Dest", "document")
                    .header("Sec-Fetch-Mode", "navigate")
                    .header("Sec-Fetch-Site", "none")
                    .header("Sec-Fetch-User", "?1")
                    .header("Upgrade-Insecure-Requests", "1")
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36")
                    .header("sec-ch-ua", "\"Chromium\";v=\"131\", \"Not=A?Brand\";v=\"24\", \"Google Chrome\";v=\"131\"")
                    .header("sec-ch-ua-mobile", "?0")
                    .header("sec-ch-ua-platform", "\"Windows\"")
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
        } catch (Exception e) {
            System.err.println("getNeonetCookies 요청 실패: " + e.getMessage());
            System.err.println("요청 URL: " + redirectedUrl);
            return null;
        }

        if (response == null || response.isEmpty()) {
            System.err.println("getNeonetCookies 응답이 비어있음. URL: " + redirectedUrl);
            return null;
        }

        Document doc = Jsoup.parse(response);
        System.out.println("응답 HTML 길이: " + response.length());
        // ID 추출
        Elements txt01Divs = doc.select("div.txt01");
        String id = null;
        for (Element div : txt01Divs) {
            Element link = div.selectFirst("a");
            if (link != null) {
                String href = link.attr("href");
                id = href.split("//")[1].split("\\.neonet")[0];
                break;
            }
        }

        // agency_cd 추출
        Elements mmcDtDtitleDivs = doc.select("div.mmc_dt_dtitle");
        String agencyCd = null;
        for (Element div : mmcDtDtitleDivs) {
            Element link = div.selectFirst("a");
            if (link != null) {
                String href = link.attr("href");
                agencyCd = href.split("agency_cd=")[1].split("&")[0];
                break;
            }
        }

        // atclNo 추출
        Element atclNoElement = doc.getElementById("atclNo");
        String atclNo = atclNoElement != null ? atclNoElement.attr("value") : null;

        return new String[]{id, agencyCd, atclNo};
    }

    /**
     * 상세 페이지 HTML 가져오기
     */
    private Document getOfferingsPage(String bankItemNo, String id, String agencyCd) {
        String url = String.format(
                "https://agency.neonet.co.kr/novo-agency/view/offerings/OfferingsDetail.neo?offerings_cd=%s&offerings_gbn=ST&offer_gbn=M&status_gbn=70&page=1&call_gbn=naver_list",
                bankItemNo
        );

        String response = webClient.get()
                .uri(url)
                .header("Cookie", String.format("neonet_id_save_=%s; id__=%s; agency_cd__=%s", id, id, agencyCd))
                .retrieve()
                .bodyToMono(String.class)
                .block();

        return Jsoup.parse(response);
    }

    /**
     * 메모 추출
     */
    private String getMemo(Document doc) {
        Elements thElements = doc.select("th");
        for (Element th : thElements) {
            if (th.text().contains("중개사메모")) {
                Element td = th.nextElementSibling();
                return td != null ? td.text().trim() : "없음";
            }
        }
        return "해당 항목 없음";
    }

    /**
     * 상세 주소 추출
     */
    private String getDetailAddress(Document doc) {
        Elements thElements = doc.select("th");
        for (Element th : thElements) {
            if (th.text().contains("소재지")) {
                Element td = th.nextElementSibling();
                if (td != null) {
                    String address = td.text().trim();
                    // 번지수 이후의 상세 주소만 추출
                    String[] parts = address.split("\\s+");
                    for (int i = 0; i < parts.length; i++) {
                        if (parts[i].matches("^\\d.*")) {
                            if (i + 1 < parts.length) {
                                StringBuilder detailAddr = new StringBuilder();
                                for (int j = i + 1; j < parts.length; j++) {
                                    detailAddr.append(parts[j]).append(" ");
                                }
                                return detailAddr.toString().trim();
                            }
                        }
                    }
                }
            }
        }

        // 값이 없으면 id='address2'에서 가져오기
        Element address2 = doc.getElementById("address2");
        if (address2 != null) {
            return address2.attr("value").trim();
        }

        return "없음";
    }

    /**
     * th 키워드로 td 값 추출
     */
    private String getNextTdText(Document doc, String thKeyword) {
        Elements thElements = doc.select("th");
        for (Element th : thElements) {
            if (th.text().trim().contains(thKeyword)) {
                Element td = th.nextElementSibling();
                return td != null ? td.text().trim() : null;
            }
        }
        return null;
    }

    /**
     * 부동산뱅크 소유자 정보 조회
     */
    public Mono<String[]> getBankOwnerInfo(String url, String verificationTypeName) {
        return Mono.fromCallable(() -> {
//            String[] redirectData = getRedirectedUrl(url);
//            if (redirectData == null) {
//                return new String[]{"리디렉션 실패", "", "", "", "", "", ""};
//            }
//
//            String redirectedUrl = redirectData[0];
//            String bankItemNo = redirectData[1];
            String[] cookieData = getNeonetCookies(url);
            if (cookieData == null || cookieData[0] == null || cookieData[1] == null) {
                return new String[]{"ID 또는 agency_cd 추출 실패", "", "", "", "", "", ""};
            }

            String id = cookieData[0];
            String agencyCd = cookieData[1];
            String bankItemNo = cookieData[2];

            Document doc = getOfferingsPage(bankItemNo, id, agencyCd);

            // 홍보확인 방식
            String verificationMethod = getNextTdText(doc, "홍보확인 방식");
            if (verificationMethod == null) {
                verificationMethod = "해당 항목 없음";
            }

            // 소유주 이름
            Element ownerNmElement = doc.getElementById("owner_nm");
            String ownerName = ownerNmElement != null ? ownerNmElement.attr("value") : "없음";

            // 소유주 전화번호, 통신사, 성별
            String ownerPhone = "없음";
            String mccpNm = null;
            String gender = null;

            if (verificationMethod.contains("모바일 검증")) {
                Element ownerPhoneTd = doc.select("#tr_owner_name td:last-of-type").first();
                ownerPhone = ownerPhoneTd != null ? ownerPhoneTd.text().trim() : "없음";
                mccpNm = getNextTdText(doc, "소유자 휴대전화 통신사");
                gender = getNextTdText(doc, "성별");
            } else if (verificationMethod.contains("신홍보")) {
                Element descDiv = doc.getElementById("tr_input_T_owner_tel_desc");
                if (descDiv != null) {
                    Element ownerPhoneTd = descDiv.parent();
                    if (ownerPhoneTd != null) {
                        ownerPhone = ownerPhoneTd.text().replace(descDiv.text(), "").trim();
                    }
                }
                mccpNm = getNextTdText(doc, "소유자 휴대전화 통신사");
                gender = getNextTdText(doc, "성별");
            }

            String memo = getMemo(doc);
            String detailAddr = getDetailAddress(doc);

            return new String[]{ownerName, ownerPhone, memo, detailAddr, verificationMethod, mccpNm, gender};
        });
    }

    /**
     * 부동산뱅크 메모 조회
     */
    public Mono<String[]> getBankMemo(String url) {
        return Mono.fromCallable(() -> {
            String[] redirectData = getRedirectedUrl(url);
            if (redirectData == null) {
                return new String[]{"리디렉션 실패", ""};
            }

            String redirectedUrl = redirectData[0];
            String bankItemNo = redirectData[1];

            String[] cookieData = getNeonetCookies(redirectedUrl);
            if (cookieData == null || cookieData[0] == null || cookieData[1] == null) {
                return new String[]{"ID 또는 agency_cd 추출 실패", ""};
            }

            String id = cookieData[0];
            String agencyCd = cookieData[1];

            Document doc = getOfferingsPage(bankItemNo, id, agencyCd);

            String memo = getMemo(doc);
            String detailAddr = getDetailAddress(doc);

            return new String[]{memo, detailAddr};
        });
    }
}
