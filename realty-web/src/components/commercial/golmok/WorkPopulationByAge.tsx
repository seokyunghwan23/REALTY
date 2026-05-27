import React from 'react';
import { BarChart, Bar, XAxis, YAxis, CartesianGrid, Legend, ResponsiveContainer, Cell } from 'recharts';

interface WorkPopulationByAgeProps {
  data: any;
}

const WorkPopulationByAge: React.FC<WorkPopulationByAgeProps> = ({ data }) => {
  if (!data || data.status !== 'success' || !data.data) {
    return null;
  }

  const apiData = data.data;
  const top1 = apiData.TOP1 || '';

  // 차트 데이터 구성
  const chartData = [
    {
      age: '10대',
      남성: apiData.MAN_10 || 0,
      여성: apiData.WOMAN_10 || 0,
    },
    {
      age: '20대',
      남성: apiData.MAN_20 || 0,
      여성: apiData.WOMAN_20 || 0,
    },
    {
      age: '30대',
      남성: apiData.MAN_30 || 0,
      여성: apiData.WOMAN_30 || 0,
    },
    {
      age: '40대',
      남성: apiData.MAN_40 || 0,
      여성: apiData.WOMAN_40 || 0,
    },
    {
      age: '50대',
      남성: apiData.MAN_50 || 0,
      여성: apiData.WOMAN_50 || 0,
    },
    {
      age: '60대 이상',
      남성: apiData.MAN_60 || 0,
      여성: apiData.WOMAN_60 || 0,
    },
  ];

  // 최대값 찾기 (막대 색상 강조용)
  let maxValue = 0;
  let maxGender = '';
  let maxAge = '';

  chartData.forEach(item => {
    if (item.남성 > maxValue) {
      maxValue = item.남성;
      maxGender = '남성';
      maxAge = item.age;
    }
    if (item.여성 > maxValue) {
      maxValue = item.여성;
      maxGender = '여성';
      maxAge = item.age;
    }
  });

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
        성별·연령별 직장인구
      </h4>

      {top1 && (
        <p style={{
          fontSize: '14px',
          color: '#6B7280',
          marginBottom: '12px',
        }}>
          <strong style={{ color: '#10B981' }}>{top1}</strong> 직장인구가 가장 많아요.
        </p>
      )}

      <ResponsiveContainer width="100%" height={300}>
          <BarChart data={chartData} margin={{ top: 20, right: 10, left: 0, bottom: 5 }}>
            <CartesianGrid strokeDasharray="3 3" stroke="#E5E7EB" vertical={false} />
            <XAxis
              dataKey="age"
              tick={{ fontSize: 12, fill: '#6B7280' }}
              stroke="#9CA3AF"
            />
            <YAxis
              tick={{ fontSize: 11, fill: '#6B7280' }}
              stroke="#9CA3AF"
            />
            <Legend
              wrapperStyle={{ fontSize: '12px' }}
              iconType="rect"
              align="center"
              verticalAlign="top"
            />
            <Bar
              dataKey="남성"
              fill="#3B82F6"
              radius={[4, 4, 0, 0]}
              label={renderCustomLabel}
              isAnimationActive={false}
            />
            <Bar
              dataKey="여성"
              fill="#10B981"
              radius={[4, 4, 0, 0]}
              label={renderCustomLabel}
              isAnimationActive={false}
            />
          </BarChart>
        </ResponsiveContainer>
    </div>
  );
};

export default WorkPopulationByAge;
