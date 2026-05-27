import React from 'react';

interface FloatingPopulationCountCardProps {
  data: any;
}

const FloatingPopulationCountCard: React.FC<FloatingPopulationCountCardProps> = ({ data }) => {
  if (!data || data.status !== 'success' || !data.data) {
    return null;
  }

  const apiData = data.data;
  const dailyAvg = apiData.DAILY_FLPOP_CO || 0;
  const density = apiData.TRDAR_TOT_FLPOP_CO_BF_0 || 0;

  return (
    <div style={{
      backgroundColor: 'white',
      borderRadius: '12px',
      padding: '24px',
      marginBottom: '20px',
      boxShadow: '0 1px 3px rgba(0,0,0,0.1)',
    }}>
      <h4 style={{
        fontSize: '16px',
        fontWeight: '600',
        marginBottom: '16px',
        color: '#374151',
      }}>
        유동인구 수
      </h4>

      <p style={{
        fontSize: '15px',
        color: '#374151',
        lineHeight: '1.6',
        margin: 0,
      }}>
        유동인구 수는 일평균 <strong style={{ color: '#10B981', fontSize: '18px' }}>{dailyAvg.toLocaleString()}명</strong> 이고
        밀도는 <strong style={{ color: '#3B82F6', fontSize: '18px' }}>{density.toLocaleString()}명/㏊</strong> 입니다.
      </p>
    </div>
  );
};

export default FloatingPopulationCountCard;
