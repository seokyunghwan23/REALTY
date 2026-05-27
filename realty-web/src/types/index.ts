// 검색 결과 아이템 타입
export interface SearchResultItem {
  itemId: number;
  address: string;
  floor: string;
  area: number;
  deposit: number;
  monthlyRent: number;
  maintenanceFee: number;
  hasPhoto: string;
  agency: string;
  registerDate: string;
  moveInDate: string;
  verificationTypeName: string;
  verificationTypeCode: string;
  platform: string;
  url: string;
  cpPcArticleUrl: string;  // CP 매물 URL (실제 플랫폼 URL - serve, neonet 등)
  title: string;
  description: string;
  establishRegistrationNo: string;
}

// 플랫폼 체크박스 타입
export interface PlatformCheckValues {
  직방?: boolean;
  다방?: boolean;
  피터팬?: boolean;
  네이버?: boolean;
  네모?: boolean;
}

// 거래 방식 타입
export interface TransactionValues {
  매매?: boolean;
  전세?: boolean;
  월세?: boolean;
}

// 검색 필터 타입
export interface SearchFilters {
  url: string;
  address: string;
  floor: string;
  propertyType: '주택' | '상가' | '';
  platforms: PlatformCheckValues;
  transactions: TransactionValues;
  myProperties?: boolean;
}

// 공실클럽 및 우리집부동산 등록번호 딕셔너리
export const GONGSIL_USERUID_DICT: Record<string, { uid: string; name: string }> = {
  '제11680-2018-0038호': { uid: '18290', name: '주식회사 강남부동산중개' },
  '11710-2017-00355': { uid: '18430', name: '(주)마이다스리얼에스테이트부동산중개법인' },
  '9250-10238': { uid: '18775', name: '(주)마이다스부동산중개법인' },
  '9250-11547': { uid: '18838', name: '삼광공인중개사사무소' },
  '11620-2024-00123': { uid: '19245', name: '단비공인중개사사무소' },
  '11680-2018-00210': { uid: '19609', name: '오렌지 부동산중개법인주식회사' },
  '제11680-2023-00012호': { uid: '22931', name: '주식회사시작부동산중개법인' },
  '제11710-2025-00023호': { uid: '23083', name: '(주)강남애플부동산중개법인' },
  '제11710-2022-00039호': { uid: '24079', name: '제이로드부동산중개' },
  '11590-2023-00051': { uid: '26265', name: '이수자이멘토부동산공인중개사사무소' },
  '제11710-2023-00091호': { uid: '26316', name: '88공인중개사사무소' },
  '11590-2025-00052': { uid: '26625', name: '차씨네부동산공인중개사사무소' },
  '11680-2024-00367': { uid: '27552', name: '주식회사블루핀부동산중개' },
  '11590-2023-00113': { uid: '27579', name: '주식회사 조각부동산중개법인' },
  '11650-2024-00175': { uid: '27937', name: '강남국민공인중개사사무소' },
  '11650-2025-00026': { uid: '28484', name: '(주)마이다스부동산중개법인서초' },
  '제11620-2015-00100호': { uid: '20420', name: '열린공인중개사사무소' },
  '제11200-2023-00088호': { uid: '26839', name: '주식회사마이다스부동산중개법인성수' },
  '제11620-2024-00061호': { uid: '27772', name: '원데이공인중개사사무소' },
  '제11620-2022-00040호': { uid: '22534', name: '구름공인중개사사무소' },
  '11650-2019-00294': { uid: '19466', name: '삼성래미안공인중개사사무소' },
  '제11710-2023-00077호': { uid: '27786', name: '주식회사오픈닥터부동산중개법인' },
  '제11650-2025-00008호': { uid: '23984', name: '리움공인중개사사무소' },
  '제11410-2022-00028호': { uid: '26234', name: '리치공인중개사사무소' },
  '제11620-2025-00050호': { uid: '28796', name: '애나네 공인중개사 사무소' },
  '제11620-2021-00069호': { uid: '23084', name: '정평공인중개사사무소' },
  '제11440-2022-00225호': { uid: '25903', name: '홍대당근부동산중개사무소' },
  '11710-2022-00018': { uid: '24050', name: '우리공인중개사사무소' },
    '제11680-2021-00369호': { uid: '23445', name: '부동산채움공인중개사사무소' },
    '11590-2025-00066': { uid: '29187', name: '딩동부동산공인중개사사무소' },
    '제 11650-2025-00242 호': { uid: '29061', name: '에덴프라퍼티공인중개사사무소' },
};

export const WOORI_USERUID_DICT: Record<string, { uid: string; name: string }> = {
  '11620-2022-00158': { uid: '40618', name: '홍일공인중개사사무소' },
  '11560-2024-00003': { uid: '40286', name: '대박공인중개사사무소' },
  '11710-2022-00018': { uid: '40097', name: '우리공인중개사사무소' },
  '11620-2024-00030': { uid: '40963', name: '별빛공인중개사사무소' },
  '11560-2016-00039': { uid: '40228', name: '명품당산역공인중개사사무소' },
  '11620-2020-00017': { uid: '40268', name: 'SS삼성공인중개사사무소' },
  '11440-2021-00132': { uid: '40075', name: '힘찬부동산중개법인' },
  '11440-2023-00056': { uid: '38919', name: '연우공인중개사사무소' }
};

// 소유자 상세 정보 타입
export interface OwnerDetailDto {
  address: string;
  detailAddress: string;
  platform: string;
  owner: string;
  contact: string;
  verificationMethod: string;
  managementOffice: string;
  gender: string;
  memo: string;
}
