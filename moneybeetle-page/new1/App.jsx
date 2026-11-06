const { useEffect, useState, useContext, createContext } = React;
const MoneyCtx = createContext({ level: 8, season: 'active', trades: [] });
const initialTrades = [
  { turn: '4턴', stock: '한빛반도체', type: 'buy', qty: 6, price: 81200, reason: '뉴스를 보고' },
  { turn: '3턴', stock: '그린셀에너지', type: 'buy', qty: 40, price: 18640, reason: '가격이 싸서' },
  { turn: '2턴', stock: '별숲엔터', type: 'buy', qty: 10, price: 40500, reason: '오를 것 같아서' },
  { turn: '1턴', stock: '한빛반도체', type: 'buy', qty: 6, price: 76800, reason: '나눠 담으려고' },
];
const SEASON_OPTIONS = [['active', '진행 중'], ['stale', '턴 전환 직후'], ['closed', '일요일 휴장'], ['settle', '정산 중'], ['ended', '랭킹 공개'], ['join', '참가 전'], ['joinClosed', '참가 마감'], ['none', '시즌 없음']];



const stocks = [
  { initial: '한', name: '한빛반도체', sector: '반도체', price: '84,500', change: '+2.8%', up: true, tone: 'mint' },
  { initial: '모', name: '모아모빌리티', sector: '모빌리티', price: '32,800', change: '+1.4%', up: true, tone: 'orange' },
  { initial: '그', name: '그린셀에너지', sector: '에너지', price: '18,250', change: '−0.9%', up: false, tone: 'blue' },
  { initial: '별', name: '별숲엔터', sector: '콘텐츠', price: '45,100', change: '+4.2%', up: true, tone: 'purple' },
  { initial: '뉴', name: '뉴웨이브로보틱스', sector: '로봇', price: '71,300', change: '−1.7%', up: false, tone: 'pink' },
  { initial: '온', name: '온담바이오', sector: '바이오', price: '26,900', change: '+0.6%', up: true, tone: 'yellow' },
  { initial: '미', name: '미래칩스', sector: '반도체', price: '56,700', change: '+1.1%', up: true, tone: 'blue' },
  { initial: '라', name: '라온드라이브', sector: '모빌리티', price: '29,400', change: '−0.4%', up: false, tone: 'mint' },
  { initial: '해', name: '해솔파워', sector: '에너지', price: '22,150', change: '+2.2%', up: true, tone: 'orange' },
  { initial: '다', name: '다온스튜디오', sector: '콘텐츠', price: '38,600', change: '−1.2%', up: false, tone: 'purple' },
  { initial: '키', name: '키움메카트로닉스', sector: '로봇', price: '63,900', change: '+3.0%', up: true, tone: 'pink' },
  { initial: '새', name: '새봄제약', sector: '바이오', price: '41,250', change: '+0.8%', up: true, tone: 'yellow' },
  { initial: '구', name: '구름핀테크', sector: '핀테크', price: '19,800', change: '+1.6%', up: true, tone: 'mint' },
  { initial: '보', name: '보름페이', sector: '핀테크', price: '24,300', change: '−0.7%', up: false, tone: 'blue' },
  { initial: '참', name: '참좋은푸드', sector: '식품', price: '15,750', change: '+0.3%', up: true, tone: 'orange' },
  { initial: '산', name: '산들키친', sector: '식품', price: '21,900', change: '+1.9%', up: true, tone: 'yellow' },
  { initial: '푸', name: '푸른클라우드', sector: '클라우드', price: '48,200', change: '+2.5%', up: true, tone: 'blue' },
  { initial: '코', name: '코어스택', sector: '클라우드', price: '53,100', change: '−0.6%', up: false, tone: 'purple' },
  { initial: '마', name: '마음케어', sector: '헬스케어', price: '31,400', change: '+1.2%', up: true, tone: 'pink' },
  { initial: '바', name: '바른메디', sector: '헬스케어', price: '27,650', change: '−1.0%', up: false, tone: 'mint' },
  { initial: '이', name: '이음리빙', sector: '생활', price: '12,900', change: '+0.5%', up: true, tone: 'yellow' },
  { initial: '소', name: '소담하우스', sector: '생활', price: '17,450', change: '+1.3%', up: true, tone: 'orange' },
  { initial: '길', name: '길벗로지스', sector: '물류', price: '36,800', change: '−0.8%', up: false, tone: 'blue' },
  { initial: '빠', name: '빠른배송', sector: '물류', price: '42,100', change: '+2.0%', up: true, tone: 'mint' },
  { initial: '배', name: '배움나무', sector: '교육', price: '14,600', change: '+0.9%', up: true, tone: 'purple' },
  { initial: '슬', name: '슬기러닝', sector: '교육', price: '18,900', change: '−0.3%', up: false, tone: 'pink' },
  { initial: '맑', name: '맑은소재', sector: '소재', price: '33,500', change: '+1.7%', up: true, tone: 'blue' },
  { initial: '단', name: '단단컴포짓', sector: '소재', price: '39,700', change: '−1.4%', up: false, tone: 'orange' },
  { initial: '누', name: '누리여행', sector: '여행', price: '25,300', change: '+2.4%', up: true, tone: 'yellow' },
  { initial: '쉼', name: '쉼표투어', sector: '여행', price: '20,850', change: '−0.5%', up: false, tone: 'purple' },
];

const news = [
  { turn: 5, title: 'AI 기기 수요 확대, 반도체 업계에 훈풍', tags: ['한빛반도체', '뉴웨이브로보틱스'], sectors: ['반도체', '로봇'] },
  { turn: 5, title: '도심형 전기차 보조금 확대안 발표', tags: ['모아모빌리티', '그린셀에너지'], sectors: ['모빌리티', '에너지'] },
  { turn: 5, title: 'K-콘텐츠 신규 구독자 수 역대 최고치', tags: ['별숲엔터'], sectors: ['콘텐츠'] },
];

const holdings = [
  ['한빛반도체', '1,014,000 캐시', '+7.0%'],
  ['그린셀에너지', '730,000 캐시', '−2.1%'],
  ['별숲엔터', '451,000 캐시', '+11.4%'],
];

function Button({ children, className = '', onClick, disabled, ariaLabel, ariaExpanded }) {
  return (
    <button className={className} onClick={onClick} disabled={disabled} aria-label={ariaLabel} aria-expanded={ariaExpanded}>
      {children}
    </button>
  );
}

function Logo() {
  return (
    <div className="logo">
      <span className="logo-mark" aria-hidden="true">
        <svg viewBox="0 0 32 32">
          <path d="M10.8 9.4 7.3 6M21.2 9.4 24.7 6M9 16H4m24 0h-5M10 22l-4 4m16-4 4 4" />
          <path d="M16 7c5 0 8.5 4 8.5 9.5S21 26 16 26s-8.5-4-8.5-9.5S11 7 16 7Z" />
          <path d="M16 8v18M8 15h16" />
        </svg>
      </span>
      <span>Money Beetle</span>
    </div>
  );
}

function LineIcon({ name }) {
  const paths = {
    menu: <><path d="M4 7h16M4 12h16M4 17h16" /></>,
    book: <><path d="M5 4.5h10a3 3 0 0 1 3 3V20H8a3 3 0 0 1-3-3Z" /><path d="M8 4.5V20" /></>,
    home: <><path d="m3.5 11 8.5-7 8.5 7" /><path d="M6 9.5V20h12V9.5M10 20v-6h4v6" /></>,
    chart: <><path d="M4 19V5M4 19h16" /><path d="m7 15 4-5 3 3 5-7" /></>,
    bookmark: <><path d="M7 4h10v16l-5-3-5 3Z" /></>,
    user: <><circle cx="12" cy="8" r="4" /><path d="M4.5 20c.8-4 3.3-6 7.5-6s6.7 2 7.5 6" /></>,
    'chevron-up': <path d="m7 14 5-5 5 5" />,
    'chevron-down': <path d="m7 10 5 5 5-5" />,
  };
  return <svg className="line-icon" viewBox="0 0 24 24" aria-hidden="true">{paths[name]}</svg>;
}

const mbWords = [
  { term: '복리', desc: '원금에 붙은 이자에도 다시 이자가 붙는 방식.' },
  { term: '실수령액', desc: '세전 급여에서 4대보험과 소득세를 뺀 금액.' },
  { term: '배당', desc: '기업이 이익의 일부를 주주에게 나눠 주는 것.' },
  { term: '리밸런싱', desc: '정해 둔 자산 비중으로 포트폴리오를 다시 맞추는 일.' },
  { term: '청약 가점', desc: '무주택 기간·부양가족·통장 가입 기간으로 매기는 점수.' },
];

function BrandFixed({ toggleSidebar, sidebarOpen }) {
  return (
    <div className="mb-brand">
      <button className="mb-menu-btn" onClick={toggleSidebar} aria-label="메뉴" aria-expanded={sidebarOpen}>
        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round"><path d="M4 6h16M4 12h16M4 18h16" /></svg>
      </button>
      <a href="Main.dc.html" className="mb-logo"><img src="assets/logo.png" alt="Money Beetle" /></a>
    </div>
  );
}

function Header({ showSpacer }) {
  const [wordsOpen, setWordsOpen] = useState(false);
  return (
    <>
      <header className="mb-header">
        {showSpacer && <div style={{ width: 200, height: 40, flex: 'none' }} />}
        <form action="UserLectureCategory.dc.html" method="get" style={{ flex: 1, minWidth: 0, display: 'flex', justifyContent: 'center', margin: 0 }}><label style={{ width: '100%', maxWidth: 480, height: 44, boxSizing: 'border-box', display: 'flex', alignItems: 'center', gap: 10, padding: '0 18px', borderRadius: 999, background: '#fff', border: '1.5px solid #D5E1CC' }}><svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="#263B1A" strokeWidth="2" strokeLinecap="round" style={{ flex: 'none' }}><circle cx="11" cy="11" r="7" /><path d="m20 20-3.5-3.5" /></svg><input name="q" type="search" placeholder="강의, 경제 용어 검색" style={{ all: 'unset', flex: 1, minWidth: 0, fontSize: 15, color: '#263B1A' }} /></label></form>
        <button className="mb-pill-btn" onClick={() => setWordsOpen(true)}>오늘의 경제단어</button>
        <span className="mb-tag neutral" style={{ display: 'inline-flex', alignItems: 'center', gap: 4 }}><img src="assets/cash-coin2.png" alt="캐시" style={{ width: 18, height: 18, flex: 'none' }} />12,400</span>
        <button data-mb-bell="1" aria-label="알림" style={{ all: 'unset', cursor: 'pointer', position: 'relative', width: 38, height: 38, borderRadius: '50%', display: 'grid', placeItems: 'center', flex: 'none' }}><svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"><path d="M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9" /><path d="M10.3 21a1.94 1.94 0 0 0 3.4 0" /></svg></button>
        <a href="UserMyPage.dc.html" className="mb-profile">
          <span className="mb-avatar34">민</span>민지
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5"><path d="m9 6 6 6-6 6" /></svg>
        </a>
      </header>
      {wordsOpen && (
        <div className="mb-backdrop" onClick={() => setWordsOpen(false)}>
          <div className="mb-dialog" onClick={(e) => e.stopPropagation()}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
              <span className="mb-tag accent" style={{ fontSize: 11, padding: '3px 10px' }}>오늘의 경제단어</span>
              <div style={{ flex: 1 }} />
              <button className="mb-menu-btn static" onClick={() => setWordsOpen(false)} aria-label="닫기">
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round"><path d="M18 6 6 18M6 6l12 12" /></svg>
              </button>
            </div>
            <div style={{ display: 'flex', flexDirection: 'column' }}>
              {mbWords.map(w => (
                <div className="mb-word" key={w.term}><strong>{w.term}</strong><span>{w.desc}</span></div>
              ))}
            </div>
            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 7 }}>
              <button className="mb-ghost" onClick={() => setWordsOpen(false)}>오늘 하루 보지 않기</button>
              <button className="mb-primary" onClick={() => setWordsOpen(false)}>확인</button>
            </div>
          </div>
        </div>
      )}
    </>
  );
}

function Sidebar({ open, onClose, onInvestmentHome, level = 8 }) {
  const [lectureOpen, setLectureOpen] = useState(true);
  const [trainingOpen, setTrainingOpen] = useState(true);
  const [myOpen, setMyOpen] = useState(false);
  return (
    <aside className="mb-sidebar" data-mb-drawer="1" data-open={String(open)} aria-label="메뉴" aria-hidden={!open}>
      <div className="mb-sidebar-spacer"><a href="Main.dc.html" style={{ display: 'block', textDecoration: 'none' }}><img src="assets/logo.png" alt="Money Beetle" style={{ height: 30, width: 'auto', maxWidth: 160, objectFit: 'contain', display: 'block' }} /></a><button data-mb-close="1" onClick={onClose} aria-label="메뉴 닫기" style={{ all: 'unset', cursor: 'pointer', width: 40, height: 40, borderRadius: 999, display: 'grid', placeItems: 'center', color: '#263B1A' }}><svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round"><path d="M6 6l12 12M18 6L6 18" /></svg></button></div>
      <div className="mb-profile-card">
        <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
          <div className="mb-avatar40">민</div>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 2, lineHeight: 1.3 }}>
            <span style={{ fontWeight: 600, fontSize: 14, color: '#263B1A' }}>민지</span>
            <span style={{ fontSize: 12, color: '#687161', display: 'flex', alignItems: 'center', gap: 6, whiteSpace: 'nowrap' }}>Lv. {level}<span style={{ display: 'inline-block', padding: '1px 8px', borderRadius: 999, background: '#B9EF8E', color: '#263B1A', fontWeight: 700, fontSize: 11 }}>{level <= 3 ? '고시원' : level <= 7 ? '원룸' : level <= 11 ? '투룸' : level <= 16 ? '아파트' : '내 집'}</span><span style={{ display: 'inline-flex', alignItems: 'center', gap: 3, marginLeft: 4 }}><img src="assets/cash-coin2.png" alt="캐시" style={{ width: 13, height: 13, flex: 'none' }} />1,250,000</span></span>
          </div>
        </div>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 5, paddingTop: 2 }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 11, color: '#687161' }}><span>경험치</span><span>90 / 150 XP</span></div>
          <div style={{ height: 4, background: '#D5E1CC' }}><div style={{ height: '100%', background: '#B9EF8E', width: '60%' }} /></div>
          <span style={{ fontSize: 11, color: '#687161', display: 'flex', alignItems: 'center', gap: 4, whiteSpace: 'nowrap' }}>Lv. {Math.min(level + 1, 20)} 달성 시<img src="assets/cash-coin2.png" alt="캐시" style={{ width: 12, height: 12, flex: 'none' }} />{(Math.min(level + 1, 20) * 1000).toLocaleString()} 지급</span>
        </div>
      </div>
      <nav className="mb-nav">
        <a href="UserHome-vr2.dc.html" className="mb-nav-item"><svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round"><path d="M3 10.5 12 3l9 7.5V20a1 1 0 0 1-1 1h-5v-6H9v6H4a1 1 0 0 1-1-1z" /></svg>홈</a>
        <button className="mb-nav-item" data-mb-keep="1" onClick={() => setLectureOpen(v => !v)}>
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round"><path d="M2 4h6a4 4 0 0 1 4 4v13a3 3 0 0 0-3-3H2z" /><path d="M22 4h-6a4 4 0 0 0-4 4v13a3 3 0 0 1 3-3h7z" /></svg>
          <span style={{ flex: 1 }}>강의</span><span className="mb-caret">{lectureOpen ? '▲' : '▼'}</span>
        </button>
        {lectureOpen && <div className="mb-sub"><a href="UserLectureCategory.dc.html?cat=money">돈 굴리기</a><a href="UserLectureCategory.dc.html?cat=estate">부동산</a></div>}
        <button className="mb-nav-item" data-mb-keep="1" onClick={() => setTrainingOpen(v => !v)}>
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round"><path d="M3 3v18h18" /><path d="m7 15 4-4 3 3 6-6" /></svg>
          <span style={{ flex: 1 }}>트레이닝</span><span className="mb-caret">{trainingOpen ? '▲' : '▼'}</span>
        </button>
        {trainingOpen && (
          <div className="mb-sub">
            <a href="#" className="selected" style={{ display: 'flex', alignItems: 'center', gap: 6 }} onClick={(e) => { e.preventDefault(); onInvestmentHome(); }}>{level < 8 && <img src="uploads/lock.png" alt="잠김" style={{ width: 14, height: 14, objectFit: 'contain', flex: 'none' }} />}<span>모의 투자</span>{level < 8 && <span className="mb-lock">· Lv.8부터</span>}</a>
            <a href="UserDemoHousingSubscription.dc.html" style={{ display: 'flex', alignItems: 'center', gap: 6 }}>{level < 12 && <img src="uploads/lock.png" alt="잠김" style={{ width: 14, height: 14, objectFit: 'contain', flex: 'none' }} />}<span>모의 청약</span>{level < 12 && <span className="mb-lock">· Lv.12부터</span>}</a>
          </div>
        )}
        <button className="mb-nav-item" data-mb-keep="1" onClick={() => setMyOpen(v => !v)}>
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round"><path d="M19 21l-7-4-7 4V5a2 2 0 0 1 2-2h10a2 2 0 0 1 2 2z" /></svg>
          <span style={{ flex: 1 }}>내 학습</span><span className="mb-caret">{myOpen ? '▲' : '▼'}</span>
        </button>
        {myOpen && <div className="mb-sub"><a href="UserMyPage.dc.html#tab-wrong">퀴즈 오답노트</a><a href="UserMyPage.dc.html#tab-scrap">강의 스크랩</a><a href="UserMyPage.dc.html">시청기록 · 학습완료</a></div>}
        <a href="UserInquiry.dc.html" className="mb-nav-item"><svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round"><path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z" /></svg>문의 게시판</a>
        <a href="UserMyPage.dc.html" className="mb-nav-item"><svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round"><circle cx="12" cy="8" r="4" /><path d="M4 21a8 8 0 0 1 16 0" /></svg>마이페이지</a>
      </nav>
    </aside>
  );
}

function Footer() {
  return (
    <footer>
      <span className="shield" aria-hidden="true">✓</span>
      가상 시장에서 진행되는 학습용 모의투자이며 실제 금융 거래가 아닙니다. 모든 종목은 가상 기업입니다.
    </footer>
  );
}

function HighlightedName({ name, query }) {
  if (!query) return <>{name}</>;
  const index = name.toLowerCase().indexOf(query.toLowerCase());
  if (index < 0) return <>{name}</>;
  return <>{name.slice(0, index)}<mark>{name.slice(index, index + query.length)}</mark>{name.slice(index + query.length)}</>;
}

function StockSearch({ onSelect, compact = false }) {
  const [query, setQuery] = useState('');
  const [open, setOpen] = useState(false);
  const recentNames = ['한빛반도체', '그린셀에너지', '별숲엔터'];
  const recent = recentNames.map(name => stocks.find(stock => stock.name === name)).filter(Boolean);
  const results = query.trim()
    ? stocks.filter(stock => `${stock.name} ${stock.sector}`.toLowerCase().includes(query.trim().toLowerCase())).slice(0, 6)
    : recent;

  const chooseStock = (stock) => {
    setOpen(false);
    setQuery('');
    onSelect(stock);
  };

  return (
    <div className={`stock-search ${compact ? 'compact-search' : ''}`}>
      <div className={`search-input ${open ? 'focused' : ''}`}>
        <svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="11" cy="11" r="6.5" /><path d="m16 16 4 4" /></svg>
        <input
          value={query}
          onChange={(event) => { setQuery(event.target.value); setOpen(true); }}
          onFocus={() => setOpen(true)}
          onBlur={() => window.setTimeout(() => setOpen(false), 150)}
          placeholder="종목명이나 분야로 검색 (예: 반도체)"
          aria-label="종목 검색"
        />
      </div>
      {open && (
        <div className="search-dropdown">
          {!query && <div className="search-caption">최근 본 종목</div>}
          {results.length > 0 ? (
            <div className="search-results">
              {results.map(stock => (
                <Button className="search-result" onClick={() => chooseStock(stock)} key={stock.name}>
                  <span className={`stock-avatar ${stock.tone}`}>{stock.initial}</span>
                  <span className="result-name"><strong><HighlightedName name={stock.name} query={query.trim()} /></strong><small>{stock.sector}</small></span>
                  <span className="stock-price"><strong>{stock.price} 캐시</strong><small className={stock.up ? 'up' : 'down'}>{stock.up ? '▲' : '▼'} {stock.change}</small></span>
                </Button>
              ))}
            </div>
          ) : (
            <p className="empty-search">검색 결과가 없어요.<br />종목명이나 분야를 다시 확인해 주세요.</p>
          )}
        </div>
      )}
    </div>
  );
}

function SeasonProgress() {
  const days = [
    { weekday: '월', label: '1턴', state: 'complete' },
    { weekday: '화', label: '2턴', state: 'complete' },
    { weekday: '수', label: '3턴', state: 'complete' },
    { weekday: '목', label: '4턴', state: 'complete' },
    { weekday: '금', label: '5턴', state: 'current' },
    { weekday: '토', label: '6턴', state: 'upcoming' },
    { weekday: '일', label: '휴장', state: 'closed' },
    { weekday: '월', label: '7턴', state: 'upcoming' },
    { weekday: '화', label: '8턴', state: 'upcoming' },
    { weekday: '수', label: '9턴', state: 'upcoming' },
    { weekday: '목', label: '10턴', state: 'upcoming' },
    { weekday: '금', label: '11턴', state: 'upcoming' },
    { weekday: '토', label: '12턴', state: 'upcoming' },
    { weekday: '일', label: '정산 · 랭킹 공개', state: 'settlement' },
  ];
  return (
    <div className="season-wrap">
      <div className="season-line" aria-label="시즌 진행률">
        {days.map((day) => (
          <div className={`turn-cell ${day.state}`} key={`${day.weekday}-${day.label}`}>
            <span className="weekday">{day.state === 'complete' ? <><i>✓</i>{day.weekday}</> : day.weekday}</span>
            <small>{day.label}</small>
          </div>
        ))}
      </div>
      <div className="progress-legend"><span><i className="dot mint" />진행 완료</span><span><i className="dot outline" />현재 턴</span><span><i className="dot gray" />일요일 휴장</span><span><i className="dot gold" />정산·랭킹 공개</span></div>
    </div>
  );
}

function MarketCard({ onStock, onSector, onAll }) {
  const [tab, setTab] = useState('popular');
  return (
    <section className="card side-card market-card">
      <div className="card-head">
        <h2>오늘의 가상 시세</h2>
        <span className="live-dot">5턴 기준</span>
      </div>
      <StockSearch onSelect={onStock} />
      <div className="segmented mini market-toggle">
        <Button className={tab === 'popular' ? 'selected' : ''} onClick={() => setTab('popular')}>인기</Button>
        <Button className={tab === 'sector' ? 'selected' : ''} onClick={() => setTab('sector')}>분야별</Button>
      </div>
      {tab === 'sector' ? <SectorRows onSector={onSector} /> : <div className="stock-list">
        {stocks.slice(0, 8).map((stock) => (
          <Button className="stock-row" onClick={() => onStock(stock)} key={stock.name}>
            <span className={`stock-avatar ${stock.tone}`}>{stock.initial}</span>
            <span className="stock-name"><strong>{stock.name}</strong><small>{stock.sector}</small></span>
            <span className="stock-price"><strong>{stock.price} 캐시</strong><small className={stock.up ? 'up' : 'down'}>{stock.up ? '▲' : '▼'} {stock.change}</small></span>
          </Button>
        ))}
      </div>}
      <Button className="text-link" onClick={onAll}>전체 30개 종목 보기 <span>›</span></Button>
    </section>
  );
}

function RankingCard() {
  const [tab, setTab] = useState('asset');
  const listRef = React.useRef(null);
  React.useLayoutEffect(() => {
    const list = listRef.current; if (!list) return;
    const card = list.closest('.rank-card');
    const fit = () => {
      const rows = [...list.children];
      rows.forEach(r => { r.style.display = ''; });
      const cs = getComputedStyle(card);
      const my = card.querySelector('.my-rank'), note = card.querySelector('.rank-note');
      const outer = el => { if (!el) return 0; const st = getComputedStyle(el); return el.getBoundingClientRect().height + parseFloat(st.marginTop) + parseFloat(st.marginBottom); };
      const bottom = card.getBoundingClientRect().bottom - parseFloat(cs.paddingBottom) - parseFloat(cs.borderBottomWidth);
      const avail = bottom - outer(note) - outer(my) - parseFloat(getComputedStyle(list).marginBottom) - list.getBoundingClientRect().top;
      const top = list.getBoundingClientRect().top;
      let n = 0;
      for (const r of rows) { if (r.getBoundingClientRect().bottom - top <= avail + 0.5) n++; else break; }
      rows.forEach((r, i) => { if (i >= n) r.style.display = 'none'; });
    };
    let raf = 0;
    const run = () => { cancelAnimationFrame(raf); raf = requestAnimationFrame(fit); };
    const ro = new ResizeObserver(run); ro.observe(card); ro.observe(card.parentElement); run();
    window.addEventListener('resize', run);
    const t = setTimeout(run, 500);
    if (document.fonts) document.fonts.ready.then(run);
    return () => { ro.disconnect(); cancelAnimationFrame(raf); clearTimeout(t); window.removeEventListener('resize', run); };
  }, [tab]);
  const { season } = useContext(MoneyCtx);
  const rankNote = season === 'ended' ? '시즌 7 랭킹이 공개됐어요. 상위 3위에게는 뱃지와 칭호 장식을 드려요' : season === 'settle' ? '정산 중이에요. 정산이 끝나면 랭킹이 공개돼요' : '이번 시즌 랭킹은 시즌 종료 후 공개돼요';
  const ranks = [
    ['1', '초록개미', '내 집', '8,920,400 캐시'],
    ['2', '월급지킴이', '아파트', '8,410,200 캐시'],
    ['3', '장기투자왕', '아파트', '7,985,000 캐시'],
    ['4', '수익이좋아', '투룸', '7,420,100 캐시'],
    ['5', '민트초코단', '투룸', '6,990,800 캐시'],
    ['6', '저축요정', '투룸', '6,540,300 캐시'],
    ['7', '복리왕', '투룸', '6,120,000 캐시'],
    ['8', '티끌모아', '원룸', '5,880,500 캐시'],
    ['9', '개미지망생', '원룸', '5,410,900 캐시'],
    ['10', '첫월급', '원룸', '5,020,700 캐시'],
  ];
  const rates = [42.8, 39.4, 35.1, 31.7, 28.3, 25.6, 22.9, 20.4, 18.1, 15.7];
  const row = ([rank, name, title, value]) => (
    <div className={`rank-row${Number(rank) > 3 ? ' rank-row-sm' : ''}`} key={rank}>
      <span className={`rank-number r${rank}`}>{({ 1: '🥇', 2: '🥈', 3: '🥉' })[rank] || rank}</span>
      <span className="rank-name" style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-start', gap: 4, minWidth: 0 }}><span style={{ display: 'flex', alignItems: 'center', gap: 6, minWidth: 0, whiteSpace: 'nowrap' }}><span style={{ overflow: 'hidden', textOverflow: 'ellipsis' }}>{name}</span><small style={{ whiteSpace: 'nowrap' }}>{title}</small></span>{Number(rank) <= 3 && <span className="gold-chip" style={{ whiteSpace: 'nowrap' }}>뱃지·칭호</span>}</span>
      <strong>{tab === 'asset' ? value : `+${rates[Number(rank) - 1]}%`}</strong>
    </div>
  );
  return (
    <section className="card side-card rank-card">
      <div className="card-head">
        <div><span className="eyebrow">SEASON 6</span><h2>지난 시즌 랭킹</h2></div>
        <div className="segmented mini">
          <Button className={tab === 'asset' ? 'selected' : ''} onClick={() => setTab('asset')}>순자산</Button>
          <Button className={tab === 'rate' ? 'selected' : ''} onClick={() => setTab('rate')}>수익률</Button>
        </div>
      </div>
      <div className="rank-list" ref={listRef}>
        {ranks.map(row)}
      </div>
      <div className="my-rank"><span><strong>나</strong><b>23위</b></span><strong>{tab === 'asset' ? '4,610,000 캐시' : '+6.5%'}</strong></div>
      <p className="rank-note">{rankNote}</p>
    </section>
  );
}


function SectorRows({ onSector }) {
  const sectors = [...new Set(stocks.map(st => st.sector))].slice(0, 8);
  return (
    <div className="stock-list">
      {sectors.map(sec => {
        const list = stocks.filter(st => st.sector === sec);
        return (
          <Button className="stock-row" onClick={() => onSector(sec)} key={sec}>
            <span className={'stock-avatar ' + list[0].tone}>{sec[0]}</span>
            <span className="stock-name"><strong>{sec}</strong><small>{list.map(st => st.name).join(' · ')}</small></span>
            <span className="stock-price"><strong>{list.length}종목</strong><small className="sector-go">›</small></span>
          </Button>
        );
      })}
    </div>
  );
}

function AllStocks({ initialSector, onStock }) {
  const sectors = [...new Set(stocks.map(st => st.sector))];
  const [sector, setSector] = useState(initialSector || '전체');
  const shown = sector === '전체' ? sectors : [sector];
  return (
    <section className="card all-stocks">
      <div className="card-head"><div><span className="eyebrow">ALL STOCKS</span><h2>전체 {stocks.length}개 종목</h2></div><span className="live-dot">5턴 기준 · {sectors.length}개 분야</span></div>
      <div className="sector-chips">
        {['전체', ...sectors].map(sec => <Button key={sec} className={sector === sec ? 'selected' : ''} onClick={() => setSector(sec)}>{sec}</Button>)}
      </div>
      <div className="sector-groups">
        {shown.map(sec => (
          <div className="sector-group" key={sec}>
            <h3>{sec}<small>{stocks.filter(st => st.sector === sec).length}종목</small></h3>
            {stocks.filter(st => st.sector === sec).map(stock => (
              <Button className="stock-row" onClick={() => onStock(stock)} key={stock.name}>
                <span className={'stock-avatar ' + stock.tone}>{stock.initial}</span>
                <span className="stock-name"><strong>{stock.name}</strong><small>{stock.sector}</small></span>
                <span className="stock-price"><strong>{stock.price} 캐시</strong><small className={stock.up ? 'up' : 'down'}>{stock.up ? '▲' : '▼'} {stock.change}</small></span>
              </Button>
            ))}
          </div>
        ))}
      </div>
    </section>
  );
}

function TradeHistory() {
  const { trades } = useContext(MoneyCtx);
  return (
    <section className="card trade-card">
      <div className="card-head"><div><span className="eyebrow">TRADE LOG</span><h2>내 거래 내역</h2></div><span className="live-dot">총 {trades.length}건</span></div>
      <div className="trade-scroll">
        <div className="trade-row trade-head"><span>턴</span><span>종목</span><span>구분</span><span>수량</span><span>체결가</span><span>매매 이유</span></div>
        {trades.map((t, i) => (
          <div className="trade-row" key={i}>
            <span><b className="turn-badge">{t.turn}</b></span>
            <strong>{t.stock}</strong>
            <span className={t.type === 'buy' ? 'trade-buy' : 'trade-sell'}>{t.type === 'buy' ? '매수' : '매도'}</span>
            <span>{t.qty}주</span>
            <span>{t.price.toLocaleString()} 캐시</span>
            <span className="trade-reason">{t.reason}</span>
          </div>
        ))}
      </div>
    </section>
  );
}

function SeasonNotice() {
  const { season } = useContext(MoneyCtx);
  const map = {
    closed: ['오늘은 휴장일이에요', '다음 거래일에 다시 거래할 수 있어요. 시세와 뉴스는 그대로 볼 수 있어요.'],
    settle: ['시즌이 끝났어요', '결과를 기다려 주세요. 정산이 끝나면 랭킹이 공개돼요.'],
    ended: ['시즌 7 랭킹이 공개됐어요', '순자산·수익률 랭킹에서 내 순위를 확인해 보세요.'],
  };
  if (!map[season]) return null;
  return <div className={'season-notice ' + season}><div><strong>{map[season][0]}</strong><p>{map[season][1]}</p></div></div>;
}

function StateScreen({ title, desc, children }) {
  return (
    <main className="page state-page">
      <div className="state-screen">
        <svg width="36" height="36" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round"><rect x="4" y="11" width="16" height="10" /><path d="M8 11V7a4 4 0 0 1 8 0v4" /></svg>
        <h1>{title}</h1>
        <p>{desc}</p>
        <div>{children}</div>
      </div>
    </main>
  );
}

function JoinScreen({ onJoin }) {
  return (
    <main className="page state-page">
      <div className="state-screen join">
        <span className="eyebrow season">VIRTUAL MARKET · SEASON 7</span>
        <h1>시즌 7에 참가할까요?</h1>
        <p>14일 · 12턴 동안 같은 가상 시장에서 모두가 함께 투자해요.</p>
        <div className="join-sum">
          <div><span>모은 캐시</span><strong>3,200,000 캐시</strong></div>
          <div><span>이월금</span><strong>+ 1,620,000 캐시</strong></div>
          <div className="total-line"><span>내 투자 가능 금액</span><strong>4,820,000 캐시</strong></div>
        </div>
        <ul className="join-rules">
          <li>시즌당 1회만 참가할 수 있고, 참가하면 금액은 시즌 중 바뀌지 않아요</li>
          <li>첫 턴이 끝나기 전까지만 참가할 수 있어요</li>
          <li>수수료와 세금은 없어요</li>
        </ul>
        <Button className="primary-button" onClick={onJoin}>시즌 7 참가하기 <span>→</span></Button>
      </div>
    </main>
  );
}

function DemoPanel({ level, setLevel, season, setSeason }) {
  const [open, setOpen] = useState(false);
  return (
    <div className="demo-panel">
      <button className="demo-toggle" onClick={() => setOpen(v => !v)}>시연 상태 {open ? '▼' : '▲'}</button>
      {open && (
        <div className="demo-body">
          <label>내 레벨<select value={level} onChange={(e) => setLevel(Number(e.target.value))}><option value={4}>Lv.4 원룸 (잠김)</option><option value={8}>Lv.8 투룸 (해금)</option></select></label>
          <label>시즌 상태<select value={season} onChange={(e) => setSeason(e.target.value)}>{SEASON_OPTIONS.map(([v, l]) => <option key={v} value={v}>{l}</option>)}</select></label>
          <small>기획서의 상태별 화면을 확인하는 용도예요</small>
        </div>
      )}
    </div>
  );
}

function Home({ goChart, goNews, goAll }) {
  return (
    <main className="page home-page">
      <div className="home-grid">
        <div className="home-top">
          <SeasonNotice />
          <section className="hero">
            <div className="hero-top">
              <div>
                <span className="eyebrow season">VIRTUAL MARKET · SEASON 7</span>
                <h1>시즌 7 진행 중</h1>
                <p><strong>5턴 / 12턴</strong><span /> 다음 턴까지 <b>06:24:13</b></p>
              </div>
              <div className="season-chip"><span>시즌 종료</span><strong>D−9</strong></div>
            </div>
            <SeasonProgress />
          </section>

          <section className="balance-card">
            <div>
              <span className="label">내 투자 가능 금액</span>
              <div className="money"><small>남은 현금</small> 2,939,000<small>캐시</small></div>
              <p>시즌 시작 자금 <strong>4,820,000 캐시</strong> = 모은 캐시 <strong>3,200,000 캐시</strong> + 이월금 <strong>1,620,000 캐시</strong></p>
              <p className="rule-note">시즌 참가 완료 · 시즌당 1회 · 금액은 시즌 중 바뀌지 않아요<br />이월금은 시즌이 끝나면 수익의 30%(상한 안)가 다음 시즌으로 저장돼요 · 수수료·세금 없음</p>
            </div>
            <Button className="primary-button" onClick={() => goChart()}>
              투자 시작하기 <span>→</span>
            </Button>
          </section>
        </div>

          <section className="card portfolio-card">
            <div className="portfolio-summary">
              <div><span className="label">내 주식</span><h2>순자산</h2></div>
              <div className="asset"><strong>5,134,000<small>캐시</small></strong><span className="up">▲ +6.5%</span></div>
            </div>
            <div className="holdings">
              <div className="holding-head"><span>보유 종목 3개</span><span>평가금액</span><span>수익률</span></div>
              {holdings.map(([name, amount, rate], index) => (
                <Button className="holding-row" onClick={() => goChart(stocks.find(stock => stock.name === name))} key={name}>
                  <span><i className={`mini-logo color-${index}`}>{name[0]}</i>{name}</span>
                  <strong>{amount}</strong>
                  <b className={rate.startsWith('+') ? 'up' : 'down'}>{rate.startsWith('+') ? '▲ ' : '▼ '}{rate}</b>
                </Button>
              ))}
            </div>
          </section>

          <TradeHistory />

          <section className="news-section">
            <div className="news-grid card">
              <div className="news-card-header">
                <div><h2>이번 턴 뉴스</h2><span className="turn-badge">5턴</span></div>
                <p>가격에 영향을 줄 소식을 확인해 보세요</p>
              </div>
              {news.map((item, index) => (
                <Button className="news-card" onClick={() => goNews(stocks.find(stock => stock.name === item.tags[0]) || stocks[0])} key={item.title}>
                  <div className="news-index">0{index + 1}</div>
                  <div className="news-row-content"><span className="turn-badge">{item.turn}턴</span><h3>{item.title}</h3><div className="tag-row">{item.sectors.map(sec => <span className="sector-tag" key={sec}>{sec}</span>)}{item.tags.map(tag => <span key={tag}>{tag}</span>)}</div></div>
                  <span className="arrow">↗</span>
                </Button>
              ))}
            </div>
          </section>

          <MarketCard onStock={goChart} onSector={goAll} onAll={() => goAll()} />
          <RankingCard />
      </div>
    </main>
  );
}

function StockHeader({ screen, setScreen, stock }) {
  return (
    <>
      <section className="stock-header">
        <div className="stock-identity">
          <span className="company-logo">{stock.initial}</span>
          <div><span className="breadcrumb">국내 주식 <b>·</b> {stock.sector}</span><h1>{stock.name}</h1><div className="stock-tags"><span>{stock.sector}</span><span>국내</span><span className="virtual">가상 기업</span></div></div>
        </div>
        <div className="quote">
          <span>이번 턴 가격</span>
          <strong>{stock.price}<small>캐시</small></strong>
          <b className={stock.up ? 'up' : 'down'}>{stock.up ? '▲' : '▼'} {stock.change}</b>
          <small>지난 턴 대비</small>
        </div>
      </section>
      <div className="stock-tabs">
        <Button className={screen === 'chart' ? 'active' : ''} onClick={() => setScreen('chart')}>차트 및 거래</Button>
        <Button className={screen === 'info' ? 'active' : ''} onClick={() => setScreen('info')}>종목정보</Button>
      </div>
    </>
  );
}

function PriceChart() {
  const rows = [
    ['1턴', '76,800 캐시', '+1.2%'],
    ['2턴', '78,200 캐시', '+1.8%'],
    ['3턴', '81,400 캐시', '+4.1%'],
    ['4턴', '82,200 캐시', '+1.0%'],
    ['5턴', '84,500 캐시', '+2.8%'],
  ];
  return (
    <section className="card chart-card">
      <div className="chart-head">
        <div><span className="eyebrow">TURN PRICE</span><h2>턴별 가격 흐름</h2></div>
        <div className="chart-summary"><span>시즌 수익률</span><strong className="up">▲ +11.2%</strong></div>
      </div>
      <div className="chart">
        <svg viewBox="0 0 840 340" role="img" aria-label="1턴부터 5턴까지 한빛반도체 가격 상승 차트">
          <defs>
            <linearGradient id="area" x1="0" x2="0" y1="0" y2="1">
              <stop offset="0%" stopColor="#9FE870" stopOpacity=".5" />
              <stop offset="100%" stopColor="#9FE870" stopOpacity="0" />
            </linearGradient>
            <filter id="glow"><feGaussianBlur stdDeviation="4" result="blur" /><feMerge><feMergeNode in="blur" /><feMergeNode in="SourceGraphic" /></feMerge></filter>
          </defs>
          {[55, 115, 175, 235, 295].map(y => <line key={y} x1="52" x2="790" y1={y} y2={y} className="gridline" />)}
          <path className="chart-area" d="M52 275 L236.5 239 L421 156 L605.5 135 L790 65 L790 295 L52 295Z" />
          <path className="chart-line" d="M52 275 L236.5 239 L421 156 L605.5 135 L790 65" />
          {[[52, 275], [236.5, 239], [421, 156], [605.5, 135]].map(([x, y]) => <circle key={x} cx={x} cy={y} r="4" className="chart-dot" />)}
          <line x1="790" x2="790" y1="60" y2="295" className="current-line" />
          <circle cx="790" cy="65" r="8" className="current-dot" filter="url(#glow)" />
          <g className="price-label"><rect x="682" y="22" width="108" height="30" rx="7" /><text x="736" y="42">84,500 캐시</text></g>
          {['1턴', '2턴', '3턴', '4턴', '5턴'].map((turn, i) => <text key={turn} x={52 + i * 184.5} y="326" className="axis-label" textAnchor={i === 0 ? 'start' : i === 4 ? 'end' : 'middle'}>{turn}</text>)}
        </svg>
      </div>
      <div className="price-table">
        {rows.map(([turn, price, change], index) => (
          <div className={index === 4 ? 'current' : ''} key={turn}><span>{turn}{index === 4 && <b>현재</b>}</span><strong>{price}</strong><small className="up">▲ {change}</small></div>
        ))}
      </div>
    </section>
  );
}

function OrderPanel({ hasHolding, stock, onOrder }) {
  const [orderType, setOrderType] = useState('buy');
  const [quantity, setQuantity] = useState(1);
  const [reason, setReason] = useState('');
  const ctx = useContext(MoneyCtx);
  const [limitMsg, setLimitMsg] = useState('');
  const blocked = ctx.season === 'closed' ? '오늘은 휴장일이에요. 다음 거래일에 다시 거래할 수 있어요' : (ctx.season === 'settle' || ctx.season === 'ended') ? '시즌이 끝났어요. 결과를 기다려 주세요' : '';
  useEffect(() => { setLimitMsg(''); }, [quantity, orderType]);
  const submit = () => {
    if (blocked || !reason) return;
    if (ctx.season === 'stale') { setLimitMsg('가격이 바뀌었어요. 현재 가격을 확인해 주세요'); return; }
    onOrder({ type: orderType, quantity, reason, price: 84500 });
    setQuantity(1); setReason('');
  };
  const buyReasons = ['나눠 담으려고', '뉴스를 보고', '가격이 싸서', '오를 것 같아서', '손실을 줄이려고'];
  const sellReasons = ['목표 수익에 도달해서', '손실을 줄이려고', '비중을 맞추려고', '돈이 필요해서', '뉴스를 보고'];
  const reasons = orderType === 'buy' ? buyReasons : sellReasons;
  const max = orderType === 'buy' ? 34 : 12;
  const changeOrderType = (type) => {
    setOrderType(type);
    setQuantity(1);
    setReason('');
  };
  const setPercent = (percent) => setQuantity(Math.max(1, Math.floor(max * percent)));
  return (
    <aside className="order-column">
      <section className="card order-card">
        <div className="order-tabs">
          <Button className={orderType === 'buy' ? 'buy active' : ''} onClick={() => changeOrderType('buy')}>매수</Button>
          <Button className={orderType === 'sell' ? 'sell active' : ''} onClick={() => changeOrderType('sell')}>매도</Button>
        </div>
        <div className="order-content">
          <div className={`holding-summary ${hasHolding ? '' : 'empty'}`}>
            {hasHolding ? (
              <>
                <span className="holding-badge">보유 중</span>
                <div className="holding-summary-values">
                  <div><small>보유 수량</small><strong>12주</strong></div>
                  <div><small>평균 단가</small><strong>79,000 캐시</strong></div>
                  <div><small>평가손익</small><em className="up">▲ +66,000 캐시 <span>(+7.0%)</span></em></div>
                </div>
              </>
            ) : <p>아직 보유하지 않은 종목이에요</p>}
          </div>
          <div className="fixed-price">
            <div><label>이번 턴 가격</label><span title="이번 턴에는 이 가격으로만 거래돼요">수정 불가</span></div>
            <strong>84,500 캐시</strong>
          </div>
          <p className="fixed-note">같은 턴 안에서는 모든 거래가 이 가격으로 체결돼요 · 수수료와 세금은 없어요</p>
          {blocked && <p className="order-block">{blocked}</p>}

          <div className="quantity-head"><label>수량</label><span>최대 {max}주</span></div>
          <div className="quantity-input">
            <Button onClick={() => setQuantity(Math.max(1, quantity - 1))} ariaLabel="수량 줄이기">−</Button>
            <span><strong>{quantity}</strong> 주</span>
            <Button onClick={() => { if (quantity >= max) setLimitMsg(orderType === 'buy' ? '현금이 부족해요' : '보유한 수량보다 많이 매도할 수 없어요'); else setQuantity(quantity + 1); }} ariaLabel="수량 늘리기">＋</Button>
          </div>
          <div className="quick-buttons">
            {[.1, .25, .5, 1].map((percent) => <Button key={percent} onClick={() => setPercent(percent)}>{percent === 1 ? '최대' : `${percent * 100}%`}</Button>)}
          </div>

          {limitMsg && <p className="order-error">{limitMsg}</p>}
          <div className="total"><span>{orderType === 'buy' ? '총 주문 금액' : '예상 판매 금액'}</span><strong>{(quantity * 84500).toLocaleString()}<small>캐시</small></strong></div>
          <div className="available"><span>{orderType === 'buy' ? '주문 가능 금액' : '매도 가능 수량'}</span><strong>{orderType === 'buy' ? '2,939,000 캐시' : '12주'}</strong></div>

          <div className="reason-title"><label>매매 이유를 골라주세요</label><span>필수</span></div>
          <div className="reason-chips">
            {reasons.map(item => <Button key={item} className={reason === item ? 'selected' : ''} onClick={() => setReason(item)}>{item}</Button>)}
          </div>
          <Button disabled={!reason || !!blocked} onClick={submit} className={`order-submit ${orderType}`}>
            {blocked ? '지금은 주문할 수 없어요' : reason ? `${orderType === 'buy' ? '매수' : '매도'} · ${quantity}주 ${(quantity * 84500).toLocaleString()} 캐시` : '매매 이유를 골라주세요'}
          </Button>
        </div>
      </section>
    </aside>
  );
}

function ChartScreen({ hasHolding, stock, onOrder }) {
  return (
    <div className="detail-grid">
      <PriceChart />
      <OrderPanel hasHolding={hasHolding} stock={stock} onOrder={onOrder} />
    </div>
  );
}

function Donut() {
  return (
    <div className="donut" aria-label="매출 구성: 메모리 반도체 70%, 파운드리 20%, 기타 10%">
      <div><strong>100%</strong><span>매출 구성</span></div>
    </div>
  );
}

function CompanyInfo() {
  const info = [
    { label: '분야', value: '반도체' },
    { label: '시장', value: '국내' },
    { label: '시가총액', value: '8조 4,500억 캐시', note: '이번 턴 가격 × 발행 주식 수' },
    { label: '발행 주식 수', value: '1억 주' },
    { label: '설립연도', value: '2008년' },
    { label: '대표 제품', value: 'HBM 메모리 칩' },
    { label: '직원 수', value: '2,430명' },
  ];
  return (
    <div className="company-info">
      <div className="company-intro">
        <div className="company-title"><h2>한빛반도체</h2><span>국내 · 반도체 · 가상 기업</span></div>
        <p>고성능 메모리와 AI 반도체 설계에 집중하는 기술 기업이에요. 데이터센터용 차세대 메모리 솔루션을 중심으로 국내외 시장에서 성장하고 있습니다.</p>
      </div>

      <section className="info-section">
        <div className="info-title"><span>01</span><h3>기본 정보</h3></div>
        <div className="facts">
          {info.map(({ label, value, note }) => <div key={label}><span>{label}</span><strong>{value}{note && <small>{note}</small>}</strong></div>)}
        </div>
      </section>

      <section className="info-section revenue-section">
        <div className="info-title"><span>02</span><h3>매출 구성</h3></div>
        <div className="revenue-content">
          <Donut />
          <div className="legend">
            <div><i className="memory" /><span>메모리 반도체</span><strong>70%</strong></div>
            <div><i className="foundry" /><span>파운드리</span><strong>20%</strong></div>
            <div><i className="other" /><span>기타</span><strong>10%</strong></div>
          </div>
        </div>
      </section>

      <section className="info-section business-section">
        <div className="info-title"><span>03</span><h3>주요 사업</h3></div>
        <div className="business-list">
          <div><span>01</span><p><strong>고성능 메모리</strong>AI 서버와 데이터센터에 사용되는 고대역폭 메모리를 개발해요.</p></div>
          <div><span>02</span><p><strong>반도체 위탁 생산</strong>중소 팹리스의 설계를 실제 반도체 제품으로 생산해요.</p></div>
          <div><span>03</span><p><strong>차세대 패키징</strong>여러 반도체를 하나로 연결해 성능과 전력 효율을 높여요.</p></div>
        </div>
      </section>
    </div>
  );
}

function CompanyNews() {
  const items = [
    { turn: '5턴', title: 'AI 서버용 신형 메모리 공급 계약 체결', summary: '한빛반도체가 대형 데이터센터 운영사와 차세대 메모리 공급 계약을 맺었어요. 시장은 생산량 확대 가능성에 주목하고 있습니다.', tag: '한빛반도체', impact: '다음 턴 ?', current: true },
    { turn: '4턴', title: '신규 생산 라인 시험 가동 시작', summary: '충청 생산단지의 신규 라인이 계획대로 시험 가동에 들어갔어요. 정식 양산은 다음 시즌 초로 예정되어 있습니다.', tag: '한빛반도체', impact: '다음 턴 ▲ +2.8%', up: true },
    { turn: '3턴', title: '원재료 가격 상승으로 업계 수익성 우려', summary: '반도체 생산에 필요한 일부 원재료 가격이 상승했어요. 단기적으로 제조 비용 부담이 커질 수 있다는 분석입니다.', tag: '반도체', impact: '다음 턴 ▲ +1.0%', up: true, unexpected: true },
    { turn: '2턴', title: '메모리 시장 수요 회복 전망', summary: '시장 조사 기관이 하반기 메모리 수요 전망을 상향했어요. AI 기기와 데이터센터 투자가 주요 배경입니다.', tag: '한빛반도체', impact: '다음 턴 ▲ +4.1%', up: true },
    { turn: '1턴', title: '시즌 개막, 반도체 업황 점검 보고서 발표', summary: '새 시즌이 시작되며 업계 연구 기관이 반도체 업황 점검 보고서를 냈어요. 재고가 줄고 있다는 내용이 담겼습니다.', tag: '반도체', impact: '다음 턴 ▲ +1.6%', up: true },
  ];
  return (
    <div className="company-news">
      <div className="news-list-head"><div><span className="eyebrow">COMPANY NEWS</span><h2>턴별 뉴스</h2></div><p>뉴스가 공개된 다음 턴의 주가 변화를 함께 확인해 보세요.<br />1턴부터 현재 턴까지의 뉴스를 볼 수 있어요.</p></div>
      <div className="news-now"><span className="now-dot" />현재 <b>{items[0].turn}</b> 진행 중 · 전체 {items.length}개 턴</div>
      <div className="timeline timeline-scroll">
        {items.map(item => (
          <article className={item.current ? 'current' : ''} key={item.title}>
            <div className="timeline-marker"><span>{item.turn}</span><i /></div>
            <div className="timeline-card">
              <div className="timeline-top"><span className="turn-badge">{item.turn}</span>{item.current && <span className="now-badge">현재 턴</span>}<div className="impact-wrap"><span className={`impact ${item.up === true ? 'up' : item.up === false ? 'down' : ''}`}>{item.impact}</span>{item.unexpected && <span className="unexpected-tag">예상과 다르게 움직였어요</span>}</div></div>
              <h3>{item.title}</h3>
              <p>{item.summary}</p>
              <span className="company-tag">{item.tag}</span><span className="company-tag sector">관련 분야 · 반도체</span>
            </div>
          </article>
        ))}
      </div>
    </div>
  );
}

function InfoScreen({ initialTab = 'overview' }) {
  const [infoTab, setInfoTab] = useState(initialTab);
  return (
    <div className="info-layout card">
      <aside className="info-menu">
        <span className="menu-label">기업 알아보기</span>
        <Button className={infoTab === 'overview' ? 'active' : ''} onClick={() => setInfoTab('overview')}><span aria-hidden="true"><svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><circle cx="12" cy="12" r="10"></circle><path d="M12 16v-4"></path><path d="M12 8h.01"></path></svg></span>주요 정보<i>›</i></Button>
        <Button className={infoTab === 'news' ? 'active' : ''} onClick={() => setInfoTab('news')}><span aria-hidden="true"><svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M4 22h16a2 2 0 0 0 2-2V4a2 2 0 0 0-2-2H8a2 2 0 0 0-2 2v16a2 2 0 0 1-2 2Zm0 0a2 2 0 0 1-2-2v-9c0-1.1.9-2 2-2h2"></path><path d="M18 14h-8"></path><path d="M15 18h-5"></path><path d="M10 6h8v4h-8V6Z"></path></svg></span>뉴스<i>›</i></Button>
        <div className="learn-tip"><span>LEARNING TIP</span><strong>기업을 알면<br />투자가 쉬워져요</strong><p>숫자만 보지 말고 어떤 일을 하는 회사인지 살펴보세요.</p></div>
      </aside>
      <div className="info-body">
        {infoTab === 'overview' ? <CompanyInfo /> : <CompanyNews />}
      </div>
    </div>
  );
}

function App({ admin }) {
  const isAdmin = admin === true || admin === 'true';
  const [screen, setScreen] = useState('home');
  const [selectedStock, setSelectedStock] = useState(stocks[0]);
  const [infoStartTab, setInfoStartTab] = useState('overview');
  const [sidebarOpen, setSidebarOpen] = useState(false);
  const wideLayout = false;
  const [level, setLevel] = useState(8);
  const [season, setSeason] = useState('active');
  useEffect(() => {
    if (isAdmin) return;
    const read = () => {
      try {
        const d = JSON.parse(localStorage.getItem('mb-inv-sync') || 'null');
        if (!d) return;
        const map = { '준비': 'none', '진행 중': 'active', '휴장': 'closed', '정산': d.published ? 'ended' : 'settle', '종료': d.published ? 'ended' : 'settle' };
        if (map[d.status]) setSeason(map[d.status]);
      } catch (e) {}
    };
    read();
    window.addEventListener('storage', read);
    return () => window.removeEventListener('storage', read);
  }, []);
  const [trades, setTrades] = useState(initialTrades);
  const [toast, setToast] = useState('');
  const [allSector, setAllSector] = useState('전체');
  const showToast = (m) => { setToast(m); window.setTimeout(() => setToast(''), 3000); };
  const goAll = (sector) => { setAllSector(typeof sector === 'string' ? sector : '전체'); setScreen('all'); };
  const placeOrder = (o) => {
    setTrades(list => [{ turn: '5턴', stock: selectedStock.name, type: o.type, qty: o.quantity, price: o.price, reason: o.reason }, ...list]);
    showToast(selectedStock.name + ' ' + o.quantity + '주를 ' + (o.type === 'buy' ? '샀어요' : '팔았어요'));
  };
  const joinSeason = () => { setSeason('active'); showToast('시즌 7에 참가했어요. 투자 가능 금액이 확정됐어요'); };
  const house = level <= 3 ? '고시원' : level <= 7 ? '원룸' : level <= 11 ? '투룸' : level <= 16 ? '아파트' : '내 집';
  const gate = level < 8 ? (
    <StateScreen title="모의투자는 Lv.8부터 열려요" desc={'지금은 Lv.' + level + ' ' + house + '예요. 강의를 완료하고 경험치를 모아 보세요. 주소를 직접 입력해도 들어올 수 없어요.'}><a className="primary-button" href="UserHome-vr2.dc.html">홈으로 가기</a></StateScreen>
  ) : season === 'none' ? (
    <StateScreen title="다음 시즌을 준비 중이에요" desc="진행 중인 시즌이 없어요. 시즌이 시작되면 이곳에서 참가할 수 있어요."><a className="primary-button" href="UserHome-vr2.dc.html">홈으로 가기</a></StateScreen>
  ) : season === 'joinClosed' ? (
    <StateScreen title="이번 시즌 참가는 마감됐어요" desc="시즌 참가는 첫 턴이 끝나기 전까지만 할 수 있어요. 다음 시즌에 참가해 주세요."><a className="primary-button" href="UserHome-vr2.dc.html">홈으로 가기</a></StateScreen>
  ) : season === 'join' ? <JoinScreen onJoin={joinSeason} /> : null;
  const goHome = () => setScreen('home');
  const goChart = (stock = selectedStock) => {
    setSelectedStock(stock);
    setScreen('chart');
  };
  const goNews = (stock) => {
    setSelectedStock(stock);
    setInfoStartTab('news');
    setScreen('info');
  };
  const changeDetailScreen = (nextScreen) => {
    if (nextScreen === 'info') setInfoStartTab('overview');
    setScreen(nextScreen);
  };
  return (
    <div className={`app ${sidebarOpen ? 'sidebar-open' : 'sidebar-closed'}`}>
      <BrandFixed sidebarOpen={sidebarOpen} toggleSidebar={() => setSidebarOpen(open => !open)} />
      <Sidebar level={level} open={sidebarOpen} onClose={() => setSidebarOpen(false)} onInvestmentHome={() => { goHome(); if (!wideLayout) setSidebarOpen(false); }} />
      <div data-mb-overlay="1" data-open={String(sidebarOpen)} onClick={() => setSidebarOpen(false)} aria-hidden="true" style={{ position: 'fixed', inset: 0, zIndex: 55, background: 'rgba(38,59,26,.38)', transition: 'opacity 180ms ease-out', opacity: sidebarOpen ? 1 : 0, pointerEvents: sidebarOpen ? 'auto' : 'none' }} />
      <MoneyCtx.Provider value={{ level, season, trades }}>
      <div className="app-content">
        <Header showSpacer={true} />
        {gate ? gate : screen === 'home' ? <Home goChart={goChart} goNews={goNews} goAll={goAll} /> : screen === 'all' ? (
          <main className="page detail-page">
            <div className="detail-toolbar">
              <Button className="back-button" onClick={goHome}>← 모의투자 홈</Button>
              <StockSearch compact onSelect={goChart} />
            </div>
            <AllStocks initialSector={allSector} onStock={goChart} />
          </main>
        ) : (
          <main className="page detail-page">
            <div className="detail-toolbar">
              <Button className="back-button" onClick={goHome}>← 모의투자 홈</Button>
              <StockSearch compact onSelect={goChart} />
            </div>
            <StockHeader screen={screen} setScreen={changeDetailScreen} stock={selectedStock} />
            {screen === 'chart' ? <ChartScreen hasHolding={holdings.some(([name]) => name === selectedStock.name)} stock={selectedStock} onOrder={placeOrder} /> : <InfoScreen initialTab={infoStartTab} />}
          </main>
        )}
        <Footer />
      </div>
      </MoneyCtx.Provider>
      {isAdmin && <div className="admin-badge">관리자 모드</div>}
      {isAdmin && <DemoPanel level={level} setLevel={setLevel} season={season} setSeason={setSeason} />}
      {toast && <div className="mb-toast" role="status">{toast}</div>}
    </div>
  );
}

module.exports = { App };
