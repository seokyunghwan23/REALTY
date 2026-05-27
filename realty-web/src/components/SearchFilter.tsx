import React from 'react';
import { SearchFilters, PlatformCheckValues, TransactionValues } from '../types';
import { getEmployeeById } from '../constants/employeeData';
import './SearchFilter.css';

interface SearchFilterProps {
  filters: SearchFilters;
  onFiltersChange: (filters: SearchFilters) => void;
  onSearch: () => void;
  onUrlSearch: () => void;
  onOwnerInquiry: () => void;
  onCopyAddress: () => void;
  isSearching: boolean;
  currentUser: string | null;
}

const SearchFilter: React.FC<SearchFilterProps> = ({
  filters,
  onFiltersChange,
  onSearch,
  onUrlSearch,
  onOwnerInquiry,
  onCopyAddress,
  isSearching,
  currentUser
}) => {
  // 청운 소속 여부 확인
  const employee = currentUser ? getEmployeeById(currentUser) : null;
  const isChungwoon = employee?.agentName === '청운';
  const handleInputChange = (field: keyof SearchFilters, value: string) => {
    onFiltersChange({ ...filters, [field]: value });
  };

  const handlePropertyTypeChange = (type: '주택' | '상가') => {
    const newPlatforms: PlatformCheckValues = type === '주택'
      ? { 직방: false, 다방: false, 피터팬: false, 네이버: false }
      : { 네이버: false, 네모: false };

    onFiltersChange({
      ...filters,
      propertyType: type,
      platforms: newPlatforms
    });
  };

  const handlePlatformChange = (platform: string, checked: boolean) => {
    onFiltersChange({
      ...filters,
      platforms: { ...filters.platforms, [platform]: checked }
    });
  };

  const handleTransactionChange = (transaction: keyof TransactionValues, checked: boolean) => {
    onFiltersChange({
      ...filters,
      transactions: { ...filters.transactions, [transaction]: checked }
    });
  };

  const handleKeyPress = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter') {
      onSearch();
    }
  };

  return (
    <div className="search-filter">
      {/* URL 조회 섹션 */}
      <div className="filter-section">
        <div className="input-group">
          <label>URL/매물번호</label>
          <div className="input-with-checkbox">
            <input
              type="text"
              value={filters.url}
              onChange={(e) => handleInputChange('url', e.target.value)}
              placeholder="매물 URL 또는 매물번호를 입력하세요"
            />
            {isChungwoon && (
              <label className="my-properties-checkbox">
                <input
                  type="checkbox"
                  checked={filters.myProperties || false}
                  onChange={(e) => onFiltersChange({ ...filters, myProperties: e.target.checked })}
                />
                내 매물 조회
              </label>
            )}
          </div>
        </div>
        <div className="button-group">
          <button onClick={onUrlSearch} className="btn-secondary">주소조회</button>
          <button onClick={onOwnerInquiry} className="btn-secondary">정보조회</button>
        </div>
      </div>

      {/* 주소 검색 섹션 */}
      <div className="filter-section">
        <div className="input-row">
          <div className="input-group flex-1">
            <label>주소</label>
            <input
              type="text"
              value={filters.address}
              onChange={(e) => handleInputChange('address', e.target.value)}
              onKeyPress={handleKeyPress}
              placeholder="주소를 입력하세요"
            />
          </div>
          <div className="input-group">
            <label>층</label>
            <input
              type="text"
              value={filters.floor}
              onChange={(e) => handleInputChange('floor', e.target.value)}
              onKeyPress={handleKeyPress}
              placeholder="층"
              className="input-small"
            />
          </div>
        </div>
        <div className="button-group">
          <button onClick={onCopyAddress} className="btn-secondary">주소 복사</button>
          <button onClick={onSearch} className="btn-primary" disabled={isSearching}>
            {isSearching ? '검색중...' : '광고 검색'}
          </button>
        </div>
      </div>

      {/* 매물 종류 선택 */}
      <div className="filter-section">
        <div className="property-types">
          <div className="property-type-group">
            <div className="radio-header">
              <input
                type="radio"
                id="housing"
                checked={filters.propertyType === '주택'}
                onChange={() => handlePropertyTypeChange('주택')}
              />
              <label htmlFor="housing">주택</label>
            </div>
            {filters.propertyType === '주택' && (
              <div className="platform-checkboxes">
                <label>
                  <input
                    type="checkbox"
                    checked={filters.platforms.직방 || false}
                    onChange={(e) => handlePlatformChange('직방', e.target.checked)}
                  />
                  직방
                </label>
                <label>
                  <input
                    type="checkbox"
                    checked={filters.platforms.다방 || false}
                    onChange={(e) => handlePlatformChange('다방', e.target.checked)}
                  />
                  다방
                </label>
                <label>
                  <input
                    type="checkbox"
                    checked={filters.platforms.피터팬 || false}
                    onChange={(e) => handlePlatformChange('피터팬', e.target.checked)}
                  />
                  피터팬
                </label>
                <label>
                  <input
                    type="checkbox"
                    checked={filters.platforms.네이버 || false}
                    onChange={(e) => handlePlatformChange('네이버', e.target.checked)}
                  />
                  네이버
                </label>
              </div>
            )}
          </div>

          <div className="property-type-group">
            <div className="radio-header">
              <input
                type="radio"
                id="commercial"
                checked={filters.propertyType === '상가'}
                onChange={() => handlePropertyTypeChange('상가')}
              />
              <label htmlFor="commercial">상가</label>
            </div>
            {filters.propertyType === '상가' && (
              <div className="platform-checkboxes">
                <label>
                  <input
                    type="checkbox"
                    checked={filters.platforms.네이버 || false}
                    onChange={(e) => handlePlatformChange('네이버', e.target.checked)}
                  />
                  네이버
                </label>
                <label>
                  <input
                    type="checkbox"
                    checked={filters.platforms.네모 || false}
                    onChange={(e) => handlePlatformChange('네모', e.target.checked)}
                  />
                  네모
                </label>
              </div>
            )}
          </div>

          {/* 거래 방식 */}
          <div className="property-type-group">
            <div className="radio-header">
              <label>거래방식</label>
            </div>
            <div className="platform-checkboxes">
              {/*<label>*/}
              {/*  <input*/}
              {/*    type="checkbox"*/}
              {/*    checked={filters.transactions.매매}*/}
              {/*    onChange={(e) => handleTransactionChange('매매', e.target.checked)}*/}
              {/*  />*/}
              {/*  매매*/}
              {/*</label>*/}
              {/*<label>*/}
              {/*  <input*/}
              {/*    type="checkbox"*/}
              {/*    checked={filters.transactions.전세}*/}
              {/*    onChange={(e) => handleTransactionChange('전세', e.target.checked)}*/}
              {/*  />*/}
              {/*  전세*/}
              {/*</label>*/}
              <label>
                <input
                  type="checkbox"
                  checked={filters.transactions.월세}
                  onChange={(e) => handleTransactionChange('월세', e.target.checked)}
                />
                월세
              </label>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};

export default SearchFilter;
