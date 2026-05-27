import axios from 'axios';
import { getApiUrl } from '../config';
import { getAuthHeaders } from '../auth';
import {
  AnalysisRequest,
  AnalysisResponse,
  GolmokAnalysisData,
  NiceFloatingPopulation,
  AISummaryRequest,
  AISummaryResponse
} from '../types/commercial';

const API_BASE = `${getApiUrl()}/api/commercial`;

/**
 * 상권 분석 API 클라이언트
 */
export const commercialApi = {
  /**
   * 헬스 체크
   */
  healthCheck: async (): Promise<any> => {
    const response = await axios.get(`${API_BASE}/health`, {
      headers: getAuthHeaders()
    });
    return response.data;
  },

  /**
   * 기본 분석 (반경)
   */
  analyzeBasic: async (
    latitude: number,
    longitude: number,
    radius: number = 500
  ): Promise<AnalysisResponse> => {
    const response = await axios.post(
      `${API_BASE}/analyze`,
      { latitude, longitude, radius },
      { headers: getAuthHeaders() }
    );
    return response.data;
  },

  /**
   * 폴리곤 분석
   */
  analyzePolygon: async (
    polygonCoords: [number, number][]
  ): Promise<AnalysisResponse> => {
    const response = await axios.post(
      `${API_BASE}/analyze-polygon`,
      { polygonCoords },
      { headers: getAuthHeaders() }
    );
    return response.data;
  },

  /**
   * 골목상권 데이터만 조회
   */
  getGolmokData: async (
    latitude: number,
    longitude: number,
    radius: number = 500
  ): Promise<GolmokAnalysisData> => {
    const response = await axios.post(
      `${API_BASE}/golmok`,
      { latitude, longitude, radius },
      { headers: getAuthHeaders() }
    );
    return response.data;
  },

  /**
   * 나이스비즈맵 데이터만 조회
   */
  getNiceData: async (
    latitude: number,
    longitude: number,
    radius: number = 500
  ): Promise<NiceFloatingPopulation> => {
    const response = await axios.post(
      `${API_BASE}/nice`,
      { latitude, longitude, radius },
      { headers: getAuthHeaders() }
    );
    return response.data;
  },

  /**
   * AI 요약만 생성
   */
  getAISummary: async (
    golmokAnalysis: any
  ): Promise<AISummaryResponse> => {
    const response = await axios.post(
      `${API_BASE}/ai-summary`,
      { golmok_analysis: golmokAnalysis },
      { headers: getAuthHeaders() }
    );
    return response.data;
  },

  /**
   * 통합 분석 (나이스 + 골목 + AI)
   */
  integratedAnalysis: async (
    request: AnalysisRequest
  ): Promise<AnalysisResponse> => {
    const response = await axios.post(
      `${API_BASE}/integrated`,
      request,
      { headers: getAuthHeaders() }
    );
    return response.data;
  }
};
