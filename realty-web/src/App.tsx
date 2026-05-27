import React, { useState, useEffect } from 'react';
import { BrowserRouter, Routes, Route, Link, useLocation, Navigate } from 'react-router-dom';
import Login from './components/Login';
import SearchPage from './components/SearchPage';
// import CommercialAnalysisPage from './components/commercial/CommercialAnalysisPage';
import AdminPage from './components/AdminPage';
import { checkTokenFromUrl, removeAuthToken, setEmployeeId, getEmployeeId, removeEmployeeId, SECRET_TOKEN } from './auth';
import { isChungunAgent, isValidId } from './constants/employeeData';
import './App.css';

// 메인 컨텐츠 컴포넌트 (라우팅 내부에서 사용)
const MainContent: React.FC<{ currentUser: string | null; onLogout: () => void }> = ({ currentUser, onLogout }) => {
  const location = useLocation();

  return (
    <>
      <header className="app-header">
        <h1>청운부동산 시스템</h1>
        <nav className="main-nav">
          <Link to="/" className={location.pathname === '/' ? 'active' : ''}>
            광고 검색
          </Link>
          {/* <Link to="/commercial" className={location.pathname === '/commercial' ? 'active' : ''}>
            상권 분석
          </Link> */}
          {isChungunAgent(currentUser) && (
            <Link to="/admin" className={location.pathname === '/admin' ? 'active' : ''}>
              매물목록
            </Link>
          )}
        </nav>
        <div className="user-info">
          <span>직원ID: {currentUser}</span>
          <button onClick={onLogout} className="btn-logout">로그아웃</button>
        </div>
      </header>

      <Routes>
        <Route path="/" element={<SearchPage currentUser={currentUser} />} />
        {/* <Route path="/commercial" element={<CommercialAnalysisPage currentUser={currentUser} />} /> */}
        <Route
          path="/admin"
          element={
            isChungunAgent(currentUser)
              ? <AdminPage currentUser={currentUser} />
              : <Navigate to="/" replace />
          }
        />
      </Routes>
    </>
  );
};

const App: React.FC = () => {
  const [isAuthorized, setIsAuthorized] = useState(false);
  const [isLoggedIn, setIsLoggedIn] = useState(false);
  const [currentUser, setCurrentUser] = useState<string | null>(null);

  useEffect(() => {
    const hasValidToken = checkTokenFromUrl();
    setIsAuthorized(hasValidToken);

    if (hasValidToken) {
      const savedUser = getEmployeeId();
      if (savedUser && isValidId(savedUser)) {
        setIsLoggedIn(true);
        setCurrentUser(savedUser);
      } else {
        if (savedUser) {
          console.warn('저장된 직원 ID가 유효하지 않아 로그아웃 처리합니다.');
          removeEmployeeId();
        }
        setIsLoggedIn(false);
        setCurrentUser(null);
      }
    }
  }, []);

  const handleLogin = (employeeId: string) => {
    setEmployeeId(employeeId);
    setCurrentUser(employeeId);
    setIsLoggedIn(true);
  };

  const handleLogout = () => {
    removeEmployeeId();
    removeAuthToken();
    window.location.href = `/?token=${SECRET_TOKEN}`;
  };

  if (!isAuthorized) {
    return (
      <div style={{
        display: 'flex',
        justifyContent: 'center',
        alignItems: 'center',
        minHeight: '100vh',
        background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
        color: 'white',
        textAlign: 'center',
        padding: '20px'
      }}>
        <div>
          <h1 style={{ fontSize: '48px', marginBottom: '20px' }}>🔒</h1>
          <h2 style={{ marginBottom: '10px' }}>접근 권한이 없습니다</h2>
          <p>올바른 URL로 접속해주세요</p>
        </div>
      </div>
    );
  }

  if (!isLoggedIn) {
    return <Login onLogin={handleLogin} />;
  }

  return (
    <BrowserRouter>
      <div className="app">
        <MainContent currentUser={currentUser} onLogout={handleLogout} />
      </div>
    </BrowserRouter>
  );
};

export default App;
