# Gemini AI 통합 가이드

## 개요

OpenAI와 동일한 쿼리 구조를 사용하여 Google Gemini (Vertex AI)로 상권 분석 AI 요약을 생성하는 기능을 추가했습니다.

---

## 구현된 파일

### 1. GeminiService.java
**경로**: `src/main/java/com/realty/Realtymate/service/commercialApi/GeminiService.java`

**주요 기능**:
- OpenAIService와 동일한 쿼리 구조 사용
- Vertex AI를 통한 Gemini API 호출
- 골목상권 데이터 13개 섹션 구조화
- 500자 내외 분석 요약 생성

**주요 메서드**:
```java
public String generateAISummary(Map<String, Object> golmokData)
```

**사용 모델**: `gemini-1.5-flash` (기본값, 설정 파일에서 변경 가능)

---

### 2. CommercialAnalysisService.java 업데이트

**추가된 메서드**:
```java
public Mono<Map<String, Object>> getGeminiSummaryOnly(Map<String, Object> golmokData)
```

**의존성 주입**:
```java
private final GeminiService geminiService;
```

---

### 3. CommercialAnalysisController.java 업데이트

**새로운 엔드포인트**:
```
POST /api/commercial/gemini-summary
```

**Request Body**:
```json
{
  "golmok_analysis": {
    "block_area": {...},
    "wrc_poplt_sex_age": {...},
    "flpop_sex_age": {...},
    ...
  }
}
```

**Response**:
```json
{
  "status": "success",
  "summary": "AI 생성 요약 텍스트",
  "provider": "gemini"
}
```

---

### 4. build.gradle.kts 업데이트

**추가된 의존성**:
```kotlin
// 상권 분석 - Google Gemini (Vertex AI)
implementation(platform("com.google.cloud:libraries-bom:26.32.0"))
implementation("com.google.cloud:google-cloud-vertexai")
```

---

### 5. application.properties 업데이트

**추가된 설정**:
```properties
# Google Gemini API (Vertex AI)
gemini.api.key=${GEMINI_API_KEY:}
gemini.project.id=${GEMINI_PROJECT_ID:}
gemini.location=us-central1
gemini.model=gemini-1.5-flash
```

---

## 환경 설정 방법

### 1. Google Cloud 프로젝트 설정

1. **Google Cloud Console** 접속: https://console.cloud.google.com/
2. **프로젝트 생성** 또는 기존 프로젝트 선택
3. **Vertex AI API 활성화**:
   - API 및 서비스 > 라이브러리
   - "Vertex AI API" 검색 후 활성화

### 2. 서비스 계정 생성 (선택사항)

Vertex AI를 사용하려면 인증이 필요합니다.

**방법 1: Application Default Credentials (로컬 개발)**
```bash
gcloud auth application-default login
```

**방법 2: 서비스 계정 키 파일**
1. IAM 및 관리 > 서비스 계정
2. 서비스 계정 생성
3. 역할: "Vertex AI User" 추가
4. 키 생성 (JSON) → 다운로드
5. 환경변수 설정:
   ```bash
   export GOOGLE_APPLICATION_CREDENTIALS="/path/to/service-account-key.json"
   ```

### 3. 환경변수 설정

**Windows (환경변수)**:
```cmd
setx GEMINI_PROJECT_ID "your-project-id"
setx GOOGLE_APPLICATION_CREDENTIALS "C:\path\to\service-account-key.json"
```

**Linux/Mac (.bashrc 또는 .zshrc)**:
```bash
export GEMINI_PROJECT_ID="your-project-id"
export GOOGLE_APPLICATION_CREDENTIALS="/path/to/service-account-key.json"
```

**application.properties (직접 입력 - 비추천)**:
```properties
gemini.project.id=your-project-id
```

---

## 사용 방법

### OpenAI vs Gemini 비교

| 항목 | OpenAI | Gemini |
|------|--------|--------|
| **엔드포인트** | `/api/commercial/ai-summary` | `/api/commercial/gemini-summary` |
| **모델** | gpt-4o-mini | gemini-1.5-flash |
| **쿼리 구조** | 동일 (13개 섹션) | 동일 (13개 섹션) |
| **응답 길이** | 500자 내외 | 500자 내외 |
| **provider** | "openai" | "gemini" |

### API 호출 예시

**OpenAI 요약**:
```bash
curl -X POST http://localhost:8081/api/commercial/ai-summary \
  -H "Content-Type: application/json" \
  -H "X-Employee-Id: test-user" \
  -d '{
    "golmok_analysis": {
      "block_area": {...},
      "wrc_poplt_sex_age": {...},
      ...
    }
  }'
```

**Gemini 요약**:
```bash
curl -X POST http://localhost:8081/api/commercial/gemini-summary \
  -H "Content-Type: application/json" \
  -H "X-Employee-Id: test-user" \
  -d '{
    "golmok_analysis": {
      "block_area": {...},
      "wrc_poplt_sex_age": {...},
      ...
    }
  }'
```

---

## 쿼리 구조 (OpenAI와 동일)

### 분석 쿼리에 포함되는 13개 섹션

1. **분석 영역 정보** (`block_area`)
2. **직장인구 성별/연령별 분포** (`wrc_poplt_sex_age`)
3. **직장인구 총계** (`wrc_poplt_ha`)
4. **거주인구 성별/연령별 분포** (`repop_sex_age`)
5. **시간대별 매출 데이터** (`selng_hour`)
6. **요일별 매출 데이터** (`selng_week`)
7. **연령대별 매출 데이터** (`selng_age`)
8. **요일별 유동인구** (`flpop_week_co`)
9. **시간대별 유동인구** (`flpop_hour_co`)
10. **아파트 가구세대 수** (`apt_hshld_co`)
11. **주거인구 총계** (`repop_dnst_co`)
12. **유동인구 총계** (`flpop_co`)
13. **유동인구 성별/연령별 분포** (`flpop_sex_age`)

### 분석 요청 내용

```
위 데이터를 바탕으로 다음 항목을 포함하여 500자 내외로 요약해주세요:
1. 상권 특성 요약 (인구 구성, 유동인구, 매출 패턴 등)
2. 주요 강점
3. 주요 약점 또는 위험 요소
4. 추천 업종 및 타겟 고객층

참고사항:
1. 유동인구, 직장인구, 주거인구의 이름을 정확히 밝혀주세요.
2. 약점 보다는 강점 위주로! 예를들면, 특정 연령대 유동인구가 많다. 특정 연령대 매출이 두드러진다 등.
3. 데이터 분석 결과의 순위 등에 실수가 있으면 안되므로, 순위별로 먼저 정리하고 그 다음에 분석해주세요.
4. 분석 자체를 유동인구만, 매출만, 직장인구만 이렇게 단편적으로 보기보다는, 복합적으로 고려해서 해주세요.
```

---

## 빌드 및 실행

### 1. 의존성 설치
```bash
./gradlew build
```

### 2. 애플리케이션 실행
```bash
./gradlew bootRun
```

또는 IDE에서 `RealtymateApplication.java` 실행

### 3. 테스트

**헬스 체크**:
```bash
curl http://localhost:8081/api/commercial/health
```

**Gemini 요약 테스트** (골목상권 데이터 필요):
```bash
curl -X POST http://localhost:8081/api/commercial/gemini-summary \
  -H "Content-Type: application/json" \
  -d @test_golmok_data.json
```

---

## 비용 및 할당량

### OpenAI (GPT-4o-mini)
- **가격**: ~$0.15 / 1M input tokens, ~$0.60 / 1M output tokens
- **할당량**: API 키 기반

### Google Gemini (gemini-1.5-flash)
- **가격**:
  - 입력: $0.00001875 / 1K characters (~$0.075 / 1M tokens)
  - 출력: $0.000075 / 1K characters (~$0.30 / 1M tokens)
- **무료 할당량** (Gemini API 기준):
  - 1,500 요청/일
  - 100만 토큰/일
- **참고**: Vertex AI는 별도 요금제

---

## 트러블슈팅

### 1. "Gemini API 설정이 올바르지 않습니다"
- `GEMINI_PROJECT_ID` 환경변수 확인
- `GOOGLE_APPLICATION_CREDENTIALS` 경로 확인
- Vertex AI API 활성화 여부 확인

### 2. "Permission denied" 오류
- 서비스 계정에 "Vertex AI User" 역할 추가
- `gcloud auth application-default login` 재실행

### 3. 컴파일 오류
```bash
./gradlew clean build --refresh-dependencies
```

### 4. 모델 변경하고 싶을 때
`application.properties`:
```properties
gemini.model=gemini-1.5-pro  # 더 강력한 모델
```

사용 가능한 모델:
- `gemini-1.5-flash` (빠르고 저렴)
- `gemini-1.5-pro` (더 정확하지만 비쌈)
- `gemini-2.0-flash-exp` (실험적)

---

## 코드 예시

### Java 서비스 호출
```java
@Autowired
private CommercialAnalysisService commercialAnalysisService;

public void testGeminiSummary() {
    Map<String, Object> golmokData = new HashMap<>();
    // ... 골목상권 데이터 구성

    Mono<Map<String, Object>> result =
        commercialAnalysisService.getGeminiSummaryOnly(golmokData);

    result.subscribe(response -> {
        String summary = (String) response.get("summary");
        System.out.println("Gemini 요약: " + summary);
    });
}
```

---

## 참고 자료

- **Google Vertex AI 문서**: https://cloud.google.com/vertex-ai/docs
- **Gemini API 가이드**: https://ai.google.dev/docs
- **Java Client Library**: https://cloud.google.com/java/docs/reference/google-cloud-vertexai/latest/overview
- **가격 정보**: https://cloud.google.com/vertex-ai/pricing

---

## 다음 단계

1. ✅ GeminiService 구현 완료
2. ✅ Controller 엔드포인트 추가 완료
3. ✅ 의존성 설정 완료
4. ⬜ 환경변수 설정 (사용자 작업 필요)
5. ⬜ 빌드 및 테스트
6. ⬜ 프론트엔드 연동 (선택사항)

---

**작성일**: 2025-11-26
**버전**: 1.0.0
