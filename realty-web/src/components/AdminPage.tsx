import React, { useState, useEffect, useCallback, useRef } from 'react';
import { getApiUrl } from '../config';
import html2canvas from 'html2canvas';
import { getProposalStyles, modalStyles, colorThemes } from './proposalPdf.styles';
import { getEmployeeById, MASTER_APPS_SCRIPT_URL } from '../constants/employeeData';

interface PropertyItem {
    지역: string;
    매물번호: number;
    주소: string;
    세부주소: string;
    상호: string;
    층: number;
    "전체 층": number;
    건축물용도: string;
    사용승인일: number;
    방향: string;
    공급면적: number;
    전용면적: number;
    보증금: number;
    월세: number;
    관리비: number;
    권리금: number;
    주차: number;
    총주차: number;
    화장실: string;
    위반건축물: string;
    특징: string;
    비고: string;
    메모?: string;
    연락처: string; // 호환성을 위해 유지
    임차인연락처?: string;
    임대인연락처?: string;
    소유자: string;
}

// 제안서용 확장 인터페이스 (비고, 별점, 사진, 수정가능 필드 추가)
interface ProposalItem extends PropertyItem {
    제안서비고: string;
    별점: number;
    사진?: string; // base64 이미지 또는 URL
    // 수정 가능한 필드들 (원본과 별도 관리)
    수정_상호?: string;
    수정_층?: number;
    수정_보증금?: number;
    수정_월세?: number;
    수정_관리비?: number;
    수정_권리금?: number;
}

interface AdminPageProps {
    currentUser: string | null;
}

const thStyle: React.CSSProperties = {
    padding: '10px 8px',
    textAlign: 'center',
    fontWeight: 'bold'
};

const tdStyle: React.CSSProperties = {
    padding: '8px',
    textAlign: 'left'
};

const tdCenterStyle: React.CSSProperties = {
    padding: '8px',
    textAlign: 'center'
};

// 금액 포맷팅 함수 (만원 단위 → 억 표시, 만 단위 제거)
const formatMoney = (value: number | undefined | null): string => {
    if (!value || value === 0) {
        return '없음';
    }
    if (value >= 10000) {
        const eok = Math.floor(value / 10000);
        const man = value % 10000;
        return man > 0 ? `${eok}억 ${man.toLocaleString()}` : `${eok}억`;
    }
    return `${value.toLocaleString()}`;
};

const AdminPage: React.FC<AdminPageProps> = ({ currentUser }) => {
    // 전체 매물 목록 (스프레드시트에서 로드)
    const [allProperties, setAllProperties] = useState<PropertyItem[]>([]);
    const [loadingAll, setLoadingAll] = useState(true);

    // 탭 상태
    const [activeTab, setActiveTab] = useState<'all' | 'recommend'>('all');

    // 전체 매물 탭용 (클라이언트 필터링)
    const [searchKeyword, setSearchKeyword] = useState('');
    const [filteredResult, setFilteredResult] = useState<PropertyItem[] | null>(null);

    // 추천 매물 탭용 (서버 추천)
    const [propertyNumber, setPropertyNumber] = useState('');
    const [loading, setLoading] = useState(false);
    const [recommendResult, setRecommendResult] = useState<PropertyItem[] | null>(null);
    const [error, setError] = useState<string | null>(null);

    // 추천 매물 필터 옵션
    const [showFilters, setShowFilters] = useState(false);
    const [filters, setFilters] = useState({
        지역: '',
        minFloor: '', maxFloor: '',
        minArea: '', maxArea: '',
        minDeposit: '', maxDeposit: '',
        minRent: '', maxRent: '',
        minRightMoney: '', maxRightMoney: '',
        특징: ''
    });
    // AI 자연어 검색 프롬프트
    const [aiPrompt, setAiPrompt] = useState('');

    // 사용자 설정 (Apps Script URL, 시트 이름 등)
    const userConfig = currentUser ? getEmployeeById(currentUser) : undefined;
    const APPS_SCRIPT_URL = MASTER_APPS_SCRIPT_URL;
    const SHEET_NAME = userConfig?.sheetName || '사무실';
    const SHEET_ID = userConfig?.sheetId || '';

    // 제안서 모드
    const [showProposal, setShowProposal] = useState(false);
    const [proposalItems, setProposalItems] = useState<ProposalItem[]>([]);

    // 제안서 입력 필드 (localStorage에서 불러오기)
    const [proposalTitle, setProposalTitle] = useState(() => localStorage.getItem('proposal_title') || '');
    const [proposalManager, setProposalManager] = useState(() => localStorage.getItem('proposal_manager') || '');
    const [proposalPhone, setProposalPhone] = useState(() => localStorage.getItem('proposal_phone') || '');

    // 지도 미리보기 (Static Map 이미지)
    const [mapImage, setMapImage] = useState<string | null>(null);
    const [showMap, setShowMap] = useState(false);
    const [mapLoading, setMapLoading] = useState(false);

    // PDF 미리보기 모달
    const [showPreview, setShowPreview] = useState(false);
    const [previewBody, setPreviewBody] = useState(''); // body 내용만 저장
    const previewRef = useRef<HTMLDivElement>(null);

    // 드래그 관련 상태
    const [modalPos, setModalPos] = useState({ x: 100, y: 50 });
    const [isDragging, setIsDragging] = useState(false);
    const [dragOffset, setDragOffset] = useState({ x: 0, y: 0 });

    // 색상 테마
    // const [selectedThemeIndex, setSelectedThemeIndex] = useState(0); <- Removed duplicate

    // 색상 테마
    const [selectedThemeIndex, setSelectedThemeIndex] = useState(0);
    const [previewImage, setPreviewImage] = useState<string | null>(null); // 모바일용 미리보기 이미지

    // [모바일 반응형 State]
    const [isMobile, setIsMobile] = useState(window.innerWidth <= 1200);
    const [mobileProposalOpen, setMobileProposalOpen] = useState(false);

    useEffect(() => {
        const handleResize = () => setIsMobile(window.innerWidth <= 1200);
        window.addEventListener('resize', handleResize);
        return () => window.removeEventListener('resize', handleResize);
    }, []);

    // 드래그 이벤트 핸들러
    const handleMouseDown = (e: React.MouseEvent) => {
        setIsDragging(true);
        setDragOffset({
            x: e.clientX - modalPos.x,
            y: e.clientY - modalPos.y
        });
    };

    const handleMouseMove = useCallback((e: MouseEvent) => {
        if (isDragging) {
            setModalPos({
                x: e.clientX - dragOffset.x,
                y: e.clientY - dragOffset.y
            });
        }
    }, [isDragging, dragOffset]);

    const handleMouseUp = useCallback(() => {
        setIsDragging(false);
    }, []);

    // 드래그 이벤트 리스너 등록
    useEffect(() => {
        if (isDragging) {
            window.addEventListener('mousemove', handleMouseMove);
            window.addEventListener('mouseup', handleMouseUp);
        }
        return () => {
            window.removeEventListener('mousemove', handleMouseMove);
            window.removeEventListener('mouseup', handleMouseUp);
        };
    }, [isDragging, handleMouseMove, handleMouseUp]);

    // 페이지 로드 시 전체 매물 로드
    useEffect(() => {
        const loadAllProperties = async () => {
            try {
                setLoadingAll(true);
                
                console.log('[DEBUG] 로그인 사용자:', currentUser);
                console.log('[DEBUG] 설정된 SHEET_ID:', SHEET_ID);
                
                // sheetId가 없으면 매물 조회하지 않음
                if (!SHEET_ID) {
                    console.warn('[WARNING] SHEET_ID가 없습니다! 연결된 시트가 없습니다.');
                    setAllProperties([]);
                    setFilteredResult([]);
                    setLoadingAll(false);
                    return;
                }
                
                const apiUrl = getApiUrl();
                // const apiUrl = 'http://localhost:8089'; // 서버 포트 변경 테스트 (8081 -> 8089)
                
                // sheetId와 sheetName을 쿼리스트링으로 전달
                let url = `${apiUrl}/api/items/properties`;
                const params = new URLSearchParams();
                
                params.append('sheetId', SHEET_ID);
                params.append('sheetName', SHEET_NAME || '사무실');
                
                const fullUrl = `${url}?${params.toString()}`;
                console.log('[DEBUG] 최종 요청 URL:', fullUrl);

                const response = await fetch(fullUrl);
                const data = await response.json();

                if (response.ok && data.items) {
                    // 유효한 매물만 필터링 (주소가 필수)
                    const validItems = data.items.filter((item: PropertyItem) => 
                        item['주소'] && String(item['주소']).trim() !== ''
                    );
                    setAllProperties(validItems);
                    setFilteredResult(validItems);
                    console.log(`전체 매물 ${data.count}개 중 유효 매물 ${validItems.length}개 로드 완료`);
                } else {
                    console.error('매물 로드 실패:', data.error);
                }
            } catch (err) {
                console.error('매물 로드 실패:', err);
            } finally {
                setLoadingAll(false);
            }
        };

        loadAllProperties();
    }, [SHEET_ID, SHEET_NAME]); // Add SHEET_ID, SHEET_NAME to dependency array

    // 지도 이미지 생성 함수
    const fetchMapImage = useCallback(async (items: PropertyItem[]) => {
        if (items.length === 0) {
            setMapImage(null);
            setShowMap(false);
            return;
        }

        setMapLoading(true);

        try {
            const apiUrl = getApiUrl();
            const addresses = items.map(item => item.주소);

            const response = await fetch(`${apiUrl}/api/items/static-map`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify({ addresses }),
            });

            const data = await response.json();
            console.log('Static Map 응답:', data);

            if (response.ok && data.image) {
                setMapImage(data.image);
                setShowMap(true);
            } else {
                console.error(data.error || 'Static Map 생성 실패');
                setMapImage(null);
            }
        } catch (err) {
            console.error('Static Map 생성 실패:', err);
            setMapImage(null);
        } finally {
            setMapLoading(false);
        }
    }, []);

    // proposalItems가 비워지면 지도도 초기화 (자동 생성은 하지 않음 - PDF 생성 시에만)
    useEffect(() => {
        if (proposalItems.length === 0) {
            setMapImage(null);
            setShowMap(false);
        }
    }, [proposalItems]);

    // 입력값 변경 시 localStorage 저장
    const handleTitleChange = (value: string) => {
        setProposalTitle(value);
        localStorage.setItem('proposal_title', value);
    };
    const handleManagerChange = (value: string) => {
        setProposalManager(value);
        localStorage.setItem('proposal_manager', value);
    };
    const handlePhoneChange = (value: string) => {
        setProposalPhone(value);
        localStorage.setItem('proposal_phone', value);
    };

    // 지도 새로고침
    const handleRefreshMap = () => {
        if (proposalItems.length === 0) {
            alert('제안서에 매물을 추가해주세요.');
            return;
        }
        fetchMapImage(proposalItems);
    };

    // 금액 포맷 (PDF용 - 만원 단위 유지)
    const formatMoneyPdf = (val: number | undefined | null): string => {
        if (val === undefined || val === null || val === 0) return '-';
        if (val >= 10000) return `${(val / 10000).toFixed(val % 10000 === 0 ? 0 : 1)}억`;
        return `${val.toLocaleString()}만원`;
    };

    // 금액 포맷 (테이블용 - 단위 제거)
    const formatMoneyTable = (val: number | undefined | null): string => {
        if (val === undefined || val === null || val === 0) return '-';
        if (val >= 10000) return `${(val / 10000).toFixed(val % 10000 === 0 ? 0 : 1)}억`;
        return val.toLocaleString();
    };

    // PDF 생성 (새 창에서 인쇄)
    const handleGeneratePDF = async () => {
        if (proposalItems.length === 0) {
            alert('제안서에 매물을 추가해주세요.');
            return;
        }

        // 지도 이미지가 없으면 먼저 생성
        let currentMapImage = mapImage;
        if (!currentMapImage) {
            try {
                const apiUrl = getApiUrl();
                // const apiUrl = 'http://localhost:8089'; // 서버 포트 변경 테스트 (8081 -> 8089)
                const addresses = proposalItems.map(item => item.주소);

                const response = await fetch(`${apiUrl}/api/items/static-map`, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ addresses }),
                });

                const data = await response.json();
                if (response.ok && data.image) {
                    currentMapImage = data.image;
                    setMapImage(data.image);
                    setShowMap(true);
                }
            } catch (err) {
                console.error('지도 이미지 생성 실패:', err);
            }
        }

        // 페이지 분할 (5개씩)
        const maxCardsPerPage = 5;
        const pages: ProposalItem[][] = [];
        for (let i = 0; i < proposalItems.length; i += maxCardsPerPage) {
            pages.push(proposalItems.slice(i, i + maxCardsPerPage));
        }

        // 별점 생성 함수
        const renderStars = (count: number) => {
            if (count <= 0) return '';
            return `<span class="stars">${'★'.repeat(count)}</span>`;
        };

        // 수정된 값 또는 원본 값 가져오기 (PDF용)
        const getPdfValue = (item: ProposalItem, field: '상호' | '층' | '보증금' | '월세' | '관리비' | '권리금') => {
            const editField = `수정_${field}` as keyof ProposalItem;
            return item[editField] !== undefined ? item[editField] : item[field];
        };

        // 대체 이미지 경로
        const placeholderImage = '/images/대체.jpg';

        // HTML 생성
        const pagesHtml = pages.map((pageItems, pageIdx) => {
            const startIdx = pageIdx * maxCardsPerPage;

            // 요약 테이블 행
            const tableRows = pageItems.map((item, idx) => `
                <tr>
                    <td class="col-num">${renderStars(item.별점)}<span class="num-circle">${startIdx + idx + 1}</span></td>
                    <td>${getPdfValue(item, '상호') || '-'}</td>
                    <td>${getPdfValue(item, '층') || '-'}층</td>
                    <td>${item.전용면적 || '-'}평</td>
                    <td>${formatMoneyPdf(getPdfValue(item, '보증금') as number)}</td>
                    <td>${formatMoneyPdf(getPdfValue(item, '월세') as number)}</td>
                    <td>${formatMoneyPdf(getPdfValue(item, '관리비') as number)}</td>
                    <td>${formatMoneyPdf(getPdfValue(item, '권리금') as number)}</td>
                </tr>
            `).join('');

            // 매물 카드 (테이블 구조, 8줄)
            const cardsHtml = pageItems.map((item, idx) => {
                const 보증금 = getPdfValue(item, '보증금') as number;
                const 월세 = getPdfValue(item, '월세') as number;
                const 관리비 = getPdfValue(item, '관리비') as number;
                const 권리금 = getPdfValue(item, '권리금') as number;
                const 상호 = getPdfValue(item, '상호') as string;
                const 층 = getPdfValue(item, '층') as number;
                const imageUrl = item.사진 || placeholderImage;

                return `
                <div class="property-card">
                    <div class="card-image" style="background: url('${imageUrl}') center/cover no-repeat;">
                        <div class="card-number">${startIdx + idx + 1}</div>
                        ${item.별점 > 0 ? `<div class="card-stars">${'★'.repeat(item.별점)}</div>` : ''}
                    </div>
                    <div class="card-content">
                        <table class="card-table">
                            <tr><td class="lbl">주소</td><td class="val">${item.주소 || '-'}</td></tr>
                            <tr><td class="lbl">상호</td><td class="val">${상호 || '-'}</td></tr>
                            <tr><td class="lbl">층/면적</td><td class="val">${층 || '-'}층 / ${item.전용면적 || '-'}평</td></tr>
                            <tr><td class="lbl">보증금</td><td class="val">${formatMoneyPdf(보증금)}</td></tr>
                            <tr><td class="lbl">임대료</td><td class="val">${formatMoneyPdf(월세)}</td></tr>
                            <tr><td class="lbl">관리비</td><td class="val">${formatMoneyPdf(관리비)}</td></tr>
                            <tr><td class="lbl">권리금</td><td class="val">${권리금 && 권리금 > 0 ? formatMoneyPdf(권리금) : '무권리'}</td></tr>
                            <tr><td class="lbl">비고</td><td class="val">${item.제안서비고 || '-'}</td></tr>
                        </table>
                    </div>
                </div>
            `}).join('');

            // 왼쪽 영역 (모든 페이지에 지도 표시)
            const leftContent = currentMapImage
                ? `<img src="data:image/png;base64,${currentMapImage}" alt="지도" style="width:100%;height:100%;object-fit:cover;"/>`
                : `<div class="map-placeholder">지도 이미지 없음</div>`;

            return `
                <div class="page">
                    <div class="header">
                        <div class="title">${proposalTitle || '매물 제안서'}</div>
                        <div class="contact-info">
                            <div>담당자 : ${proposalManager || '-'}</div>
                            <div>연락처 : ${proposalPhone || '-'}</div>
                        </div>
                    </div>
                    <div class="content">
                        <div class="left-section">
                            <div class="map-container">${leftContent}</div>
                            <table class="summary-table">
                                <thead>
                                    <tr>
                                        <th style="width:50px;">구분</th>
                                        <th style="width:90px;">상호</th>
                                        <th style="width:30px;">층</th>
                                        <th style="width:55px;">전용면적</th>
                                        <th style="width:55px;">보증금</th>
                                        <th style="width:55px;">임대료</th>
                                        <th style="width:50px;">관리비</th>
                                        <th style="width:55px;">권리금</th>
                                    </tr>
                                </thead>
                                <tbody>${tableRows}</tbody>
                            </table>
                        </div>
                        <div class="right-section">${cardsHtml}</div>
                    </div>
                    <div class="watermark">${proposalManager || ''}</div>
                </div>
            `;
        }).join('');

        // 미리보기 모달 표시 - 이미지 선 생성 방식 (PC/Mobile 통합)
        setPreviewBody(pagesHtml);

        // 보이지 않는 div에 렌더링 후 이미지 캡처
        const container = document.createElement('div');
        container.style.position = 'absolute';
        container.style.left = '-9999px';
        container.style.top = '0';
        container.style.width = '1122px'; // A4 width
        container.innerHTML = `
            <style>${getProposalStyles(proposalManager || '', colorThemes[selectedThemeIndex])}</style>
            <div class="proposal-container">${pagesHtml}</div>
        `;
        document.body.appendChild(container);

        try {
            // 이미지 생성 비동기 처리
            html2canvas(container, {
                scale: 2, // 고해상도
                useCORS: true,
                logging: false,
                width: 1122,
                windowWidth: 1122
            }).then(canvas => {
                setPreviewImage(canvas.toDataURL('image/png'));
                document.body.removeChild(container);
                // 이미지 생성 완료 후 미리보기 표시
                setShowPreview(true);
                // 화면 중앙 정렬
                const modalWidth = 1171;
                const modalHeight = 888;
                const calculateCenter = () => ({
                    x: Math.max((window.innerWidth - modalWidth) / 2, 20),
                    y: Math.max((window.innerHeight - modalHeight) / 2, 20)
                });
                setModalPos(calculateCenter());
            }).catch(err => {
                console.error('이미지 생성 실패:', err);
                document.body.removeChild(container);
                alert('이미지 생성 중 오류가 발생했습니다.');
            });
        } catch (error) {
            console.error('이미지 생성 실패:', error);
            document.body.removeChild(container);
        }
    };

    // PDF 저장 (이미지 다운로드로 통합)
    const handleSavePDF = async () => {
        if (!previewImage) return;
        
        const link = document.createElement("a");
        link.href = previewImage;
        link.download = `${proposalTitle || '매물제안서'}.png`;
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
    };

    // 미리보기 닫기
    const handleClosePreview = () => {
        setShowPreview(false);
        setPreviewBody('');
    };

    // 전체 매물 탭에서 검색 (클라이언트 필터링)
    const handleSearch = () => {
        if (!searchKeyword.trim()) {
            setFilteredResult(null);
            return;
        }

        const keyword = searchKeyword.trim().toLowerCase();
        const found = allProperties.filter(item =>
            String(item.매물번호).includes(keyword) ||
            (item.지역 && item.지역.toLowerCase().includes(keyword)) ||
            (item.주소 && item.주소.toLowerCase().includes(keyword)) ||
            (item.상호 && item.상호.toLowerCase().includes(keyword)) ||
            (item.특징 && item.특징.toLowerCase().includes(keyword)) ||
            (item.메모 && item.메모.toLowerCase().includes(keyword)) ||
            (item.비고 && item.비고.toLowerCase().includes(keyword))
        );

        setFilteredResult(found.length > 0 ? found : []);
    };

    // 검색 초기화
    const handleClearSearch = () => {
        setSearchKeyword('');
        setFilteredResult(null);
    };

    // 추천 매물 조회 (Apps Script 직접 호출)
    const handleRecommend = async () => {
        setLoading(true);
        setRecommendResult(null);
        setFilteredResult(null);
        setError(null);

        if (!APPS_SCRIPT_URL) {
            alert('사용자의 Apps Script URL 설정이 없습니다. 관리자에게 문의하세요.');
            setLoading(false);
            return;
        }

        try {
            // 1. 유효성 검사: 기준 매물번호가 없으면 필터나 AI 프롬프트라도 있어야 함
            if (!propertyNumber.trim() && !Object.values(filters).some(v => v.trim()) && !aiPrompt.trim()) {
                alert('기준 매물번호, 검색 필터, 또는 AI 요청 메시지 중 하나 이상을 입력해주세요.');
                setLoading(false);
                return;
            }

            // 2. 대상 매물 찾기 (없을 수도 있음)
            const targetProperty = propertyNumber.trim() 
                ? allProperties.find((p: any) => String(p['매물번호']) === propertyNumber.trim()) 
                : null;

            if (propertyNumber.trim() && !targetProperty) {
                alert('입력한 기준 매물번호에 해당하는 매물을 찾을 수 없습니다.');
                setLoading(false);
                return;
            }

            // 3. 필터 조건 문자열 생성
            const filterParts: string[] = [];
            
            if (filters.지역.trim()) filterParts.push(`지역: ${filters.지역.trim()}`);
            if (filters.특징.trim()) filterParts.push(`특징/키워드: ${filters.특징.trim()}`);

            const addRangeFilter = (label: string, min: string, max: string) => {
                if (min.trim() && max.trim()) {
                    if (min.trim() === max.trim()) filterParts.push(`${label}: 정확히 ${min.trim()}`);
                    else filterParts.push(`${label}: ${min.trim()} ~ ${max.trim()}`);
                } else if (min.trim()) {
                    filterParts.push(`${label}: ${min.trim()} 이상`);
                } else if (max.trim()) {
                    filterParts.push(`${label}: ${max.trim()} 이하`);
                }
            };

            addRangeFilter('층', filters.minFloor, filters.maxFloor);
            addRangeFilter('전용면적', filters.minArea, filters.maxArea);
            addRangeFilter('보증금', filters.minDeposit, filters.maxDeposit);
            addRangeFilter('월세', filters.minRent, filters.maxRent);
            addRangeFilter('권리금', filters.minRightMoney, filters.maxRightMoney);

            const filterString = filterParts.length > 0 ? filterParts.join(', ') : '없음';

            // 4. 대상 매물 정보 문자열 생성 (조건부)
            let targetInfo = "[기준 매물 정보]\n없음 (사용자 필터 기반 검색)";
            if (targetProperty) {
                targetInfo = `
[기준 매물 정보]
- 매물번호: ${targetProperty['매물번호']}
- 지역: ${targetProperty['지역'] || '미지정'}
- 주소: ${targetProperty['주소'] || '미지정'}
- 층: ${targetProperty['층'] || 0}층
- 전용면적: ${targetProperty['전용면적'] || 0}평
- 보증금: ${targetProperty['보증금'] || 0}만원
- 월세: ${targetProperty['월세'] || 0}만원
- 권리금: ${targetProperty['권리금'] || 0}만원
- 특징: ${targetProperty['특징'] || '없음'}`;
            }

            // 5. 전체 프롬프트 생성
            const fullPrompt = `
당신은 부동산 전문 데이터 분석 에이전트입니다. 
제공된 도구 'search_properties'를 사용하여 데이터베이스(시트)에서 조건에 맞는 매물을 조회하십시오.

${targetInfo}

[사용자 검색 필터]
${filterString}

[사용자 요청 메시지 (최우선 반영)]
"${aiPrompt}"

[수행 가이드]
1. 사용자가 입력한 [사용자 요청 메시지]가 있다면 이를 최우선으로 분석하여 검색 필터로 변환하십시오.
2. [사용자 검색 필터]에 값이 있다면 이는 보조 조건으로 활용하십시오. (단, 사용자 요청 메시지와 충돌 시 요청 메시지 우선)
3. **중요: 사용자가 입력한 숫자(범위, 금액, 층수 등)는 절대로 임의로 변경하거나 범위를 넓히지 말고 입력된 값 그대로 적용하십시오.**
   - 단위 변환 금지: 모든 면적은 '평', 금액은 '만원' 단위이므로 별도 환산 없이 숫자 그대로 사용하십시오.
   - 예: "월세 200" -> maxRent: 200 (210 등으로 임의 조정 절대 금지)
4. 기준 매물만 있고 필터가 없는 경우에 한해서만, 기준 매물의 ±10% 범위를 적용하여 유사 매물을 찾으십시오.
5. 반드시 'search_properties' 도구를 호출하여 결과를 얻어야 합니다.
6. 사용자 요청 메시지만 입력된 경우, 필터는 적용하지 않습니다.

7. [지역(location) 유연 처리 규칙]
   - '역', '동', '구', '시' 등의 행정 접미사는 **제거하고 핵심 지명만 추출**하십시오. (단순 포함 검색을 위해 짧은 단어가 유리함)
   - 예: "사당역" -> location: "사당" (사당동, 사당역 모두 검색됨)
   - 예: "서초동" -> location: "서초"
   - 예: "논현초등학교" -> location: "논현초"
   - 예: "사당이랑 이수" -> location: "사당, 이수"

8. [키워드(keywords) 유연 확장 규칙]
   - 사용자가 입력한 특징 키워드와 **유사한 의미(유의어)나 연관된 표현**이 있다면 확장해서 포함하십시오.
   - 단, '상가', '사무실', '매물' 등 일반 명사는 여전히 제외합니다.
   - 'keywords' 파라미터에는 구체적인 고유명사나 식별 가능한 특징만 포함해야 합니다.
   - 예: "테라스" -> keywords: "테라스, 베란다, 옥상, 루프탑, 단독사용"
   - 예: "카페 자리" -> keywords: "카페, 노출, 층고, 에폭시, 커피"
   - 예: "병원" -> keywords: "병원, 의원, 메디컬, 클리닉"
   - 예: "방배경찰서" -> keywords: "방배경찰서"
  
9. 보증금이 Null이거나 '' 공백인 매물은 결과에서 제외하세요.
`;

            console.log('========== 생성된 프롬프트 ==========');
            console.log(fullPrompt);
            console.log('=====================================');

            const requestBody = {
                targetId: propertyNumber.trim(),
                mode: 'recommend',
                fullPrompt: fullPrompt,
                sheetId: SHEET_ID,
                sheetName: SHEET_NAME
            };

            console.log('Apps Script 요청:', requestBody);

            const response = await fetch(APPS_SCRIPT_URL, {
                method: 'POST',
                body: JSON.stringify(requestBody),
            });

            const data = await response.json();
            console.log('Apps Script 응답:', data);

            if (data.status === 'error') {
                setError(data.message || '알 수 없는 오류');
                setRecommendResult([]); // 에러 시 결과 초기화
            } else if (data.status === 'success') {
                // 성공 시 data.data에 배열이 들어있음
                const result = Array.isArray(data.data) ? data.data : [];
                setRecommendResult(result);
                setActiveTab('recommend'); // 결과 탭으로 자동 전환
                
                if (data.ai_message) {
                    console.log("AI 추천 사유:", data.ai_message);
                    // 필요 시 여기에 alert이나 toast로 ai_message를 보여줄 수 있음
                }
            } else {
                // 예기치 않은 응답 형식일 경우의 폴백
                 if (data.error) {
                    setError(data.error);
                 } else {
                     // 기존처럼 배열로 왔을 경우 (혹시 모를 호환성)
                     const result = Array.isArray(data) ? data : [];
                     setRecommendResult(result);
                 }
            }
        } catch (err) {
            console.error('Apps Script 호출 실패:', err);
            setError('Apps Script 호출 실패: ' + (err instanceof Error ? err.message : '알 수 없는 오류'));
        } finally {
            setLoading(false);
        }
    };

    // 필터 값 변경
    const handleFilterChange = (key: string, value: string) => {
        setFilters(prev => ({ ...prev, [key]: value }));
    };

    // 필터 초기화
    const handleClearFilters = () => {
        setFilters({
            지역: '',
            minFloor: '', maxFloor: '',
            minArea: '', maxArea: '',
            minDeposit: '', maxDeposit: '',
            minRent: '', maxRent: '',
            minRightMoney: '', maxRightMoney: '',
            특징: ''
        });
    };

    // 제안서 만들기 버튼 클릭
    const handleOpenProposal = () => {
        setShowProposal(true);
    };

    // 현재 탭에서 표시할 매물 목록
    const displayItems = activeTab === 'all'
        ? (filteredResult !== null ? filteredResult : allProperties)
        : (recommendResult || []);

    // 현재 상태 라벨
    const getStatusLabel = () => {
        if (activeTab === 'all') {
            if (filteredResult !== null) {
                return `검색 결과 ${filteredResult.length}개`;
            }
            return `전체 매물 ${allProperties.length}개`;
        } else {
            if (recommendResult) {
                return `추천 매물 ${recommendResult.length}개`;
            }
            return '추천 매물 조회를 실행하세요';
        }
    };

    // 제안서 닫기
    const handleCloseProposal = () => {
        setShowProposal(false);
    };

    // 원본 테이블에서 제안서로 추가
    const handleAddToProposal = (item: PropertyItem) => {
        // 이미 추가된 항목인지 확인
        const exists = proposalItems.some(p => 
            p.주소 === item.주소 && 
            p.상호 === item.상호 && 
            p.층 === item.층 &&
            p.전용면적 === item.전용면적 &&
            p.보증금 === item.보증금 &&
            p.월세 === item.월세
        );
        if (!exists) {
            const proposalItem: ProposalItem = {
                ...item,
                제안서비고: '',
                별점: 0
            };
            setProposalItems([...proposalItems, proposalItem]);
        }
    };

    // 제안서에서 항목 제거
    const handleRemoveFromProposal = (index: number) => {
        setProposalItems(proposalItems.filter((_, i) => i !== index));
    };

    // 제안서 비고 업데이트
    const handleProposalNoteChange = (index: number, value: string) => {
        const updated = [...proposalItems];
        updated[index] = { ...updated[index], 제안서비고: value };
        setProposalItems(updated);
    };

    // 제안서 별점 업데이트
    const handleProposalStarChange = (index: number, value: number) => {
        const updated = [...proposalItems];
        updated[index] = { ...updated[index], 별점: value };
        setProposalItems(updated);
    };

    // 제안서 필드 업데이트 (수정 가능 필드들)
    const handleProposalFieldChange = (index: number, field: keyof ProposalItem, value: string | number) => {
        const updated = [...proposalItems];
        updated[index] = { ...updated[index], [field]: value };
        setProposalItems(updated);
    };

    // 제안서 사진 업로드
    const handleProposalPhotoUpload = (index: number, event: React.ChangeEvent<HTMLInputElement>) => {
        const file = event.target.files?.[0];
        if (file) {
            const reader = new FileReader();
            reader.onloadend = () => {
                const updated = [...proposalItems];
                updated[index] = { ...updated[index], 사진: reader.result as string };
                setProposalItems(updated);
            };
            reader.readAsDataURL(file);
        }
    };

    // 제안서 사진 삭제
    const handleProposalPhotoRemove = (index: number) => {
        const updated = [...proposalItems];
        updated[index] = { ...updated[index], 사진: undefined };
        setProposalItems(updated);
    };

    // 수정값 또는 원본값 가져오기
    const getEditableValue = (item: ProposalItem, field: '상호' | '층' | '보증금' | '월세' | '관리비' | '권리금') => {
        const editField = `수정_${field}` as keyof ProposalItem;
        return item[editField] !== undefined ? item[editField] : item[field];
    };

    // 탭 스타일
    const tabStyle = (isActive: boolean): React.CSSProperties => ({
        padding: '12px 24px',
        fontSize: '14px',
        fontWeight: isActive ? 'bold' : 'normal',
        backgroundColor: isActive ? '#4a90d9' : '#e0e0e0',
        color: isActive ? 'white' : '#333',
        border: 'none',
        borderRadius: '8px 8px 0 0',
        cursor: 'pointer',
        marginRight: '4px'
    });

    return (
        <div style={{ padding: '20px', display: 'flex', flexDirection: isMobile ? 'column' : 'row', gap: '20px' }}>
            {/* 왼쪽: 매물 조회 영역 */}
            <div style={{ flex: (!isMobile && showProposal) ? 1 : 'auto', minWidth: 0, width: '100%' }}>
                {/* 탭 헤더 */}
                <div style={{ display: 'flex', alignItems: 'center', marginBottom: '0' }}>
                    <button
                        onClick={() => setActiveTab('all')}
                        style={tabStyle(activeTab === 'all')}
                    >
                        전체 매물
                    </button>
                    <button
                        onClick={() => setActiveTab('recommend')}
                        style={{
                            ...tabStyle(activeTab === 'recommend'),
                            backgroundColor: activeTab === 'recommend' ? '#ff9800' : '#e0e0e0'
                        }}
                    >
                        추천 매물 {recommendResult && `(${recommendResult.length})`}
                    </button>

                    <div style={{ marginLeft: 'auto', display: 'flex', alignItems: 'center', gap: '10px' }}>
                        <span style={{ fontSize: '13px', color: '#666' }}>
                            {getStatusLabel()}
                        </span>
                        {!showProposal && (
                            <button
                                onClick={handleOpenProposal}
                                style={{
                                    padding: '10px 16px',
                                    fontSize: '14px',
                                    backgroundColor: '#45a049',
                                    color: 'white',
                                    border: 'none',
                                    borderRadius: '4px',
                                    cursor: 'pointer'
                                }}
                            >
                                제안서 만들기
                            </button>
                        )}
                    </div>
                </div>

                {/* 탭 컨텐츠 영역 */}
                <div style={{
                    border: '1px solid #ddd',
                    borderRadius: '0 8px 8px 8px',
                    padding: '15px',
                    backgroundColor: '#fafafa'
                }}>
                    {/* 전체 매물 탭 */}
                    {activeTab === 'all' && (
                        <div style={{
                            display: 'flex',
                            gap: '10px',
                            alignItems: 'center',
                            marginBottom: '15px',
                            flexWrap: 'wrap'
                        }}>
                            <input
                                type="text"
                                value={searchKeyword}
                                onChange={(e) => setSearchKeyword(e.target.value)}
                                placeholder="검색 (매물번호, 지역, 주소, 상호, 특징, 비고)"
                                style={{
                                    padding: '10px 15px',
                                    fontSize: '14px',
                                    border: '1px solid #ccc',
                                    borderRadius: '4px',
                                    width: '300px'
                                }}
                                onKeyPress={(e) => e.key === 'Enter' && handleSearch()}
                            />
                            <button
                                onClick={handleSearch}
                                style={{
                                    padding: '10px 16px',
                                    fontSize: '14px',
                                    backgroundColor: '#4a90d9',
                                    color: 'white',
                                    border: 'none',
                                    borderRadius: '4px',
                                    cursor: 'pointer'
                                }}
                            >
                                검색
                            </button>
                            {filteredResult !== null && (
                                <button
                                    onClick={handleClearSearch}
                                    style={{
                                        padding: '10px 16px',
                                        fontSize: '14px',
                                        backgroundColor: '#757575',
                                        color: 'white',
                                        border: 'none',
                                        borderRadius: '4px',
                                        cursor: 'pointer'
                                    }}
                                >
                                    검색 초기화
                                </button>
                            )}
                        </div>
                    )}

                    {/* 추천 매물 탭 */}
                    {activeTab === 'recommend' && (
                        <div style={{ marginBottom: '15px' }}>
                            {/* 필터 옵션 패널 (항상 표시) */}
                            <div style={{
                                marginTop: '10px',
                                padding: '10px',
                                backgroundColor: '#f9f9f9',
                                borderRadius: '8px',
                                border: '1px solid #eee',
                                maxWidth: '350px',
                                width: '100%',
                                boxSizing: 'border-box'
                            }}>
                                <style>{`
                                    input[type=number]::-webkit-inner-spin-button, 
                                    input[type=number]::-webkit-outer-spin-button { 
                                        -webkit-appearance: none;
                                        margin: 0; 
                                    }
                                    input[type=number] {
                                        -moz-appearance: textfield;
                                    }
                                    .filter-panel input {
                                        box-sizing: border-box;
                                    }
                                `}</style>
                                
                                {/* 0. 기준 매물번호 (최상단) */}
                                <div style={{ marginBottom: '10px' }}>
                                    <label style={{ fontSize: '11px', fontWeight: 'bold', marginBottom: '3px', color: '#333', display: 'block' }}>기준 매물번호</label>
                                    <input
                                        type="text"
                                        value={propertyNumber}
                                        onChange={(e) => setPropertyNumber(e.target.value)}
                                        placeholder="예: 12345"
                                        onKeyDown={(e) => {
                                            if (e.key === 'Enter') handleRecommend();
                                        }}
                                        style={{
                                            width: '100%',
                                            padding: '8px',
                                            fontSize: '13px',
                                            border: '1px solid #ccc',
                                            borderRadius: '4px',
                                            marginBottom: '5px',
                                            boxSizing: 'border-box'
                                        }}
                                    />
                                </div>

                                {/* 0-1. AI 자연어 입력 (신규 추가) */}
                                <div style={{ marginBottom: '15px' }}>
                                    <label style={{ fontSize: '11px', fontWeight: 'bold', marginBottom: '3px', color: '#6d4c41', display: 'block' }}>
                                        AI에게 요청하기
                                    </label>
                                    <textarea
                                        value={aiPrompt}
                                        onChange={(e) => setAiPrompt(e.target.value)}
                                        placeholder="예: 사당이나 이수쪽 30평대 사무실 찾아줘. 월세는 200 아래로."
                                        style={{
                                            width: '100%',
                                            padding: '8px',
                                            fontSize: '13px',
                                            border: '1px solid #d7ccc8',
                                            borderRadius: '4px',
                                            boxSizing: 'border-box',
                                            minHeight: '60px',
                                            resize: 'vertical',
                                            fontFamily: 'inherit'
                                        }}
                                    />
                                </div>

                                <div className="filter-panel" style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '6px 10px', boxSizing: 'border-box' }}>
                                    {/* Row 1 Left: 지역 */}
                                    <div style={{ display: 'flex', flexDirection: 'column' }}>
                                        <label style={{ fontSize: '11px', fontWeight: 'bold', marginBottom: '3px', color: '#555' }}>지역</label>
                                        <input
                                            type="text"
                                            value={filters.지역}
                                            onChange={(e) => handleFilterChange('지역', e.target.value)}
                                            placeholder="예: 사당,이수"
                                            style={{ width: '100%', boxSizing: 'border-box', padding: '6px', fontSize: '12px', border: '1px solid #ddd', borderRadius: '4px' }}
                                        />
                                    </div>
                                    {/* Row 1 Right: 빈칸 */}
                                    <div></div>

                                    {/* Row 2 Left: 층 */}
                                    <div style={{ display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
                                        <label style={{ fontSize: '11px', fontWeight: 'bold', marginBottom: '3px', color: '#555' }}>층</label>
                                        <div style={{ display: 'flex', alignItems: 'center', gap: '3px' }}>
                                            <input
                                                type="number"
                                                value={filters.minFloor}
                                                onChange={(e) => handleFilterChange('minFloor', e.target.value)}
                                                placeholder="최소"
                                                style={{ width: '100%', boxSizing: 'border-box', minWidth: 0, padding: '6px', fontSize: '12px', border: '1px solid #ddd', borderRadius: '4px' }}
                                            />
                                            <span style={{ fontSize: '11px', color: '#888' }}>~</span>
                                            <input
                                                type="number"
                                                value={filters.maxFloor}
                                                onChange={(e) => handleFilterChange('maxFloor', e.target.value)}
                                                placeholder="최대"
                                                style={{ width: '100%', boxSizing: 'border-box', minWidth: 0, padding: '6px', fontSize: '12px', border: '1px solid #ddd', borderRadius: '4px' }}
                                            />
                                        </div>
                                    </div>
                                    {/* Row 2 Right: 면적 */}
                                    <div style={{ display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
                                        <label style={{ fontSize: '11px', fontWeight: 'bold', marginBottom: '3px', color: '#555' }}>면적(평)</label>
                                        <div style={{ display: 'flex', alignItems: 'center', gap: '3px' }}>
                                            <input
                                                type="number"
                                                value={filters.minArea}
                                                onChange={(e) => handleFilterChange('minArea', e.target.value)}
                                                placeholder="최소"
                                                style={{ width: '100%', boxSizing: 'border-box', minWidth: 0, padding: '6px', fontSize: '12px', border: '1px solid #ddd', borderRadius: '4px' }}
                                            />
                                            <span style={{ fontSize: '11px', color: '#888' }}>~</span>
                                            <input
                                                type="number"
                                                value={filters.maxArea}
                                                onChange={(e) => handleFilterChange('maxArea', e.target.value)}
                                                placeholder="최대"
                                                style={{ width: '100%', boxSizing: 'border-box', minWidth: 0, padding: '6px', fontSize: '12px', border: '1px solid #ddd', borderRadius: '4px' }}
                                            />
                                        </div>
                                    </div>

                                    {/* Row 3 Left: 보증금 */}
                                    <div style={{ display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
                                        <label style={{ fontSize: '11px', fontWeight: 'bold', marginBottom: '3px', color: '#555' }}>보증금(만)</label>
                                        <div style={{ display: 'flex', alignItems: 'center', gap: '3px' }}>
                                            <input
                                                type="number"
                                                value={filters.minDeposit}
                                                onChange={(e) => handleFilterChange('minDeposit', e.target.value)}
                                                placeholder="최소"
                                                style={{ width: '100%', boxSizing: 'border-box', minWidth: 0, padding: '6px', fontSize: '12px', border: '1px solid #ddd', borderRadius: '4px' }}
                                            />
                                            <span style={{ fontSize: '11px', color: '#888' }}>~</span>
                                            <input
                                                type="number"
                                                value={filters.maxDeposit}
                                                onChange={(e) => handleFilterChange('maxDeposit', e.target.value)}
                                                placeholder="최대"
                                                style={{ width: '100%', boxSizing: 'border-box', minWidth: 0, padding: '6px', fontSize: '12px', border: '1px solid #ddd', borderRadius: '4px' }}
                                            />
                                        </div>
                                    </div>
                                    {/* Row 3 Right: 월세 */}
                                    <div style={{ display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
                                        <label style={{ fontSize: '11px', fontWeight: 'bold', marginBottom: '3px', color: '#555' }}>월세(만)</label>
                                        <div style={{ display: 'flex', alignItems: 'center', gap: '3px' }}>
                                            <input
                                                type="number"
                                                value={filters.minRent}
                                                onChange={(e) => handleFilterChange('minRent', e.target.value)}
                                                placeholder="최소"
                                                style={{ width: '100%', boxSizing: 'border-box', minWidth: 0, padding: '6px', fontSize: '12px', border: '1px solid #ddd', borderRadius: '4px' }}
                                            />
                                            <span style={{ fontSize: '11px', color: '#888' }}>~</span>
                                            <input
                                                type="number"
                                                value={filters.maxRent}
                                                onChange={(e) => handleFilterChange('maxRent', e.target.value)}
                                                placeholder="최대"
                                                style={{ width: '100%', boxSizing: 'border-box', minWidth: 0, padding: '6px', fontSize: '12px', border: '1px solid #ddd', borderRadius: '4px' }}
                                            />
                                        </div>
                                    </div>

                                    {/* Row 4 Left: 권리금 */}
                                    <div style={{ display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
                                        <label style={{ fontSize: '11px', fontWeight: 'bold', marginBottom: '3px', color: '#555' }}>권리금(만)</label>
                                        <div style={{ display: 'flex', alignItems: 'center', gap: '3px' }}>
                                            <input
                                                type="number"
                                                value={filters.minRightMoney}
                                                onChange={(e) => handleFilterChange('minRightMoney', e.target.value)}
                                                placeholder="최소"
                                                style={{ width: '100%', boxSizing: 'border-box', flex: 1, minWidth: 0, padding: '6px', fontSize: '12px', border: '1px solid #ddd', borderRadius: '4px' }}
                                            />
                                            <span style={{ fontSize: '11px', color: '#888' }}>~</span>
                                            <input
                                                type="number"
                                                value={filters.maxRightMoney}
                                                onChange={(e) => handleFilterChange('maxRightMoney', e.target.value)}
                                                placeholder="최대"
                                                style={{ width: '100%', boxSizing: 'border-box', flex: 1, minWidth: 0, padding: '6px', fontSize: '12px', border: '1px solid #ddd', borderRadius: '4px' }}
                                            />
                                        </div>
                                    </div>
                                    {/* Row 4 Right: 빈칸 */}
                                    <div></div>

                                    {/* Row 5: 특징 (전체 너비) */}
                                    <div style={{ display: 'flex', flexDirection: 'column', gridColumn: '1 / -1' }}>
                                        <label style={{ fontSize: '11px', fontWeight: 'bold', marginBottom: '3px', color: '#555' }}>특징 / 키워드</label>
                                        <input
                                            type="text"
                                            value={filters.특징}
                                            onChange={(e) => handleFilterChange('특징', e.target.value)}
                                            placeholder="예: 신축, 대로변"
                                            style={{ width: '100%', boxSizing: 'border-box', padding: '6px', fontSize: '12px', border: '1px solid #ddd', borderRadius: '4px' }}
                                        />
                                    </div>
                                </div>        
                                
                                {/* 하단 버튼 영역 */}
                                <div style={{ marginTop: '15px', display: 'flex', gap: '10px', alignItems: 'center' }}>
                                    <button
                                        onClick={handleRecommend}
                                        disabled={loading}
                                        style={{
                                            flex: 1,
                                            padding: '10px',
                                            fontSize: '14px',
                                            fontWeight: 'bold',
                                            backgroundColor: loading ? '#ccc' : '#ff9800',
                                            color: 'white',
                                            border: 'none',
                                            borderRadius: '4px',
                                            cursor: loading ? 'not-allowed' : 'pointer'
                                        }}
                                    >
                                        {loading ? '분석 중...' : '추천매물 조회'}
                                    </button>
                                    <button 
                                        onClick={handleClearFilters}
                                        style={{
                                            padding: '10px',
                                            backgroundColor: '#f0f0f0',
                                            border: '1px solid #ccc',
                                            borderRadius: '4px',
                                            cursor: 'pointer',
                                            fontSize: '12px',
                                            color: '#555',
                                            whiteSpace: 'nowrap'
                                        }}
                                    >
                                        초기화
                                    </button>
                                </div>
                            </div>
                        </div>
                    )}

                    {/* 에러 표시 */}
                    {error && (
                    <div style={{
                        marginTop: '20px',
                        padding: '15px',
                        background: '#ffebee',
                        color: '#c62828',
                        borderRadius: '8px'
                    }}>
                        {error}
                    </div>
                )}

                    {/* 테이블 영역 */}
                    {(activeTab === 'all' && loadingAll) ? (
                        <div style={{
                            padding: '40px',
                            textAlign: 'center',
                            color: '#666'
                        }}>
                            매물 목록 로딩 중...
                        </div>
                    ) : (activeTab === 'recommend' && loading) ? (
                        <div style={{
                            padding: '40px',
                            textAlign: 'center',
                            color: '#666'
                        }}>
                            추천 매물 조회 중...
                        </div>
                    ) : (activeTab === 'recommend' && !recommendResult) ? (
                        <div style={{
                            padding: '40px',
                            textAlign: 'center',
                            color: '#999',
                            background: '#f5f5f5',
                            borderRadius: '8px'
                        }}>
                            기준 매물번호를 입력하고 "추천매물 조회" 버튼을 눌러주세요.
                        </div>
                    ) : displayItems.length > 0 ? (
                        <div style={{ overflowX: 'auto' }}>
                            <table style={{
                                width: '100%',
                                minWidth: '1400px',
                                borderCollapse: 'collapse',
                                fontSize: '12px',
                                background: 'white',
                                tableLayout: 'auto'
                            }}>
                                <thead>
                                    <tr style={{ background: activeTab === 'recommend' ? '#ff9800' : '#4a90d9', color: 'white' }}>
                                    {showProposal && <th style={thStyle}></th>}
                                    <th style={thStyle}>지역</th>
                                    <th style={thStyle}>매물번호</th>
                                    <th style={{ ...thStyle, width: '180px' }}>주소</th>
                                    <th style={{ ...thStyle, width: '180px' }}>상호</th>
                                    <th style={thStyle}>층</th>
                                    <th style={thStyle}>전용면적</th>
                                    <th style={thStyle}>보증금</th>
                                    <th style={thStyle}>월세</th>
                                    <th style={thStyle}>관리비</th>
                                    <th style={thStyle}>권리금</th>
                                    <th style={{ ...thStyle, width: '300px' }}>비고</th>
                                    <th style={{ ...thStyle, width: '200px' }}>임차인연락처</th>
                                    <th style={{ ...thStyle, width: '200px' }}>임대인연락처</th>
                                </tr>
                            </thead>
                            <tbody>
                                {displayItems.map((item, index) => {
                                    const isInProposal = proposalItems.some(p => 
                                        p.주소 === item.주소 && 
                                        p.상호 === item.상호 && 
                                        p.층 === item.층 &&
                                        p.전용면적 === item.전용면적 &&
                                        p.보증금 === item.보증금 &&
                                        p.월세 === item.월세
                                    );
                                    return (
                                        <tr key={index} style={{
                                            borderBottom: '1px solid #ddd',
                                            backgroundColor: isInProposal ? '#e8f5e9' : 'transparent'
                                        }}>
                                            {showProposal && (
                                                <td style={tdCenterStyle}>
                                                    <button
                                                        onClick={() => handleAddToProposal(item)}
                                                        disabled={isInProposal}
                                                        style={{
                                                            width: '28px',
                                                            height: '28px',
                                                            borderRadius: '50%',
                                                            border: 'none',
                                                            backgroundColor: isInProposal ? '#ccc' : '#45a049',
                                                            color: 'white',
                                                            fontSize: '18px',
                                                            cursor: isInProposal ? 'not-allowed' : 'pointer',
                                                            display: 'flex',
                                                            alignItems: 'center',
                                                            justifyContent: 'center'
                                                        }}
                                                    >
                                                        +
                                                    </button>
                                                </td>
                                            )}
                                            <td style={tdCenterStyle}>{item.지역}</td>
                                            <td style={tdCenterStyle}>{item.매물번호}</td>
                                            <td style={{ ...tdStyle, maxWidth: '180px', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }} title={item.주소}>{item.주소}</td>
                                            <td style={tdCenterStyle}>{item.상호}</td>
                                            <td style={tdCenterStyle}>{item.층}</td>
                                            <td style={tdCenterStyle}>{item.전용면적}</td>
                                            <td style={tdCenterStyle}>{formatMoney(item.보증금)}</td>
                                            <td style={tdCenterStyle}>{formatMoney(item.월세)}</td>
                                            <td style={tdCenterStyle}>{formatMoney(item.관리비)}</td>
                                            <td style={tdCenterStyle}>{formatMoney(item.권리금)}</td>
                                            <td style={{ ...tdStyle, maxWidth: '300px', whiteSpace: 'normal', wordBreak: 'break-word' }}>{item.메모 || item.비고}</td>
                                            <td style={{ ...tdCenterStyle, maxWidth: '200px', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{item.임차인연락처 || item.연락처 || '-'}</td>
                                            <td style={{ ...tdCenterStyle, maxWidth: '200px', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{item.임대인연락처 || '-'}</td>
                                        </tr>
                                    );
                                })}
                            </tbody>
                        </table>
                    </div>
                ) : (
                    <div style={{
                        padding: '40px',
                        textAlign: 'center',
                        color: '#999',
                        background: '#f5f5f5',
                        borderRadius: '8px'
                    }}>
                        {!SHEET_ID ? '연결된 시트가 없습니다.' : '매물 데이터가 없습니다.'}
                    </div>
                    )}
                </div>
            </div>

            {/* 오른쪽: 제안서 작성 패널 */}
            {showProposal && (
                <>
                    {/* 모바일용 플로팅 버튼 (FAB) */}
                    {isMobile && !mobileProposalOpen && (
                        <button
                            onClick={() => setMobileProposalOpen(true)}
                            style={{
                                position: 'fixed',
                                bottom: '120px',
                                right: '80px',
                                width: '150px',
                                height: '150px',
                                borderRadius: '40px',
                                backgroundColor: '#45a049',
                                color: 'white',
                                border: 'none',
                                boxShadow: '0 4px 12px rgba(0,0,0,0.3)',
                                zIndex: 999,
                                display: 'flex',
                                flexDirection: 'column',
                                alignItems: 'center',
                                justifyContent: 'center',
                                cursor: 'pointer'
                            }}
                        >
                            <span style={{ fontSize: '50px' }}>📝</span>
                            <span style={{ fontSize: '30px', fontWeight: 'bold' }}>{proposalItems.length}</span>
                        </button>
                    )}

                    {/* 제안서 패널 컨테이너 */}
                    <div style={{
                        flex: (!isMobile && showProposal) ? 1.2 : 'none',
                        minWidth: isMobile ? '0' : '430px',
                        width: isMobile ? '100%' : 'auto',
                        backgroundColor: '#f8f9fa', // 부드러운 배경색
                        borderLeft: isMobile ? 'none' : '2px solid #ddd',
                        padding: isMobile ? '0' : '0 0 0 20px', // 모바일은 패딩 제거(헤더가 꽉 차게)
                        display: 'flex',
                        flexDirection: 'column',
                        position: isMobile ? 'fixed' : 'static',
                        top: 0, 
                        left: 0,
                        height: isMobile ? '100%' : 'auto',
                        zIndex: isMobile ? 2000 : 'auto',
                        visibility: isMobile ? (mobileProposalOpen ? 'visible' : 'hidden') : 'visible',
                        overflowY: isMobile ? 'auto' : 'visible',
                        // 모바일 오버레이 스타일 강화
                        boxShadow: isMobile ? '0 0 25px rgba(0,0,0,0.2)' : 'none',
                    }}>
                        {/* 모바일용 헤더 (App Bar 스타일) */}
                        {isMobile && (
                            <div style={{ 
                                display: 'flex', 
                                justifyContent: 'space-between', 
                                alignItems: 'center', 
                                marginBottom: '15px',
                                background: 'linear-gradient(90deg, #45a049 0%, #2e7d32 100%)', // 그라데이션 헤더
                                padding: '15px 20px',
                                color: 'white',
                                boxShadow: '0 2px 5px rgba(0,0,0,0.1)'
                            }}>
                                <h3 style={{ margin: 0, fontSize: '18px', fontWeight: '600' }}>📄 제안서 ({proposalItems.length})</h3>
                                <div style={{ display: 'flex', gap: '10px' }}>
                                    <button 
                                        onClick={handleGeneratePDF}
                                        style={{ 
                                            padding: '6px 12px', 
                                            background: 'rgba(255,255,255,0.2)', 
                                            color: 'white', 
                                            border: '1px solid rgba(255,255,255,0.4)', 
                                            borderRadius: '20px', 
                                            fontSize: '13px',
                                            cursor: 'pointer' 
                                        }}
                                    >
                                        PDF 생성
                                    </button>
                                    <button 
                                        onClick={() => setMobileProposalOpen(false)}
                                        style={{ 
                                            padding: '6px 12px', 
                                            background: 'white', 
                                            color: '#2e7d32', 
                                            fontWeight: 'bold',
                                            border: 'none', 
                                            borderRadius: '20px', 
                                            fontSize: '13px', 
                                            cursor: 'pointer' 
                                        }}
                                    >
                                        닫기
                                    </button>
                                </div>
                            </div>
                        )}

                    {/* 데스크탑용 헤더 */}
                    {!isMobile && (
                        <div style={{
                            display: 'flex',
                        justifyContent: 'space-between',
                        alignItems: 'center',
                        marginBottom: '15px'
                    }}>
                        <h3 style={{ margin: 0, color: '#333' }}>제안서 매물 목록</h3>
                        <div style={{ display: 'flex', gap: '10px' }}>
                            <button
                                onClick={handleGeneratePDF}
                                style={{
                                    padding: '8px 16px',
                                    fontSize: '14px',
                                    backgroundColor: '#4a90d9',
                                    color: 'white',
                                    border: 'none',
                                    borderRadius: '4px',
                                    cursor: 'pointer'
                                }}
                            >
                                PDF 생성
                            </button>
                            <button
                                onClick={handleCloseProposal}
                                style={{
                                    padding: '8px 16px',
                                    fontSize: '14px',
                                    backgroundColor: '#757575',
                                    color: 'white',
                                    border: 'none',
                                    borderRadius: '4px',
                                    cursor: 'pointer'
                                }}
                            >
                                닫기
                            </button>
                        </div>
                    </div>
                )}

                    {/* 제안서 입력 필드 */}
                    <div style={{
                        display: 'flex',
                        gap: '15px',
                        marginBottom: '15px',
                        flexWrap: 'wrap'
                    }}>
                        <div>
                            <label style={{ fontSize: '12px', color: '#666' }}>제목</label>
                            <input
                                type="text"
                                placeholder="제안서 제목"
                                value={proposalTitle}
                                onChange={(e) => handleTitleChange(e.target.value)}
                                style={{
                                    display: 'block',
                                    padding: '8px 12px',
                                    fontSize: '14px',
                                    border: '1px solid #ccc',
                                    borderRadius: '4px',
                                    width: '150px',
                                    marginTop: '4px'
                                }}
                            />
                        </div>
                        <div>
                            <label style={{ fontSize: '12px', color: '#666' }}>담당자</label>
                            <input
                                type="text"
                                placeholder="담당자 이름"
                                value={proposalManager}
                                onChange={(e) => handleManagerChange(e.target.value)}
                                style={{
                                    display: 'block',
                                    padding: '8px 12px',
                                    fontSize: '14px',
                                    border: '1px solid #ccc',
                                    borderRadius: '4px',
                                    width: '160px',
                                    marginTop: '4px'
                                }}
                            />
                        </div>
                        <div>
                            <label style={{ fontSize: '12px', color: '#666' }}>연락처</label>
                            <input
                                type="text"
                                placeholder="연락처"
                                value={proposalPhone}
                                onChange={(e) => handlePhoneChange(e.target.value)}
                                style={{
                                    display: 'block',
                                    padding: '8px 12px',
                                    fontSize: '14px',
                                    border: '1px solid #ccc',
                                    borderRadius: '4px',
                                    width: '130px',
                                    marginTop: '4px'
                                }}
                            />
                        </div>
                    </div>

                    {/* 제안서 테이블 */}
                    {proposalItems.length > 0 ? (
                        <div style={{ overflowX: 'auto' }}>
                            <table style={{
                                width: '100%',
                                borderCollapse: 'collapse',
                                fontSize: '12px',
                                background: 'white'
                            }}>
                                <thead>
                                    <tr style={{ background: '#45a049', color: 'white', fontSize: '13px' }}>
                                        <th style={{...thStyle, width: '30px'}}></th>
                                        <th style={{...thStyle, width: '50px'}}>별점</th>
                                        <th style={{...thStyle, width: '35px'}}>순번</th>
                                        <th style={{...thStyle, width: '70px'}}>사진</th>
                                        <th style={{...thStyle, minWidth: '120px'}}>주소</th>
                                        <th style={{...thStyle, width: '120px'}}>상호</th>
                                        <th style={{...thStyle, width: '45px'}}>층</th>
                                        <th style={{...thStyle, width: '70px'}}>보증금</th>
                                        <th style={{...thStyle, width: '60px'}}>월세</th>
                                        <th style={{...thStyle, width: '60px'}}>관리비</th>
                                        <th style={{...thStyle, width: '70px'}}>권리금</th>
                                        <th style={{...thStyle, minWidth: '100px'}}>비고</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    {proposalItems.map((item, index) => (
                                        <tr key={index} style={{ borderBottom: '1px solid #ddd' }}>
                                            {/* 삭제 버튼 */}
                                            <td style={{...tdCenterStyle, padding: '10px 8px'}}>
                                                <button
                                                    onClick={() => handleRemoveFromProposal(index)}
                                                    style={{
                                                        width: '24px',
                                                        height: '24px',
                                                        borderRadius: '50%',
                                                        border: 'none',
                                                        backgroundColor: '#e53935',
                                                        color: 'white',
                                                        fontSize: '16px',
                                                        cursor: 'pointer',
                                                        display: 'flex',
                                                        alignItems: 'center',
                                                        justifyContent: 'center'
                                                    }}
                                                >
                                                    −
                                                </button>
                                            </td>
                                            {/* 별점 */}
                                            <td style={{...tdCenterStyle, padding: '10px 8px'}}>
                                                <select
                                                    value={item.별점}
                                                    onChange={(e) => handleProposalStarChange(index, Number(e.target.value))}
                                                    style={{
                                                        padding: '5px',
                                                        fontSize: '13px',
                                                        border: '1px solid #ccc',
                                                        borderRadius: '4px',
                                                        width: '50px'
                                                    }}
                                                >
                                                    <option value={0}>-</option>
                                                    <option value={1}>★</option>
                                                    <option value={2}>★★</option>
                                                    <option value={3}>★★★</option>
                                                </select>
                                            </td>
                                            {/* 순번 */}
                                            <td style={{...tdCenterStyle, padding: '10px 8px', fontSize: '13px'}}>{index + 1}</td>
                                            {/* 사진 */}
                                            <td style={{...tdCenterStyle, padding: '10px 8px'}}>
                                                <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '4px' }}>
                                                    {item.사진 ? (
                                                        <>
                                                            <img
                                                                src={item.사진}
                                                                alt="매물사진"
                                                                style={{
                                                                    width: '50px',
                                                                    height: '50px',
                                                                    objectFit: 'cover',
                                                                    borderRadius: '4px',
                                                                    border: '1px solid #ddd'
                                                                }}
                                                            />
                                                            <button
                                                                onClick={() => handleProposalPhotoRemove(index)}
                                                                style={{
                                                                    padding: '2px 6px',
                                                                    fontSize: '10px',
                                                                    backgroundColor: '#ff6b6b',
                                                                    color: 'white',
                                                                    border: 'none',
                                                                    borderRadius: '3px',
                                                                    cursor: 'pointer'
                                                                }}
                                                            >
                                                                삭제
                                                            </button>
                                                        </>
                                                    ) : (
                                                        <label style={{
                                                            display: 'flex',
                                                            flexDirection: 'column',
                                                            alignItems: 'center',
                                                            cursor: 'pointer',
                                                            padding: '8px',
                                                            border: '1px dashed #ccc',
                                                            borderRadius: '4px',
                                                            backgroundColor: '#f9f9f9'
                                                        }}>
                                                            <span style={{ fontSize: '18px' }}>📷</span>
                                                            <span style={{ fontSize: '9px', color: '#666' }}>추가</span>
                                                            <input
                                                                type="file"
                                                                accept="image/*"
                                                                onChange={(e) => handleProposalPhotoUpload(index, e)}
                                                                style={{ display: 'none' }}
                                                            />
                                                        </label>
                                                    )}
                                                </div>
                                            </td>
                                            {/* 주소 */}
                                            <td style={{...tdStyle, padding: '10px 8px', fontSize: '13px'}}>
                                                {item.주소 || '-'}
                                            </td>
                                            {/* 상호 */}
                                            <td style={{...tdCenterStyle, padding: '10px 8px'}}>
                                                <input
                                                    type="text"
                                                    value={getEditableValue(item, '상호') as string}
                                                    onChange={(e) => handleProposalFieldChange(index, '수정_상호', e.target.value)}
                                                    style={{
                                                        padding: '6px',
                                                        fontSize: '13px',
                                                        border: '1px solid #ccc',
                                                        borderRadius: '4px',
                                                        width: '100%',
                                                        minWidth: '50px',
                                                        textAlign: 'center'
                                                    }}
                                                />
                                            </td>
                                            {/* 층 */}
                                            <td style={{...tdCenterStyle, padding: '10px 8px'}}>
                                                <input
                                                    type="text"
                                                    value={getEditableValue(item, '층') as number}
                                                    onChange={(e) => handleProposalFieldChange(index, '수정_층', Number(e.target.value) || 0)}
                                                    style={{
                                                        padding: '6px',
                                                        fontSize: '13px',
                                                        border: '1px solid #ccc',
                                                        borderRadius: '4px',
                                                        width: '40px',
                                                        textAlign: 'center'
                                                    }}
                                                />
                                            </td>
                                            {/* 보증금 */}
                                            <td style={{...tdCenterStyle, padding: '10px 8px'}}>
                                                <input
                                                    type="text"
                                                    value={getEditableValue(item, '보증금') as number}
                                                    onChange={(e) => handleProposalFieldChange(index, '수정_보증금', Number(e.target.value) || 0)}
                                                    placeholder="만원"
                                                    style={{
                                                        padding: '6px',
                                                        fontSize: '13px',
                                                        border: '1px solid #ccc',
                                                        borderRadius: '4px',
                                                        width: '60px',
                                                        textAlign: 'center'
                                                    }}
                                                />
                                            </td>
                                            {/* 월세 */}
                                            <td style={{...tdCenterStyle, padding: '10px 8px'}}>
                                                <input
                                                    type="text"
                                                    value={getEditableValue(item, '월세') as number}
                                                    onChange={(e) => handleProposalFieldChange(index, '수정_월세', Number(e.target.value) || 0)}
                                                    placeholder="만원"
                                                    style={{
                                                        padding: '6px',
                                                        fontSize: '13px',
                                                        border: '1px solid #ccc',
                                                        borderRadius: '4px',
                                                        width: '55px',
                                                        textAlign: 'center'
                                                    }}
                                                />
                                            </td>
                                            {/* 관리비 */}
                                            <td style={{...tdCenterStyle, padding: '10px 8px'}}>
                                                <input
                                                    type="text"
                                                    value={getEditableValue(item, '관리비') as number}
                                                    onChange={(e) => handleProposalFieldChange(index, '수정_관리비', Number(e.target.value) || 0)}
                                                    placeholder="만원"
                                                    style={{
                                                        padding: '6px',
                                                        fontSize: '13px',
                                                        border: '1px solid #ccc',
                                                        borderRadius: '4px',
                                                        width: '55px',
                                                        textAlign: 'center'
                                                    }}
                                                />
                                            </td>
                                            {/* 권리금 */}
                                            <td style={{...tdCenterStyle, padding: '10px 8px'}}>
                                                <input
                                                    type="text"
                                                    value={getEditableValue(item, '권리금') as number}
                                                    onChange={(e) => handleProposalFieldChange(index, '수정_권리금', Number(e.target.value) || 0)}
                                                    placeholder="만원"
                                                    style={{
                                                        padding: '6px',
                                                        fontSize: '13px',
                                                        border: '1px solid #ccc',
                                                        borderRadius: '4px',
                                                        width: '60px',
                                                        textAlign: 'center'
                                                    }}
                                                />
                                            </td>
                                            <td style={{...tdCenterStyle, padding: '10px 8px'}}>
                                                <input
                                                    type="text"
                                                    value={item.제안서비고}
                                                    onChange={(e) => handleProposalNoteChange(index, e.target.value)}
                                                    placeholder="비고 입력"
                                                    style={{
                                                        padding: '6px',
                                                        fontSize: '13px',
                                                        border: '1px solid #ccc',
                                                        borderRadius: '4px',
                                                        width: '100%',
                                                        minWidth: '80px',
                                                        textAlign: 'center'
                                                    }}
                                                />
                                            </td>
                                        </tr>
                                    ))}
                                </tbody>
                            </table>
                        </div>
                    ) : (
                        <div style={{
                            padding: '40px',
                            textAlign: 'center',
                            color: '#999',
                            background: '#f5f5f5',
                            borderRadius: '8px'
                        }}>
                            왼쪽 테이블에서 + 버튼을 눌러 매물을 추가하세요
                        </div>
                    )}

                    <div style={{
                        marginTop: '15px',
                        fontSize: '13px',
                        color: '#666'
                    }}>
                        총 {proposalItems.length}개 매물 선택됨
                    </div>
                </div>
                </>
            )}

            {/* PDF 미리보기 모달 */}
            {showPreview && (
                <>
                    <div style={modalStyles.overlay} onClick={handleClosePreview} />
                    <div style={{
                        ...modalStyles.modal(modalPos.x, modalPos.y),
                        ...(isMobile ? {
                            position: 'fixed',
                            zIndex: 9999, // 제안서 목록(2000)보다 위로
                            top: '50%',
                            left: '50%',
                            transform: 'translate(-50%, -50%)',
                            width: '95vw',
                            height: '90vh',
                            maxWidth: 'none',
                            maxHeight: 'none',
                            overflow: 'auto', // 내부 스크롤 활성화
                            WebkitOverflowScrolling: 'touch'
                        } : {})
                    }}>
                        <div onMouseDown={isMobile ? undefined : handleMouseDown} style={modalStyles.header(isDragging)}>
                            <span>PDF 미리보기</span>
                            <div style={modalStyles.buttonGroup}>
                                <select
                                    value={selectedThemeIndex}
                                    onChange={(e) => setSelectedThemeIndex(Number(e.target.value))}
                                    onMouseDown={(e) => e.stopPropagation()}
                                    style={{
                                        padding: '5px 8px',
                                        fontSize: '12px',
                                        borderRadius: '4px',
                                        border: 'none',
                                        cursor: 'pointer'
                                    }}
                                >
                                    {colorThemes.map((theme, idx) => (
                                        <option key={idx} value={idx}>{theme.name}</option>
                                    ))}
                                </select>
                                <button onClick={handleSavePDF} style={modalStyles.saveButton}>
                                    {isMobile ? '이미지 저장' : '인쇄 / PDF 저장'}
                                </button>
                                <button onClick={handleClosePreview} style={modalStyles.closeButton}>닫기</button>
                            </div>
                        </div>
                        <div style={modalStyles.content}>
                            {previewImage ? (
                                <div style={{ 
                                    width: '100%', 
                                    height: '100%', 
                                    overflow: 'auto', 
                                    display: 'flex', 
                                    justifyContent: 'center', 
                                    alignItems: 'center',
                                    background: '#525659'
                                }}>
                                    <img 
                                        src={previewImage} 
                                        alt="미리보기" 
                                        style={{ 
                                            maxWidth: '95%', 
                                            maxHeight: 'none', 
                                            boxShadow: '0 0 10px rgba(0,0,0,0.5)' 
                                        }} 
                                    />
                                </div>
                            ) : (
                                <div style={{ color: 'white', marginTop: '50px' }}>이미지 생성 중...</div>
                            )}
                        </div>
                    </div>
                </>
            )}
        </div>
    );
};

export default AdminPage;
