package com.realty.Realtymate.service.GoogleSheetApi;

import java.util.List;
import java.util.Map;

public interface GoogleSheetService {

    /**
     * 데이터를 Google Sheets에 저장합니다.
     * @param data 저장할 데이터 (키-값 쌍)
     * @return 저장 성공 여부
     */
    boolean saveToGoogleSheet(Map<String, Object> data);

    /**
     * Google Sheets에서 모든 매물 데이터를 읽어옵니다.
     * @return 매물 데이터 리스트 (각 행은 Map으로 표현)
     */
    List<Map<String, Object>> getAllProperties();

    /**
     * 특정 Google Sheet에서 모든 매물 데이터를 읽어옵니다.
     * @param sheetId  스프레드시트 ID
     * @param sheetName 워크시트 이름
     * @return 매물 데이터 리스트
     */
    List<Map<String, Object>> getAllProperties(String sheetId, String sheetName);

    /**
     * 연락처 워크시트에 데이터를 저장합니다.
     * - 주소 기준 중복 체크: 같은 주소면 기존 행에 개행으로 추가
     * - 연락처 중복 체크: 같은 주소 + 같은 연락처면 저장 안 함
     * @param data 저장할 데이터 (키-값 쌍)
     * @return 저장 성공 여부
     */
    boolean saveToContactSheet(Map<String, Object> data);

    /**
     * 연락처 워크시트에서 주소로 연락처가 있는지 확인합니다.
     * @param address 확인할 주소
     * @return true: 주소가 있고 연락처도 있음, false: 주소가 없거나 연락처가 없음
     */
    boolean hasContactByAddress(String address);

    /**
     * REALTYMATE 스프레드시트의 gongsil 워크시트에서 전체 데이터를 로드합니다.
     * @return {등록번호: {uid, name, prtn_uid}} 형태의 맵
     */
    Map<String, Map<String, String>> loadGongsilDict();

    /**
     * REALTYMATE 스프레드시트의 gongsil 워크시트에 공실클럽 uid 정보를 저장합니다.
     * - 등록번호가 이미 존재하면 name, prtn_uid만 업데이트 (uid 유지)
     * - 등록번호가 없으면 새 행 추가
     * @param establishRegistrationNo 중개사 등록번호
     * @param name 중개소 이름 (rname)
     * @param prtnUid prtn_uid
     * @return 저장 성공 여부
     */
    boolean saveToGongsilSheet(String establishRegistrationNo, String name, String prtnUid);
}