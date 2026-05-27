import React from 'react';
import { AreaChart, Area, XAxis, YAxis, CartesianGrid, ResponsiveContainer } from 'recharts';

interface SalesByHourProps {
  data: any;
}

const SalesByHour: React.FC<SalesByHourProps> = ({ data }) => {
  if (!data || data.status !== 'success' || !data.data) {
    return null;
  }

  const apiData = data.data;
  const top1 = apiData.TOP1 || '';

  // 차트 데이터 구성
  const chartData = [
    { time: '00~06시', value: apiData.HOUR_0006 || 0 },
    { time: '06~11시', value: apiData.HOUR_0611 || 0 },
    { time: '11~14시', value: apiData.HOUR_1114 || 0 },
    { time: '14~17시', value: apiData.HOUR_1417 || 0 },
    { time: '17~21시', value: apiData.HOUR_1721 || 0 },
    { time: '21~24시', value: apiData.HOUR_2124 || 0 },
  ];

  // 커스텀 라벨 (포인트 위에 숫자 표시)
  const renderCustomLabel = (props: any) => {
    const { x, y, value } = props;
    return (
      <text
        x={x}
        y={y - 10}
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
        시간대별 매출
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

      <ResponsiveContainer width="100%" height={300}>
          <AreaChart data={chartData} margin={{ top: 20, right: 10, left: 0, bottom: 5 }}>
            <defs>
              <linearGradient id="colorSales" x1="0" y1="0" x2="0" y2="1">
                <stop offset="5%" stopColor="#0EA5E9" stopOpacity={0.3}/>
                <stop offset="95%" stopColor="#0EA5E9" stopOpacity={0.05}/>
              </linearGradient>
            </defs>
            <CartesianGrid strokeDasharray="3 3" stroke="#E5E7EB" />
            <XAxis
              dataKey="time"
              tick={{ fontSize: 11, fill: '#6B7280' }}
              stroke="#9CA3AF"
            />
            <YAxis
              tick={{ fontSize: 11, fill: '#6B7280' }}
              stroke="#9CA3AF"
            />
            <Area
              type="monotone"
              dataKey="value"
              stroke="#0EA5E9"
              strokeWidth={2}
              fill="url(#colorSales)"
              dot={{ fill: '#0EA5E9', r: 4 }}
              activeDot={{ r: 6 }}
              label={renderCustomLabel}
              isAnimationActive={false}
            />
          </AreaChart>
        </ResponsiveContainer>
    </div>
  );
};

export default SalesByHour;
