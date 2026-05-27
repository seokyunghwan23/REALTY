import React from 'react';
import { OwnerDetailDto } from '../types';
import './OwnerDetailModal.css';

interface OwnerDetailModalProps {
  isOpen: boolean;
  onClose: () => void;
  detail: OwnerDetailDto | null;
  isLoading: boolean;
}

const OwnerDetailModal: React.FC<OwnerDetailModalProps> = ({ isOpen, onClose, detail, isLoading }) => {
  if (!isOpen) return null;

  return (
    <div className="modal-overlay" onClick={onClose}>
      <div className="modal-content" onClick={(e) => e.stopPropagation()}>
        <div className="modal-header">
          <h2>매물 상세 정보</h2>
          <button className="modal-close" onClick={onClose}>×</button>
        </div>

        {isLoading ? (
          <div className="modal-loading">
            <div className="spinner"></div>
            <p>조회 중...</p>
          </div>
        ) : detail ? (
          <div className="modal-body">
            <div className="info-section">
              <h3>📍 기본 정보</h3>
              <div className="info-item">
                <span className="info-label">주소</span>
                <span className="info-value">{detail.address}</span>
              </div>
              <div className="info-item">
                <span className="info-label">상세주소</span>
                <span className="info-value">{detail.detailAddress || '-'}</span>
              </div>
            </div>

            <div className="info-section">
              <h3>👤 소유자 정보</h3>
              <div className="info-item">
                <span className="info-label">소유자</span>
                <span className="info-value">{detail.owner || '-'} {detail.gender && `(${detail.gender})`}</span>
              </div>
              <div className="info-item">
                <span className="info-label">연락처</span>
                <span className="info-value">
                  {detail.contact || '-'}
                  {detail.managementOffice && ` / ${detail.managementOffice}`}
                </span>
              </div>
              <div className="info-item">
                <span className="info-label">등록방식</span>
                <span className="info-value">{detail.verificationMethod || '-'}</span>
              </div>
            </div>

            {detail.memo && (
              <div className="info-section">
                <h3>📝 메모</h3>
                <div className="memo-content">
                  {detail.memo}
                </div>
              </div>
            )}
          </div>
        ) : (
          <div className="modal-error">
            <p>정보를 불러올 수 없습니다.</p>
          </div>
        )}

        <div className="modal-footer">
          <button className="btn-close" onClick={onClose}>닫기</button>
        </div>
      </div>
    </div>
  );
};

export default OwnerDetailModal;
