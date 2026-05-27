package com.realty.Realtymate.types;

import java.util.HashMap;
import java.util.Map;

public class GONGSIL_USERUID_DICT {
    private static final Map<String, Map<String, String>> dict = new HashMap<>();

    /**
     * Google Sheets에서 로드한 데이터로 전체 dict를 초기화합니다.
     * @param data {등록번호: {uid, name, prtn_uid}} 형태의 맵
     */
    public static void loadAll(Map<String, Map<String, String>> data) {
        dict.clear();
        dict.putAll(data);
        System.out.println("[gongsil] GONGSIL_USERUID_DICT 로드 완료: " + dict.size() + "개");
    }

    /**
     * 등록번호로 uid 조회
     */
    public static String get(String establishRegistrationNo) {
        Map<String, String> info = dict.get(establishRegistrationNo);
        return info != null ? info.get("uid") : null;
    }

    /**
     * 등록번호로 전체 정보 조회
     */
    public static Map<String, String> getInfo(String establishRegistrationNo) {
        return dict.get(establishRegistrationNo);
    }

    /**
     * 등록번호 존재 여부 확인
     */
    public static boolean contains(String establishRegistrationNo) {
        return dict.containsKey(establishRegistrationNo);
    }

    /**
     * prtn_uid 업데이트 (Google Sheets에서 가져온 값 저장)
     */
    public static void updatePrtnUid(String establishRegistrationNo, String prtnUid) {
        Map<String, String> info = dict.get(establishRegistrationNo);
        if (info != null) {
            info.put("prtn_uid", prtnUid);
        } else {
            // 새로운 등록번호인 경우 추가
            Map<String, String> newInfo = new HashMap<>();
            newInfo.put("prtn_uid", prtnUid);
            dict.put(establishRegistrationNo, newInfo);
        }
    }

    /**
     * 전체 dict 반환 (Google Sheets 연동용)
     */
    public static Map<String, Map<String, String>> getAll() {
        return dict;
    }
}
