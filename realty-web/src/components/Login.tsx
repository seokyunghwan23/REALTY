import React, { useState } from 'react';
import './Login.css';
import { VALID_ID_MAP } from '../constants/employeeData'; // VALID_ID_MAP을 import하여 사용

interface LoginProps {
    onLogin: (employeeId: string) => void;
}

const Login: React.FC<LoginProps> = ({ onLogin }) => {
    const [employeeId, setEmployeeId] = useState('');
    const [error, setError] = useState('');

    const handleSubmit = (e: React.FormEvent) => {
        e.preventDefault();

        // 기존의 하드코딩된 validIds 배열은 삭제하거나 주석 처리했습니다.
        // const validIds = [ ... ];

        const trimmedId = employeeId.trim();

        // 1. VALID_ID_MAP을 사용하여 ID 유효성 검사
        if (VALID_ID_MAP.has(trimmedId)) {
            // 2. 로그인 성공 시, Map에서 직원 이름 조회
            const employeeName = VALID_ID_MAP.get(trimmedId);

            // 3. Local Storage에 ID와 이름 저장 (다른 컴포넌트에서 접근 가능하도록)
            localStorage.setItem('employeeId', trimmedId);
            // 이름이 없을 경우를 대비해 '미상' 등의 기본값 처리 가능
            localStorage.setItem('employeeName', employeeName || '미상');

            setError('');
            onLogin(trimmedId);
        } else {
            setError('유효하지 않은 직원ID입니다.');
            setEmployeeId('');
        }
    };

    const handleKeyPress = (e: React.KeyboardEvent) => {
        if (e.key === 'Enter') {
            handleSubmit(e);
        }
    };

    return (
        <div className="login-container">
            <div className="login-box">
                <h1>부동산 매물 검색</h1>
                <h2>로그인</h2>

                <form onSubmit={handleSubmit}>
                    <div className="input-wrapper">
                        <label>직원ID</label>
                        <input
                            type="text"
                            value={employeeId}
                            onChange={(e) => setEmployeeId(e.target.value)}
                            onKeyPress={handleKeyPress}
                            placeholder="직원ID를 입력하세요"
                            autoFocus
                        />
                    </div>

                    {error && <div className="error-message">{error}</div>}

                    <button type="submit" className="login-button">
                        로그인
                    </button>
                </form>
            </div>
        </div>
    );
};

export default Login;