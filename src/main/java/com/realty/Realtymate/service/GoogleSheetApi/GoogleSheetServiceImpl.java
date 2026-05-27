package com.realty.Realtymate.service.GoogleSheetApi;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.api.services.sheets.v4.model.AppendValuesResponse;
import com.google.api.services.sheets.v4.model.ValueRange;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class GoogleSheetServiceImpl implements GoogleSheetService {

    // --- 설정 (application.properties 등에서 주입) ---
    private static final String APPLICATION_NAME = "Realtymate-GoogleSheetSaver";
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();

    @Value("${google.sheets.spreadsheet-id}") // 스프레드시트 ID로 변경 (파이썬 코드의 이름 대신 ID 사용 권장)
    private String spreadsheetId;

    @Value("${google.sheets.worksheet-name}") // 파이썬 코드와 동일한 워크시트 이름
    private String worksheetName;

    @Value("${google.sheets.property-worksheet-name:매물}") // 매물 데이터 워크시트 이름 (기본값: 매물)
    private String propertyWorksheetName;

    @Value("${google.sheets.realtymate-spreadsheet-id}")
    private String realtymateSpreadsheetId;

    // gongsil 워크시트 관련 상수
    private static final String GONGSIL_WORKSHEET_NAME = "gongsil";
    private static final List<String> GONGSIL_HEADERS = Arrays.asList(
            "등록번호", "uid", "name", "prtn_uid"
    );

    // 연락처 워크시트 관련 상수
    private static final String CONTACT_WORKSHEET_NAME = "연락처";
    private static final List<String> CONTACT_HEADERS = Arrays.asList(
            "주소", "소유자", "연락처", "상세주소", "확인방식", "통신사", "성별"
    );
    private static final Map<String, String> CONTACT_KEY_MAP = Map.of(
            "주소", "address",
            "소유자", "owner",
            "연락처", "contact",
            "상세주소", "detailAddress",
            "확인방식", "verificationMethod",
            "통신사", "managementOffice",
            "성별", "gender"
    );

    // 서비스 계정 키 파일 경로 (resources 폴더 내)
    @Value("classpath:${google.sheets.service-account-file}")
    private Resource serviceAccountFile;

    private final ResourceLoader resourceLoader;
    private Sheets sheetsService;
    private List<String> cachedHeaders; // 헤더 캐싱

    // 파이썬의 ENGLISH_TO_KOREAN_HEADER_MAP과 동일한 역할
    private static final Map<String, String> ENGLISH_TO_KOREAN_HEADER_MAP = Map.of(
            "address", "주소",
            "owner", "소유자",
            "contact", "연락처",
            "memo", "메모",
            "detailAddress", "상세주소",
            "verificationMethod", "확인방식",
            "managementOffice", "통신사",
            "gender", "성별",
            "timestamp", "시간",
            "employeeName", "사용자"
    );

    // 파이썬의 KOREAN_TO_ENGLISH_KEY_MAP과 동일한 역할
    private static final Map<String, String> KOREAN_TO_ENGLISH_KEY_MAP =
            ENGLISH_TO_KOREAN_HEADER_MAP.entrySet().stream()
                    .collect(Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey));

    public GoogleSheetServiceImpl(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    /**
     * Google Sheets 서비스 객체를 초기화하고 인증합니다. (파이썬 코드의 gspread.authorize와 동일)
     */
    @PostConstruct
    public void init() {
        try {
            Credential credential = authorize();
            sheetsService = new Sheets.Builder(
                    GoogleNetHttpTransport.newTrustedTransport(),
                    JSON_FACTORY,
                    credential)
                    .setApplicationName(APPLICATION_NAME)
                    .build();
            System.out.println("Google Sheets 서비스 초기화 및 인증 성공!");
            // 초기화 시 헤더를 한 번 읽어 캐싱
            cachedHeaders = getOrCreateHeaders();
            // gongsil dict를 Sheets에서 로드하여 초기화
            com.realty.Realtymate.types.GONGSIL_USERUID_DICT.loadAll(loadGongsilDict());
        } catch (IOException | GeneralSecurityException e) {
            System.err.println("Google Sheets 인증 또는 초기화 실패: " + e.getMessage());
            sheetsService = null;
        }
    }

    /**
     * 서비스 계정 인증 정보를 로드합니다.
     */
    private Credential authorize() throws IOException {
        // gspread의 SCOPE와 동일
        List<String> scopes = Arrays.asList(SheetsScopes.SPREADSHEETS, SheetsScopes.DRIVE_FILE);

        if (!serviceAccountFile.exists()) {
            throw new IOException("서비스 계정 파일이 존재하지 않습니다: " + serviceAccountFile.getFilename());
        }

        // 파이썬의 Credentials.from_service_account_file 역할
        return GoogleCredential.fromStream(serviceAccountFile.getInputStream())
                .createScoped(scopes);
    }

    /**
     * 스프레드시트의 첫 번째 행(헤더)을 읽거나, 없으면 생성합니다.
     */
    private List<String> getOrCreateHeaders() throws IOException {
        if (sheetsService == null) {
            return Collections.emptyList();
        }

        try {
            // 헤더 범위: 워크시트 이름!A1:Z1 (적절히 조정 가능)
            String range = "'" + worksheetName + "'!1:1";
            ValueRange response = sheetsService.spreadsheets().values()
                    .get(spreadsheetId, range)
                    .execute();

            List<List<Object>> values = response.getValues();

            if (values == null || values.isEmpty() || values.get(0).isEmpty()) {
                // 헤더가 없으면 새로 추가 (파이썬 코드의 worksheet.append_row 역할)
                List<Object> newHeaders = new ArrayList<>(ENGLISH_TO_KOREAN_HEADER_MAP.values());
                ValueRange body = new ValueRange().setValues(Collections.singletonList(newHeaders));

                sheetsService.spreadsheets().values()
                        .append(spreadsheetId, range, body)
                        .setValueInputOption("RAW")
                        .execute();
                System.out.println("Google Sheet에 헤더 행이 추가되었습니다: " + newHeaders);
                return newHeaders.stream().map(Object::toString).collect(Collectors.toList());
            } else {
                // 기존 헤더 반환 (파이썬 코드의 worksheet.row_values(1) 역할)
                return values.get(0).stream().map(Object::toString).collect(Collectors.toList());
            }
        } catch (IOException e) {
            System.err.println("헤더를 읽거나 생성하는 중 오류 발생. 스프레드시트 ID 또는 워크시트 이름을 확인하세요: " + e.getMessage());
            throw e; // 예외를 던져 초기화 실패를 알림
        }
    }

    /**
     * 데이터를 Google Sheets에 저장합니다. (파이썬 코드의 save_to_google_sheet와 동일)
     * @param data 저장할 데이터 (Map<String, Object> 형태)
     * @return 성공 여부
     */
    @Override
    public boolean saveToGoogleSheet(Map<String, Object> data) {
        if (sheetsService == null) {
            System.out.println("Google Sheets에 연결되지 않아 저장을 건넙니다.");
            return false;
        }
        try {
            Map<String, Object> dataToSave = new HashMap<>(data);
            // dataToSave에 이미 RealtyWebServiceImpl에서 생성한 문자열 타임스탬프가 있습니다.

            // 스프레드시트의 헤더 순서에 맞춰 데이터를 정렬합니다.
            List<Object> rowData = new ArrayList<>();
            for (String headerInSheet : cachedHeaders) {
                // 헤더에 해당하는 영어 키를 찾습니다.
                String englishKey = KOREAN_TO_ENGLISH_KEY_MAP.get(headerInSheet);

                if (englishKey != null) {
                    // 키가 있으면 데이터를 가져오고, 없으면 빈 문자열('')을 사용합니다.
                    Object value = dataToSave.getOrDefault(englishKey, "");

                    String stringValue = value != null ? value.toString() : "";

                    // ✨ 타임스탬프 필드인 경우, 문자열 앞에 작은따옴표(')를 붙입니다.
                    if (englishKey.equals("timestamp") && !stringValue.isEmpty()) {
                        stringValue = "'" + stringValue;
                    }

                    rowData.add(stringValue);
                } else {
                    // 시트에 있지만 매핑 맵에 없는 헤더는 빈 값으로 채웁니다.
                    rowData.add("");
                }
            }

            // 데이터 행 추가 (파이썬 코드의 worksheet.append_row(row_data) 역할)
            ValueRange body = new ValueRange().setValues(Collections.singletonList(rowData));
            AppendValuesResponse result = sheetsService.spreadsheets().values()
                    .append(spreadsheetId, "'" + worksheetName + "'!A:Z", body) // A:Z 범위는 실제 컬럼 수에 따라 조정 가능
                    .setValueInputOption("USER_ENTERED") // 포맷/수식 인식을 위해 USER_ENTERED 사용 권장
                    .execute();

            String address = dataToSave.getOrDefault("address", "알 수 없는 주소").toString();
            System.out.println("Google Sheet 저장 완료: " + address);
            // 실제 환경에서는 로그 기록이나 사용자 알림 로직 추가
            return true;

        } catch (IOException e) {
            String address = data.getOrDefault("address", "알 수 없는 주소").toString();
            System.err.println("Google Sheet 저장 중 오류 발생 (" + address + "): " + e.getMessage());
            // gspread의 예외 처리와 유사하게 동작
            // 예외 종류에 따라 로그 기록이나 알림 로직 세분화 필요
            return false;
        } catch (Exception e) {
            String address = data.getOrDefault("address", "알 수 없는 주소").toString();
            System.err.println("Google Sheet 저장 중 예상치 못한 오류 발생 (" + address + "): " + e.getMessage());
            return false;
        }
    }

    /**
     * 특정 Google Sheet에서 모든 매물 데이터를 읽어옵니다.
     */
    @Override
    public List<Map<String, Object>> getAllProperties(String sheetId, String sheetName) {
        List<Map<String, Object>> result = new ArrayList<>();

        if (sheetsService == null) {
            System.err.println("Google Sheets에 연결되지 않았습니다.");
            return result;
        }

        try {
            // 전체 데이터 범위 읽기 (A:Z)
            // sheetName이 없으면 기본값 사용
            String targetSheet = (sheetName != null && !sheetName.isEmpty()) ? sheetName : propertyWorksheetName;
            System.out.println("조회할 시트 이름: " + targetSheet);

            // 한글 시트 이름을 위해 작은따옴표로 감싸기
            String range = "'" + targetSheet + "'!A:Z";
            System.out.println("조회할 범위: " + range);

            ValueRange response = sheetsService.spreadsheets().values()
                    .get(sheetId, range)
                    .execute();

            List<List<Object>> values = response.getValues();

            if (values == null || values.isEmpty()) {
                System.out.println("스프레드시트(" + sheetId + ")에 데이터가 없습니다.");
                return result;
            }

            // 첫 번째 행은 헤더
            List<Object> headers = values.get(0);
            List<String> headerStrings = headers.stream()
                    .map(h -> h != null ? h.toString() : "")
                    .collect(Collectors.toList());

            System.out.println("========================================");
            System.out.println("Google Sheets 개별 사용자 매물 조회");
            System.out.println("ID: " + sheetId);
            System.out.println("시트: " + targetSheet);
            System.out.println("헤더: " + headerStrings);
            System.out.println("총 데이터 행 수: " + (values.size() - 1));

            // 데이터 행 처리 (헤더 제외)
            for (int i = 1; i < values.size(); i++) {
                List<Object> row = values.get(i);
                Map<String, Object> rowData = new LinkedHashMap<>();

                for (int j = 0; j < headerStrings.size(); j++) {
                    String header = headerStrings.get(j);
                    Object value = (j < row.size()) ? row.get(j) : "";

                    // 숫자 변환 시도
                    if (value != null && !value.toString().isEmpty()) {
                        String strValue = value.toString().trim();
                        try {
                            if (strValue.matches("-?\\d+")) {
                                rowData.put(header, Long.parseLong(strValue));
                            } else if (strValue.matches("-?\\d+\\.\\d+")) {
                                rowData.put(header, Double.parseDouble(strValue));
                            } else {
                                rowData.put(header, strValue);
                            }
                        } catch (NumberFormatException e) {
                            rowData.put(header, strValue);
                        }
                    } else {
                        rowData.put(header, "");
                    }
                }

                // 보증금이 공백인 매물은 제외
                Object deposit = rowData.get("보증금");
                if (deposit == null || deposit.toString().trim().isEmpty()) {
                    continue; // 보증금이 없으면 건너뛰기
                }

                result.add(rowData);
            }

            System.out.println("조회 완료: " + result.size() + "개 매물 (보증금 공백 제외)");
            System.out.println("========================================");

            return result;

        } catch (IOException e) {
            System.err.println("Google Sheets 데이터 읽기 실패 (" + sheetId + "): " + e.getMessage());
            e.printStackTrace();
            return result;
        }
    }

    /**
     * Google Sheets에서 모든 매물 데이터를 읽어옵니다.
     * @return 매물 데이터 리스트 (각 행은 헤더를 키로 하는 Map)
     */
    @Override
    public List<Map<String, Object>> getAllProperties() {
        List<Map<String, Object>> result = new ArrayList<>();

        if (sheetsService == null) {
            System.err.println("Google Sheets에 연결되지 않았습니다.");
            return result;
        }

        try {
            // 전체 데이터 범위 읽기 (A:Z는 필요에 따라 조정)
            // 한글 시트 이름을 위해 작은따옴표로 감싸기
            String range = "'" + propertyWorksheetName + "'!A:Z";
            ValueRange response = sheetsService.spreadsheets().values()
                    .get(spreadsheetId, range)
                    .execute();

            List<List<Object>> values = response.getValues();

            if (values == null || values.isEmpty()) {
                System.out.println("스프레드시트에 데이터가 없습니다.");
                return result;
            }

            // 첫 번째 행은 헤더
            List<Object> headers = values.get(0);
            List<String> headerStrings = headers.stream()
                    .map(h -> h != null ? h.toString() : "")
                    .collect(Collectors.toList());

            System.out.println("========================================");
            System.out.println("Google Sheets 매물 데이터 조회");
            System.out.println("시트: " + propertyWorksheetName);
            System.out.println("헤더: " + headerStrings);
            System.out.println("총 데이터 행 수: " + (values.size() - 1));

            // 데이터 행 처리 (헤더 제외)
            for (int i = 1; i < values.size(); i++) {
                List<Object> row = values.get(i);
                Map<String, Object> rowData = new LinkedHashMap<>();

                for (int j = 0; j < headerStrings.size(); j++) {
                    String header = headerStrings.get(j);
                    Object value = (j < row.size()) ? row.get(j) : "";

                    // 숫자 변환 시도 (정수/실수)
                    if (value != null && !value.toString().isEmpty()) {
                        String strValue = value.toString().trim();
                        try {
                            // 정수 시도
                            if (strValue.matches("-?\\d+")) {
                                rowData.put(header, Long.parseLong(strValue));
                            }
                            // 실수 시도
                            else if (strValue.matches("-?\\d+\\.\\d+")) {
                                rowData.put(header, Double.parseDouble(strValue));
                            }
                            else {
                                rowData.put(header, strValue);
                            }
                        } catch (NumberFormatException e) {
                            rowData.put(header, strValue);
                        }
                    } else {
                        rowData.put(header, "");
                    }
                }

                // 보증금이 공백인 매물은 제외
                Object deposit = rowData.get("보증금");
                if (deposit == null || deposit.toString().trim().isEmpty()) {
                    continue; // 보증금이 없으면 건너뛰기
                }

                result.add(rowData);
            }

            System.out.println("조회 완료: " + result.size() + "개 매물 (보증금 공백 제외)");
            System.out.println("========================================");

            return result;

        } catch (IOException e) {
            System.err.println("Google Sheets 데이터 읽기 실패: " + e.getMessage());
            e.printStackTrace();
            return result;
        }
    }

    /**
     * 연락처 워크시트에 데이터를 저장합니다.
     * - 주소 기준 중복 체크: 같은 주소면 기존 행에 개행으로 추가
     * - 연락처 중복 체크: 같은 주소 + 같은 연락처면 저장 안 함
     */
    @Override
    public boolean saveToContactSheet(Map<String, Object> data) {
        if (sheetsService == null) {
            return false;
        }

        try {
            // 1. 주소/연락처 유효성 검사
            String address = getStringValue(data, "address");
            String contact = getStringValue(data, "contact");

            if (address.isEmpty() || contact.isEmpty()) {
                return false; // 주소나 연락처 없으면 저장 안 함
            }

            // 2. 연락처 워크시트 전체 데이터 읽기
            String range = "'" + CONTACT_WORKSHEET_NAME + "'!A:G";
            ValueRange response;
            List<List<Object>> allValues;

            try {
                response = sheetsService.spreadsheets().values()
                        .get(spreadsheetId, range)
                        .execute();
                allValues = response.getValues();
            } catch (IOException e) {
                // 워크시트가 없는 경우 빈 리스트로 시작
                allValues = null;
            }

            // 3. 헤더 확인/생성
            if (allValues == null || allValues.isEmpty()) {
                // 헤더 행 추가
                ValueRange headerBody = new ValueRange()
                        .setValues(Collections.singletonList(new ArrayList<>(CONTACT_HEADERS)));
                sheetsService.spreadsheets().values()
                        .update(spreadsheetId, "'" + CONTACT_WORKSHEET_NAME + "'!A1:G1", headerBody)
                        .setValueInputOption("RAW")
                        .execute();
                allValues = new ArrayList<>();
                allValues.add(new ArrayList<>(CONTACT_HEADERS));
            }

            // 4. 주소로 기존 행 검색
            int foundRowIndex = -1;
            List<Object> existingRow = null;

            for (int i = 1; i < allValues.size(); i++) {
                List<Object> row = allValues.get(i);
                if (!row.isEmpty() && address.equals(row.get(0).toString())) {
                    // 연락처 중복 체크
                    String existingContacts = row.size() > 2 ? row.get(2).toString() : "";
                    if (Arrays.asList(existingContacts.split("\n")).contains(contact)) {
                        // 같은 연락처 이미 존재 → 건너뜀
                        return false;
                    }
                    foundRowIndex = i + 1; // 1-based row number (헤더가 1행)
                    existingRow = row;
                    break;
                }
            }

            // 5. 새 데이터 행 준비
            List<Object> newRowData = new ArrayList<>();
            for (String header : CONTACT_HEADERS) {
                String key = CONTACT_KEY_MAP.get(header);
                String value = key != null ? getStringValue(data, key) : "";
                newRowData.add(value);
            }

            // 6. 저장
            if (foundRowIndex > 0 && existingRow != null) {
                // 기존 행에 개행으로 추가 (주소 제외)
                List<Object> updatedRow = new ArrayList<>();
                for (int col = 0; col < CONTACT_HEADERS.size(); col++) {
                    String existingValue = col < existingRow.size() ? existingRow.get(col).toString() : "";
                    String newValue = col < newRowData.size() ? newRowData.get(col).toString() : "";

                    if (col == 0) {
                        // 주소는 그대로 유지
                        updatedRow.add(existingValue);
                    } else {
                        // 나머지는 개행으로 추가
                        if (!existingValue.isEmpty() && !newValue.isEmpty()) {
                            updatedRow.add(existingValue + "\n" + newValue);
                        } else if (!newValue.isEmpty()) {
                            updatedRow.add(newValue);
                        } else {
                            updatedRow.add(existingValue);
                        }
                    }
                }

                // 행 업데이트
                ValueRange updateBody = new ValueRange()
                        .setValues(Collections.singletonList(updatedRow));
                sheetsService.spreadsheets().values()
                        .update(spreadsheetId, "'" + CONTACT_WORKSHEET_NAME + "'!A" + foundRowIndex + ":G" + foundRowIndex, updateBody)
                        .setValueInputOption("USER_ENTERED")
                        .execute();
            } else {
                // 새 행 추가
                ValueRange appendBody = new ValueRange()
                        .setValues(Collections.singletonList(newRowData));
                sheetsService.spreadsheets().values()
                        .append(spreadsheetId, "'" + CONTACT_WORKSHEET_NAME + "'!A:G", appendBody)
                        .setValueInputOption("USER_ENTERED")
                        .execute();
            }

            return true;

        } catch (Exception e) {
            System.err.println("연락처 시트 저장 중 오류: " + e.getMessage());
            return false;
        }
    }

    /**
     * Map에서 문자열 값을 안전하게 추출
     */
    private String getStringValue(Map<String, Object> data, String key) {
        Object value = data.get(key);
        return value != null ? value.toString() : "";
    }

    /**
     * REALTYMATE 스프레드시트의 gongsil 워크시트에서 전체 데이터를 로드합니다.
     * Python의 load_gongsil_dict()와 동일한 로직.
     */
    @Override
    public Map<String, Map<String, String>> loadGongsilDict() {
        Map<String, Map<String, String>> result = new HashMap<>();

        if (sheetsService == null) {
            System.err.println("[gongsil] Google Sheets 연결 안됨 - dict 로드 건너뜀");
            return result;
        }

        try {
            String range = "'" + GONGSIL_WORKSHEET_NAME + "'!A:D";
            ValueRange response = sheetsService.spreadsheets().values()
                    .get(realtymateSpreadsheetId, range)
                    .execute();

            List<List<Object>> allValues = response.getValues();
            if (allValues == null || allValues.size() <= 1) {
                // 헤더만 있거나 비어있음
                return result;
            }

            // 헤더 제외하고 데이터 행 처리
            for (int i = 1; i < allValues.size(); i++) {
                List<Object> row = allValues.get(i);
                if (row.size() < 3) continue; // 등록번호, uid, name 최소 필요

                String registrationNo = row.get(0).toString().trim();
                String uid = row.get(1).toString().trim();
                String name = row.size() > 2 ? row.get(2).toString().trim() : "";
                String prtnUid = row.size() > 3 ? row.get(3).toString().trim() : "";

                if (registrationNo.isEmpty() || uid.isEmpty()) continue; // uid 없으면 건너뜀

                Map<String, String> info = new HashMap<>();
                info.put("uid", uid);
                info.put("name", name);
                if (!prtnUid.isEmpty()) {
                    info.put("prtn_uid", prtnUid);
                }
                result.put(registrationNo, info);
            }

            System.out.println("[gongsil] dict 로드 완료: " + result.size() + "개");
            return result;

        } catch (IOException e) {
            System.err.println("[gongsil] dict 로드 중 오류: " + e.getMessage());
            return result;
        }
    }

    /**
     * REALTYMATE 스프레드시트의 gongsil 워크시트에 공실클럽 uid 정보를 저장합니다.
     * - 등록번호가 이미 존재하면 name, prtn_uid만 업데이트 (uid 유지)
     * - 등록번호가 없으면 새 행 추가 [등록번호, '', name, prtn_uid]
     */
    @Override
    public boolean saveToGongsilSheet(String establishRegistrationNo, String name, String prtnUid) {
        if (sheetsService == null) {
            System.err.println("[gongsil] Google Sheets 연결 안됨 - 저장 건너뜀");
            return false;
        }
        if (establishRegistrationNo == null || establishRegistrationNo.isEmpty()) {
            return false;
        }

        try {
            String range = "'" + GONGSIL_WORKSHEET_NAME + "'!A:D";
            ValueRange response;
            List<List<Object>> allValues;

            try {
                response = sheetsService.spreadsheets().values()
                        .get(realtymateSpreadsheetId, range)
                        .execute();
                allValues = response.getValues();
            } catch (IOException e) {
                allValues = null;
            }

            // 헤더 확인/생성
            if (allValues == null || allValues.isEmpty()) {
                ValueRange headerBody = new ValueRange()
                        .setValues(Collections.singletonList(new ArrayList<>(GONGSIL_HEADERS)));
                sheetsService.spreadsheets().values()
                        .update(realtymateSpreadsheetId, "'" + GONGSIL_WORKSHEET_NAME + "'!A1:D1", headerBody)
                        .setValueInputOption("RAW")
                        .execute();
                allValues = new ArrayList<>();
                allValues.add(new ArrayList<>(GONGSIL_HEADERS));
            }

            // 등록번호로 기존 행 검색
            int foundRowIndex = -1;
            List<Object> existingRow = null;

            for (int i = 1; i < allValues.size(); i++) {
                List<Object> row = allValues.get(i);
                if (!row.isEmpty() && establishRegistrationNo.equals(row.get(0).toString())) {
                    foundRowIndex = i + 1; // 1-based
                    existingRow = row;
                    break;
                }
            }

            if (foundRowIndex > 0 && existingRow != null) {
                // 기존 행 → name(col 2), prtn_uid(col 3) 업데이트만 (uid 유지)
                List<Object> updatedRow = new ArrayList<>();
                updatedRow.add(establishRegistrationNo);                          // 등록번호
                updatedRow.add(existingRow.size() > 1 ? existingRow.get(1) : ""); // uid 유지
                updatedRow.add(name);                                             // name 업데이트
                updatedRow.add(prtnUid);                                          // prtn_uid 업데이트

                ValueRange updateBody = new ValueRange()
                        .setValues(Collections.singletonList(updatedRow));
                sheetsService.spreadsheets().values()
                        .update(realtymateSpreadsheetId,
                                "'" + GONGSIL_WORKSHEET_NAME + "'!A" + foundRowIndex + ":D" + foundRowIndex,
                                updateBody)
                        .setValueInputOption("USER_ENTERED")
                        .execute();
                System.out.println("[gongsil] 기존 행 업데이트: " + establishRegistrationNo);
            } else {
                // 새 행 추가
                List<Object> newRow = Arrays.asList(establishRegistrationNo, "", name, prtnUid);
                ValueRange appendBody = new ValueRange()
                        .setValues(Collections.singletonList(newRow));
                sheetsService.spreadsheets().values()
                        .append(realtymateSpreadsheetId,
                                "'" + GONGSIL_WORKSHEET_NAME + "'!A:D",
                                appendBody)
                        .setValueInputOption("USER_ENTERED")
                        .execute();
                System.out.println("[gongsil] 새 행 추가: " + establishRegistrationNo);
            }

            return true;

        } catch (Exception e) {
            System.err.println("[gongsil] 저장 중 오류: " + e.getMessage());
            return false;
        }
    }

    /**
     * 연락처 워크시트에서 주소로 연락처가 있는지 확인합니다.
     * @param address 확인할 주소
     * @return true: 주소가 있고 연락처도 있음, false: 주소가 없거나 연락처가 없음
     */
    @Override
    public boolean hasContactByAddress(String address) {
        if (sheetsService == null || address == null || address.isEmpty()) {
            return false;
        }

        try {
            // 연락처 워크시트 전체 데이터 읽기
            String range = "'" + CONTACT_WORKSHEET_NAME + "'!A:C"; // 주소(A), 소유자(B), 연락처(C)
            ValueRange response = sheetsService.spreadsheets().values()
                    .get(spreadsheetId, range)
                    .execute();

            List<List<Object>> allValues = response.getValues();

            if (allValues == null || allValues.size() <= 1) {
                // 데이터 없음 (헤더만 있거나 빈 시트)
                return false;
            }

            // 주소로 검색 (1행은 헤더이므로 2행부터)
            for (int i = 1; i < allValues.size(); i++) {
                List<Object> row = allValues.get(i);
                if (!row.isEmpty()) {
                    String rowAddress = row.get(0).toString();
                    if (address.equals(rowAddress)) {
                        // 주소 발견 - 연락처(C열, 인덱스 2) 확인
                        if (row.size() > 2) {
                            String contact = row.get(2).toString();
                            return contact != null && !contact.trim().isEmpty();
                        }
                        return false; // 주소는 있지만 연락처 없음
                    }
                }
            }

            return false; // 주소 없음

        } catch (Exception e) {
            System.err.println("연락처 시트 조회 중 오류: " + e.getMessage());
            return false;
        }
    }
}