import React from 'react';

interface ResidentPopulationCountCardProps {
  data: any;
  householdData?: any;
}

const ResidentPopulationCountCard: React.FC<ResidentPopulationCountCardProps> = ({ data, householdData }) => {
  if (!data || data.status !== 'success' || !data.data) {
    return null;
  }

  const apiData = data.data;
  const totalCount = apiData.TRDAR_TOT_REPOP_CO || 0;
  const density = apiData.TRDAR_TOT_REPOP_DNST_BF_0 || 0;

  // 가구세대 수 데이터
  const totalHousehold = householdData?.data?.TRDAR_TOT_HSHLD_CO_BF_0 || 0;

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
        주거인구 및 가구 정보
      </h4>

      <p style={{
        fontSize: '15px',
        color: '#374151',
        lineHeight: '1.6',
        margin: 0,
      }}>
        주거인구는 <strong style={{ color: '#8B5CF6', fontSize: '18px' }}>{totalCount.toLocaleString()}명</strong> 이고
        밀도는 <strong style={{ color: '#EC4899', fontSize: '18px' }}>{density.toLocaleString()}명/㏊</strong> 입니다.
        {householdData && householdData.status === 'success' && totalHousehold > 0 && (
          <>
            {' '}총 가구 수는 <strong style={{ color: '#10B981', fontSize: '18px' }}>{totalHousehold.toLocaleString()}세대</strong> 입니다.
          </>
        )}
      </p>
    </div>
  );
};

export default ResidentPopulationCountCard;
