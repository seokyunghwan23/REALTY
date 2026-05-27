// src/constants/employeeData.ts

// 직원 정보 타입 정의
export interface Employee {
    id: string;
    name: string;
    agentName: string;
    adAgent: string;
    phone: string;
    authorization: string;
    sheetName?: string;
    sheetId?: string;
}

// 직원 데이터
export const EMPLOYEES: Employee[] = [
    {
        id: '900227',
        name: 'admin',
        agentName: '청운',
        adAgent: '',
        phone: '010-6300-8895',
        authorization: '',
        sheetName: '사무실',
        sheetId: '1b4DRVhG-g0ohyBRcdvdBdGV_dWO-rKL2GmL9Nwczceg', // [중요] 구글 시트 ID를 여기에 입력하세요
    },
    {
        id: '147963',
        name: '서경환',
        agentName: '청운',
        adAgent: '',
        phone: '010-6300-8895',
        authorization: '',
        sheetName: '사무실',
        sheetId: '1b4DRVhG-g0ohyBRcdvdBdGV_dWO-rKL2GmL9Nwczceg', // [중요] 구글 시트 ID를 여기에 입력하세요
    },
    {
        id: '730424',
        name: '김청미',
        agentName: '청운',
        adAgent: '써브',
        phone: '010-4331-2805',
        authorization: 'Bearer eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiIxNTQwMjk5NSIsIm1lbWJlclR5cGUiOiJHRU5FUkFMIiwiZXhwIjoxNzY2ODAyNjI5fQ.b9cy4L_W6a_pfWmdsVUfy-fnrI0O_XB16u1ljoXZwpr0UlWWVATNtS2ujPtzaEopDOmR-w-aycsCFj1xyXiw0w',
        sheetName: '상가_사당/이수',
        sheetId: '1yn5n4wIwcMF6nJgK0glA9pwrb5EeRoob08NLLMZZcwE'
    },
    {
        id: '950829',
        name: '이제헌',
        agentName: '청운',
        adAgent: '써브',
        phone: '010-7560-1799',
        authorization: 'Bearer eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiIxNTQwNTI3MyIsIm1lbWJlclR5cGUiOiJHRU5FUkFMIiwiZXhwIjoxNzY2ODIxMTQ2fQ.Dp7xx_ZypFS6QsQyzLT9nzuEJlp58-TqK8QTV2LDYl1l5ENz22i1Kl5ldfCprY30ap9b4Cfq11Jl6cx_TSyNGg',
    },
    {
        id: '231476',
        name: '조주현',
        agentName: '대양',
        adAgent: '',
        phone: '010-4686-7646',
        authorization: '',
    },
    {
        id: '041756',
        name: '장준혁',
        agentName: '원빌딩',
        adAgent: '',
        phone: '010-3037-3827',
        authorization: '',
    },
];

// admin ID
export const ADMIN_ID = '900227';

// ============ 조회 헬퍼 함수들 ============

// ID로 직원 조회
export const getEmployeeById = (id: string): Employee | undefined => {
    return EMPLOYEES.find(emp => emp.id === id);
};

// 이름으로 직원 조회
export const getEmployeeByName = (name: string): Employee | undefined => {
    return EMPLOYEES.find(emp => emp.name === name);
};

// ID 유효성 검사
export const isValidId = (id: string): boolean => {
    return EMPLOYEES.some(emp => emp.id === id);
};

// ID로 이름 조회
export const getNameById = (id: string): string | undefined => {
    return getEmployeeById(id)?.name;
};

// admin 여부 확인
export const isAdmin = (employeeId: string | null): boolean => {
    return employeeId === ADMIN_ID;
};

// 청운 에이전트 여부 확인 (매물목록 접근 권한)
export const isChungunAgent = (employeeId: string | null): boolean => {
    if (!employeeId) return false;
    const emp = getEmployeeById(employeeId);
    return emp?.agentName === '청운';
};

// ID로 토큰 조회
export const getTokenByEmployeeId = (employeeId: string): string | undefined => {
    return getEmployeeById(employeeId)?.authorization || undefined;
};

// 이름으로 토큰 조회
export const getTokenByName = (name: string): string | undefined => {
    return getEmployeeByName(name)?.authorization || undefined;
};

// 기존 VALID_ID_MAP 호환용 (deprecated - 점진적 마이그레이션용)
export const VALID_ID_MAP = new Map<string, string>(
    EMPLOYEES.map(emp => [emp.id, emp.name])
);

// 마스터 앱스크립트 URL (모든 직원이 공통으로 사용)
export const MASTER_APPS_SCRIPT_URL = 'https://script.google.com/macros/s/AKfycbzx7er4KXITgj_as6E7G4ZOBqK1LwDKeXgWoITRjPkoYCOEo8v0FOW-HChEzf6ApnrF0A/exec';
