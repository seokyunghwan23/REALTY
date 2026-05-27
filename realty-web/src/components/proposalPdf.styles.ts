import React from 'react';

// 색상 테마 정의
export interface ColorTheme {
    name: string;
    main: string;      // 메인 텍스트/헤더
    accent: string;    // 포인트/액센트
    background: string; // 카드 배경
    border: string;    // 테두리
}

export const colorThemes: ColorTheme[] = [
    { name: '0. 원본 (블루그레이)', main: '#405D72', accent: '#758694', background: '#FFF8F3', border: '#F7E7DC' },
    { name: '1. 모던 네이비 + 골드', main: '#1E3A5F', accent: '#C9A962', background: '#F8FAFC', border: '#E2E8F0' },
    { name: '2. 미니멀 차콜', main: '#2C3E50', accent: '#3498DB', background: '#FFFFFF', border: '#E0E0E0' },
    { name: '3. 웜 어스톤', main: '#5D4E37', accent: '#A67B5B', background: '#FAF8F5', border: '#E8DFD5' },
    { name: '4. 클린 틸', main: '#1B4D5C', accent: '#2A9D8F', background: '#F5F9FA', border: '#D1E3E0' },
    { name: '5. 모노크롬', main: '#1A1A1A', accent: '#666666', background: '#FAFAFA', border: '#E5E5E5' },
    { name: '6. 로즈 골드', main: '#8B4557', accent: '#D4A574', background: '#FDF8F8', border: '#F0E0E0' },
    { name: '7. 포레스트 그린', main: '#2D4A3E', accent: '#5D8A66', background: '#F5F8F6', border: '#D4E2D8' },
    { name: '8. 딥 퍼플', main: '#4A3B5C', accent: '#7C5C9E', background: '#F9F8FA', border: '#E5E0EB' },
    { name: '9. 선셋 오렌지', main: '#9C4221', accent: '#DD6B20', background: '#FFFBF7', border: '#FED7AA' },
    { name: '10. 오션 블루', main: '#1E40AF', accent: '#0EA5E9', background: '#F0F9FF', border: '#BAE6FD' },
];

// 모달 스타일
export const modalStyles = {
    overlay: {
        position: 'fixed' as const,
        top: 0,
        left: 0,
        right: 0,
        bottom: 0,
        backgroundColor: 'rgba(0, 0, 0, 0.5)',
        zIndex: 999
    },
    modal: (x: number, y: number): React.CSSProperties => ({
        position: 'fixed',
        top: y,
        left: x,
        width: '1171px',
        height: '888px',
        backgroundColor: 'white',
        borderRadius: '8px',
        boxShadow: '0 4px 19px rgba(0, 0, 0, 0.3)',
        zIndex: 1000,
        display: 'flex',
        flexDirection: 'column',
        overflow: 'hidden'
    }),
    header: (isDragging: boolean): React.CSSProperties => ({
        padding: '11px 15px',
        backgroundColor: '#1E40AF',
        color: 'white',
        fontWeight: 'bold',
        fontSize: '15px',
        cursor: isDragging ? 'grabbing' : 'grab',
        display: 'flex',
        justifyContent: 'space-between',
        alignItems: 'center',
        userSelect: 'none'
    }),
    buttonGroup: {
        display: 'flex',
        gap: '11px'
    } as React.CSSProperties,
    saveButton: {
        padding: '6px 15px',
        fontSize: '13px',
        backgroundColor: '#45a049',
        color: 'white',
        border: 'none',
        borderRadius: '4px',
        cursor: 'pointer'
    } as React.CSSProperties,
    closeButton: {
        padding: '6px 11px',
        fontSize: '13px',
        backgroundColor: '#e53935',
        color: 'white',
        border: 'none',
        borderRadius: '4px',
        cursor: 'pointer'
    } as React.CSSProperties,
    content: {
        flex: 1,
        overflow: 'auto',
        padding: 0,
        backgroundColor: '#e0e0e0',
        display: 'flex',
        justifyContent: 'center',
        alignItems: 'flex-start'
    } as React.CSSProperties,
    iframe: {
        width: '1122px',
        height: '794px',
        border: 'none',
        boxShadow: '0 4px 19px rgba(0,0,0,0.3)',
        backgroundColor: 'white'
    } as React.CSSProperties
};

// PDF 제안서 스타일 (A4 가로: 1122px × 794px)
export const getProposalStyles = (watermarkText: string, theme: ColorTheme = colorThemes[0]) => `
    @page { size: A4 landscape; margin: 0; }
    @media print {
        body { -webkit-print-color-adjust: exact; print-color-adjust: exact; background: white; margin: 0; }
        .page { margin: 0; }
    }
    
    /* 제안서 컨테이너 (기존 body 역할) */
    .proposal-container {
        font-family: 'Malgun Gothic', 'NanumGothic', sans-serif;
        font-size: 10pt;
        color: ${theme.main};
        background: white;
        width: 1122px; /* A4 가로 고정 */
        margin: 0 auto;
    }

    .proposal-container * { margin: 0; padding: 0; box-sizing: border-box; }

    /* 페이지: A4 가로 (1122px × 794px) */
    .page {
        width: 1122px;
        height: 794px;
        background: white;
        position: relative;
        overflow: hidden;
        page-break-after: always;
        margin: 0;
    }
    .page:last-child { page-break-after: avoid; }

    /* 워터마크 */
    .watermark {
        position: absolute; top: 0; left: 0; right: 0; bottom: 0;
        display: flex; flex-wrap: wrap; align-content: center; justify-content: center;
        font-size: 28px; color: rgba(112, 112, 112, 0.2);
        transform: rotate(-30deg); pointer-events: none; z-index: 10;
        overflow: hidden; line-height: 230px;
    }
    .watermark::before {
        content: '${(watermarkText + '                ').repeat(50)}';
        white-space: pre-wrap; word-break: break-all;
    }

    /* 헤더 */
    .header {
        height: 53px;
        text-align: center;
        position: relative;
        z-index: 1;
        margin-top: 20px;
    }
    .title {
        font-size: 18pt;
        font-weight: bold;
        color: ${theme.main};
        display: inline-block;
        padding-bottom: 4px;
        border-bottom: 3px solid ${theme.accent};
    }
    .contact-info {
        position: absolute;
        right: 70px;
        top: 5px;
        display: flex;
        flex-direction: column;
        align-items: flex-start;
        gap: 2px;
        font-size: 10pt;
        font-weight: bold;
        color: ${theme.main};
    }

    /* 콘텐츠 영역 */
    .content {
        display: flex;
        gap: 8px;
        height: 691px;
        position: relative;
        z-index: 1;
        width: 1082px;
        margin: 0 auto;
    }

    /* 왼쪽 영역: 646px */
    .left-section {
        width: 646px;
        display: flex;
        flex-direction: column;
    }

    /* 지도 컨테이너: 450px 높이 */
    .map-container {
        width: 646px;
        height: 460px;
        border: 3px solid ${theme.border};
        border-radius: 6px;
        overflow: hidden;
        background: #f5f5f5;
        margin-bottom: 11px;
    }
    .map-container img {
        width: 100%;
        height: 100%;
        object-fit: cover;
    }
    .map-placeholder, .page-indicator {
        width: 100%;
        height: 100%;
        display: flex;
        align-items: center;
        justify-content: center;
        color: #999;
        font-size: 14pt;
        background: ${theme.background};
    }

    /* 요약 테이블 */
    .summary-table {
        width: 646px;
        border-collapse: separate;
        border-spacing: 0;
        font-size: 9pt;
        border: 3px solid ${theme.border};
        border-radius: 6px;
        overflow: hidden;
    }
    .summary-table th {
        background: ${theme.main};
        color: ${theme.background};
        padding: 8px 4px;
        text-align: center;
        font-weight: normal;
        font-size: 9pt;
    }
    .summary-table td {
        background: ${theme.background};
        color: ${theme.main};
        padding: 7px 4px;
        text-align: center;
        vertical-align: middle;
        border: 1px solid ${theme.border};
        font-size: 9pt;
    }
    .summary-table tr:nth-child(even) td {
        background: white;
    }
    .summary-table .col-num {
        position: relative;
        font-weight: bold;
    }
    .summary-table .col-num .num-circle {
        display: inline-flex;
        align-items: center;
        justify-content: center;
        width: 20px;
        height: 20px;
        background: linear-gradient(135deg, ${theme.main} 0%, ${theme.accent} 100%);
        border-radius: 50%;
        color: white;
        font-size: 8pt;
        font-weight: bold;
        padding-bottom: 2px; /* 시각적 보정 */
    }
    .summary-table .stars {
        color: #FFB800;
        font-size: 7pt;
        position: absolute;
        left: 2px;
        top: 2px;
    }

    /* 오른쪽 영역: 428px */
    .right-section {
        width: 428px;
        height: 710px;
        display: flex;
        flex-direction: column;
        gap: 5px;
    }

    /* 매물 카드: 428px × 132px */
    .property-card {
        width: 428px;
        height: 140px;
        background: ${theme.background};
        border: 3px solid ${theme.border};
        border-radius: 6px;
        display: flex;
        overflow: hidden;
    }

    /* 카드 이미지 (정사각형): 136px × 136px */
    .card-image {
        width: 136px;
        height: 136px;
        background: #eee;
        border-right: 3px solid ${theme.border};
        position: relative;
        flex-shrink: 0;
    }
    .card-number {
        position: absolute;
        top: 5px;
        left: 5px;
        width: 26px;
        height: 26px;
        background: linear-gradient(135deg, ${theme.main} 0%, ${theme.accent} 100%);
        border-radius: 50%;
        display: flex;
        align-items: center;
        justify-content: center;
        font-size: 11pt;
        font-weight: bold;
        color: white;
        box-shadow: 0 2px 4px rgba(0,0,0,0.2);
        padding-bottom: 3px; /* 시각적 보정 */
    }
    .card-stars {
        position: absolute;
        bottom: 3px;
        left: 3px;
        color: #FFB800;
        font-size: 12pt;
        text-shadow: 0 0 3px rgba(0,0,0,0.3);
    }

    /* 카드 내용 */
    .card-content {
        flex: 1;
        padding: 3px 8px;
        display: flex;
        flex-direction: column;
        justify-content: center;
    }
    .card-table {
        width: 100%;
        border-collapse: collapse;
    }
    .card-table td {
        padding: 1px 0;
        font-size: 9pt;
        color: ${theme.main};
        vertical-align: middle;
        line-height: 1.2;
    }
    .card-table .lbl {
        width: 68px;
        font-weight: bold;
    }
`;
