/* comment.
*   담당 : PM 이재성 (#278, 검색창 자동완성)
*   공통 헤더의 검색창(.mb-search input[name=q])에 추천 검색어를 붙인다. 모든 학습자 화면에서 header.html 의 scriptsFragment 가 불러온다.
*   - 요청은 기존 API 28 (GET /api/v1/search?keyword=) 을 그대로 쓴다. 응답의 강의 제목·종목 이름·경제 용어를 추천 검색어로 보여 준다.
*   - 입력을 멈추고 SUGGEST_DELAY ms 뒤에 한 번만 요청하고, 늦게 온 응답이 최신 결과를 덮지 않게 요청 번호가 마지막인 것만 그린다.
*   - 추천 검색어를 고르면 그 글자로 통합 검색 결과(/user/search?q=)로 이동한다.
*   - 키보드: ↑↓ 로 고르고 Enter 로 이동, Esc 로 닫는다. 아무것도 고르지 않고 Enter 를 누르면 입력한 글자로 그대로 검색한다.
*   - 접근성: 입력칸이 combobox, 목록이 listbox, 각 항목이 option 이다 (aria-activedescendant 로 고른 항목을 알린다).
*   서버가 준 문구는 모두 textContent 로 넣는다 (HTML 로 해석하지 않는다).
*  */
(function () {
  'use strict';
  var input = document.querySelector('.mb-search input[name="q"]');
  if (!input || !window.MB || !window.MB.api) return;
  var form = input.form;

  var SUGGEST_DELAY = 200;     // 입력을 멈춘 뒤 기다리는 시간(ms)
  var PER_GROUP = 4;           // 종류(강의·종목·경제 용어)마다 보여 줄 최대 개수
  var GROUPS = [
    { key: 'courses', label: '강의', text: function (i) { return i.title; } },
    { key: 'stocks', label: '종목', text: function (i) { return i.name; } },
    { key: 'terms', label: '경제 용어', text: function (i) { return i.term; } }
  ];

  var listId = 'mb-suggest';
  var box = document.createElement('div');
  box.className = 'mb-suggest';
  box.id = listId;
  box.hidden = true;
  box.setAttribute('role', 'listbox');
  box.setAttribute('aria-label', '추천 검색어');
  form.appendChild(box);

  input.setAttribute('role', 'combobox');
  input.setAttribute('aria-autocomplete', 'list');
  input.setAttribute('aria-expanded', 'false');
  input.setAttribute('aria-controls', listId);

  var timer = null;
  var requestNo = 0;
  var options = [];            // 지금 보이는 추천 항목 (option 요소)
  var active = -1;             // 키보드로 고른 항목의 번호

  function el(tag, cls, text) {
    var n = document.createElement(tag);
    if (cls) n.className = cls;
    if (text != null) n.textContent = text;
    return n;
  }

  /** 입력한 글자와 같은 부분을 굵게 보여 준다 (대소문자 구분 없음) */
  function highlight(parent, text, q) {
    var lower = String(text).toLowerCase();
    var word = q.toLowerCase();
    var from = 0;
    var at;
    while (word && (at = lower.indexOf(word, from)) >= 0) {
      if (at > from) parent.appendChild(document.createTextNode(text.slice(from, at)));
      parent.appendChild(el('b', null, text.slice(at, at + word.length)));
      from = at + word.length;
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

  function go(text) {
    window.location.href = (form.getAttribute('action') || '/user/search') + '?q=' + encodeURIComponent(text);
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

  function notice(text, isError) {
    box.replaceChildren(el('div', 'mb-suggest__note' + (isError ? ' is-error' : ''), text));
    options = [];
    active = -1;
    input.removeAttribute('aria-activedescendant');
    open();
  }

  function render(data, q) {
    var nodes = [];
    var items = [];
    GROUPS.forEach(function (g) {
      var list = (data && data[g.key] || []).filter(function (i) { return g.text(i); }).slice(0, PER_GROUP);
      if (!list.length) return;
      var group = el('div', 'mb-suggest__group');
      group.setAttribute('role', 'group');
      group.setAttribute('aria-label', g.label);
      group.appendChild(el('span', 'mb-suggest__label', g.label));
      list.forEach(function (i) {
        var text = g.text(i);
        var opt = el('div', 'mb-suggest__item');
        opt.id = listId + '-' + items.length;
        opt.setAttribute('role', 'option');
        opt.setAttribute('aria-selected', 'false');
        highlight(opt, text, q);
        // 입력칸이 포커스를 잃어 목록이 닫히기 전에 클릭이 처리되도록 mousedown 에서 포커스 이동을 막는다
        opt.addEventListener('mousedown', function (e) { e.preventDefault(); });
        opt.addEventListener('click', function () { input.value = text; go(text); });
        group.appendChild(opt);
        items.push(opt);
      });
      nodes.push(group);
    });
    if (!nodes.length) { notice('추천 검색어가 없어요. Enter 를 누르면 그대로 검색해요'); return; }
    box.replaceChildren.apply(box, nodes);
    options = items;
    active = -1;
    input.removeAttribute('aria-activedescendant');
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

  input.addEventListener('input', function () {
    clearTimeout(timer);
    var q = input.value.trim();
    requestNo++;                      // 진행 중이던 요청의 응답은 버린다
    if (!q) { close(); return; }
    timer = setTimeout(function () { request(q); }, SUGGEST_DELAY);
  });

  input.addEventListener('keydown', function (e) {
    if (e.isComposing) return;        // 한글 조합 중에는 Enter·화살표를 가로채지 않는다
    if (e.key === 'Escape') {
      if (!box.hidden) { close(); e.preventDefault(); }
    } else if (e.key === 'ArrowDown' || e.key === 'ArrowUp') {
      if (!options.length) return;
      e.preventDefault();
      setActive(active < 0 ? (e.key === 'ArrowDown' ? 0 : options.length - 1) : active + (e.key === 'ArrowDown' ? 1 : -1));
    } else if (e.key === 'Enter' && active >= 0) {
      e.preventDefault();
      var text = options[active].textContent;
      input.value = text;
      go(text);
    }
  });

  input.addEventListener('blur', function () { clearTimeout(timer); requestNo++; close(); });
  form.addEventListener('submit', function () { clearTimeout(timer); requestNo++; close(); });
})();
