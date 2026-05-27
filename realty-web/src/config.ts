// API 서버 URL 설정
// 외부에서 접속할 때는 localhost 대신 공인 IP를 사용해야 함
export const API_BASE_URL = process.env.REACT_APP_API_URL || 'http://localhost:8081';

// 현재 호스트가 localhost가 아니면 (외부 접속이면) API도 같은 호스트 사용
export const getApiUrl = () => {
  const currentHost = window.location.hostname;

  // localhost나 127.0.0.1이 아니면 외부 접속
  if (currentHost !== 'localhost' && currentHost !== '127.0.0.1') {
    return `http://${currentHost}:8081`;
  }

  return API_BASE_URL;
};
