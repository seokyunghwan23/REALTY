import React, { useState } from 'react';
import './TaxCalculatorPage.css';

// ─── 헬퍼 ─────────────────────────────────────────────────
const commas = (n: number) => Math.round(n).toLocaleString('ko-KR');
const won = (n: number) => commas(n) + '원';
const eokMan = (n: number): string => {
  if (n === 0) return '0원';
  const e = Math.floor(n / 1e8), m = Math.floor((n % 1e8) / 1e4);
  return [e > 0 ? e + '억' : '', m > 0 ? m + '만' : ''].filter(Boolean).join(' ') + '원';
};
const toNum = (s: string) => Number(s.replace(/[^0-9]/g, '')) || 0;
const fmt = (s: string) => {
  const n = s.replace(/[^0-9]/g, '');
  return n ? Number(n).toLocaleString('ko-KR') : '';
};

// ─── 소득세 누진세율 ──────────────────────────────────────
const progressive = (base: number): { rate: number; deduct: number } => {
  if (base <= 14_000_000)    return { rate: 0.06, deduct: 0 };
  if (base <= 50_000_000)    return { rate: 0.15, deduct: 1_260_000 };
  if (base <= 88_000_000)    return { rate: 0.24, deduct: 5_760_000 };
  if (base <= 150_000_000)   return { rate: 0.35, deduct: 15_440_000 };
  if (base <= 300_000_000)   return { rate: 0.38, deduct: 19_940_000 };
  if (base <= 500_000_000)   return { rate: 0.40, deduct: 25_940_000 };
  if (base <= 1_000_000_000) return { rate: 0.42, deduct: 35_940_000 };
  return { rate: 0.45, deduct: 65_940_000 };
};

// ─── 툴팁 ─────────────────────────────────────────────────
const Tip: React.FC<{ text: string }> = ({ text }) => {
  const [v, setV] = useState(false);
  return (
    <span className="tc-tip"
      onMouseEnter={() => setV(true)}
      onMouseLeave={() => setV(false)}>
      <span className="tc-tip-icon">i</span>
      {v && <span className="tc-tip-box">{text}</span>}
    </span>
  );
};

type Row = { label: string; value: string };

const Section: React.FC<{ title: string }> = ({ title }) => (
  <div style={{ fontSize: 11, fontWeight: 700, color: '#888', letterSpacing: 1, marginTop: 4, paddingBottom: 4, borderBottom: '1px solid #eee' }}>
    {title}
  </div>
);

// ══════════════════════════════════════════════════════════
// 1. 취득세
// ══════════════════════════════════════════════════════════
const AcquisitionTax: React.FC = () => {
  const [buyer, setBuyer] = useState<'P' | 'C'>('P');
  const [corpRental, setCorpRental] = useState(false);
  const [prop, setProp] = useState<'house' | 'comm' | 'farm' | 'offi' | 'mixed'>('comm');
  const [hc, setHc] = useState(1);
  const [priceS, setPriceS] = useState('');
  const [areaS, setAreaS] = useState('');
  const [haS, setHaS] = useState('');
  const [caS, setCaS] = useState('');
  const [adj, setAdj] = useState(false);
  const [overpop, setOverpop] = useState(false);
  const [newCorp, setNewCorp] = useState(false);
  const [depop, setDepop] = useState(false);
  const [firstHome, setFirstHome] = useState(false);
  const [birth, setBirth] = useState(false);
  const [temp2, setTemp2] = useState(false);
  const [res, setRes] = useState<any>(null);

  const isHouse = prop === 'house' || prop === 'mixed';

  const calc = () => {
    const price = toNum(priceS);
    const area = parseFloat(areaS) || 0;
    const ha = parseFloat(haS) || 0;
    const ca = parseFloat(caS) || 0;
    if (!price) return;

    const rows: Row[] = [];
    let acq = 0, edu = 0, rural = 0, disc = 0;

    if (prop === 'house' || prop === 'mixed') {
      const totalA = prop === 'mixed' ? (ha + ca) || 1 : 1;
      const hRatio = prop === 'mixed' ? ha / totalA : 1;
      const cRatio = 1 - hRatio;
      const hPrice = price * hRatio;
      const cPrice = price * cRatio;
      const effA = prop === 'mixed' ? ha : area;

      let hr = 0, he = 0, hr2 = 0;

      if (buyer === 'C') {
        if (corpRental) {
          hr = 0.02; he = 0.002; hr2 = 0;
          rows.push({ label: '적용 세율 (법인·임대사업자 최초분양)', value: '취득세 2% 감면' });
        } else {
          hr = 0.12; he = 0.004; hr2 = 0.01;
          rows.push({ label: '적용 세율 (법인·주택)', value: '취득세 12%' });
        }
      } else if (depop) {
        if (hPrice <= 600_000_000)      { hr = 0.01; he = 0.001; }
        else if (hPrice <= 900_000_000) { hr = (hPrice * 2 / 1e8 - 3) / 100; he = hr / 10; }
        else                            { hr = 0.03; he = 0.003; }
        hr2 = effA > 85 ? 0.002 : 0;
        rows.push({ label: '인구감소지역 → 중과 배제', value: `일반세율 ${(hr * 100).toFixed(2)}% 적용` });
      } else if (temp2 && hc === 2) {
        if (hPrice <= 600_000_000)      { hr = 0.01; he = 0.001; }
        else if (hPrice <= 900_000_000) { hr = (hPrice * 2 / 1e8 - 3) / 100; he = hr / 10; }
        else                            { hr = 0.03; he = 0.003; }
        hr2 = effA > 85 ? 0.002 : 0;
        rows.push({ label: '일시적 2주택 → 중과 배제', value: `일반세율 ${(hr * 100).toFixed(2)}% 적용` });
        rows.push({ label: '주의', value: '3년 내 기존 주택 미처분 시 중과 추징' });
      } else if (hc === 2 && adj) {
        hr = 0.08; he = 0.004; hr2 = 0.006;
        rows.push({ label: '적용 세율 (2주택·조정대상지역)', value: '취득세 8%' });
      } else if (hc >= 3 && adj) {
        hr = 0.12; he = 0.004; hr2 = 0.01;
        rows.push({ label: '적용 세율 (3주택+·조정대상지역)', value: '취득세 12%' });
      } else if (hc >= 3 && !adj) {
        hr = 0.08; he = 0.004; hr2 = 0.006;
        rows.push({ label: '적용 세율 (3주택+·비조정)', value: '취득세 8%' });
      } else {
        if (hPrice <= 600_000_000)      { hr = 0.01; he = 0.001; }
        else if (hPrice <= 900_000_000) { hr = (hPrice * 2 / 1e8 - 3) / 100; he = hr / 10; }
        else                            { hr = 0.03; he = 0.003; }
        hr2 = effA > 85 ? 0.002 : 0;
        rows.push({ label: '적용 세율 (일반)', value: `취득세 ${(hr * 100).toFixed(2)}%` });
        if (effA > 85) rows.push({ label: '농어촌특별세 추가', value: '0.2% (85㎡ 초과)' });
      }

      const hAcq = hPrice * hr, hEdu = hPrice * he, hRural = hPrice * hr2;
      let cAcq = 0, cEdu = 0, cRural = 0;
      if (prop === 'mixed' && cPrice > 0) {
        const cr = buyer === 'C' && overpop && newCorp ? 0.12 : 0.04;
        cAcq = cPrice * cr; cEdu = cPrice * 0.004; cRural = cPrice * 0.002;
        rows.push({ label: `상가 부분 (면적비 ${(cRatio * 100).toFixed(0)}%)`, value: `취득세 ${cr * 100}%` });
      }
      acq = hAcq + cAcq; edu = hEdu + cEdu; rural = hRural + cRural;

      if (firstHome && buyer === 'P' && price <= 1_200_000_000) {
        const d = Math.min(hAcq, 2_000_000);
        disc += d;
        rows.push({ label: '생애최초 감면', value: `-${won(d)}` });
      }
      if (birth && buyer === 'P' && price <= 1_200_000_000) {
        const d = Math.min(Math.max(0, hAcq - 2_000_000), 5_000_000);
        disc += d;
        rows.push({ label: '출산가구 감면', value: `-${won(d)}` });
      }
    } else if (prop === 'comm' || prop === 'offi') {
      const cr = buyer === 'C' && overpop && newCorp ? 0.12 : 0.04;
      acq = price * cr; edu = price * 0.004; rural = price * 0.002;
      if (buyer === 'C' && overpop && newCorp) {
        rows.push({ label: '과밀억제권역 법인 중과', value: `취득세 ${cr * 100}%` });
      } else {
        rows.push({ label: `적용 세율 (${prop === 'offi' ? '오피스텔' : '상가'})`, value: `취득세 ${cr * 100}%` });
      }
    } else {
      acq = price * 0.03; edu = price * 0.003;
      rows.push({ label: '적용 세율 (농지)', value: '취득세 3%' });
    }

    const total = Math.max(0, acq + edu + rural - disc);
    setRes({ acq, edu, rural, disc, total, rows });
  };

  return (
    <div className="tc-body">
      <div className="tc-form">
        <Section title="매수 주체" />
        <div className="tc-row">
          <label>개인 / 법인 <Tip text="법인은 주택 취득 시 원칙적으로 12% 중과세율이 적용됩니다." /></label>
          <div className="tc-radios">
            <label><input type="radio" checked={buyer==='P'} onChange={()=>{ setBuyer('P'); setCorpRental(false); }} /> 개인</label>
            <label><input type="radio" checked={buyer==='C'} onChange={()=>setBuyer('C')} /> 법인</label>
          </div>
        </div>
        {buyer === 'C' && (
          <div className="tc-row">
            <label>법인 임대사업자 최초분양 <Tip text="공공지원 민간임대주택을 최초 취득하는 임대사업자 법인은 취득세 감면 혜택을 받을 수 있습니다. 요건 충족 여부 세무사 확인 필요." /></label>
            <label className="tc-check"><input type="checkbox" checked={corpRental} onChange={e=>setCorpRental(e.target.checked)} /> 해당함</label>
          </div>
        )}

        <Section title="물건 정보" />
        <div className="tc-row">
          <label>물건 종류 <Tip text="주택: 아파트·단독주택 등 / 상가: 근린생활시설·업무용 / 농지: 전·답·과수원 / 오피스텔 / 상가주택: 복합건물" /></label>
          <select value={prop} onChange={e=>setProp(e.target.value as any)}>
            <option value="house">주택 (아파트·단독·다가구)</option>
            <option value="comm">상가 (근생·업무용)</option>
            <option value="farm">농지 (전·답·과수원)</option>
            <option value="offi">오피스텔</option>
            <option value="mixed">상가주택 (복합건물)</option>
          </select>
        </div>
        {isHouse && (
          <div className="tc-row">
            <label>취득 후 세대 합산 주택 수 <Tip text="취득 완료 후 세대 전체(배우자·동일 세대원 포함) 주택 수 기준." /></label>
            <select value={hc} onChange={e=>setHc(Number(e.target.value))}>
              <option value={1}>1주택</option>
              <option value={2}>2주택</option>
              <option value={3}>3주택 이상</option>
            </select>
          </div>
        )}
        <div className="tc-row">
          <label>취득가액 (원) <Tip text="실제 매매계약서상 거래금액. 상가·오피스텔은 부가가치세 포함 여부를 확인하세요." /></label>
          <input type="text" inputMode="numeric" value={priceS} onChange={e=>setPriceS(fmt(e.target.value))} placeholder="예: 500,000,000" />
          {priceS && <span className="tc-hint">{eokMan(toNum(priceS))}</span>}
        </div>
        {prop === 'house' && (
          <div className="tc-row">
            <label>전용면적 (㎡) <Tip text="85㎡ 초과 시 농어촌특별세 0.2% 추가. 이하는 면제." /></label>
            <input type="number" value={areaS} onChange={e=>setAreaS(e.target.value)} placeholder="예: 84.9" />
          </div>
        )}
        {prop === 'mixed' && <>
          <div className="tc-row">
            <label>주거 전용면적 (㎡) <Tip text="주거용 면적. 취득가액을 면적 비율로 안분해 주택·상가 세율을 각각 적용합니다." /></label>
            <input type="number" value={haS} onChange={e=>setHaS(e.target.value)} placeholder="예: 60" />
          </div>
          <div className="tc-row">
            <label>상가 전용면적 (㎡) <Tip text="비주거 상가·사무실 부분의 면적." /></label>
            <input type="number" value={caS} onChange={e=>setCaS(e.target.value)} placeholder="예: 40" />
          </div>
        </>}

        <Section title="입지 조건" />
        <div className="tc-row">
          <div className="tc-checks">
            <label>
              <input type="checkbox" checked={adj} onChange={e=>setAdj(e.target.checked)} />
              조정대상지역 <Tip text="서울 전역 등 지정 지역. 2주택 8%, 3주택+ 12% 중과. 국토부 지정 현황 확인 필요." />
            </label>
            <label>
              <input type="checkbox" checked={overpop} onChange={e=>setOverpop(e.target.checked)} />
              과밀억제권역 <Tip text="서울 전역·인천 일부·경기 일부. 법인이 상가·오피스텔 취득 시 설립 5년 미만이면 중과 적용." />
            </label>
            <label>
              <input type="checkbox" checked={depop} onChange={e=>setDepop(e.target.checked)} />
              인구감소지역 <Tip text="행안부 지정 인구감소지역(비수도권 89개 시·군). 다주택 중과 배제, 일반세율 적용." />
            </label>
          </div>
        </div>
        {overpop && buyer === 'C' && (prop === 'comm' || prop === 'offi' || prop === 'mixed') && (
          <div className="tc-row">
            <label>법인 설립 후 5년 미만 <Tip text="과밀억제권역 내 설립·전입 후 5년 미만 법인은 상가·오피스텔 취득 시 취득세 표준세율 × 3배(12%) 중과." /></label>
            <label className="tc-check"><input type="checkbox" checked={newCorp} onChange={e=>setNewCorp(e.target.checked)} /> 해당함</label>
          </div>
        )}

        {isHouse && buyer === 'P' && hc === 2 && (
          <>
            <Section title="특수 조건" />
            <div className="tc-row">
              <label>일시적 2주택 <Tip text="기존 주택 보유 중 신규 주택 취득 후 3년 이내 처분 예정 시 중과 배제. 미처분 시 중과 추징." /></label>
              <label className="tc-check"><input type="checkbox" checked={temp2} onChange={e=>setTemp2(e.target.checked)} /> 일시적 2주택 (3년 내 처분 예정)</label>
            </div>
          </>
        )}

        {isHouse && buyer === 'P' && (
          <>
            <Section title="감면 사항" />
            <div className="tc-row">
              <div className="tc-checks">
                <label>
                  <input type="checkbox" checked={firstHome} onChange={e=>setFirstHome(e.target.checked)} />
                  생애최초 구입 <Tip text="세대원 전원 무주택. 취득가 12억 이하 시 최대 200만원 감면." />
                </label>
                <label>
                  <input type="checkbox" checked={birth} onChange={e=>setBirth(e.target.checked)} />
                  출산가구 <Tip text="만 2세 미만 자녀 포함 가구. 취득가 12억 이하 주택 최대 500만원 감면." />
                </label>
              </div>
            </div>
          </>
        )}

        <button className="tc-calc-btn" onClick={calc}>계산하기</button>
      </div>

      {res && (
        <div className="tc-result">
          <h3>취득세 계산 결과</h3>
          <table className="tc-table">
            <tbody>
              {res.rows.map((r: Row, i: number) => (
                <tr key={i}><td>{r.label}</td><td className="tc-right">{r.value}</td></tr>
              ))}
              <tr className="tc-sep"><td>취득세</td><td className="tc-right">{won(res.acq)}</td></tr>
              <tr><td>지방교육세</td><td className="tc-right">{won(res.edu)}</td></tr>
              <tr><td>농어촌특별세</td><td className="tc-right">{won(res.rural)}</td></tr>
              {res.disc > 0 && <tr className="tc-disc"><td>감면 합계</td><td className="tc-right">-{won(res.disc)}</td></tr>}
              <tr className="tc-total-row">
                <td><strong>납부세액 합계</strong></td>
                <td className="tc-right tc-total-val">
                  <strong>{won(res.total)}</strong>
                  <small>{eokMan(res.total)}</small>
                </td>
              </tr>
            </tbody>
          </table>
          <p className="tc-note">※ 취득 후 6개월 이내 신고·납부 의무.</p>
        </div>
      )}
    </div>
  );
};

// ══════════════════════════════════════════════════════════
// 2. 양도소득세
// ══════════════════════════════════════════════════════════
const CapitalGainsTax: React.FC = () => {
  const [prop, setProp] = useState<'house' | 'other'>('house');
  const [trfS, setTrfS] = useState('');
  const [acqS, setAcqS] = useState('');
  const [expS, setExpS] = useState('');
  const [hy, setHy] = useState('');
  const [ly, setLy] = useState('');
  const [hc, setHc] = useState(1);
  const [adj, setAdj] = useState(false);
  const [isOne, setIsOne] = useState(false);
  const [res, setRes] = useState<any>(null);

  const calc = () => {
    const trf = toNum(trfS), acq = toNum(acqS), exp = toNum(expS);
    const holdYrs = parseFloat(hy) || 0, liveYrs = parseFloat(ly) || 0;
    const gain = trf - acq - exp;
    const rows: Row[] = [
      { label: '양도가액', value: won(trf) },
      { label: '취득가액', value: won(acq) },
      { label: '필요경비', value: won(exp) },
      { label: '양도차익', value: won(gain) },
    ];

    if (gain <= 0) {
      setRes({ rows: [...rows, { label: '결과', value: '양도차익 없음 → 납부세액 없음' }], total: 0 });
      return;
    }

    if (isOne && prop === 'house' && holdYrs >= 2 && liveYrs >= 2) {
      if (trf <= 1_200_000_000) {
        setRes({ rows, total: 0, exempt: true });
        return;
      }
      const exAmt = gain * (1_200_000_000 / trf);
      const taxable = gain - exAmt;
      rows.push({ label: '비과세분 (12억 이하)', value: won(exAmt) });
      rows.push({ label: '과세대상 양도차익', value: won(taxable) });
      let ltcRate = 0;
      if (holdYrs >= 3) {
        const hRate = Math.min(Math.floor(holdYrs) * 4, 40) / 100;
        const lRate = liveYrs >= 2 ? Math.min(Math.floor(liveYrs) * 4, 40) / 100 : 0;
        ltcRate = Math.min(hRate + lRate, 0.80);
        rows.push({ label: `장기보유특별공제 표2 (${(ltcRate*100).toFixed(0)}%)`, value: won(taxable * ltcRate) });
      }
      const base = taxable * (1 - ltcRate);
      rows.push({ label: '과세표준', value: won(base) });
      const { rate, deduct } = progressive(base);
      const tax = Math.max(0, base * rate - deduct);
      const local = tax * 0.1;
      rows.push({ label: `기본세율 (${(rate*100).toFixed(0)}%)`, value: won(tax) });
      rows.push({ label: '지방소득세 (10%)', value: won(local) });
      setRes({ rows, total: tax + local });
      return;
    }

    if (holdYrs < 1) {
      const rate = prop === 'house' ? 0.70 : 0.50;
      const tax = gain * rate;
      const local = tax * 0.1;
      rows.push({ label: `단기세율 ${rate*100}% (1년 미만)`, value: won(tax) });
      rows.push({ label: '지방소득세 (10%)', value: won(local) });
      setRes({ rows, total: tax + local });
      return;
    }
    if (holdYrs < 2) {
      const rate = prop === 'house' ? 0.60 : 0.40;
      const tax = gain * rate;
      const local = tax * 0.1;
      rows.push({ label: `단기세율 ${rate*100}% (1~2년)`, value: won(tax) });
      rows.push({ label: '지방소득세 (10%)', value: won(local) });
      setRes({ rows, total: tax + local });
      return;
    }

    const isHeavy = !isOne && adj && hc >= 2;
    const surcharge = isHeavy ? (hc >= 3 ? 0.30 : 0.20) : 0;
    let ltcRate = 0;
    if (!isHeavy && holdYrs >= 3) {
      ltcRate = Math.min(Math.floor(holdYrs) * 2, 30) / 100;
      rows.push({ label: `장기보유특별공제 표1 (${(ltcRate*100).toFixed(0)}%)`, value: won(gain * ltcRate) });
    }
    if (isHeavy) rows.push({ label: `다주택 중과 (+${surcharge*100}%p)`, value: '장기보유특별공제 배제' });

    const base = gain * (1 - ltcRate);
    rows.push({ label: '과세표준', value: won(base) });
    const { rate, deduct } = progressive(base);
    const basicTax = Math.max(0, base * rate - deduct);
    const surchargeTax = base * surcharge;
    const tax = basicTax + surchargeTax;
    const local = tax * 0.1;
    rows.push({ label: `기본세율 (${(rate*100).toFixed(0)}%)`, value: won(basicTax) });
    if (surchargeTax > 0) rows.push({ label: `중과 (+${surcharge*100}%p)`, value: won(surchargeTax) });
    rows.push({ label: '지방소득세 (10%)', value: won(local) });
    setRes({ rows, total: tax + local });
  };

  return (
    <div className="tc-body">
      <div className="tc-form">
        <div className="tc-row">
          <label>물건 종류 <Tip text="주택·조합원입주권: 단기세율 70%/60% / 상가·토지 등: 단기세율 50%/40%" /></label>
          <div className="tc-radios">
            <label><input type="radio" checked={prop==='house'} onChange={()=>setProp('house')} /> 주택·입주권</label>
            <label><input type="radio" checked={prop==='other'} onChange={()=>setProp('other')} /> 상가·토지·기타</label>
          </div>
        </div>
        <div className="tc-row">
          <label>양도가액 (원) <Tip text="실제 매도한 금액 (계약서상 거래금액)." /></label>
          <input type="text" inputMode="numeric" value={trfS} onChange={e=>setTrfS(fmt(e.target.value))} placeholder="예: 800,000,000" />
          {trfS && <span className="tc-hint">{eokMan(toNum(trfS))}</span>}
        </div>
        <div className="tc-row">
          <label>취득가액 (원) <Tip text="최초 매수한 금액. 증빙 불가 시 양도가액×5% 환산취득가액 또는 기준시가 사용 가능." /></label>
          <input type="text" inputMode="numeric" value={acqS} onChange={e=>setAcqS(fmt(e.target.value))} placeholder="예: 500,000,000" />
          {acqS && <span className="tc-hint">{eokMan(toNum(acqS))}</span>}
        </div>
        <div className="tc-row">
          <label>필요경비 (원) <Tip text="취득세·법무사비·자본적 지출·양도 시 중개보수 등. 단순 수선비·생활비는 불포함." /></label>
          <input type="text" inputMode="numeric" value={expS} onChange={e=>setExpS(fmt(e.target.value))} placeholder="예: 15,000,000" />
        </div>
        <div className="tc-row">
          <label>보유기간 (년) <Tip text="취득일부터 양도일까지. 소수점 가능 (예: 2.5 = 2년 6개월)." /></label>
          <input type="number" step="0.5" min="0" value={hy} onChange={e=>setHy(e.target.value)} placeholder="예: 5" />
        </div>
        {prop === 'house' && (
          <div className="tc-row">
            <label>실제 거주기간 (년) <Tip text="주민등록상 실제 거주 기간. 1주택 비과세 및 장기보유특별공제 표2 적용에 사용." /></label>
            <input type="number" step="0.5" min="0" value={ly} onChange={e=>setLy(e.target.value)} placeholder="예: 3" />
          </div>
        )}
        {prop === 'house' && (
          <div className="tc-row">
            <label>1세대 1주택 <Tip text="양도 시 세대 전체가 1주택만 보유. 보유 2년 + 조정지역은 거주 2년 이상. 양도가 12억 이하면 전액 비과세." /></label>
            <label className="tc-check"><input type="checkbox" checked={isOne} onChange={e=>setIsOne(e.target.checked)} /> 해당함</label>
          </div>
        )}
        {!isOne && (
          <>
            <div className="tc-row">
              <label>양도 시 세대 주택 수 <Tip text="세대 전체 주택 수. 다주택자 중과 여부 판단." /></label>
              <select value={hc} onChange={e=>setHc(Number(e.target.value))}>
                <option value={1}>1주택</option>
                <option value={2}>2주택</option>
                <option value={3}>3주택 이상</option>
              </select>
            </div>
            <div className="tc-row">
              <label>조정대상지역 <Tip text="조정지역 다주택자: 2주택 +20%p, 3주택+ +30%p 중과. 장기보유특별공제 배제." /></label>
              <label className="tc-check"><input type="checkbox" checked={adj} onChange={e=>setAdj(e.target.checked)} /> 조정대상지역</label>
            </div>
          </>
        )}
        <button className="tc-calc-btn" onClick={calc}>계산하기</button>
      </div>

      {res && (
        <div className="tc-result">
          <h3>양도소득세 계산 결과</h3>
          {res.exempt ? (
            <div className="tc-exempt">✓ 1세대 1주택 비과세 요건 충족<br/>납부세액 없음</div>
          ) : (
            <table className="tc-table">
              <tbody>
                {res.rows.map((r: Row, i: number) => (
                  <tr key={i}><td>{r.label}</td><td className="tc-right">{r.value}</td></tr>
                ))}
                <tr className="tc-total-row">
                  <td><strong>납부세액 합계</strong></td>
                  <td className="tc-right tc-total-val">
                    <strong>{won(res.total)}</strong>
                    <small>{eokMan(res.total)}</small>
                  </td>
                </tr>
              </tbody>
            </table>
          )}
          <p className="tc-note">※ 양도일 속한 달 말일로부터 2개월 이내 예정신고·납부.</p>
        </div>
      )}
    </div>
  );
};

// ══════════════════════════════════════════════════════════
// 3. 부가가치세
// ══════════════════════════════════════════════════════════
const VatCalc: React.FC = () => {
  const [totalS, setTotalS] = useState('');
  const [landS, setLandS] = useState('');
  const [bldgS, setBldgS] = useState('');
  const [overall, setOverall] = useState(false);
  const [res, setRes] = useState<any>(null);

  const calc = () => {
    if (overall) { setRes({ overall: true }); return; }
    const total = toNum(totalS), land = toNum(landS), bldg = toNum(bldgS);
    if (!total || !land || !bldg) return;
    const bldgRatio = bldg / (land + bldg);
    const bldgPrice = total * bldgRatio;
    const vat = bldgPrice * 0.1;
    const rows: Row[] = [
      { label: '총 매매가', value: won(total) },
      { label: '토지 기준시가', value: won(land) },
      { label: '건물 기준시가', value: won(bldg) },
      { label: `건물 안분비율 (${(bldgRatio * 100).toFixed(2)}%)`, value: '' },
      { label: '건물 안분가액', value: won(bldgPrice) },
      { label: '부가가치세 (10%)', value: won(vat) },
    ];
    setRes({ rows, vat });
  };

  return (
    <div className="tc-body">
      <div className="tc-form">
        <div className="tc-row">
          <label>포괄양수도 계약 <Tip text="사업 동일성 유지하며 일체 양도 시 부가세 과세 제외. 세금계산서 발급 불가." /></label>
          <label className="tc-check"><input type="checkbox" checked={overall} onChange={e=>setOverall(e.target.checked)} /> 포괄양수도 (부가세 없음)</label>
        </div>
        {!overall && <>
          <div className="tc-row">
            <label>총 매매가 (원) <Tip text="토지+건물 합산 거래금액. 건물 부분만 부가세 과세 대상." /></label>
            <input type="text" inputMode="numeric" value={totalS} onChange={e=>setTotalS(fmt(e.target.value))} placeholder="예: 1,000,000,000" />
            {totalS && <span className="tc-hint">{eokMan(toNum(totalS))}</span>}
          </div>
          <div className="tc-row">
            <label>토지 기준시가 (원) <Tip text="공시지가 × 면적. realtyprice.kr에서 확인." /></label>
            <input type="text" inputMode="numeric" value={landS} onChange={e=>setLandS(fmt(e.target.value))} placeholder="예: 300,000,000" />
          </div>
          <div className="tc-row">
            <label>건물 기준시가 (원) <Tip text="국세청 건물 기준시가. 홈택스(hometax.go.kr) → 기준시가 조회." /></label>
            <input type="text" inputMode="numeric" value={bldgS} onChange={e=>setBldgS(fmt(e.target.value))} placeholder="예: 200,000,000" />
          </div>
        </>}
        <button className="tc-calc-btn" onClick={calc}>계산하기</button>
      </div>
      {res && (
        <div className="tc-result">
          <h3>부가가치세 계산 결과</h3>
          {res.overall ? (
            <div className="tc-exempt">포괄양수도 계약<br/>부가가치세 없음 (0원)</div>
          ) : (
            <table className="tc-table">
              <tbody>
                {res.rows.map((r: Row, i: number) => (
                  <tr key={i} className={r.label.includes('부가가치세') ? 'tc-total-row' : ''}>
                    <td>{r.label}</td>
                    <td className="tc-right">
                      {r.label.includes('부가가치세')
                        ? <><strong>{r.value}</strong><br/><small>{eokMan(res.vat)}</small></>
                        : r.value}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
          <p className="tc-note">※ 상가(건물분)에만 부가세 10% 적용. 토지는 면제.</p>
        </div>
      )}
    </div>
  );
};

// ══════════════════════════════════════════════════════════
// 4. 재산세
// ══════════════════════════════════════════════════════════
const calcHousePropTax = (pub: number) => {
  const base = pub * 0.6;
  let tax = 0;
  if (base <= 60_000_000)       tax = base * 0.001;
  else if (base <= 150_000_000) tax = 60_000 + (base - 60_000_000) * 0.0015;
  else if (base <= 300_000_000) tax = 195_000 + (base - 150_000_000) * 0.0025;
  else                           tax = 570_000 + (base - 300_000_000) * 0.004;
  const urban = base * 0.0014;
  const edu = tax * 0.20;
  return { base, tax, urban, edu, total: tax + urban + edu };
};

const calcLandPropTax = (pub: number, kind: 'separate' | 'general') => {
  const base = pub * 0.7;
  let tax = 0;
  if (kind === 'separate') {
    if (base <= 200_000_000)        tax = base * 0.002;
    else if (base <= 1_000_000_000) tax = 400_000 + (base - 200_000_000) * 0.003;
    else                             tax = 2_800_000 + (base - 1_000_000_000) * 0.004;
  } else {
    if (base <= 50_000_000)         tax = base * 0.002;
    else if (base <= 100_000_000)   tax = 100_000 + (base - 50_000_000) * 0.003;
    else                             tax = 250_000 + (base - 100_000_000) * 0.005;
  }
  const urban = base * 0.0014;
  const edu = tax * 0.20;
  return { base, tax, urban, edu, total: tax + urban + edu };
};

const PropertyTax: React.FC = () => {
  const [kind, setKind] = useState<'house' | 'separate' | 'general'>('house');
  const [pubS, setPubS] = useState('');
  const [res, setRes] = useState<any>(null);

  const calc = () => {
    const pub = toNum(pubS);
    if (!pub) return;
    const r = kind === 'house' ? calcHousePropTax(pub) : calcLandPropTax(pub, kind as 'separate' | 'general');
    setRes({ ...r, pub, kind });
  };

  return (
    <div className="tc-body">
      <div className="tc-form">
        <div className="tc-row">
          <label>물건 종류 <Tip text="주택: 공정시장가액비율 60% / 별도합산토지: 상가 부속토지 70% / 종합합산토지: 나대지·잡종지 70%" /></label>
          <select value={kind} onChange={e=>setKind(e.target.value as any)}>
            <option value="house">주택 (공정시장가액비율 60%)</option>
            <option value="separate">별도합산토지 (상가 부속토지, 70%)</option>
            <option value="general">종합합산토지 (나대지·잡종지, 70%)</option>
          </select>
        </div>
        <div className="tc-row">
          <label>공시가격 (원) <Tip text="주택: 국토부 공동주택 공시가격 / 토지: 개별공시지가 × 면적. realtyprice.kr에서 조회." /></label>
          <input type="text" inputMode="numeric" value={pubS} onChange={e=>setPubS(fmt(e.target.value))} placeholder="예: 500,000,000" />
          {pubS && <span className="tc-hint">{eokMan(toNum(pubS))}</span>}
        </div>
        <div className="tc-row" style={{background:'#fff8e1', borderRadius:'8px', padding:'10px', gap:'4px'}}>
          <span style={{fontSize:'12px', color:'#795548', fontWeight:600}}>📅 납부 기준일</span>
          <span style={{fontSize:'12px', color:'#795548'}}>매년 6월 1일 기준 소유자 납부 의무. 매도 시 잔금일·등기일 중 빠른 날 기준.</span>
        </div>
        <button className="tc-calc-btn" onClick={calc}>계산하기</button>
      </div>
      {res && (
        <div className="tc-result">
          <h3>재산세 계산 결과</h3>
          <table className="tc-table">
            <tbody>
              <tr><td>공시가격</td><td className="tc-right">{won(res.pub)}</td></tr>
              <tr><td>과세표준 ({kind === 'house' ? '60%' : '70%'})</td><td className="tc-right">{won(res.base)}</td></tr>
              <tr className="tc-sep"><td>재산세 본세</td><td className="tc-right">{won(res.tax)}</td></tr>
              <tr><td>도시지역분 (0.14%)</td><td className="tc-right">{won(res.urban)}</td></tr>
              <tr><td>지방교육세 (20%)</td><td className="tc-right">{won(res.edu)}</td></tr>
              <tr className="tc-total-row">
                <td><strong>납부세액 합계</strong></td>
                <td className="tc-right tc-total-val">
                  <strong>{won(res.total)}</strong>
                  <small>{eokMan(res.total)}</small>
                </td>
              </tr>
            </tbody>
          </table>
          <p className="tc-note">※ 주택: 20만원 이하 7월 전액 / 초과 시 7월·9월 각 50% 분납. 토지: 9월 납부.</p>
        </div>
      )}
    </div>
  );
};

// ══════════════════════════════════════════════════════════
// 5. 종합부동산세
// ══════════════════════════════════════════════════════════
const calcCompTax = (base: number, heavy: boolean): number => {
  if (!heavy) {
    if (base <= 300_000_000)       return base * 0.005;
    if (base <= 600_000_000)       return 1_500_000 + (base - 300_000_000) * 0.007;
    if (base <= 1_200_000_000)     return 3_600_000 + (base - 600_000_000) * 0.010;
    if (base <= 2_500_000_000)     return 9_600_000 + (base - 1_200_000_000) * 0.013;
    if (base <= 5_000_000_000)     return 26_500_000 + (base - 2_500_000_000) * 0.015;
    if (base <= 9_400_000_000)     return 64_000_000 + (base - 5_000_000_000) * 0.020;
    return 152_000_000 + (base - 9_400_000_000) * 0.027;
  } else {
    if (base <= 300_000_000)       return base * 0.012;
    if (base <= 600_000_000)       return 3_600_000 + (base - 300_000_000) * 0.016;
    if (base <= 1_200_000_000)     return 8_400_000 + (base - 600_000_000) * 0.022;
    if (base <= 2_500_000_000)     return 21_600_000 + (base - 1_200_000_000) * 0.036;
    if (base <= 5_000_000_000)     return 68_400_000 + (base - 2_500_000_000) * 0.050;
    return 193_400_000 + (base - 5_000_000_000) * 0.060;
  }
};

const ComprehensiveTax: React.FC = () => {
  const [pubS, setPubS] = useState('');
  const [isOne, setIsOne] = useState(false);
  const [hc, setHc] = useState(2);
  const [adj, setAdj] = useState(false);
  const [res, setRes] = useState<any>(null);

  const calc = () => {
    const pub = toNum(pubS);
    if (!pub) return;
    const deduction = isOne ? 1_200_000_000 : 900_000_000;
    const taxable = pub - deduction;
    if (taxable <= 0) {
      setRes({ pub, deduction, noTax: true });
      return;
    }
    const base = taxable * 0.60;
    const isHeavy = !isOne && ((adj && hc >= 2) || hc >= 3);
    const tax = calcCompTax(base, isHeavy);
    const rural = tax * 0.20;
    setRes({ pub, deduction, taxable, base, tax, rural, total: tax + rural, isHeavy });
  };

  return (
    <div className="tc-body">
      <div className="tc-form">
        <div className="tc-row">
          <label>합산 공시가격 (원) <Tip text="세대 전체 보유 주택의 공시가격 합산. realtyprice.kr에서 확인." /></label>
          <input type="text" inputMode="numeric" value={pubS} onChange={e=>setPubS(fmt(e.target.value))} placeholder="예: 1,200,000,000" />
          {pubS && <span className="tc-hint">{eokMan(toNum(pubS))}</span>}
        </div>
        <div className="tc-row">
          <label>1세대 1주택 <Tip text="1세대 1주택자: 기본공제 12억 / 다주택자: 9억." /></label>
          <label className="tc-check"><input type="checkbox" checked={isOne} onChange={e=>setIsOne(e.target.checked)} /> 1세대 1주택</label>
        </div>
        {!isOne && <>
          <div className="tc-row">
            <label>세대 보유 주택 수 <Tip text="중과세율 적용 여부 판단." /></label>
            <select value={hc} onChange={e=>setHc(Number(e.target.value))}>
              <option value={2}>2주택</option>
              <option value={3}>3주택 이상</option>
            </select>
          </div>
          <div className="tc-row">
            <label>조정대상지역 <Tip text="조정지역 2주택 또는 3주택 이상 보유 시 중과세율(최대 6%) 적용." /></label>
            <label className="tc-check"><input type="checkbox" checked={adj} onChange={e=>setAdj(e.target.checked)} /> 조정대상지역</label>
          </div>
        </>}
        <button className="tc-calc-btn" onClick={calc}>계산하기</button>
      </div>
      {res && (
        <div className="tc-result">
          <h3>종합부동산세 계산 결과</h3>
          {res.noTax ? (
            <div className="tc-exempt">공제액({eokMan(res.deduction)}) 이하<br/>종부세 과세 대상 아님</div>
          ) : (
            <table className="tc-table">
              <tbody>
                <tr><td>합산 공시가격</td><td className="tc-right">{won(res.pub)}</td></tr>
                <tr><td>기본공제 ({isOne ? '1주택 12억' : '9억'})</td><td className="tc-right">-{won(res.deduction)}</td></tr>
                <tr><td>과세 대상</td><td className="tc-right">{won(res.taxable)}</td></tr>
                <tr><td>공정시장가액비율 (60%)</td><td className="tc-right">{won(res.base)}</td></tr>
                {res.isHeavy && <tr><td>적용 세율</td><td className="tc-right">중과세율</td></tr>}
                <tr className="tc-sep"><td>종합부동산세</td><td className="tc-right">{won(res.tax)}</td></tr>
                <tr><td>농어촌특별세 (20%)</td><td className="tc-right">{won(res.rural)}</td></tr>
                <tr className="tc-total-row">
                  <td><strong>납부세액 합계</strong></td>
                  <td className="tc-right tc-total-val">
                    <strong>{won(res.total)}</strong>
                    <small>{eokMan(res.total)}</small>
                  </td>
                </tr>
              </tbody>
            </table>
          )}
          <p className="tc-note">※ 매년 12월 1~15일 납부. 250만원 초과 시 6개월 분납 가능.</p>
        </div>
      )}
    </div>
  );
};

// ══════════════════════════════════════════════════════════
// 메인 페이지
// ══════════════════════════════════════════════════════════
const TABS = [
  { id: 'acq',  label: '취득세' },
  { id: 'cgt',  label: '양도소득세' },
  { id: 'vat',  label: '부가가치세' },
  { id: 'prop', label: '재산세' },
  { id: 'comp', label: '종합부동산세' },
];

const TaxCalculatorPage: React.FC = () => {
  const [tab, setTab] = useState('acq');
  return (
    <div className="tc-page">
      <div className="tc-header">
        <h2>부동산 5대 세금 계산기</h2>
        <p>2026년 세법 기준 · 참고용 계산기</p>
      </div>
      <div className="tc-tabs">
        {TABS.map(t => (
          <button key={t.id} className={`tc-tab${tab === t.id ? ' active' : ''}`} onClick={() => setTab(t.id)}>
            {t.label}
          </button>
        ))}
      </div>
      {tab === 'acq'  && <AcquisitionTax />}
      {tab === 'cgt'  && <CapitalGainsTax />}
      {tab === 'vat'  && <VatCalc />}
      {tab === 'prop' && <PropertyTax />}
      {tab === 'comp' && <ComprehensiveTax />}
      <footer className="tc-footer">
        <p>
          ⚠️ 본 계산기는 <strong>참고용</strong>이며, 실제 세액은 과세 관청의 결정·개별 감면 적용 등에 따라 달라질 수 있습니다.
          정확한 세금은 반드시 <strong>세무사</strong>와 상담하시기 바랍니다.
        </p>
      </footer>
    </div>
  );
};

export default TaxCalculatorPage;
