import React from 'react';
import { BarChart, Bar, XAxis, YAxis, CartesianGrid, ResponsiveContainer, Cell } from 'recharts';

interface SalesByAgeProps {
  data: any;
}

const SalesByAge: React.FC<SalesByAgeProps> = ({ data }) => {
  if (!data || data.status !== 'success' || !data.data) {
    return null;
  }

  const apiData = data.data;
  const top1 = apiData.TOP1 || '';
  const topIndustry = apiData.TOP_INDUTY_NM || '외식업';

  // 외식업 데이터
  const outData = [
    { age: '10대', value: apiData.OUT_AGRDE_10_SELNG_RATE || 0 },
    { age: '20대', value: apiData.OUT_AGRDE_20_SELNG_RATE || 0 },
    { age: '30대', value: apiData.OUT_AGRDE_30_SELNG_RATE || 0 },
    { age: '40대', value: apiData.OUT_AGRDE_40_SELNG_RATE || 0 },
    { age: '50대', value: apiData.OUT_AGRDE_50_SELNG_RATE || 0 },
    { age: '60대 이상', value: apiData.OUT_AGRDE_60_ABOVE_SELNG_RATE || 0 },
  ];

  // 최대값 찾기
  const maxValue = Math.max(...outData.map(item => item.value));

  // 커스텀 라벨 (막대 위에 숫자 표시)
  const renderCustomLabel = (props: any) => {
    const { x, y, width, value } = props;
    if (value === 0) return <></>;

    return (
      <text
        x={x + width / 2}
        y={y - 5}
        fill="#374151"
        textAnchor="middle"
        fontSize="11"
        fontWeight="500"
      >
        {value}
      </text>
    );
  };

  return (
    <div style={{
      backgroundColor: 'white',
      borderRadius: '12px',
      padding: '16px',
      marginBottom: '20px',
      boxShadow: '0 1px 3px rgba(0,0,0,0.1)',
      border: '1px solid #E5E7EB',
    }}>
      <h4 style={{
        fontSize: '16px',
        fontWeight: '600',
        marginBottom: '6px',
        color: '#374151',
      }}>
        연령대별 매출
      </h4>

      {top1 && topIndustry && (
        <p style={{
          fontSize: '14px',
          color: '#6B7280',
          marginBottom: '12px',
        }}>
          <strong style={{ color: '#10B981' }}>{topIndustry}의 {top1}</strong> 매출이 높아요.
        </p>
      )}

      <ResponsiveContainer width="100%" height={300}>
          <BarChart data={outData} margin={{ top: 20, right: 10, left: 0, bottom: 5 }}>
            <CartesianGrid strokeDasharray="3 3" stroke="#E5E7EB" vertical={false} />
            <XAxis
              dataKey="age"
              tick={{ fontSize: 11, fill: '#6B7280' }}
              stroke="#9CA3AF"
            />
            <YAxis
              tick={{ fontSize: 11, fill: '#6B7280' }}
              stroke="#9CA3AF"
            />
            <Bar
              dataKey="value"
              radius={[4, 4, 0, 0]}
              label={renderCustomLabel}
              isAnimationActive={false}
            >
              {outData.map((entry, index) => (
                <Cell
                  key={`cell-${index}`}
                  fill={entry.value === maxValue ? '#0EA5E9' : '#9CA3AF'}
                />
              ))}
            </Bar>
          </BarChart>
        </ResponsiveContainer>
    </div>
  );
};

export default SalesByAge;
