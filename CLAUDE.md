# 상권 분석 기능 통합 마이그레이션 계획

## 📋 프로젝트 개요

**Source**: Python Flask + React (commercial_analysis_qgis)
**Target**: Java Spring Boot + React (Realtymate)
**목표**: 상권 분석 기능을 청운부동산의 새 메뉴로 추가

---

## 🎯 핵심 결정사항

- ✅ **DB 사용 안 함** - 실시간 API 호출만 (저장 기능 없음)
- ✅ **별도 탭/메뉴** - React Router로 완전 분리된 화면
- ✅ **로그인 필요** - 기존 토큰 인증 활용

---

## 📅 Phase 1: 프로젝트 준비 (1일)

### 1.1 Backend 의존성 추가 (`build.gradle.kts`)

```kotlin
// 공간 데이터 처리
implementation("org.locationtech.jts:jts-core:1.19.0")
implementation("org.geotools:gt-main:29.2")
implementation("org.geotools:gt-referencing:29.2")
implementation("org.geotools:gt-epsg-hsql:29.2")

// OpenAI
implementation("com.theokanning.openai-gpt3-java:service:0.18.2")

// HTTP Client (이미 있음)
// implementation("org.apache.httpcomponents.client5:httpclient5:5.2.1")
```

**GeoTools Repository 추가 필요**:
```kotlin
repositories {
    mavenCentral()
    maven { url = uri("https://repo.osgeo.org/repository/release/") }
}
```

### 1.2 Frontend 의존성 추가 (`realty-web/package.json`)

```json
{
  "dependencies": {
    "react-router-dom": "^6.20.0",
    "leaflet": "^1.9.4",
    "react-leaflet": "^4.2.1",
    "leaflet.heat": "^0.2.0",
    "recharts": "^2.10.3",
    "@types/leaflet": "^1.9.8",
    "@types/leaflet.heat": "^0.2.5"
  }
}
```

**설치 명령어**:
```bash
cd realty-web
npm install react-router-dom leaflet react-leaflet leaflet.heat recharts @types/leaflet @types/leaflet.heat
```

---

## 📅 Phase 2: 백엔드 구현 (3-4일)

### 2.1 패키지 구조 (신규 생성)

```
src/main/java/com/realty/Realtymate/
├── controller/
│   └── CommercialAnalysisController.java          ⬅️ NEW
├── service/
│   └── commercialApi/                             ⬅️ NEW
│       ├── CommercialAnalysisService.java
│       ├── GolmokApiService.java
│       ├── NiceBizmapApiService.java
│       └── OpenAIService.java
├── model/
│   └── commercial/                                ⬅️ NEW
│       └── dto/                                   (Entity 없음, DTO만)
│           ├── AnalysisRequestDto.java
│           ├── AnalysisResponseDto.java
│           ├── GolmokDataDto.java
│           ├── NiceBizmapDataDto.java
│           └── ... (기타 API 응답 DTO)
└── utils/
    └── GeoUtils.java                              ⬅️ NEW
```

### 2.2 구현 순서

#### Step 1: GeoUtils.java
- **기능**:
  - WGS84 (EPSG:4326) ↔ EPSG:5181 좌표 변환
  - WKT 폴리곤 생성 (반경/폴리곤)
  - 면적 계산
- **참고**: Python `app.py` lines 480-529

#### Step 2: NiceBizmapApiService.java
- **기능**: 나이스비즈맵 유동인구 API 호출
- **메서드**: `getFloatingPopulationByRadius(lat, lng, radius)`
- **참고**: Python `app.py` lines 531-584

#### Step 3: GolmokApiService.java
- **기능**: 서울골목상권 API 12개 엔드포인트 호출
- **메서드**:
  1. `getBlockArea()` - 상권 블록 정보 (필수 선행)
  2. `getWrcPopltSexAge()` - 직장인구 성별/연령
  3. `getWrcPopltHa()` - 직장인구 수/밀도
  4. `getRepopSexAge()` - 거주인구 성별/연령
  5. `getSelngHour()` - 시간대별 매출
  6. `getSelngWeek()` - 요일별 매출
  7. `getSelngAge()` - 연령별 매출
  8. `getFlpopWeekCo()` - 요일별 유동인구
  9. `getFlpopHourCo()` - 시간대별 유동인구
  10. `getAptHshldCo()` - 아파트 가구수
  11. `getRepopDnstCo()` - 거주인구 밀도
  12. `getFlpopCo()` - 총 유동인구
  13. `getFlpopSexAge()` - 유동인구 성별/연령
- **참고**: Python `app.py` lines 83-417

#### Step 4: OpenAIService.java
- **기능**: GPT-4 기반 상권 분석 인사이트 생성
- **메서드**: `generateAISummary(golmokData)`
- **참고**: Python `app.py` lines 2283-2529

#### Step 5: CommercialAnalysisService.java
- **기능**: 여러 API 결과 통합 및 비즈니스 로직
- **메서드**:
  - `analyzeBasic(lat, lng, radius)` - 나이스 + 골목 + AI
  - `analyzePolygon(polygonCoords)` - 폴리곤 분석

#### Step 6: CommercialAnalysisController.java
- **REST 엔드포인트**:
  ```java
  @RestController
  @RequestMapping("/api/commercial")
  @CrossOrigin(origins = "*")
  public class CommercialAnalysisController {

      // 기본 분석 (반경)
      POST /api/commercial/analyze

      // 골목상권 데이터만
      POST /api/commercial/golmok

      // 나이스 데이터만
      POST /api/commercial/nice

      // 통합 분석
      POST /api/commercial/integrated

      // AI 요약
      POST /api/commercial/ai-summary

      // 헬스 체크
      GET /api/commercial/health
  }
  ```

### 2.3 환경변수 설정 (`application.properties`)

```properties
# OpenAI API
openai.api.key=${OPENAI_API_KEY:your-api-key-here}

# Seoul Golmok API
golmok.api.base-url=https://golmok.seoul.go.kr/region
golmok.api.cookie=${GOLMOK_COOKIE:your-cookie-here}

# Nice Bizmap API
nice.api.base-url=https://apis.nicebizmap.co.kr
```

---

## 📅 Phase 3: 프론트엔드 구현 (4-5일)

### 3.1 라우팅 설정

#### Step 1: React Router 설치
```bash
cd realty-web
npm install react-router-dom
```

#### Step 2: `App.tsx` 수정
```tsx
import { BrowserRouter, Routes, Route, Link } from 'react-router-dom';
import Login from './components/Login';
import SearchPage from './components/SearchPage';  // 기존 화면
import CommercialAnalysisPage from './components/commercial/CommercialAnalysisPage';

function App() {
  return (
    <BrowserRouter>
      {/* 상단 네비게이션 */}
      <nav className="main-nav">
        <Link to="/">부동산 검색</Link>
        <Link to="/commercial">상권 분석</Link>
      </nav>

      <Routes>
        <Route path="/" element={<SearchPage />} />
        <Route path="/commercial" element={<CommercialAnalysisPage />} />
      </Routes>
    </BrowserRouter>
  );
}
```

### 3.2 컴포넌트 구조

Python 프로젝트에서 이식할 컴포넌트:

```
realty-web/src/components/commercial/
├── CommercialAnalysisPage.tsx           ⬅️ 메인 페이지 (NEW)
├── CommercialMap.tsx                    ⬅️ Leaflet 지도 + 그리기 도구
├── ResultModal.tsx                      ⬅️ 분석 결과 모달
└── charts/                              ⬅️ Recharts 차트 컴포넌트들
    ├── GolmokAnalysisResult.tsx
    ├── WorkPopulationCard.tsx
    ├── WorkPopulationByAge.tsx
    ├── ResidentPopulationByAge.tsx
    ├── FloatingPopulationByHour.tsx
    ├── FloatingPopulationByWeek.tsx
    ├── FloatingPopulationCountCard.tsx
    ├── SalesByAge.tsx
    ├── SalesByHour.tsx
    └── SalesByWeek.tsx
```

**복사할 파일 목록**:
```
Source: C:\Users\SEO\Desktop\commercial_analysis_qgis\frontend\src\components\
Target: C:\Users\SEO\IdeaProjects\Realtymate\realty-web\src\components\commercial\

1. AnalysisMapAdvanced.tsx → CommercialMap.tsx
2. AnalysisResultModal.tsx → ResultModal.tsx
3. golmok/*.tsx → charts/*.tsx (전체 복사)
```

### 3.3 API 클라이언트 (`src/api/commercialApi.ts`)

```typescript
import axios from 'axios';
import { getApiUrl } from '../config';

const API_BASE = `${getApiUrl()}/api/commercial`;

export const commercialApi = {
  // 기본 분석
  analyzeBasic: (data: AnalysisRequest) =>
    axios.post(`${API_BASE}/analyze`, data),

  // 골목 데이터
  getGolmokData: (data: any) =>
    axios.post(`${API_BASE}/golmok`, data),

  // 나이스 데이터
  getNiceData: (data: any) =>
    axios.post(`${API_BASE}/nice`, data),

  // AI 요약
  getAISummary: (data: any) =>
    axios.post(`${API_BASE}/ai-summary`, data),
};
```

### 3.4 타입 정의 (`src/types/commercial.ts`)

```typescript
export interface AnalysisRequest {
  latitude: number;
  longitude: number;
  radius?: number;
  polygonCoords?: [number, number][];
  analysisType: 'radius' | 'polygon';
}

export interface AnalysisResponse {
  status: string;
  data: {
    floating_population?: any;
    golmok_analysis?: GolmokData;
    ai_summary?: string;
  };
}

export interface GolmokData {
  block_area: any;
  wrc_poplt_ha: any;
  repop_sex_age: any;
  // ... 12개 데이터 타입
}

// ... 기타 타입 정의
```

### 3.5 CSS/Styling

Leaflet CSS 임포트 필요 (`index.tsx` 또는 `App.tsx`):
```tsx
import 'leaflet/dist/leaflet.css';
```

---

## 📅 Phase 4: 통합 테스트 (1-2일)

### 4.1 테스트 체크리스트

- [ ] 좌표 변환 정확도 검증 (WGS84 ↔ EPSG:5181)
- [ ] 나이스 API 호출 성공
- [ ] 골목 API 12개 엔드포인트 호출 성공
- [ ] OpenAI API 연동 확인
- [ ] 프론트엔드-백엔드 통합 테스트
- [ ] 지도 그리기 기능 (반경, 폴리곤)
- [ ] 차트 렌더링 확인
- [ ] 로그인 인증 적용
- [ ] 에러 핸들링 확인
- [ ] 모바일 반응형 확인

### 4.2 테스트 위치 예시

**서울시청 (테스트용)**:
- 위도: 37.5665
- 경도: 126.9780
- 반경: 500m

---

## 🔧 환경 설정 참고사항

### API 인증 정보 획득

#### 1. 서울골목상권 API
- URL: https://golmok.seoul.go.kr
- 쿠키/세션 정보 필요
- 브라우저 개발자 도구 → Network → Cookie 복사

#### 2. 나이스비즈맵 API
- URL: https://apis.nicebizmap.co.kr
- 공개 API (제한적)

#### 3. OpenAI API
- URL: https://platform.openai.com/api-keys
- API 키 발급 필요

---

## 📦 총 예상 기간

| Phase | 작업 내용 | 예상 시간 |
|-------|---------|---------|
| Phase 1 | 의존성 추가 | 1일 |
| Phase 2 | 백엔드 구현 | 3-4일 |
| Phase 3 | 프론트엔드 구현 | 4-5일 |
| Phase 4 | 통합 테스트 | 1-2일 |
| **Total** | | **9-12일** |

---

## 🎯 성공 기준

- [x] DB 없이 실시간 API 호출만으로 동작
- [x] 별도 탭으로 깔끔하게 분리
- [x] 기존 부동산 검색 기능에 영향 없음
- [x] 로그인 사용자만 접근 가능
- [ ] Python 프로젝트와 동일한 분석 결과 제공
- [ ] 반응형 UI (모바일 지원)
- [ ] 에러 처리 및 로딩 상태 표시

---

## 📝 작업 진행 상황

### ✅ 완료
- [ ] Phase 1.1: Backend 의존성 추가
- [ ] Phase 1.2: Frontend 의존성 추가

### 🔄 진행 중
-

### 📅 예정
- Phase 2: 백엔드 구현
- Phase 3: 프론트엔드 구현
- Phase 4: 통합 테스트

---

## 🚨 주의사항

1. **API 키 보안**
   - `application.properties`에 직접 입력 금지
   - 환경변수 사용 또는 `.env` 파일 (`.gitignore` 추가)

2. **좌표계 변환**
   - EPSG:5181은 한국 전용 좌표계
   - 서울 외 지역 사용 시 다른 좌표계 필요할 수 있음

3. **API Rate Limiting**
   - 서울골목 API: 순차 호출 (12개) → 시간 소요
   - 캐싱 고려 (선택사항)

4. **CORS 설정**
   - 개발: localhost:3000 → localhost:8081 (허용됨)
   - 배포: 도메인 설정 확인 필요

---

## 📚 참고 자료

### Python 프로젝트 주요 파일
- Backend: `C:\Users\SEO\Desktop\commercial_analysis_qgis\backend\app.py`
- Frontend: `C:\Users\SEO\Desktop\commercial_analysis_qgis\frontend\src\`

### Java 프로젝트 주요 파일
- Backend: `C:\Users\SEO\IdeaProjects\Realtymate\src\main\java\com\realty\Realtymate\`
- Frontend: `C:\Users\SEO\IdeaProjects\Realtymate\realty-web\src\`

### 외부 문서
- GeoTools: https://docs.geotools.org/
- JTS: https://locationtech.github.io/jts/
- Leaflet: https://leafletjs.com/
- Recharts: https://recharts.org/

---

## 🏠 매물 제안서 기능 (AdminPage)

### 참고 파이썬 코드
- **제안서 메인**: `C:\Users\SEO\Desktop\zigbang\매물제안서\제안서_상가_최종.py`
- **지도 마킹**: `C:\Users\SEO\Desktop\zigbang\매물제안서\지도마킹.py`

### 현재 작업 내용
- **컴포넌트**: `realty-web/src/components/AdminPage.tsx`
- **기능**:
  - 매물번호로 매물 조회 (추천매물 포함)
  - 제안서 매물 목록 편집
  - 지도 미리보기 (네이버 지도 API)
  - PDF 생성 (jsPDF, 클라이언트에서 브라우저로 저장)

### TODO
- [ ] 네이버 지도 API 인증 문제 해결 (도메인 등록 필요)
- [ ] PDF에 지도 이미지 넣기
- [ ] PDF 한글 폰트 적용
- [ ] PDF 레이아웃 파이썬 코드처럼 완성

---

**마지막 업데이트**: 2025-12-18
