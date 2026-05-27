import React, { useState } from 'react';
import WorkPopulationCard from './WorkPopulationCard';
import WorkPopulationByAge from './WorkPopulationByAge';
import ResidentPopulationByAge from './ResidentPopulationByAge';
import ResidentPopulationCountCard from './ResidentPopulationCountCard';
import FloatingPopulationByHour from './FloatingPopulationByHour';
import FloatingPopulationByWeek from './FloatingPopulationByWeek';
import FloatingPopulationByAge from './FloatingPopulationByAge';
import FloatingPopulationCountCard from './FloatingPopulationCountCard';
import SalesByHour from './SalesByHour';
import SalesByWeek from './SalesByWeek';
import SalesByAge from './SalesByAge';

interface GolmokAnalysisResultProps {
  golmokData: any;
}

const GolmokAnalysisResult: React.FC<GolmokAnalysisResultProps> = ({ golmokData }) => {
  const [isPopulationExpanded, setIsPopulationExpanded] = useState(true);
  const [isSalesExpanded, setIsSalesExpanded] = useState(true);

  // golmokData 구조: {status: 'success', data: {...실제 골목 데이터...}}
  if (!golmokData || golmokData.status !== 'success') {
    return (
      <div style={{
        padding: '20px',
        textAlign: 'center',
        color: '#6B7280',
        backgroundColor: '#F9FAFB',
        borderRadius: '8px',
        marginBottom: '20px'
      }}>
        골목상권 데이터를 불러오는 중...
      </div>
    );
  }

  // 실제 골목 분석 데이터 추출
  const data = golmokData.data || {};

  return (
    <div style={{ paddingBottom: '20px' }}>
      <h3 style={{
        fontSize: '16px',
        fontWeight: 'bold',
        marginBottom: '20px',
        marginTop: '20px',
        color: '#111827',
        paddingBottom: '0'
      }}>
        📊 상권 분석 결과
      </h3>

      {/* 인구 섹션 */}
      <div style={{
        marginBottom: '20px',
        border: '1px solid #E5E7EB',
        borderRadius: '12px',
        overflow: 'hidden',
      }}>
        <div
          onClick={() => setIsPopulationExpanded(!isPopulationExpanded)}
          style={{
            padding: '20px',
            backgroundColor: '#F3F4F6',
            cursor: 'pointer',
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
            userSelect: 'none',
          }}
        >
          <h4 style={{
            fontSize: '18px',
            fontWeight: '600',
            color: '#111827',
            margin: 0,
          }}>
            👥 인구 분석
          </h4>
          <span style={{ fontSize: '20px', color: '#6B7280' }}>
            {isPopulationExpanded ? '▼' : '▶'}
          </span>
        </div>

        {isPopulationExpanded && (
          <div style={{ padding: '20px' }}>
            {/* 1. 유동인구 수 */}
            {data.flpop_co && (
              <FloatingPopulationCountCard data={data.flpop_co} />
            )}

            {/* 2. 성별, 연령별 유동인구 */}
            {data.flpop_sex_age && (
              <FloatingPopulationByAge data={data.flpop_sex_age} />
            )}

            {/* 3. 요일별 유동인구 */}
            {data.flpop_week_co && (
              <FloatingPopulationByWeek data={data.flpop_week_co} />
            )}

            {/* 4. 시간대별 유동인구 */}
            {data.flpop_hour_co && (
              <FloatingPopulationByHour data={data.flpop_hour_co} />
            )}

            {/* 5. 주거인구 수 및 가구세대 수 */}
            {data.repop_dnst_co && (
              <ResidentPopulationCountCard
                data={data.repop_dnst_co}
                householdData={data.apt_hshld_co}
              />
            )}

            {/* 6. 성별, 연령별 거주인구 */}
            {data.repop_sex_age && (
              <ResidentPopulationByAge data={data.repop_sex_age} />
            )}

            {/* 8. 직장인구 수 */}
            {data.wrc_poplt_ha && (
              <WorkPopulationCard data={data.wrc_poplt_ha} />
            )}

            {/* 9. 성별, 연령별 직장인구 */}
            {data.wrc_poplt_sex_age && (
              <WorkPopulationByAge data={data.wrc_poplt_sex_age} />
            )}
          </div>
        )}
      </div>

      {/* 매출 섹션 */}
      <div style={{
        marginBottom: '20px',
        border: '1px solid #E5E7EB',
        borderRadius: '12px',
        overflow: 'hidden',
      }}>
        <div
          onClick={() => setIsSalesExpanded(!isSalesExpanded)}
          style={{
            padding: '20px',
            backgroundColor: '#F3F4F6',
            cursor: 'pointer',
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
            userSelect: 'none',
          }}
        >
          <h4 style={{
            fontSize: '18px',
            fontWeight: '600',
            color: '#111827',
            margin: 0,
          }}>
            💰 매출 분석
          </h4>
          <span style={{ fontSize: '20px', color: '#6B7280' }}>
            {isSalesExpanded ? '▼' : '▶'}
          </span>
        </div>

        {isSalesExpanded && (
          <div style={{ padding: '20px' }}>
            {/* 연령대별 매출 */}
            {data.selng_age && (
              <SalesByAge data={data.selng_age} />
            )}

            {/* 요일별 매출 */}
            {data.selng_week && (
              <SalesByWeek data={data.selng_week} />
            )}

            {/* 시간대별 매출 */}
            {data.selng_hour && (
              <SalesByHour data={data.selng_hour} />
            )}
          </div>
        )}
      </div>
    </div>
  );
};

export default GolmokAnalysisResult;
