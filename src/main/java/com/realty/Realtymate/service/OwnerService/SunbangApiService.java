package com.realty.Realtymate.service.OwnerService;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Service
public class SunbangApiService {

    private final WebClient webClient;

    public SunbangApiService(WebClient.Builder webClientBuilder) {
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
     * 리디렉션 URL 추출
     */
    private String getRedirectedUrl(String url) {
        byte[] responseBytes = webClient.get()
                .uri(url)
                .retrieve()
                .bodyToMono(byte[].class)
                .block();

        // EUC-KR로 디코딩
        String response = new String(responseBytes, java.nio.charset.Charset.forName("EUC-KR"));

        Document doc = Jsoup.parse(response);

        // a 태그의 href 추출 (예: /sale_mm_view.aspx?mm_uid=6355274)
        Element link = doc.selectFirst("a[href]");
        if (link != null) {
            String href = link.attr("href");

            // 상대 경로를 절대 경로로 변환
            if (href.startsWith("/")) {
                return "http://homesdid.co.kr" + href;
            }
            return href;
        }

        return null;
    }

    /**
     * homesdid 쿠키 생성
     */
    private String getHomesdidCookies(String url) {
        String response = webClient.get()
                .uri(url)
                .retrieve()
                .bodyToMono(String.class)
                .block();

        Document doc = Jsoup.parse(response);
        Element banner0 = doc.getElementById("banner_0");
        if (banner0 != null) {
            String id = banner0.attr("value");
            return String.format("hkrealtor=prtn%%5Fuid=%s;", id);
        }
        return null;
    }

    /**
     * 소유자 정보 페이지 가져오기
     */
    private Document getOwnerPage(String mmUid, String cookie) {
        String url = String.format(
                "http://homesdid.co.kr/mmc/memul/memulStep.asp?s_step=&mm_uid=%s&url=&newimgChk=&currentpage=2&s_orderby=&s_orderby2=&s_rlsttype_cd=&s_dealtype_cd=&VRFC_TYPE=&s_mm_uid=&s_area=&DONG_NM=&ADR_HO=&ADR_BUNJI=&s_startdate=&s_enddate=&s_proc=&srch_order_flag=1&s_viewCount=15&excel_flag=",
                mmUid
        );

        String response = webClient.get()
                .uri(url)
                .header("Cookie", cookie)
                .retrieve()
                .bodyToMono(String.class)
                .block();

        return Jsoup.parse(response);
    }

    /**
     * 수정 페이지 가져오기 (메모 포함)
     */
    private Document getEditPage(String mmUid, String cookie) {
        String url = String.format(
                "http://homesdid.co.kr/mmc/memul/memulModifyForm.asp?s_step=&mm_uid=%s&url=&newimgChk=&currentpage=2&s_orderby=&s_orderby2=&s_rlsttype_cd=&s_dealtype_cd=&VRFC_TYPE=&s_mm_uid=&s_area=&DONG_NM=&ADR_HO=&ADR_BUNJI=&s_startdate=&s_enddate=&s_proc=&srch_order_flag=1&s_viewCount=15&excel_flag=",
                mmUid
        );

        String response = webClient.get()
                .uri(url)
                .header("Cookie", cookie)
                .retrieve()
                .bodyToMono(String.class)
                .block();

        return Jsoup.parse(response);
    }

    /**
     * 메모 추출
     */
    private String getMemo(Document editDoc) {
        Element brkgMemo = editDoc.getElementById("brkg_memo");
        return brkgMemo != null ? brkgMemo.text() : "";
    }

    /**
     * 선방 소유자 정보 조회
     */
    public Mono<String[]> getSunbangOwnerInfo(String url, String verificationTypeName) {
        return Mono.fromCallable(() -> {
            String redirectedUrl = getRedirectedUrl(url);
            if (redirectedUrl == null) {
                return new String[]{"리디렉션 실패", "", "", "", "", "", ""};
            }

            String mmUid = redirectedUrl.split("mm_uid=")[1];
            String cookie = getHomesdidCookies(redirectedUrl);

            Document ownerDoc = getOwnerPage(mmUid, cookie);

            String verfMthdNm = getNextTdText(ownerDoc, "매물검증방식");
            String detailAddrTemp = getNextTdText(ownerDoc, "매물명");
            String detailAddr = "";
            if (detailAddrTemp != null) {
                detailAddr = detailAddrTemp.replace("^", " ");
                String[] parts = detailAddr.split("\\s+", 2);
                if (parts.length > 1) {
                    detailAddr = parts[1];
                }
            }

            String ownerName, ownerPhone, mccpNm = null, gender = null;

            if ("신홍보확인서".equals(verfMthdNm)) {
                ownerName = getNextTdText(ownerDoc, "매도자명");
                ownerPhone = getNextTdText(ownerDoc, "매도자전화번호");
            } else if (verfMthdNm != null && verfMthdNm.contains("모바일버전")) {
                Element onameElement = ownerDoc.getElementById("oname");
                ownerName = onameElement != null ? onameElement.attr("value") : "";

                Element selectedTelecom = ownerDoc.selectFirst("select#otelecom option[selected]");
                mccpNm = selectedTelecom != null ? selectedTelecom.text() : null;

                Element selectedOphone1 = ownerDoc.selectFirst("select#ophone1 option[selected]");
                String ophone1 = selectedOphone1 != null ? selectedOphone1.text() : "";

                Element ophone2Element = ownerDoc.getElementById("ophone2");
                String ophone2 = ophone2Element != null ? ophone2Element.attr("value") : "";

                Element ophone3Element = ownerDoc.getElementById("ophone3");
                String ophone3 = ophone3Element != null ? ophone3Element.attr("value") : "";

                ownerPhone = ophone1 + "-" + ophone2 + "-" + ophone3;

                Element checkedRadio = ownerDoc.selectFirst("input[type=radio][name=osex][checked]");
                if (checkedRadio != null) {
                    String genderValue = checkedRadio.attr("value");
                    gender = "M".equals(genderValue) ? "남" : ("F".equals(genderValue) ? "여" : null);
                }
            } else if (verfMthdNm != null && verfMthdNm.contains("바로등록")) {
                // 바로등록 - _z suffix 사용
                Element selectedTelecom = ownerDoc.selectFirst("select#otelecom_z option[selected]");
                mccpNm = selectedTelecom != null ? selectedTelecom.text() : null;

                Element selectedOphone1 = ownerDoc.selectFirst("select#ophone_z_1 option[selected]");
                String phone1 = selectedOphone1 != null ? selectedOphone1.text() : "";

                Element ophone2Element = ownerDoc.getElementById("ophone_z_2");
                String phone2 = ophone2Element != null ? ophone2Element.attr("value") : "";

                Element ophone3Element = ownerDoc.getElementById("ophone_z_3");
                String phone3 = ophone3Element != null ? ophone3Element.attr("value") : "";

                ownerPhone = phone1 + "-" + phone2 + "-" + phone3;

                Element onameElement = ownerDoc.getElementById("oname_z");
                ownerName = onameElement != null ? onameElement.attr("value") : "";

                Element checkedRadioZ = ownerDoc.selectFirst("input[type=radio][name=osex_z][checked]");
                if (checkedRadioZ != null) {
                    String genderValue = checkedRadioZ.attr("value");
                    gender = "M".equals(genderValue) ? "남" : ("F".equals(genderValue) ? "여" : null);
                }
            } else {
                ownerName = "";
                ownerPhone = "";
            }

            Document editDoc = getEditPage(mmUid, cookie);
            String memo = getMemo(editDoc);

            return new String[]{ownerName, ownerPhone, memo, detailAddr, verfMthdNm, mccpNm, gender};
        });
    }

    /**
     * 선방 메모 조회
     */
    public Mono<String[]> getSunbangMemo(String url) {
        return Mono.fromCallable(() -> {
            String redirectedUrl = getRedirectedUrl(url);
            if (redirectedUrl == null) {
                return new String[]{"리디렉션 실패", ""};
            }

            String mmUid = redirectedUrl.split("mm_uid=")[1];
            String cookie = getHomesdidCookies(redirectedUrl);

            Document editDoc = getEditPage(mmUid, cookie);
            String memo = getMemo(editDoc);

            Element adrHo = editDoc.getElementById("adr_ho");
            String detailAddr = adrHo != null ? adrHo.attr("value") : "";

            return new String[]{memo, detailAddr};
        });
    }
}
