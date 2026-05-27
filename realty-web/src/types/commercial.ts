// 상권 분석 요청 타입
export interface AnalysisRequest {
  latitude: number;
  longitude: number;
  radius?: number;
  analysisType: 'radius' | 'polygon';
  polygonCoords?: [number, number][];
}

// 상권 분석 응답 타입
export interface AnalysisResponse {
  status: string;
  message?: string;
  data?: {
    floating_population?: any;
    golmok_analysis?: GolmokAnalysisData;
    ai_summary?: string;
  };
  metadata?: {
    latitude?: number;
    longitude?: number;
    radius?: number;
    area?: number;
    wkt?: string;
  };
}

// 골목상권 데이터 타입
export interface GolmokAnalysisData {
  status: string;
  data?: {
    block_area?: any;
    wrc_poplt_sex_age?: any;
    wrc_poplt_ha?: any;
    repop_sex_age?: any;
    selng_hour?: any;
    selng_week?: any;
    selng_age?: any;
    flpop_week_co?: any;
    flpop_hour_co?: any;
    apt_hshld_co?: any;
    repop_dnst_co?: any;
    flpop_co?: any;
    flpop_sex_age?: any;
  };
}

// 나이스 유동인구 데이터 타입
export interface NiceFloatingPopulation {
  status: string;
  data?: any;
}

// AI 요약 요청 타입
export interface AISummaryRequest {
  golmok_analysis: any;
}

// AI 요약 응답 타입
export interface AISummaryResponse {
  status: string;
  summary?: string;
  message?: string;
}
