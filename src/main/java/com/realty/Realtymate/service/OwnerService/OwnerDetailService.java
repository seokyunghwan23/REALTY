package com.realty.Realtymate.service.OwnerService;

import com.realty.Realtymate.model.OwnerDetailDto;
import com.realty.Realtymate.service.peterpanzApi.PeterpanzApiService;
import com.realty.Realtymate.types.GONGSIL_USERUID_DICT;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
public class OwnerDetailService {

    @Autowired
    private GongsilApiService gongsilApiService;

    @Autowired
    private BankApiService bankApiService;

    @Autowired
    private PosApiService posApiService;

    @Autowired
    private SunbangApiService sunbangApiService;

    @Autowired
    private ServeApiService serveApiService;

    @Autowired
    private WooriApiService wooriApiService;
    @Autowired
    private PeterpanApiService peterpanApiService;
    @Autowired
    private TencomzApiService tencomzApiService;

    /**
     * 매물 상세 정보 조회 (소유자 + 메모 통합)
     *
     * @param url 매물 URL
     * @param platform 플랫폼 이름
     * @param verificationTypeName 검증 타입 이름
     * @param verificationTypeCode 검증 타입 코드
     * @param establishRegistrationNo 중개사 등록번호
     * @param address 주소
     * @param myProperties 내 매물 조회 여부
     * @param authorization Bearer 토큰 (내 매물 조회 시 필요)
     * @param photoCount 사진 개수 (공실클럽 조회용)
     * @param realtorId 중개소 ID (공실클럽 조회용)
     * @return OwnerDetailDto
     */
    public Mono<OwnerDetailDto> getOwnerDetail(
            String url,
            String platform,
            String verificationTypeName,
            String verificationTypeCode,
            String establishRegistrationNo,
            String address,
            boolean myProperties,
            String authorization,
            int photoCount,
            String realtorId
    ) {
        return Mono.fromCallable(() -> {

            String owner = null;
            String contact = null;
            String memo = null;
            String detailAddr = null;
            String verfMthdNm = null;
            String mccpNm = null;
            String gender = null;
            try {
                // 내 매물 조회 (써브 전용)
                if (myProperties && url.contains("serve") && authorization != null) {
                    String[] result = serveApiService.getMyPropertyDetail(url, authorization).block();
                    if (result != null && result.length >= 7) {
                        owner = result[0];
                        contact = result[1];
                        memo = result[2];
                        detailAddr = result[3];
                        verfMthdNm = result[4];
                        mccpNm = result[5];
                        gender = result[6];
                    }
                }
                // 부동산써브 (기존)
                else if (url.contains("serve")) {
                    String[] result = serveApiService.getServeOwnerInfo(url, verificationTypeName).block();
                    if (result != null && result.length >= 7) {
                        owner = result[0];
                        contact = result[1];
                        memo = result[2];
                        detailAddr = result[3];
                        verfMthdNm = result[4];
                        mccpNm = result[5];
                        gender = result[6];
                    }
                }
                // 부동산뱅크
                else if (url.contains("neonet")) {
                    String[] result = bankApiService.getBankOwnerInfo(url, verificationTypeName).block();
                    if (result != null && result.length >= 7) {
                        owner = result[0];
                        contact = result[1];
                        memo = result[2];
                        detailAddr = result[3];
                        verfMthdNm = result[4];
                        mccpNm = result[5];
                        gender = result[6];
                    }
                }
                // 부동산포스
                else if (url.contains("rfine")) {
                    String[] result = posApiService.getPosOwnerInfo(url, verificationTypeName).block();
                    if (result != null && result.length >= 2) {
                        owner = result[0];
                        contact = result[1];
                    }
                }
                // 공실클럽
                else if (url.contains("gongsilclub")) {
                    String useruid = GONGSIL_USERUID_DICT.get(establishRegistrationNo);

                    if (useruid != null) {
                        // dict에 uid 있음 → 소유자 + 메모 전체 조회
                        System.out.println("[공실클럽] dict에 uid 있음 → 소유자 조회");
                        String[] ownerResult = gongsilApiService.getGongsilOwnerInfo(url, verificationTypeName, useruid).block();
                        if (ownerResult != null && ownerResult.length >= 7) {
                            owner = ownerResult[0];
                            contact = ownerResult[1];
                            memo = ownerResult[2];
                            detailAddr = ownerResult[3];
                            verfMthdNm = ownerResult[4];
                            mccpNm = ownerResult[5];
                            gender = ownerResult[6];
                        }
                    } else {
                        // dict에 uid 없음 → 메모만 조회 (에러 없이)
                        System.out.println("[공실클럽] dict에 uid 없음 → 메모만 조회");
                        String[] memoResult = gongsilApiService.getGongsilMemoOnly(
                                url,
                                establishRegistrationNo,
                                photoCount,
                                realtorId
                        ).block();

                        if (memoResult != null && memoResult.length >= 2) {
                            memo = memoResult[0];
                            detailAddr = memoResult[1];
                        }
                        // owner, contact는 빈 값 유지
                    }
                }
                // 우리집부동산
                else if (platform != null && platform.contains("우리집부동산")) {
                    String[] result = wooriApiService.getWooriOwnerInfo(url, verificationTypeCode).block();
                    if (result != null && result.length >= 7) {
                        owner = result[0];
                        contact = result[1];
                        memo = result[2];
                        detailAddr = result[3];
                        verfMthdNm = result[4];
                        mccpNm = result[5];
                        gender = result[6];
                    }
                }
                // 피터팬
                else if (platform != null && platform.contains("피터팬의 좋은방구하기")) {
                    String[] result = peterpanApiService.getPeterpanzOwnerInfo(url, verificationTypeCode).block();
                    if (result != null) {
                        owner = result[0];
                        contact = result[1];
                        memo = result[2];
                        detailAddr = result[3];

                    }
                }
                // 텐컴즈
                else if (url.contains("tencom.co.kr")) {
                    String[] result = tencomzApiService.getTencomzOwnerInfo(url, verificationTypeName).block();
                    if (result != null && result.length >= 7) {
                        owner = result[0];
                        contact = result[1];
                        memo = result[2];
                        detailAddr = result[3];
                        verfMthdNm = result[4];
                        mccpNm = result[5];
                        gender = result[6];
                    }
                }
                // 선방
                else if (platform != null && platform.contains("선방")) {
                    String[] result = sunbangApiService.getSunbangOwnerInfo(url, verificationTypeName).block();
                    if (result != null && result.length >= 7) {
                        owner = result[0];
                        contact = result[1];
                        memo = result[2];
                        detailAddr = result[3];
                        verfMthdNm = result[4];
                        mccpNm = result[5];
                        gender = result[6];
                    }
                }
                else {
                    System.out.println("⚠ 지원하지 않는 플랫폼");
                }

            } catch (Exception e) {
                // NOT_MY_PROPERTY 에러는 상위로 전파
                if (e.getMessage() != null && e.getMessage().contains("NOT_MY_PROPERTY")) {
                    throw e;
                }
                System.err.println("❌ API 호출 중 오류 발생: " + e.getMessage());
            }

            // 결과 조합
            OwnerDetailDto dto = OwnerDetailDto.builder()
                    .address(address != null ? address : "정보 없음")
                    .detailAddress(detailAddr != null ? detailAddr : "")
                    .platform(platform != null ? platform : "")
                    .owner(owner != null ? owner : "")
                    .contact(contact != null ? contact : "")
                    .verificationMethod(verfMthdNm != null ? verfMthdNm : "")
                    .managementOffice(buildManagementOffice(mccpNm, gender))
                    .gender(gender != null ? gender : "")
                    .memo(memo != null ? memo : "")
                    .build();


            return dto;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 통신사와 성별 정보로 관리소 필드 구성
     */
    private String buildManagementOffice(String mccpNm, String gender) {
        if (mccpNm == null && gender == null) {
            return "";
        }
        if (mccpNm != null && gender != null) {
            return mccpNm + " / " + gender;
        }
        return mccpNm != null ? mccpNm : gender;
    }
}
