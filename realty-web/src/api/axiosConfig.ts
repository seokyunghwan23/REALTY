import axios from 'axios';
import { getAuthHeaders, removeAuthToken, removeEmployeeId, SECRET_TOKEN } from '../auth';

// =================================================================
// Axios 인터셉터 설정
// =================================================================
// 1. 요청(Request) 보낼 때: 자동으로 인증 헤더를 붙여서 보냅니다.
// 2. 응답(Response) 받을 때: 인증 에러(401, 403)가 나면 자동으로 로그아웃 시킵니다.
// =================================================================

// 1. 요청 인터셉터
axios.interceptors.request.use(
  (config) => {
    // 헤더가 없는 경우를 대비해 빈 객체로 초기화
    if (!config.headers) {
      config.headers = {} as any;
    }

    const authHeaders = getAuthHeaders();
    
    // 기존 헤더에 인증 헤더 병합
    // @ts-ignore - axios 타입 호환성 문제 회피
    Object.entries(authHeaders).forEach(([key, value]) => {
      config.headers[key] = value;
    });

    return config;
  },
  (error) => {
    return Promise.reject(error);
  }
);

// 2. 응답 인터셉터
axios.interceptors.response.use(
  (response) => {
    // 정상이면 그대로 통과
    return response;
  },
  (error) => {
    // 에러 발생 시 처리
    const status = error.response ? error.response.status : null;

    // 401(인증 안됨) 또는 403(권한 없음) 에러인 경우
    if (status === 401 || status === 403) {
      console.warn(`인증 오류 발생 (Status: ${status}): 재로그인이 필요합니다.`);

      // 1. 로컬 스토리지 정보 삭제 (로그아웃)
      removeAuthToken();
      removeEmployeeId();
      
      // 2. 알림 메시지
      // 현재 페이지가 이미 로그인 페이지('/')가 아닐 때만 알림
      if (window.location.pathname !== '/') {
        alert('로그인 정보가 만료되었거나 유효하지 않습니다.\n다시 로그인해주세요.');
        // 3. 토큰이 포함된 로그인 페이지로 이동
        window.location.href = `/?token=${SECRET_TOKEN}`;
      }
    }

    // 그 외의 에러는 그대로 호출한 곳으로 넘김
    return Promise.reject(error);
  }
);

export default axios;
