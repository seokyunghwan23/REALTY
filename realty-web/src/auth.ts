// 보안 토큰 (백엔드와 동일하게 설정)
const SECRET_TOKEN = "RealtyMate_SecureToken_2025_KH_System";

// 토큰을 localStorage에 저장/불러오기 (브라우저 껐다 켜도 유지됨)
export const setAuthToken = (token: string) => {
  localStorage.setItem('authToken', token);
};

export const getAuthToken = (): string | null => {
  return localStorage.getItem('authToken');
};

export const removeAuthToken = () => {
  localStorage.removeItem('authToken');
};

// URL에서 토큰 파라미터 확인
export const checkTokenFromUrl = (): boolean => {
  const urlParams = new URLSearchParams(window.location.search);
  const token = urlParams.get('token');

  if (token === SECRET_TOKEN) {
    setAuthToken(token);
    // URL에서 토큰 제거 (보안)
    window.history.replaceState({}, document.title, window.location.pathname);
    return true;
  }

  // 이미 저장된 토큰이 있으면 검증
  const savedToken = getAuthToken();
  return savedToken === SECRET_TOKEN;
};

// 직원ID 저장/불러오기
export const setEmployeeId = (employeeId: string) => {
  localStorage.setItem('employeeId', employeeId);
};

export const getEmployeeId = (): string | null => {
  return localStorage.getItem('employeeId');
};

export const removeEmployeeId = () => {
  localStorage.removeItem('employeeId');
};

// 요청 헤더에 토큰 추가
export const getAuthHeaders = () => {
  const token = getAuthToken();
  const employeeId = getEmployeeId();
  return {
    'Content-Type': 'application/json',
    'X-Auth-Token': token || '',
    'X-Employee-Id': employeeId || '',
  };
};

export { SECRET_TOKEN };
