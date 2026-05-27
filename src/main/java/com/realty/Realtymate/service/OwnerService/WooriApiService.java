package com.realty.Realtymate.service.OwnerService;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.http.codec.ClientCodecConfigurer;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class WooriApiService {

    private final WebClient webClient;

    public WooriApiService(WebClient.Builder webClientBuilder) throws Exception {
        // 버퍼 크기를 10MB로 증가
        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(configurer -> configurer
                        .defaultCodecs()
                        .maxInMemorySize(10 * 1024 * 1024)) // 10MB
                .build();

        // SSL 검증 비활성화 (woori-house.co.kr 인증서 문제 해결)
        SslContext sslContext = SslContextBuilder
                .forClient()
                .trustManager(InsecureTrustManagerFactory.INSTANCE)
                .build();

        // 리다이렉트를 따라가도록 HttpClient 설정 + SSL 비활성화
        HttpClient httpClient = HttpClient.create()
                .followRedirect(true)
                .secure(t -> t.sslContext(sslContext));

        this.webClient = webClientBuilder
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .exchangeStrategies(strategies)
                .build();
    }

    /**
     * th 텍스트로 td 값 찾기
     */
    private String findTdByThText(Document doc, String thText) {
        Elements thElements = doc.select("th");
        for (Element th : thElements) {
            if (th.text().trim().contains(thText.trim())) {
                Element td = th.nextElementSibling();
                if (td != null) {
                    // 복사본 만들기
                    Element tdClone = td.clone();

                    // display:none 요소 제거 (숨겨진 폼 요소들)
                    tdClone.select("[style*=display:none], [style*=display: none]").remove();

                    // 남은 폼 요소들 제거
                    tdClone.select("select, option, input, button, textarea").remove();

                    // &nbsp; 를 공백으로 변환하고 여러 공백을 하나로
                    String cleanText = tdClone.text().trim().replaceAll("\\s+", " ");

                    System.out.println("추출된 텍스트: " + cleanText);
                    return cleanText;
                }
                return null;
            }
        }
        return null;
    }

    /**
     * 라디오 버튼의 선택된 라벨 텍스트 가져오기
     */
    private String getCheckedRadioLabelText(Document doc, String name) {
        Element checkedInput = doc.selectFirst(String.format("input[type=radio][name=%s][checked]", name));
        if (checkedInput != null) {
            String inputId = checkedInput.id();
            Element label = doc.selectFirst(String.format("label[for=%s]", inputId));
            return label != null ? label.text().trim() : null;
        }
        return null;
    }

    /**
     * MM 테이블 가져오기
     */
    private Element getMMTable(String itemId, String wooriUid) {
        String url = String.format(
                "https://woori-house.co.kr/new_mmc/memul/mm_list.asp?s_step=&mm_uid=&url=&action_url_val=mm_list.asp&newimgChk=&currentpage=1&s_orderby=&s_orderby2=&s_rlsttype_cd=&s_dealtype_cd=&srch_mm_send_flag=&VRFC_TYPE=&s_area=&DONG_NM=&ADR_HO=&ADR_BUNJI=&s_regdate=all&s_startdate=&s_enddate=&s_proc=&excel_flag=&s_mm_uid=%s&s_viewCount=15&initialization=&vrfc_d_pop=&srch_trash_flag=",
                itemId
        );

        String response = webClient.get()
                .uri(url)
                .header("Cookie", String.format("hkrealtor=prtn%%5Fuid=%s", wooriUid))
                .retrieve()
                .bodyToMono(String.class)
                .block();

        Document doc = Jsoup.parse(response);
        Element table = doc.selectFirst("table.tbl_mmLst");
        if (table != null) {
            Elements rows = table.select("tr");
            if (rows.size() > 1) {
                return rows.get(1); // 두 번째 행 반환
            }
        }
        return null;
    }

    /**
     * 메모 추출
     */
    private String getMemo(Element row) {
        if (row == null) return "";

        Elements tds = row.select("td");
        if (tds.size() > 0) {
            Element jbTextDiv = tds.get(0).selectFirst("div.jb-text");
            return jbTextDiv != null ? jbTextDiv.text().trim() : "";
        }
        return "";
    }

    /**
     * 상세 주소 추출
     */
    private String getDetailAddr(Element row) {
        if (row == null) return "";

        Elements tds = row.select("td");
        if (tds.size() > 4) {
            Elements spans = tds.get(4).select("span");
            if (spans.size() >= 2) {
                return spans.get(0).text().trim() + " " + spans.get(1).text().trim();
            } else if (spans.size() == 1) {
                return spans.get(0).text().trim();
            }
        }
        return "";
    }

    /**
     * 리다이렉션 URL에서 wooriUid 추출
     */
    private String getWooriUidFromRedirect(String url) {
        try {
            // 1차 리다이렉션: 네이버 URL -> woori-house URL 추출
            String firstResponse = webClient.get()
                    .uri(url)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

//            // HTML에서 href URL 추출
//            Document doc = Jsoup.parse(firstResponse);
//            Element link = doc.selectFirst("a[href]");
//            if (link == null) {
//                System.err.println("리다이렉션 링크를 찾을 수 없습니다");
//                return null;
//            }
//
//            String wooriUrl = link.attr("href");
//
//            // 2차 요청: woori-house URL에서 prtn_uid 추출
//            String secondResponse = webClient.get()
//                    .uri(wooriUrl)
//                    .retrieve()
//                    .bodyToMono(String.class)
//                    .block();
//
//            // id="prtn_url_id" 링크에서 UID 추출
            Document secondDoc = Jsoup.parse(firstResponse);
            Element prtnLink = secondDoc.selectFirst("a#prtn_url_id");
            if (prtnLink != null) {
                String href = prtnLink.attr("href");

                // URL의 마지막 부분에서 숫자 추출 (예: https://home.woori-house.co.kr/40286)
                String[] parts = href.split("/");
                String uid = parts[parts.length - 1];
                return uid;
            }

            System.err.println("prtn_url_id 링크를 찾을 수 없습니다");
            return null;
        } catch (Exception e) {
            System.err.println("리다이렉션 URL에서 UID 추출 실패: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * URL에서 itemId 추출
     */
    private String extractItemId(String url) {
        if (url.contains("uid=")) {
            String[] parts = url.split("uid=");
            if (parts.length > 1) {
                return parts[1].substring(0, Math.min(10, parts[1].length()));
            }
        } else if (url.contains("/fin/")) {
            String[] parts = url.split("/");
            return parts[parts.length - 1];
        }
        return "";
    }

    /**
     * 우리집부동산 소유자 정보 조회
     */
    public Mono<String[]> getWooriOwnerInfo(String url, String verificationTypeCode) {
        return Mono.fromCallable(() -> {
            // 리다이렉션 URL에서 wooriUid 추출
            String wooriUid = getWooriUidFromRedirect(url);
            if (wooriUid == null) {
                return new String[]{"UID 추출 실패", "", "", "", "", "", ""};
            }

            // URL에서 itemId 추출
            String itemId = extractItemId(url);
            Element row = getMMTable(itemId, wooriUid);
            if (row == null) {
                return new String[]{"매물 정보 없음", "", "", "", "", "", ""};
            }

            Elements tds = row.select("td");
            String memo = getMemo(row);
            String detailAddr = getDetailAddr(row);

            String verificationMethod = "";
            if (tds.size() > 0) {
                Elements divs = tds.get(0).select("div");
                if (divs.size() > 0) {
                    verificationMethod = divs.get(0).text().trim();
                }
            }

            // mm_uid 추출
            String mmUid = "";
            if (tds.size() > 1) {
                Elements links = tds.get(1).select("a");
                if (links.size() > 0) {
                    mmUid = links.get(0).text().trim();
                }
            }

            // 상세 페이지 요청
            String detailUrl = String.format(
                    "https://woori-house.co.kr/new_mmc/memul/mm_naver.asp?s_step=&mm_uid=%s&url=&action_url_val=mm_list.asp&newimgChk=&currentpage=1&s_orderby=&s_orderby2=&s_rlsttype_cd=&s_dealtype_cd=&srch_mm_send_flag=&VRFC_TYPE=&s_area=&DONG_NM=&ADR_HO=&ADR_BUNJI=&s_startdate=&s_enddate=&s_proc=&excel_flag=&s_mm_uid=&s_viewCount=15&initialization=&vrfc_d_pop=&srch_trash_flag=",
                    mmUid
            );

            String detailResponse = webClient.get()
                    .uri(detailUrl)
                    .header("Cookie", String.format("hkrealtor=prtn%%5Fuid=%s", wooriUid))
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            Document doc = Jsoup.parse(detailResponse);

            String ownerName, ownerPhone, mccpNm = null, gender = null;

            if (verificationTypeCode.contains("NDOC")) {
                ownerName = findTdByThText(doc, "등기부상 소유자명");
                ownerPhone = findTdByThText(doc, "의뢰인 연락처");
            } else if (verificationTypeCode.contains("MOBL")) {
                ownerName = findTdByThText(doc, "매물홍보 의뢰인명");
                ownerPhone = findTdByThText(doc, "소유자 전화번호");
            } else if (verificationTypeCode.contains("OWNER")) {
                ownerName = findTdByThText(doc, "매물홍보 의뢰인명");
                String ownerPhoneTemp = findTdByThText(doc, "소유자 전화번호");
                if (ownerPhoneTemp != null) {
                    String[] parts = ownerPhoneTemp.split("\\s+");
                    if (parts.length >= 2) {
                        mccpNm = parts[0];
                        ownerPhone = parts[1];
                    } else {
                        ownerPhone = ownerPhoneTemp;
                    }
                } else {
                    ownerPhone = "";
                }
                gender = getCheckedRadioLabelText(doc, "osex_z");
            } else {
                ownerName = "";
                ownerPhone = "";
            }

            return new String[]{ownerName, ownerPhone, memo, detailAddr, verificationMethod, mccpNm, gender};
        });
    }

    /**
     * 우리집부동산 메모 조회
     */
    public Mono<String[]> getWoorilMemo(String itemId, String wooriUid) {
        return Mono.fromCallable(() -> {
            Element row = getMMTable(itemId, wooriUid);
            if (row == null) {
                return new String[]{"매물 정보 없음", ""};
            }

            String memo = getMemo(row);
            String detailAddr = getDetailAddr(row);

            return new String[]{memo, detailAddr};
        });
    }
}
