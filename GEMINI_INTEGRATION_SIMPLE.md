# Gemini API 통합 (간단 버전)

## ✅ 완료된 작업

Python에서 사용하던 **Gemini REST API 방식**으로 통합 완료!

---

## 📋 주요 변경사항

### 1. Vertex AI → REST API 변경
- ❌ **이전**: Google Cloud 프로젝트 ID, 서비스 계정 등 복잡한 설정 필요
- ✅ **현재**: API 키만으로 간단하게 사용

### 2. 설정 파일 (application.properties)

```properties
# Google Gemini API (REST API - 간단한 방식)
gemini.api.key=AIzaSyDMQOc8wR8K9-cX6z1kUZAdFahpT-JLDEo
gemini.model=gemini-3-pro-preview
```

**설정 끝!** 프로젝트 ID, 서비스 계정 필요 없음.

---

## 🎯 API 엔드포인트

### 1. OpenAI만 사용
```
POST /api/commercial/ai-summary
```

**Response**:
```json
{
  "status": "success",
  "summary": "OpenAI 분석 결과...",
  "provider": "openai"
}
```

---

### 2. Gemini만 사용
```
POST /api/commercial/gemini-summary
```

**Response**:
```json
{
  "status": "success",
  "summary": "Gemini 분석 결과...",
  "provider": "gemini"
}
```

---

### 3. **둘 다 비교** ⭐ (NEW!)
```
POST /api/commercial/both-ai-summary
```

**Response**:
```json
{
  "status": "success",
  "openai_summary": "OpenAI 분석 결과...",
  "gemini_summary": "Gemini 분석 결과..."
}
```

**로그 출력**:
```
=== OpenAI 분석 결과 ===
[OpenAI 요약 내용]

=== Gemini 분석 결과 ===
[Gemini 요약 내용]
```

---

## 📝 Request Body (공통)

```json
{
  "golmok_analysis": {
    "block_area": {...},
    "wrc_poplt_sex_age": {...},
    "repop_sex_age": {...},
    "selng_hour": {...},
    "selng_week": {...},
    "selng_age": {...},
    "flpop_week_co": {...},
    "flpop_hour_co": {...},
    "apt_hshld_co": {...},
    "repop_dnst_co": {...},
    "flpop_co": {...},
    "flpop_sex_age": {...}
  }
}
```

---

## 🚀 사용 예시

### cURL로 테스트

```bash
# 둘 다 비교
curl -X POST http://localhost:8081/api/commercial/both-ai-summary \
  -H "Content-Type: application/json" \
  -H "X-Employee-Id: test-user" \
  -d @golmok_data.json
```

### JavaScript (Axios)

```javascript
const response = await axios.post('/api/commercial/both-ai-summary', {
  golmok_analysis: golmokData
});

console.log('OpenAI:', response.data.openai_summary);
console.log('Gemini:', response.data.gemini_summary);
```

---

## 🔍 로그 확인 방법

서버 실행 후 `/api/commercial/both-ai-summary` 호출 시 콘솔에 출력:

```
[INFO] [test-user] AI요약생성(OpenAI+Gemini)
[INFO] === OpenAI 분석 결과 ===
이 상권은 20-30대 직장인구가 밀집된 지역으로...

[INFO] === Gemini 분석 결과 ===
주요 강점은 높은 유동인구와 30대 매출 비중...
```

---

## ⚙️ 구현 상세

### 병렬 호출 (성능 최적화)

OpenAI와 Gemini를 **동시에** 호출하여 응답 시간 단축:

```java
Mono<String> openaiMono = Mono.fromCallable(() -> openAIService.generateAISummary(golmokData));
Mono<String> geminiMono = Mono.fromCallable(() -> geminiService.generateAISummary(golmokData));

return Mono.zip(openaiMono, geminiMono)
    .map(tuple -> {
        String openaiSummary = tuple.getT1();
        String geminiSummary = tuple.getT2();

        log.info("=== OpenAI 분석 결과 ===\n{}", openaiSummary);
        log.info("=== Gemini 분석 결과 ===\n{}", geminiSummary);

        return Map.of(
            "status", "success",
            "openai_summary", openaiSummary,
            "gemini_summary", geminiSummary
        );
    });
```

---

## 📊 모델 정보

### OpenAI
- **모델**: `gpt-4o-mini`
- **설정**: temperature=0.7, max_tokens=1000

### Gemini
- **모델**: `gemini-3-pro-preview` (설정 파일에서 변경 가능)
- **설정**: temperature=0.7, maxOutputTokens=1000

**모델 변경 방법**:
```properties
# application.properties
gemini.model=gemini-2.0-flash-exp  # 또는 다른 모델
```

사용 가능한 모델:
- `gemini-2.0-flash-exp` (가장 빠름, 실험적)
- `gemini-3-pro-preview` (균형)
- `gemini-2.5-flash-lite-preview-06-17` (경량)

---

## 🔐 보안

API 키가 코드에 하드코딩되어 있으므로, **프로덕션 환경**에서는:

1. **환경변수 사용**:
```properties
gemini.api.key=${GEMINI_API_KEY}
```

2. **환경변수 설정** (Windows):
```cmd
setx GEMINI_API_KEY "AIzaSy..."
```

3. **환경변수 설정** (Linux/Mac):
```bash
export GEMINI_API_KEY="AIzaSy..."
```

---

## ✅ 체크리스트

- [x] GeminiService REST API 방식으로 구현
- [x] Vertex AI 의존성 제거
- [x] application.properties 간단 설정
- [x] OpenAI 개별 엔드포인트
- [x] Gemini 개별 엔드포인트
- [x] **두 AI 동시 비교 엔드포인트** (`/both-ai-summary`)
- [x] 병렬 호출로 성능 최적화
- [x] 로그 출력으로 콘솔에서 비교 가능

---

## 🎉 완료!

이제 다음 명령으로 **OpenAI와 Gemini의 분석 결과를 동시에 비교**할 수 있습니다:

```bash
POST /api/commercial/both-ai-summary
```

서버 콘솔에서 두 AI의 분석 결과를 바로 확인하세요!

---

**작성일**: 2025-11-26
**버전**: 2.0 (REST API 간단 버전)
