import React from 'react';
import './AnalysisResultModal.css';
import GolmokAnalysisResult from './golmok/GolmokAnalysisResult';

interface AnalysisResultModalProps {
  isOpen: boolean;
  onClose: () => void;
  result: any;
  onDownloadPDF?: () => void;
  mapSnapshot?: string; // 지도 스냅샷 이미지 URL
}

const AnalysisResultModal: React.FC<AnalysisResultModalProps> = ({ isOpen, onClose, result, onDownloadPDF, mapSnapshot }) => {
  if (!isOpen) return null;

  const handleDownloadPDF = async () => {
    if (onDownloadPDF) {
      onDownloadPDF();
    }
  };

  return (
    <div className="modal-overlay">
      <div className="modal-content">
        <div className="modal-header">
          <h2>상권 분석 결과</h2>
          <div style={{ display: 'flex', gap: '10px', alignItems: 'center' }}>
            <button
              onClick={handleDownloadPDF}
              style={{
                padding: '8px 16px',
                backgroundColor: '#2196F3',
                color: 'white',
                border: 'none',
                borderRadius: '4px',
                cursor: 'pointer',
                fontSize: '14px',
                fontWeight: 'bold',
                display: 'flex',
                alignItems: 'center',
                gap: '6px'
              }}
            >
              📥  다운로드
            </button>
            <button className="modal-close" onClick={onClose}>✕</button>
          </div>
        </div>

        <div className="modal-body">
          {/* AI 종합 분석 요약 */}
          {result?.ai_summary && (
            <div style={{
              backgroundColor: '#F0F9FF',
              border: '2px solid #0EA5E9',
              borderRadius: '12px',
              padding: '20px',
              marginBottom: '25px',
            }}>
              <h3 style={{
                fontSize: '18px',
                fontWeight: 'bold',
                marginBottom: '12px',
                color: '#0369A1',
                display: 'flex',
                alignItems: 'center',
                gap: '8px',
              }}>
                <svg width="24" height="24" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg" style={{ flexShrink: 0 }}>
                  <path d="M12 2L14.09 8.26L20 10L14.09 11.74L12 18L9.91 11.74L4 10L9.91 8.26L12 2Z" fill="#0369A1"/>
                  <path d="M19 9L19.74 11.26L22 12L19.74 12.74L19 15L18.26 12.74L16 12L18.26 11.26L19 9Z" fill="#0EA5E9"/>
                  <path d="M19 19L19.74 21.26L22 22L19.74 22.74L19 25L18.26 22.74L16 22L18.26 21.26L19 19Z" fill="#0EA5E9"/>
                </svg>
                AI 상권 분석 요약 (Gemini)
              </h3>
              <p style={{
                fontSize: '15px',
                lineHeight: '1.8',
                color: '#1E3A8A',
                margin: 0,
                whiteSpace: 'pre-wrap',
              }}>
                {result.ai_summary}
              </p>
            </div>
          )}

          {/* 골목상권 분석 결과 */}
          {result?.golmok_analysis && (
            <GolmokAnalysisResult golmokData={result.golmok_analysis} />
          )}

          {/* 유동인구 데이터 (나이스비즈맵) */}
          {/* {result?.floating_population && !result.floating_population.error && (
            <div className="floating-population-section">
              <h3>유동인구 정보</h3>
              <div className="stats-grid">
                <div className="stat-item">
                  <span className="stat-label">총 격자 수</span>
                  <span className="stat-value">{result.floating_population.count || 0}개</span>
                </div>
                <div className="stat-item">
                  <span className="stat-label">평균 유동인구</span>
                  <span className="stat-value">
                    {result.floating_population.flowpopData
                      ? Math.round(
                          result.floating_population.flowpopData.reduce((sum: number, item: any) => sum + (item.flowpopCount || 0), 0) /
                          result.floating_population.flowpopData.length
                        ).toLocaleString()
                      : 0}명
                  </span>
                </div>
                <div className="stat-item">
                  <span className="stat-label">총 유동인구</span>
                  <span className="stat-value">
                    {result.floating_population.flowpopData
                      ? result.floating_population.flowpopData.reduce((sum: number, item: any) => sum + (item.flowpopCount || 0), 0).toLocaleString()
                      : 0}명
                  </span>
                </div>
              </div>

              <h4>등급별 분포</h4>
              <div className="density-distribution">
                {result.floating_population.flowpopData && (() => {
                  const gradeCount: Record<number, number> = {};
                  result.floating_population.flowpopData.forEach((item: any) => {
                    gradeCount[item.grade] = (gradeCount[item.grade] || 0) + 1;
                  });

                  const gradeNames: Record<number, string> = {
                    1: '매우 높음',
                    2: '높음',
                    3: '보통',
                    4: '낮음',
                    5: '매우 낮음'
                  };

                  return Object.entries(gradeCount)
                    .sort(([a], [b]) => Number(a) - Number(b))
                    .map(([grade, count]) => (
                      <div key={grade} className="density-item">
                        <span>{gradeNames[Number(grade)]}</span>
                        <span>{count}개</span>
                      </div>
                    ));
                })()}
              </div>
            </div>
          )} */}
        </div>
      </div>
    </div>
  );
};

export default AnalysisResultModal;
