import React from 'react';
import { BarChart, Bar, XAxis, YAxis, CartesianGrid, Legend, ResponsiveContainer } from 'recharts';

interface FloatingPopulationByAgeProps {
  data: any;
}

const FloatingPopulationByAge: React.FC<FloatingPopulationByAgeProps> = ({ data }) => {
  if (!data || data.status !== 'success' || !data.data) {
    return null;
  }

  const apiData = data.data;

  // 데이터 변환
  const chartData = [
    { age: '10대', 남성: apiData.MAN_10 || 0, 여성: apiData.WOMAN_10 || 0 },
    { age: '20대', 남성: apiData.MAN_20 || 0, 여성: apiData.WOMAN_20 || 0 },
    { age: '30대', 남성: apiData.MAN_30 || 0, 여성: apiData.WOMAN_30 || 0 },
    { age: '40대', 남성: apiData.MAN_40 || 0, 여성: apiData.WOMAN_40 || 0 },
    { age: '50대', 남성: apiData.MAN_50 || 0, 여성: apiData.WOMAN_50 || 0 },
    { age: '60대+', 남성: apiData.MAN_60 || 0, 여성: apiData.WOMAN_60 || 0 },
  ];

  const top1 = apiData.TOP1 || '';

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
        성별·연령별 유동인구
      </h4>

      {top1 && (
        <p style={{
          fontSize: '14px',
          color: '#6B7280',
          marginBottom: '12px',
        }}>
          <strong style={{ color: '#10B981' }}>{top1}</strong> 유동인구가 가장 많아요.
        </p>
      )}

      <ResponsiveContainer width="100%" height={300}>
        <BarChart
          data={chartData}
          margin={{ top: 5, right: 30, left: 20, bottom: 5 }}
          style={{ outline: 'none' }}
        >
          <CartesianGrid strokeDasharray="3 3" stroke="#E5E7EB" />
          <XAxis
            dataKey="age"
            stroke="#6B7280"
            style={{ fontSize: '12px' }}
          />
          <YAxis
            stroke="#6B7280"
            style={{ fontSize: '12px' }}
          />
          <Legend
            wrapperStyle={{ fontSize: '12px', outline: 'none' }}
            iconType="rect"
            align="center"
            verticalAlign="top"
          />
          <Bar
            dataKey="남성"
            fill="#3B82F6"
            radius={[4, 4, 0, 0]}
            isAnimationActive={false}
            style={{ outline: 'none' }}
          />
          <Bar
            dataKey="여성"
            fill="#EC4899"
            radius={[4, 4, 0, 0]}
            isAnimationActive={false}
            style={{ outline: 'none' }}
          />
        </BarChart>
      </ResponsiveContainer>
    </div>
  );
};

export default FloatingPopulationByAge;
