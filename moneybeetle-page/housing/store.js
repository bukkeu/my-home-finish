/* MoneyBeetle 모의 청약 공유 데이터 저장소 (window.MBHousing)
 * ─────────────────────────────────────────────────────────────
 * DemoHousingSubscription · MyPage · Admin 세 페이지가 같은 데이터를 읽고 써요.
 * 지금은 백엔드가 없어 브라우저 localStorage에 목업 데이터를 저장해요.
 * 실제 연동 시: 아래 "API" 표시 함수들의 내부를 서버 호출로 바꾸고,
 * 관리자 함수는 서버에서 역할(role) 검증을 반드시 다시 수행해야 해요.
 */
(function () {
  if (window.MBHousing) return;
  var KEY = 'mb-housing-v1', ROLE_KEY = 'mb-preview-role';
  var TODAY = '2026-11-03';
  var TIMES = { post: '10:00', close: '18:00', announce: '14:00' };
  var STATUSES = ['모집 예정', '모집 중', '모집 마감', '발표 완료'];
  var POLICY = (window.MB_CONFIG && window.MB_CONFIG.housingDeposit) || { min: 20000, max: 500000, dailyRecognized: 250000 };

  // ───── 목업 시드 데이터 (실제 DB 연동 시 제거) ─────
  function seed() {
    var base = { img: null, computed: false };
    function n(o) { var r = {}; for (var k in base) r[k] = base[k]; for (var j in o) r[j] = o[j]; return r; }
    return {
      user: { id: 'me', nickname: '민지', birth: '1999-04-12', region: '서울', cash: 1250000 },
      account: {
        balance: 1840000, total: 1840000, recognized: 1590000, count: 14, last: '2026-11-02',
        history: [
          { date: '2026-11-02', amount: 100000, rec: 100000, count: 14 },
          { date: '2026-11-01', amount: 300000, rec: 250000, count: 13 },
          { date: '2026-10-31', amount: 50000, rec: 50000, count: 12 }
        ]
      },
      notices: [
        n({ id: 'n1', name: '그린숲 행복주택', type: 'public', region: '경기', district: '고양시 덕양구', area: 59, price: 320000000, units: 120, post: '2026-10-28', close: '2026-11-10', announce: '2026-11-17', status: '모집 중', minCount: 6, minAmount: 600000, method: '가점제 100%', cutline: 46, competitors: 1860 }),
        n({ id: 'n2', name: '리버뷰 마포 더퍼스트', type: 'private', region: '서울', district: '마포구 상암동', area: 84, price: 980000000, units: 312, post: '2026-10-30', close: '2026-11-07', announce: '2026-11-14', status: '모집 중', minCount: 3, minAmount: 300000, method: '가점제 40% · 추첨제 60%', cutline: 52, competitors: 9840 }),
        n({ id: 'n6', name: '광교 호수 리젠시', type: 'private', region: '경기', district: '수원시 영통구', area: 102, price: 1120000000, units: 96, post: '2026-11-03', close: '2026-11-13', announce: '2026-11-20', status: '모집 중', minCount: 3, minAmount: 300000, method: '가점제 30% · 추첨제 70%', cutline: 58, competitors: 4300 }),
        n({ id: 'n3', name: '한빛 공공분양 3단지', type: 'public', region: '세종', district: '나성동', area: 74, price: 290000000, units: 200, post: '2026-11-12', close: '2026-11-24', announce: '2026-12-01', status: '모집 예정', minCount: 12, minAmount: 1200000, method: '가점제 100%', cutline: 50, competitors: 2400 }),
        n({ id: 'n4', name: '해운대 센트럴 파크뷰', type: 'private', region: '부산', district: '해운대구 우동', area: 84, price: 640000000, units: 450, post: '2026-10-08', close: '2026-10-20', announce: '2026-11-05', status: '모집 마감', minCount: 3, minAmount: 300000, method: '가점제 40% · 추첨제 60%', cutline: 41, competitors: 5120 }),
        n({ id: 'n7', name: '푸른들 행복주택 2단지', type: 'public', region: '서울', district: '노원구 상계동', area: 49, price: 210000000, units: 80, post: '2026-09-08', close: '2026-09-19', announce: '2026-09-29', status: '발표 완료', minCount: 6, minAmount: 600000, method: '가점제 100%', cutline: 38, competitors: 960, computed: true, computedAt: '2026-09-27', announcedAt: '2026-09-29' }),
        n({ id: 'n5', name: '새봄 국민주택', type: 'public', region: '인천', district: '연수구 송도동', area: 59, price: 250000000, units: 150, post: '2026-09-21', close: '2026-10-02', announce: '2026-10-12', status: '발표 완료', minCount: 6, minAmount: 600000, method: '가점제 100%', cutline: 44, competitors: 1320, computed: true, computedAt: '2026-10-10', announcedAt: '2026-10-12' })
      ],
      apps: [
        { id: 'a6', userId: 'me', nickname: '민지', region: '서울', noticeId: 'n7', receipt: 'MB-260910-0207', at: '2026-09-10 10:05', count: 12, recognized: 1200000, level: 12, marital: '미혼', newlywed: '해당 없음', score: 45, max: 85,
          breakdown: [{ label: '무주택 여부', pts: 20, max: 20 }, { label: '납입 회차', pts: 10, max: 20 }, { label: '인정금액', pts: 8, max: 20 }, { label: '해당 지역 거주', pts: 10, max: 10 }, { label: '부양가족 수', pts: 7, max: 15 }],
          result: { kind: 'win', method: '가점제' } },
        { id: 'a1', userId: 'me', nickname: '민지', region: '서울', noticeId: 'n5', receipt: 'MB-261001-0418', at: '2026-10-01 13:42', count: 11, recognized: 1100000, level: 13, marital: '미혼', newlywed: '해당 없음', score: 41, max: 85,
          breakdown: [{ label: '무주택 여부', pts: 20, max: 20 }, { label: '납입 회차', pts: 9, max: 20 }, { label: '인정금액', pts: 7, max: 20 }, { label: '해당 지역 거주', pts: 0, max: 10 }, { label: '부양가족 수', pts: 5, max: 15 }],
          result: { kind: 'reserve', rank: 10 } },
        { id: 'a2', userId: 'me', nickname: '민지', region: '서울', noticeId: 'n4', receipt: 'MB-261015-0932', at: '2026-10-15 09:18', count: 12, recognized: 1240000, level: 14, marital: '미혼', newlywed: '해당 없음', score: 43, max: 68,
          breakdown: [{ label: '무주택 여부', pts: 20, max: 20 }, { label: '부양가족 수', pts: 7, max: 21 }, { label: '통장 납입 회차', pts: 10, max: 17 }, { label: '나이', pts: 6, max: 10 }],
          result: null },
        { id: 'a3', userId: 'u2', nickname: '복리왕', region: '부산', noticeId: 'n4', receipt: 'MB-261012-2210', at: '2026-10-12 21:05', count: 9, recognized: 900000, level: 15, marital: '미혼', newlywed: '해당 없음', score: 36, max: 68, breakdown: [{ label: '무주택 여부', pts: 20, max: 20 }, { label: '부양가족 수', pts: 0, max: 21 }, { label: '통장 납입 회차', pts: 10, max: 17 }, { label: '나이', pts: 6, max: 10 }], result: null },
        { id: 'a4', userId: 'u9', nickname: '내집마련러', region: '부산', noticeId: 'n4', receipt: 'MB-261018-4471', at: '2026-10-18 11:30', count: 20, recognized: 2400000, level: 18, marital: '기혼', newlywed: '해당', score: 52, max: 68, breakdown: [{ label: '무주택 여부', pts: 20, max: 20 }, { label: '부양가족 수', pts: 14, max: 21 }, { label: '통장 납입 회차', pts: 12, max: 17 }, { label: '나이', pts: 6, max: 10 }], result: null },
        { id: 'a5', userId: 'u2', nickname: '복리왕', region: '부산', noticeId: 'n2', receipt: 'MB-261101-1187', at: '2026-11-01 08:52', count: 10, recognized: 1000000, level: 15, marital: '미혼', newlywed: '해당 없음', score: 36, max: 68, breakdown: [], result: null }
      ]
    };
  }

  function read() { try { var raw = localStorage.getItem(KEY); if (raw) return JSON.parse(raw); } catch (e) {} return null; }
  var db = read() || seed();
  (function () { if (db.apps && !db.apps.some(function (a) { return a.id === 'a6'; })) { var s = seed(); db.notices = db.notices.concat(s.notices.filter(function (m) { return m.id === 'n7'; })); db.apps = db.apps.concat(s.apps.filter(function (a) { return a.id === 'a6'; })); try { localStorage.setItem(KEY, JSON.stringify(db)); } catch (e) {} } })();
  function write(next) {
    db = next;
    try { localStorage.setItem(KEY, JSON.stringify(next)); } catch (e) {}
    window.dispatchEvent(new CustomEvent('mb-housing-change'));
  }
  if (!read()) { try { localStorage.setItem(KEY, JSON.stringify(db)); } catch (e) {} }
  window.addEventListener('storage', function (e) {
    if (e.key === KEY) { db = read() || seed(); window.dispatchEvent(new CustomEvent('mb-housing-change')); }
  });
  function clone(o) { return JSON.parse(JSON.stringify(o)); }

  // ───── 정책 · 계산 규칙 ─────
  function virtualMarital(level) {
    return level >= 17
      ? { marital: '기혼', newlywed: '해당', note: '현재 레벨에 따라 신혼부부로 설정되었어요.' }
      : { marital: '미혼', newlywed: '해당 없음', note: '현재 레벨에 따라 미혼으로 설정되었어요.' };
  }
  // 임시 시뮬레이션 가점 (기준 미확정 · 이 표만 바꾸면 전 페이지에 반영)
  var RULES = {
    public: [
      { label: '무주택 여부', max: 20, calc: function (c) { return c.homeless ? 20 : 0; } },
      { label: '납입 회차', max: 20, calc: function (c) { return Math.round(Math.min(c.count / 24, 1) * 20); } },
      { label: '인정금액', max: 20, calc: function (c) { return Math.round(Math.min(c.recognized / 6000000, 1) * 20); } },
      { label: '해당 지역 거주', max: 10, calc: function (c) { return c.regionMatch ? 10 : 0; } },
      { label: '부양가족 수', max: 15, calc: function (c) { return Math.min(c.dep, 3) * 5; } },
      { label: '혼인·신혼부부 우대', max: 0, pending: true, calc: function () { return 0; } }
    ],
    private: [
      { label: '무주택 여부', max: 20, calc: function (c) { return c.homeless ? 20 : 0; } },
      { label: '부양가족 수', max: 21, calc: function (c) { return Math.min(c.dep, 3) * 7; } },
      { label: '통장 납입 회차', max: 17, calc: function (c) { return Math.round(Math.min(c.count / 24, 1) * 17); } },
      { label: '나이', max: 10, calc: function (c) { return c.age >= 30 ? 10 : c.age >= 25 ? 6 : 2; } },
      { label: '혼인·신혼부부 우대', max: 0, pending: true, calc: function () { return 0; } }
    ]
  };
  // 가상 경쟁자 기반 1인 체험형 판정 (최종 알고리즘 확정 전 임시) — 회원끼리 비교하지 않음
  function judge(app, n) {
    if (app.score >= n.cutline) return { kind: 'win', method: '가점제' };
    if (app.score >= n.cutline - 6) return { kind: 'reserve', rank: (n.cutline - app.score) * 3 + 1 };
    return { kind: 'lose' };
  }
  function statusFromDates(post, close) { return post > TODAY ? '모집 예정' : close < TODAY ? '모집 마감' : '모집 중'; }

  // ───── 청약통장 API ─────
  function depositPreview(amt) {
    var a = db.account, u = db.user, error = '';
    if (a.last === TODAY) error = '오늘은 이미 납입했어요. 내일 다시 납입할 수 있어요.';
    else if (amt && amt < POLICY.min) error = '최소 20,000 캐시부터 납입할 수 있어요.';
    else if (amt > POLICY.max) error = '한 번에 최대 500,000 캐시까지 납입할 수 있어요.';
    else if (amt > u.cash) error = '보유 캐시가 부족해요. (보유 ' + u.cash.toLocaleString('ko-KR') + ' 캐시)';
    var rec = Math.min(amt, POLICY.dailyRecognized);
    return { error: error, rec: rec, excess: Math.max(0, amt - POLICY.dailyRecognized), after: a.balance + amt, todayPaid: a.last === TODAY };
  }
  function deposit(amt) {
    var p = depositPreview(amt);
    if (p.error || !amt) return { ok: false, error: p.error || '납입 금액을 입력해 주세요.' };
    var next = clone(db), a = next.account;
    next.user.cash -= amt; a.balance += amt; a.total += amt; a.recognized += p.rec; a.count += 1; a.last = TODAY;
    a.history.unshift({ date: TODAY, amount: amt, rec: p.rec, count: a.count });
    write(next);
    return { ok: true, rec: p.rec, excess: p.excess, count: a.count };
  }

  // ───── 청약 신청 API ─────
  function apply(app) {
    if (db.apps.some(function (x) { return x.userId === app.userId && x.noticeId === app.noticeId; })) return { ok: false, error: '이미 신청한 공고예요. 공고당 1회만 신청할 수 있어요.' };
    var n = db.notices.find(function (x) { return x.id === app.noticeId; });
    if (!n || n.status !== '모집 중') return { ok: false, error: '지금은 신청할 수 없는 공고예요.' };
    var snap = clone(app);
    if (snap.count == null && snap.userId === db.user.id) { snap.count = db.account.count; snap.recognized = db.account.recognized; }
    var next = clone(db); next.apps.unshift(snap); write(next);
    return { ok: true };
  }

  // ───── 관리자 API (미리보기 역할 확인 · 실제 서비스는 서버 검증 필수) ─────
  function getPreviewRole() { try { return localStorage.getItem(ROLE_KEY) || 'user'; } catch (e) { return 'user'; } }
  function setPreviewRole(r) { try { localStorage.setItem(ROLE_KEY, r); } catch (e) {} window.dispatchEvent(new CustomEvent('mb-housing-change')); }
  function guard() { if (getPreviewRole() !== 'admin') throw new Error('관리자 권한이 필요해요.'); }
  function saveNotice(data, id) {
    guard();
    var next = clone(db);
    if (id) {
      var cur = db.notices.find(function (m) { return m.id === id; });
      var has = db.apps.some(function (a) { return a.noticeId === id; });
      if (cur && cur.status === '발표 완료') throw new Error('발표 완료된 공고는 수정할 수 없어요.');
      if (cur && has && (data.type !== cur.type || Number(data.units) !== Number(cur.units))) throw new Error('신청자가 있는 공고는 청약 유형과 공급 세대 수를 바꿀 수 없어요.');
      next.notices = next.notices.map(function (m) { if (m.id !== id) return m; var r = clone(m); for (var k in data) r[k] = data[k]; return r; });
    } else {
      var isPub = data.type === 'public';
      var n = { id: 'n' + Date.now(), computed: false, minCount: isPub ? 6 : 3, minAmount: isPub ? 600000 : 300000,
        method: isPub ? '가점제 100%' : '가점제 40% · 추첨제 60%', cutline: 40 + Math.floor(Math.random() * 16), competitors: data.units * (5 + Math.floor(Math.random() * 20)) };
      for (var k2 in data) n[k2] = data[k2];
      if (!n.status) n.status = statusFromDates(n.post, n.close);
      next.notices.unshift(n);
    }
    write(next);
  }
  function setStatus(id, status, force) {
    guard();
    var cur = db.notices.find(function (m) { return m.id === id; });
    if (!force && cur) {
      if (cur.status === '발표 완료') throw new Error('발표 완료된 공고는 상태를 바꿀 수 없어요.');
      if (status === '발표 완료') throw new Error("발표 완료는 '당첨 결과 관리'에서 결과를 발표해야 바뀌어요.");
      if (cur.computed && status !== '모집 마감') throw new Error('결과를 산정한 공고는 모집 마감 상태로 유지돼요.');
    }
    var next = clone(db); next.notices.forEach(function (m) { if (m.id === id) m.status = status; }); write(next); }
  function computeResults(id) {
    guard(); var next = clone(db), n = next.notices.find(function (m) { return m.id === id; });
    if (!n || n.status !== '모집 마감') throw new Error('청약 모집이 마감된 후 당첨 결과를 산정할 수 있어요.');
    if (n.computed) throw new Error('이미 산정한 공고예요. 재산정은 제한돼요.');
    next.apps.forEach(function (a) { if (a.noticeId === id) a.result = judge(a, n); });
    n.computed = true; n.computedAt = TODAY; write(next);
  }
  function publishResults(id) {
    guard(); var n = db.notices.find(function (m) { return m.id === id; });
    if (!n || !n.computed) throw new Error('결과를 먼저 산정해 주세요.');
    if (n.status === '발표 완료') throw new Error('이미 발표한 공고예요.');
    var next = clone(db); next.notices.forEach(function (m) { if (m.id === id) { m.status = '발표 완료'; m.announcedAt = TODAY; } }); write(next);
  }
  function deleteNotice(id) {
    guard();
    if (db.apps.some(function (a) { return a.noticeId === id; })) throw new Error('신청자가 있는 공고는 삭제할 수 없어요. 비공개로 전환해 주세요.');
    var next = clone(db); next.notices = next.notices.filter(function (m) { return m.id !== id; }); write(next);
  }
  function setHidden(id, hidden) {
    guard(); var next = clone(db); next.notices.forEach(function (m) { if (m.id === id) m.hidden = !!hidden; }); write(next);
  }

  function subscribe(fn) { window.addEventListener('mb-housing-change', fn); return function () { window.removeEventListener('mb-housing-change', fn); }; }
  function reset() { write(seed()); }

  window.MBHousing = {
    TODAY: TODAY, TIMES: TIMES, STATUSES: STATUSES, POLICY: POLICY, RULES: RULES,
    get: function () { return db; }, isMockData: true,
    virtualMarital: virtualMarital, judge: judge, statusFromDates: statusFromDates,
    depositPreview: depositPreview, deposit: deposit, apply: apply,
    getPreviewRole: getPreviewRole, setPreviewRole: setPreviewRole,
    saveNotice: saveNotice, setStatus: setStatus, deleteNotice: deleteNotice, setHidden: setHidden, computeResults: computeResults, publishResults: publishResults,
    subscribe: subscribe, reset: reset
  };
  window.dispatchEvent(new CustomEvent('mb-housing-change'));
})();
