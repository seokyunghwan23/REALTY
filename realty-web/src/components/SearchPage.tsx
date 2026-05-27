import React, { useState } from 'react';
import SearchFilter from './SearchFilter';
import ResultsTable from './ResultsTable';
import OwnerDetailModal from './OwnerDetailModal';
import { SearchFilters, SearchResultItem, OwnerDetailDto } from '../types';
import { getApiUrl } from '../config';
import { getAuthHeaders } from '../auth';
import {VALID_ID_MAP, getTokenByEmployeeId} from "../constants/employeeData";

interface SearchPageProps {
  currentUser: string | null;
}

const SearchPage: React.FC<SearchPageProps> = ({ currentUser }) => {
  const [filters, setFilters] = useState<SearchFilters>({
    url: '',
    address: '',
    floor: '',
    propertyType: '상가',
    platforms: {
      네이버: true,
      네모: true
    },
    transactions: {
      매매: false,
      전세: false,
      월세: true
    }
  });

  const [results, setResults] = useState<SearchResultItem[]>([]);
  const [isSearching, setIsSearching] = useState(false);
  const [statusMessage, setStatusMessage] = useState('');
  const [isModalOpen, setIsModalOpen] = useState(false);
  // const [ownerDetail, setOwnerDetail] = useState<OwnerDetailDto | null>(null);
    const [ownerDetail, setOwnerDetail] = useState<any | null>(null); // 변경
  const [isLoadingDetail, setIsLoadingDetail] = useState(false);

  const showStatus = (message: string, duration: number = 3000) => {
    setStatusMessage(message);
    setTimeout(() => setStatusMessage(''), duration);
  };

  const handleUrlSearch = async () => {
    if (!filters.url.trim()) {
      alert('URL을 입력하세요');
      return;
    }

    try {
      const apiUrl = getApiUrl();
      const response = await fetch(`${apiUrl}/api/items/address-search`, {
        method: 'POST',
        headers: getAuthHeaders(),
        body: JSON.stringify({ url: filters.url })
      });

      if (!response.ok) {
        const errorText = await response.text();
        console.error('서버 응답 에러:', errorText);
        throw new Error(`주소 조회 실패 (${response.status}): ${errorText}`);
      }

      const data = await response.json();
      setFilters({ ...filters, address: data.address });
      showStatus(data.message || '주소 조회 완료');
    } catch (error) {
      console.error('주소 조회 오류:', error);
      if (error instanceof TypeError) {
        alert('서버에 연결할 수 없습니다. Spring Boot 서버가 실행 중인지 확인하세요. (포트 8081)');
      } else {
        alert(`주소 조회 실패: ${error}`);
      }
    }
  };

  const handleCopyAddress = () => {
    if (filters.address) {
      if (navigator.clipboard && navigator.clipboard.writeText) {
        navigator.clipboard.writeText(filters.address)
          .then(() => showStatus('주소 복사 완료'))
          .catch(err => {
            console.error('클립보드 복사 실패:', err);
            fallbackCopyAddress(filters.address);
          });
      } else {
        fallbackCopyAddress(filters.address);
      }
    }
  };

  const fallbackCopyAddress = (text: string) => {
    const textarea = document.createElement('textarea');
    textarea.value = text;
    textarea.style.position = 'fixed';
    textarea.style.opacity = '0';
    document.body.appendChild(textarea);
    textarea.select();

    try {
      const successful = document.execCommand('copy');
      if (successful) {
        showStatus('주소 복사 완료');
      } else {
        showStatus('복사 실패');
      }
    } catch (err) {
      console.error('복사 실패:', err);
      showStatus('복사 실패');
    } finally {
      document.body.removeChild(textarea);
    }
  };

  const handleSearch = async () => {
    if (!filters.address.trim()) {
      alert('주소를 입력하세요');
      return;
    }

    if (!filters.propertyType) {
      alert('매물 종류(주택/상가)를 선택하세요');
      return;
    }

    const hasTransaction = filters.transactions.매매 || filters.transactions.전세 || filters.transactions.월세;
    if (!hasTransaction) {
      alert('거래 방식을 선택하세요');
      return;
    }

    const selectedPlatforms = Object.entries(filters.platforms).filter(([_, v]) => v);
    if (selectedPlatforms.length === 0) {
      alert('플랫폼을 선택하세요');
      return;
    }

    setIsSearching(true);
    showStatus('검색중...');

    try {
      const apiUrl = getApiUrl();
      const response = await fetch(`${apiUrl}/api/items/search_ad`, {
        method: 'POST',
        headers: getAuthHeaders(),
        body: JSON.stringify({
          address: filters.address,
          floor: filters.floor,
          propertyType: filters.propertyType,
          platforms: filters.platforms,
          transactions: filters.transactions
        })
      });

      if (!response.ok) {
        throw new Error('검색 실패');
      }

      const data = await response.json();

      const mappedResults: SearchResultItem[] = data.items.map((item: any) => ({
        itemId: item.itemId || item.id || 0,
        address: item.address || '',
        floor: item.floor?.toString() || '',
        area: item.area || 0,
        deposit: item.deposit || 0,
        monthlyRent: item.monthlyFee || 0,
        maintenanceFee: item.managementFee || 0,
        hasPhoto: item.hasPhoto || 'X',
        agency: item.agentName || '직거래',
        registerDate: item.registerDate || '',
        moveInDate: item.moveInDate || '',
        verificationTypeName: item.verificationTypeName || '일반',
        verificationTypeCode: item.verificationTypeCode || '',
        platform: item.cpName ? `${item.cpName}/${item.platform}` : item.platform || '네이버',
        url: item.url || '',
        cpPcArticleUrl: item.cpPcArticleUrl || '',
        title: item.title || '',
        description: item.description || '',
        establishRegistrationNo: item.establishRegistrationNo || ''
      }));

      setResults(mappedResults);
      setIsSearching(false);
      showStatus(`검색 완료 (${mappedResults.length}건)`);
    } catch (error) {
      console.error('검색 오류:', error);
      setIsSearching(false);
      alert('검색 실패');
    }
  };

  const handleOpenAll = () => {
    results.forEach(result => {
      window.open(result.url, '_blank');
    });
  };

  const handlePhotoAll = () => {
    const photosResults = results.filter(r => r.hasPhoto === 'O');
    photosResults.forEach(result => {
      window.open(result.url, '_blank');
    });
  };

// ...
    const handleOwnerInquiry = async () => {
        if (!filters.url.trim()) {
            alert('URL 또는 매물번호를 입력하세요');
            return;
        }

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

        // 3. 요청 본문 생성
        const requestBody: Record<string, string | undefined> = {
            url: filters.url,
            employeeName: employeeName,
            myProperties: filters.myProperties ? 'true' : undefined,
            authorization: filters.myProperties && currentUser
                ? getTokenByEmployeeId(currentUser)
                : undefined
        };

        try {
            const apiUrl = getApiUrl();
            console.log('=== handleOwnerInquiry 시작 ===');
            console.log('요청 본문:', requestBody);

            const response = await fetch(`${apiUrl}/api/items/detail`, {
                method: 'POST',
                headers: getAuthHeaders(),
                body: JSON.stringify(requestBody)
            });

            console.log('응답 상태:', response.status, response.ok);

            if (!response.ok) {
                const errorData = await response.json().catch(() => ({}));
                console.log('에러 응답:', errorData);
                const errorMsg = errorData.error || `조회 실패 (${response.status})`;
                console.log('에러 메시지:', errorMsg);
                throw new Error(errorMsg);
            }

            const data: any = await response.json();
            setOwnerDetail(data);
            showStatus(`소유자 정보 조회 및 시트 저장 완료: ${data.owner || ''}`, 5000);

        } catch (error: any) {
            console.error('소유자 정보 조회 및 저장 실패:', error);
            const errorMessage = error?.message || String(error);
            console.log('캐치된 에러 메시지:', errorMessage);
            console.log('NOT_MY_PROPERTY 포함 여부:', errorMessage.includes('NOT_MY_PROPERTY'));
            if (errorMessage.includes('NOT_MY_PROPERTY')) {
                alert('내 매물이 아닙니다.');
            } else {
                alert('소유자 정보를 조회/저장할 수 없습니다.\n에러: ' + errorMessage);
            }
            setIsModalOpen(false);
        } finally {
            setIsLoadingDetail(false);
        }
    };

  const closeModal = () => {
    setIsModalOpen(false);
    setOwnerDetail(null);
  };

  return (
    <>
      <main className="app-main">
        <SearchFilter
          filters={filters}
          onFiltersChange={setFilters}
          onSearch={handleSearch}
          onUrlSearch={handleUrlSearch}
          onOwnerInquiry={handleOwnerInquiry}
          onCopyAddress={handleCopyAddress}
          isSearching={isSearching}
          currentUser={currentUser}
        />

        <ResultsTable
          results={results}
          onOpenAll={handleOpenAll}
          onPhotoAll={handlePhotoAll}
        />
      </main>

      {statusMessage && (
        <div className="status-bar">
          {statusMessage}
        </div>
      )}

      <OwnerDetailModal
        isOpen={isModalOpen}
        onClose={closeModal}
        detail={ownerDetail}
        isLoading={isLoadingDetail}
      />
    </>
  );
};

export default SearchPage;
