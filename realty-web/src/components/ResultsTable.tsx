import React, { useState } from 'react';
import { SearchResultItem, GONGSIL_USERUID_DICT, WOORI_USERUID_DICT, OwnerDetailDto } from '../types';
import { getApiUrl } from '../config';
import { getAuthHeaders } from '../auth';
import OwnerDetailModal from './OwnerDetailModal';
import './ResultsTable.css';
import { VALID_ID_MAP } from '../constants/employeeData';
interface ResultsTableProps {
  results: SearchResultItem[];
  onOpenAll: () => void;
  onPhotoAll: () => void;
}

type SortField = keyof SearchResultItem;
type SortDirection = 'asc' | 'desc';

const ResultsTable: React.FC<ResultsTableProps> = ({ results, onOpenAll, onPhotoAll }) => {
  const [sortField, setSortField] = useState<SortField>('registerDate');
  const [sortDirection, setSortDirection] = useState<SortDirection>('desc');
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [ownerDetail, setOwnerDetail] = useState<OwnerDetailDto | null>(null);
  const [isLoadingDetail, setIsLoadingDetail] = useState(false);

  const handleSort = (field: SortField) => {
    if (sortField === field) {
      setSortDirection(sortDirection === 'asc' ? 'desc' : 'asc');
    } else {
      setSortField(field);
      setSortDirection('asc');
    }
  };

  const sortedResults = [...results].sort((a, b) => {
    const aVal = a[sortField];
    const bVal = b[sortField];

    if (aVal === bVal) return 0;

    const comparison = aVal > bVal ? 1 : -1;
    return sortDirection === 'asc' ? comparison : -comparison;
  });

  // 조건에 따라 노란색으로 표시할지 체크하는 함수
  const shouldHighlight = (item: SearchResultItem): boolean => {
    const estNo = item.establishRegistrationNo;
    const platform = item.platform;
    const verification = item.verificationTypeName;

    // 1. 공실클럽 조건
    if (estNo in GONGSIL_USERUID_DICT && platform.includes('공실클럽')) {
      return true;
    }

    // 2. 우리집부동산 - 리다이렉션 URL에서 UID 추출하므로 조건 없이 가능
    // if (platform.includes('우리집부동산')) {
    //   return true;
    // }

    // 3. 부동산뱅크, 부동산써브, 선방 키워드 포함
    const keywords = ['부동산뱅크', '부동산써브', '선방', '우리집부동산'];
    if (keywords.some(keyword => platform.includes(keyword))) {
      return true;
    }

    // 4. 부동산포스 + 소유자가 '일반'이 아닌 경우
    if (platform.includes('부동산포스') && verification !== '일반') {
      return true;
    }

    return false;
  };

  // 조회 버튼 클릭 핸들러
  const handleInquiry = async (item: SearchResultItem) => {
    //console.log('조회 요청:', item);

    setIsModalOpen(true);
    setOwnerDetail(null);
    setIsLoadingDetail(true);

      // ✨ 1. Local Storage에서 직원 ID를 가져옵니다.
      const employeeId = localStorage.getItem('employeeId');

      let employeeName = '미상';

      if (employeeId) {
          // ✨ 2. ID가 있을 경우, VALID_ID_MAP에서 이름을 조회합니다.
          employeeName = VALID_ID_MAP.get(employeeId) || '미상';
      }
    try {
      const apiUrl = getApiUrl();
      const requestUrl = `${apiUrl}/api/items/detail`;

      // 통합된 API: 매물번호 또는 URL만 전달
      const requestBody = {
        url: item.itemId,  // 매물번호 전달 (서버에서 매물 정보 조회)
        employeeName: employeeName,
      };

      const response = await fetch(requestUrl, {
        method: 'POST',
        headers: getAuthHeaders(),
        body: JSON.stringify(requestBody)
      });
      //console.log('응답 상태:', response.status);

      if (!response.ok) {
        throw new Error(`조회 실패 (${response.status})`);
      }
      const data: OwnerDetailDto = await response.json();
      //console.log('받은 데이터:', data);
      setOwnerDetail(data);
    } catch (error) {
      console.error('소유자 정보 조회 실패:', error);
      alert('소유자 정보를 조회할 수 없습니다.\n에러: ' + error);
      setIsModalOpen(false);
    } finally {
      setIsLoadingDetail(false);
    }
  };

  const closeModal = () => {
    setIsModalOpen(false);
    setOwnerDetail(null);
  };

  const formatNumber = (num: number): string => {
    return num.toLocaleString('ko-KR');
  };

  const calculatePyeong = (area: number): number => {
    return Math.round(area / 3.3);
  };

  if (results.length === 0) {
    return (
      <div className="no-results">
        <p>검색 결과가 없습니다.</p>
      </div>
    );
  }

  return (
    <div className="results-container">
      <div className="results-header">
        <div className="results-count">
          검색 결과: <strong>{results.length}</strong>건
        </div>
        {/*<div className="results-actions">*/}
        {/*  <button onClick={onPhotoAll} className="btn-action">사진열기</button>*/}
        {/*  <button onClick={onOpenAll} className="btn-action">전체열기</button>*/}
        {/*</div>*/}
      </div>

      <div className="table-wrapper">
        <table className="results-table">
          <thead>
            <tr>
              <th className="index-column">INDEX</th>
              <th onClick={() => handleSort('address')}>주소</th>
              <th onClick={() => handleSort('floor')}>층</th>
              <th onClick={() => handleSort('area')}>면적(평)</th>
              <th onClick={() => handleSort('deposit')}>보증금/매매가</th>
              <th onClick={() => handleSort('monthlyRent')}>월세</th>
              <th onClick={() => handleSort('maintenanceFee')}>관리비</th>
              <th>사진</th>
              <th onClick={() => handleSort('agency')}>중개사무소</th>
              <th onClick={() => handleSort('registerDate')}>등록일</th>
              <th onClick={() => handleSort('moveInDate')}>입주가능일</th>
              <th onClick={() => handleSort('verificationTypeName')}>소유자</th>
              <th onClick={() => handleSort('platform')}>플랫폼</th>
              <th className="inquiry-column">조회</th>
            </tr>
          </thead>
          <tbody>
            {sortedResults.map((item, index) => (
              <tr
                key={index}
                className={shouldHighlight(item) ? 'highlighted' : ''}
              >
                <td className="index-column">{index + 1}</td>
                <td className="address-cell text-left">
                  <a
                    href={`https://map.naver.com/p/search/${encodeURIComponent(item.address)}`}
                    target="_blank"
                    rel="noopener noreferrer"
                  >
                    {item.address}
                  </a>
                </td>
                <td>{item.floor}</td>
                <td>{calculatePyeong(item.area)}</td>
                <td>{formatNumber(item.deposit)}</td>
                <td>{formatNumber(item.monthlyRent)}</td>
                <td>{item.maintenanceFee}</td>
                <td>{item.hasPhoto}</td>
                <td className="text-left">{item.agency}</td>
                <td>{item.registerDate}</td>
                <td>{item.moveInDate}</td>
                <td>{item.verificationTypeName}</td>
                <td className="platform-cell">
                  <a
                    href={item.url}
                    target="_blank"
                    rel="noopener noreferrer"
                  >
                    {item.platform}
                  </a>
                </td>
                <td className="inquiry-column">
                  {shouldHighlight(item) && (
                    <button
                      className="btn-inquiry"
                      onClick={() => handleInquiry(item)}
                    >
                      정보조회
                    </button>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {/* 모바일용 카드 뷰 */}
      <div className="mobile-cards">
        {sortedResults.map((item, index) => (
          <div key={index} className="result-card">
            <div className="card-header">
              <a
                href={`https://map.naver.com/p/search/${encodeURIComponent(item.address)}`}
                target="_blank"
                rel="noopener noreferrer"
                className="address-link"
              >
                {item.address}
              </a>
              <div className="header-badges">
                {item.hasPhoto === 'O' && <span className="photo-badge">📷</span>}
                <span className="platform-badge">{item.platform}</span>
              </div>
            </div>
            <div className="card-body">
              <div className="info-row">
                <span className="label">층/면적:</span>
                <span className="value">{item.floor}층 / {calculatePyeong(item.area)}평</span>
              </div>
              <div className="info-row price-row">
                <span className="label">보증금/월세/관리비:</span>
                <span className="value">{formatNumber(item.deposit)}/{formatNumber(item.monthlyRent)}/{item.maintenanceFee}</span>
              </div>
              <div className="info-row">
                <span className="label">중개사무소:</span>
                <span className="value">{item.agency}</span>
              </div>
              <div className="card-footer">
                {shouldHighlight(item) && (
                  <button
                    className="btn-inquiry mobile"
                    onClick={() => handleInquiry(item)}
                  >
                    정보조회
                  </button>
                )}
                <a
                  href={item.url}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="view-detail-btn"
                >
                  상세보기
                </a>
              </div>
            </div>
          </div>
        ))}
      </div>

      <OwnerDetailModal
        isOpen={isModalOpen}
        onClose={closeModal}
        detail={ownerDetail}
        isLoading={isLoadingDetail}
      />
    </div>
  );
};

export default ResultsTable;
