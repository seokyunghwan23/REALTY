import React from 'react';

interface HouseholdCountCardProps {
  data: any;
}

const HouseholdCountCard: React.FC<HouseholdCountCardProps> = ({ data }) => {
  if (!data || data.status !== 'success' || !data.data) {
    return null;
  }

  const apiData = data.data;
  const totalHousehold = apiData.TRDAR_TOT_HSHLD_CO_BF_0 || 0;

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
        가구세대 수
      </h4>

      <div style={{
        display: 'flex',
        justifyContent: 'space-between',
        alignItems: 'center',
        padding: '12px',
        backgroundColor: '#F3F4F6',
        borderRadius: '8px',
      }}>
        <span style={{ fontSize: '14px', color: '#6B7280' }}>총 가구 수</span>
        <span style={{ fontSize: '18px', fontWeight: '600', color: '#374151' }}>
          {totalHousehold.toLocaleString()}세대
        </span>
      </div>
    </div>
  );
};

export default HouseholdCountCard;
