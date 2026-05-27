# 부동산 매물 검색 웹 앱

React + TypeScript로 구현된 모바일 친화적 부동산 매물 검색 애플리케이션입니다.

## 주요 기능

### 1. 검색 기능
- **URL 주소 조회**: 매물 URL을 입력하여 주소 자동 추출
- **주소 검색**: 주소와 층수를 입력하여 매물 검색
- **매물 종류 선택**: 주택 또는 상가 선택
- **플랫폼 필터**:
  - 주택: 직방, 다방, 피터팬, 네이버
  - 상가: 네이버, 네모
- **거래 방식 필터**: 매매, 전세, 월세

### 2. 검색 결과
- **데스크톱**: 테이블 형태로 전체 정보 표시
- **모바일**: 카드 형태로 최적화된 뷰 제공
- **정렬 기능**: 각 컬럼 클릭으로 정렬 가능
- **상세 정보**:
  - 주소, 층, 면적(평)
  - 보증금/매매가, 월세, 관리비
  - 사진 유무, 중개사무소
  - 등록일, 입주가능일
  - 소유자 구분, 플랫폼

### 3. 편의 기능
- **주소 복사**: 클립보드에 주소 복사
- **전체열기**: 모든 매물 URL 새 탭에서 열기
- **사진열기**: 사진이 있는 매물만 새 탭에서 열기
- **네이버 지도 연동**: 주소 클릭 시 네이버 지도로 이동

## 설치 및 실행

### 필수 요구사항
- Node.js 16.0 이상
- npm 또는 yarn

### 설치
```bash
cd realty-web
npm install
```

### 개발 서버 실행
```bash
npm start
```
브라우저에서 http://localhost:3000 으로 접속

### 프로덕션 빌드
```bash
npm run build
```

## 프로젝트 구조

```
realty-web/
├── public/
│   └── index.html          # HTML 템플릿
├── src/
│   ├── components/         # React 컴포넌트
│   │   ├── SearchFilter.tsx        # 검색 필터 컴포넌트
│   │   ├── SearchFilter.css
│   │   ├── ResultsTable.tsx        # 결과 테이블 컴포넌트
│   │   └── ResultsTable.css
│   ├── types/              # TypeScript 타입 정의
│   │   └── index.ts
│   ├── App.tsx             # 메인 앱 컴포넌트
│   ├── App.css
│   ├── index.tsx           # 엔트리 포인트
│   └── index.css
├── package.json
├── tsconfig.json
└── README.md
```

## 기술 스택

- **React 18**: UI 라이브러리
- **TypeScript**: 정적 타입 검사
- **CSS3**: 스타일링 (반응형 디자인)
- **React Hooks**: 상태 관리

## 반응형 디자인

### 데스크톱 (768px 이상)
- 2단 레이아웃 (필터 + 검색 정보)
- 테이블 형태의 결과 표시
- 다중 컬럼 정렬 지원

### 모바일 (768px 미만)
- 1단 레이아웃
- 카드 형태의 결과 표시
- 터치 친화적 인터페이스
- 간결한 정보 표시

## API 연동

현재는 더미 데이터로 동작합니다. 실제 API 연동을 위해서는:

1. `src/App.tsx`의 `handleSearch` 함수 수정
2. `handleUrlSearch` 함수에서 실제 API 호출
3. 백엔드 API 엔드포인트 설정

예시:
```typescript
// Spring Boot 백엔드 연동 예시
const handleSearch = async () => {
  const response = await fetch('http://localhost:8080/api/items/search', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(filters)
  });
  const data = await response.json();
  setResults(data);
};
```

## 다음 단계 개발 사항

- [ ] 실제 백엔드 API 연동
- [ ] 로딩 상태 애니메이션 개선
- [ ] 에러 처리 강화
- [ ] 검색 필터 저장 기능 (LocalStorage)
- [ ] 즐겨찾기 기능
- [ ] 검색 결과 내보내기 (CSV, Excel)
- [ ] 다크 모드 지원
- [ ] PWA 지원

## 라이선스

MIT
