/* comment.
*  담당 : SCM 서수영 (#63, 관리자 모의 청약 운영 화면)
*  templates/admin/housing.html 의 동작. 화면 구성은 moneybeetle-page/AdminHousingManagement.dc.html 시안을 따른다
*  - 데이터 : 단지 목록 GET /api/v1/admin/housing/complexes (142), 등록 POST (143), 수정 PUT /{id} (144), 삭제 DELETE /{id} (145)
*            신청자 GET /{id}/applications (146), 산정 POST /{id}/compute (147), 발표 POST /{id}/announce (148)
*  - 보기 4개 : 대시보드 / 청약 공고 관리 / 신청 현황 / 당첨 결과 관리 (주소 끝 #notices, #apps, #results)
*  - 시안 칸 중 API 에 따로 없는 값은 있는 값에서 만든다
*      청약 유형 = supplyType 이 "공공" 으로 시작하면 공공, 아니면 민간 / 거주지역·세부 위치 = location 을 첫 칸에서 나눔
*      전용면적 = areaType 의 숫자 / 신청 시작일 = 공고 게시일 / 커트라인 = 산정 결과의 마지막 당첨자 가점 (산정 전에는 지금 순위로 예상)
*  - 규칙(신청자가 있거나 산정한 공고의 일정·세대 수 잠금, 산정·발표 날짜)은 화면에서 먼저 막고, 서버도 같은 규칙으로 다시 막는다
*  */
(function () {
  'use strict';

  var root = document.getElementById('ah');
  if (!root || !window.MB) return;

  var API = '/api/v1/admin/housing/complexes';
  var TODAY = root.dataset.today;                       // 'yyyy-MM-dd' (서울)
  var STATUS_TEXT = { UPCOMING: '모집 예정', OPEN: '모집 중', CLOSED: '모집 마감', ANNOUNCED: '발표 완료' };
  var STATUS_ORDER = ['UPCOMING', 'OPEN', 'CLOSED', 'ANNOUNCED'];
  var TYPE_TEXT = { public: '공공', private: '민간' };
  var SUPPLY_TYPE = { public: '공공분양 일반공급', private: '민영 일반공급' };   // 시드와 같은 표기
  var REGIONS = ['서울', '경기', '인천', '부산', '세종', '대구', '광주', '대전'];
  var WEEK = ['일', '월', '화', '수', '목', '금', '토'];
  var VIEWS = {
    dash: ['모의 청약 대시보드', '모의 청약 공고와 신청 현황을 한눈에 확인해요'],
    notices: ['청약 공고 관리', '공공·민간 가상 공고를 등록하고 수정해요. 수정한 일정은 사용자 캘린더에 바로 반영돼요'],
    apps: ['신청 현황', '공고별 신청 목록과 신청 당시 정보를 확인해요'],
    results: ['당첨 결과 관리', '모집 마감 후 가점 순위로 결과를 산정하고 발표해요']
  };

  var state = {
    view: 'dash', items: [], loaded: false, apps: {},
    nStatus: '', nQ: '', nType: '', editing: null,
    af: { q: '', complex: '', type: '', state: '', date: '' }
  };

  // ---------- 공통 도우미 ----------
  function el(tag, cls, text) {
    var node = document.createElement(tag);
    if (cls) node.className = cls;
    if (text != null) node.textContent = text;
    return node;
  }
  function fmt(n) {
    return Number(n || 0).toLocaleString('ko-KR');
  }
  function fmtD(iso) {                                  // 2026-11-12 → 11.12(목)
    if (!iso) return '-';
    var p = String(iso).slice(0, 10).split('-');
    var d = new Date(Number(p[0]), Number(p[1]) - 1, Number(p[2]));
    return p[1] + '.' + p[2] + '(' + WEEK[d.getDay()] + ')';
  }
  function fmtAt(iso) {                                 // 2026-10-03T09:00:00 → 2026.10.03 09:00
    return String(iso || '').slice(0, 16).replace('T', ' ').replace(/-/g, '.');
  }
  function daysTo(iso) {
    var a = new Date(TODAY + 'T00:00:00');
    var b = new Date(String(iso).slice(0, 10) + 'T00:00:00');
    return Math.round((b - a) / 86400000);
  }
  function won(n) {                                     // 320000000 → 3억 2,000만 캐시, 123456789 → 1억 2,345만 6,789 캐시
    var eok = Math.floor(n / 100000000);
    var man = Math.floor((n % 100000000) / 10000);
    var rest = n % 10000;                               // 만 단위 아래도 버리지 않는다 (폼은 1 이상 정수를 모두 받는다)
    var out = (eok ? eok + '억 ' : '') + (man ? fmt(man) + '만 ' : '') + (rest ? fmt(rest) : '');
    return (out.trim() || '0') + ' 캐시';
  }
  function typeOf(c) {
    return String(c.supplyType || '').indexOf('공공') === 0 ? 'public' : 'private';
  }
  function typeChip(c) {
    var t = typeOf(c);
    return el('span', 'ah-type is-' + t, TYPE_TEXT[t]);
  }
  function statusChip(status, big) {
    return el('span', 'ah-st is-' + status + (big ? ' ah-st--lg' : ''), STATUS_TEXT[status] || status);
  }
  function dateChips(c) {
    var box = el('div', 'ah-dates');
    box.appendChild(el('span', 'is-post', '공고 ' + fmtD(c.noticeDate)));
    box.appendChild(el('span', 'is-close', '마감 ' + fmtD(c.applyEndDate)));
    box.appendChild(el('span', 'is-announce', '발표 ' + fmtD(c.announceDate)));
    return box;
  }
  function bind(name, value) {
    document.querySelectorAll('[data-bind="' + name + '"]').forEach(function (n) { n.textContent = value; });
  }
  function find(id) {
    return state.items.filter(function (c) { return c.complexId === id; })[0] || null;
  }
  function appsOf(c) {
    return state.apps[c.complexId] || [];
  }
  function isScheduleLocked(c) {                        // 신청자가 있거나 이미 산정함 → 일정·세대 수 잠금 (SP-76, #48)
    return c.applicants > 0 || c.computed;
  }
  function canCompute(c) {                              // 신청 마감 다음 날부터, 한 번만
    return !c.computed && TODAY > c.applyEndDate;
  }
  function canAnnounce(c) {                             // 산정한 뒤 발표일부터, 한 번만
    return c.computed && c.status !== 'ANNOUNCED' && TODAY >= c.announceDate;
  }
  function needsWork(c) {                               // 결과 산정 대기 (시안 hTodo)
    return (c.status === 'CLOSED' && !c.computed) || (c.computed && c.status !== 'ANNOUNCED');
  }
  function count(status) {
    return state.items.filter(function (c) { return c.status === status; }).length;
  }
  function button(cls, text, onClick) {
    var b = el('button', cls, text);
    b.type = 'button';
    if (onClick) b.addEventListener('click', onClick);
    return b;
  }

  // ---------- 확인창 (되돌릴 수 없는 작업 전에) ----------
  // 어느 버튼을 눌렀는지는 폼 submit 의 submitter 로 안다 (close 이벤트는 브라우저에 따라 오지 않을 때가 있다). Esc 는 cancel
  function modal(dialog) {
    var form = dialog.querySelector('form');
    return new Promise(function (resolve) {
      function done(ok) {
        form.removeEventListener('submit', onSubmit);
        dialog.removeEventListener('cancel', onCancel);
        if (dialog.open) dialog.close();
        resolve(ok);
      }
      function onSubmit(e) {
        e.preventDefault();
        done(!!(e.submitter && e.submitter.value === 'ok'));
      }
      function onCancel(e) {
        e.preventDefault();
        done(false);
      }
      form.addEventListener('submit', onSubmit);
      dialog.addEventListener('cancel', onCancel);
      dialog.showModal();
    });
  }
  var confirmDialog = document.getElementById('ah-confirm');
  function confirmBox(title, body, okLabel) {
    confirmDialog.querySelector('[data-confirm-title]').textContent = title;
    confirmDialog.querySelector('[data-confirm-body]').textContent = body;
    confirmDialog.querySelector('[data-confirm-ok]').textContent = okLabel || '확인';
    return modal(confirmDialog);
  }

  // ---------- 보기 전환 (왼쪽 메뉴) ----------
  function showView(name, opts) {
    if (!VIEWS[name]) name = 'dash';
    state.view = name;
    root.querySelectorAll('[data-view]').forEach(function (v) { v.hidden = v.dataset.view !== name; });
    document.querySelectorAll('.ah-nav__item').forEach(function (b) {
      var on = b.dataset.viewGo === name;
      b.classList.toggle('is-active', on);
      if (on) b.setAttribute('aria-current', 'page'); else b.removeAttribute('aria-current');
    });
    root.querySelector('[data-page-title]').textContent = VIEWS[name][0];
    root.querySelector('[data-page-desc]').textContent = VIEWS[name][1];
    if (history.replaceState) history.replaceState(null, '', name === 'dash' ? location.pathname : '#' + name);
    // 모바일에서 메뉴가 열려 있으면 닫는다 (공통 drawer.js : 오버레이를 누르면 닫힘)
    var side = document.querySelector('.ah-side');
    if (side && side.getAttribute('data-open') === 'true') document.querySelector('[data-mb-overlay]').click();
    if (!(opts && opts.keepScroll)) window.scrollTo(0, 0);
    renderView();
  }
  document.addEventListener('click', function (e) {
    var go = e.target.closest('[data-view-go]');
    if (go) showView(go.dataset.viewGo);
  });

  function renderView() {
    if (!state.loaded) return;
    if (state.view === 'dash') renderDash();
    else if (state.view === 'notices') renderNotices();
    else if (state.view === 'apps') renderApps();
    else renderResults();
  }

  // ---------- 데이터 (API 142 + 단지별 146) ----------
  var loadSeq = 0;
  function loadAll() {
    var seq = ++loadSeq;
    return MB.api(API).then(function (data) {
      if (seq !== loadSeq) return null;
      var items = (data && data.items) || [];
      // 신청자가 있는 단지만 신청자 목록을 받는다 (가점·순위·결과 → 신청 현황·커트라인·결과 검토)
      return Promise.all(items.map(function (c) {
        if (!c.applicants) return Promise.resolve([]);
        return MB.api(API + '/' + c.complexId + '/applications')
            .then(function (d) { return (d && d.items) || []; })
            .catch(function () { return []; });
      })).then(function (lists) {
        if (seq !== loadSeq) return;
        state.items = items;
        state.apps = {};
        items.forEach(function (c, i) { state.apps[c.complexId] = lists[i]; });
        state.loaded = true;
        renderSide();
        fillRegionOptions();
        fillComplexOptions();
        renderView();
      });
    }).catch(function (err) {
      if (seq !== loadSeq) return;
      root.querySelectorAll('[data-list]').forEach(function (box) {
        if (box.dataset.list === 'stats' || box.dataset.list === 'statusChips') return;
        box.replaceChildren(el('p', 'ah-muted', '불러오지 못했어요. ' + err.message));
      });
      MB.toast(err.message, 'error');
    });
  }

  function renderSide() {
    var todo = state.items.filter(needsWork).length;
    bind('todo', String(todo));
    var badge = document.querySelector('[data-bind-badge]');
    badge.textContent = String(todo);
    badge.hidden = !todo;
  }

  // ---------- 대시보드 ----------
  function renderDash() {
    var all = state.items;
    var members = {};
    var totalApps = 0;
    all.forEach(function (c) {
      totalApps += c.applicants;
      appsOf(c).forEach(function (a) { members[a.nickname] = true; });
    });
    var stats = [
      { label: '전체 청약 공고', value: all.length + '건', dot: '#263B1A',
        sub: '공공 ' + all.filter(function (c) { return typeOf(c) === 'public'; }).length + ' · 민간 ' + all.filter(function (c) { return typeOf(c) === 'private'; }).length,
        go: function () { goNotices(''); } },
      { label: '모집 예정', value: count('UPCOMING') + '건', sub: '공고 게시 전', dot: '#C9D3C1', go: function () { goNotices('UPCOMING'); } },
      { label: '모집 중', value: count('OPEN') + '건', sub: '지금 신청 가능', dot: '#9fe870', go: function () { goNotices('OPEN'); } },
      { label: '모집 마감', value: count('CLOSED') + '건', dot: '#F59E0B',
        sub: '산정 대기 ' + all.filter(function (c) { return c.status === 'CLOSED' && !c.computed; }).length + '건',
        go: function () { showView('results'); } },
      { label: '발표 완료', value: count('ANNOUNCED') + '건', sub: '회원 결과 공개됨', dot: '#F2C14E', go: function () { showView('results'); } },
      { label: '전체 회원 신청', value: totalApps + '건', sub: '신청 회원 ' + Object.keys(members).length + '명', dot: '#3B82F6',
        go: function () { showView('apps'); } }
    ];
    var box = root.querySelector('[data-list="stats"]');
    box.replaceChildren();
    stats.forEach(function (s) {
      var b = button('ah-stat', null, s.go);
      var label = el('span', 'ah-stat__label');
      var dot = el('span', 'ah-stat__dot');
      dot.style.background = s.dot;
      label.appendChild(dot);
      label.appendChild(document.createTextNode(s.label));
      b.appendChild(label);
      b.appendChild(el('span', 'ah-stat__value', s.value));
      b.appendChild(el('span', 'ah-stat__sub', s.sub));
      box.appendChild(b);
    });

    var groups = [
      { label: '결과 산정 필요', dot: '#F59E0B', action: '결과 산정',
        items: all.filter(function (c) { return c.status === 'CLOSED' && !c.computed; }),
        note: function (c) { return '모집 마감 ' + fmtD(c.applyEndDate) + ' · 회원 신청 ' + c.applicants + '건'; },
        go: function () { showView('results'); } },
      { label: '산정 완료 · 발표 대기', dot: '#10B981', action: '결과 발표',
        items: all.filter(function (c) { return c.computed && c.status !== 'ANNOUNCED'; }),
        note: function (c) { return '발표 예정일 ' + fmtD(c.announceDate); },
        go: function () { showView('results'); } },
      { label: '7일 이내 모집 마감', dot: '#3B82F6', action: '공고 보기',
        items: all.filter(function (c) { var d = daysTo(c.applyEndDate); return c.status === 'OPEN' && d >= 0 && d <= 7; })
            .sort(function (a, b) { return a.applyEndDate < b.applyEndDate ? -1 : 1; }),
        note: function (c) { return '마감 ' + fmtD(c.applyEndDate) + ' · D-' + daysTo(c.applyEndDate) + ' · 회원 신청 ' + c.applicants + '건'; },
        go: function () { goNotices('OPEN'); } }
    ];
    var work = root.querySelector('[data-list="work"]');
    work.replaceChildren();
    var total = 0;
    groups.forEach(function (g) {
      total += g.items.length;
      var wrap = el('div', 'ah-work__group');
      var head = el('div', 'ah-work__head');
      var dot = el('span', 'ah-stat__dot');
      dot.style.background = g.dot;
      head.appendChild(dot);
      head.appendChild(el('span', null, g.label));
      head.appendChild(el('span', null, g.items.length + '건'));
      wrap.appendChild(head);
      if (!g.items.length) wrap.appendChild(el('span', 'ah-work__empty', '해당 공고가 없어요.'));
      g.items.forEach(function (c) {
        var t = button('ah-task', null, g.go);
        var text = el('span', 'ah-task__text');
        text.appendChild(el('b', null, c.name));
        text.appendChild(el('span', null, g.note(c)));
        t.appendChild(text);
        t.appendChild(el('span', 'ah-task__go', g.action + ' →'));
        wrap.appendChild(t);
      });
      work.appendChild(wrap);
    });
    bind('workTotal', String(total));

    var recent = root.querySelector('[data-list="recent"]');
    recent.replaceChildren();
    var list = all.slice().sort(function (a, b) { return a.noticeDate < b.noticeDate ? 1 : -1; }).slice(0, 5);
    if (!list.length) recent.appendChild(el('p', 'ah-muted', '등록된 공고가 없어요.'));
    list.forEach(function (c) {
      var row = el('div', 'ah-recent');
      var head = el('div', 'ah-recent__head');
      head.appendChild(typeChip(c));
      head.appendChild(el('b', null, c.name));
      head.appendChild(statusChip(c.status));
      row.appendChild(head);
      row.appendChild(dateChips(c));
      recent.appendChild(row);
    });
  }

  function goNotices(status) {
    state.nStatus = status;
    showView('notices');
  }

  // ---------- 청약 공고 관리 : 목록 ----------
  function renderNotices() {
    var chips = root.querySelector('[data-list="statusChips"]');
    chips.replaceChildren();
    [''].concat(STATUS_ORDER).forEach(function (k) {
      var b = button('ah-chip' + (state.nStatus === k ? ' is-active' : ''), k ? STATUS_TEXT[k] : '전체', function () {
        state.nStatus = k;
        renderNotices();
      });
      b.appendChild(el('span', null, String(k ? count(k) : state.items.length)));
      chips.appendChild(b);
    });

    var q = state.nQ.trim().toLowerCase();
    var rows = state.items.filter(function (c) {
      return (!state.nStatus || c.status === state.nStatus) && (!state.nType || typeOf(c) === state.nType)
          && (!q || c.name.toLowerCase().indexOf(q) >= 0);
    });
    bind('noticeCount', String(rows.length));
    var box = root.querySelector('[data-list="notices"]');
    box.replaceChildren();
    if (!rows.length) {
      box.appendChild(el('div', 'ah-empty-row', state.items.length ? '조건에 맞는 공고가 없어요.' : '등록된 공고가 없어요. 위에서 첫 공고를 게시해 보세요.'));
      return;
    }
    rows.forEach(function (c) { box.appendChild(noticeRow(c)); });
  }

  function noticeRow(c) {
    var row = el('div', 'ah-row' + (state.editing === c.complexId ? ' is-editing' : ''));
    var head = el('div', 'ah-row__head');
    head.appendChild(typeChip(c));
    head.appendChild(el('b', null, c.name));
    head.appendChild(el('div', 'ah-row__fill'));
    head.appendChild(statusChip(c.status, true));
    row.appendChild(head);
    row.appendChild(el('span', 'ah-row__meta',
        [c.location, '전용 ' + c.areaType, won(c.salePrice), fmt(c.supplyUnits) + '세대'].join(' · ')));
    row.appendChild(dateChips(c));
    if (c.applicants > 0) {
      row.appendChild(el('span', 'ah-row__lock', '신청 ' + c.applicants + '건 · 일정·공급 세대 수는 바꿀 수 없고, 삭제할 수 없어요'));
    } else if (c.computed) {
      row.appendChild(el('span', 'ah-row__lock', '산정 완료 · 일정·공급 세대 수는 바꿀 수 없어요'));
    }
    var actions = el('div', 'ah-actions');
    actions.appendChild(button('ah-pill', '수정', function () { startEdit(c.complexId); }));
    actions.appendChild(button('ah-pill', '신청 현황 ' + c.applicants + '건', function () { openApps(c.complexId); }));
    var hide = button('ah-pill', '비공개 전환');
    hide.setAttribute('aria-disabled', 'true');
    hide.title = '준비 중이에요 (공고 숨김 기능이 아직 없어요)';
    actions.appendChild(hide);
    var del = button('ah-pill ah-pill--danger', '삭제', function () { removeComplex(c); });
    del.disabled = c.applicants > 0;
    if (del.disabled) del.title = '신청자가 있는 공고는 삭제할 수 없어요';
    actions.appendChild(del);
    row.appendChild(actions);
    return row;
  }

  root.querySelector('[data-filter="q"]').addEventListener('input', function (e) {
    state.nQ = e.target.value;
    renderNotices();
  });
  root.querySelector('[data-filter="type"]').addEventListener('change', function (e) {
    state.nType = e.target.value;
    renderNotices();
  });

  // ---------- 청약 공고 관리 : 등록·수정 폼 (API 143·144) ----------
  var form = document.getElementById('ah-form');
  var submitBtn = form.querySelector('[data-form-submit]');
  var cancelBtn = form.querySelector('[data-form-cancel]');
  var formMsg = form.querySelector('[data-form-msg]');
  var lockNote = form.querySelector('[data-lock-note]');
  var LOCKED_FIELDS = ['supplyUnits', 'noticeDate', 'applyEndDate', 'announceDate'];

  function input(name) {
    return form.querySelector('[name="' + name + '"]');
  }
  function splitLocation(location) {                    // '서울 마포구 상암동' → ['서울', '마포구 상암동']
    var s = String(location || '').trim();
    var i = s.indexOf(' ');
    return i < 0 ? [s, ''] : [s.slice(0, i), s.slice(i + 1).trim()];
  }
  function fillRegionOptions() {
    var select = input('region');
    var keep = select.value;
    var regions = REGIONS.slice();
    state.items.forEach(function (c) {
      var r = splitLocation(c.location)[0];
      if (r && regions.indexOf(r) < 0) regions.push(r);   // 기존 공고의 지역도 고를 수 있게
    });
    select.replaceChildren();
    regions.forEach(function (r) {
      var o = el('option', null, r);
      o.value = r;
      select.appendChild(o);
    });
    if (keep && regions.indexOf(keep) >= 0) select.value = keep;
  }
  function setLocked(locked) {
    LOCKED_FIELDS.forEach(function (n) { input(n).disabled = locked; });
  }
  function clearErrors() {
    form.querySelectorAll('.is-error').forEach(function (n) { n.classList.remove('is-error'); });
    formMsg.textContent = '';
  }

  function resetForm() {
    state.editing = null;
    form.reset();
    clearErrors();
    setLocked(false);
    lockNote.hidden = true;
    form.querySelector('[data-form-title]').textContent = '새 공고 등록';
    submitBtn.textContent = '공고 게시';
    cancelBtn.hidden = true;
    if (state.view === 'notices') renderNotices();
  }

  function startEdit(id) {
    var c = find(id);
    if (!c) return;
    resetForm();
    state.editing = id;
    var loc = splitLocation(c.location);
    input('name').value = c.name;
    input('type').value = typeOf(c);
    input('region').value = loc[0];
    input('district').value = loc[1];
    input('area').value = String(c.areaType || '').replace(/[^0-9.]/g, '');
    input('salePrice').value = String(c.salePrice);
    input('supplyUnits').value = String(c.supplyUnits);
    input('noticeDate').value = c.noticeDate;
    input('applyEndDate').value = c.applyEndDate;
    input('announceDate').value = c.announceDate;
    var locked = isScheduleLocked(c);
    setLocked(locked);
    lockNote.hidden = !locked;
    lockNote.textContent = c.computed
        ? '당첨자를 산정한 공고라 공급 세대 수와 일정은 바꿀 수 없어요. 신청이 다시 열리지 않게 하기 위한 제한이에요.'
        : '신청자가 있는 공고라 공급 세대 수와 일정은 바꿀 수 없어요. 신청 이력과 당첨 결과를 지키기 위한 제한이에요.';
    form.querySelector('[data-form-title]').textContent = '공고 수정 · ' + c.name;
    submitBtn.textContent = '수정 저장';
    cancelBtn.hidden = false;
    renderNotices();
    form.scrollIntoView({ behavior: 'smooth', block: 'start' });
    input('name').focus({ preventScroll: true });
  }
  cancelBtn.addEventListener('click', resetForm);

  // 입력값만 보고 알 수 있는 규칙 (API 143 : 빈 칸 없음, 금액·세대 수 1 이상, 일정 순서). 서버도 다시 확인한다
  function readForm() {
    clearErrors();
    var orig = state.editing ? find(state.editing) : null;
    var locked = orig && isScheduleLocked(orig);
    var problems = [];
    function bad(name, msg) {
      input(name).classList.add('is-error');
      problems.push(msg);
    }
    var name = input('name').value.trim();
    var district = input('district').value.trim();
    var area = input('area').value.replace(/㎡/g, '').trim();
    var price = input('salePrice').value.replace(/,/g, '').trim();
    var units = input('supplyUnits').value.replace(/,/g, '').trim();
    if (!name) bad('name', '아파트 단지명을 입력해 주세요');
    if (!district) bad('district', '세부 위치를 입력해 주세요');
    if (!/^\d+(\.\d+)?$/.test(area) || Number(area) <= 0) bad('area', '전용면적은 0보다 큰 숫자로 입력해 주세요');
    if (!/^\d+$/.test(price) || Number(price) < 1) bad('salePrice', '분양가는 1 이상의 숫자로 입력해 주세요');
    if (!locked && (!/^\d+$/.test(units) || Number(units) < 1)) bad('supplyUnits', '공급 세대 수는 1 이상의 숫자로 입력해 주세요');
    var dates = ['noticeDate', 'applyEndDate', 'announceDate'];
    if (!locked) {
      dates.forEach(function (n) { if (!input(n).value) bad(n, '공고 게시일·신청 마감일·발표일을 모두 입력해 주세요'); });
      if (!problems.length) {
        if (input('noticeDate').value > input('applyEndDate').value) bad('applyEndDate', '신청 마감일은 공고 게시일과 같거나 뒤여야 해요');
        else if (input('applyEndDate').value > input('announceDate').value) bad('announceDate', '당첨자 발표일은 신청 마감일과 같거나 뒤여야 해요');
      }
    }
    if (problems.length) {
      formMsg.textContent = problems[0];
      return null;
    }
    var type = input('type').value;
    return {
      name: name,
      location: input('region').value + ' ' + district,
      areaType: area + '㎡',
      salePrice: Number(price),
      // 유형을 바꾸지 않았으면 원래 공급 유형 표기를 그대로 둔다
      supplyType: orig && typeOf(orig) === type ? orig.supplyType : SUPPLY_TYPE[type],
      supplyUnits: locked ? orig.supplyUnits : Number(units),
      noticeDate: locked ? orig.noticeDate : input('noticeDate').value,
      // 시안 : 게시 시점부터 바로 신청을 받는다 → 신청 시작일 = 공고 게시일 (잠긴 공고는 원래 일정 그대로)
      applyStartDate: locked ? orig.applyStartDate : input('noticeDate').value,
      applyEndDate: locked ? orig.applyEndDate : input('applyEndDate').value,
      announceDate: locked ? orig.announceDate : input('announceDate').value
    };
  }

  form.addEventListener('submit', function (e) {
    e.preventDefault();
    var body = readForm();
    if (!body) return;
    var id = state.editing;
    submitBtn.disabled = true;                          // 연타로 같은 공고를 두 번 등록하지 않도록
    var req = id
        ? MB.api(API + '/' + id, { method: 'PUT', body: body })
        : MB.api(API, { method: 'POST', body: body });
    req.then(function () {
      MB.toast(id ? '공고를 수정했어요' : '공고를 게시했어요 · ' + body.name);
      resetForm();
      return loadAll();
    }).catch(function (err) {
      formMsg.textContent = err.message;
      MB.toast(err.message, 'error');
      // 그사이 신청자가 생겼거나 산정됐으면 목록을 새로 받아 잠금 상태를 맞춘다
      if (err.code === 'COMPLEX_HAS_APPLICANTS' || err.code === 'ALREADY_COMPUTED' || err.code === 'NOT_FOUND') {
        loadAll().then(function () { if (id && find(id)) startEdit(id); else resetForm(); });
      }
    }).finally(function () {
      submitBtn.disabled = false;
    });
  });

  // ---------- 청약 공고 관리 : 삭제 (API 145, Soft Delete) ----------
  function removeComplex(c) {
    confirmBox('공고를 삭제할까요?', c.name + '\n삭제한 공고는 사용자 화면과 이 목록에서 사라져요.', '삭제')
        .then(function (ok) {
          if (!ok) return null;
          return MB.api(API + '/' + c.complexId, { method: 'DELETE' }).then(function () {
            MB.toast('공고를 삭제했어요 · ' + c.name);
            if (state.editing === c.complexId) resetForm();
            return loadAll();
          });
        })
        .catch(function (err) {
          MB.toast(err.message, 'error');
          loadAll();
        });
  }

  // ---------- 신청 현황 (API 146, 공고를 모아서) ----------
  var RES_BG = { '당첨': '#9fe870', '낙첨': '#E4E7E0', '산정 완료(미발표)': '#EEF1EA', '접수 완료': '#EAF7D8', '발표 대기': '#FFF0D6' };
  function appState(a, c) {
    if (a.result) {
      if (c.status !== 'ANNOUNCED') return '산정 완료(미발표)';
      return a.result === 'WIN' ? '당첨' : '낙첨';
    }
    return c.status === 'OPEN' ? '접수 완료' : '발표 대기';
  }

  var appFilters = root.querySelectorAll('[data-app-filter]');
  function fillComplexOptions() {
    var select = root.querySelector('[data-app-filter="complex"]');
    var keep = state.af.complex;
    select.replaceChildren(el('option', null, '전체 공고'));
    select.firstChild.value = '';
    state.items.forEach(function (c) {
      var o = el('option', null, c.name);
      o.value = String(c.complexId);
      select.appendChild(o);
    });
    select.value = keep && find(Number(keep)) ? keep : '';
  }
  appFilters.forEach(function (f) {
    f.addEventListener(f.tagName === 'SELECT' || f.type === 'date' ? 'change' : 'input', function () {
      state.af[f.dataset.appFilter] = f.value;
      renderApps();
    });
  });
  root.querySelector('[data-app-reset]').addEventListener('click', function () {
    state.af = { q: '', complex: '', type: '', state: '', date: '' };
    appFilters.forEach(function (f) { f.value = ''; });
    renderApps();
  });

  function openApps(complexId) {
    state.af = { q: '', complex: String(complexId), type: '', state: '', date: '' };
    appFilters.forEach(function (f) { f.value = state.af[f.dataset.appFilter]; });
    showView('apps');
  }

  function renderApps() {
    var af = state.af;
    var q = af.q.trim().toLowerCase();
    var rows = [];
    state.items.forEach(function (c) {
      if (af.complex && String(c.complexId) !== af.complex) return;
      if (af.type && typeOf(c) !== af.type) return;
      if (q && c.name.toLowerCase().indexOf(q) < 0) return;
      appsOf(c).forEach(function (a) {
        var st = appState(a, c);
        if (af.state && st !== af.state) return;
        if (af.date && String(a.appliedAt).slice(0, 10) !== af.date) return;
        rows.push({ a: a, c: c, st: st });
      });
    });
    rows.sort(function (x, y) { return x.a.appliedAt < y.a.appliedAt ? 1 : -1; });   // 최근 신청부터

    bind('appCount', String(rows.length));
    bind('appScope', af.complex && find(Number(af.complex)) ? find(Number(af.complex)).name : '전체 공고');
    var avg = rows.length ? rows.reduce(function (t, r) { return t + r.a.totalScore; }, 0) / rows.length : null;
    bind('appAvg', avg == null ? '-' : String(Math.round(avg * 10) / 10));
    root.querySelector('[data-app-reset]').hidden = !(af.q || af.complex || af.type || af.state || af.date);

    var box = root.querySelector('[data-list="apps"]');
    box.replaceChildren();
    if (!rows.length) {
      box.appendChild(el('div', 'ah-table-empty', '조건에 맞는 신청이 없어요.'));
      return;
    }
    rows.forEach(function (r) {
      var tr = button('ah-tr', null, function () { openAppDetail(r); });
      tr.appendChild(el('span', 'ah-tr__receipt', r.a.receiptNo));
      tr.appendChild(el('b', null, r.a.nickname));
      tr.appendChild(el('span', null, r.c.name));
      tr.appendChild(typeChip(r.c));
      tr.appendChild(el('span', null, fmt(r.a.paidCount) + '회'));
      tr.appendChild(el('b', null, r.a.totalScore + '점'));
      tr.appendChild(el('span', null, r.a.resultRank != null ? r.a.resultRank + '위' : '-'));
      tr.appendChild(el('span', 'ah-tr__at', fmtAt(r.a.appliedAt)));
      var res = el('span', 'ah-res', r.st);
      res.style.background = RES_BG[r.st] || '#EEF1EA';
      tr.appendChild(res);
      box.appendChild(tr);
    });
  }

  var detailDialog = document.getElementById('ah-app-detail');
  function openAppDetail(r) {
    var kv = detailDialog.querySelector('[data-app-detail]');
    kv.replaceChildren();
    [['접수번호', r.a.receiptNo], ['신청자', r.a.nickname], ['신청 단지', r.c.name], ['청약 유형', TYPE_TEXT[typeOf(r.c)]],
      ['신청 일시', fmtAt(r.a.appliedAt)], ['신청 당시 가점', r.a.totalScore + '점'], ['신청 당시 납입 회차', fmt(r.a.paidCount) + '회'],
      ['산정 순위', r.a.resultRank != null ? r.a.resultRank + '위' : '산정 전'], ['신청 상태', r.st]].forEach(function (p) {
      kv.appendChild(el('dt', null, p[0]));
      kv.appendChild(el('dd', null, p[1]));
    });
    modal(detailDialog);
  }

  // ---------- 당첨 결과 관리 (API 147·148) ----------
  // 처리 순서 : 모집 마감 → 결과 산정 → 결과 검토 → 결과 발표
  function cutline(c) {
    var list = appsOf(c);
    if (!list.length) return '-';
    if (c.computed) {
      var wins = list.filter(function (a) { return a.result === 'WIN'; });
      if (!wins.length) return '-';
      return Math.min.apply(null, wins.map(function (a) { return a.totalScore; })) + '점';
    }
    // 산정 전 : 서버가 가점 → 납입 회차 → 신청 순으로 준 순서에서 공급 세대 수번째 (같은 점수는 추첨이라 예상)
    if (list.length <= c.supplyUnits) return '전원 당첨 (예상)';
    return list[c.supplyUnits - 1].totalScore + '점 (예상)';
  }

  function renderResults() {
    var box = root.querySelector('[data-list="results"]');
    box.replaceChildren();
    if (!state.items.length) {
      box.appendChild(el('div', 'card ah-box ah-muted', '등록된 공고가 없어요.'));
      return;
    }
    // 오늘 처리할 수 있는 공고를 먼저 (그다음은 목록 순서 그대로)
    var items = state.items.slice().sort(function (a, b) {
      var ra = canCompute(a) || canAnnounce(a) ? 0 : 1;
      var rb = canCompute(b) || canAnnounce(b) ? 0 : 1;
      return ra - rb;
    });
    items.forEach(function (c) { box.appendChild(resultCard(c)); });
  }

  function resultCard(c) {
    var pub = c.status === 'ANNOUNCED';
    var card = el('section', 'card ah-box ah-result');
    var head = el('div', 'ah-result__head');
    head.appendChild(typeChip(c));
    head.appendChild(el('b', null, c.name));
    head.appendChild(statusChip(c.status, true));
    head.appendChild(el('div', 'ah-row__fill'));
    head.appendChild(el('span', 'ah-result__date', (pub ? '발표일 ' : '발표 예정일 ') + fmtD(c.announceDate)));
    card.appendChild(head);

    var done = pub ? 4 : c.computed ? 2 : c.status === 'CLOSED' ? 1 : 0;
    var steps = el('div', 'ah-steps');
    ['모집 마감', '결과 산정', '결과 검토', '결과 발표'].forEach(function (label, i) {
      var s = el('div', 'ah-step' + (i < done ? ' is-done' : i === done ? ' is-now' : ''));
      s.appendChild(el('i', null, i < done ? '✓' : String(i + 1)));
      s.appendChild(el('span', null, label));
      steps.appendChild(s);
    });
    card.appendChild(steps);

    var list = appsOf(c);
    var wins = list.filter(function (a) { return a.result === 'WIN'; }).length;
    var loses = list.filter(function (a) { return a.result === 'LOSE'; }).length;
    var kvs = el('dl', 'ah-kvs');
    [['공급 세대', fmt(c.supplyUnits) + '세대'], ['회원 신청', c.applicants + '건'],
      ['당첨 인원', c.computed ? wins + '명' : '최대 ' + Math.min(c.supplyUnits, c.applicants) + '명'],
      ['경쟁률', (c.applicants / c.supplyUnits).toFixed(2) + ' : 1'], ['가점 커트라인', cutline(c)],
      ['산정 상태', pub ? '발표 완료' : c.computed ? '산정 완료' : '미산정']].forEach(function (p) {
      var d = el('div');
      d.appendChild(el('dt', null, p[0]));
      d.appendChild(el('dd', null, p[1]));
      kvs.appendChild(d);
    });
    card.appendChild(kvs);

    if (c.computed) card.appendChild(reviewBox(c, list, wins, loses, pub));

    var hint;
    var hintCls = '';
    if (c.status === 'UPCOMING') hint = '모집 예정 공고예요. 모집이 마감된 후 당첨 결과를 산정할 수 있어요.';
    else if (c.status === 'OPEN') hint = '청약 모집이 마감된 후 당첨 결과를 산정할 수 있어요. (마감 ' + fmtD(c.applyEndDate) + ')';
    else if (pub) { hint = '발표 완료 · 회원이 결과를 확인할 수 있어요. 발표 후에는 재산정·수정할 수 없어요.'; hintCls = ' is-done'; }
    else if (c.computed) {
      hint = canAnnounce(c)
          ? '산정 결과를 검토한 뒤 발표해 주세요. 발표 전까지 회원 화면에는 공개되지 않아요.'
          : '산정 결과를 검토해 주세요. 당첨자 발표일(' + fmtD(c.announceDate) + ')부터 발표할 수 있어요.';
      hintCls = canAnnounce(c) ? ' is-todo' : '';
    } else { hint = '결과를 산정해야 발표할 수 있어요.'; hintCls = ' is-todo'; }
    card.appendChild(el('span', 'ah-hint' + hintCls, hint));

    var actions = el('div', 'ah-actions');
    var cBtn = button('ah-pill' + (c.computed ? ' ah-pill--done' : canCompute(c) ? ' ah-pill--line' : ''),
        pub ? '✓ 산정 완료' : c.computed ? '✓ 산정 완료 · 재산정 제한' : '당첨 결과 산정');
    cBtn.disabled = !canCompute(c);
    cBtn.addEventListener('click', function () { compute(c, cBtn); });
    actions.appendChild(cBtn);
    var pBtn = button('ah-pill' + (pub ? ' ah-pill--done' : canAnnounce(c) ? ' ah-pill--primary' : ''), pub ? '✓ 발표 완료' : '결과 발표');
    pBtn.disabled = !canAnnounce(c);
    pBtn.addEventListener('click', function () { announce(c, pBtn); });
    actions.appendChild(pBtn);
    card.appendChild(actions);
    return card;
  }

  function reviewBox(c, list, wins, loses, pub) {
    var box = el('div', 'ah-review');
    var head = el('div', 'ah-review__head');
    head.appendChild(el('b', null, pub ? '발표된 결과' : '결과 검토'));
    head.appendChild(el('span', null, '당첨 방식 · 가점 → 납입 회차 → 추첨'));
    box.appendChild(head);
    var stats = el('div', 'ah-review__stats');
    [['판정 대상 회원', list.length + '명', '#fff'], ['당첨', wins + '명', '#EAF7D8'], ['낙첨', loses + '명', '#E4E7E0']].forEach(function (k) {
      var d = el('div');
      d.style.background = k[2];
      d.appendChild(el('span', null, k[0]));
      d.appendChild(el('b', null, k[1]));
      stats.appendChild(d);
    });
    box.appendChild(stats);
    var ok = wins + loses === list.length;
    box.appendChild(el('span', 'ah-review__sum' + (ok ? '' : ' is-bad'), ok
        ? '✓ 당첨 + 낙첨 = 회원 신청 ' + list.length + '건'
        : '합계가 회원 신청 건수와 달라요. 산정 결과를 확인해 주세요.'));
    var cut = cutline(c);
    list.forEach(function (a) {
      var row = el('div', 'ah-review__row');
      row.appendChild(el('b', null, a.nickname));
      row.appendChild(el('span', null, '신청 당시 가점 ' + a.totalScore + '점 · 납입 ' + a.paidCount + '회 · 커트라인 ' + cut));
      var label = a.result === 'WIN' ? '당첨' : a.result === 'LOSE' ? '낙첨' : '-';
      var tag = el('span', 'ah-res', (a.resultRank != null ? a.resultRank + '위 · ' : '') + label);
      tag.style.background = a.result === 'WIN' ? '#9fe870' : a.result === 'LOSE' ? '#E4E7E0' : '#EEF1EA';
      row.appendChild(tag);
      box.appendChild(row);
    });
    return box;
  }

  function compute(c, btn) {
    var body = c.name + '\n공급 ' + fmt(c.supplyUnits) + '세대 · 회원 신청 ' + c.applicants + '건'
        + '\n가점 → 납입 회차 → 추첨 순으로 순위를 매겨 당첨자를 정해요.'
        + '\n한 번만 할 수 있고 되돌릴 수 없어요. 산정 뒤에는 일정·공급 세대 수를 바꿀 수 없어요.';
    confirmBox('당첨 결과를 산정할까요?', body, '산정').then(function (ok) {
      if (!ok) return null;
      btn.disabled = true;                              // 요청 중 연타 방지
      return MB.api(API + '/' + c.complexId + '/compute', { method: 'POST' }).then(function (r) {
        MB.toast('산정했어요 · 회원 신청 ' + r.applicants + '건 중 당첨 ' + r.winners + '명');
        return loadAll();
      });
    }).catch(function (err) {
      MB.toast(err.message, 'error');
      loadAll();                                        // 그사이 다른 관리자가 처리했으면 최신 상태로
    });
  }

  function announce(c, btn) {
    var body = c.name + '\n발표하면 회원 화면(내 신청 내역)에 당첨·낙첨 결과가 보이고, 신청 회원 '
        + c.applicants + '명에게 알림이 가요.\n한 번만 할 수 있고 되돌릴 수 없어요.';
    confirmBox('결과를 발표할까요?', body, '발표').then(function (ok) {
      if (!ok) return null;
      btn.disabled = true;
      return MB.api(API + '/' + c.complexId + '/announce', { method: 'POST' }).then(function (r) {
        MB.toast('결과를 발표했어요 · 알림 ' + r.notified + '명');
        return loadAll();
      });
    }).catch(function (err) {
      MB.toast(err.message, 'error');
      loadAll();
    });
  }

  // ---------- 시작 ----------
  fillRegionOptions();
  showView(location.hash.replace('#', '') || 'dash', { keepScroll: true });
  loadAll();
})();
