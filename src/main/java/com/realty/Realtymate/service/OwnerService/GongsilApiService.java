package com.realty.Realtymate.service.OwnerService;

import com.fasterxml.jackson.databind.JsonNode;
import com.realty.Realtymate.service.GoogleSheetApi.GoogleSheetService;
import com.realty.Realtymate.service.naverApi.NaverApiService;
import com.realty.Realtymate.types.GONGSIL_USERUID_DICT;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class GongsilApiService {

    private final WebClient webClient;

    @Autowired
    private NaverApiService naverApiService;

    @Autowired
    private GoogleSheetService googleSheetService;

    public GongsilApiService(WebClient.Builder webClientBuilder) {
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
     * 네이버 매물 번호로부터 prtn_uid와 rname 추출 (Python 방식)
     * @param articleNo 네이버 매물 번호
     * @return Map with "prtn_uid" and "rname"
     */
    private Mono<Map<String, String>> extractPrtnUidFromGongsilUrl(String articleNo) {
        return Mono.fromCallable(() -> {
            try {
                String redirectUrl = "http://n.gongsilclub.com/item/naver_view.asp?itemno=" + articleNo;

                byte[] bytes = webClient.get()
                        .uri(redirectUrl)
                        .retrieve()
                        .bodyToMono(byte[].class)
                        .block();

                String html = new String(bytes, "EUC-KR");
                Document redirectDoc = Jsoup.parse(html);

                // 리다이렉트 페이지에서 실제 URL로 이동
                Element link = redirectDoc.selectFirst("a[href]");
                if (link == null) {
                    System.out.println("[DEBUG] 리다이렉트 링크 없음");
                    Map<String, String> emptyResult = new HashMap<>();
                    emptyResult.put("prtn_uid", "");
                    emptyResult.put("rname", "");
                    return emptyResult;
                }

                String href = link.attr("href");
                String actualUrl = "http://n.gongsilclub.com" + href;

                byte[] actualBytes = webClient.get()
                        .uri(actualUrl)
                        .retrieve()
                        .bodyToMono(byte[].class)
                        .block();

                Document doc = parseHtml(actualBytes);

                // rname 추출
                Element rnameInput = doc.getElementById("rname");
                String rname = rnameInput != null ? rnameInput.attr("value") : null;

                // prtn_uid 추출 (MainPhotoTr의 background-image에서)
                Element mainPhotoTr = doc.getElementById("MainPhotoTr");
                if (mainPhotoTr != null) {
                    Element td = mainPhotoTr.selectFirst("td[style]");
                    if (td != null) {
                        String style = td.attr("style");

                        // 정규식으로 이미지 URL 추출: url('...')
                        Pattern urlPattern = Pattern.compile("url\\(['\"]?([^'\"\\)]+)['\"]?\\)");
                        Matcher urlMatcher = urlPattern.matcher(style);

                        if (urlMatcher.find()) {
                            String imgUrl = urlMatcher.group(1);

                            // _숫자_wt.jpg 패턴에서 숫자 추출
                            Pattern prtnPattern = Pattern.compile("_(\\d+)_wt\\.jpg");
                            Matcher prtnMatcher = prtnPattern.matcher(imgUrl);

                            if (prtnMatcher.find()) {
                                String prtnUid = prtnMatcher.group(1);
                                Map<String, String> result = new HashMap<>();
                                result.put("prtn_uid", prtnUid);
                                result.put("rname", rname != null ? rname : "");
                                System.out.println("[DEBUG] prtn_uid 추출 성공: " + prtnUid + ", rname: " + rname);
                                return result;
                            } else {
                                System.out.println("[DEBUG] prtn_uid 패턴 매칭 실패");
                            }
                        } else {
                            System.out.println("[DEBUG] img_url 추출 실패");
                        }
                    } else {
                        System.out.println("[DEBUG] td 또는 style 없음");
                    }
                } else {
                    System.out.println("[DEBUG] MainPhotoTr 없음");
                }
            } catch (Exception e) {
                System.err.println("[DEBUG] Exception 발생: " + e.getClass().getName() + ": " + e.getMessage());
                e.printStackTrace();
            }

            // 실패 시 빈 값 반환
            Map<String, String> emptyResult = new HashMap<>();
            emptyResult.put("prtn_uid", "");
            emptyResult.put("rname", "");
            System.out.println("[DEBUG] prtn_uid를 찾지 못함 - 반환 (빈 값)");
            return emptyResult;
        });
    }

    /**
     * prtn_uid를 사용하여 공실클럽 매물 리스트 조회 (Python 방식)
     * @param articleNo 네이버 매물 번호
     * @param prtnUid prtn_uid
     * @return [memo, detailAddr]
     */
    private Mono<String[]> getGongsilMemoByPrtnUidInternal(String articleNo, String prtnUid) {
        return Mono.fromCallable(() -> {
            String url = "https://land.gongsilclub.com/mmc/memul/memulList.asp";

            // 쿼리 파라미터 구성 (Python과 동일)
            StringBuilder queryParams = new StringBuilder();
            queryParams.append("?s_step=")
                    .append("&mm_uid=")
                    .append("&url=")
                    .append("&newimgChk=")
                    .append("&currentpage=1")
                    .append("&s_orderby=")
                    .append("&s_orderby2=")
                    .append("&s_rlsttype_cd=")
                    .append("&s_dealtype_cd=")
                    .append("&VRFC_TYPE=")
                    .append("&s_brkg_memo=")
                    .append("&s_area=")
                    .append("&DONG_NM=")
                    .append("&ADR_HO=")
                    .append("&ADR_BUNJI=")
                    .append("&ADR_BUNJI2=")
                    .append("&s_startdate=")
                    .append("&s_enddate=")
                    .append("&s_naver_uid=").append(articleNo)
                    .append("&s_mm_uid=")
                    .append("&x=32")
                    .append("&y=11")
                    .append("&s_landofgsid=전체")
                    .append("&s_order=")
                    .append("&s_proc=all")
                    .append("&s_viewCount=15");

            String fullUrl = url + queryParams.toString();

            byte[] responseBytes = webClient.get()
                    .uri(fullUrl)
                    .header("Cookie", String.format("gsncookie=siteNo=1000&userid=cw2025&prtn_uid=%s", prtnUid))
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8")
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/144.0.0.0 Safari/537.36")
                    .header("Referer", "https://land.gongsilclub.com/mmc/memul/memulList.asp")
                    .retrieve()
                    .bodyToMono(byte[].class)
                    .block();

            Document doc = parseHtml(responseBytes);

            String memo = null;
            String detailAddr = null;

            // tbType2 테이블 찾기
            Element table = doc.selectFirst("table.tbType2");

            if (table != null) {
                // 메모 파싱 - 테이블 내 <b> 태그 안의 텍스트 찾기
                Element bTag = table.selectFirst("b");
                if (bTag != null) {
                    memo = bTag.text().trim();
                }

                // 상세주소 파싱 - 테이블 내 viewMemul3를 포함한 모든 a 태그 찾기
                Elements links = table.select("a[href*=viewMemul3]");

                // 링크 순서: [0]=공실클럽번호, [1]=네이버번호, [2]=주소, [3]=상세주소
                if (links.size() >= 4) {
                    detailAddr = links.get(3).text().trim();
                } else if (links.size() >= 3) {
                    // 주소가 없는 경우 3번째가 상세주소일 수도
                    detailAddr = links.get(2).text().trim();
                }
            }

            return new String[]{
                    memo != null ? memo : "",
                    detailAddr != null ? detailAddr : ""
            };
        });
    }

    /**
     * 공실클럽 메모만 조회 (dict에 uid가 없는 경우 사용)
     * - 예외를 던지지 않고, 실패 시 빈 값 반환
     *
     * @param url 네이버 부동산 URL
     * @param establishRegistrationNo 중개사 등록번호
     * @param photoCount 사진 개수
     * @param realtorId 중개소 ID
     * @return [memo, detailAddr] - 실패 시 빈 값
     */
    public Mono<String[]> getGongsilMemoOnly(String url, String establishRegistrationNo,
                                              int photoCount, String realtorId) {
        return Mono.fromCallable(() -> {
            String naverItemNo = url.split("uid=")[1];
            String[] emptyResult = new String[]{"", ""};

            try {
                // 1. dict에 prtn_uid가 있는 경우 → 바로 조회
                if (GONGSIL_USERUID_DICT.contains(establishRegistrationNo)) {
                    Map<String, String> info = GONGSIL_USERUID_DICT.getInfo(establishRegistrationNo);
                    if (info.containsKey("prtn_uid")) {
                        System.out.println("[공실클럽] prtn_uid 캐시 사용: " + info.get("prtn_uid"));
                        return getGongsilMemoByPrtnUidInternal(naverItemNo, info.get("prtn_uid")).block();
                    }
                }

                // 2. 사진이 있는 경우 → prtn_uid 추출 시도
                if (photoCount > 0) {
                    System.out.println("[공실클럽] 사진 있음, prtn_uid 추출 시도");
                    Map<String, String> extractResult = extractPrtnUidFromGongsilUrl(naverItemNo).block();
                    String prtnUid = extractResult.get("prtn_uid");

                    if (prtnUid != null && !prtnUid.isEmpty()) {
                        GONGSIL_USERUID_DICT.updatePrtnUid(establishRegistrationNo, prtnUid);
                        String rname = extractResult.get("rname");
                        if (rname != null && !rname.isEmpty()) {
                            googleSheetService.saveToGongsilSheet(establishRegistrationNo, rname, prtnUid);
                        }
                        System.out.println("[공실클럽] prtn_uid 추출 성공: " + prtnUid);
                        return getGongsilMemoByPrtnUidInternal(naverItemNo, prtnUid).block();
                    }
                }
                System.out.println(realtorId);
                // 3. 사진 없음 + realtorId 있음 → 다른 매물에서 prtn_uid 찾기
                if (realtorId != null && !realtorId.isEmpty()) {
                    System.out.println("[공실클럽] realtorId로 다른 매물 검색: " + realtorId);
                    String prtnUid = findPrtnUidFromOtherArticles(realtorId, establishRegistrationNo).block();

                    if (prtnUid != null && !prtnUid.isEmpty()) {
                        return getGongsilMemoByPrtnUidInternal(naverItemNo, prtnUid).block();
                    }
                }

                // 4. 모든 방법 실패 → 빈 값 반환 (에러 아님)
                System.out.println("[공실클럽] 메모 조회 불가 - 빈 값 반환");
                return emptyResult;

            } catch (Exception e) {
                System.err.println("[공실클럽] 메모 조회 중 오류: " + e.getMessage());
                return emptyResult;
            }
        });
    }

    /**
     * realtorId로 같은 중개소의 다른 매물에서 prtn_uid 찾기 (Python 로직 포팅)
     */
    private Mono<String> findPrtnUidFromOtherArticles(String realtorId, String establishRegistrationNo) {
        return Mono.fromCallable(() -> {
            try {
                // 1. realtorId로 매물 목록 조회
                JsonNode root = naverApiService.getArticlesByRealtorId(realtorId);
                JsonNode articleList = root.get("articleList");

                if (articleList == null || !articleList.isArray()) {
                    System.out.println("[DEBUG] articleList 없음");
                    return null;
                }

                // 2. 공실클럽 + 사진 있는 매물 찾기
                for (JsonNode article : articleList) {
                    String cpName = article.has("cpName") ? article.get("cpName").asText() : "";

                    if (!cpName.contains("공실클럽")) {
                        continue;
                    }

                    String tempArticleNo = article.has("articleNo") ? article.get("articleNo").asText() : null;
                    if (tempArticleNo == null) {
                        continue;
                    }

                    // 임시 매물의 상세정보 조회
                    JsonNode tempDetail = naverApiService.getArticleDetailRaw(tempArticleNo);
                    JsonNode tempPhotos = tempDetail.get("articlePhotos");

                    if (tempPhotos != null && tempPhotos.isArray() && tempPhotos.size() > 0) {
                        // 사진 있는 매물 발견! → prtn_uid 추출
                        System.out.println("[DEBUG] 사진 있는 매물 발견: " + tempArticleNo);
                        Map<String, String> extractResult = extractPrtnUidFromGongsilUrl(tempArticleNo).block();
                        String prtnUid = extractResult.get("prtn_uid");

                        if (prtnUid != null && !prtnUid.isEmpty()) {
                            GONGSIL_USERUID_DICT.updatePrtnUid(establishRegistrationNo, prtnUid);
                            String rname = extractResult.get("rname");
                            if (rname != null && !rname.isEmpty()) {
                                googleSheetService.saveToGongsilSheet(establishRegistrationNo, rname, prtnUid);
                            }
                            System.out.println("[DEBUG] prtn_uid 추출 성공 (다른 매물에서): " + prtnUid);
                            return prtnUid;
                        }
                    }
                }

                System.out.println("[DEBUG] 사진 있는 매물을 찾지 못함");
                return null;

            } catch (Exception e) {
                System.err.println("[ERROR] realtorId 매물 목록 조회 중 오류: " + e.getMessage());
                e.printStackTrace();
                return null;
            }
        });
    }

    /**
     * 공실클럽 소유자 정보 조회 (Python 방식 - prtn_uid 기반)
     * @param url 네이버 부동산 URL
     * @param verificationTypeName 검증 타입
     * @param useruid 사용자 UID
     * @return [owner_name, owner_phone, memo, detailAddr, verification_method, mccpNm, gender]
     */
    public Mono<String[]> getGongsilOwnerInfo(String url, String verificationTypeName, String useruid) {
        return Mono.fromCallable(() -> {
            String naverItemNo = url.split("uid=")[1];

            // 1. prtn_uid 추출
            Map<String, String> extractResult = extractPrtnUidFromGongsilUrl(naverItemNo).block();
            String prtnUid = extractResult.get("prtn_uid");

            if (prtnUid == null || prtnUid.isEmpty()) {
                System.err.println("[ERROR] prtn_uid 추출 실패 - 기존 방식으로 폴백");
                // 기존 방식으로 폴백
                return getGongsilOwnerInfoLegacy(url, verificationTypeName, useruid).block();
            }

            // 2. prtn_uid를 사용하여 메모/상세주소 조회
            String[] memoResult = getGongsilMemoByPrtnUidInternal(naverItemNo, prtnUid).block();
            String memo = memoResult[0];
            String detailAddr = memoResult[1];

            // 3. 소유자 정보는 기존 방식 사용 (memulReRegForm.asp)
            String[] ownerInfo = getGongsilOwnerInfoLegacy(url, verificationTypeName, useruid).block();

            // 메모와 상세주소를 새로운 방식으로 덮어쓰기
            ownerInfo[2] = memo;
            ownerInfo[3] = detailAddr;

            return ownerInfo;
        });
    }

    /**
     * 기존 방식 - 공실클럽 메모 조회 (폴백용)
     */
    private Mono<String[]> getGongsilMemoLegacy(String url, String verificationTypeName, String useruid) {
        return Mono.fromCallable(() -> {
            String naverItemNo = url.split("uid=")[1];

            String redirectUrl = "http://n.gongsilclub.com/item/naver_view.asp?itemno=" + naverItemNo;
            byte[] redirectBytes = webClient.get()
                    .uri(redirectUrl)
                    .retrieve()
                    .bodyToMono(byte[].class)
                    .block();

            String redirectResponse = new String(redirectBytes, "EUC-KR");
            Document redirectDoc = Jsoup.parse(redirectResponse);
            Element atclNoElement = redirectDoc.getElementById("atclNo");

            if (atclNoElement == null) {
                return new String[]{"", ""};
            }

            String atclNo = atclNoElement.attr("value");

            String detailUrl = String.format(
                    "https://land.gongsilclub.com/mmc/memul/memulReRegForm.asp?s_step=nfail&mm_uid=%s&url=&newimgChk=&currentpage=1&s_orderby=&s_orderby2=&s_rlsttype_cd=&s_dealtype_cd=&VRFC_TYPE=&s_brkg_memo=&s_area=&DONG_NM=&ADR_HO=&ADR_BUNJI=&ADR_BUNJI2=&s_startdate=&s_enddate=&s_naver_uid=&s_mm_uid=&s_order=&s_proc=all&s_viewCount=15",
                    atclNo
            );

            byte[] detailBytes = webClient.get()
                    .uri(detailUrl)
                    .header("Cookie", String.format("gsncookie=PRTN%%5FUID=18753&useruid=%s&userid=jogak2023;", useruid))
                    .retrieve()
                    .bodyToMono(byte[].class)
                    .block();

            Document doc = parseHtml(detailBytes);

            Element brkgMemoElement = doc.getElementById("brkg_memo");
            String memo = brkgMemoElement != null ? brkgMemoElement.text() : "";

            Element adrHoOrgElement = doc.getElementById("adr_ho_org");
            String detailAddr = adrHoOrgElement != null ? adrHoOrgElement.attr("value") : "";

            return new String[]{memo, detailAddr};
        });
    }

    /**
     * 기존 방식 - 공실클럽 소유자 정보 조회 (폴백용)
     */
    private Mono<String[]> getGongsilOwnerInfoLegacy(String url, String verificationTypeName, String useruid) {
        return Mono.fromCallable(() -> {
            String naverItemNo = url.split("uid=")[1];

            String redirectUrl = "http://n.gongsilclub.com/item/naver_view.asp?itemno=" + naverItemNo;
            byte[] redirectBytes = webClient.get()
                    .uri(redirectUrl)
                    .retrieve()
                    .bodyToMono(byte[].class)
                    .block();

            String redirectResponse = new String(redirectBytes, "EUC-KR");
            Document redirectDoc = Jsoup.parse(redirectResponse);
            Element link = redirectDoc.selectFirst("a[href]");

            if (link == null) {
                return new String[]{"리다이렉션 링크를 찾을 수 없습니다", "", "", "", "", "", ""};
            }

            String href = link.attr("href");
            String atclNo = href.split("itemno=")[1];

            String detailUrl = String.format(
                    "https://land.gongsilclub.com/mmc/memul/memulReRegForm.asp?s_step=nfail&mm_uid=%s&url=&newimgChk=&currentpage=1&s_orderby=&s_orderby2=&s_rlsttype_cd=&s_dealtype_cd=&VRFC_TYPE=&s_brkg_memo=&s_area=&DONG_NM=&ADR_HO=&ADR_BUNJI=&ADR_BUNJI2=&s_startdate=&s_enddate=&s_naver_uid=&s_mm_uid=&s_order=&s_proc=all&s_viewCount=15",
                    atclNo
            );

            byte[] detailBytes = webClient.get()
                    .uri(detailUrl)
                    .header("Cookie", String.format("gsncookie=PRTN%%5FUID=18753&useruid=%s&userid=jogak2023;", useruid))
                    .retrieve()
                    .bodyToMono(byte[].class)
                    .block();

            Document doc = parseHtml(detailBytes);

            Element brkgMemoElement = doc.getElementById("brkg_memo");
            String memo = brkgMemoElement != null ? brkgMemoElement.text() : "";

            Element adrHoOrgElement = doc.getElementById("adr_ho_org");
            String detailAddr = adrHoOrgElement != null ? adrHoOrgElement.attr("value") : "";

            String ownerName = "";
            Element vrfcOnameElement = doc.getElementById("vrfc_oname");
            Element vrfcNameElement = doc.getElementById("vrfc_name");

            if (vrfcOnameElement != null && !vrfcOnameElement.attr("value").trim().isEmpty()) {
                ownerName = vrfcOnameElement.attr("value").trim();
            } else if (vrfcNameElement != null && !vrfcNameElement.attr("value").trim().isEmpty()) {
                ownerName = vrfcNameElement.attr("value").trim();
            }

            String ownerPhone = "";
            Element vrfcOcphoneElement = doc.getElementById("vrfc_ocphone");
            Element vrfcCphoneElement = doc.getElementById("vrfc_cphone");

            if (vrfcOcphoneElement != null && !vrfcOcphoneElement.attr("value").trim().isEmpty()) {
                ownerPhone = vrfcOcphoneElement.attr("value").trim();
            } else if (vrfcCphoneElement != null && !vrfcCphoneElement.attr("value").trim().isEmpty()) {
                ownerPhone = vrfcCphoneElement.attr("value").trim();
            }

            String verificationMethod = "";
            Element vrfcTypeElement = doc.getElementById("vrfc_type");
            if (vrfcTypeElement != null) {
                String vrfcType = vrfcTypeElement.attr("value");
                if ("N".equals(vrfcType)) {
                    verificationMethod = "신홍보확인서";
                } else if ("O".equals(vrfcType)) {
                    verificationMethod = "모바일";
                }
            }

            String mccpNm = null;
            Element otelecomElement = doc.getElementById("otelecom");
            if (otelecomElement != null) {
                String otelecom = otelecomElement.attr("value");
                switch (otelecom) {
                    case "01":
                        mccpNm = "SKT";
                        break;
                    case "02":
                        mccpNm = "KT";
                        break;
                    case "03":
                        mccpNm = "LG";
                        break;
                }
            }

            String gender = null;
            Element osexElement = doc.getElementById("osex");
            if (osexElement != null) {
                String osex = osexElement.attr("value");
                if ("M".equals(osex)) {
                    gender = "남";
                } else if ("F".equals(osex)) {
                    gender = "여";
                }
            }

            return new String[]{ownerName, ownerPhone, memo, detailAddr, verificationMethod, mccpNm, gender};
        });
    }

    /**
     * HTML 바이트를 올바른 인코딩으로 파싱합니다.
     * meta charset 태그를 감지하여 사용하며, 없으면 UTF-8로 폴백합니다.
     */
    private Document parseHtml(byte[] bytes) {
        Document metaDoc = Jsoup.parse(new String(bytes, StandardCharsets.ISO_8859_1));
        Charset charset = StandardCharsets.UTF_8;
        Element metaCharset = metaDoc.selectFirst("meta[charset]");
        if (metaCharset != null) {
            charset = Charset.forName(metaCharset.attr("charset"));
        } else {
            Element metaHttp = metaDoc.selectFirst("meta[http-equiv='Content-Type']");
            if (metaHttp != null && metaHttp.attr("content").contains("charset=")) {
                charset = Charset.forName(metaHttp.attr("content").split("charset=")[1].trim().split("[;\\s]")[0]);
            }
        }
        return Jsoup.parse(new String(bytes, charset));
    }
}
