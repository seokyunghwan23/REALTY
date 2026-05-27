import React from 'react';
import { BarChart, Bar, XAxis, YAxis, CartesianGrid, ResponsiveContainer, Cell } from 'recharts';

interface SalesByWeekProps {
  data: any;
}

const SalesByWeek: React.FC<SalesByWeekProps> = ({ data }) => {
  if (!data || data.status !== 'success' || !data.data) {
    return null;
  }

  const apiData = data.data;
  const top1 = apiData.TOP1 || '';

  // 차트 데이터 구성
  const chartData = [
    { day: '월요일', value: apiData.MON || 0 },
    { day: '화요일', value: apiData.TUE || 0 },
    { day: '수요일', value: apiData.WEN || 0 },
    { day: '목요일', value: apiData.THU || 0 },
    { day: '금요일', value: apiData.FRI || 0 },
    { day: '토요일', value: apiData.SAT || 0 },
    { day: '일요일', value: apiData.SUN || 0 },
  ];

  // 최대값 찾기
  const maxValue = Math.max(...chartData.map(item => item.value));

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
        요일별 매출
      </h4>

      {top1 && (
        <p style={{
          fontSize: '14px',
          color: '#6B7280',
          marginBottom: '12px',
        }}>
          <strong style={{ color: '#10B981' }}>{top1}</strong> 매출이 가장 높아요.
        </p>
      )}

        <ResponsiveContainer width="100%" height={280}>
          <BarChart data={chartData} margin={{ top: 20, right: 10, left: 0, bottom: 5 }}>
            <CartesianGrid strokeDasharray="3 3" stroke="#E5E7EB" vertical={false} />
            <XAxis
              dataKey="day"
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
              {chartData.map((entry, index) => (
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

export default SalesByWeek;
