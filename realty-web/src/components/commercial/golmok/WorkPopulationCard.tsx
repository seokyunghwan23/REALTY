import React from 'react';
import { LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, Legend, ResponsiveContainer } from 'recharts';

interface WorkPopulationCardProps {
  data: any;
}

const WorkPopulationCard: React.FC<WorkPopulationCardProps> = ({ data }) => {
  if (!data || data.status !== 'success' || !data.data) {
    return null;
  }

  const apiData = data.data;

  // 총 직장인구 수 및 밀도
  const totalPopulation = apiData.TOT_WRC_POPLTN_CO || 0;
  const density = apiData.TOT_WRC_POPLTN_DNST || 0;

  // 전년/전분기 대비
  const quarterDiff = apiData.TRDAR_WRC_POPLTN_DNST_QDIFF || 0;
  const yearDiff = apiData.TRDAR_WRC_POPLTN_DNST_YDIFF || 0;

  // 트렌드 차트 데이터 (5개 분기)
  const trendData = [
    {
      quarter: apiData.TOT_WRC_POPLTN_QU_BF_4 || '2024년 2분기',
      선택상권: apiData.TRDAR_TOT_WRC_POPLTN_HA_BF_4 || 0,
      자치구: apiData.SIGNGU_TOT_WRC_POPLTN_HA_BF_4 || 0,
      서울시: apiData.SIDO_TOT_WRC_POPLTN_HA_BF_4 || 0,
    },
    {
      quarter: apiData.TOT_WRC_POPLTN_QU_BF_3 || '2024년 3분기',
      선택상권: apiData.TRDAR_TOT_WRC_POPLTN_HA_BF_3 || 0,
      자치구: apiData.SIGNGU_TOT_WRC_POPLTN_HA_BF_3 || 0,
      서울시: apiData.SIDO_TOT_WRC_POPLTN_HA_BF_3 || 0,
    },
    {
      quarter: apiData.TOT_WRC_POPLTN_QU_BF_2 || '2024년 4분기',
      선택상권: apiData.TRDAR_TOT_WRC_POPLTN_HA_BF_2 || 0,
      자치구: apiData.SIGNGU_TOT_WRC_POPLTN_HA_BF_2 || 0,
      서울시: apiData.SIDO_TOT_WRC_POPLTN_HA_BF_2 || 0,
    },
    {
      quarter: apiData.TOT_WRC_POPLTN_QU_BF_1 || '2025년 1분기',
      선택상권: apiData.TRDAR_TOT_WRC_POPLTN_HA_BF_1 || 0,
      자치구: apiData.SIGNGU_TOT_WRC_POPLTN_HA_BF_1 || 0,
      서울시: apiData.SIDO_TOT_WRC_POPLTN_HA_BF_1 || 0,
    },
    {
      quarter: apiData.TOT_WRC_POPLTN_QU_BF_0 || '2025년 2분기',
      선택상권: apiData.TRDAR_TOT_WRC_POPLTN_HA_BF_0 || 0,
      자치구: apiData.SIGNGU_TOT_WRC_POPLTN_HA_BF_0 || 0,
      서울시: apiData.SIDO_TOT_WRC_POPLTN_HA_BF_0 || 0,
    },
  ];

  return (
    <div style={{ marginBottom: '30px' }}>
      <h4 style={{ fontSize: '14px', color: '#6B7280', marginBottom: '10px' }}>직장인구 수</h4>

      {/* 제목 */}
      <p style={{ fontSize: '18px', marginBottom: '15px', lineHeight: '1.6' }}>
        직장인구 수는 <strong style={{ color: '#0EA5E9' }}>{totalPopulation.toLocaleString()}명</strong>이고
        밀도는 <strong style={{ color: '#0EA5E9' }}>{density}명/ha</strong> 입니다.
      </p>

      
    </div>
  );
};

export default WorkPopulationCard;
