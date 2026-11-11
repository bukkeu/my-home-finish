/* comment.
*   담당 : PM 이재성 (#278, 검색창 자동완성)
*   공통 헤더의 검색창(.mb-search input[name=q])에 추천 검색어를 붙인다. 모든 학습자 화면에서 header.html 의 scriptsFragment 가 불러온다.
*   모양·동작은 시안(moneybeetle-page/mb-search.js)을 따른다.
*   - 요청은 기존 API 28 (GET /api/v1/search?keyword=) 을 그대로 쓴다. 시안의 강의·Q&A·게시판 대신 API 가 주는 강의·종목·경제 용어를 보여 준다.
*   - 종류마다 색 배지 + "N건", 앞의 3개를 제목(일치 글자 연두색 강조) + 아랫줄 정보로 보여 주고, 맨 아래에 "전체 검색 결과 보기" 링크를 둔다.
*   - 항목을 고르면 그 항목으로 바로 간다: 강의 상세(courseId), 모의투자 종목(stockId). 경제 용어는 상세 화면이 없어 그 용어로 통합 검색한다.
*   - 입력하거나 검색창에 다시 들어오면 연다. 입력을 멈추고 SUGGEST_DELAY ms 뒤에 한 번만 요청하고,
*     늦게 온 응답이 최신 결과를 덮지 않게 요청 번호가 마지막인 것만 그린다.
*   - 키보드: ↑↓ 로 고르고 Enter 로 이동, Esc 로 닫는다. 아무것도 고르지 않고 Enter 를 누르면 입력한 글자로 그대로 검색한다.
*   - 접근성: 입력칸이 combobox, 목록이 listbox, 각 항목이 option 이다 (aria-activedescendant 로 고른 항목을 알린다).
*   서버가 준 문구는 모두 textContent 로 넣는다 (HTML 로 해석하지 않는다).
*  */
(function () {
  'use strict';
  var input = document.querySelector('.mb-search input[name="q"]');
  if (!input || !window.MB || !window.MB.api) return;
  var form = input.form;
  var anchor = input.closest('label') || form;   // 목록을 검색창 폭에 맞춰 붙인다 (시안: left 0, right 0)

  var SUGGEST_DELAY = 200;     // 입력을 멈춘 뒤 기다리는 시간(ms)
  var PER_GROUP = 3;           // 종류마다 보여 줄 최대 개수 (시안과 같다)
  var SEARCH_PATH = form.getAttribute('action') || '/user/search';
  function searchHref(q) { return SEARCH_PATH + '?q=' + encodeURIComponent(q); }

  var GROUPS = [
    { key: 'courses', label: '강의', badge: 'is-course',
      title: function (i) { return i.title; },
      meta: function (i) { return String(i.categoryName || '').split('›').map(function (s) { return s.trim(); }).filter(Boolean).join(' · '); },
      href: function (i) { return i.courseId != null ? '/user/lecture-detail?courseId=' + encodeURIComponent(i.courseId) : null; } },
    { key: 'stocks', label: '종목', badge: 'is-stock',
      title: function (i) { return i.name; },
      meta: function (i) { return ['모의투자', i.sectorName].filter(Boolean).join(' · '); },
      href: function (i) { return i.stockId != null ? '/user/demo-trading?stockId=' + encodeURIComponent(i.stockId) : null; } },
    { key: 'terms', label: '경제 용어', badge: 'is-term',
      title: function (i) { return i.term; },
      meta: function (i) { return i.description || ''; },
      href: function (i) { return i.term ? searchHref(i.term) : null; } }
  ];

  var listId = 'mb-suggest';
  var box = document.createElement('div');
  box.className = 'mb-suggest';
  box.id = listId;
  box.hidden = true;
  box.setAttribute('role', 'listbox');
  box.setAttribute('aria-label', '추천 검색어');
  anchor.appendChild(box);

  input.setAttribute('role', 'combobox');
  input.setAttribute('aria-autocomplete', 'list');
  input.setAttribute('aria-expanded', 'false');
  input.setAttribute('aria-controls', listId);

  var timer = null;
  var requestNo = 0;
  var options = [];            // 지금 보이는 항목 (option 링크)
  var active = -1;             // 키보드로 고른 항목의 번호
  var last = { q: null, data: null };   // 마지막으로 그린 검색어와 응답 (검색창에 다시 들어오면 다시 요청하지 않고 보여 준다)

  function el(tag, cls, text) {
    var n = document.createElement(tag);
    if (cls) n.className = cls;
    if (text != null) n.textContent = text;
    return n;
  }

  /** 검색어(띄어쓰기로 나눈 낱말)와 같은 글자를 시안처럼 <mark> 로 칠한다. 대소문자 구분 없음 */
  function highlight(parent, text, q) {
    text = String(text || '');
    var words = String(q).toLowerCase().split(/\s+/).filter(Boolean);
    var lower = text.toLowerCase();
    var from = 0;
    while (from < text.length) {
      var at = -1, len = 0;
      words.forEach(function (w) {
        var i = lower.indexOf(w, from);
        if (i >= 0 && (at < 0 || i < at || (i === at && w.length > len))) { at = i; len = w.length; }
      });
      if (at < 0) break;
      if (at > from) parent.appendChild(document.createTextNode(text.slice(from, at)));
      parent.appendChild(el('mark', null, text.slice(at, at + len)));
      from = at + len;
    }
    if (from < text.length) parent.appendChild(document.createTextNode(text.slice(from)));
  }

  function open() { box.hidden = false; input.setAttribute('aria-expanded', 'true'); }
  function close() {
    box.hidden = true;
    input.setAttribute('aria-expanded', 'false');
    input.removeAttribute('aria-activedescendant');
    options = [];
    active = -1;
  }

  function setActive(n) {
    if (!options.length) return;
    active = (n + options.length) % options.length;
    options.forEach(function (o, i) {
      var on = i === active;
      o.classList.toggle('is-active', on);
      o.setAttribute('aria-selected', String(on));
    });
    input.setAttribute('aria-activedescendant', options[active].id);
    options[active].scrollIntoView({ block: 'nearest' });
  }

  function notice(node, isError) {
    var note = el('div', 'mb-suggest__note' + (isError ? ' is-error' : ''));
    if (typeof node === 'string') note.textContent = node; else note.appendChild(node);
    box.replaceChildren(note);
    options = [];
    active = -1;
    input.removeAttribute('aria-activedescendant');
    open();
  }

  /** 추천 항목 하나: 링크(option) 안에 제목 줄과 아랫줄 정보 */
  function option(href, q, title, meta) {
    var a = el('a', 'mb-suggest__item');
    a.href = href;
    a.id = listId + '-' + options.length;
    a.setAttribute('role', 'option');
    a.setAttribute('aria-selected', 'false');
    var t = el('span', 'mb-suggest__title');
    highlight(t, title, q);
    a.appendChild(t);
    if (meta) a.appendChild(el('span', 'mb-suggest__meta', meta));
    // 입력칸이 포커스를 잃어 목록이 닫히기 전에 클릭이 처리되도록 mousedown 에서 포커스 이동을 막는다
    a.addEventListener('mousedown', function (e) { e.preventDefault(); });
    options.push(a);
    return a;
  }

  function render(data, q) {
    last = { q: q, data: data };
    options = [];
    active = -1;
    input.removeAttribute('aria-activedescendant');
    var nodes = [];
    GROUPS.forEach(function (g) {
      var all = (data && data[g.key] || []).filter(function (i) { return g.title(i) && g.href(i); });
      if (!all.length) return;
      var group = el('div', 'mb-suggest__group');
      group.setAttribute('role', 'group');
      group.setAttribute('aria-label', g.label + ' ' + all.length + '건');
      var head = el('div', 'mb-suggest__head');
      head.append(el('span', 'mb-suggest__badge ' + g.badge, g.label), el('span', null, all.length + '건'));
      group.appendChild(head);
      all.slice(0, PER_GROUP).forEach(function (i) { group.appendChild(option(g.href(i), q, g.title(i), g.meta(i))); });
      nodes.push(group);
    });
    if (!nodes.length) {
      var none = document.createDocumentFragment();
      none.append(document.createTextNode('‘'), el('span', null, q), document.createTextNode('’에 맞는 결과가 없어요'));
      notice(none);
      return;
    }
    var all = option(searchHref(q), '', '‘' + q + '’ 전체 검색 결과 보기 →', '');
    all.classList.add('mb-suggest__all');
    nodes.push(all);
    box.replaceChildren.apply(box, nodes);
    open();
  }

  function request(q) {
    var no = ++requestNo;
    notice('찾는 중이에요…');
    MB.api('/api/v1/search?keyword=' + encodeURIComponent(q)).then(function (data) {
      if (no !== requestNo) return;   // 그 사이 더 새 입력이 있었다
      render(data, q);
    }, function () {
      if (no !== requestNo) return;
      notice('추천 검색어를 불러오지 못했어요. Enter 를 누르면 그대로 검색해요', true);
    });
  }

  /** 입력·포커스 때 부른다. 같은 검색어를 이미 받아 두었으면 다시 요청하지 않는다 */
  function refresh() {
    clearTimeout(timer);
    var q = input.value.trim();
    requestNo++;                      // 진행 중이던 요청의 응답은 버린다
    if (!q) { close(); return; }
    if (q === last.q) { render(last.data, q); return; }
    // 이전 검색어의 추천 목록·선택을 바로 지운다. 남겨 두면 고친 뒤 200ms 안에 Enter 를 눌렀을 때 예전 추천으로 이동한다.
    // 목록을 닫지 않고 '찾는 중' 으로 바꿔서 한글을 칠 때마다 목록이 깜빡이지 않게 한다
    notice('찾는 중이에요…');
    timer = setTimeout(function () { request(q); }, SUGGEST_DELAY);
  }

  input.addEventListener('input', refresh);
  input.addEventListener('focus', refresh);

  input.addEventListener('keydown', function (e) {
    if (e.isComposing) return;        // 한글 조합 중에는 Enter·화살표를 가로채지 않는다
    if (e.key === 'Escape') {
      // 닫기 전에 시작한 대기 중 요청(타이머·응답)이 목록을 다시 열지 못하게 함께 무효로 한다
      clearTimeout(timer);
      requestNo++;
      if (!box.hidden) { close(); e.preventDefault(); }
    } else if (e.key === 'ArrowDown' || e.key === 'ArrowUp') {
      if (!options.length) return;
      e.preventDefault();
      setActive(active < 0 ? (e.key === 'ArrowDown' ? 0 : options.length - 1) : active + (e.key === 'ArrowDown' ? 1 : -1));
    } else if (e.key === 'Enter' && active >= 0 && options[active]) {
      e.preventDefault();
      window.location.href = options[active].href;
    }
  });

  input.addEventListener('blur', function () { clearTimeout(timer); requestNo++; close(); });
  form.addEventListener('submit', function () { clearTimeout(timer); requestNo++; close(); });
})();
