import React, { useState, useEffect, useRef } from 'react';
import { MapContainer, TileLayer, Circle, Marker, useMapEvents, GeoJSON, Popup, useMap, CircleMarker, Polygon, Polyline } from 'react-leaflet';
import L from 'leaflet';
import 'leaflet/dist/leaflet.css';
import 'leaflet.heat';
import axios from 'axios';
import { getApiUrl } from '../../config';
import { getAuthHeaders } from '../../auth';
import './CommercialMap.css';
import GolmokAnalysisResult from './golmok/GolmokAnalysisResult';
import AnalysisResultModal from './AnalysisResultModal';
import jsPDF from 'jspdf';
import html2canvas from 'html2canvas';
import JSZip from 'jszip';

// Leaflet 기본 아이콘 설정
delete (L.Icon.Default.prototype as any)._getIconUrl;
L.Icon.Default.mergeOptions({
  iconRetinaUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.7.1/images/marker-icon-2x.png',
  iconUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.7.1/images/marker-icon.png',
  shadowUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.7.1/images/marker-shadow.png',
});

interface AnalysisParams {
  latitude: number;
  longitude: number;
  radius: number;
  gridSize: number;
  ageGroup: string;
  gender: 'all' | 'male' | 'female';
  analysisType: 'radius' | 'polygon';
  polygonCoords: [number, number][];
}

interface SpatialResult {
  center?: any;
  buffer?: any;
  grid?: any;
  points?: any;
  statistics?: {
    total_facilities: number;
    grid_count: number;
    avg_facilities_per_grid: number;
    density_distribution: Record<string, number>;
    analysis_area: string;
    grid_size: string;
  };
  // 기본 분석 결과
  location?: {
    latitude: number;
    longitude: number;
    radius: number;
  };
  demographics?: {
    total_population: number;
    target_population: number;
    age_range: string;
    gender: string;
  };
  facilities?: {
    restaurants: number;
    cafes: number;
    convenience_stores: number;
    bus_stops: number;
    subway_stations: number;
  };
  scores?: {
    total_score: number;
    population_score: number;
    facility_score: number;
    accessibility_score: number;
    grade: string;
    recommendation: string;
  };
  floating_population?: any;
  ai_summary?: string;
  golmok_analysis?: any;
}

// 지도 클릭 핸들러
function MapClickHandler({
  onClick,
  onRightClick
}: {
  onClick: (lat: number, lng: number) => void;
  onRightClick?: () => void;
}) {
  useMapEvents({
    click: (e) => {
      onClick(e.latlng.lat, e.latlng.lng);
    },
    contextmenu: () => {
      if (onRightClick) {
        onRightClick();
      }
    }
  });
  return null;
}

// 라인을 일정 간격으로 샘플링하는 함수
function sampleLineToPoints(coords: [number, number][], intervalMeters: number, grade: number) {
  const points: Array<{ lat: number; lng: number; grade: number }> = [];

  // Haversine 거리 계산
  const haversine = (lat1: number, lng1: number, lat2: number, lng2: number) => {
    const R = 6371000; // 지구 반경 (미터)
    const toRad = (deg: number) => (deg * Math.PI) / 180;
    const dLat = toRad(lat2 - lat1);
    const dLng = toRad(lng2 - lng1);
    const a = Math.sin(dLat / 2) ** 2 +
              Math.cos(toRad(lat1)) * Math.cos(toRad(lat2)) *
              Math.sin(dLng / 2) ** 2;
    const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    return R * c;
  };

  // 두 점 사이를 선형 보간
  const interpolate = (lat1: number, lng1: number, lat2: number, lng2: number, ratio: number) => {
    return {
      lat: lat1 + (lat2 - lat1) * ratio,
      lng: lng1 + (lng2 - lng1) * ratio
    };
  };

  // 각 라인 세그먼트를 따라 포인트 생성
  for (let i = 0; i < coords.length - 1; i++) {
    const [lat1, lng1] = coords[i];
    const [lat2, lng2] = coords[i + 1];

    const segmentLength = haversine(lat1, lng1, lat2, lng2);
    const numPoints = Math.max(1, Math.floor(segmentLength / intervalMeters));

    for (let j = 0; j <= numPoints; j++) {
      const ratio = j / numPoints;
      const point = interpolate(lat1, lng1, lat2, lng2, ratio);
      points.push({ ...point, grade });
    }
  }

  return points;
}

// 점과 선분 사이의 거리 계산 함수
function pointToLineSegmentDistance(
  pointLat: number,
  pointLng: number,
  lat1: number,
  lng1: number,
  lat2: number,
  lng2: number
): number {
  const R = 6371000; // 지구 반경 (미터)

  // Haversine 거리 계산
  const haversine = (latA: number, lngA: number, latB: number, lngB: number) => {
    const toRad = (deg: number) => (deg * Math.PI) / 180;
    const dLat = toRad(latB - latA);
    const dLng = toRad(lngB - lngA);
    const a = Math.sin(dLat / 2) ** 2 +
              Math.cos(toRad(latA)) * Math.cos(toRad(latB)) *
              Math.sin(dLng / 2) ** 2;
    const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    return R * c;
  };

  // 선분의 길이
  const segmentLength = haversine(lat1, lng1, lat2, lng2);
  if (segmentLength === 0) {
    return haversine(pointLat, pointLng, lat1, lng1);
  }

  // 점에서 선분으로의 투영 비율 계산
  const dx = lat2 - lat1;
  const dy = lng2 - lng1;
  const t = Math.max(0, Math.min(1,
    ((pointLat - lat1) * dx + (pointLng - lng1) * dy) /
    (dx * dx + dy * dy)
  ));

  // 투영점 계산
  const projLat = lat1 + t * dx;
  const projLng = lng1 + t * dy;

  // 점과 투영점 사이의 거리 반환
  return haversine(pointLat, pointLng, projLat, projLng);
}

// 통합 히트맵 컴포넌트 (나이스 + 골목)
function CombinedHeatmapLayer({
  nicePoints,
  golmokLines,
  analysisParams,
  onClusteredPoints,
  onGolmokSampledPoints,
  onRawHeatmapPoints
}: {
  nicePoints: Array<{ lat: number; lng: number; flowpopCount: number; grade: number }>;
  golmokLines: Array<{ coords: [number, number][]; grade: number; cost: number }>;
  analysisParams: AnalysisParams;
  onClusteredPoints?: (points: Array<{ lat: number; lng: number; avgGrade: number; isClustered: boolean; count: number; clusterLevel: number }>) => void;
  onGolmokSampledPoints?: (points: Array<{ lat: number; lng: number; grade: number }>) => void;
  onRawHeatmapPoints?: (points: Array<[number, number, number]>) => void;
}) {
  const map = useMap();
  const [zoom, setZoom] = React.useState(map.getZoom());

  useEffect(() => {
    //console.log('=== CombinedHeatmapLayer useEffect 실행 ===');
    //console.log('nicePoints:', nicePoints.length);
    //console.log('golmokLines:', golmokLines.length);

    if (!map) {
      //console.log('map이 없음');
      return;
    }
    if (nicePoints.length === 0 && golmokLines.length === 0) {
      //console.log('데이터가 없음');
      return;
    }

    // 분석 영역 필터링 함수
    const isPointInAnalysisArea = (lat: number, lng: number): boolean => {
      if (analysisParams.analysisType === 'radius') {
        // 반경 분석: Haversine 거리 계산
        const R = 6371000; // 지구 반경 (미터)
        const toRad = (deg: number) => (deg * Math.PI) / 180;
        const dLat = toRad(lat - analysisParams.latitude);
        const dLng = toRad(lng - analysisParams.longitude);
        const a = Math.sin(dLat / 2) ** 2 +
                  Math.cos(toRad(analysisParams.latitude)) * Math.cos(toRad(lat)) *
                  Math.sin(dLng / 2) ** 2;
        const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        const distance = R * c;
        return distance <= analysisParams.radius;
      } else if (analysisParams.analysisType === 'polygon' && analysisParams.polygonCoords.length >= 3) {
        // 폴리곤 분석: point-in-polygon 알고리즘
        let inside = false;
        const polygon = analysisParams.polygonCoords;
        for (let i = 0, j = polygon.length - 1; i < polygon.length; j = i++) {
          const xi = polygon[i][0], yi = polygon[i][1];
          const xj = polygon[j][0], yj = polygon[j][1];
          const intersect = ((yi > lng) !== (yj > lng)) &&
                           (lat < (xj - xi) * (lng - yi) / (yj - yi) + xi);
          if (intersect) inside = !inside;
        }
        return inside;
      }
      return true; // 분석 타입이 없으면 모두 표시
    };

    // 나이스 포인트 필터링
    const filteredNicePoints = nicePoints.filter(point =>
      isPointInAnalysisArea(point.lat, point.lng)
    );

    // 골목 라인을 7m 간격으로 샘플링
    const golmokSampledPoints: Array<{ lat: number; lng: number; grade: number }> = [];
    golmokLines.forEach((line) => {
      const points = sampleLineToPoints(line.coords, 7, line.grade);
      const filteredPoints = points.filter(point => isPointInAnalysisArea(point.lat, point.lng));
      golmokSampledPoints.push(...filteredPoints);
    });

    //console.log('나이스 포인트:', filteredNicePoints.length);
    //console.log('골목 샘플링 포인트:', golmokSampledPoints.length);

    // 골목 샘플링 포인트를 부모 컴포넌트로 전달
    //console.log('골목 샘플링 포인트 전달:', golmokSampledPoints.length);
    if (onGolmokSampledPoints) {
      onGolmokSampledPoints(golmokSampledPoints);
    }

    // 줌 레벨에 따른 설정 (밀집도 영향 최소화, Grade 영향 최대화)
    let radius: number, blur: number, maxValue: number, minOpacity: number;

    if (zoom <= 15) {
      radius = 5;           // 영향 범위 대폭 축소 (겹침 최소화)
      blur = 3;             // 흐림 효과 최소화
      maxValue = 1.0;
      minOpacity = 0.1;
    } else if (zoom <= 17){
      radius = 6;           // 영향 범위 대폭 축소
      blur = 4;             // 흐림 효과 최소화
      maxValue = 1.0;
      minOpacity = 0.2;
    } else {
      radius = 16;           // 영향 범위 대폭 축소
      blur = 22;             // 흐림 효과 최소화
      maxValue = 1.0;
      minOpacity = 0.2;
    }

    // 가중치 적용 (나이스 더 신뢰: 8:2)
    const NICE_WEIGHT = 1;
    const GOLMOK_WEIGHT = 1;

    // 모든 점을 하나의 배열로 합침 (가중치 적용된 grade와 함께)
    const allPoints: Array<{ lat: number; lng: number; weightedGrade: number }> = [];

    // 나이스 포인트 처리: grade로 변환
    filteredNicePoints.forEach(point => {
      let niceGrade = 1;
      if (point.flowpopCount >= 20000) niceGrade = 5;
      else if (point.flowpopCount >= 5000) niceGrade = 4;
      else if (point.flowpopCount >= 1000) niceGrade = 3;
      else if (point.flowpopCount >= 500) niceGrade = 2;
      else niceGrade = 1;

      allPoints.push({
        lat: point.lat,
        lng: point.lng,
        weightedGrade: niceGrade * NICE_WEIGHT
      });
    });

    // 골목 포인트 처리: 일단 제외
    // golmokSampledPoints.forEach(point => {
    //   allPoints.push({
    //     lat: point.lat,
    //     lng: point.lng,
    //     weightedGrade: point.grade  // 가중치 없이 grade 그대로
    //   });
    // });

    // 3m 이내의 점들을 재귀적으로 클러스터링하여 평균값으로 합침
    const haversine = (lat1: number, lng1: number, lat2: number, lng2: number) => {
      const R = 6371000;
      const toRad = (deg: number) => (deg * Math.PI) / 180;
      const dLat = toRad(lat2 - lat1);
      const dLng = toRad(lng2 - lng1);
      const a = Math.sin(dLat / 2) ** 2 +
                Math.cos(toRad(lat1)) * Math.cos(toRad(lat2)) *
                Math.sin(dLng / 2) ** 2;
      const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
      return R * c;
    };

    // 재귀적 클러스터링: 더 이상 3m 이내 점이 없을 때까지 반복
    let currentPoints = allPoints.map(p => ({
      lat: p.lat,
      lng: p.lng,
      avgGrade: p.weightedGrade,
      count: 1,
      clusterLevel: 0  // 클러스터 단계 추가
    }));

    let hasChanged = true;
    let iterationCount = 0;
    const maxIterations = 100; // 무한루프 방지

    // 각 반복마다 합쳐진 개수 추적
    const iterationStats: Array<{ iteration: number; totalPoints: number; mergedInThisIteration: number }> = [];

    while (hasChanged && iterationCount < maxIterations) {
      hasChanged = false;
      iterationCount++;

      const newPoints: Array<{ lat: number; lng: number; avgGrade: number; count: number; clusterLevel: number }> = [];
      const used = new Set<number>();
      let mergedCount = 0;

      currentPoints.forEach((point, i) => {
        if (used.has(i)) return;

        const cluster = [point];
        used.add(i);

        // 3m 이내의 다른 점들 찾기
        for (let j = i + 1; j < currentPoints.length; j++) {
          if (used.has(j)) continue;
          const distance = haversine(point.lat, point.lng, currentPoints[j].lat, currentPoints[j].lng);
          if (distance <= 3) {
            cluster.push(currentPoints[j]);
            used.add(j);
            hasChanged = true; // 합쳐진 경우 다음 반복 필요
          }
        }

        // 클러스터의 평균 위치와 평균 grade 계산
        const totalCount = cluster.reduce((sum, p) => sum + p.count, 0);
        const avgLat = cluster.reduce((sum, p) => sum + p.lat * p.count, 0) / totalCount;
        const avgLng = cluster.reduce((sum, p) => sum + p.lng * p.count, 0) / totalCount;
        const avgGrade = cluster.reduce((sum, p) => sum + p.avgGrade * p.count, 0) / totalCount;
        const maxClusterLevel = Math.max(...cluster.map(p => p.clusterLevel));

        newPoints.push({
          lat: avgLat,
          lng: avgLng,
          avgGrade,
          count: totalCount,
          clusterLevel: cluster.length > 1 ? maxClusterLevel + 1 : maxClusterLevel
        });

        if (cluster.length > 1) {
          mergedCount++;
        }
      });

      iterationStats.push({
        iteration: iterationCount,
        totalPoints: newPoints.length,
        mergedInThisIteration: mergedCount
      });

      currentPoints = newPoints;
    }

    //console.log(`=== 클러스터링 통계 ===`);
    //console.log(`총 반복 횟수: ${iterationCount}회`);
    iterationStats.forEach(stat => {
      //console.log(`${stat.iteration}차 클러스터링: 총 ${stat.totalPoints}개 점, 이번 반복에서 ${stat.mergedInThisIteration}개 합쳐짐`);
    });
    //console.log('최종 클러스터링된 점 개수:', currentPoints.length);
    //console.log('합쳐진 점 개수:', currentPoints.filter(p => p.count > 1).length);

    // 클러스터 레벨별 개수
    const levelCounts = new Map<number, number>();
    currentPoints.forEach(p => {
      levelCounts.set(p.clusterLevel, (levelCounts.get(p.clusterLevel) || 0) + 1);
    });
    //console.log('클러스터 레벨별 개수:', Object.fromEntries(levelCounts));

    const clusteredPoints = currentPoints.map(p => ({
      ...p,
      isClustered: p.count > 1
    }));

    // 히트맵 데이터 생성 (intensity만으로 Grade 표현)
    const combinedHeatData: [number, number, number][] = [];

    clusteredPoints.forEach(point => {
      // avgGrade를 0~1 사이 intensity로 선형 변환
      // Grade 5 = 1.0, 4 = 0.8, 3 = 0.6, 2 = 0.4, 1 = 0.2
      const intensity = point.avgGrade / 5;

      // 각 위치에 1개 점만 찍기 (intensity로만 강도 조절)
      combinedHeatData.push([point.lat, point.lng, intensity]);
    });

    //console.log('클러스터링 전 포인트:', allPoints.length);
    //console.log('클러스터링 후 포인트:', clusteredPoints.length);
    //console.log('전체 히트맵 포인트:', combinedHeatData.length);

    // 클러스터링된 점들을 부모 컴포넌트로 전달
    //console.log('clusteredPoints 전달:', clusteredPoints.length);
    //console.log('합쳐진 점만:', clusteredPoints.filter(p => p.isClustered).length);
    if (onClusteredPoints) {
      onClusteredPoints(clusteredPoints);
    }

    // 히트맵 원본 점들을 부모 컴포넌트로 전달
    if (onRawHeatmapPoints) {
      onRawHeatmapPoints(combinedHeatData);
    }

    // @ts-ignore
    const heatLayer = L.heatLayer(combinedHeatData, {
      radius: radius,
      blur: blur,
      max: maxValue,
      minOpacity: minOpacity,
      gradient: {
        0.0: 'rgba(70, 65, 216, 0)',
        0.3: '#4641D8',
        0.5: '#47C83E',
        0.7: '#FAED7D',
        0.85: '#FF6B00',
        1.0: '#FF0000'
      }
    }).addTo(map);

    // 줌 변경 시 state 업데이트
    const onZoomEnd = () => {
      setZoom(map.getZoom());
    };

    map.on('zoomend', onZoomEnd);

    return () => {
      map.off('zoomend', onZoomEnd);
      map.removeLayer(heatLayer);
    };
  }, [map, nicePoints, golmokLines, analysisParams, zoom]);

  return null;
}

// 골목 전용 히트맵 컴포넌트
function GolmokOnlyHeatmapLayer({
  golmokLines,
  analysisParams,
  onGolmokHeatmapPoints,
  showHeatmap = true
}: {
  golmokLines: Array<{ coords: [number, number][]; grade: number; cost: number }>;
  analysisParams: AnalysisParams;
  onGolmokHeatmapPoints?: (points: Array<{ lat: number; lng: number; grade: number; intensity: number }>) => void;
  showHeatmap?: boolean;
}) {
  const map = useMap();
  const [zoom, setZoom] = React.useState(Math.floor(map.getZoom()));

  useEffect(() => {
    if (!map) return;
    if (golmokLines.length === 0) return;

    // 분석 영역 필터링 함수
    const isPointInAnalysisArea = (lat: number, lng: number): boolean => {
      if (analysisParams.analysisType === 'radius') {
        const R = 6371000;
        const toRad = (deg: number) => (deg * Math.PI) / 180;
        const dLat = toRad(lat - analysisParams.latitude);
        const dLng = toRad(lng - analysisParams.longitude);
        const a = Math.sin(dLat / 2) ** 2 +
                  Math.cos(toRad(analysisParams.latitude)) * Math.cos(toRad(lat)) *
                  Math.sin(dLng / 2) ** 2;
        const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        const distance = R * c;
        return distance <= analysisParams.radius;
      } else if (analysisParams.analysisType === 'polygon' && analysisParams.polygonCoords.length >= 3) {
        let inside = false;
        const polygon = analysisParams.polygonCoords;
        for (let i = 0, j = polygon.length - 1; i < polygon.length; j = i++) {
          const xi = polygon[i][0], yi = polygon[i][1];
          const xj = polygon[j][0], yj = polygon[j][1];
          const intersect = ((yi > lng) !== (yj > lng)) &&
                           (lat < (xj - xi) * (lng - yi) / (yj - yi) + xi);
          if (intersect) inside = !inside;
        }
        return inside;
      }
      return true;
    };

    // 골목 라인을 7m 간격으로 샘플링
    const golmokSampledPoints: Array<{ lat: number; lng: number; grade: number }> = [];
    golmokLines.forEach((line) => {
      const points = sampleLineToPoints(line.coords, 7, line.grade);
      const filteredPoints = points.filter(point => isPointInAnalysisArea(point.lat, point.lng));
      golmokSampledPoints.push(...filteredPoints);
    });

    //console.log('골목 전용 히트맵 - 샘플링 포인트:', golmokSampledPoints.length);

    // 3m 이내의 점들을 재귀적으로 클러스터링
    const haversine = (lat1: number, lng1: number, lat2: number, lng2: number) => {
      const R = 6371000;
      const toRad = (deg: number) => (deg * Math.PI) / 180;
      const dLat = toRad(lat2 - lat1);
      const dLng = toRad(lng2 - lng1);
      const a = Math.sin(dLat / 2) ** 2 +
                Math.cos(toRad(lat1)) * Math.cos(toRad(lat2)) *
                Math.sin(dLng / 2) ** 2;
      const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
      return R * c;
    };

    let currentPoints = golmokSampledPoints.map(p => ({
      lat: p.lat,
      lng: p.lng,
      avgGrade: p.grade,
      count: 1,
      clusterLevel: 0
    }));

    let hasChanged = true;
    let iterationCount = 0;
    const maxIterations = 100;

    while (hasChanged && iterationCount < maxIterations) {
      hasChanged = false;
      iterationCount++;

      const newPoints: Array<{ lat: number; lng: number; avgGrade: number; count: number; clusterLevel: number }> = [];
      const used = new Set<number>();

      currentPoints.forEach((point, i) => {
        if (used.has(i)) return;

        const cluster = [point];
        used.add(i);

        for (let j = i + 1; j < currentPoints.length; j++) {
          if (used.has(j)) continue;
          const distance = haversine(point.lat, point.lng, currentPoints[j].lat, currentPoints[j].lng);
          if (distance <= 3) {
            cluster.push(currentPoints[j]);
            used.add(j);
            hasChanged = true;
          }
        }

        const totalCount = cluster.reduce((sum, p) => sum + p.count, 0);
        const avgLat = cluster.reduce((sum, p) => sum + p.lat * p.count, 0) / totalCount;
        const avgLng = cluster.reduce((sum, p) => sum + p.lng * p.count, 0) / totalCount;
        const avgGrade = cluster.reduce((sum, p) => sum + p.avgGrade * p.count, 0) / totalCount;
        const maxClusterLevel = Math.max(...cluster.map(p => p.clusterLevel));

        newPoints.push({
          lat: avgLat,
          lng: avgLng,
          avgGrade,
          count: totalCount,
          clusterLevel: cluster.length > 1 ? maxClusterLevel + 1 : maxClusterLevel
        });
      });

      currentPoints = newPoints;
    }

    //console.log(`골목 전용 히트맵 - 클러스터링 ${iterationCount}회 반복`);
    //console.log('골목 전용 히트맵 - 최종 클러스터링 점 개수:', currentPoints.length);

    // 줌 레벨에 따른 설정
    let radius: number, blur: number;
    if (zoom <= 15) {
      radius = 5;
      blur = 3;
    } else if (zoom <= 17) {
      radius = 6;
      blur = 4;
    } else {
      radius = 15.5;
      blur = 21;
    }

    // 골목 Grade를 intensity로 변환 (intensityPower 적용)
    const golmokHeatData: [number, number, number][] = [];
    const golmokHeatmapPoints: Array<{ lat: number; lng: number; grade: number; intensity: number }> = [];

    currentPoints.forEach(point => {
      // Grade를 0~1 사이 intensity로 선형 변환
      // Grade 5 = 1.0, 4 = 0.8, 3 = 0.6, 2 = 0.4, 1 = 0.2
      const intensity = point.avgGrade / 5;

      // 각 위치에 1개 점만 찍기 (intensity로만 강도 조절)
      golmokHeatData.push([point.lat, point.lng, intensity]);

      // 히트맵 직전 데이터 저장
      golmokHeatmapPoints.push({
        lat: point.lat,
        lng: point.lng,
        grade: Math.round(point.avgGrade), // 평균 Grade를 반올림
        intensity: intensity
      });
    });

    //console.log('골목 전용 히트맵 포인트:', golmokHeatData.length);

    // 히트맵 직전 데이터를 부모 컴포넌트로 전달
    if (onGolmokHeatmapPoints) {
      onGolmokHeatmapPoints(golmokHeatmapPoints);
    }

    // 히트맵 레이어 생성 (showHeatmap이 true일 때만)
    if (showHeatmap) {
      // @ts-ignore
      const heatLayer = L.heatLayer(golmokHeatData, {
        radius: radius,
        blur: blur,
        max: 1.0,
        minOpacity: 0.2,
        gradient: {
          0.0: 'rgba(70, 65, 216, 0)',
          0.3: '#4641D8',
          0.5: '#47C83E',
          0.7: '#FAED7D',
          0.85: '#FF6B00',
          1.0: '#FF0000'
        }
      }).addTo(map);

      // 줌 변경 시 state 업데이트
      const onZoomEnd = () => {
        setZoom(Math.floor(map.getZoom()));
      };

      map.on('zoomend', onZoomEnd);

      return () => {
        map.off('zoomend', onZoomEnd);
        map.removeLayer(heatLayer);
      };
    }
  }, [map, golmokLines, analysisParams, zoom, showHeatmap]);

  return null;
}

// 나이스 전용 히트맵 레이어 (5m 클러스터링)
function NiceOnlyHeatmapLayer({
  nicePoints,
  analysisParams,
  onClusteredPoints,
  showHeatmap = true
}: {
  nicePoints: any[];
  analysisParams: AnalysisParams;
  onClusteredPoints?: (points: Array<{ lat: number; lng: number; grade: number; count: number; intensity: number }>) => void;
  showHeatmap?: boolean;
}) {
  const map = useMap();
  const [zoom, setZoom] = React.useState(Math.floor(map.getZoom()));

  useEffect(() => {
    if (!map) return;
    if (nicePoints.length === 0) return;

    // 폴리곤 내부 체크 함수
    const isPointInPolygon = (lat: number, lng: number): boolean => {
      if (analysisParams.analysisType !== 'polygon') return true;
      if (analysisParams.polygonCoords.length < 3) return true;

      let inside = false;
      const polygon = analysisParams.polygonCoords;
      for (let i = 0, j = polygon.length - 1; i < polygon.length; j = i++) {
        const xi = polygon[i][0], yi = polygon[i][1];
        const xj = polygon[j][0], yj = polygon[j][1];
        const intersect = ((yi > lng) !== (yj > lng)) && (lat < (xj - xi) * (lng - yi) / (yj - yi) + xi);
        if (intersect) inside = !inside;
      }
      return inside;
    };

    // 폴리곤 필터링 적용
    const filteredPoints = nicePoints.filter(p => isPointInPolygon(p.lat, p.lng));
    if (filteredPoints.length === 0) return;

    // Haversine 거리 계산
    const haversine = (lat1: number, lng1: number, lat2: number, lng2: number) => {
      const R = 6371000;
      const toRad = (deg: number) => (deg * Math.PI) / 180;
      const dLat = toRad(lat2 - lat1);
      const dLng = toRad(lng2 - lng1);
      const a = Math.sin(dLat / 2) ** 2 +
                Math.cos(toRad(lat1)) * Math.cos(toRad(lat2)) *
                Math.sin(dLng / 2) ** 2;
      const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
      return R * c;
    };

    // 5m 재귀적 클러스터링
    let currentPoints = filteredPoints.map(p => ({
      lat: p.lat,
      lng: p.lng,
      grade: p.grade,
      count: 1
    }));

    const maxIterations = 100;
    let iterationCount = 0;
    let hasChanged = true;

    while (hasChanged && iterationCount < maxIterations) {
      hasChanged = false;
      iterationCount++;

      const newPoints: typeof currentPoints = [];
      const used = new Set<number>();

      for (let i = 0; i < currentPoints.length; i++) {
        if (used.has(i)) continue;

        const cluster = [currentPoints[i]];
        used.add(i);

        for (let j = i + 1; j < currentPoints.length; j++) {
          if (used.has(j)) continue;

          const distance = haversine(
            currentPoints[i].lat, currentPoints[i].lng,
            currentPoints[j].lat, currentPoints[j].lng
          );

          if (distance <= 5) {
            cluster.push(currentPoints[j]);
            used.add(j);
            hasChanged = true;
          }
        }

        if (cluster.length > 1) {
          const totalWeight = cluster.reduce((sum, p) => sum + p.count, 0);
          const avgLat = cluster.reduce((sum, p) => sum + p.lat * p.count, 0) / totalWeight;
          const avgLng = cluster.reduce((sum, p) => sum + p.lng * p.count, 0) / totalWeight;
          const avgGrade = cluster.reduce((sum, p) => sum + p.grade * p.count, 0) / totalWeight;

          newPoints.push({
            lat: avgLat,
            lng: avgLng,
            grade: avgGrade, // 이미 변환된 grade (5=많음, 1=적음)
            count: totalWeight
          });
        } else {
          newPoints.push(currentPoints[i]);
        }
      }

      currentPoints = newPoints;
    }

    //console.log(`나이스 클러스터링 ${iterationCount}회 반복`);
    //console.log('최종 클러스터링 점 개수:', currentPoints.length);

    // 줌 레벨에 따른 설정
    let radius: number, blur: number;
    if (zoom <= 15) {
      radius = 5;
      blur = 3;
    } else if (zoom <= 17) {
      radius = 6;
      blur = 4;
    } else {
      radius = 16.5;
      blur = 20;
    }

    // grade를 intensity로 변환 (5=많음→1.0, 1=적음→0.2)
    const niceHeatData: [number, number, number][] = [];
    const pointsWithIntensity = currentPoints.map(point => {
      const intensity = point.grade / 5; // 5→1.0, 1→0.2
      niceHeatData.push([point.lat, point.lng, intensity]);
      return {
        ...point,
        intensity: intensity
      };
    });

    // 클러스터링된 점들을 부모 컴포넌트로 전달 (intensity 포함)
    if (onClusteredPoints) {
      onClusteredPoints(pointsWithIntensity);
    }

    //console.log('나이스 히트맵 포인트:', niceHeatData.length);

    // 히트맵 레이어 생성 (showHeatmap이 true일 때만)
    if (showHeatmap) {
      const heatLayer = (L as any).heatLayer(niceHeatData, {
        radius: radius,
        blur: blur,
        maxZoom: 19,
        max: 1.0,
        minOpacity: 0.2,
        gradient: {
          0.0: '#4641D8',   // 파랑 (Grade 1)
          0.25: '#47C83E',  // 초록 (Grade 2)
          0.5: '#FAED7D',   // 노랑 (Grade 3)
          0.75: '#F29661',  // 주황 (Grade 4)
          1.0: '#FF0000'    // 빨강 (Grade 5)
        }
      }).addTo(map);

      return () => {
        map.removeLayer(heatLayer);
      };
    }
  }, [map, nicePoints, analysisParams, zoom, showHeatmap, onClusteredPoints]);

  useEffect(() => {
    const onZoomEnd = () => {
      setZoom(Math.floor(map.getZoom()));
    };
    map.on('zoomend', onZoomEnd);
    return () => {
      map.off('zoomend', onZoomEnd);
    };
  }, [map]);

  return null;
}

// 통합 클러스터 히트맵 레이어
function CombinedClusteredHeatmapLayer({
  niceClusteredPoints,
  golmokClusteredPoints,
  analysisParams,
  onFinalClusteredPoints,
  showHeatmap = true
}: {
  niceClusteredPoints: Array<{ lat: number; lng: number; grade: number; count: number; intensity: number }>;
  golmokClusteredPoints: Array<{ lat: number; lng: number; grade: number; intensity: number }>;
  analysisParams: AnalysisParams;
  onFinalClusteredPoints?: (points: Array<{ lat: number; lng: number; avgGrade: number; count: number; intensity: number }>) => void;
  showHeatmap?: boolean;
}) {
  const map = useMap();
  const [zoom, setZoom] = React.useState(Math.floor(map.getZoom()));

  useEffect(() => {
    if (!map) return;
    if (niceClusteredPoints.length === 0 && golmokClusteredPoints.length === 0) return;

    // 폴리곤 필터링 함수
    const isPointInPolygon = (lat: number, lng: number): boolean => {
      if (analysisParams.analysisType !== 'polygon') return true;
      if (analysisParams.polygonCoords.length < 3) return true;

      let inside = false;
      const polygon = analysisParams.polygonCoords;
      for (let i = 0, j = polygon.length - 1; i < polygon.length; j = i++) {
        const xi = polygon[i][0], yi = polygon[i][1];
        const xj = polygon[j][0], yj = polygon[j][1];
        const intersect = ((yi > lng) !== (yj > lng)) && (lat < (xj - xi) * (lng - yi) / (yj - yi) + xi);
        if (intersect) inside = !inside;
      }
      return inside;
    };

    // 모든 포인트를 통합 형식으로 변환 (폴리곤 필터링 적용)
    // Nice: grade 사용 (이미 변환됨, 5=많음, 1=적음)
    // Golmok: grade 사용 (5=많음, 1=적음)
    const allPoints = [
      ...niceClusteredPoints
        .filter(p => isPointInPolygon(p.lat, p.lng))
        .map(p => ({
          lat: p.lat,
          lng: p.lng,
          avgGrade: p.grade, // 통일된 grade 사용
          count: p.count
        })),
      ...golmokClusteredPoints
        .filter(p => isPointInPolygon(p.lat, p.lng))
        .map(p => ({
          lat: p.lat,
          lng: p.lng,
          avgGrade: p.grade, // 골목 grade 그대로
          count: 1
        }))
    ];

    //console.log('통합 클러스터링 전 포인트:', allPoints.length);
    //console.log('- 나이스:', niceClusteredPoints.length);
    //console.log('- 골목:', golmokClusteredPoints.length);

    // Haversine 거리 계산
    const haversine = (lat1: number, lng1: number, lat2: number, lng2: number) => {
      const R = 6371000;
      const toRad = (deg: number) => (deg * Math.PI) / 180;
      const dLat = toRad(lat2 - lat1);
      const dLng = toRad(lng2 - lng1);
      const a = Math.sin(dLat / 2) ** 2 +
                Math.cos(toRad(lat1)) * Math.cos(toRad(lat2)) *
                Math.sin(dLng / 2) ** 2;
      const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
      return R * c;
    };

    // 4m 재귀적 클러스터링
    let currentPoints = allPoints;
    const maxIterations = 100;
    let iterationCount = 0;
    let hasChanged = true;

    while (hasChanged && iterationCount < maxIterations) {
      hasChanged = false;
      iterationCount++;

      const newPoints: typeof currentPoints = [];
      const used = new Set<number>();

      for (let i = 0; i < currentPoints.length; i++) {
        if (used.has(i)) continue;

        const cluster = [currentPoints[i]];
        used.add(i);

        for (let j = i + 1; j < currentPoints.length; j++) {
          if (used.has(j)) continue;

          const distance = haversine(
            currentPoints[i].lat, currentPoints[i].lng,
            currentPoints[j].lat, currentPoints[j].lng
          );

          if (distance <= 10) {
            cluster.push(currentPoints[j]);
            used.add(j);
            hasChanged = true;
          }
        }

        if (cluster.length > 1) {
          const totalWeight = cluster.reduce((sum, p) => sum + p.count, 0);
          const avgLat = cluster.reduce((sum, p) => sum + p.lat * p.count, 0) / totalWeight;
          const avgLng = cluster.reduce((sum, p) => sum + p.lng * p.count, 0) / totalWeight;
          const avgGrade = cluster.reduce((sum, p) => sum + p.avgGrade * p.count, 0) / totalWeight;

          newPoints.push({
            lat: avgLat,
            lng: avgLng,
            avgGrade: avgGrade,
            count: totalWeight
          });
        } else {
          newPoints.push(currentPoints[i]);
        }
      }

      currentPoints = newPoints;
    }

    //console.log(`통합 클러스터링 ${iterationCount}회 반복 (4m 기준)`);
    //console.log('최종 클러스터링 점 개수:', currentPoints.length);

    // Grade를 intensity로 변환 (제곱 적용으로 차이 극대화)
    // Note: Golmok 형식(5=높음, 1=낮음)으로 통일됨
    const combinedHeatData: [number, number, number][] = [];
    const pointsWithIntensity = currentPoints.map(p => {
      const normalizedGrade = p.avgGrade / 5; // 0~1 정규화 (5→1.0, 1→0.2)
      const intensity = Math.pow(normalizedGrade, 2); // 제곱: grade 5→1.0, 4→0.64, 3→0.36, 2→0.16, 1→0.04
      combinedHeatData.push([p.lat, p.lng, intensity]);
      return {
        ...p,
        intensity: intensity
      };
    });

    // 최종 클러스터링된 점들을 부모 컴포넌트로 전달
    if (onFinalClusteredPoints) {
      onFinalClusteredPoints(pointsWithIntensity);
    }

    // 줌 레벨에 따른 설정
    let radius: number, blur: number;
    if (zoom <= 15) {
      radius = 3;
      blur = 1;
    } else if (zoom <= 17) {
      radius = 20;
      blur = 18;
    } else {
      radius = 20;
      blur = 18;
    }

    // 히트맵 레이어 생성 (showHeatmap이 true일 때만)
    if (showHeatmap) {
      const heatLayer = (L as any).heatLayer(combinedHeatData, {
        radius: radius,
        blur: blur,
        maxZoom: 19,
        max: 0.6,
        minOpacity: 0.05,
        gradient: {
          0.0: '#4641D8',   // 파랑 (Grade 1)
          0.25: '#47C83E',  // 초록 (Grade 2)
          0.5: '#FAED7D',   // 노랑 (Grade 3)
          0.75: '#F29661',  // 주황 (Grade 4)
          1.0: '#FF0000'    // 빨강 (Grade 5)
        }
      }).addTo(map);

      const onZoomEnd = () => {
        setZoom(Math.floor(map.getZoom()));
      };
      map.on('zoomend', onZoomEnd);

      return () => {
        map.off('zoomend', onZoomEnd);
        map.removeLayer(heatLayer);
      };
    }
  }, [map, niceClusteredPoints, golmokClusteredPoints, analysisParams, zoom, showHeatmap, onFinalClusteredPoints]);

  return null;
}

// 줌 레벨 표시 컴포넌트
function ZoomDisplay() {
  const map = useMap();
  const [zoom, setZoom] = React.useState(Math.floor(map.getZoom()));

  useEffect(() => {
    const onZoomEnd = () => {
      setZoom(Math.floor(map.getZoom()));
    };

    map.on('zoomend', onZoomEnd);

    return () => {
      map.off('zoomend', onZoomEnd);
    };
  }, [map]);

  return (
    <div style={{
      position: 'absolute',
      top: '10px',
      right: '10px',
      zIndex: 1000,
      backgroundColor: 'rgba(255, 255, 255, 0.9)',
      padding: '8px 12px',
      borderRadius: '4px',
      boxShadow: '0 2px 6px rgba(0,0,0,0.3)',
      fontSize: '14px',
      fontWeight: 'bold',
      color: '#333'
    }}>
      Zoom: {zoom}
    </div>
  );
}

// Custom Zoom Control 컴포넌트
function CustomZoomControl() {
  const map = useMap();

  const handleZoomIn = () => {
    map.zoomIn();
  };

  const handleZoomOut = () => {
    map.zoomOut();
  };

  return (
    <div style={{
      position: 'absolute',
      top: '50px',
      right: '10px',
      zIndex: 1000,
      display: 'flex',
      flexDirection: 'row',
      gap: '5px'
    }}>
      <button
        onClick={handleZoomIn}
        style={{
          width: '30px',
          height: '30px',
          backgroundColor: 'rgba(255, 255, 255, 0.9)',
          border: 'none',
          borderRadius: '4px',
          boxShadow: '0 2px 6px rgba(0,0,0,0.3)',
          cursor: 'pointer',
          fontSize: '18px',
          fontWeight: 'bold',
          color: '#333',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center'
        }}
        title="Zoom in"
      >
        +
      </button>
      <button
        onClick={handleZoomOut}
        style={{
          width: '30px',
          height: '30px',
          backgroundColor: 'rgba(255, 255, 255, 0.9)',
          border: 'none',
          borderRadius: '4px',
          boxShadow: '0 2px 6px rgba(0,0,0,0.3)',
          cursor: 'pointer',
          fontSize: '18px',
          fontWeight: 'bold',
          color: '#333',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center'
        }}
        title="Zoom out"
      >
        −
      </button>
    </div>
  );
}

// 폴리곤 완성 핸들러
function PolygonCompleteButton({ onComplete }: { onComplete: () => void }) {
  return (
    <div style={{
      position: 'absolute',
      top: '80px',
      left: '10px',
      zIndex: 1000,
    }}>
      <button
        onClick={onComplete}
        style={{
          padding: '10px 15px',
          backgroundColor: '#4CAF50',
          color: 'white',
          border: 'none',
          borderRadius: '4px',
          cursor: 'pointer',
          fontWeight: 'bold',
        }}
      >
        폴리곤 완성
      </button>
    </div>
  );
}

// 최적화된 히트맵 레이어 (원본 데이터 10m 클러스터링)
function OptimizedHeatmapLayer({
  clusteredPoints
}: {
  clusteredPoints: Array<{ lat: number; lng: number; avgGrade: number; count: number }>;
}) {
  const map = useMap();

  useEffect(() => {
    if (!clusteredPoints || clusteredPoints.length === 0) {
      return;
    }

    //console.log('=== 최적화된 히트맵 생성 ===');
    //console.log(`클러스터 포인트: ${clusteredPoints.length}개`);

    const zoom = map.getZoom();

    // Grade를 intensity로 변환 (제곱 적용)
    const heatData: [number, number, number][] = clusteredPoints.map(p => {
      const normalizedGrade = p.avgGrade / 5; // 0~1 정규화
      const intensity = Math.pow(normalizedGrade, 2); // 제곱으로 대비 강화
      return [p.lat, p.lng, intensity];
    });

    // 기존 히트맵 제거
    if ((window as any).optimizedHeatmapLayer) {
      map.removeLayer((window as any).optimizedHeatmapLayer);
    }

    // 줌 레벨에 따른 설정
    let radius: number, blur: number;
    if (zoom <= 15) {
      radius = 2;
      blur = 3;
    } else if (zoom <= 17) {
      radius = 16;
      blur = 20;
    } else {
      radius = 18;
      blur = 20;
    }

    // 히트맵 레이어 생성
    const heatLayer = (L as any).heatLayer(heatData, {
      radius: radius,
      blur: blur,
      maxZoom: 19,
      max: 0.6,
      minOpacity: 0.05,
      gradient: {
        0.0: '#4641D8',   // 파랑 (Grade 1)
        0.25: '#47C83E',  // 초록 (Grade 2)
        0.5: '#FAED7D',   // 노랑 (Grade 3)
        0.75: '#F29661',  // 주황 (Grade 4)
        1.0: '#FF0000'    // 빨강 (Grade 5)
      }
    }).addTo(map);

    (window as any).optimizedHeatmapLayer = heatLayer;
    //console.log('최적화된 히트맵 생성 완료');

    // 줌 변경 시 히트맵 재생성
    const handleZoomEnd = () => {
      const newZoom = map.getZoom();
      let newRadius: number, newBlur: number;
      if (newZoom <= 15) {
        newRadius = 15;
        newBlur = 18;
      } else if (newZoom <= 17) {
        newRadius = 17;
        newBlur = 19;
      } else {
        newRadius = 18;
        newBlur = 22;
      }

      //console.log(`줌 레벨 변경: ${newZoom}, radius: ${newRadius}, blur: ${newBlur}`);

      // 기존 히트맵 제거
      if ((window as any).optimizedHeatmapLayer) {
        map.removeLayer((window as any).optimizedHeatmapLayer);
      }

      // 새 히트맵 생성
      const newHeatLayer = (L as any).heatLayer(heatData, {
        radius: newRadius,
        blur: newBlur,
        maxZoom: 19,
        max: 0.6,
        minOpacity: 0.05,
        gradient: {
          0.0: '#4641D8',
          0.25: '#47C83E',
          0.5: '#FAED7D',
          0.75: '#F29661',
          1.0: '#FF0000'
        }
      }).addTo(map);

      (window as any).optimizedHeatmapLayer = newHeatLayer;
    };

    map.on('zoomend', handleZoomEnd);

    // 클린업
    return () => {
      if ((window as any).optimizedHeatmapLayer) {
        map.removeLayer((window as any).optimizedHeatmapLayer);
        (window as any).optimizedHeatmapLayer = null;
      }
      map.off('zoomend', handleZoomEnd);
    };
  }, [map, clusteredPoints]);

  return null;
}

// 지도 위치 저장 (복원은 MapContainer 초기값으로)
function MapPositionHandler() {
  const map = useMap();

  useEffect(() => {
    // 지도 이동/줌 변경 시 위치 저장
    const saveMapPosition = () => {
      const center = map.getCenter();
      const zoom = map.getZoom();
      localStorage.setItem('lastMapPosition', JSON.stringify({
        lat: center.lat,
        lng: center.lng,
        zoom: zoom
      }));
    };

    map.on('moveend', saveMapPosition);
    map.on('zoomend', saveMapPosition);

    return () => {
      map.off('moveend', saveMapPosition);
      map.off('zoomend', saveMapPosition);
    };
  }, [map]);

  return null;
}

// 미리보기 시 분석 영역으로 지도 이동
function FitBoundsToAnalysis({ params }: { params: AnalysisParams | null }) {
  const map = useMap();

  useEffect(() => {
    if (!params) return;

    if (params.analysisType === 'radius') {
      // 반경 분석: 중심점으로 이동
      map.setView([params.latitude, params.longitude], 15);
    } else if (params.analysisType === 'polygon' && params.polygonCoords.length >= 3) {
      // 폴리곤 분석: 폴리곤의 bounds로 이동
      const latlngs = params.polygonCoords.map(coord => L.latLng(coord[0], coord[1]));
      const bounds = L.latLngBounds(latlngs);
      map.fitBounds(bounds, { padding: [50, 50] });
    }
  }, [params, map]);

  return null;
}

function AnalysisMapAdvanced() {
  // 저장된 지도 위치 가져오기
  const getInitialMapState = () => {
    const savedPosition = localStorage.getItem('lastMapPosition');
    if (savedPosition) {
      try {
        const { lat, lng, zoom } = JSON.parse(savedPosition);
        return { lat: lat || 37.5665, lng: lng || 126.9780, zoom: zoom || 15 };
      } catch (error) {
        console.error('저장된 지도 위치 파싱 실패:', error);
      }
    }
    return { lat: 37.5665, lng: 126.9780, zoom: 15 };
  };

  const initialMapState = getInitialMapState();

  const [params, setParams] = useState<AnalysisParams>({
    latitude: initialMapState.lat,
    longitude: initialMapState.lng,
    radius: 100,
    gridSize: 100,
    ageGroup: '20',
    gender: 'all',
    analysisType: 'radius',
    polygonCoords: [],
  });

  const [result, setResult] = useState<SpatialResult | null>(null);
  const [loading, setLoading] = useState(false);
  const [qgisAvailable, setQgisAvailable] = useState<boolean | null>(null);
  const [key, setKey] = useState(0); // GeoJSON 강제 리렌더링용
  const [flowpopData, setFlowpopData] = useState<any[]>([]); // 유동인구 데이터

  // 원본 데이터 (클러스터링 전)
  const [rawNiceData, setRawNiceData] = useState<any[]>([]);
  const [rawGolmokData, setRawGolmokData] = useState<any[]>([]);

  // 클러스터링 설정
  const [clusterSettings, setClusterSettings] = useState({
    niceDistance: 3,      // Nice 클러스터링 거리 (m)
    golmokDistance: 3,    // Golmok 클러스터링 거리 (m)
    combinedDistance: 10  // 통합 클러스터링 거리 (m)
  });

  // 클러스터링 결과
  const [niceClusteredResult, setNiceClusteredResult] = useState<any[]>([]);
  const [golmokClusteredResult, setGolmokClusteredResult] = useState<any[]>([]);
  const [combinedClusteredResult, setCombinedClusteredResult] = useState<any[]>([]);

  // Nice 전용 데이터
  const [niceOnlyPoints, setNiceOnlyPoints] = useState<Array<{ lat: number; lng: number; avgGrade: number; count: number; intensity?: number }>>([]); // Nice 전용 포인트
  const [showNiceOnlyHeatmap, setShowNiceOnlyHeatmap] = useState(false); // Nice 전용 히트맵 표시 여부
  const [showNiceOnlyPoints, setShowNiceOnlyPoints] = useState(false); // Nice 전용 점 표시 여부

  // Golmok 전용 데이터
  const [golmokOnlyPoints, setGolmokOnlyPoints] = useState<Array<{ lat: number; lng: number; avgGrade: number; count: number; intensity?: number }>>([]); // Golmok 전용 포인트
  const [showGolmokOnlyHeatmap2, setShowGolmokOnlyHeatmap2] = useState(false); // Golmok 전용 히트맵 표시 여부
  const [showGolmokOnlyPoints, setShowGolmokOnlyPoints] = useState(false); // Golmok 전용 점 표시 여부
  const [showGolmokClusteredPoints, setShowGolmokClusteredPoints] = useState(false); // Golmok 클러스터링된 점 표시 여부 (count > 1인 것만)

  // 지도 캡처를 위한 ref
  const mapContainerRef = useRef<HTMLDivElement>(null);

  // 이미지 파일로 다운로드 (ZIP으로 압축)
  const handleDownloadPDF = async () => {
    if (!activeAnalysis) return;

    try {
      const modalBody = document.querySelector('.modal-body') as HTMLElement;
      if (!modalBody) {
        alert('분석 결과를 찾을 수 없습니다.');
        return;
      }

      console.log('=== 이미지 파일 생성 시작 ===');

      const zip = new JSZip();
      const timestamp = new Date().toISOString().replace(/[:.]/g, '-').slice(0, -5);
      let fileIndex = 1;

      // 1. AI 요약 캡처
      const aiSummary = modalBody.children[0] as HTMLElement;
      if (aiSummary) {
        const canvas = await html2canvas(aiSummary, {
          useCORS: true,
          allowTaint: false,
          backgroundColor: '#ffffff',
          scale: 2
        });

        const imgData = canvas.toDataURL('image/png');
        const base64Data = imgData.split(',')[1];
        zip.file(`${fileIndex}_AI요약.png`, base64Data, { base64: true });
        console.log(`${fileIndex}. AI 요약 저장 완료`);
        fileIndex++;
      }

      // 2. 골목상권 분석 결과 내의 섹션들 캡처
      const golmokContainer = modalBody.children[1] as HTMLElement;
      if (golmokContainer) {
        console.log('골목 컨테이너 자식 수:', golmokContainer.children.length);

        // h3 제목 다음에 있는 div들을 직접 찾기
        // children[0] = h3 제목 (📊 상권 분석 결과)
        // children[1] = 인구 분석 섹션
        // children[2] = 매출 분석 섹션

        const sections = [];
        for (let i = 1; i < golmokContainer.children.length; i++) {
          const child = golmokContainer.children[i] as HTMLElement;
          // marginBottom이 있는 div만 선택 (섹션들)
          if (child.tagName === 'DIV' && child.style.marginBottom) {
            sections.push(child);
          }
        }

        console.log(`총 ${sections.length}개 섹션 발견`);

        // 각 섹션 캡처 (인구 분석, 매출 분석)
        for (let i = 0; i < sections.length; i++) {
          const section = sections[i];

          // 섹션 제목 추출 (👥 인구 분석, 💰 매출 분석)
          const titleElement = section.querySelector('h4');
          const title = titleElement?.textContent || `섹션_${i + 1}`;

          console.log(`${fileIndex}번 캡처 시작: ${title}`);

          const canvas = await html2canvas(section, {
            useCORS: true,
            allowTaint: false,
            backgroundColor: '#ffffff',
            scale: 2
          });

          const imgData = canvas.toDataURL('image/png');
          const base64Data = imgData.split(',')[1];
          const fileName = `${fileIndex}_${title.replace(/[\/\\?%*:|"<>]/g, '')}.png`;
          zip.file(fileName, base64Data, { base64: true });
          console.log(`${fileIndex}. ${title} 저장 완료`);
          fileIndex++;
        }
      }

      // ZIP 파일 생성 및 다운로드
      const zipBlob = await zip.generateAsync({ type: 'blob' });
      const link = document.createElement('a');
      link.href = URL.createObjectURL(zipBlob);
      link.download = `상권분석_${timestamp}.zip`;
      link.click();

      console.log(`=== 총 ${fileIndex - 1}개 이미지 파일을 ZIP으로 압축 완료 ===`);

    } catch (error) {
      console.error('이미지 생성 오류:', error);
    }
  };

  // 컴포넌트 로드 시 저장된 데이터 자동 로드 (주석처리 - 실시간 API 사용)
  // useEffect(() => {
  //   const loadSavedData = async () => {
  //     try {
  //       //console.log('\n=== saved_data 자동 로드 ===');

  //       // 백엔드 API로부터 데이터 불러오기 (좌표 변환 포함)
  //       const response = await axios.get('http://localhost:5000/api/flowpop/saved');

  //       if (response.data.status !== 'success') {
  //         console.error('saved_data 로드 실패');
  //         return;
  //       }

  //       const { nice, golmok } = response.data.data;

  //       // Nice 데이터 추출 (이미 grade 변환됨)
  //       const niceFlowpop = nice.response.data.data.flowpopData;
  //       //console.log(`나이스 원본: ${niceFlowpop.length}개`);

  //       // Nice 포인트를 그대로 사용
  //       const nicePoints = niceFlowpop.map((p: any) => ({
  //         lat: p.lat,
  //         lng: p.lng,
  //         grade: p.grade,
  //         count: 1
  //       }));

  //       // Golmok 데이터 (이미 좌표 변환됨)
  //       //console.log(`골목 원본: ${golmok.length}개 LineString`);

  //       // Golmok LineString을 7m 샘플링
  //       const golmokSampledPoints: any[] = [];
  //       golmok.forEach((line: any) => {
  //         const points = sampleLineToPoints(line.coords, 7, line.grade);
  //         golmokSampledPoints.push(...points);
  //       });

  //       const golmokPoints = golmokSampledPoints.map((p: any) => ({
  //         lat: p.lat,
  //         lng: p.lng,
  //         grade: p.grade,
  //         count: 1
  //       }));

  //       //console.log(`총 포인트: ${nicePoints.length + golmokPoints.length}개 (Nice: ${nicePoints.length}, Golmok: ${golmokPoints.length})`);

  //       // 원본 데이터 저장
  //       setRawNiceData(nicePoints);
  //       setRawGolmokData(golmokPoints);

  //       // Nice 전용 데이터 저장 (클러스터링 없음)
  //       setNiceOnlyPoints(nicePoints.map((p: any) => ({
  //         ...p,
  //         avgGrade: p.grade,
  //         intensity: p.grade  // 기본 점지도는 grade 그대로
  //       })));

  //       // Golmok 전용 데이터 저장 (클러스터링 없음)
  //       setGolmokOnlyPoints(golmokPoints.map((p: any) => ({
  //         ...p,
  //         avgGrade: p.grade,
  //         intensity: p.grade  // 기본 점지도는 grade 그대로
  //       })));

  //       //console.log('=== saved_data 자동 로드 완료 ===\n');

  //     } catch (error) {
  //       console.error('saved_data 자동 로드 오류:', error);
  //     }
  //   };

  //   loadSavedData();
  // }, []);

  // 클러스터링 설정이 변경될 때마다 재클러스터링
  useEffect(() => {
    if (rawNiceData.length === 0 && rawGolmokData.length === 0) return;

    //console.log('\n=== 클러스터링 시작 ===');
    //console.log('설정:', clusterSettings);

    // 1. Nice 클러스터링 (중복 없을 때까지)
    let niceResult = rawNiceData;
    let niceIter = 0;

    if (clusterSettings.niceDistance === 0) {
      // 거리가 0이면 클러스터링 없이 원본 데이터에 intensity만 추가
      niceResult = rawNiceData.map(p => ({
        ...p,
        avgGrade: p.grade,
        intensity: p.grade  // 기본 점지도는 grade 그대로
      }));
      //console.log(`Nice 클러스터링 건너뜀 (거리=0): ${rawNiceData.length}개`);
    } else {
      while (true) {
        const niceClusters = performClustering(niceResult, clusterSettings.niceDistance);
        const newNiceResult = niceClusters.map(cluster => {
          const totalCount = cluster.points.reduce((sum: number, p: any) => sum + (p.count || 1), 0);
          const gradeSum = cluster.grades.reduce((sum: number, g: number) => sum + g, 0);
          const avgGrade = gradeSum / cluster.grades.length;
          const intensity = gradeSum / cluster.grades.length;  // grade의 합 / grade의 개수

          return {
            lat: cluster.points.reduce((sum: number, p: any) => sum + p.lat, 0) / cluster.points.length,
            lng: cluster.points.reduce((sum: number, p: any) => sum + p.lng, 0) / cluster.points.length,
            grade: avgGrade,
            count: totalCount,
            avgGrade: avgGrade,
            intensity: intensity
          };
        });
        niceIter++;
        //console.log(`Nice 클러스터링 반복 ${niceIter}: ${niceResult.length}개 → ${newNiceResult.length}개`);
        if (newNiceResult.length === niceResult.length) break; // 더 이상 변화 없으면 종료
        niceResult = newNiceResult;
      }
      //console.log(`Nice 클러스터링 완료: ${rawNiceData.length}개 → ${niceResult.length}개 (${niceIter}회 반복)`);
    }
    setNiceClusteredResult(niceResult);
    setNiceOnlyPoints(niceResult); // Nice 전용 레이어에도 반영

    // 2. Golmok 클러스터링 (중복 없을 때까지)
    let golmokResult = rawGolmokData;
    let golmokIter = 0;

    if (clusterSettings.golmokDistance === 0) {
      // 거리가 0이면 클러스터링 없이 원본 데이터에 intensity만 추가
      golmokResult = rawGolmokData.map(p => ({
        ...p,
        avgGrade: p.grade,
        intensity: p.grade  // 기본 점지도는 grade 그대로
      }));
      //console.log(`Golmok 클러스터링 건너뜀 (거리=0): ${rawGolmokData.length}개`);
    } else {
      while (true) {
        const golmokClusters = performClustering(golmokResult, clusterSettings.golmokDistance);
        const newGolmokResult = golmokClusters.map(cluster => {
          const totalCount = cluster.points.reduce((sum: number, p: any) => sum + (p.count || 1), 0);
          const gradeSum = cluster.grades.reduce((sum: number, g: number) => sum + g, 0);
          const avgGrade = gradeSum / cluster.grades.length;
          const intensity = gradeSum / cluster.grades.length;  // grade의 합 / grade의 개수

          return {
            lat: cluster.points.reduce((sum: number, p: any) => sum + p.lat, 0) / cluster.points.length,
            lng: cluster.points.reduce((sum: number, p: any) => sum + p.lng, 0) / cluster.points.length,
            grade: avgGrade,
            count: totalCount,
            avgGrade: avgGrade,
            intensity: intensity
          };
        });
        golmokIter++;
        //console.log(`Golmok 클러스터링 반복 ${golmokIter}: ${golmokResult.length}개 → ${newGolmokResult.length}개`);
        if (newGolmokResult.length === golmokResult.length) break; // 더 이상 변화 없으면 종료
        golmokResult = newGolmokResult;
      }
      //console.log(`Golmok 클러스터링 완료: ${rawGolmokData.length}개 → ${golmokResult.length}개 (${golmokIter}회 반복)`);
    }
    setGolmokClusteredResult(golmokResult);
    setGolmokOnlyPoints(golmokResult); // Golmok 전용 레이어에도 반영

    // 3. 통합 클러스터링 (중복 없을 때까지)
    let combined = [...niceResult, ...golmokResult];
    let combinedIter = 0;
    while (true) {
      const combinedClusters = performClustering(combined, clusterSettings.combinedDistance);
      const newCombined = combinedClusters.map(cluster => ({
        lat: cluster.points.reduce((sum: number, p: any) => sum + p.lat, 0) / cluster.points.length,
        lng: cluster.points.reduce((sum: number, p: any) => sum + p.lng, 0) / cluster.points.length,
        grade: cluster.grades.reduce((sum: number, g: number) => sum + g, 0) / cluster.grades.length,
        count: cluster.points.reduce((sum: number, p: any) => sum + p.count, 0),
        avgGrade: cluster.grades.reduce((sum: number, g: number) => sum + g, 0) / cluster.grades.length
      }));
      combinedIter++;
      //console.log(`통합 클러스터링 반복 ${combinedIter}: ${combined.length}개 → ${newCombined.length}개`);
      if (newCombined.length === combined.length) break; // 더 이상 변화 없으면 종료
      combined = newCombined;
    }
    setCombinedClusteredResult(combined);
    setFinalClusteredPoints(combined);
    //console.log(`통합 클러스터링 완료: ${combined.length}개`);
    //console.log('=== 클러스터링 완료 ===\n');

  }, [rawNiceData, rawGolmokData, clusterSettings]);

  // 클러스터링 함수
  const performClustering = (points: any[], distance: number) => {
    const clusters = [];
    const used = new Set<number>();

    for (let i = 0; i < points.length; i++) {
      if (used.has(i)) continue;

      const cluster = {
        points: [points[i]],
        grades: [points[i].grade]
      };
      used.add(i);

      for (let j = i + 1; j < points.length; j++) {
        if (used.has(j)) continue;

        const dist = haversineDistance(points[i].lat, points[i].lng, points[j].lat, points[j].lng);
        if (dist <= distance) {
          cluster.points.push(points[j]);
          cluster.grades.push(points[j].grade);
          used.add(j);
        }
      }

      clusters.push(cluster);
    }

    return clusters;
  };

  const [showFlowpop, setShowFlowpop] = useState(true); // 유동인구 표시 여부
  const [selectedRadius, setSelectedRadius] = useState<number>(100); // 선택된 반경값
  const [golmokFlowpopData, setGolmokFlowpopData] = useState<any[]>([]); // 골목상권 유동인구 데이터 (지역 1)
  const [golmokFlowpopData2, setGolmokFlowpopData2] = useState<any[]>([]); // 골목상권 유동인구 데이터 (지역 2)
  const [showGolmokFlowpop, setShowGolmokFlowpop] = useState(false); // 골목상권 유동인구 표시 여부
  const [showGolmokFlowpop2, setShowGolmokFlowpop2] = useState(false); // 골목상권 유동인구 표시 여부 (지역 2)
  const [flowpopValueType, setFlowpopValueType] = useState<'cost' | 'acost'>('cost'); // 표시할 값 타입
  const [integratedFlowpopData, setIntegratedFlowpopData] = useState<any[]>([]); // 통합 유동인구 데이터
  const [showIntegratedFlowpop, setShowIntegratedFlowpop] = useState(false); // 통합 유동인구 표시 여부
  const [selectedRoadNicePoints, setSelectedRoadNicePoints] = useState<any[]>([]); // 선택된 도로의 NICE 포인트
  const [niceFlowpopData, setNiceFlowpopData] = useState<any[]>([]); // 나이스 유동인구 원본 데이터
  const [showNiceFlowpop, setShowNiceFlowpop] = useState(false); // 나이스 포인트 표시 여부
  const [showNiceHeatmap, setShowNiceHeatmap] = useState(false); // 나이스 히트맵 표시 여부
  const [niceClusteredPoints, setNiceClusteredPoints] = useState<Array<{ lat: number; lng: number; grade: number; count: number; intensity: number }>>([]); // 나이스 클러스터링된 점들
  const [showNiceClusteredPoints, setShowNiceClusteredPoints] = useState(false); // 나이스 클러스터링 점 표시 여부
  const [showCombinedClusteredPoints, setShowCombinedClusteredPoints] = useState(false); // 통합 클러스터 포인트 표시 여부 (재클러스터링 전)
  const [showCombinedClusteredHeatmap, setShowCombinedClusteredHeatmap] = useState(false); // 통합 클러스터 히트맵 표시 여부
  const [finalClusteredPoints, setFinalClusteredPoints] = useState<Array<{ lat: number; lng: number; avgGrade: number; count: number; intensity?: number; niceCount?: number; golmokCount?: number }>>([]); // 최종 클러스터링된 점들
  const [showFinalClusteredPoints, setShowFinalClusteredPoints] = useState(false); // 최종 클러스터 포인트 표시 여부
  const [showCombinedHeatmap, setShowCombinedHeatmap] = useState(false); // 통합 히트맵 표시 여부
  const [showGolmokOnlyHeatmap, setShowGolmokOnlyHeatmap] = useState(false); // 골목 전용 히트맵 표시 여부
  const [showClusteredPoints, setShowClusteredPoints] = useState(false); // 합쳐진 점 표시 여부
  const [showSinglePoints, setShowSinglePoints] = useState(false); // 혼자 있는 점 표시 여부
  const [showAllHeatmapPoints, setShowAllHeatmapPoints] = useState(false); // 모든 히트맵 포인트 표시 여부
  const [showGolmokSampledPoints, setShowGolmokSampledPoints] = useState(false); // 골목 샘플링 포인트 표시 여부 (사용 안함)
  const [showRawHeatmapPoints, setShowRawHeatmapPoints] = useState(false); // 히트맵 원본 점들 표시 여부
  const [showGolmokHeatmapPoints, setShowGolmokHeatmapPoints] = useState(false); // 골목 히트맵 직전 점들 표시 여부
  const [rawHeatmapPoints, setRawHeatmapPoints] = useState<Array<[number, number, number]>>([]); // 히트맵에 들어간 원본 점들
  const [golmokSampledPoints, setGolmokSampledPoints] = useState<Array<{ lat: number; lng: number; grade: number }>>([]); // 골목 샘플링된 점들
  const [golmokHeatmapPoints, setGolmokHeatmapPoints] = useState<Array<{ lat: number; lng: number; grade: number; intensity: number }>>([]); // 골목 히트맵 직전 점들
  const [clusteredHeatmapPoints, setClusteredHeatmapPoints] = useState<Array<{ lat: number; lng: number; avgGrade: number; isClustered: boolean; count: number; clusterLevel: number }>>([]); // 클러스터링된 점들
  const [isPolygonLocked, setIsPolygonLocked] = useState(false); // 폴리곤 잠금 상태
  const [isPolygonDrawingEnabled, setIsPolygonDrawingEnabled] = useState(false); // 폴리곤 그리기 활성화 상태
  const [showAnalysisArea, setShowAnalysisArea] = useState(false); // 분석 영역(원/폴리곤) 표시 여부
  const [analysisHistory, setAnalysisHistory] = useState<Array<{
    id: string;
    timestamp: number;
    params: AnalysisParams;
    result: SpatialResult;
    isMinimized: boolean;
    title: string;
    mapSnapshot?: string; // 지도 스냅샷
  }>>([]); // 분석 결과 히스토리
  const [activeAnalysisId, setActiveAnalysisId] = useState<string | null>(null); // 현재 활성화된 분석 ID
  const [previewAnalysisId, setPreviewAnalysisId] = useState<string | null>(null); // 미리보기 중인 분석 ID

  // 서버 기능 확인 및 골목 샘플 데이터 로드
  useEffect(() => {
    // 서버 기능 확인
    const API_BASE = `${getApiUrl()}/api/commercial`;
    axios.get(`${API_BASE}/health`, { headers: getAuthHeaders() })
      .then(res => {
        setQgisAvailable(false); // QGIS 기능은 사용하지 않음
      })
      .catch(() => setQgisAvailable(false));
  }, []);

  const handleMapClick = (lat: number, lng: number) => {
    // 분석 영역 표시가 꺼져있으면 클릭 무시
    if (!showAnalysisArea) {
      return;
    }

    if (params.analysisType === 'polygon') {
      // 폴리곤이 잠겨있거나 그리기가 활성화되지 않았으면 클릭 무시
      if (isPolygonLocked || !isPolygonDrawingEnabled) {
        return;
      }

      // 폴리곤 모드: 클릭한 지점을 폴리곤 좌표에 바로 추가
      const newCoords: [number, number][] = [...params.polygonCoords, [lat, lng]];

      // 3개 이상일 때만 면적 검증
      if (newCoords.length >= 3) {
        const area = calculatePolygonArea(newCoords);
        const maxArea = 3140000; // 3.14 km² = 3,140,000 m²

        if (area > maxArea) {
          const areaKm2 = (area / 1000000).toFixed(2);
          if (window.confirm(`폴리곤 면적이 너무 넓습니다.\n현재 면적: ${areaKm2} km²\n최대 허용 면적: 3.14 km²\n\n확인을 누르면 폴리곤이 초기화됩니다.`)) {
            // 확인 누르면 초기화
            setParams(prev => ({
              ...prev,
              polygonCoords: []
            }));
          }
          return;
        }
      }

      // 면적이 괜찮으면 좌표 추가
      setParams(prev => ({
        ...prev,
        polygonCoords: newCoords
      }));
    } else if (params.analysisType === 'radius') {
      // 반경 모드: 중심점 설정
      setParams(prev => ({
        ...prev,
        latitude: lat,
        longitude: lng,
      }));
    }
  };

  // 폴리곤 면적 계산 (제곱미터)
  const calculatePolygonArea = (coords: [number, number][]): number => {
    if (coords.length < 3) return 0;

    // Haversine 기반 면적 계산
    const toRadians = (deg: number) => (deg * Math.PI) / 180;
    const R = 6371000; // 지구 반경 (미터)

    let area = 0;
    const n = coords.length;

    for (let i = 0; i < n; i++) {
      const [lat1, lng1] = coords[i];
      const [lat2, lng2] = coords[(i + 1) % n];

      const phi1 = toRadians(lat1);
      const phi2 = toRadians(lat2);
      const lambda1 = toRadians(lng1);
      const lambda2 = toRadians(lng2);

      area += (lambda2 - lambda1) * (2 + Math.sin(phi1) + Math.sin(phi2));
    }

    area = Math.abs((area * R * R) / 2);
    return area;
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();

    // 폴리곤 검증 (최소 점 개수만 체크, 면적은 클릭 시 이미 체크됨)
    if (params.analysisType === 'polygon') {
      if (params.polygonCoords.length < 3) {
        alert('폴리곤은 최소 3개의 점이 필요합니다.');
        return;
      }
      // 폴리곤 잠금
      setIsPolygonLocked(true);
    }

    setLoading(true);
    setResult(null);

    try {
      // 새로운 효율적인 클러스터링 방식 사용 (이미 API 호출 포함)
      const analysisResult = await performRawDataClustering(params);

      if (analysisResult) {
        //console.log('=== 분석 결과 ===');
        //console.log('전체 데이터:', analysisResult);
        //console.log('유동인구 데이터:', analysisResult.floating_population);

        setResult(analysisResult);

        // 유동인구 데이터는 새로운 클러스터링 방식에서 처리됨

        // 지도 스냅샷 캡처
        let mapSnapshot: string | undefined;
        if (mapContainerRef.current) {
          const leafletContainer = mapContainerRef.current.querySelector('.leaflet-container') as HTMLElement;
          if (leafletContainer) {
            try {
              const canvas = await html2canvas(leafletContainer, {
                useCORS: true,
                allowTaint: false,
                backgroundColor: '#ffffff',
                scale: 1,
                logging: false
              });
              mapSnapshot = canvas.toDataURL('image/png');
            } catch (err) {
              console.error('지도 스냅샷 캡처 실패:', err);
            }
          }
        }

        // 분석 결과를 히스토리에 추가
        const analysisId = `analysis-${Date.now()}`;
        const analysisTitle = params.analysisType === 'radius'
          ? `반경 ${params.radius}m`
          : `다각형`;

        setAnalysisHistory(prev => [...prev, {
          id: analysisId,
          timestamp: Date.now(),
          params: { ...params },
          result: analysisResult,
          isMinimized: false,
          title: analysisTitle,
          mapSnapshot: mapSnapshot
        }]);
        setActiveAnalysisId(analysisId);

        setKey(prev => prev + 1); // 강제 리렌더링
      } else {
        alert('분석 결과를 가져오지 못했습니다.');
      }
    } catch (error: any) {
      console.error('오류:', error);
      alert(error.response?.data?.message || '분석 중 오류가 발생했습니다.');
    } finally {
      setLoading(false);
    }
  };

  // Haversine 거리 계산 함수 (미터 단위)
  const haversineDistance = (lat1: number, lng1: number, lat2: number, lng2: number): number => {
    const R = 6371000; // 지구 반지름 (미터)
    const toRad = (deg: number) => (deg * Math.PI) / 180;
    const dLat = toRad(lat2 - lat1);
    const dLng = toRad(lng2 - lng1);
    const a = Math.sin(dLat / 2) ** 2 +
              Math.cos(toRad(lat1)) * Math.cos(toRad(lat2)) *
              Math.sin(dLng / 2) ** 2;
    const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    return R * c;
  };


  // saved_data 폴더의 실제 데이터 로드
  const performRawDataClustering = async (params: AnalysisParams) => {
    try {
      //console.log('\n=== 통합 유동인구 데이터 로드 ===');
      setLoading(true);

      // API 페이로드 구성
      let apiPayload: any = {
        analysisType: params.analysisType,
      };

      if (params.analysisType === 'radius') {
        apiPayload.latitude = params.latitude;
        apiPayload.longitude = params.longitude;
        apiPayload.radius = params.radius;
      } else if (params.analysisType === 'polygon') {
        apiPayload.polygon = params.polygonCoords;
      }

      // 백엔드 API로부터 실시간 데이터 불러오기 (좌표 변환 포함)
      const API_BASE = `${getApiUrl()}/api/commercial`;
      const endpoint = params.analysisType === 'radius'
        ? `${API_BASE}/analyze`
        : `${API_BASE}/analyze-polygon`;
      const response = await axios.post(endpoint, apiPayload, { headers: getAuthHeaders() });

      //console.log('=== 클라이언트: 서버 응답 받음 ===');
      //console.log('response.data:', response.data);
      //console.log('response.data.status:', response.data.status);
      //console.log('response.data.data:', response.data.data);

      if (response.data.status !== 'success') {
        console.error('통합 유동인구 데이터 로드 실패');
        setLoading(false);
        return;
      }

      const { nice, golmok } = response.data.data;
      //console.log('nice:', nice);
      //console.log('golmok:', golmok);

      // Nice 데이터 추출
      // nice는 nice_result 객체이므로 nice.data.data.flowpopData로 접근
      const niceFlowpop = nice.data.data.flowpopData;
      //console.log(`나이스 원본: ${niceFlowpop.length}개`);

      // 처음 10개 포인트의 grade 확인
      //console.log('=== 나이스 포인트 원본 grade 샘플 (처음 10개) ===');
      niceFlowpop.slice(0, 10).forEach((p: any, idx: number) => {
        //console.log(`${idx}: grade=${p.grade}, flowpopCount=${p.flowpopCount}`);
      });

      // 반경 체크 (디버깅용)
      if (params.analysisType === 'radius' && niceFlowpop.length > 0) {
        const checkPoint = niceFlowpop[0];
        const dist = haversineDistance(params.latitude, params.longitude, checkPoint.lat, checkPoint.lng);
        //console.log(`첫 번째 포인트 거리: ${dist.toFixed(2)}m (설정 반경: ${params.radius}m)`);
      }

      // Nice 포인트 (서버에서 이미 grade 변환됨: 5=많음, 1=적음)
      const nicePoints = niceFlowpop.map((p: any) => ({
        lat: p.lat,
        lng: p.lng,
        grade: p.grade,  // 서버에서 이미 변환됨
        count: 1
      }));

      //console.log('=== 나이스 포인트 최종 grade 샘플 (처음 10개) ===');
      nicePoints.slice(0, 10).forEach((p: any, idx: number) => {
        //console.log(`${idx}: grade=${p.grade}`);
      });

      // Golmok 데이터 추출
      // golmok은 {status: 'success', data: [{coords: [[lat, lng], ...], grade: 5, ...}, ...]} 구조
      //console.log('골목 전체 객체:', golmok);
      //console.log('골목 status:', golmok?.status);
      //console.log('골목 data:', golmok?.data);

      const golmokData = golmok?.data || [];
      const golmokLineStrings = Array.isArray(golmokData) ? golmokData : [];
      //console.log(`골목 원본: ${golmokLineStrings.length}개 LineString`);

      if (golmokLineStrings.length > 0) {
        //console.log('첫 번째 골목 LineString:', golmokLineStrings[0]);
        //console.log('첫 번째 골목 coords 길이:', golmokLineStrings[0]?.coords?.length);
        //console.log('첫 번째 골목 grade:', golmokLineStrings[0]?.grade);
      }

      // Golmok LineString을 7m 샘플링
      const golmokSampledPoints: any[] = [];
      golmokLineStrings.forEach((line: any) => {
        const points = sampleLineToPoints(line.coords, 7, line.grade);
        golmokSampledPoints.push(...points);
      });

      //console.log(`골목 샘플링: ${golmokSampledPoints.length}개`);

      // 반경/폴리곤으로 필터링
      let filteredGolmokPoints: any[] = [];
      if (params.analysisType === 'radius') {
        // 반경 필터링
        filteredGolmokPoints = golmokSampledPoints.filter((p: any) => {
          const dist = haversineDistance(params.latitude, params.longitude, p.lat, p.lng);
          return dist <= params.radius;
        });
        //console.log(`골목 반경 필터링: ${golmokSampledPoints.length}개 → ${filteredGolmokPoints.length}개`);
      } else if (params.analysisType === 'polygon') {
        // 폴리곤 필터링
        const isPointInPolygon = (lat: number, lng: number, polygon: [number, number][]): boolean => {
          let inside = false;
          for (let i = 0, j = polygon.length - 1; i < polygon.length; j = i++) {
            const xi = polygon[i][0], yi = polygon[i][1];
            const xj = polygon[j][0], yj = polygon[j][1];
            const intersect = ((yi > lng) !== (yj > lng)) && (lat < (xj - xi) * (lng - yi) / (yj - yi) + xi);
            if (intersect) inside = !inside;
          }
          return inside;
        };

        filteredGolmokPoints = golmokSampledPoints.filter((p: any) =>
          isPointInPolygon(p.lat, p.lng, params.polygonCoords)
        );
        //console.log(`골목 폴리곤 필터링: ${golmokSampledPoints.length}개 → ${filteredGolmokPoints.length}개`);
      } else {
        filteredGolmokPoints = golmokSampledPoints;
      }

      const golmokPoints = filteredGolmokPoints.map((p: any) => ({
        lat: p.lat,
        lng: p.lng,
        grade: p.grade,
        count: 1
      }));

      //console.log(`총 포인트: ${nicePoints.length + golmokPoints.length}개 (Nice: ${nicePoints.length}, Golmok: ${golmokPoints.length})`);

      // 원본 데이터 저장 (클러스터링 트리거용)
      setRawNiceData(nicePoints);
      setRawGolmokData(golmokPoints);

      // Nice 전용 데이터 저장 (클러스터링 없음)
      setNiceOnlyPoints(nicePoints.map((p: any) => ({
        ...p,
        avgGrade: p.grade,
        intensity: p.grade  // 기본 점지도는 grade 그대로
      })));

      // Golmok 전용 데이터 저장 (클러스터링 없음)
      setGolmokOnlyPoints(golmokPoints.map((p: any) => ({
        ...p,
        avgGrade: p.grade,
        intensity: p.grade  // 기본 점지도는 grade 그대로
      })));

      //console.log('=== 통합 유동인구 데이터 로드 완료 ===\n');
      setLoading(false);

      // 전체 분석 결과 반환
      return response.data.data;

    } catch (error) {
      console.error('통합 유동인구 데이터 로드 오류:', error);
      setLoading(false);
      return null;
    }
  };


  // 격자 스타일 함수
  const gridStyle = (feature: any) => {
    const color = feature.properties.color || '#cccccc';
    return {
      fillColor: color,
      weight: 1,
      opacity: 0.8,
      color: '#666',
      fillOpacity: 0.6,
    };
  };

  // 버퍼 스타일
  const bufferStyle = {
    fillColor: 'blue',
    weight: 2,
    opacity: 0.8,
    color: 'blue',
    fillOpacity: 0.1,
  };

  // 포인트 스타일
  const pointToLayer = (feature: any, latlng: L.LatLng) => {
    return L.circleMarker(latlng, {
      radius: 4,
      fillColor: '#ff7800',
      color: '#000',
      weight: 1,
      opacity: 1,
      fillOpacity: 0.8,
    });
  };

  // 유동인구 등급별 색상 (골목상권: 5가 가장 높음, 1이 가장 낮음)
  const getGolmokFlowpopColor = (grade: number) => {
    switch (grade) {
      case 5: return '#FF0000';     // 빨강 - 가장 많음
      case 4: return '#F29661';     // 주황
      case 3: return '#FAED7D';     // 노랑
      case 2: return '#47C83E';     // 초록
      case 1: return '#4641D8';     // 파랑 - 가장 적음
      default: return '#cccccc';
    }
  };

  // 기존 유동인구 등급별 색상 (1이 가장 높음)
  const getFlowpopColor = (grade: number) => {
    switch (grade) {
      case 1: return '#d73027'; // 가장 높음 - 빨강
      case 2: return '#fc8d59'; // 높음 - 주황
      case 3: return '#fee08b'; // 보통 - 노랑
      case 4: return '#91cf60'; // 낮음 - 초록
      default: return '#cccccc';
    }
  };

  // 점이 폴리곤 내부에 있는지 확인
  const isPointInPolygon = (lat: number, lng: number, polygon: [number, number][]): boolean => {
    if (polygon.length < 3) return false;

    let inside = false;
    for (let i = 0, j = polygon.length - 1; i < polygon.length; j = i++) {
      const xi = polygon[i][1], yi = polygon[i][0]; // [lat, lng] -> lng, lat
      const xj = polygon[j][1], yj = polygon[j][0];

      const intersect = ((yi > lat) !== (yj > lat))
        && (lng < (xj - xi) * (lat - yi) / (yj - yi) + xi);
      if (intersect) inside = !inside;
    }

    return inside;
  };

  // 점이 원 내부에 있는지 확인 (Haversine)
  const isPointInRadius = (lat: number, lng: number, centerLat: number, centerLng: number, radiusMeters: number): boolean => {
    const toRadians = (deg: number) => (deg * Math.PI) / 180;
    const R = 6371000; // 지구 반경 (미터)

    const dLat = toRadians(lat - centerLat);
    const dLng = toRadians(lng - centerLng);

    const a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
              Math.cos(toRadians(centerLat)) * Math.cos(toRadians(lat)) *
              Math.sin(dLng / 2) * Math.sin(dLng / 2);

    const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    const distance = R * c;

    return distance <= radiusMeters;
  };

  // 히스토리에서 분석 결과 복원
  const handleRestoreAnalysis = (id: string) => {
    const analysis = analysisHistory.find(a => a.id === id);
    if (analysis) {
      setResult(analysis.result);
      setActiveAnalysisId(id);
      setAnalysisHistory(prev => prev.map(a =>
        a.id === id ? { ...a, isMinimized: false } : a
      ));

      // 유동인구 데이터 복원
      const floatingPop = analysis.result.floating_population;
      if (floatingPop && floatingPop.data && floatingPop.data.flowpopData) {
        setFlowpopData(floatingPop.data.flowpopData);
      } else if (floatingPop && floatingPop.flowpopData) {
        setFlowpopData(floatingPop.flowpopData);
      } else {
        setFlowpopData([]);
      }
    }
  };

  // 분석 결과 최소화 및 지도 초기화
  const handleMinimizeAnalysis = (id: string) => {
    setAnalysisHistory(prev => prev.map(a =>
      a.id === id ? { ...a, isMinimized: true } : a
    ));
    if (activeAnalysisId === id) {
      setActiveAnalysisId(null);
      setResult(null);
      // 지도 초기화
      setFlowpopData([]);
      setIsPolygonLocked(false);
      setParams(prev => ({
        ...prev,
        polygonCoords: []
      }));
    }
  };

  // 분석 결과 삭제
  const handleDeleteAnalysis = (id: string) => {
    setAnalysisHistory(prev => prev.filter(a => a.id !== id));
    if (activeAnalysisId === id) {
      setActiveAnalysisId(null);
      setResult(null);
    }
  };

  // 현재 활성화된 분석
  const activeAnalysis = analysisHistory.find(a => a.id === activeAnalysisId);

  // 미리보기 중인 분석
  const previewAnalysis = analysisHistory.find(a => a.id === previewAnalysisId);

  // 유동인구 데이터 필터링
  const filteredFlowpopData = flowpopData.filter(item => {
    // 미리보기 중이면 미리보기 분석의 영역으로 필터링
    if (previewAnalysis) {
      if (previewAnalysis.params.analysisType === 'radius') {
        return isPointInRadius(item.lat, item.lng, previewAnalysis.params.latitude, previewAnalysis.params.longitude, previewAnalysis.params.radius);
      } else if (previewAnalysis.params.analysisType === 'polygon' && previewAnalysis.params.polygonCoords.length >= 3) {
        return isPointInPolygon(item.lat, item.lng, previewAnalysis.params.polygonCoords);
      }
    }

    // 현재 분석 중인 영역으로 필터링
    if (params.analysisType === 'radius') {
      return isPointInRadius(item.lat, item.lng, params.latitude, params.longitude, params.radius);
    } else if (params.analysisType === 'polygon' && params.polygonCoords.length >= 3) {
      return isPointInPolygon(item.lat, item.lng, params.polygonCoords);
    }
    return true; // 분석 범위가 설정되지 않으면 모두 표시
  });

  // 미리보기 카드 토글
  const handlePreviewToggle = (id: string) => {
    if (previewAnalysisId === id) {
      // 같은 카드를 다시 클릭하면 미리보기 해제
      setPreviewAnalysisId(null);
      setFlowpopData([]); // 유동인구 데이터 초기화
    } else {
      // 다른 카드 클릭하면 미리보기 활성화
      setPreviewAnalysisId(id);

      // 유동인구 데이터 복원
      const analysis = analysisHistory.find(a => a.id === id);
      if (analysis) {
        const floatingPop = analysis.result.floating_population;
        if (floatingPop && floatingPop.data && floatingPop.data.flowpopData) {
          setFlowpopData(floatingPop.data.flowpopData);
        } else if (floatingPop && floatingPop.flowpopData) {
          setFlowpopData(floatingPop.flowpopData);
        } else {
          setFlowpopData([]);
        }
      }
    }
  };

  return (
    <div className="analysis-container">
      <div className="map-section" ref={mapContainerRef}>
        {/* 클러스터링 설정 패널 */}
        <div style={{
          position: 'absolute',
          top: '530px',
          left: '10px',
          zIndex: 1000,
          backgroundColor: 'white',
          padding: '15px',
          borderRadius: '8px',
          boxShadow: '0 2px 8px rgba(0,0,0,0.15)',
          minWidth: '250px'
        }}>
          <div style={{ fontWeight: 'bold', marginBottom: '12px', fontSize: '14px' }}>
            클러스터링 설정
          </div>

          <div style={{ marginBottom: '10px' }}>
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '4px' }}>
              <label style={{ fontSize: '12px' }}>
                Nice 거리 (m): {clusterSettings.niceDistance}
              </label>
              <div style={{ display: 'flex', gap: '4px' }}>
                <button
                  onClick={() => setClusterSettings({...clusterSettings, niceDistance: Math.max(0, clusterSettings.niceDistance - 1)})}
                  style={{ padding: '2px 8px', fontSize: '12px', cursor: 'pointer' }}
                >
                  ▼
                </button>
                <button
                  onClick={() => setClusterSettings({...clusterSettings, niceDistance: Math.min(20, clusterSettings.niceDistance + 1)})}
                  style={{ padding: '2px 8px', fontSize: '12px', cursor: 'pointer' }}
                >
                  ▲
                </button>
              </div>
            </div>
            <input
              type="range"
              min="0"
              max="20"
              value={clusterSettings.niceDistance}
              onChange={(e) => setClusterSettings({...clusterSettings, niceDistance: Number(e.target.value)})}
              style={{ width: '100%' }}
            />
          </div>

          <div style={{ marginBottom: '10px' }}>
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '4px' }}>
              <label style={{ fontSize: '12px' }}>
                Golmok 거리 (m): {clusterSettings.golmokDistance}
              </label>
              <div style={{ display: 'flex', gap: '4px' }}>
                <button
                  onClick={() => setClusterSettings({...clusterSettings, golmokDistance: Math.max(0, clusterSettings.golmokDistance - 1)})}
                  style={{ padding: '2px 8px', fontSize: '12px', cursor: 'pointer' }}
                >
                  ▼
                </button>
                <button
                  onClick={() => setClusterSettings({...clusterSettings, golmokDistance: Math.min(20, clusterSettings.golmokDistance + 1)})}
                  style={{ padding: '2px 8px', fontSize: '12px', cursor: 'pointer' }}
                >
                  ▲
                </button>
              </div>
            </div>
            <input
              type="range"
              min="0"
              max="20"
              value={clusterSettings.golmokDistance}
              onChange={(e) => setClusterSettings({...clusterSettings, golmokDistance: Number(e.target.value)})}
              style={{ width: '100%' }}
            />
          </div>

          <div style={{ marginBottom: '10px' }}>
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '4px' }}>
              <label style={{ fontSize: '12px' }}>
                통합 거리 (m): {clusterSettings.combinedDistance}
              </label>
              <div style={{ display: 'flex', gap: '4px' }}>
                <button
                  onClick={() => setClusterSettings({...clusterSettings, combinedDistance: Math.max(0, clusterSettings.combinedDistance - 1)})}
                  style={{ padding: '2px 8px', fontSize: '12px', cursor: 'pointer' }}
                >
                  ▼
                </button>
                <button
                  onClick={() => setClusterSettings({...clusterSettings, combinedDistance: Math.min(50, clusterSettings.combinedDistance + 1)})}
                  style={{ padding: '2px 8px', fontSize: '12px', cursor: 'pointer' }}
                >
                  ▲
                </button>
              </div>
            </div>
            <input
              type="range"
              min="0"
              max="50"
              value={clusterSettings.combinedDistance}
              onChange={(e) => setClusterSettings({...clusterSettings, combinedDistance: Number(e.target.value)})}
              style={{ width: '100%' }}
            />
          </div>

          <div style={{ fontSize: '11px', color: '#666', marginTop: '12px', paddingTop: '12px', borderTop: '1px solid #eee' }}>
            <div style={{ marginBottom: '4px', color: '#888' }}>클러스터링 완료 시까지 자동 반복</div>
            <div>Nice: {rawNiceData.length} → {niceClusteredResult.length}</div>
            <div>Golmok: {rawGolmokData.length} → {golmokClusteredResult.length}</div>
            <div>통합: {combinedClusteredResult.length}개</div>
          </div>
        </div>

        {/* 레이어 토글 컨트롤 */}
        <div style={{
          position: 'absolute',
          top: '170px',
          left: '10px',
          zIndex: 1000,
          backgroundColor: 'white',
          padding: '15px',
          borderRadius: '8px',
          boxShadow: '0 2px 8px rgba(0,0,0,0.15)',
          minWidth: '220px'
        }}>
          <div style={{ fontWeight: 'bold', marginBottom: '10px', fontSize: '14px' }}>
            레이어 표시
          </div>

          {/* 유동인구 1 (Nice) */}
          <div style={{ marginBottom: '12px', paddingBottom: '12px', borderBottom: '1px solid #eee' }}>
            <div style={{ fontSize: '12px', fontWeight: 'bold', marginBottom: '6px', color: '#666' }}>유동인구 1</div>
            <label style={{
              display: 'flex',
              alignItems: 'center',
              gap: '8px',
              cursor: 'pointer',
              fontSize: '13px',
              marginBottom: '6px'
            }}>
              <input
                type="checkbox"
                checked={showNiceOnlyHeatmap}
                onChange={(e) => setShowNiceOnlyHeatmap(e.target.checked)}
                style={{ cursor: 'pointer', width: '16px', height: '16px' }}
              />
              <span>히트맵</span>
            </label>
            <label style={{
              display: 'flex',
              alignItems: 'center',
              gap: '8px',
              cursor: 'pointer',
              fontSize: '13px',
              marginBottom: '6px'
            }}>
              <input
                type="checkbox"
                checked={showNiceOnlyPoints}
                onChange={(e) => setShowNiceOnlyPoints(e.target.checked)}
                style={{ cursor: 'pointer', width: '16px', height: '16px' }}
              />
              <span>점지도</span>
            </label>
          </div>

          {/* Golmok 전용 */}
          <div style={{ marginBottom: '12px', paddingBottom: '12px', borderBottom: '1px solid #eee' }}>
            <div style={{ fontSize: '12px', fontWeight: 'bold', marginBottom: '6px', color: '#666' }}>유동인구 2</div>
            <label style={{
              display: 'flex',
              alignItems: 'center',
              gap: '8px',
              cursor: 'pointer',
              fontSize: '13px',
              marginBottom: '6px'
            }}>
              <input
                type="checkbox"
                checked={showGolmokOnlyHeatmap2}
                onChange={(e) => setShowGolmokOnlyHeatmap2(e.target.checked)}
                style={{ cursor: 'pointer', width: '16px', height: '16px' }}
              />
              <span>히트맵</span>
            </label>
            <label style={{
              display: 'flex',
              alignItems: 'center',
              gap: '8px',
              cursor: 'pointer',
              fontSize: '13px',
              marginBottom: '6px'
            }}>
              <input
                type="checkbox"
                checked={showGolmokOnlyPoints}
                onChange={(e) => setShowGolmokOnlyPoints(e.target.checked)}
                style={{ cursor: 'pointer', width: '16px', height: '16px' }}
              />
              <span>점지도</span>
            </label>
          </div>

          {/* 통합 */}
          <div style={{ marginBottom: '12px' }}>
            <div style={{ fontSize: '12px', fontWeight: 'bold', marginBottom: '6px', color: '#666' }}>통합</div>
            <label style={{
              display: 'flex',
              alignItems: 'center',
              gap: '8px',
              cursor: 'pointer',
              fontSize: '13px',
              marginBottom: '6px'
            }}>
              <input
                type="checkbox"
                checked={showCombinedClusteredHeatmap}
                onChange={(e) => setShowCombinedClusteredHeatmap(e.target.checked)}
                style={{ cursor: 'pointer', width: '16px', height: '16px' }}
              />
              <span style={{ fontWeight: 'bold', color: '#FF5722' }}>통합 히트맵</span>
            </label>
            <label style={{
              display: 'flex',
              alignItems: 'center',
              gap: '8px',
              cursor: 'pointer',
              fontSize: '13px',
              marginBottom: '6px'
            }}>
              <input
                type="checkbox"
                checked={showFinalClusteredPoints}
                onChange={(e) => setShowFinalClusteredPoints(e.target.checked)}
                style={{ cursor: 'pointer', width: '16px', height: '16px' }}
              />
              <span style={{ fontWeight: 'bold', color: '#4CAF50' }}>통합 점지도</span>
            </label>
          </div>
        </div>

        {/* 분석 중 로딩 오버레이 */}
        {loading && (
          <div style={{
            position: 'absolute',
            top: 0,
            left: 0,
            right: 0,
            bottom: 0,
            backgroundColor: 'rgba(0, 0, 0, 0.5)',
            zIndex: 2000,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            flexDirection: 'column',
            gap: '20px'
          }}>
            <div style={{
              width: '60px',
              height: '60px',
              border: '6px solid #f3f3f3',
              borderTop: '6px solid #4CAF50',
              borderRadius: '50%',
              animation: 'spin 1s linear infinite'
            }}></div>
            <div style={{
              color: 'white',
              fontSize: '18px',
              fontWeight: 'bold',
              textShadow: '0 2px 4px rgba(0,0,0,0.5)'
            }}>
              클러스터링 분석 중...
            </div>
          </div>
        )}

        <MapContainer
          center={[initialMapState.lat, initialMapState.lng]}
          zoom={initialMapState.zoom}
          style={{ height: '100%', width: '100%' }}
          zoomControl={false}
        >
          <TileLayer
            attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'
            url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
          />
          <MapClickHandler onClick={handleMapClick} onRightClick={() => setShowAnalysisArea(false)} />
          <MapPositionHandler />
          <ZoomDisplay />
          <CustomZoomControl />
          {previewAnalysis && <FitBoundsToAnalysis params={previewAnalysis.params} />}

          {/* 반경 모드 - 설정 시에만 표시 */}
          {showAnalysisArea && params.analysisType === 'radius' && (
            <>
              <Marker position={[params.latitude, params.longitude]}>
                <Popup>분석 중심점</Popup>
              </Marker>
              <Circle
                center={[params.latitude, params.longitude]}
                radius={params.radius}
                pathOptions={{ color: 'blue', fillColor: 'blue', fillOpacity: 0.1 }}
              />
            </>
          )}

          {/* 폴리곤 모드 - 설정 시에만 표시 */}
          {showAnalysisArea && params.analysisType === 'polygon' && params.polygonCoords.length > 0 && (
            <>
              {/* 폴리곤 점들 */}
              {params.polygonCoords.map((coord, idx) => (
                <CircleMarker
                  key={`point-${idx}`}
                  center={coord}
                  radius={5}
                  pathOptions={{ color: 'red', fillColor: 'red', fillOpacity: 1 }}
                >
                  <Popup>점 {idx + 1}</Popup>
                </CircleMarker>
              ))}

              {/* 3개 이상이면 폴리곤, 2개면 파란 선 */}
              {params.polygonCoords.length >= 3 ? (
                <Polygon
                  positions={params.polygonCoords}
                  pathOptions={{ color: 'blue', fillColor: 'blue', fillOpacity: 0.2, weight: 2 }}
                >
                  <Popup>분석 영역 ({params.polygonCoords.length}개 점)</Popup>
                </Polygon>
              ) : params.polygonCoords.length === 2 ? (
                <Polyline
                  positions={params.polygonCoords}
                  pathOptions={{ color: 'blue', weight: 2 }}
                />
              ) : null}
            </>
          )}


          {/* 통합 히트맵 (나이스 + 골목) */}
          {showCombinedHeatmap && (
            <CombinedHeatmapLayer
              nicePoints={niceFlowpopData}
              golmokLines={golmokFlowpopData}
              analysisParams={params}
              onClusteredPoints={setClusteredHeatmapPoints}
              onGolmokSampledPoints={setGolmokSampledPoints}
              onRawHeatmapPoints={setRawHeatmapPoints}
            />
          )}

          {/* 골목 전용 히트맵 */}
          {(showGolmokOnlyHeatmap || showGolmokHeatmapPoints || showCombinedClusteredPoints || showCombinedClusteredHeatmap) && (
            <GolmokOnlyHeatmapLayer
              golmokLines={golmokFlowpopData}
              analysisParams={params}
              onGolmokHeatmapPoints={setGolmokHeatmapPoints}
              showHeatmap={showGolmokOnlyHeatmap}
            />
          )}

          {/* 나이스 전용 히트맵 */}
          {(showNiceHeatmap || showNiceClusteredPoints || showCombinedClusteredPoints || showCombinedClusteredHeatmap) && (
            <NiceOnlyHeatmapLayer
              nicePoints={niceFlowpopData}
              analysisParams={params}
              onClusteredPoints={setNiceClusteredPoints}
              showHeatmap={showNiceHeatmap}
            />
          )}

          {/* Nice 전용 히트맵 */}
          {showNiceOnlyHeatmap && niceOnlyPoints.length > 0 && (
            <OptimizedHeatmapLayer
              clusteredPoints={niceOnlyPoints}
            />
          )}

          {/* Golmok 전용 히트맵 */}
          {showGolmokOnlyHeatmap2 && golmokOnlyPoints.length > 0 && (
            <OptimizedHeatmapLayer
              clusteredPoints={golmokOnlyPoints}
            />
          )}

          {/* 통합 히트맵 (원본 데이터) */}
          {showCombinedClusteredHeatmap && finalClusteredPoints.length > 0 && (
            <OptimizedHeatmapLayer
              clusteredPoints={finalClusteredPoints}
            />
          )}

          {/* Nice 전용 점 표시 */}
          {showNiceOnlyPoints && niceOnlyPoints.map((point, index) => {
            const getColorByGrade = (grade: number) => {
              if (grade >= 4.5) return '#FF0000';      // 빨강 (가장 높음)
              else if (grade >= 3.5) return '#F29661'; // 주황
              else if (grade >= 2.5) return '#FAED7D'; // 노랑
              else if (grade >= 1.5) return '#47C83E'; // 초록
              else return '#4641D8';                   // 파랑 (가장 낮음)
            };

            return (
              <CircleMarker
                key={`nice-only-${index}`}
                center={[point.lat, point.lng]}
                radius={5}
                pathOptions={{
                  fillColor: getColorByGrade(point.avgGrade),
                  fillOpacity: 0.8,
                  color: '#fff',
                  weight: 2
                }}
              >
                <Popup>
                  <div style={{ fontSize: '12px' }}>
                    <strong>Nice 포인트</strong><br />
                    Grade: {point.avgGrade.toFixed(2)}<br />
                    Intensity: {point.intensity !== undefined ? point.intensity.toFixed(3) : 'N/A'}<br />
                    위치: {point.lat.toFixed(6)}, {point.lng.toFixed(6)}
                  </div>
                </Popup>
              </CircleMarker>
            );
          })}

          {/* Golmok 전용 점 표시 */}
          {showGolmokOnlyPoints && golmokOnlyPoints.map((point, index) => {
            const getColorByGrade = (grade: number) => {
              if (grade >= 4.5) return '#FF0000';      // 빨강 (가장 높음)
              else if (grade >= 3.5) return '#F29661'; // 주황
              else if (grade >= 2.5) return '#FAED7D'; // 노랑
              else if (grade >= 1.5) return '#47C83E'; // 초록
              else return '#4641D8';                   // 파랑 (가장 낮음)
            };

            return (
              <CircleMarker
                key={`golmok-only-${index}`}
                center={[point.lat, point.lng]}
                radius={5}
                pathOptions={{
                  fillColor: getColorByGrade(point.avgGrade),
                  fillOpacity: 0.8,
                  color: '#fff',
                  weight: 2
                }}
              >
                <Popup>
                  <div style={{ fontSize: '12px' }}>
                    <strong>Golmok 포인트</strong><br />
                    Grade: {point.avgGrade.toFixed(2)}<br />
                    Intensity: {point.intensity !== undefined ? point.intensity.toFixed(3) : 'N/A'}<br />
                    위치: {point.lat.toFixed(6)}, {point.lng.toFixed(6)}
                  </div>
                </Popup>
              </CircleMarker>
            );
          })}

          {/* Golmok 클러스터링된 점만 표시 (count > 1) */}
          {showGolmokClusteredPoints && golmokOnlyPoints.filter(p => p.count > 1).map((point, index) => {
            const getColorByIntensity = (intensity: number) => {
              if (intensity >= 10) return '#8B0000';       // 진한 빨강 (매우 높음)
              else if (intensity >= 7) return '#FF0000';   // 빨강
              else if (intensity >= 5) return '#FF6347';   // 토마토색
              else if (intensity >= 3) return '#FFA500';   // 주황
              else return '#FFD700';                       // 금색
            };

            return (
              <CircleMarker
                key={`golmok-clustered-${index}`}
                center={[point.lat, point.lng]}
                radius={7}
                pathOptions={{
                  fillColor: getColorByIntensity(point.intensity || 0),
                  fillOpacity: 0.85,
                  color: '#9C27B0',
                  weight: 3
                }}
              >
                <Popup>
                  <div style={{ fontSize: '12px' }}>
                    <strong style={{ color: '#9C27B0' }}>Golmok 클러스터 포인트</strong><br />
                    <hr style={{ margin: '5px 0' }} />
                    합쳐진 점 개수: <strong>{point.count}개</strong><br />
                    Grade: {point.avgGrade.toFixed(2)}<br />
                    Intensity: <strong>{(point.intensity || 0).toFixed(3)}</strong><br />
                    <hr style={{ margin: '5px 0' }} />
                    위치: {point.lat.toFixed(6)}, {point.lng.toFixed(6)}
                  </div>
                </Popup>
              </CircleMarker>
            );
          })}

          {/* 최종 클러스터링된 점 표시 (통합) */}
          {showFinalClusteredPoints && finalClusteredPoints.map((point, index) => {
            const getColorByGrade = (grade: number) => {
              if (grade >= 4.5) return '#FF0000';      // 빨강 (가장 높음)
              else if (grade >= 3.5) return '#F29661'; // 주황
              else if (grade >= 2.5) return '#FAED7D'; // 노랑
              else if (grade >= 1.5) return '#47C83E'; // 초록
              else return '#4641D8';                   // 파랑 (가장 낮음)
            };

            return (
              <CircleMarker
                key={`final-cluster-${index}`}
                center={[point.lat, point.lng]}
                radius={5}
                pathOptions={{
                  fillColor: getColorByGrade(point.avgGrade),
                  fillOpacity: 0.8,
                  color: '#fff',
                  weight: 2
                }}
              >
                <Popup>
                  <div style={{ fontSize: '12px' }}>
                    <strong style={{ color: '#4CAF50' }}>통합 포인트</strong><br />
                    <hr style={{ margin: '5px 0' }} />
                    합쳐진 점 개수: <strong>{point.count || 1}개</strong><br />
                    Grade: {point.avgGrade.toFixed(2)}<br />
                    Intensity: {point.intensity !== undefined ? point.intensity.toFixed(3) : point.avgGrade.toFixed(3)}<br />
                    <hr style={{ margin: '5px 0' }} />
                    위치: {point.lat.toFixed(6)}, {point.lng.toFixed(6)}
                  </div>
                </Popup>
              </CircleMarker>
            );
          })}

          {/* 통합 클러스터 포인트 표시 (나이스 + 골목) */}
          {showCombinedClusteredPoints && (() => {
            // 나이스 클러스터 포인트 (이미 변환된 grade 사용)
            const nicePoints = niceClusteredPoints.map(p => ({
              lat: p.lat,
              lng: p.lng,
              grade: p.grade, // 이미 변환됨 (5=많음, 1=적음)
              intensity: p.intensity,
              count: p.count,
              source: 'nice'
            }));

            // 골목 클러스터 포인트 (grade 그대로)
            const golmokPoints = golmokHeatmapPoints.map(p => ({
              lat: p.lat,
              lng: p.lng,
              grade: p.grade,
              intensity: p.intensity,
              count: 1, // 골목은 count 정보가 없으므로 1로 설정
              source: 'golmok'
            }));

            // 합치기
            const allPoints = [...nicePoints, ...golmokPoints];

            // Grade에 따른 색상 (정규화된 grade 기준: 5가 가장 높음)
            const getColorByGrade = (grade: number) => {
              if (grade >= 4.5) return '#FF0000';      // 빨강 (가장 높음)
              else if (grade >= 3.5) return '#F29661'; // 주황
              else if (grade >= 2.5) return '#FAED7D'; // 노랑
              else if (grade >= 1.5) return '#47C83E'; // 초록
              else return '#4641D8';                   // 파랑 (가장 낮음)
            };

            return allPoints.map((point, index) => (
              <CircleMarker
                key={`combined-cluster-${index}`}
                center={[point.lat, point.lng]}
                radius={5}
                pathOptions={{
                  fillColor: getColorByGrade(point.grade),
                  fillOpacity: 0.8,
                  color: '#fff',
                  weight: 2
                }}
              >
                <Popup>
                  <div style={{ fontSize: '12px' }}>
                    <strong>통합 클러스터 포인트</strong><br />
                    출처: {point.source === 'nice' ? '나이스' : '골목'}<br />
                    Grade: {point.grade.toFixed(2)}<br />
                    Intensity: {point.intensity.toFixed(3)}<br />
                    {point.count > 1 && `합쳐진 점 개수: ${point.count}개`}<br />
                    위치: {point.lat.toFixed(6)}, {point.lng.toFixed(6)}
                  </div>
                </Popup>
              </CircleMarker>
            ));
          })()}

          {/* 나이스 클러스터링된 점 표시 */}
          {showNiceClusteredPoints && niceClusteredPoints.map((point, index) => {
            // Golmok 형식으로 통일: grade 5=높음(빨강), 1=낮음(파랑)
            const getColorByGrade = (grade: number) => {
              if (grade >= 4.5) return '#FF0000';      // 빨강 (가장 높음)
              else if (grade >= 3.5) return '#F29661'; // 주황
              else if (grade >= 2.5) return '#FAED7D'; // 노랑
              else if (grade >= 1.5) return '#47C83E'; // 초록
              else return '#4641D8';                   // 파랑 (가장 낮음)
            };

            return (
              <CircleMarker
                key={`nice-clustered-${index}`}
                center={[point.lat, point.lng]}
                radius={5}
                pathOptions={{
                  fillColor: getColorByGrade(point.grade),
                  fillOpacity: 0.8,
                  color: '#fff',
                  weight: 2
                }}
              >
                <Popup>
                  <div style={{ fontSize: '12px' }}>
                    <strong>나이스 클러스터 포인트</strong><br />
                    Grade: {point.grade.toFixed(2)}<br />
                    Intensity: {point.intensity.toFixed(3)}<br />
                    합쳐진 점 개수: {point.count}개<br />
                    위치: {point.lat.toFixed(6)}, {point.lng.toFixed(6)}<br />
                    색상 기준: grade 5(빨강)~1(파랑)
                  </div>
                </Popup>
              </CircleMarker>
            );
          })}

          {/* 합쳐진 점 표시 (2개 이상) */}
          {showClusteredPoints && clusteredHeatmapPoints.filter(p => p.isClustered).map((point, index) => {
            const getColorByGrade = (grade: number) => {
              if (grade >= 4) return '#FF0000';
              else if (grade >= 3) return '#F29661';
              else if (grade >= 2) return '#FAED7D';
              else if (grade >= 1) return '#47C83E';
              else return '#4641D8';
            };

            return (
              <CircleMarker
                key={`clustered-${index}`}
                center={[point.lat, point.lng]}
                radius={5}
                pathOptions={{
                  fillColor: getColorByGrade(point.avgGrade),
                  fillOpacity: 0.9,
                  color: '#9C27B0',
                  weight: 2
                }}
              >
                <Popup>
                  <div>
                    <strong style={{ color: '#9C27B0' }}>합쳐진 점</strong><br />
                    클러스터 레벨: {point.clusterLevel}차<br />
                    합쳐진 개수: {point.count}개<br />
                    평균 Grade: {point.avgGrade.toFixed(2)}<br />
                    위치: {point.lat.toFixed(6)}, {point.lng.toFixed(6)}
                  </div>
                </Popup>
              </CircleMarker>
            );
          })}

          {/* 혼자 있는 점 표시 */}
          {showSinglePoints && clusteredHeatmapPoints.filter(p => !p.isClustered).map((point, index) => {
            const getColorByGrade = (grade: number) => {
              if (grade >= 4) return '#FF0000';
              else if (grade >= 3) return '#F29661';
              else if (grade >= 2) return '#FAED7D';
              else if (grade >= 1) return '#47C83E';
              else return '#4641D8';
            };

            return (
              <CircleMarker
                key={`single-${index}`}
                center={[point.lat, point.lng]}
                radius={3}
                pathOptions={{
                  fillColor: getColorByGrade(point.avgGrade),
                  fillOpacity: 0.7,
                  color: '#FF9800',
                  weight: 2
                }}
              >
                <Popup>
                  <div>
                    <strong style={{ color: '#FF9800' }}>혼자 있는 점</strong><br />
                    Grade: {point.avgGrade.toFixed(2)}<br />
                    위치: {point.lat.toFixed(6)}, {point.lng.toFixed(6)}
                  </div>
                </Popup>
              </CircleMarker>
            );
          })}

          {/* 모든 히트맵 포인트 표시 (통합) */}
          {showAllHeatmapPoints && clusteredHeatmapPoints.map((point, index) => {
            const getColorByGrade = (grade: number) => {
              if (grade >= 4) return '#FF0000';
              else if (grade >= 3) return '#F29661';
              else if (grade >= 2) return '#FAED7D';
              else if (grade >= 1) return '#47C83E';
              else return '#4641D8';
            };

            return (
              <CircleMarker
                key={`all-heatmap-${index}`}
                center={[point.lat, point.lng]}
                radius={4}
                pathOptions={{
                  fillColor: getColorByGrade(point.avgGrade),
                  fillOpacity: 0.8,
                  color: '#00BCD4',
                  weight: 2
                }}
              >
                <Popup>
                  <div>
                    <strong style={{ color: '#00BCD4' }}>히트맵 포인트</strong><br />
                    Grade: {point.avgGrade.toFixed(2)}<br />
                    클러스터 개수: {point.count}개<br />
                    위치: {point.lat.toFixed(6)}, {point.lng.toFixed(6)}
                  </div>
                </Popup>
              </CircleMarker>
            );
          })}

          {/* 골목 샘플링 포인트 표시 */}
          {showGolmokSampledPoints && golmokSampledPoints.map((point, index) => {
            const getColorByGrade = (grade: number) => {
              if (grade >= 5) return '#FF0000';
              else if (grade >= 4) return '#F29661';
              else if (grade >= 3) return '#FAED7D';
              else if (grade >= 2) return '#47C83E';
              else return '#4641D8';
            };

            return (
              <CircleMarker
                key={`golmok-sampled-${index}`}
                center={[point.lat, point.lng]}
                radius={3}
                pathOptions={{
                  fillColor: getColorByGrade(point.grade),
                  fillOpacity: 0.9,
                  color: '#E91E63',
                  weight: 1
                }}
              >
                <Popup>
                  <div>
                    <strong style={{ color: '#E91E63' }}>골목 샘플링 포인트</strong><br />
                    Grade: {point.grade}<br />
                    위치: {point.lat.toFixed(6)}, {point.lng.toFixed(6)}
                  </div>
                </Popup>
              </CircleMarker>
            );
          })}

          {/* 히트맵 원본 점 표시 (0.5m 반경 분산된 점들) */}
          {showRawHeatmapPoints && rawHeatmapPoints.map((point, index) => (
            <CircleMarker
              key={`raw-heatmap-${index}`}
              center={[point[0], point[1]]}
              radius={1}
              pathOptions={{
                fillColor: '#795548',
                fillOpacity: 0.6,
                color: '#795548',
                weight: 0.5
              }}
            >
              <Popup>
                <div>
                  <strong style={{ color: '#795548' }}>히트맵 원본 점</strong><br />
                  Intensity: {point[2].toFixed(2)}<br />
                  위치: {point[0].toFixed(6)}, {point[1].toFixed(6)}
                </div>
              </Popup>
            </CircleMarker>
          ))}

          {/* 골목 히트맵 직전 데이터 표시 */}
          {showGolmokHeatmapPoints && golmokHeatmapPoints.map((point, index) => {
            const getColorByGrade = (grade: number) => {
              if (grade >= 5) return '#FF0000';
              else if (grade >= 4) return '#F29661';
              else if (grade >= 3) return '#FAED7D';
              else if (grade >= 2) return '#47C83E';
              else return '#4641D8';
            };

            return (
              <CircleMarker
                key={`golmok-heatmap-${index}`}
                center={[point.lat, point.lng]}
                radius={5}
                pathOptions={{
                  fillColor: getColorByGrade(point.grade),
                  fillOpacity: 0.8,
                  color: '#fff',
                  weight: 2
                }}
              >
                <Popup>
                  <div>
                    <strong style={{ color: '#8BC34A' }}>골목 히트맵 직전 데이터</strong><br />
                    Grade: {point.grade}<br />
                    Intensity: {point.intensity.toFixed(2)}<br />
                    위치: {point.lat.toFixed(6)}, {point.lng.toFixed(6)}
                  </div>
                </Popup>
              </CircleMarker>
            );
          })}

          {/* 나이스 포인트 표시 */}
          {showNiceFlowpop && niceFlowpopData.length > 0 && niceFlowpopData.map((point, index) => {
            // 백엔드에서 이미 grade 계산됨 (5=많음, 1=적음)
            if (!point.grade) return null; // grade가 없으면 스킵

            // Grade에 따른 색상 (5=많음, 1=적음)
            const getColorByGrade = (g: number) => {
              if (g >= 4.5) return '#FF0000';      // 빨강 (가장 높음)
              else if (g >= 3.5) return '#F29661'; // 주황
              else if (g >= 2.5) return '#FAED7D'; // 노랑
              else if (g >= 1.5) return '#47C83E'; // 초록
              else return '#4641D8';               // 파랑 (가장 낮음)
            };

            return (
              <CircleMarker
                key={`nice-point-${index}`}
                center={[point.lat, point.lng]}
                radius={4}
                pathOptions={{
                  fillColor: getColorByGrade(point.grade),
                  fillOpacity: 0.7,
                  color: '#fff',
                  weight: 1
                }}
              >
                <Popup>
                  <div style={{ fontSize: '12px' }}>
                    <strong>나이스 클러스터 포인트 (3m)</strong><br />
                    Grade: {point.grade}<br />
                    유동인구: {point.flowpopCount?.toLocaleString() || 'N/A'}명<br />
                    클러스터 개수: {point.count || 1}개<br />
                    위치: {point.lat.toFixed(6)}, {point.lng.toFixed(6)}<br />
                    색상 기준: grade 5(빨강)~1(파랑)
                  </div>
                </Popup>
              </CircleMarker>
            );
          })}

          {/* 골목상권 유동인구 라인 - 주석 처리 */}
          {/* {showGolmokFlowpop && golmokFlowpopData.length > 0 && (() => {
            // 현재 선택된 값 타입에 따라 최대/최소값 계산
            const values = golmokFlowpopData.map(item =>
              flowpopValueType === 'cost' ? item.cost : item.acost
            );
            const maxValue = Math.max(...values);
            const minValue = Math.min(...values);
            const range = maxValue - minValue;

            // 값에 따라 색상 계산 (min-max 정규화)
            const getColorByValue = (value: number) => {
              // 정규화: (값 - 최소값) / (최대값 - 최소값) = 0~1 사이의 값
              const normalized = range > 0 ? (value - minValue) / range : 0.5;
              const percentage = normalized * 100;

              // Grade와 유사한 구간 분할
              if (percentage >= 47.5) return '#FF0000';      // 빨강 - Grade 5 수준
              else if (percentage >= 36.5) return '#F29661'; // 주황 - Grade 4 수준
              else if (percentage >= 25.0) return '#FAED7D'; // 노랑 - Grade 3 수준
              else if (percentage >= 12.5) return '#47C83E'; // 초록 - Grade 2 수준
              else return '#4641D8';                          // 파랑 - Grade 1 수준
            };

            return golmokFlowpopData.map((item, index) => {
              const displayValue = flowpopValueType === 'cost' ? item.cost : item.acost;
              const normalized = range > 0 ? ((displayValue - minValue) / range * 100) : 50;

              return (
                <Polyline
                  key={`golmok-flowpop-${index}-${flowpopValueType}`}
                  positions={item.coords}
                  color={getColorByValue(displayValue)}
                  weight={4}
                  opacity={0.8}
                >
                  <Popup>
                    <strong>유동인구 (골목상권)</strong><br/>
                    실시간: {item.cost.toLocaleString()}명<br/>
                    누적평균: {item.acost.toLocaleString()}명<br/>
                    등급: {item.grade} (5=많음, 1=적음)<br/>
                    원본 비율: {item.per.toFixed(1)}%<br/>
                    정규화 비율: {normalized.toFixed(1)}%<br/>
                    도로ID: {item.roadLinkId}
                  </Popup>
                </Polyline>
              );
            });
          })()} */}

          {/* 통합 유동인구 라인 (골목 + 나이스) - Grade 기반 - 주석 처리 */}
          {/* {showIntegratedFlowpop && integratedFlowpopData.length > 0 && (() => {
            // integrated_grade에 따라 색상 결정 (1~5 등급)
            const getColorByGrade = (grade: number) => {
              if (grade >= 4.5) return '#FF0000';       // 5등급: 빨강
              else if (grade >= 3.5) return '#F29661';  // 4등급: 주황
              else if (grade >= 2.5) return '#FAED7D';  // 3등급: 노랑
              else if (grade >= 1.5) return '#47C83E';  // 2등급: 초록
              else return '#4641D8';                     // 1등급: 파랑
            };

            return integratedFlowpopData.map((item, index) => {
              return (
                <Polyline
                  key={`integrated-flowpop-${index}`}
                  positions={item.coords}
                  color={getColorByGrade(item.integrated_grade || item.grade)}
                  weight={6}
                  opacity={0.9}
                  eventHandlers={{
                    click: () => {
                      // 클릭 시 해당 도로의 NICE 포인트 표시
                      setSelectedRoadNicePoints(item.nice_points || []);
                    }
                  }}
                >
                  <Popup>
                    <strong style={{ color: '#ff5722' }}>통합 유동인구 (Grade 기반)</strong><br/>
                    <hr style={{ margin: '8px 0' }}/>
                    <strong>골목 Grade:</strong> {item.golmok_grade || item.grade}<br/>
                    <strong>NICE Grade 평균:</strong> {(item.nice_grade_avg || 0).toFixed(2)}<br/>
                    <small style={{ color: '#666' }}>
                      ({item.nice_count}개 포인트)
                    </small><br/>
                    <strong style={{ color: '#ff5722' }}>통합 Grade:</strong> <strong>{(item.integrated_grade || 0).toFixed(2)}</strong><br/>
                    <hr style={{ margin: '8px 0' }}/>
                    골목 유동인구: {item.golmok_cost.toLocaleString()}명<br/>
                    도로ID: {item.roadLinkId}
                  </Popup>
                </Polyline>
              );
            });
          })()} */}

          {/* 선택된 도로의 NICE 포인트 표시 - 주석 처리 */}
          {/* {selectedRoadNicePoints.length > 0 && selectedRoadNicePoints.map((point, index) => (
            <CircleMarker
              key={`selected-nice-point-${index}`}
              center={[point.lat, point.lng]}
              radius={8}
              pathOptions={{
                color: '#FF00FF',
                fillColor: '#FF00FF',
                fillOpacity: 0.8,
                weight: 2
              }}
            >
              <Popup>
                <strong style={{ color: '#FF00FF' }}>선택된 도로의 NICE 포인트</strong><br/>
                <hr style={{ margin: '8px 0' }}/>
                <strong>유동인구:</strong> {point.flowpopCount.toLocaleString()}명<br/>
                {point.grade && (
                  <>
                    <strong>Grade:</strong> {point.grade.toFixed(2)}<br/>
                  </>
                )}
                <small style={{ color: '#666' }}>
                  위치: ({point.lat.toFixed(6)}, {point.lng.toFixed(6)})
                </small>
              </Popup>
            </CircleMarker>
          ))} */}

          {/* 미리보기 중인 분석의 폴리곤 표시 - 주석 처리 */}
          {/* {previewAnalysis && previewAnalysis.params.analysisType === 'polygon' && previewAnalysis.params.polygonCoords.length >= 3 && (
            <Polygon
              positions={previewAnalysis.params.polygonCoords}
              pathOptions={{ color: '#FF5722', fillColor: '#FF5722', fillOpacity: 0.3, weight: 3 }}
            />
          )} */}

          {/* {previewAnalysis && previewAnalysis.params.analysisType === 'radius' && (
            <Circle
              center={[previewAnalysis.params.latitude, previewAnalysis.params.longitude]}
              radius={previewAnalysis.params.radius}
              pathOptions={{ color: '#FF5722', fillColor: '#FF5722', fillOpacity: 0.3, weight: 3 }}
            />
          )} */}

          {/* 분석 결과 레이어 - 주석 처리 */}
          {/* {result && (
            <>
              버퍼 영역
              {result.buffer && (
                <GeoJSON
                  key={`buffer-${key}`}
                  data={result.buffer}
                  style={bufferStyle}
                />
              )}

              격자
              {result.grid && (
                <GeoJSON
                  key={`grid-${key}`}
                  data={result.grid}
                  style={gridStyle}
                  onEachFeature={(feature, layer) => {
                    const props = feature.properties;
                    if (props) {
                      layer.bindPopup(`
                        <strong>격자 정보</strong><br/>
                        밀도 등급: ${props.density_class || 'N/A'}<br/>
                        점수: ${(props.density_score * 100).toFixed(1)}%<br/>
                        시설 수: ${props.count || 0}
                      `);
                    }
                  }}
                />
              )}

              포인트 (시설물)
              {result.points && (
                <GeoJSON
                  key={`points-${key}`}
                  data={result.points}
                  pointToLayer={pointToLayer}
                />
              )}
            </>
          )} */}
        </MapContainer>
      </div>

      <div className="control-panel">
        <h2>상권 분석 설정</h2>

        <form onSubmit={handleSubmit}>
          {/* 분석 영역 표시 토글 */}
          <div className="form-group">
            <label style={{ display: 'flex', alignItems: 'center', gap: '8px', cursor: 'pointer' }}>
              <input
                type="checkbox"
                checked={showAnalysisArea}
                onChange={(e) => setShowAnalysisArea(e.target.checked)}
                style={{ cursor: 'pointer', width: '16px', height: '16px' }}
              />
              <span>분석 영역 표시</span>
            </label>
          </div>

          <div className="form-group">
            <label>분석 범위</label>
            <div style={{ display: 'flex', gap: '10px', marginTop: '5px' }}>
              <button
                type="button"
                onClick={() => {
                  setParams({ ...params, analysisType: 'radius', polygonCoords: [] });
                  setShowAnalysisArea(true); // 범위 변경 시 영역 표시
                }}
                style={{
                  flex: 1,
                  padding: '8px 16px',
                  backgroundColor: params.analysisType === 'radius' ? '#2196F3' : '#f0f0f0',
                  color: params.analysisType === 'radius' ? 'white' : '#333',
                  border: 'none',
                  borderRadius: '4px',
                  cursor: 'pointer',
                  fontWeight: params.analysisType === 'radius' ? 'bold' : 'normal',
                  transition: 'all 0.3s ease'
                }}
              >
                반경
              </button>
              <button
                type="button"
                onClick={() => {
                  setParams({ ...params, analysisType: 'polygon', polygonCoords: [] });
                  setShowAnalysisArea(true); // 범위 변경 시 영역 표시
                }}
                style={{
                  flex: 1,
                  padding: '8px 16px',
                  backgroundColor: params.analysisType === 'polygon' ? '#2196F3' : '#f0f0f0',
                  color: params.analysisType === 'polygon' ? 'white' : '#333',
                  border: 'none',
                  borderRadius: '4px',
                  cursor: 'pointer',
                  fontWeight: params.analysisType === 'polygon' ? 'bold' : 'normal',
                  transition: 'all 0.3s ease'
                }}
              >
                다각형
              </button>
            </div>
          </div>

          {params.analysisType === 'radius' && (
            <>
              <div className="form-group">
                <label>분석 반경 (미터)</label>
                <select
                  value={selectedRadius}
                  onChange={(e) => {
                    const radius = parseInt(e.target.value);
                    setSelectedRadius(radius);
                    setParams({ ...params, radius });
                    setShowAnalysisArea(true);
                  }}
                  required
                >
                  <option value="100">100m</option>
                  <option value="250">250m</option>
                  <option value="500">500m</option>
                </select>
              </div>
            </>
          )}

          {params.analysisType === 'polygon' && (
            <>
              <div className="form-group">
                <label>다각형 설정</label>

                {/* 버튼 그룹 */}
                <div style={{ display: 'flex', gap: '10px', marginTop: '10px', marginBottom: '10px' }}>
                  <button
                    type="button"
                    className="submit-btn"
                    style={{
                      backgroundColor: isPolygonDrawingEnabled ? '#ff9800' : '#4CAF50',
                      flex: 1
                    }}
                    onClick={() => {
                      setIsPolygonDrawingEnabled(!isPolygonDrawingEnabled);
                      if (!isPolygonDrawingEnabled) {
                        setShowAnalysisArea(true);
                      }
                    }}
                    disabled={isPolygonLocked}
                  >
                    {isPolygonDrawingEnabled ? '일시정지' : '영역설정'}
                  </button>

                  <button
                    type="button"
                    className="submit-btn"
                    style={{
                      backgroundColor: '#f44336',
                      flex: 1
                    }}
                    onClick={() => {
                      setParams(prev => ({ ...prev, polygonCoords: [] }));
                      setIsPolygonLocked(false);
                      setIsPolygonDrawingEnabled(false);
                      setResult(null);
                      setFlowpopData([]);
                    }}
                  >
                    재설정
                  </button>
                </div>

                {isPolygonLocked ? (
                  <>
                    <p style={{ fontSize: '12px', color: '#4CAF50', margin: '5px 0', fontWeight: 'bold' }}>
                      폴리곤이 설정되었습니다
                    </p>
                    {params.polygonCoords.length >= 3 && (
                      <p style={{ fontSize: '12px', color: '#666', margin: '5px 0' }}>
                        면적: {(calculatePolygonArea(params.polygonCoords) / 1000000).toFixed(2)} km²
                      </p>
                    )}
                  </>
                ) : (
                  <>
                    {params.polygonCoords.length >= 3 && (
                      <p style={{ fontSize: '12px', color: '#666', margin: '5px 0' }}>
                        면적: {(calculatePolygonArea(params.polygonCoords) / 1000000).toFixed(2)} km²
                      </p>
                    )}
                  </>
                )}
              </div>
            </>
          )}

          <button type="submit" disabled={loading} className="submit-btn">
            {loading ? '분석 중...' : '상권 분석 시작'}
          </button>
        </form>

      </div>

      {/* 분석 결과 모달 */}
      {activeAnalysis && !activeAnalysis.isMinimized && (
        <AnalysisResultModal
          isOpen={true}
          onClose={() => handleMinimizeAnalysis(activeAnalysis.id)}
          result={activeAnalysis.result}
          onDownloadPDF={handleDownloadPDF}
          mapSnapshot={activeAnalysis.mapSnapshot}
        />
      )}

      {/* 최소화된 분석 목록 */}
      {analysisHistory.filter(a => a.isMinimized).length > 0 && (
        <div style={{
          position: 'absolute',
          bottom: '20px',
          left: '270px',
          zIndex: 2000,
          display: 'flex',
          flexDirection: 'row',
          gap: '10px',
          maxWidth: 'calc(100% - 20px)',
          overflowX: 'auto',
          padding: '10px'
        }}>
          {analysisHistory.filter(a => a.isMinimized).map(analysis => (
            <div
              key={analysis.id}
              onClick={() => handlePreviewToggle(analysis.id)}
              style={{
                backgroundColor: 'white',
                padding: '15px',
                borderRadius: '8px',
                boxShadow: previewAnalysisId === analysis.id ? '0 4px 16px rgba(0,0,0,0.3)' : '0 2px 12px rgba(0,0,0,0.2)',
                minWidth: '250px',
                cursor: 'pointer',
                border: previewAnalysisId === analysis.id ? '2px solid #FF5722' : '2px solid #2196F3',
                transition: 'all 0.2s',
                transform: previewAnalysisId === analysis.id ? 'translateY(-2px)' : 'translateY(0)'
              }}
            >
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '8px' }}>
                <span style={{ fontSize: '14px', fontWeight: 'bold', color: '#333' }}>
                  {analysis.title}
                </span>
                <button
                  onClick={(e) => {
                    e.stopPropagation();
                    handleDeleteAnalysis(analysis.id);
                  }}
                  style={{
                    padding: '4px 8px',
                    backgroundColor: '#f44336',
                    color: 'white',
                    border: 'none',
                    borderRadius: '4px',
                    cursor: 'pointer',
                    fontSize: '12px'
                  }}
                >
                  삭제
                </button>
              </div>
              <div style={{ fontSize: '12px', color: '#666', marginBottom: '10px' }}>
                {new Date(analysis.timestamp).toLocaleString('ko-KR')}
              </div>
              <button
                onClick={() => handleRestoreAnalysis(analysis.id)}
                style={{
                  width: '100%',
                  padding: '8px',
                  backgroundColor: '#2196F3',
                  color: 'white',
                  border: 'none',
                  borderRadius: '4px',
                  cursor: 'pointer',
                  fontWeight: 'bold'
                }}
              >
                결과 보기
              </button>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

export default AnalysisMapAdvanced;
