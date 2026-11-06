/* Money Beetle 통합 검색 — 색인(강의·Q&A·게시판) + 상단 검색창 자동완성. 실제 검색 API로 교체할 때는 index()만 바꾸면 됩니다. */
(function () {
  if (window.MBSearch) return;
  var CAT = { money: '자본 증액', estate: '부동산' };
  // 강의 목록 데이터 (UserLectureCategory.dc.html 의 catData 와 동일하게 유지)
  var LECT = [
    { cat: 'money', subs: [
      ['월급 쪼개기', '월급이 들어오면 먼저 나누는 습관', [['통장 3개로 월급 나누기 (월급·생활비·비상금)', '한지수'], ['비상금은 얼마나 모아야 할까', '한지수'], ['새는 고정비 줄이기', '오민재']]],
      ['예금·적금', '가장 안전하게 돈을 모으는 방법', [['예금과 적금, 뭐가 다를까', '박서연'], ['이자는 어떻게 계산될까', '박서연'], ['중도해지하면 생기는 일', '오민재'], ['청년 정책 적금 알아보기', '박서연']]],
      ['국내 주식', '주식이 무엇인지부터 차근차근', [['주식은 회사의 조각이다', '이도현'], ['증권 계좌와 주문 방법', '이도현']]],
      ['미국 주식', '환율까지 함께 보는 해외 투자', [['미국 주식을 사는 방법', '정하린'], ['환율과 해외 주식 세금', '정하린']]],
      ['ETF·분산 투자', '한 바구니에 담지 않는 투자', [['ETF와 펀드, 채권', '이도현'], ['분산 투자로 위험 줄이기', '정하린'], ['코인과 위험', '오민재']]]
    ] },
    { cat: 'estate', subs: [
      ['청약', '내 집 마련의 첫 단추', [['청약통장 기초와 가점 계산', '박서연']]],
      ['전·월세', '계약 전에 꼭 확인할 것들', []],
      ['대출', '내게 맞는 대출 고르기', []]
    ] }
  ];
  var SEED_QNA = [
    { title: '청약통장은 언제부터 납입할 수 있나요?', body: '레벨 제한이 있는지 궁금해요.', date: '2026.11.01', lecture: { cat: 'estate', sub: 1, title: '청약통장 기초와 가점 계산' } },
    { title: '부양가족 수에 부모님도 포함되나요?', body: '함께 살고 있지 않으면 어떻게 되나요?', date: '2026.11.02', lecture: { cat: 'estate', sub: 1, title: '청약통장 기초와 가점 계산' } }
  ];
  var SEED_BOARD = [
    { id: 3, cat: '모의 청약', private: true, title: '청약통장 인정금액이 잔액보다 적게 나와요', body: '40만 캐시를 납입했는데 인정금액은 25만 캐시만 올라갔어요.\n오류인가요?', date: '2026.11.03' },
    { id: 2, cat: '모의 투자', title: '시즌 중간에 참가할 수 있나요?', body: '시즌 7이 이미 시작됐는데 지금 참가해도 되는지 궁금해요.', date: '2026.11.01' },
    { id: 1, cat: '계정·캐시', title: '출석 캐시가 들어오지 않았어요', body: '10월 30일에 출석했는데 캐시가 지급되지 않았어요.', date: '2026.10.30' }
  ];
  function rd(k) { try { return JSON.parse(localStorage.getItem(k) || 'null'); } catch (e) { return null; } }
  function lectHref(cat, sub, title) { return 'UserLectureDetail.dc.html?cat=' + cat + '&sub=' + sub + '&title=' + encodeURIComponent(title); }
  function index() {
    var lecture = [], qna = [], board = [];
    LECT.forEach(function (c) { c.subs.forEach(function (s, si) { s[2].forEach(function (l) {
      lecture.push({ type: 'lecture', title: l[0], desc: s[1], meta: [CAT[c.cat], s[0], l[1] + ' 강사'], extra: CAT[c.cat] + ' ' + s[0] + ' ' + l[1], href: lectHref(c.cat, si + 1, l[0]) });
    }); }); });
    var seen = {}, mine = rd('mb-my-qna'), list = Array.isArray(mine) ? mine.concat(SEED_QNA) : SEED_QNA;
    list.forEach(function (q) {
      if (!q || !q.title) return;
      var key = q.title + '|' + (q.lecture ? q.lecture.title : ''); if (seen[key]) return; seen[key] = 1;
      var lc = q.lecture && CAT[q.lecture.cat] && q.lecture.title;
      qna.push({ type: 'qna', title: q.title, desc: q.body || '', meta: [lc ? '강의 · ' + q.lecture.title : '강의 연결 정보 없음', q.date || ''], extra: '', href: lc ? lectHref(q.lecture.cat, q.lecture.sub, q.lecture.title) : 'UserMyPage.dc.html#tab-qna' });
    });
    var inq = rd('mb-inquiry-v1'); if (!Array.isArray(inq)) inq = SEED_BOARD;
    inq.forEach(function (p) {
      if (!p || p.hidden || !p.title) return;
      board.push({ type: 'board', title: p.title, desc: p.body || '', meta: [p.private ? '비공개' : '공개', p.cat || '', p.date || ''].filter(Boolean), extra: p.cat || '', href: 'UserInquiry.dc.html?open=' + p.id });
    });
    return { lecture: lecture, qna: qna, board: board };
  }
  function toks(q) { return String(q || '').toLowerCase().split(/\s+/).filter(Boolean); }
  function search(q) {
    var t = toks(q), idx = index(), out = { lecture: [], qna: [], board: [] };
    if (!t.length) return out;
    ['lecture', 'qna', 'board'].forEach(function (k) {
      out[k] = idx[k].filter(function (it) { var h = (it.title + ' ' + it.desc + ' ' + it.extra).toLowerCase(); return t.every(function (w) { return h.indexOf(w) >= 0; }); })
        .sort(function (a, b) { var A = a.title.toLowerCase(), B = b.title.toLowerCase(); return (t.every(function (w) { return A.indexOf(w) >= 0; }) ? 0 : 1) - (t.every(function (w) { return B.indexOf(w) >= 0; }) ? 0 : 1); });
    });
    return out;
  }
  function parts(text, q) {
    text = String(text || ''); var t = toks(q).map(function (w) { return w.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'); });
    if (!t.length) return [{ t: text, m: false }];
    var re = new RegExp('(' + t.join('|') + ')', 'gi'), r = [], last = 0, m;
    while ((m = re.exec(text))) { if (m.index > last) r.push({ t: text.slice(last, m.index), m: false }); r.push({ t: m[0], m: true }); last = m.index + m[0].length; if (m[0].length === 0) re.lastIndex++; }
    if (last < text.length) r.push({ t: text.slice(last), m: false });
    return r;
  }
  var TYPES = { lecture: { label: '강의', bg: '#9FE870' }, qna: { label: 'Q&A', bg: '#DCEFF0' }, board: { label: '게시판', bg: '#F2EAD9' } };
  window.MBSearch = { index: index, search: search, parts: parts, types: TYPES, lectHref: lectHref };

  function esc(s) { return String(s).replace(/[&<>"]/g, function (c) { return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]; }); }
  function hl(text, q) { return parts(text, q).map(function (p) { return p.m ? '<mark style="background:#9FE870;color:#263B1A;border-radius:3px;padding:0 1px">' + esc(p.t) + '</mark>' : esc(p.t); }).join(''); }
  function attach(inp) {
    if (inp.__mbs) return; inp.__mbs = 1;
    var lab = inp.closest('label') || inp.parentElement; if (getComputedStyle(lab).position === 'static') lab.style.position = 'relative';
    inp.setAttribute('autocomplete', 'off'); inp.setAttribute('placeholder', '강의, Q&A, 게시판 검색');
    var box = document.createElement('div'), act = -1;
    box.setAttribute('role', 'listbox');
    box.style.cssText = 'display:none;position:absolute;top:calc(100% + 8px);left:0;right:0;background:#fff;border:1.5px solid #D5E1CC;border-radius:16px;box-shadow:0 12px 32px rgba(38,59,26,.14);z-index:80;max-height:min(70vh,460px);overflow:auto;padding:6px 0;text-align:left';
    lab.appendChild(box);
    function links() { return [].slice.call(box.querySelectorAll('a[data-mbs]')); }
    function mark() { links().forEach(function (a, i) { a.style.background = i === act ? '#EEF5E7' : 'transparent'; }); }
    function render() {
      var q = inp.value.trim(); act = -1;
      if (!q) { box.style.display = 'none'; return; }
      var r = search(q), html = '', n = 0;
      ['lecture', 'qna', 'board'].forEach(function (k) {
        if (!r[k].length) return; n += r[k].length;
        html += '<div style="display:flex;align-items:center;gap:8px;padding:10px 16px 4px;font-size:12px;font-weight:700;color:#687161"><span style="background:' + TYPES[k].bg + ';color:#263B1A;border-radius:999px;padding:2px 10px;font-size:12px">' + TYPES[k].label + '</span>' + r[k].length + '건</div>';
        r[k].slice(0, 3).forEach(function (it) {
          html += '<a data-mbs="1" role="option" href="' + esc(it.href) + '" style="display:block;padding:8px 16px;text-decoration:none;color:#263B1A"><div style="font-size:14px;font-weight:600;overflow:hidden;text-overflow:ellipsis;white-space:nowrap">' + hl(it.title, q) + '</div><div style="font-size:12px;color:#687161;overflow:hidden;text-overflow:ellipsis;white-space:nowrap">' + esc(it.meta.join(' · ')) + '</div></a>';
        });
      });
      if (!n) html = '<div style="padding:16px;font-size:14px;color:#687161">‘' + esc(q) + '’에 맞는 결과가 없어요</div>';
      else html += '<a data-mbs="1" href="UserSearch.dc.html?q=' + encodeURIComponent(q) + '" style="display:block;padding:12px 16px;border-top:1px solid #D5E1CC;margin-top:6px;text-decoration:none;font-size:13px;font-weight:700;color:#263B1A">‘' + esc(q) + '’ 전체 검색 결과 보기 →</a>';
      box.innerHTML = html; box.style.display = 'block';
    }
    inp.addEventListener('input', render);
    inp.addEventListener('focus', render);
    inp.addEventListener('blur', function () { setTimeout(function () { box.style.display = 'none'; }, 150); });
    box.addEventListener('mousedown', function (e) { e.preventDefault(); });
    inp.addEventListener('keydown', function (e) {
      var L = links();
      if (e.key === 'Escape') { box.style.display = 'none'; }
      else if (e.key === 'ArrowDown' && L.length) { e.preventDefault(); act = (act + 1) % L.length; mark(); }
      else if (e.key === 'ArrowUp' && L.length) { e.preventDefault(); act = (act - 1 + L.length) % L.length; mark(); }
      else if (e.key === 'Enter' && act >= 0 && L[act]) { e.preventDefault(); location.href = L[act].getAttribute('href'); }
    });
  }
  function scan() {
    [].forEach.call(document.querySelectorAll('input[name=q][type=search]'), function (i) { if (i.closest('header')) attach(i); });
  }
  var raf = 0, mo = new MutationObserver(function () { if (!raf) raf = requestAnimationFrame(function () { raf = 0; scan(); }); });
  function start() { scan(); mo.observe(document.documentElement, { childList: true, subtree: true }); }
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', start); else start();
})();
