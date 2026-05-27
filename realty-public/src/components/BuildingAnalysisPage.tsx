import React, { useState, useMemo, useCallback } from 'react';
import './BuildingAnalysisPage.css';

// ── 상수 ──────────────────────────────────────────────────────────────────────

const API_BASE = process.env.REACT_APP_API_BASE || 'http://localhost:8081';

const ZONE_LIMITS: Record<string, { bc: number; vl: number }> = {
  '제1종전용주거지역': { bc: 50,  vl: 100  },
  '제2종전용주거지역': { bc: 40,  vl: 120  },
  '제1종일반주거지역': { bc: 60,  vl: 150  },
  '제2종일반주거지역': { bc: 60,  vl: 200  },
  '제3종일반주거지역': { bc: 50,  vl: 250  },
  '준주거지역':        { bc: 60,  vl: 400  },
  '중심상업지역':      { bc: 60,  vl: 1000 },
  '일반상업지역':      { bc: 60,  vl: 800  },
  '근린상업지역':      { bc: 60,  vl: 600  },
  '유통상업지역':      { bc: 60,  vl: 600  },
  '전용공업지역':      { bc: 60,  vl: 200  },
  '일반공업지역':      { bc: 60,  vl: 200  },
  '준공업지역':        { bc: 60,  vl: 400  },
  '보전녹지지역':      { bc: 20,  vl: 50   },
  '생산녹지지역':      { bc: 20,  vl: 50   },
  '자연녹지지역':      { bc: 20,  vl: 50   },
};

const ZONE_ORDER = Object.keys(ZONE_LIMITS);

// ── 타입 ──────────────────────────────────────────────────────────────────────

interface GeoCandidate {
  address_name: string;
  x: string;
  y: string;
  sigunguCd: string;
  bjdongCd: string;
  bun: string;
  ji: string;
  platGbCd: string;
}

interface FloorItem {
  flrNo: string;
  flrGbCd: string;   // "10"=지하, "20"=지상, "30"=옥탑
  etcPurps: string;
  mainPurpsCd: string;
  area: string;
}

interface BuildingForm {
  bldNm: string;
  platArea: string;
  archArea: string;
  bcRat: string;
  totArea: string;
  vlRat: string;
  vlEstmArea: string;
  grndFlrCnt: string;
  ugrndFlrCnt: string;
  elevator: string;
  parking: string;
  useAprDay: string;
  zoneType: string;
  districtPlan: string;
  violation: string;
}

interface UserInput {
  salePrice: string;
  deposit: string;
  monthlyRent: string;
  maxBcRat: string;
  maxVlRat: string;
}

// ── 헬퍼 ──────────────────────────────────────────────────────────────────────

function toNum(s: string | number | null | undefined): number {
  if (s === null || s === undefined || s === '') return 0;
  if (typeof s === 'number') return s;
  return parseFloat(String(s).replace(/,/g, '')) || 0;
}

function commas(s: string): string {
  const digits = s.replace(/[^\d]/g, '');
  if (!digits) return s;
  return parseInt(digits, 10).toLocaleString();
}

function fmt(v: number | null, unit = '', digits = 1): string {
  if (v === null) return '-';
  return `${v.toFixed(digits)}${unit}`;
}

function fmtPrice(v: number | null): string {
  if (v === null) return '-';
  if (v >= 100) {
    const eok = v / 100;
    return eok === Math.floor(eok) ? `${Math.floor(eok)}억` : `${eok.toFixed(1)}억`;
  }
  return `${v.toLocaleString()}백만원`;
}

function extractItems(apiData: any): any[] {
  const items = apiData?.response?.body?.items?.item;
  if (!items) return [];
  return Array.isArray(items) ? items : [items];
}

function parseFloorMap(floorItems: FloorItem[]): Record<number, Array<{ purps: string; area: number }>> {
  const fmap: Record<number, Array<{ purps: string; area: number }>> = {};
  for (const item of floorItems) {
    const flrGb = String(item.flrGbCd || '');
    const flrNo = parseInt(item.flrNo) || 0;
    let key: number;
    if (flrGb === '10') key = -flrNo;
    else if (flrGb === '30') key = 1000 + flrNo;
    else key = flrNo;
    const purps = (item.etcPurps || '').trim();
    const area  = parseFloat(item.area) || 0;
    if (!fmap[key]) fmap[key] = [];
    if (purps || area) fmap[key].push({ purps, area });
  }
  return fmap;
}

function classifyArea(floorItems: FloorItem[]): { residential: number; commercial: number } {
  let residential = 0, commercial = 0;
  for (const item of floorItems) {
    if (String(item.flrGbCd) !== '20') continue;
    const area = parseFloat(item.area) || 0;
    const code = String(item.mainPurpsCd || '').substring(0, 2);
    if (code === '01' || code === '02') residential += area;
    else commercial += area;
  }
  return { residential, commercial };
}

// ── 건물 SVG 시각화 ────────────────────────────────────────────────────────────

const FLOOR_H    = 26;
const FLOOR_GAP  = 2;
const FLOOR_W    = 170;
const STEP       = FLOOR_H + FLOOR_GAP;
const SVG_LEFT   = 10;
const SVG_BOTTOM = 10;

function BuildingVisual({
  grndFlrCnt, ugrndFlrCnt, additionalFloors, floorItems
}: {
  grndFlrCnt: number;
  ugrndFlrCnt: number;
  additionalFloors: number;
  floorItems: FloorItem[];
}) {
  const fmap = useMemo(() => parseFloorMap(floorItems), [floorItems]);
  const { residential, commercial } = useMemo(() => classifyArea(floorItems), [floorItems]);

  if (!grndFlrCnt && !ugrndFlrCnt) {
    return (
      <div className="bld-empty" style={{ padding: '40px', fontSize: '13px', color: '#aaa' }}>
        주소를 검색하면 건물 단면도가 표시됩니다
      </div>
    );
  }

  const fullAdd   = Math.floor(additionalFloors);
  const fracAdd   = additionalFloors - fullAdd;
  const rooftopKeys = Object.keys(fmap).map(Number).filter(k => k >= 1000).sort();

  // Build floor list bottom-to-top
  type FloorEntry = { label: string; color: string; edge: string; floorKey?: number; partial?: number };
  const floors: FloorEntry[] = [];

  for (let i = 0; i < ugrndFlrCnt; i++) {
    const depthLabel = ugrndFlrCnt - i;
    floors.push({ label: `B${depthLabel}`, color: '#5c4a3a', edge: '#3d2e20', floorKey: -depthLabel });
  }
  for (let i = 0; i < grndFlrCnt; i++) {
    floors.push({ label: `${i + 1}F`, color: '#405D72', edge: '#2d3e50', floorKey: i + 1 });
  }
  for (const rk of rooftopKeys) {
    const lbl = rooftopKeys.length > 1 ? `옥탑${rk - 1000}층` : '옥탑';
    floors.push({ label: lbl, color: '#8d6e63', edge: '#5d4037', floorKey: rk });
  }
  for (let i = 0; i < fullAdd; i++) {
    floors.push({ label: `+${i + 1}`, color: '#2ecc71', edge: '#27ae60' });
  }

  const totalSlots = floors.length + (fracAdd > 0.01 ? 1 : 0);
  const svgH = Math.max(150, totalSlots * STEP + SVG_BOTTOM * 2);
  const svgW = 310;

  // y position for floor at bottom-index idx (0=bottom)
  const floorY = (idx: number) => svgH - SVG_BOTTOM - (idx + 1) * STEP;

  return (
    <svg viewBox={`0 0 ${svgW} ${svgH}`} style={{ width: '100%', maxHeight: '460px' }}>
      {floors.map((fl, idx) => {
        const y   = floorY(idx);
        const key = fl.floorKey;
        const entries = (key !== undefined && fmap[key]) ? fmap[key] : [];
        const usageParts = entries.slice(0, 2).map(e =>
          e.purps && e.area ? `${e.purps} ${e.area.toFixed(1)}㎡` : e.purps
        ).filter(Boolean);
        const usageText = usageParts.join(' / ');

        return (
          <g key={idx}>
            <rect x={SVG_LEFT} y={y} width={FLOOR_W} height={FLOOR_H}
              fill={fl.color} stroke={fl.edge} strokeWidth={0.6} rx={2} />
            <text x={SVG_LEFT + 6} y={y + FLOOR_H / 2 + 4}
              fill="white" fontSize={10} fontWeight="bold">{fl.label}</text>
            {usageText && (
              <text x={SVG_LEFT + FLOOR_W - 5} y={y + FLOOR_H / 2 + 4}
                fill="rgba(255,255,255,0.9)" fontSize={9} textAnchor="end">
                {usageText}
              </text>
            )}
          </g>
        );
      })}

      {/* 부분 증축 층 */}
      {fracAdd > 0.01 && (() => {
        const idx = floors.length;
        const y   = floorY(idx);
        const w   = fracAdd * FLOOR_W;
        return (
          <g>
            <rect x={SVG_LEFT} y={y} width={w} height={FLOOR_H}
              fill="#2ecc71" stroke="#27ae60" strokeWidth={0.6} rx={2} />
            <text x={SVG_LEFT + 6} y={y + FLOOR_H / 2 + 4}
              fill="white" fontSize={10} fontWeight="bold">+{fullAdd + 1}</text>
          </g>
        );
      })()}

      {/* 우측 주택/상가 면적 요약 */}
      {(residential > 0 || commercial > 0) && (() => {
        const total = residential + commercial;
        const midY  = svgH / 2;
        const lines: string[] = [];
        if (residential > 0) lines.push(`주택 ${residential.toFixed(1)}㎡ (${Math.round(residential / total * 100)}%)`);
        if (commercial > 0)  lines.push(`상가 ${commercial.toFixed(1)}㎡ (${Math.round(commercial / total * 100)}%)`);
        const startY = midY - (lines.length - 1) * 9;
        return lines.map((line, i) => (
          <text key={i} x={SVG_LEFT + FLOOR_W + 10} y={startY + i * 18}
            fill="#405D72" fontSize={9} fontWeight="bold">{line}</text>
        ));
      })()}
    </svg>
  );
}

// ── 메인 페이지 ───────────────────────────────────────────────────────────────

const defaultBuilding: BuildingForm = {
  bldNm: '', platArea: '', archArea: '', bcRat: '',
  totArea: '', vlRat: '', vlEstmArea: '',
  grndFlrCnt: '', ugrndFlrCnt: '', elevator: '', parking: '',
  useAprDay: '', zoneType: '', districtPlan: '', violation: '해당없음',
};

const defaultUser: UserInput = {
  salePrice: '', deposit: '', monthlyRent: '',
  maxBcRat: '', maxVlRat: '',
};

export default function BuildingAnalysisPage() {
  const [addressInput, setAddressInput] = useState('');
  const [candidates, setCandidates]     = useState<GeoCandidate[]>([]);
  const [selected, setSelected]         = useState<GeoCandidate | null>(null);
  const [loading, setLoading]           = useState(false);
  const [error, setError]               = useState('');

  const [building, setBuilding] = useState<BuildingForm>(defaultBuilding);
  const [userIn, setUserIn]     = useState<UserInput>(defaultUser);
  const [floorItems, setFloorItems] = useState<FloorItem[]>([]);
  const [analyzed, setAnalyzed] = useState(false);

  // ── 계산값 ────────────────────────────────────────────────────────────────

  const yieldRate = useMemo(() => {
    const rent = toNum(userIn.monthlyRent);
    const sale = toNum(userIn.salePrice);
    const dep  = toNum(userIn.deposit);
    const net  = sale - dep;
    return (rent > 0 && net > 0) ? (rent * 12) / net : null;
  }, [userIn]);

  const vlRatEstm = useMemo(() => {
    const estm = toNum(building.vlEstmArea);
    const plat = toNum(building.platArea);
    return (estm > 0 && plat > 0) ? (estm / plat * 100) : null;
  }, [building.vlEstmArea, building.platArea]);

  const maxFloorsByVl = useMemo(() => {
    const curr  = parseInt(building.grndFlrCnt) || 0;
    const maxVl = toNum(userIn.maxVlRat);
    if (!curr || !maxVl) return null;
    const vl = vlRatEstm ?? toNum(building.vlRat);
    return (vl > 0) ? curr * (maxVl / vl) : null;
  }, [building.grndFlrCnt, building.vlRat, userIn.maxVlRat, vlRatEstm]);

  const additionalFloors = useMemo(() => {
    const curr = parseInt(building.grndFlrCnt) || 0;
    if (maxFloorsByVl !== null && curr > 0) return Math.max(0, maxFloorsByVl - curr);
    return null;
  }, [maxFloorsByVl, building.grndFlrCnt]);

  // ── 이벤트 핸들러 ──────────────────────────────────────────────────────────

  const handleSearch = useCallback(async () => {
    const addr = addressInput.trim();
    if (!addr) return;
    setLoading(true);
    setError('');
    setCandidates([]);
    setSelected(null);
    setAnalyzed(false);

    try {
      const res = await fetch(`${API_BASE}/api/building/geocode`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ address: addr }),
      });
      const data = await res.json();
      if (data.status !== 'success' || !data.candidates?.length) {
        setError('주소를 찾을 수 없습니다. 다른 검색어를 입력해보세요.');
        return;
      }
      const cands: GeoCandidate[] = data.candidates;
      setCandidates(cands);
      if (cands.length === 1) {
        await selectCandidate(cands[0]);
      }
    } catch (e) {
      setError('서버 연결 오류. 잠시 후 다시 시도해주세요.');
    } finally {
      setLoading(false);
    }
  }, [addressInput]);

  const selectCandidate = useCallback(async (cand: GeoCandidate) => {
    setSelected(cand);
    setLoading(true);
    setError('');
    try {
      const res = await fetch(`${API_BASE}/api/building/analyze`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          sigunguCd: cand.sigunguCd,
          bjdongCd:  cand.bjdongCd,
          platGbCd:  cand.platGbCd,
          bun:       cand.bun,
          ji:        cand.ji,
          x:         cand.x,
          y:         cand.y,
        }),
      });
      const data = await res.json();
      fillFromApiResponse(data, cand);
      setAnalyzed(true);
    } catch (e) {
      setError('건물 정보 조회 중 오류가 발생했습니다.');
    } finally {
      setLoading(false);
    }
  }, []);

  const fillFromApiResponse = (data: any, cand: GeoCandidate) => {
    // 표제부 파싱
    const titleItems = extractItems(data.title);
    const title = titleItems[0] || {};

    const elevator = (parseInt(title.rideUseElvtCnt) || 0) + (parseInt(title.emgenUseElvtCnt) || 0);
    const parking  = parseInt(title.totPkngCnt) ||
      (parseInt(title.indrMechUtcnt) || 0) + (parseInt(title.oudrMechUtcnt) || 0) +
      (parseInt(title.indrAutoUtcnt) || 0) + (parseInt(title.oudrAutoUtcnt) || 0);

    let useApr = title.useAprDay || '';
    if (useApr.length === 8) {
      useApr = `${useApr.slice(0, 4)}-${useApr.slice(4, 6)}-${useApr.slice(6, 8)}`;
    }

    // 기본개요에서 용도지역 파싱
    const basisItems = extractItems(data.basis);
    let zone = '';
    for (const item of basisItems) {
      const z = (item.jiyukCdNm || '').split(',')[0].trim();
      if (ZONE_ORDER.includes(z)) { zone = z; break; }
      if (z && !zone) zone = z;
    }

    // 지구단위계획
    const districtPlan = data.districtPlan || '';

    // 층별개요 파싱
    const floorRaw = extractItems(data.floors);
    setFloorItems(floorRaw as FloorItem[]);

    // 용도지역 → 최대건폐율/용적률 자동 적용
    const limits = ZONE_LIMITS[zone];

    setBuilding(prev => ({
      ...prev,
      bldNm:      title.bldNm      || '',
      platArea:   title.platArea   || '',
      archArea:   title.archArea   || '',
      bcRat:      title.bcRat      || '',
      totArea:    title.totArea    || '',
      vlRat:      title.vlRat      || '',
      vlEstmArea: title.vlRatEstmTotArea || '',
      grndFlrCnt: title.grndFlrCnt ? String(title.grndFlrCnt) : '',
      ugrndFlrCnt: title.ugrndFlrCnt ? String(title.ugrndFlrCnt) : '',
      elevator:   elevator > 0 ? String(elevator) : '',
      parking:    parking  > 0 ? String(parking)  : '',
      useAprDay:  useApr,
      zoneType:   zone,
      districtPlan,
    }));

    if (limits) {
      setUserIn(prev => ({
        ...prev,
        maxBcRat: String(limits.bc),
        maxVlRat: String(limits.vl),
      }));
    }
  };

  const handleZoneChange = (zone: string) => {
    setBuilding(prev => ({ ...prev, zoneType: zone }));
    const limits = ZONE_LIMITS[zone];
    if (limits) {
      setUserIn(prev => ({
        ...prev,
        maxBcRat: String(limits.bc),
        maxVlRat: String(limits.vl),
      }));
    }
  };

  const handleCalc = () => {
    // 계산은 useMemo로 이미 실시간 반영됨. 버튼은 시각적 확인용.
    setAnalyzed(true);
  };

  const setBld = (key: keyof BuildingForm) => (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) =>
    setBuilding(prev => ({ ...prev, [key]: e.target.value }));

  const setUsr = (key: keyof UserInput) => (e: React.ChangeEvent<HTMLInputElement>) => {
    const v = commas(e.target.value);
    setUserIn(prev => ({ ...prev, [key]: v }));
  };

  // ── 분석 요약 행 ───────────────────────────────────────────────────────────

  const grnd    = parseInt(building.grndFlrCnt) || 0;
  const ugrnd   = parseInt(building.ugrndFlrCnt) || 0;
  const platN   = toNum(building.platArea);
  const saleN   = toNum(userIn.salePrice);

  const addFlStr = () => {
    if (additionalFloors === null) return '-';
    if (additionalFloors > 0) return `+${additionalFloors.toFixed(1)}층 가능`;
    return '증축 불가';
  };

  const summaryRows: [string, React.ReactNode][] = [
    ['매매가', saleN > 0 ? (
      <>{fmtPrice(saleN)}{platN > 0 && (
        <span style={{ color: '#888', fontSize: 11, marginLeft: 6 }}>
          (평당 {fmtPrice(saleN / (platN / 3.3))})
        </span>
      )}</>
    ) : '-'],
    ['기보증금/월세', `${fmtPrice(toNum(userIn.deposit) || null)} / ${fmt(toNum(userIn.monthlyRent) || null, '만원', 0)}`],
    ['수익률', yieldRate !== null ? <b style={{ color: '#2ecc71' }}>{yieldRate.toFixed(2)}%</b> : '-'],
    ['현재 지상층', grnd ? `${grnd}층` : '-'],
    ['용적률 기준 최대층', maxFloorsByVl !== null ? `${maxFloorsByVl.toFixed(1)}층` : '-'],
    ['추가 증축 가능층', additionalFloors !== null ? (
      <b style={{ color: additionalFloors > 0 ? '#2ecc71' : '#e74c3c' }}>{addFlStr()}</b>
    ) : '-'],
    ['건폐율 현재/최대', `${building.bcRat ? building.bcRat + '%' : '-'} / ${userIn.maxBcRat ? userIn.maxBcRat + '%' : '-'}`],
    ['용적률 현재/최대', `${building.vlRat ? building.vlRat + '%' : '-'} / ${userIn.maxVlRat ? userIn.maxVlRat + '%' : '-'}`],
  ];

  // ── 렌더링 ────────────────────────────────────────────────────────────────

  return (
    <div className="bld-page">
      {/* 주소 검색 */}
      <div className="bld-search-bar">
        <input
          value={addressInput}
          onChange={e => setAddressInput(e.target.value)}
          onKeyDown={e => e.key === 'Enter' && handleSearch()}
          placeholder="주소를 입력하세요 (예: 서울 종로구 율곡로 1)"
        />
        <button className="bld-btn-search" onClick={handleSearch} disabled={loading}>
          {loading ? '조회 중...' : '조회'}
        </button>
      </div>

      {error && (
        <div style={{ color: '#e53935', padding: '8px 12px', fontSize: 13, marginBottom: 10 }}>
          {error}
        </div>
      )}

      {/* 후보 주소 목록 */}
      {candidates.length > 1 && (
        <div className="bld-candidates">
          <div className="bld-candidates-label">주소 후보 {candidates.length}건 — 선택하세요</div>
          {candidates.map((c, i) => (
            <div
              key={i}
              className={`bld-candidate-item${selected?.address_name === c.address_name ? ' selected' : ''}`}
              onClick={() => selectCandidate(c)}
            >
              {c.address_name}
            </div>
          ))}
        </div>
      )}

      {loading && <div className="bld-loading">건물 정보를 불러오는 중...</div>}

      {/* 메인 레이아웃 */}
      {!loading && (
        <div className="bld-main">
          {/* ── 좌측: 입력 패널 ── */}
          <div className="bld-left">
            {/* 매물 정보 */}
            <div className="bld-group">
              <div className="bld-group-title">매물 정보</div>
              <div className="bld-form">
                <div className="bld-field">
                  <label>매매가</label>
                  <input value={userIn.salePrice}  onChange={setUsr('salePrice')}  placeholder="0" />
                  <span className="bld-unit">백만원</span>
                </div>
                <div className="bld-field">
                  <label>기보증금</label>
                  <input value={userIn.deposit}    onChange={setUsr('deposit')}    placeholder="0" />
                  <span className="bld-unit">백만원</span>
                </div>
                <div className="bld-field">
                  <label>월세</label>
                  <input value={userIn.monthlyRent} onChange={setUsr('monthlyRent')} placeholder="0" />
                  <span className="bld-unit">만원</span>
                </div>
                <div className="bld-field bld-field-yield">
                  <label>수익률</label>
                  <input
                    readOnly
                    value={yieldRate !== null ? `${yieldRate.toFixed(2)}%` : ''}
                    placeholder="자동계산"
                  />
                </div>
              </div>
            </div>

            {/* 건물 정보 */}
            <div className="bld-group">
              <div className="bld-group-title">건물 정보</div>
              <div className="bld-form">
                <div className="bld-field">
                  <label>건물명</label>
                  <input readOnly value={building.bldNm} placeholder="-" />
                </div>
                <div className="bld-field">
                  <label>대지면적</label>
                  <input value={building.platArea} onChange={setBld('platArea')} placeholder="㎡" />
                  <span className="bld-unit">㎡</span>
                </div>
                <div className="bld-field">
                  <label>건축면적</label>
                  <input value={building.archArea} onChange={setBld('archArea')} placeholder="㎡" />
                  <span className="bld-unit">㎡</span>
                </div>
                <div className="bld-field">
                  <label>건폐율</label>
                  <input value={building.bcRat} onChange={setBld('bcRat')} placeholder="%" />
                  <span className="bld-unit">%</span>
                </div>
                <div className="bld-field">
                  <label>연면적</label>
                  <input value={building.totArea} onChange={setBld('totArea')} placeholder="㎡" />
                  <span className="bld-unit">㎡</span>
                </div>
                <div className="bld-field">
                  <label>용적률</label>
                  <input value={building.vlRat} onChange={setBld('vlRat')} placeholder="%" />
                  <span className="bld-unit">%</span>
                </div>
                <div className="bld-field">
                  <label>용적률산정연면적</label>
                  <input value={building.vlEstmArea} onChange={setBld('vlEstmArea')} placeholder="㎡" />
                  <span className="bld-unit">㎡</span>
                </div>
                <div className="bld-field">
                  <label>최대건폐율</label>
                  <input value={userIn.maxBcRat}
                    onChange={e => setUserIn(p => ({ ...p, maxBcRat: e.target.value }))}
                    placeholder="%" />
                  <span className="bld-unit">%</span>
                </div>
                <div className="bld-field">
                  <label>최대용적률</label>
                  <input value={userIn.maxVlRat}
                    onChange={e => setUserIn(p => ({ ...p, maxVlRat: e.target.value }))}
                    placeholder="%" />
                  <span className="bld-unit">%</span>
                </div>
                <div className="bld-field">
                  <label>지상층수</label>
                  <input value={building.grndFlrCnt} onChange={setBld('grndFlrCnt')} placeholder="층" />
                  <span className="bld-unit">층</span>
                </div>
                <div className="bld-field">
                  <label>지하층수</label>
                  <input value={building.ugrndFlrCnt} onChange={setBld('ugrndFlrCnt')} placeholder="층" />
                  <span className="bld-unit">층</span>
                </div>
                <div className="bld-field">
                  <label>위반건축물</label>
                  <select value={building.violation} onChange={setBld('violation')}>
                    <option>해당없음</option>
                    <option>위반건축물</option>
                  </select>
                </div>
                <div className="bld-field">
                  <label>용도지역</label>
                  <div className="bld-zone-row" style={{ flex: 1 }}>
                    <select className="bld-zone-select" value={building.zoneType}
                      onChange={e => handleZoneChange(e.target.value)}>
                      <option value="">-- 선택 --</option>
                      {ZONE_ORDER.map(z => (
                        <option key={z} value={z}>{z} (BC {ZONE_LIMITS[z].bc}% / VL {ZONE_LIMITS[z].vl}%)</option>
                      ))}
                    </select>
                    {building.zoneType && !ZONE_ORDER.includes(building.zoneType) && (
                      <span style={{ fontSize: 11, color: '#888' }}>{building.zoneType}</span>
                    )}
                  </div>
                </div>
                <div className="bld-field">
                  <label>지구단위계획</label>
                  <input value={building.districtPlan} onChange={setBld('districtPlan')} placeholder="-" />
                </div>
                <div className="bld-field">
                  <label>주차대수</label>
                  <input value={building.parking} onChange={setBld('parking')} placeholder="대" />
                  <span className="bld-unit">대</span>
                </div>
                <div className="bld-field">
                  <label>승강기</label>
                  <input value={building.elevator} onChange={setBld('elevator')} placeholder="대" />
                  <span className="bld-unit">대</span>
                </div>
                <div className="bld-field">
                  <label>사용승인일</label>
                  <input value={building.useAprDay} onChange={setBld('useAprDay')} placeholder="YYYY-MM-DD" />
                </div>
              </div>
            </div>

            <button className="bld-btn-calc" onClick={handleCalc}>
              계산하기
            </button>
          </div>

          {/* ── 우측: 시각화 + 요약 ── */}
          <div className="bld-right">
            {analyzed ? (
              <>
                {/* 건물 시각화 + 증축 카드 */}
                <div className="bld-vis-row">
                  <div className="bld-svg-card">
                    <BuildingVisual
                      grndFlrCnt={grnd}
                      ugrndFlrCnt={ugrnd}
                      additionalFloors={additionalFloors ?? 0}
                      floorItems={floorItems}
                    />
                  </div>
                  <div className="bld-info-card">
                    <div className="bld-info-card-title">증축 분석</div>
                    <div className="bld-info-row">
                      <span className="bld-info-label">현재 층수</span>
                      <span className="bld-info-value">{grnd ? `${grnd}층` : '-'}</span>
                    </div>
                    <div className="bld-info-row">
                      <span className="bld-info-label">최대 층수</span>
                      <span className="bld-info-value">
                        {maxFloorsByVl !== null ? `${maxFloorsByVl.toFixed(1)}층` : '-'}
                      </span>
                    </div>
                    <div className="bld-info-row">
                      <span className="bld-info-label">증축 가능</span>
                      <span className={`bld-info-value ${
                        additionalFloors === null ? '' :
                        additionalFloors > 0 ? 'add-possible' : 'add-impossible'
                      }`}>
                        {additionalFloors === null ? '-' :
                         additionalFloors > 0 ? `+${additionalFloors.toFixed(1)}층` : '불가'}
                      </span>
                    </div>
                    <div className="bld-info-row">
                      <span className="bld-info-label">현재 용적률</span>
                      <span className="bld-info-value">{building.vlRat ? `${building.vlRat}%` : '-'}</span>
                    </div>
                    <div className="bld-info-row">
                      <span className="bld-info-label">최대 용적률</span>
                      <span className="bld-info-value">{userIn.maxVlRat ? `${userIn.maxVlRat}%` : '-'}</span>
                    </div>
                  </div>
                </div>

                {/* 분석 요약 테이블 */}
                <div className="bld-summary-card">
                  <div className="bld-summary-title">분석 요약</div>
                  <table className="bld-summary-table">
                    <tbody>
                      {summaryRows.map(([label, value], i) => (
                        <tr key={i}>
                          <td>{label}</td>
                          <td>{value}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>

                {/* 층별 현황 */}
                {floorItems.length > 0 && (
                  <div className="bld-floor-detail">
                    <div className="bld-floor-detail-title">층별 현황</div>
                    <table className="bld-floor-table">
                      <thead>
                        <tr>
                          <th>층</th>
                          <th>구분</th>
                          <th>용도</th>
                          <th>면적(㎡)</th>
                        </tr>
                      </thead>
                      <tbody>
                        {floorItems
                          .filter(f => f.area && parseFloat(f.area) > 0)
                          .map((f, i) => (
                            <tr key={i}>
                              <td>{f.flrGbCd === '10' ? `B${f.flrNo}` :
                                   f.flrGbCd === '30' ? `옥탑${f.flrNo}` :
                                   `${f.flrNo}F`}</td>
                              <td>{f.flrGbCd === '10' ? '지하' :
                                   f.flrGbCd === '30' ? '옥탑' : '지상'}</td>
                              <td>{f.etcPurps || '-'}</td>
                              <td style={{ textAlign: 'right' }}>
                                {parseFloat(f.area).toFixed(1)}
                              </td>
                            </tr>
                          ))}
                      </tbody>
                    </table>
                  </div>
                )}

                {/* 주의사항 */}
                <div className="bld-notice">
                  {building.districtPlan && (
                    <strong>* 지구단위계획 해당 지역 ({building.districtPlan})</strong>
                  )}
                  본 증축 분석은 조례 및 법령 기준으로만 산정한 결과입니다.
                  지구단위계획구역 내에서는 별도의 건폐율·용적률·높이 제한 등이
                  적용될 수 있으므로 해당 지구단위계획 내용을 반드시 확인하시기 바랍니다.
                </div>
              </>
            ) : (
              <div className="bld-empty">
                <div style={{ fontSize: 48, marginBottom: 16 }}>🏢</div>
                <div>주소를 검색하면 건물 분석 결과가 표시됩니다.</div>
                <div style={{ marginTop: 8, fontSize: 12 }}>
                  주소 입력 후 조회 버튼을 클릭하세요
                </div>
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
