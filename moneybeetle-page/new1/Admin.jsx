const { useState, useEffect, useMemo } = React;

const SECTORS0 = ['반도체', '모빌리티', '에너지', '콘텐츠', '로봇', '바이오', '핀테크', '식품', '클라우드', '헬스케어', '생활', '물류', '교육', '소재', '여행'];
const BASE = [
  ['한빛반도체', '반도체', 84500], ['모아모빌리티', '모빌리티', 32800], ['그린셀에너지', '에너지', 18250], ['별숲엔터', '콘텐츠', 45100],
  ['뉴웨이브로보틱스', '로봇', 71300], ['온담바이오', '바이오', 26900], ['미래칩스', '반도체', 56700], ['라온드라이브', '모빌리티', 29400],
  ['해솔파워', '에너지', 22150], ['다온스튜디오', '콘텐츠', 38600], ['키움메카트로닉스', '로봇', 63900], ['새봄제약', '바이오', 41250],
  ['구름핀테크', '핀테크', 19800], ['보름페이', '핀테크', 24300], ['참좋은푸드', '식품', 15750], ['산들키친', '식품', 21900],
  ['푸른클라우드', '클라우드', 48200], ['코어스택', '클라우드', 53100], ['마음케어', '헬스케어', 31400], ['바른메디', '헬스케어', 27650],
  ['이음리빙', '생활', 12900], ['소담하우스', '생활', 17450], ['길벗로지스', '물류', 36800], ['빠른배송', '물류', 42100],
  ['배움나무', '교육', 14600], ['슬기러닝', '교육', 18900], ['맑은소재', '소재', 33500], ['단단컴포짓', '소재', 39700],
  ['누리여행', '여행', 25300], ['쉼표투어', '여행', 20850],
];
const STATUS = ['준비', '진행 중', '휴장', '정산', '종료'];
const REASONS = ['뉴스를 보고', '가격이 싸서', '오를 것 같아서', '나눠 담으려고', '이익을 지키려고', '손실을 줄이려고'];
const ADMIN = '김운영';
const r50 = v => Math.max(10, Math.round(v / 50) * 50);
const fmt = n => (n == null || n === '' || isNaN(n) ? '' : Number(n).toLocaleString());
const pct = n => (n > 0 ? '+' : '') + (n * 100).toFixed(1) + '%';
const now = () => new Date().toLocaleString('ko-KR', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' });

function seedPrices() {
  const rate = (i, t) => (((i * 37 + t * 53) % 13) - 6) / 100;
  const m = BASE.map(([, , p], i) => {
    const a = Array(12).fill(null);
    a[4] = p;
    for (let t = 3; t >= 0; t--) a[t] = r50(a[t + 1] / (1 + rate(i, t + 1)));
    a[5] = r50(a[4] * (1 + rate(i, 5)));
    return a;
  });
  m[7][5] = null; m[21][5] = null;
  return m;
}
function seedNews() {
  const N = (id, turn, title, sectors, stocks, memo) => ({ id, turn, title, body: title + '. 관련 업계는 다음 분기까지 흐름이 이어질 것으로 보고 있어요.', sectors, stocks, memo: memo || '' });
  return [
    N(1, 1, '새 시즌 개장, 가상 시장 첫 거래 시작', ['반도체', '에너지'], ['한빛반도체', '그린셀에너지'], '시즌 첫날 분위기 조성'),
    N(2, 1, '식품 원재료 가격 안정세', ['식품'], ['참좋은푸드', '산들키친']),
    N(3, 2, '클라우드 수요 꾸준히 증가', ['클라우드'], ['푸른클라우드', '코어스택']),
    N(4, 2, '여행 성수기 앞두고 예약 증가', ['여행'], ['누리여행', '쉼표투어']),
    N(5, 3, '원재료 가격 급등에 제조업 부담', ['소재', '모빌리티'], ['맑은소재', '모아모빌리티'], '급락 턴 설계 — 겁먹고 파는지 관찰'),
    N(6, 3, '바이오 신약 임상 중간 결과 발표', ['바이오'], ['온담바이오', '새봄제약']),
    N(7, 4, '간편결제 이용자 1천만 명 돌파', ['핀테크'], ['구름핀테크', '보름페이']),
    N(8, 4, '물류센터 자동화 투자 확대', ['물류', '로봇'], ['길벗로지스', '키움메카트로닉스']),
    N(9, 5, 'AI 기기 수요 확대, 반도체 업계에 훈풍', ['반도체', '로봇'], ['한빛반도체', '뉴웨이브로보틱스']),
    N(10, 5, '도심형 전기차 보조금 확대안 발표', ['모빌리티', '에너지'], ['모아모빌리티', '그린셀에너지']),
    N(11, 5, 'K-콘텐츠 신규 구독자 수 역대 최고치', ['콘텐츠'], ['별숲엔터']),
    N(12, 6, '헬스케어 플랫폼 이용 시간 증가', ['헬스케어'], ['마음케어', '바른메디'], '분산 투자 유도'),
  ];
}
function seedPeople() {
  const P = (id, nick, level, investable, cash, wallet, holdings, trades) => ({ id, nick, level, investable, cash, wallet, holdings, trades });
  const T = (turn, s, type, qty, price, reason) => ({ turn, s, type, qty, price, reason });
  return [
    P(1, '민지', 8, 4820000, 2939000, 12400, [[0, 12], [2, 40], [3, 10]], [T(4, 0, 'buy', 6, 81200, '뉴스를 보고'), T(3, 2, 'buy', 40, 18640, '가격이 싸서'), T(2, 3, 'buy', 10, 40500, '오를 것 같아서'), T(1, 0, 'buy', 6, 76800, '나눠 담으려고')]),
    P(2, '복리왕', 15, 9200000, 1850000, 48200, [[0, 40], [16, 50], [6, 20]], [T(5, 16, 'buy', 50, 47000, '뉴스를 보고'), T(3, 26, 'sell', 30, 31000, '손실을 줄이려고'), T(1, 0, 'buy', 40, 78000, '나눠 담으려고')]),
    P(3, '적립식', 12, 7600000, 2400000, 30100, [[11, 60], [20, 100]], [T(4, 11, 'buy', 60, 40000, '나눠 담으려고'), T(2, 20, 'buy', 100, 12500, '가격이 싸서')]),
    P(4, '나스닥러', 11, 6100000, 900000, 8800, [[4, 50], [10, 20]], [T(5, 4, 'buy', 20, 71300, '오를 것 같아서'), T(3, 1, 'sell', 40, 30500, '손실을 줄이려고'), T(1, 4, 'buy', 30, 65000, '뉴스를 보고')]),
    P(5, '배당요정', 13, 5400000, 3100000, 22000, [[14, 80], [22, 20]], [T(3, 14, 'buy', 80, 15200, '가격이 싸서'), T(2, 22, 'buy', 20, 35500, '나눠 담으려고')]),
    P(6, '분산투자', 9, 4300000, 1200000, 5600, [[12, 50], [18, 30], [28, 20]], [T(3, 28, 'sell', 10, 24000, '손실을 줄이려고'), T(2, 18, 'buy', 30, 30000, '나눠 담으려고'), T(1, 12, 'buy', 50, 19000, '나눠 담으려고')]),
    P(7, '청약준비생', 8, 3200000, 2600000, 3100, [[9, 15]], [T(5, 9, 'buy', 15, 38600, '뉴스를 보고')]),
    P(8, '새싹투자', 8, 2800000, 400000, 1500, [[5, 60], [27, 20]], [T(4, 5, 'buy', 60, 26000, '오를 것 같아서'), T(3, 27, 'sell', 20, 37000, '손실을 줄이려고'), T(3, 27, 'buy', 40, 37800, '오를 것 같아서')]),
  ];
}


const SB_ICON = {dash: <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round"><rect x="3" y="3" width="7" height="9" /><rect x="14" y="3" width="7" height="5" /><rect x="14" y="12" width="7" height="9" /><rect x="3" y="16" width="7" height="5" /></svg>,
  lecture: <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round"><path d="M2 4h6a4 4 0 0 1 4 4v13a3 3 0 0 0-3-3H2z" /><path d="M22 4h-6a4 4 0 0 0-4 4v13a3 3 0 0 1 3-3h7z" /></svg>,
  content: <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round"><path d="M14 3H6a1 1 0 0 0-1 1v16a1 1 0 0 0 1 1h12a1 1 0 0 0 1-1V8z" /><path d="M14 3v5h5M9 13h6M9 17h6" /></svg>,
  training: <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round"><path d="M3 3v18h18" /><path d="m7 15 4-4 3 3 6-6" /></svg>,
  growth: <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round"><path d="m12 2 3 7 7 .6-5.3 4.7 1.6 7.2L12 17.8 5.7 21.5l1.6-7.2L2 9.6 9 9z" /></svg>,
  member: <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round"><circle cx="9" cy="8" r="3.5" /><path d="M2.5 20a6.5 6.5 0 0 1 13 0" /><path d="M16 4.5a3.5 3.5 0 0 1 0 7M18 20a6.5 6.5 0 0 0-2.5-5.1" /></svg>,
  log: <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round"><circle cx="12" cy="12" r="9" /><path d="M12 7v5l3 2" /></svg>};
const SB_GROUPS = [["lecture","강의 운영",[["카테고리 관리","AdminDashboard.dc.html?view=categories"],["강의 검수","AdminDashboard.dc.html?view=review","review"],["공개 강의 관리","AdminDashboard.dc.html?view=courses"]]],["content","홈 콘텐츠",[["오늘의 경제 용어","AdminDashboard.dc.html?view=terms"]]],["training","트레이닝 운영",[["모의 투자","AdminTradingManagement.dc.html",null,"invest"],["모의 청약","AdminHousingManagement.dc.html","sub","sub"]]],["growth","성장·캐시",[["레벨·해금","AdminDashboard.dc.html?view=levels"],["캐시 규칙·지급","AdminDashboard.dc.html?view=cash"]]],["member","회원·게시판",[["사용자·강사","AdminDashboard.dc.html?view=users"],["문의 게시판","AdminDashboard.dc.html?view=board","board"]]]];
const SB_MENU = [["강의 운영",[["카테고리 관리","AdminDashboard.dc.html?view=categories"],["강의 검수","AdminDashboard.dc.html?view=review"],["공개 강의 관리","AdminDashboard.dc.html?view=courses"]]],["홈 콘텐츠",[["오늘의 경제 용어","AdminDashboard.dc.html?view=terms"]]],["트레이닝 운영",[["모의 투자","AdminTradingManagement.dc.html"],["모의 청약","AdminHousingManagement.dc.html"]]],["성장·캐시",[["레벨·해금","AdminDashboard.dc.html?view=levels"],["캐시 규칙·지급","AdminDashboard.dc.html?view=cash"]]],["회원·게시판",[["사용자·강사","AdminDashboard.dc.html?view=users"],["문의 게시판","AdminDashboard.dc.html?view=board"]]]];
const SB_I = {"dash":"<rect x=\"3\" y=\"3\" width=\"7\" height=\"9\"></rect><rect x=\"14\" y=\"3\" width=\"7\" height=\"5\"></rect><rect x=\"14\" y=\"12\" width=\"7\" height=\"9\"></rect><rect x=\"3\" y=\"16\" width=\"7\" height=\"5\"></rect>","강의 운영":"<path d=\"M2 4h6a4 4 0 0 1 4 4v13a3 3 0 0 0-3-3H2z\"></path><path d=\"M22 4h-6a4 4 0 0 0-4 4v13a3 3 0 0 1 3-3h7z\"></path>","홈 콘텐츠":"<path d=\"M14 3H6a1 1 0 0 0-1 1v16a1 1 0 0 0 1 1h12a1 1 0 0 0 1-1V8z\"></path><path d=\"M14 3v5h5M9 13h6M9 17h6\"></path>","트레이닝 운영":"<path d=\"M3 3v18h18\"></path><path d=\"m7 15 4-4 3 3 6-6\"></path>","성장·캐시":"<path d=\"m12 2 3 7 7 .6-5.3 4.7 1.6 7.2L12 17.8 5.7 21.5l1.6-7.2L2 9.6 9 9z\"></path>","회원·게시판":"<circle cx=\"9\" cy=\"8\" r=\"3.5\"></circle><path d=\"M2.5 20a6.5 6.5 0 0 1 13 0\"></path><path d=\"M16 4.5a3.5 3.5 0 0 1 0 7M18 20a6.5 6.5 0 0 0-2.5-5.1\"></path>","작업 기록":"<circle cx=\"12\" cy=\"12\" r=\"9\"></circle><path d=\"M12 7v5l3 2\"></path>"};
const SB_I2 = {"dash":"<rect x=\"3\" y=\"3\" width=\"7\" height=\"9\"></rect><rect x=\"14\" y=\"3\" width=\"7\" height=\"5\"></rect><rect x=\"14\" y=\"12\" width=\"7\" height=\"9\"></rect><rect x=\"3\" y=\"16\" width=\"7\" height=\"5\"></rect>","lecture":"<path d=\"M2 4h6a4 4 0 0 1 4 4v13a3 3 0 0 0-3-3H2z\"></path><path d=\"M22 4h-6a4 4 0 0 0-4 4v13a3 3 0 0 1 3-3h7z\"></path>","content":"<path d=\"M14 3H6a1 1 0 0 0-1 1v16a1 1 0 0 0 1 1h12a1 1 0 0 0 1-1V8z\"></path><path d=\"M14 3v5h5M9 13h6M9 17h6\"></path>","training":"<path d=\"M3 3v18h18\"></path><path d=\"m7 15 4-4 3 3 6-6\"></path>","growth":"<path d=\"m12 2 3 7 7 .6-5.3 4.7 1.6 7.2L12 17.8 5.7 21.5l1.6-7.2L2 9.6 9 9z\"></path>","member":"<circle cx=\"9\" cy=\"8\" r=\"3.5\"></circle><path d=\"M2.5 20a6.5 6.5 0 0 1 13 0\"></path><path d=\"M16 4.5a3.5 3.5 0 0 1 0 7M18 20a6.5 6.5 0 0 0-2.5-5.1\"></path>","log":"<circle cx=\"12\" cy=\"12\" r=\"9\"></circle><path d=\"M12 7v5l3 2\"></path>"};
Object.assign(SB_I, SB_I2);
const SB_G2 = [["lecture","강의 운영",[["카테고리 관리","categories"],["강의 검수","review","review"],["공개 강의 관리","courses"]]],["content","홈 콘텐츠",[["오늘의 경제 용어","terms"]]],["growth","성장·캐시",[["캐시 규칙·지급","cash"]]],["member","회원·게시판",[["사용자·강사","users"],["문의 게시판","board","board"]]]];
const SbIco = ({ k }) => <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" dangerouslySetInnerHTML={{ __html: SB_I[k] }} />;
const TRADE_NI = {"dash":["M3 3h7v9H3z","M14 3h7v5h-7z","M14 12h7v9h-7z","M3 16h7v5H3z"],"season":["M3 5h18v16H3z","M3 10h18","M8 3v4","M16 3v4"],"turn":["M5 4v16l13-8z"],"stocks":["M3 3v18h18","M7 15l4-4 3 3 6-6"],"prices":["M4 4h16v16H4z","M4 10h16","M4 15h16","M10 4v16"],"news":["M4 4h13v16H6a2 2 0 0 1-2-2z","M17 8h3v10a2 2 0 0 1-2 2","M8 8h5","M8 12h5","M8 16h5"],"people":["M9 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8","M2 21a7 7 0 0 1 14 0","M17 3.5a4 4 0 0 1 0 7.5","M22 21a7 7 0 0 0-4-6.3"],"ranking":["M8 21h8","M12 17v4","M7 4h10v5a5 5 0 0 1-10 0z","M17 5h3v2a3 3 0 0 1-3 3","M7 5H4v2a3 3 0 0 0 3 3"],"cash":["M12 2a10 10 0 1 0 0 20 10 10 0 0 0 0-20","M15 9a3 3 0 0 0-3-2h-1a2.5 2.5 0 0 0 0 5h2a2.5 2.5 0 0 1 0 5h-1a3 3 0 0 1-3-2","M12 5v2","M12 17v2"],"log":["M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18","M12 7v5l3 2"]};
function AdminSideNav({ nav, view, onNav, warn }) {
  return (
    <aside data-mb-pinned="1" aria-label="모의 투자 관리 메뉴" style={{ position: 'sticky', top: 0, flex: 'none', width: 'clamp(208px,19vw,280px)', height: '100vh', boxSizing: 'border-box', background: '#fff', borderRight: '1px solid #D5E1CC', display: 'flex', flexDirection: 'column', gap: 20, padding: '24px 16px', overflowY: 'auto', overflowX: 'hidden', zIndex: 35 }}>
      <a href="Main.dc.html" style={{ display: 'block', textDecoration: 'none', padding: '0 6px' }}><img src="assets/logo.png" alt="Money Beetle" style={{ height: 32, width: 'auto', maxWidth: 170, objectFit: 'contain', display: 'block' }} /></a>
      <div style={{ padding: 14, display: 'flex', flexDirection: 'column', gap: 10, border: '1.5px dashed #D5E1CC', borderRadius: 16 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
          <div style={{ width: 40, height: 40, borderRadius: '50%', background: '#263B1A', color: '#9fe870', display: 'grid', placeItems: 'center', fontWeight: 800, fontSize: 14, flex: 'none' }}>운</div>
          <div style={{ display: 'flex', flexDirection: 'column', lineHeight: 1.3, minWidth: 0 }}><span style={{ fontWeight: 700, fontSize: 14 }}>김운영</span><span style={{ fontSize: 12, color: '#687161' }}>관리자 · 모의 투자 운영</span></div>
        </div>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', paddingTop: 10, borderTop: '1px solid #D5E1CC', fontSize: 12 }}><span style={{ fontWeight: 600 }}>다음 턴 점검</span><span style={{ fontWeight: 800 }}>{warn ? '점검 필요' : '이상 없음'}</span></div>
      </div>
      <span style={{ padding: '0 10px', fontSize: 12, fontWeight: 800, letterSpacing: '.06em', color: '#687161' }}>모의 투자 관리</span>
      <nav style={{ display: 'flex', flexDirection: 'column', gap: 2, marginTop: -12 }}>
        {nav.map(([k, label]) => { const on = view === k; return (
          <button key={k} type="button" onClick={onNav(k)} aria-current={on ? 'page' : undefined} style={{ boxSizing: 'border-box', display: 'flex', alignItems: 'center', gap: 12, padding: 10, borderRadius: 10, border: 0, cursor: 'pointer', font: 'inherit', fontSize: 15, textAlign: 'left', width: '100%', color: '#263B1A', background: on ? '#EAF7D8' : 'transparent', fontWeight: on ? 800 : 500 }}>
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" style={{ flex: 'none' }}>{(TRADE_NI[k] || []).map((d, i) => <path key={i} d={d} />)}</svg>
            <span style={{ flex: 1 }}>{label}</span>
            {k === 'turn' && warn ? <span style={{ minWidth: 20, height: 20, padding: '0 6px', boxSizing: 'border-box', borderRadius: 999, background: '#E5484D', color: '#fff', fontSize: 11, fontWeight: 800, display: 'grid', placeItems: 'center' }}>!</span> : null}
          </button>); })}
      </nav>
      <div style={{ flex: 1 }} />
      <a href="AdminDashboard.dc.html?view=invest" style={{ display: 'flex', alignItems: 'center', gap: 10, padding: 10, borderRadius: 10, border: '1px solid #D5E1CC', fontSize: 14, fontWeight: 600, color: '#263B1A', textDecoration: 'none' }}><svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"><path d="m15 18-6-6 6-6" /></svg>관리자 대시보드로</a>
    </aside>
  );
}

const NAV = [
  ['dash', '대시보드'], ['season', '시즌 관리'], ['turn', '턴 진행'], ['stocks', '분야·종목'], ['prices', '턴별 가격'],
  ['news', '뉴스 관리'], ['people', '참가자 관리'], ['ranking', '정산·랭킹'], ['cash', '캐시 규칙'], ['log', '작업 기록'],
];
const HEAD = {
  dash: ['모의투자 대시보드', '진행 중인 시즌과 다음 턴 준비 상태를 한눈에 확인해요'],
  season: ['시즌 관리', '시즌을 만들고 상태와 설정을 관리해요'],
  turn: ['턴 진행', '다음 턴의 가격·뉴스를 점검하고 턴을 넘겨요'],
  stocks: ['분야·종목 관리', '15개 분야와 30개 종목, 기업정보를 관리해요'],
  prices: ['턴별 가격 입력', '30개 종목 × 12턴 가격표를 입력해요'],
  news: ['뉴스 관리', '턴별 가상 뉴스를 등록해요. 턴당 2~3개를 내요'],
  people: ['참가자 관리', '참가자의 자산·거래 내역을 보고 캐시를 지급·회수해요'],
  ranking: ['정산·랭킹', '랭킹을 미리 보고, 정산·공개·보상을 진행해요'],
  cash: ['캐시 지급 규칙', '활동별로 지급할 캐시를 설정해요'],
  log: ['작업 기록', '중요한 관리자 작업은 누가 언제 했는지 남아요'],
};

function Pill({ tone = 'gray', children }) { return <span className={'adm-pill ' + tone}>{children}</span>; }
function Card({ title, desc, right, children, className = '' }) {
  return (
    <section className={'adm-card ' + className}>
      {(title || right) && <div className="adm-card-head"><div>{title && <h2>{title}</h2>}{desc && <p>{desc}</p>}</div>{right}</div>}
      {children}
    </section>
  );
}
function statusTone(s) { return s === '진행 중' ? 'mint' : s === '휴장' ? 'gray' : s === '정산' ? 'amber' : s === '종료' ? 'dark' : 'soft'; }

function addDays(iso, n) { const d = new Date(iso + 'T00:00:00'); d.setDate(d.getDate() + n); return d; }
function dstr(d) { return (d.getMonth() + 1) + '.' + String(d.getDate()).padStart(2, '0'); }
function buildCalendar(start) {
  const out = []; let t = 0;
  for (let i = 0; i < 14; i++) {
    const d = addDays(start, i), sun = i === 6 || i === 13;
    if (!sun) t += 1;
    out.push({ date: dstr(d), dow: '월화수목금토일'[i % 7], turn: sun ? null : t, kind: i === 13 ? '랭킹 공개일' : i === 6 ? '휴장' : t + '턴' });
  }
  return out;
}

function Sparkline({ values, current }) {
  const pts = values.map((v, i) => [i, v]).filter(p => p[1] != null);
  if (pts.length < 2) return <div className="adm-empty">가격을 2턴 이상 입력하면 그래프가 보여요</div>;
  const vs = pts.map(p => p[1]), mn = Math.min(...vs), mx = Math.max(...vs), W = 560, H = 160, pad = 18;
  const x = i => pad + (i / 11) * (W - pad * 2), y = v => H - pad - ((v - mn) / (mx - mn || 1)) * (H - pad * 2);
  return (
    <svg viewBox={'0 0 ' + W + ' ' + H} className="adm-spark" role="img" aria-label="12턴 가격 흐름">
      <line x1={x(current - 1)} x2={x(current - 1)} y1={pad / 2} y2={H - pad / 2} stroke="#D5E1CC" strokeDasharray="4 4" />
      <polyline fill="none" stroke="#263B1A" strokeWidth="2.5" points={pts.map(p => x(p[0]) + ',' + y(p[1])).join(' ')} />
      {pts.map(p => <circle key={p[0]} cx={x(p[0])} cy={y(p[1])} r={p[0] === current - 1 ? 5 : 3} fill={p[0] < current ? '#263B1A' : '#9fe870'} stroke="#263B1A" strokeWidth="1.5" />)}
      {Array.from({ length: 12 }, (_, i) => <text key={i} x={x(i)} y={H - 2} textAnchor="middle" fontSize="10" fill="#687161">{i + 1}</text>)}
    </svg>
  );
}

function Admin() {
  const [view, setView] = useState('dash');
  const [sideOpen, setSideOpen] = useState(true);
  const [season, setSeason] = useState({ name: '시즌 7', start: '2026-10-19', status: '진행 중', turn: 5, carryRate: 30, carryCap: 3000000, unlock: 8, settled: false, published: false, rewarded: false });
  const [seasons, setSeasons] = useState([
    { name: '시즌 5', start: '2026-09-07', status: '종료', people: 1102, avg: '+6.8%' },
    { name: '시즌 6', start: '2026-09-21', status: '종료', people: 1240, avg: '+9.2%' },
  ]);
  const [sectors, setSectors] = useState(SECTORS0);
  const [stockList, setStockList] = useState(() => BASE.map(([name, sector]) => ({ name, sector, intro: name + '은(는) ' + sector + ' 분야의 가상 기업이에요.', biz: sector + ' 관련 제품·서비스', revenue: '1조 2,000억', profit: '1,450억' })));
  const [prices, setPrices] = useState(seedPrices);
  const [news, setNews] = useState(seedNews);
  const [people, setPeople] = useState(seedPeople);
  const [cashRules, setCashRules] = useState([
    ['학습 완료', '강의 1개 학습 완료(퀴즈 통과) 시', 3000], ['레벨업', '레벨 × 1,000 캐시 (값은 레벨당 금액)', 1000],
    ['연속 출석', '5일째부터 매일', 1000], ['퀴즈 만점', '만점 보너스', 500],
  ].map(([name, desc, amount]) => ({ name, desc, amount })));
  const [turnLog, setTurnLog] = useState([
    { at: '10.23 00:00', by: ADMIN, from: 4, to: 5 }, { at: '10.22 00:00', by: ADMIN, from: 3, to: 4 },
    { at: '10.21 00:00', by: '김운영', from: 2, to: 3 }, { at: '10.20 00:00', by: ADMIN, from: 1, to: 2 },
  ]);
  const [audit, setAudit] = useState([
    { at: '10.23 00:00', by: ADMIN, what: '시즌 7 · 4턴 → 5턴 진행' },
    { at: '10.22 18:12', by: '김운영', what: '청약준비생 캐시 수동 지급 +2,000 (출석 누락 보정)' },
    { at: '10.19 00:00', by: ADMIN, what: '시즌 7 상태 변경: 준비 → 진행 중' },
  ]);
  const [modal, setModal] = useState(null);
  const [toast, setToast] = useState('');
  const toastMsg = m => { setToast(m); clearTimeout(window.__admT); window.__admT = setTimeout(() => setToast(''), 2600); };
  const log = what => setAudit(a => [{ at: now(), by: ADMIN, what }, ...a]);

  useEffect(() => {
    try { localStorage.setItem('mb-inv-sync', JSON.stringify({ status: season.status, turn: season.turn, published: season.published, unlock: season.unlock })); } catch (e) {}
  }, [season.status, season.turn, season.published, season.unlock]);

  const T = season.turn, next = T + 1;
  const priceAt = (i, t) => { for (let k = t - 1; k >= 0; k--) if (prices[i][k] != null) return prices[i][k]; return 0; };
  const missingNext = next <= 12 ? prices.map((r, i) => (r[next - 1] == null ? i : -1)).filter(i => i >= 0) : [];
  const newsNext = news.filter(n => n.turn === next).length;
  const people2 = useMemo(() => people.map(p => {
    const net = p.cash + p.holdings.reduce((a, [i, q]) => a + q * priceAt(i, T), 0);
    return { ...p, net, ret: (net - p.investable) / p.investable };
  }), [people, prices, T]);
  const rankBy = (key) => {
    const sorted = [...people2].sort((a, b) => b[key] - a[key]);
    let prev = null, rank = 0;
    return sorted.map((p, i) => { if (p[key] !== prev) { rank = i + 1; prev = p[key]; } return { ...p, rank }; });
  };
  const todayOrders = people.reduce((a, p) => a + p.trades.filter(t => t.turn === T).length, 0);
  const holiday = season.status === '휴장';
  const turnBlock = season.status !== '진행 중' ? (holiday ? '휴장일에는 턴을 넘길 수 없어요' : '시즌이 진행 중일 때만 턴을 넘길 수 있어요')
    : next > 12 ? '마지막 턴이에요. 정산 상태로 바꿔 주세요'
    : T === 6 && !season.week2 ? '1주차가 끝났어요. 일요일 휴장을 먼저 진행해 주세요'
    : missingNext.length ? '다음 턴에 가격이 비어 있는 종목이 ' + missingNext.length + '개 있어요'
    : newsNext < 2 ? '다음 턴 뉴스가 ' + newsNext + '개예요. 2개 이상 등록해 주세요' : '';

  const advanceTurn = () => {
    setModal({
      title: next + '턴으로 넘길까요?', body: '새 가격과 뉴스가 모든 참가자에게 공개되고, 전원의 총자산 스냅샷이 저장돼요. 되돌릴 수 없어요.', ok: '턴 넘기기',
      onOk: () => {
        setSeason(s => ({ ...s, turn: s.turn + 1 }));
        setTurnLog(l => [{ at: now(), by: ADMIN, from: T, to: next }, ...l]);
        log(season.name + ' · ' + T + '턴 → ' + next + '턴 진행'); toastMsg(next + '턴으로 넘겼어요');
      },
    });
  };
  const nextStatus = () => {
    const s = season.status;
    if (s === '휴장' && T === 6) return { label: '2주차 진행 시작', to: '진행 중' };
    const i = STATUS.indexOf(s);
    if (s === '진행 중' && T === 6) return { label: '휴장으로 변경', to: '휴장' };
    if (s === '진행 중' && T < 12) return null;
    if (s === '진행 중') return { label: '정산으로 변경', to: '정산' };
    if (s === '정산') return season.published ? { label: '종료로 변경', to: '종료' } : null;
    if (i >= 0 && i < STATUS.length - 1 && s !== '휴장') return { label: STATUS[i + 1] + '(으)로 변경', to: STATUS[i + 1] };
    return null;
  };
  const changeStatus = (to) => setModal({
    title: '시즌 상태를 ' + to + '(으)로 바꿀까요?', body: '시즌 상태는 준비 → 진행 중 → 휴장 → 정산 → 종료 순서로만 바뀌고 거꾸로 가지 않아요.', ok: '변경하기',
    onOk: () => { setSeason(s => ({ ...s, status: to, week2: s.week2 || (s.status === '휴장' && to === '진행 중') })); log(season.name + ' 상태 변경: ' + season.status + ' → ' + to); toastMsg('상태를 ' + to + '(으)로 바꿨어요'); },
  });

  const go = v => () => { setView(v); window.scrollTo(0, 0); };
  const pendingWarn = (missingNext.length || newsNext < 2) && next <= 12;
  const head = HEAD[view];
  const props = { season, setSeason, seasons, setSeasons, sectors, setSectors, stockList, setStockList, prices, setPrices, news, setNews, people, setPeople, people2, rankBy, cashRules, setCashRules, turnLog, audit, log, toastMsg, setModal, go, priceAt, missingNext, newsNext, turnBlock, advanceTurn, nextStatus, changeStatus, todayOrders };

  return (
    <div className="adm" style={{ flexDirection: 'row' }}>
      <AdminSideNav active="invest" sideOpen={sideOpen} onToggle={() => {}} nav={NAV} view={view} onNav={go} warn={pendingWarn} />

      <div className="adm-main">
        <div style={{ position: 'sticky', top: 0, zIndex: 30, background: '#fff', height: 84, boxSizing: 'border-box', padding: '0 40px', display: 'flex', alignItems: 'center', justifyContent: 'flex-end', gap: 6, flexWrap: 'wrap' }}>
          <Pill tone="dark">{season.name}</Pill>
          <Pill tone={statusTone(season.status)}>{season.status}</Pill>
          <Pill tone="soft">{T} / 12턴</Pill>
        </div>
        <header className="adm-head" style={{ paddingTop: 30 }}>
          <div><span className="eyebrow">MOCK INVEST · ADMIN</span><h1>{head[0]}</h1><p>{head[1]}</p></div>
        </header>
        <main className="adm-body">
          {view === 'dash' && <Dash {...props} />}
          {view === 'season' && <SeasonView {...props} />}
          {view === 'turn' && <TurnView {...props} />}
          {view === 'stocks' && <StocksView {...props} />}
          {view === 'prices' && <PricesView {...props} />}
          {view === 'news' && <NewsView {...props} />}
          {view === 'people' && <PeopleView {...props} />}
          {view === 'ranking' && <RankingView {...props} />}
          {view === 'cash' && <CashView {...props} />}
          {view === 'log' && <LogView {...props} />}
        </main>
      </div>

      {modal && (
        <div className="adm-backdrop" onClick={() => setModal(null)}>
          <div className="adm-modal" onClick={e => e.stopPropagation()}>
            <h3>{modal.title}</h3>
            <p>{modal.body}</p>
            <div className="adm-row end">
              <button className="adm-btn ghost" onClick={() => setModal(null)}>취소</button>
              <button className={'adm-btn ' + (modal.danger ? 'danger' : 'primary')} onClick={() => { const f = modal.onOk; setModal(null); f(); }}>{modal.ok}</button>
            </div>
          </div>
        </div>
      )}
      {toast && <div className="adm-toast" role="status">{toast}</div>}
    </div>
  );
}

function Dash({ season, people2, todayOrders, missingNext, newsNext, turnBlock, go, audit, stockList, news }) {
  const T = season.turn, cal = buildCalendar(season.start), nextDay = cal.find(c => c.turn === T + 1);
  return (
    <>
      <div className="adm-stats">
        <div className="adm-stat dark"><span>진행 중인 시즌</span><strong>{season.name}</strong><small>{season.status} · 시작 {season.start.replace(/-/g, '.')}</small></div>
        <div className="adm-stat"><span>현재 턴</span><strong>{T} / 12</strong><small>{cal.find(c => c.turn === T)?.date || '-'} 거래일</small></div>
        <div className="adm-stat"><span>다음 턴 진행 예정</span><strong>{nextDay ? nextDay.date + ' 00:00' : '정산 대기'}</strong><small>관리자 버튼으로만 넘어가요</small></div>
        <div className="adm-stat"><span>참가자 수</span><strong>{people2.length.toLocaleString()}명</strong><small>Lv.{season.unlock} 이상</small></div>
        <div className="adm-stat"><span>오늘 주문 수</span><strong>{todayOrders}건</strong><small>{T}턴 체결 기준</small></div>
      </div>
      {T < 12 && (missingNext.length > 0 || newsNext < 2) ? (
        <div className="adm-alert warn">
          <div><strong>{T + 1}턴 준비가 끝나지 않았어요</strong>
            <ul>
              {missingNext.length > 0 && <li>다음 턴에 가격이 비어 있는 종목이 {missingNext.length}개 있어요 · {missingNext.map(i => stockList[i].name).join(', ')}</li>}
              {newsNext < 2 && <li>다음 턴 뉴스가 {newsNext}개예요 · 턴당 2~3개를 등록해 주세요</li>}
            </ul>
          </div>
          <div className="adm-row">
            {missingNext.length > 0 && <button className="adm-btn soft" onClick={go('prices')}>가격 입력하기</button>}
            {newsNext < 2 && <button className="adm-btn soft" onClick={go('news')}>뉴스 등록하기</button>}
          </div>
        </div>
      ) : (
        <div className="adm-alert ok"><div><strong>{turnBlock || (T + 1) + '턴 준비가 끝났어요'}</strong></div><button className="adm-btn primary" onClick={go('turn')}>턴 진행으로 가기</button></div>
      )}
      <div className="adm-grid2">
        <Card title="턴 달력" desc="지난 턴 · 오늘 · 남은 턴 · 휴장일"><Calendar season={season} /></Card>
        <Card title="최근 작업" right={<button className="adm-btn ghost sm" onClick={go('log')}>전체 보기</button>}>
          <div className="adm-list">{audit.slice(0, 5).map((a, i) => <div key={i} className="adm-li"><span>{a.what}</span><small>{a.by} · {a.at}</small></div>)}</div>
        </Card>
      </div>
    </>
  );
}

function Calendar({ season, start }) {
  const cal = buildCalendar(start || season.start), T = season.turn;
  return (
    <div className="adm-cal">
      {cal.map((c, i) => {
        const st = c.turn == null ? 'off' : c.turn < T ? 'past' : c.turn === T ? 'today' : 'future';
        return <div key={i} className={'adm-cal-cell ' + (start ? 'future' : st) + (c.turn == null ? ' off' : '')}><small>{c.dow} {c.date}</small><strong>{c.kind}</strong></div>;
      })}
      {!start && <div className="adm-legend"><span className="past">지난 턴</span><span className="today">오늘</span><span className="future">남은 턴</span><span className="off">휴장</span></div>}
    </div>
  );
}

function SeasonView({ season, setSeason, seasons, setSeasons, log, toastMsg, nextStatus, changeStatus }) {
  const [form, setForm] = useState({ name: '시즌 8', start: '2026-11-02', copy: true });
  const [cfg, setCfg] = useState({ carryRate: season.carryRate, carryCap: season.carryCap, unlock: season.unlock });
  const monday = form.start && new Date(form.start + 'T00:00:00').getDay() === 1;
  const ns = nextStatus();
  const create = () => {
    if (!form.name.trim()) return toastMsg('시즌 이름을 입력해 주세요');
    if (!monday) return toastMsg('시작일은 월요일만 선택할 수 있어요');
    if (seasons.some(s => s.name === form.name.trim()) || form.name.trim() === season.name) return toastMsg('같은 이름의 시즌이 있어요');
    setSeasons(l => [...l, { name: form.name.trim(), start: form.start, status: '준비', people: 0, avg: '-', copied: form.copy }]);
    log(form.name + ' 생성 (시작 ' + form.start + (form.copy ? ', 이전 시즌 분야·종목 복사' : '') + ')');
    toastMsg(form.name + '을(를) 만들었어요. 가격과 뉴스를 입력해 주세요');
  };
  return (
    <>
      <Card title={season.name + ' 상태'} desc="준비 → 진행 중 → 휴장 → 정산 → 종료 순서로만 바뀌어요">
        <div className="adm-steps">
          {STATUS.map((s, i) => { const cur = STATUS.indexOf(season.status); return <div key={s} className={'adm-step ' + (i < cur ? 'done' : i === cur ? 'on' : '')}><b>{i + 1}</b><span>{s}</span></div>; })}
        </div>
        <div className="adm-row">
          {ns ? <button className="adm-btn primary" onClick={() => changeStatus(ns.to)}>{ns.label}</button> : <span className="adm-muted">{season.status === '진행 중' ? '12턴(또는 1주차 6턴)을 마치면 다음 상태로 바꿀 수 있어요' : season.status === '정산' ? '정산·랭킹 공개를 마치면 종료할 수 있어요' : '마지막 상태예요'}</span>}
        </div>
      </Card>
      <div className="adm-grid2">
        <Card title="시즌 설정" desc="이월 비율과 상한, 참여 해금 레벨을 정해요">
          <div className="adm-form">
            <label>이월 비율 (%)<input type="number" min="0" max="100" value={cfg.carryRate} onChange={e => setCfg({ ...cfg, carryRate: Number(e.target.value) })} /></label>
            <label>이월 상한 (캐시)<input type="number" min="0" step="10000" value={cfg.carryCap} onChange={e => setCfg({ ...cfg, carryCap: Number(e.target.value) })} /></label>
            <label>참여 해금 레벨<input type="number" min="1" max="20" value={cfg.unlock} onChange={e => setCfg({ ...cfg, unlock: Number(e.target.value) })} /></label>
            <p className="adm-muted">수익이 난 경우에만 수익의 {cfg.carryRate}%를 최대 {fmt(cfg.carryCap)} 캐시까지 다음 시즌으로 이월해요. 손실이면 0이에요.</p>
            <div className="adm-row end"><button className="adm-btn primary" onClick={() => { setSeason(s => ({ ...s, ...cfg })); log(season.name + ' 설정 변경: 이월 ' + cfg.carryRate + '%, 상한 ' + fmt(cfg.carryCap) + ', 해금 Lv.' + cfg.unlock); toastMsg('시즌 설정을 저장했어요'); }}>설정 저장</button></div>
          </div>
        </Card>
        <Card title="새 시즌 만들기" desc="시작일(월요일)을 고르면 14일 달력이 자동으로 만들어져요">
          <div className="adm-form">
            <label>시즌 이름<input value={form.name} onChange={e => setForm({ ...form, name: e.target.value })} /></label>
            <label>시작일<input type="date" value={form.start} onChange={e => setForm({ ...form, start: e.target.value })} />{form.start && !monday && <em className="adm-err">시작일은 월요일만 선택할 수 있어요</em>}</label>
            <label className="adm-check"><input type="checkbox" checked={form.copy} onChange={e => setForm({ ...form, copy: e.target.checked })} />이전 시즌의 분야와 종목 그대로 가져오기 (가격·뉴스는 새로 입력)</label>
            {monday && <Calendar season={season} start={form.start} />}
            <div className="adm-row end"><button className="adm-btn primary" onClick={create}>시즌 만들기</button></div>
          </div>
        </Card>
      </div>
      <Card title="시즌 목록">
        <div className="adm-table-wrap"><table className="adm-table"><thead><tr><th>시즌</th><th>시작일</th><th>상태</th><th>참가자</th><th>평균 수익률</th></tr></thead><tbody>
          <tr className="hl"><td><strong>{season.name}</strong></td><td>{season.start}</td><td><Pill tone={statusTone(season.status)}>{season.status}</Pill></td><td>8명</td><td>진행 중</td></tr>
          {[...seasons].reverse().map(s => <tr key={s.name}><td>{s.name}{s.copied && <small className="adm-muted"> · 종목 복사됨</small>}</td><td>{s.start}</td><td><Pill tone={statusTone(s.status)}>{s.status}</Pill></td><td>{s.people ? s.people.toLocaleString() + '명' : '-'}</td><td>{s.avg}</td></tr>)}
        </tbody></table></div>
      </Card>
    </>
  );
}

function TurnView({ season, missingNext, newsNext, turnBlock, advanceTurn, turnLog, stockList, go, nextStatus, changeStatus }) {
  const T = season.turn, next = T + 1, ns = nextStatus();
  const checks = [
    ['시즌 상태', season.status === '진행 중', season.status === '진행 중' ? '진행 중' : season.status + ' · 턴을 넘길 수 없어요'],
    [next + '턴 가격 30개', next <= 12 && missingNext.length === 0, next > 12 ? '남은 턴 없음' : (30 - missingNext.length) + ' / 30 입력'],
    [next + '턴 뉴스 2개 이상', next <= 12 && newsNext >= 2, newsNext + '개 등록'],
  ];
  return (
    <>
      <Card title={next <= 12 ? T + '턴 → ' + next + '턴' : '모든 턴이 끝났어요'} desc="누르기 전에 다음 턴의 가격과 뉴스를 자동으로 점검해요">
        <div className="adm-checks">
          {checks.map(([l, ok, d]) => <div key={l} className={'adm-checkrow ' + (ok ? 'ok' : 'no')}><b>{ok ? '✓' : '!'}</b><span>{l}</span><small>{d}</small></div>)}
        </div>
        {missingNext.length > 0 && <p className="adm-muted">비어 있는 종목: {missingNext.map(i => stockList[i].name).join(', ')} <button className="adm-link" onClick={go('prices')}>가격 입력하기</button></p>}
        <div className="adm-row">
          <button className="adm-btn primary lg" disabled={!!turnBlock} onClick={advanceTurn}>{next <= 12 ? next + '턴으로 넘기기' : '턴 진행 완료'}</button>
          {turnBlock && <span className="adm-err">{turnBlock}</span>}
          {ns && (season.status !== '진행 중' || T === 6 || T === 12) && <button className="adm-btn soft" onClick={() => changeStatus(ns.to)}>{ns.label}</button>}
        </div>
      </Card>
      <Card title="턴 진행 기록" desc="누가 몇 시에 몇 턴으로 넘겼는지 남아요">
        <div className="adm-table-wrap"><table className="adm-table"><thead><tr><th>시각</th><th>담당자</th><th>진행</th></tr></thead><tbody>
          {turnLog.map((l, i) => <tr key={i}><td>{l.at}</td><td>{l.by}</td><td>{l.from}턴 → <strong>{l.to}턴</strong></td></tr>)}
        </tbody></table></div>
      </Card>
    </>
  );
}

function StocksView({ season, sectors, setSectors, stockList, setStockList, prices, setPrices, log, toastMsg }) {
  const [sel, setSel] = useState(0);
  const [info, setInfo] = useState(stockList[0]);
  const locked = season.status !== '준비';
  useEffect(() => { setInfo(stockList[sel]); }, [sel]);
  const renameSector = (i, v) => {
    const old = sectors[i];
    setSectors(s => s.map((x, j) => (j === i ? v : x)));
    setStockList(l => l.map(st => (st.sector === old ? { ...st, sector: v } : st)));
  };
  const counts = sectors.map(s => stockList.filter(st => st.sector === s).length);
  return (
    <>
      <Card title="분야 15개" desc="분야 이름을 바꾸면 종목의 분야도 함께 바뀌어요" right={<button className="adm-btn soft sm" onClick={() => { log('분야 이름 저장'); toastMsg('분야를 저장했어요'); }}>저장</button>}>
        <div className="adm-sectors">
          {sectors.map((s, i) => <label key={i} className={counts[i] !== 2 ? 'bad' : ''}><b>{i + 1}</b><input value={s} onChange={e => renameSector(i, e.target.value)} /><small>{counts[i]}종목</small></label>)}
        </div>
        {counts.some(c => c !== 2) && <p className="adm-err">분야마다 2개씩 종목이 있어야 해요</p>}
      </Card>
      <div className="adm-grid2 wide-left">
        <Card title="종목 30개" desc={locked ? '시즌이 시작된 뒤에는 시작 가격(1턴)을 바꿀 수 없어요' : '이름, 분야, 시작 가격을 등록·수정해요'}>
          <div className="adm-table-wrap tall"><table className="adm-table"><thead><tr><th>#</th><th>종목명</th><th>분야</th><th>시작 가격</th><th></th></tr></thead><tbody>
            {stockList.map((st, i) => (
              <tr key={i} className={sel === i ? 'hl' : ''}>
                <td>{i + 1}</td>
                <td><input className="adm-cell" value={st.name} onChange={e => setStockList(l => l.map((x, j) => (j === i ? { ...x, name: e.target.value } : x)))} /></td>
                <td><select className="adm-cell" value={st.sector} onChange={e => setStockList(l => l.map((x, j) => (j === i ? { ...x, sector: e.target.value } : x)))}>{sectors.map(s => <option key={s}>{s}</option>)}</select></td>
                <td><input className="adm-cell num" type="number" disabled={locked} value={prices[i][0] ?? ''} onChange={e => setPrices(m => m.map((r, j) => (j === i ? [Number(e.target.value), ...r.slice(1)] : r)))} /></td>
                <td><button className="adm-btn ghost sm" onClick={() => setSel(i)}>기업정보</button></td>
              </tr>
            ))}
          </tbody></table></div>
          <div className="adm-row end"><button className="adm-btn primary" onClick={() => { log('종목 정보 저장'); toastMsg('종목을 저장했어요'); }}>종목 저장</button></div>
        </Card>
        <Card title={'기업정보 · ' + stockList[sel].name} desc="주가 상세의 기업정보 탭에 보여요">
          <div className="adm-form">
            <label>회사 소개<textarea rows="4" value={info.intro} onChange={e => setInfo({ ...info, intro: e.target.value })} /></label>
            <label>주요 사업<input value={info.biz} onChange={e => setInfo({ ...info, biz: e.target.value })} /></label>
            <div className="adm-grid2 tight">
              <label>가상 매출<input value={info.revenue} onChange={e => setInfo({ ...info, revenue: e.target.value })} /></label>
              <label>가상 영업이익<input value={info.profit} onChange={e => setInfo({ ...info, profit: e.target.value })} /></label>
            </div>
            <div className="adm-row end"><button className="adm-btn primary" onClick={() => { setStockList(l => l.map((x, j) => (j === sel ? info : x))); log(info.name + ' 기업정보 수정'); toastMsg('기업정보를 저장했어요'); }}>기업정보 저장</button></div>
          </div>
        </Card>
      </div>
    </>
  );
}

function PricesView({ season, prices, setPrices, stockList, log, toastMsg, setModal }) {
  const T = season.turn;
  const [draft, setDraft] = useState(prices);
  const [mode, setMode] = useState('price');
  const [sel, setSel] = useState(0);
  const [csvErr, setCsvErr] = useState([]);
  useEffect(() => { setDraft(prices); }, [prices]);
  const prevOf = (row, t) => { for (let k = t - 1; k >= 0; k--) if (row[k] != null) return row[k]; return null; };
  const warnOf = (row, t) => { const v = row[t]; if (v == null) return ''; if (v <= 0) return '0 이하'; const p = prevOf(row, t); if (p && Math.abs(v / p - 1) > 0.3) return '±30% 초과'; return ''; };
  const setCell = (i, t, raw) => {
    setDraft(m => m.map((r, j) => {
      if (j !== i) return r;
      const a = [...r];
      if (raw === '') a[t] = null;
      else if (mode === 'rate') { const p = prevOf(a, t); a[t] = p ? r50(p * (1 + Number(raw) / 100)) : null; }
      else a[t] = Number(raw);
      return a;
    }));
  };
  const warns = []; draft.forEach((r, i) => r.forEach((_, t) => { if (t >= T && warnOf(r, t)) warns.push(stockList[i].name + ' ' + (t + 1) + '턴'); }));
  const dirty = JSON.stringify(draft) !== JSON.stringify(prices);
  const save = () => {
    const doSave = () => { setPrices(draft); log('턴별 가격 저장' + (warns.length ? ' (이상값 ' + warns.length + '건 확인)' : '')); toastMsg('가격표를 저장했어요'); };
    if (warns.length) setModal({ title: '한 턴에 ±30%를 넘는 변동이에요. 맞나요?', body: warns.slice(0, 6).join(', ') + (warns.length > 6 ? ' 외 ' + (warns.length - 6) + '건' : '') + ' 의 가격을 확인해 주세요.', ok: '그대로 저장', onOk: doSave });
    else doSave();
  };
  const download = () => {
    const rows = [['종목', '분야', ...Array.from({ length: 12 }, (_, i) => (i + 1) + '턴')], ...draft.map((r, i) => [stockList[i].name, stockList[i].sector, ...r.map(v => (v == null ? '' : v))])];
    const blob = new Blob(['\ufeff' + rows.map(r => r.join(',')).join('\n')], { type: 'text/csv;charset=utf-8' });
    const a = document.createElement('a'); a.href = URL.createObjectURL(blob); a.download = season.name + '_가격표.csv'; a.click();
  };
  const upload = e => {
    const f = e.target.files && e.target.files[0]; if (!f) return;
    f.text().then(txt => {
      const lines = txt.replace(/^\ufeff/, '').split(/\r?\n/).filter(l => l.trim()).slice(1), errs = [];
      const m = draft.map(r => [...r]);
      lines.forEach((l, n) => {
        const c = l.split(','), i = stockList.findIndex(s => s.name === c[0].trim());
        if (i < 0 || c.length < 14 || c.slice(2, 14).some(v => v.trim() !== '' && isNaN(Number(v)))) { errs.push(n + 2); return; }
        c.slice(2, 14).forEach((v, t) => { if (t >= T) m[i][t] = v.trim() === '' ? null : Number(v); });
      });
      if (errs.length) { setCsvErr(errs); toastMsg(errs[0] + '행의 형식이 맞지 않아요'); }
      else { setCsvErr([]); setDraft(m); toastMsg('CSV를 불러왔어요. 확인 후 저장해 주세요'); }
    });
    e.target.value = '';
  };
  return (
    <>
      <Card title="가격표" desc={'지난 턴과 현재 턴(1~' + T + '턴)은 잠겨 있어요 · 칸을 누르면 아래 그래프에 보여요'}
        right={<div className="adm-row">
          <div className="adm-seg"><button className={mode === 'price' ? 'on' : ''} onClick={() => setMode('price')}>가격 입력</button><button className={mode === 'rate' ? 'on' : ''} onClick={() => setMode('rate')}>변동률 입력</button></div>
          <button className="adm-btn ghost sm" onClick={download}>CSV 내려받기</button>
          <label className="adm-btn ghost sm file">CSV 올리기<input type="file" accept=".csv,text/csv" onChange={upload} /></label>
        </div>}>
        {csvErr.length > 0 && <p className="adm-err">{csvErr.join(', ')}행의 형식이 맞지 않아요. 올리기를 취소했어요</p>}
        {mode === 'rate' && <p className="adm-muted">변동률(예: -3)을 넣으면 이전 턴 가격으로 자동 계산해요 (50 캐시 단위)</p>}
        <div className="adm-table-wrap tall"><table className="adm-table grid"><thead><tr><th className="sticky">종목</th>{Array.from({ length: 12 }, (_, t) => <th key={t} className={t < T ? 'lockh' : ''}><span style={{ display: 'inline-flex', alignItems: 'center', gap: 4 }}>{t < T && <span role="img" aria-label="잠김" style={{ width: 12, height: 12, flex: 'none', background: 'currentColor', WebkitMask: 'url(uploads/lock.png) center / contain no-repeat', mask: 'url(uploads/lock.png) center / contain no-repeat' }} />}{t + 1}턴</span></th>)}</tr></thead><tbody>
          {draft.map((r, i) => (
            <tr key={i} className={sel === i ? 'hl' : ''}>
              <td className="sticky"><button className="adm-link" onClick={() => setSel(i)}>{stockList[i].name}</button></td>
              {r.map((v, t) => {
                const lockedCell = t < T, w = warnOf(r, t), p = prevOf(r, t);
                if (lockedCell) return <td key={t} className="lock">{fmt(v)}</td>;
                const shown = mode === 'rate' ? (v != null && p ? ((v / p - 1) * 100).toFixed(1) : '') : (v ?? '');
                return <td key={t} className={(w ? 'warn ' : '') + (v == null ? 'empty' : '')} title={w}><input type="number" value={shown} onFocus={() => setSel(i)} onChange={e => setCell(i, t, e.target.value)} placeholder={mode === 'rate' ? '%' : '-'} /></td>;
              })}
            </tr>
          ))}
        </tbody></table></div>
        <div className="adm-row">
          <span className="adm-muted flex">{warns.length ? '이상값 ' + warns.length + '건 · 저장할 때 다시 확인해요' : '이상값 없음'}</span>
          <button className="adm-btn ghost" disabled={!dirty} onClick={() => setDraft(prices)}>되돌리기</button>
          <button className="adm-btn primary" disabled={!dirty} onClick={save}>가격 저장</button>
        </div>
      </Card>
      <Card title={'가격 미리보기 · ' + stockList[sel].name} desc="저장 전 입력값 기준 12턴 가격 흐름이에요 · 점선은 현재 턴"><Sparkline values={draft[sel]} current={T} /></Card>
    </>
  );
}

function NewsView({ season, news, setNews, sectors, stockList, log, toastMsg, setModal }) {
  const T = season.turn;
  const [turn, setTurn] = useState(T + 1);
  const blank = { id: null, turn: T + 1, title: '', body: '', sectors: [], stocks: [], memo: '' };
  const [f, setF] = useState(null);
  const list = news.filter(n => n.turn === turn);
  const past = f && f.id && f.turn <= T;
  const toggle = (k, v) => setF(x => ({ ...x, [k]: x[k].includes(v) ? x[k].filter(y => y !== v) : [...x[k], v] }));
  const save = () => {
    if (!f.title.trim() || !f.body.trim()) return toastMsg('제목과 본문을 입력해 주세요');
    if (!f.sectors.length) return toastMsg('관련 분야를 하나 이상 골라 주세요');
    if (f.id) { setNews(l => l.map(n => (n.id === f.id ? f : n))); log(f.turn + '턴 뉴스 수정' + (past ? ' (오타 수정)' : '') + ': ' + f.title); }
    else { const id = Math.max(0, ...news.map(n => n.id)) + 1; setNews(l => [...l, { ...f, id }]); log(f.turn + '턴 뉴스 등록: ' + f.title); setTurn(f.turn); }
    setF(null); toastMsg('뉴스를 저장했어요');
  };
  return (
    <div className="adm-grid2 wide-left">
      <Card title="턴별 뉴스" right={<button className="adm-btn primary sm" onClick={() => setF({ ...blank, turn: Math.max(turn, T + 1) })}>+ 뉴스 등록</button>}>
        <div className="adm-turnchips">{Array.from({ length: 12 }, (_, i) => i + 1).map(t => { const c = news.filter(n => n.turn === t).length; return <button key={t} className={(turn === t ? 'on ' : '') + (t > T && c < 2 ? 'short' : '')} onClick={() => setTurn(t)}>{t}턴<small>{c}개</small></button>; })}</div>
        {turn > T && list.length < 2 && <p className="adm-err">{turn}턴 뉴스가 {list.length}개예요 · 턴당 2~3개를 내요</p>}
        {turn > 3 + T && list.length > 3 && <p className="adm-err">턴당 3개까지 권장해요</p>}
        <div className="adm-list">
          {list.map(n => (
            <div key={n.id} className="adm-news">
              <div className="adm-row"><Pill tone={n.turn <= T ? 'gray' : 'mint'}>{n.turn <= T ? '공개됨' : '공개 예정'}</Pill>{n.sectors.map(s => <Pill key={s} tone="soft">{s}</Pill>)}</div>
              <strong>{n.title}</strong>
              <p>{n.body}</p>
              <small>관련 종목 · {n.stocks.join(', ') || '없음'}</small>
              {n.memo && <small className="adm-memo">관리자 메모 · {n.memo}</small>}
              <div className="adm-row">
                <button className="adm-btn soft sm" onClick={() => setF(JSON.parse(JSON.stringify(n)))}>{n.turn <= T ? '오타 수정' : '수정'}</button>
                {n.turn > T && <button className="adm-btn danger-ghost sm" onClick={() => setModal({ title: '뉴스를 삭제할까요?', body: n.title, ok: '삭제', danger: true, onOk: () => { setNews(l => l.filter(x => x.id !== n.id)); log(n.turn + '턴 뉴스 삭제: ' + n.title); } })}>삭제</button>}
              </div>
            </div>
          ))}
          {!list.length && <div className="adm-empty">{turn}턴에 등록된 뉴스가 없어요</div>}
        </div>
      </Card>
      <Card title={f ? (f.id ? (past ? '지난 턴 뉴스 · 오타 수정' : '뉴스 수정') : '뉴스 등록') : '뉴스 편집'} desc={past ? '지난 턴 뉴스는 오타 수정만 할 수 있어요. 턴·분야·종목은 바꿀 수 없어요' : '제목, 본문, 관련 분야·종목을 입력해요'}>
        {!f ? <div className="adm-empty">왼쪽에서 뉴스를 고르거나 새로 등록해 주세요</div> : (
          <div className="adm-form">
            <label>턴<select value={f.turn} disabled={!!past} onChange={e => setF({ ...f, turn: Number(e.target.value) })}>{Array.from({ length: 12 - T }, (_, i) => T + 1 + i).map(t => <option key={t} value={t}>{t}턴</option>)}{past && <option value={f.turn}>{f.turn}턴</option>}</select></label>
            <label>제목<input value={f.title} onChange={e => setF({ ...f, title: e.target.value })} /></label>
            <label>본문<textarea rows="5" value={f.body} onChange={e => setF({ ...f, body: e.target.value })} /></label>
            <div className="adm-field"><span>관련 분야</span><div className="adm-chips">{sectors.map(s => <button key={s} disabled={!!past} className={f.sectors.includes(s) ? 'on' : ''} onClick={() => toggle('sectors', s)}>{s}</button>)}</div></div>
            <div className="adm-field"><span>관련 종목</span><div className="adm-chips">{stockList.filter(s => f.sectors.includes(s.sector)).map(s => <button key={s.name} disabled={!!past} className={f.stocks.includes(s.name) ? 'on' : ''} onClick={() => toggle('stocks', s.name)}>{s.name}</button>)}{!f.sectors.length && <small className="adm-muted">분야를 먼저 골라 주세요</small>}</div></div>
            <label>관리자 메모 <small className="adm-muted">사용자에게 보이지 않아요</small><textarea rows="2" value={f.memo} placeholder="이 뉴스의 설계 의도" onChange={e => setF({ ...f, memo: e.target.value })} /></label>
            <div className="adm-row end"><button className="adm-btn ghost" onClick={() => setF(null)}>취소</button><button className="adm-btn primary" onClick={save}>저장</button></div>
          </div>
        )}
      </Card>
    </div>
  );
}

function PeopleView({ season, people2, setPeople, stockList, priceAt, log, toastMsg }) {
  const [q, setQ] = useState('');
  const [sel, setSel] = useState(people2[0].id);
  const [adj, setAdj] = useState({ type: 'give', amount: '', reason: '' });
  const [err, setErr] = useState('');
  const p = people2.find(x => x.id === sel), T = season.turn;
  const list = people2.filter(x => x.nick.includes(q.trim()));
  const apply = () => {
    const amt = Number(adj.amount);
    if (!amt || amt <= 0) return setErr('금액을 입력해 주세요');
    if (!adj.reason.trim()) return setErr('사유를 입력해 주세요');
    if (adj.type === 'take' && amt > p.wallet) return setErr('회수할 캐시가 부족해요');
    setPeople(l => l.map(x => (x.id === p.id ? { ...x, wallet: x.wallet + (adj.type === 'give' ? amt : -amt) } : x)));
    log(p.nick + ' 캐시 수동 ' + (adj.type === 'give' ? '지급 +' : '회수 −') + fmt(amt) + ' (' + adj.reason.trim() + ')');
    setAdj({ type: adj.type, amount: '', reason: '' }); setErr(''); toastMsg('캐시를 ' + (adj.type === 'give' ? '지급' : '회수') + '했어요');
  };
  return (
    <div className="adm-grid2 wide-left">
      <Card title={'참가자 ' + people2.length + '명'} right={<input className="adm-search" placeholder="닉네임 검색" value={q} onChange={e => setQ(e.target.value)} />}>
        <div className="adm-table-wrap"><table className="adm-table"><thead><tr><th>닉네임</th><th>레벨</th><th>투자 가능 금액</th><th>현재 순자산</th><th>수익률</th></tr></thead><tbody>
          {list.map(x => <tr key={x.id} className={'click ' + (sel === x.id ? 'hl' : '')} onClick={() => { setSel(x.id); setErr(''); }}><td><strong>{x.nick}</strong></td><td>Lv.{x.level}</td><td>{fmt(x.investable)}</td><td>{fmt(x.net)}</td><td className={x.ret >= 0 ? 'up' : 'down'}>{x.ret >= 0 ? '▲' : '▼'} {pct(x.ret)}</td></tr>)}
        </tbody></table></div>
      </Card>
      <div className="adm-stack">
        <Card title={p.nick + ' 상세'} desc={'현금 ' + fmt(p.cash) + ' · 보유 캐시(지갑) ' + fmt(p.wallet)}>
          <h4 className="adm-sub">보유 종목</h4>
          <div className="adm-list">{p.holdings.map(([i, qty]) => <div key={i} className="adm-li"><span>{stockList[i].name} · {qty}주</span><small>{fmt(qty * priceAt(i, T))} 캐시</small></div>)}</div>
          <h4 className="adm-sub">거래 내역 · 매매 이유</h4>
          <div className="adm-list">{p.trades.map((t, k) => <div key={k} className="adm-li"><span><b className={t.type === 'buy' ? 'up' : 'down'}>{t.type === 'buy' ? '매수' : '매도'}</b> {t.turn}턴 · {stockList[t.s].name} {t.qty}주 · {fmt(t.price)}</span><Pill tone="soft">{t.reason}</Pill></div>)}</div>
        </Card>
        <Card title="캐시 수동 지급·회수" desc="오류를 바로잡을 때만 사용해요. 사유와 기록이 남아요">
          <div className="adm-form">
            <div className="adm-seg"><button className={adj.type === 'give' ? 'on' : ''} onClick={() => setAdj({ ...adj, type: 'give' })}>지급</button><button className={adj.type === 'take' ? 'on' : ''} onClick={() => setAdj({ ...adj, type: 'take' })}>회수</button></div>
            <label>금액 (캐시)<input type="number" min="0" value={adj.amount} onChange={e => { setAdj({ ...adj, amount: e.target.value }); setErr(''); }} /></label>
            <label>사유 <b className="adm-req">*</b><input value={adj.reason} placeholder="예) 출석 누락 보정" onChange={e => { setAdj({ ...adj, reason: e.target.value }); setErr(''); }} /></label>
            {err && <span className="adm-err">{err}</span>}
            <div className="adm-row end"><button className="adm-btn primary" onClick={apply}>{adj.type === 'give' ? '지급하기' : '회수하기'}</button></div>
          </div>
        </Card>
      </div>
    </div>
  );
}

function RankingView({ season, setSeason, people2, rankBy, setPeople, stockList, log, toastMsg, setModal }) {
  const [tab, setTab] = useState('net');
  const ranks = rankBy(tab);
  const settleOk = season.status === '정산';
  const settle = () => setModal({
    title: '정산을 실행할까요?', body: '최종 자산을 확정하고 이월금을 계산해요. 실행 후에는 되돌릴 수 없어요.', ok: '정산 실행',
    onOk: () => {
      setPeople(l => l.map(p => { const x = people2.find(y => y.id === p.id), profit = x.net - p.investable; return { ...p, finalNet: x.net, carry: profit > 0 ? Math.min(Math.round(profit * season.carryRate / 100), season.carryCap) : 0 }; }));
      setSeason(s => ({ ...s, settled: true })); log(season.name + ' 정산 실행'); toastMsg('정산을 마쳤어요');
    },
  });
  const publish = () => setModal({ title: '랭킹을 공개할까요?', body: '모든 사용자에게 ' + season.name + ' 순자산·수익률 랭킹이 공개돼요.', ok: '랭킹 공개', onOk: () => { setSeason(s => ({ ...s, published: true })); log(season.name + ' 랭킹 공개'); toastMsg('랭킹을 공개했어요'); } });
  const reward = () => { setSeason(s => ({ ...s, rewarded: true })); log(season.name + ' 랭킹 보상 지급 (순자산·수익률 상위 3위 뱃지·칭호)'); toastMsg('상위 3위에게 뱃지와 칭호를 지급했어요'); };
  const n = people2.length, avg = people2.reduce((a, p) => a + p.ret, 0) / n;
  const buys = {}; const reasons = {}; people2.forEach(p => p.trades.forEach(t => { if (t.type === 'buy') buys[t.s] = (buys[t.s] || 0) + t.qty; reasons[t.reason] = (reasons[t.reason] || 0) + 1; }));
  const top = Object.entries(buys).sort((a, b) => b[1] - a[1]).slice(0, 3);
  const totalR = Object.values(reasons).reduce((a, b) => a + b, 0);
  const crashTurn = 3, soldCrash = people2.filter(p => p.trades.some(t => t.turn === crashTurn && t.type === 'sell')).length;
  const steps = [['정산 상태', settleOk || season.settled], ['정산 실행', season.settled], ['랭킹 공개', season.published], ['보상 지급', season.rewarded]];
  return (
    <>
      <Card title="정산·공개 진행" desc="시즌 마지막 날 정산 → 결과 확인 → 랭킹 공개 → 보상 지급 순서로 진행해요">
        <div className="adm-steps">{steps.map(([l, d], i) => <div key={l} className={'adm-step ' + (d ? 'done' : '')}><b>{d ? '✓' : i + 1}</b><span>{l}</span></div>)}</div>
        <div className="adm-row">
          <button className="adm-btn primary" disabled={!settleOk || season.settled} onClick={settle}>정산 실행</button>
          <button className="adm-btn primary" disabled={!season.settled || season.published} onClick={publish}>랭킹 공개</button>
          <button className="adm-btn soft" disabled={!season.published || season.rewarded} onClick={reward}>보상 지급</button>
          {!settleOk && !season.settled && <span className="adm-muted">시즌 상태가 ‘정산’일 때 실행할 수 있어요 · 현재 {season.status}</span>}
        </div>
      </Card>
      <div className="adm-grid2 wide-left">
        <Card title={season.published ? '공개된 랭킹' : '랭킹 미리보기'} desc={season.published ? '사용자에게 공개됐어요' : '관리자만 볼 수 있어요 · 사용자에게는 비공개'}
          right={<div className="adm-seg"><button className={tab === 'net' ? 'on' : ''} onClick={() => setTab('net')}>순자산</button><button className={tab === 'ret' ? 'on' : ''} onClick={() => setTab('ret')}>수익률</button></div>}>
          <div className="adm-table-wrap"><table className="adm-table"><thead><tr><th>순위</th><th>닉네임</th><th>순자산</th><th>수익률</th><th>이월금</th></tr></thead><tbody>
            {ranks.map(p => { const raw = people2.find(x => x.id === p.id); return (
              <tr key={p.id}><td><strong>{['🥇', '🥈', '🥉'][p.rank - 1] || p.rank}</strong></td><td>{p.nick}{season.rewarded && p.rank <= 3 && <Pill tone="gold">뱃지·칭호</Pill>}</td><td>{fmt(p.net)}</td><td className={p.ret >= 0 ? 'up' : 'down'}>{pct(p.ret)}</td><td>{season.settled ? fmt(raw.carry ?? 0) : '정산 후'}</td></tr>
            ); })}
          </tbody></table></div>
          <p className="adm-muted">동점은 같은 순위예요 · 수익률 = (순자산 − 투자 가능 금액) ÷ 투자 가능 금액</p>
        </Card>
        <Card title="시즌 통계">
          <div className="adm-mini">
            <div><span>평균 수익률</span><strong className={avg >= 0 ? 'up' : 'down'}>{pct(avg)}</strong></div>
            <div><span>급락 턴({crashTurn}턴)에 판 사람</span><strong>{Math.round(soldCrash / n * 100)}%</strong></div>
          </div>
          <h4 className="adm-sub">많이 산 종목</h4>
          <div className="adm-list">{top.map(([s, q], i) => <div key={s} className="adm-li"><span>{i + 1}. {stockList[s].name}</span><small>{q}주</small></div>)}</div>
          <h4 className="adm-sub">매매 이유 비율</h4>
          <div className="adm-bars">{REASONS.map(r => { const v = (reasons[r] || 0) / totalR; return <div key={r}><span>{r}</span><i><b style={{ width: (v * 100) + '%' }} /></i><small>{Math.round(v * 100)}%</small></div>; })}</div>
        </Card>
      </div>
    </>
  );
}

function CashView({ cashRules, setCashRules, log, toastMsg }) {
  const [d, setD] = useState(cashRules);
  return (
    <Card title="활동별 지급 캐시" desc="학습 완료, 레벨업, 연속 출석 등 활동마다 지급할 캐시예요">
      <div className="adm-table-wrap"><table className="adm-table"><thead><tr><th>활동</th><th>지급 조건</th><th>지급 캐시</th></tr></thead><tbody>
        {d.map((r, i) => <tr key={r.name}><td><strong>{r.name}</strong></td><td>{r.desc}</td><td><input className="adm-cell num" type="number" min="0" step="100" value={r.amount} onChange={e => setD(x => x.map((y, j) => (j === i ? { ...y, amount: Number(e.target.value) } : y)))} /></td></tr>)}
      </tbody></table></div>
      <div className="adm-row end"><button className="adm-btn ghost" onClick={() => setD(cashRules)}>되돌리기</button><button className="adm-btn primary" onClick={() => { setCashRules(d); log('캐시 지급 규칙 변경: ' + d.map(r => r.name + ' ' + fmt(r.amount)).join(', ')); toastMsg('지급 규칙을 저장했어요'); }}>저장</button></div>
    </Card>
  );
}

function LogView({ audit }) {
  return (
    <Card title={'작업 기록 ' + audit.length + '건'} desc="턴 진행, 정산, 랭킹 공개, 캐시 수동 지급·회수 등">
      <div className="adm-table-wrap"><table className="adm-table"><thead><tr><th>시각</th><th>담당자</th><th>작업</th></tr></thead><tbody>
        {audit.map((a, i) => <tr key={i}><td>{a.at}</td><td>{a.by}</td><td>{a.what}</td></tr>)}
      </tbody></table></div>
    </Card>
  );
}

module.exports = { Admin };
