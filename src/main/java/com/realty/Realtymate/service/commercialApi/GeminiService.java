package com.realty.Realtymate.service.commercialApi;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.genai.Client;
import com.google.genai.types.GenerateContentResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Google Gemini API 서비스 (공식 SDK 사용)
 * - Google Gen AI SDK for Java
 * - Python SDK와 동일한 방식으로 작동
 */
@Slf4j
@Service
public class GeminiService {

    @Value("${gemini.api.key:}")
    private String geminiApiKey;

    @Value("${gemini.model:gemini-2.0-flash-exp}")
    private String modelName;

    private Client client;

    /**
     * Gemini 클라이언트 초기화
     */
    private void initializeClient() {
        if (client == null && geminiApiKey != null && !geminiApiKey.isEmpty()) {
            try {
                // Client 생성 시 API 키 직접 전달
                client = Client.builder()
                    .apiKey(geminiApiKey)
                    .build();
            } catch (Exception e) {
                log.error("Gemini 클라이언트 초기화 실패", e);
            }
        }
    }

    /**
     * 골목상권 데이터를 기반으로 AI 분석 생성 (Gemini)
     * @param golmokData 골목상권 통합 데이터
     * @return AI 분석 요약
     */
    public String generateAISummary(Map<String, Object> golmokData) {
        try {
            initializeClient();

            if (client == null) {
                log.warn("Gemini API 키가 설정되지 않음");
                return "Gemini API 키가 설정되지 않았습니다.";
            }

            // OpenAI와 동일한 분석 쿼리 생성
            String query = generateAnalysisQuery(golmokData);

            // Gemini API 호출 (Python과 동일한 방식)
            GenerateContentResponse response = client.models.generateContent(
                modelName,
                query,
                null  // config는 나중에 추가 가능
            );

            // 응답에서 텍스트 추출
            String summary = response.text();


            return summary != null && !summary.isEmpty() ? summary : "Gemini AI 분석 실패";

        } catch (Exception e) {
            log.error("Gemini AI 요약 생성 중 오류", e);
            return "Gemini AI 요약 생성 실패: " + e.getMessage();
        }
    }

    /**
     * 골목상권 데이터를 기반으로 AI 분석 쿼리 생성
     * (OpenAI와 동일한 구조)
     */
    private String generateAnalysisQuery(Map<String, Object> golmokData) {
        StringBuilder queryBuilder = new StringBuilder();
        queryBuilder.append("다음은 특정 상권에 대한 골목상권 분석 데이터입니다.\n\n");

        // 1. 블록 영역 정보
        appendBlockAreaInfo(queryBuilder, golmokData);

        // 2. 성별/연령별 직장인구
        appendDataSection(queryBuilder, golmokData, "wrc_poplt_sex_age",
            "직장인구 성별/연령별 분포",
            "해당 지역 내 근무하는 직장인구를 성별과 연령대로 구분한 데이터입니다. 평일 낮 시간대의 주요 소비층을 파악하는 데 활용됩니다.");

        // 3. 직장인구 수
        appendDataSection(queryBuilder, golmokData, "wrc_poplt_ha",
            "직장인구 총계",
            "해당 지역의 총 직장인구 수입니다. 상권 활성도 및 평일 낮 시간대 잠재 고객 규모를 파악하는 지표입니다.");

        // 4. 성별/연령별 거주인구
        appendDataSection(queryBuilder, golmokData, "repop_sex_age",
            "거주인구 성별/연령별 분포",
            "해당 지역에 실제 거주하는 주민들의 성별/연령별 분포입니다. 지역 기반 고객층과 야간/주말 소비층을 파악하는 데 사용됩니다.");

        // 5. 시간대별 매출
        appendDataSection(queryBuilder, golmokData, "selng_hour",
            "시간대별 매출 데이터",
            "하루 중 시간대별 매출 패턴입니다. 주요 영업시간과 피크 시간대를 파악하여 적정 운영 시간을 결정할 수 있습니다.");

        // 6. 요일별 매출
        appendDataSection(queryBuilder, golmokData, "selng_week",
            "요일별 매출 데이터",
            "요일별 매출 패턴입니다. 주중/주말 차이 및 특정 요일의 매출 특성을 분석하여 요일별 마케팅 전략을 수립할 수 있습니다.");

        // 7. 연령대별 매출
        appendDataSection(queryBuilder, golmokData, "selng_age",
            "연령대별 매출 데이터",
            "연령대별 매출 비중입니다. 주요 소비 연령층을 파악하여 타겟 고객층을 설정하고 상품/서비스 구성에 활용됩니다.");

        // 8. 요일별 유동인구
        appendDataSection(queryBuilder, golmokData, "flpop_week_co",
            "요일별 유동인구",
            "요일별 유동인구 수입니다. 상권의 활성화 요일과 패턴을 파악하여 마케팅 및 프로모션 계획을 수립할 수 있습니다.");

        // 9. 시간대별 유동인구
        appendDataSection(queryBuilder, golmokData, "flpop_hour_co",
            "시간대별 유동인구",
            "시간대별 유동인구 수입니다. 상권의 주요 활성화 시간대를 파악하여 영업 시간과 인력 배치를 최적화할 수 있습니다.");

        // 10. 아파트 가구수
        appendDataSection(queryBuilder, golmokData, "apt_hshld_co",
            "아파트 가구세대 수",
            "해당 지역 내 아파트 및 총 가구 수입니다. 주거 밀집도와 잠재 고객 규모를 파악하는 데 사용됩니다.");

        // 11. 거주인구 밀도
        appendDataSection(queryBuilder, golmokData, "repop_dnst_co",
            "주거인구 총계",
            "해당 지역의 총 거주인구 수와 밀도입니다. 지역 기반 고객 규모를 파악하는 기본 지표입니다.");

        // 12. 총 유동인구
        appendDataSection(queryBuilder, golmokData, "flpop_co",
            "유동인구 총계",
            "해당 지역의 총 유동인구 수입니다. 상권 활성도를 나타내는 핵심 지표로, 높을수록 유동 고객이 많음을 의미합니다.");

        // 13. 성별/연령별 유동인구
        appendDataSection(queryBuilder, golmokData, "flpop_sex_age",
            "유동인구 성별/연령별 분포",
            "해당 지역을 지나다니는 유동인구를 성별/연령별로 구분한 데이터입니다. 상권의 주요 방문객 특성과 타겟층을 파악할 수 있습니다.");

        // 분석 요청
        queryBuilder.append("\n위 데이터를 바탕으로 다음 항목을 포함하여 500자 내외로 요약해주세요:\n");
        queryBuilder.append("1. 상권 특성 요약 (인구 구성, 유동인구, 매출 패턴 등)\n");
        queryBuilder.append("2. 주요 강점\n");
        queryBuilder.append("3. 주요 약점 또는 위험 요소\n");
        queryBuilder.append("4. 추천 업종 및 타겟 고객층\n\n");

        queryBuilder.append("참고사항\n");
        queryBuilder.append("1. 유동인구, 직장인구, 주거인구의 이름을 정확히 밝혀주세요. 예를들어 단순히 어느 연령대 비율이 낮다 이것 보다는 어떤 인구의 비율이 낮다 이런 식으로 자세히 써주세요.\n");
        queryBuilder.append("2. 약점 보다는 강점 위주로! 예를들면, 특정 연령대 유동인구가 많다. 특정 연령대 매출이 두드러진다 등.\n");
        queryBuilder.append("3. 데이터 분석 결과의 순위 등에 실수가 있으면 안되므로, 순위별로 먼저 너가 정리를 하고 그 다음에 분석을 해줘야돼.\n");
        queryBuilder.append("4. 분석 자체를 유동인구 하나만, 매출만, 직장인인구만 이렇게 하나씩 단편적으로 보기보다는, 복합적으로 고려해서 해줘.\n");

        return queryBuilder.toString();
    }

    /**
     * 블록 영역 정보 추가
     */
    private void appendBlockAreaInfo(StringBuilder builder, Map<String, Object> golmokData) {
        if (golmokData.containsKey("block_area")) {
            Object blockAreaObj = golmokData.get("block_area");

            // JsonNode인 경우 처리
            if (blockAreaObj instanceof JsonNode) {
                JsonNode blockAreaResult = (JsonNode) blockAreaObj;
                builder.append("# 분석 영역 정보\n");
                builder.append("데이터: ").append(blockAreaResult.toString()).append("\n\n");
            }
            // Map인 경우 처리
            else if (blockAreaObj instanceof Map) {
                Map<String, Object> blockAreaResult = (Map<String, Object>) blockAreaObj;
                if ("success".equals(blockAreaResult.get("status"))) {
                    Object data = blockAreaResult.get("data");
                    builder.append("# 분석 영역 정보\n");
                    builder.append("데이터: ").append(data).append("\n\n");
                }
            }
        }
    }

    /**
     * 데이터 섹션 추가
     */
    private void appendDataSection(StringBuilder builder, Map<String, Object> golmokData,
                                    String key, String title, String meaning) {
        if (golmokData.containsKey(key)) {
            Object resultObj = golmokData.get(key);

            // JsonNode인 경우 처리
            if (resultObj instanceof JsonNode) {
                JsonNode result = (JsonNode) resultObj;
                if (result.has("status") && "success".equals(result.get("status").asText())) {
                    JsonNode data = result.get("data");
                    builder.append("# ").append(title).append("\n");
                    builder.append("데이터: ").append(data != null ? data.toString() : "없음").append("\n\n");
                    builder.append("의미: ").append(meaning).append("\n\n");
                }
            }
            // Map인 경우 처리
            else if (resultObj instanceof Map) {
                Map<String, Object> result = (Map<String, Object>) resultObj;
                if ("success".equals(result.get("status"))) {
                    Object data = result.get("data");
                    builder.append("# ").append(title).append("\n");
                    builder.append("데이터: ").append(data).append("\n\n");
                    builder.append("의미: ").append(meaning).append("\n\n");
                }
            }
        }
    }
}
