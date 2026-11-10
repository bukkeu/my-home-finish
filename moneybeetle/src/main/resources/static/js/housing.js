/* comment.
*  담당 : SCM 서수영 (#58, 모의 청약 화면)
*  templates/housing/housing.html 의 동작. 데이터는 모의 청약 API 로 불러온다 (MB.api, common.js)
*  - 청약통장 : GET /api/v1/housing/account (65), POST /account/deposits (66, Idempotency-Key), GET /account/deposits (67)
*  - 캘린더·모집 중 단지 : GET /api/v1/housing/complexes?month=yyyy-MM (63). Lv.12 미만이면 부르지 않고 흐린 예시만 그린다
*  - 탭 : 청약 홈 / 청약 단지 / 내 신청 내역 (주소 끝 #complexes, #history 로 바로 열 수 있다)
*  */
(function () {
  'use strict';

  var root = document.getElementById('hs');
  if (!root || !window.MB) return;

  var LOCKED = root.dataset.locked === 'true';
  var TODAY = parseDate(root.dataset.today);           // 서버(서울) 날짜
  var API = '/api/v1/housing';

  var state = {
    year: TODAY.y,
    month: TODAY.m,                                     // 1~12
    complexes: [],                                      // 이번에 보고 있는 달의 단지
    selected: null                                      // 선택한 날짜 'yyyy-MM-dd'
  };

  // ---------- 공통 도우미 ----------
  function parseDate(text) {
    var p = String(text || '').split('-');
    return { y: Number(p[0]), m: Number(p[1]), d: Number(p[2]) };
  }
  function iso(y, m, d) {
    return y + '-' + String(m).padStart(2, '0') + '-' + String(d).padStart(2, '0');
  }
  function shortDate(text) {                            // 2026-11-03 → 11.03
    var p = parseDate(text);
    return String(p.m).padStart(2, '0') + '.' + String(p.d).padStart(2, '0');
  }
  function fmt(n) {
    return Number(n || 0).toLocaleString('ko-KR');
  }
  function el(tag, cls, text) {
    var node = document.createElement(tag);
    if (cls) node.className = cls;
    if (text != null) node.textContent = text;
    return node;
  }
  function bind(name, value) {
    root.querySelectorAll('[data-bind="' + name + '"]').forEach(function (n) { n.textContent = value; });
  }
  function list(name) {
    return root.querySelector('[data-list="' + name + '"]');
  }
  function newKey() {                                   // 중복 요청 방지 키 (DP-37)
    if (window.crypto && crypto.randomUUID) return crypto.randomUUID();
    return 'hs-' + Date.now() + '-' + Math.random().toString(16).slice(2);
  }

  var STATUS_TEXT = { UPCOMING: '모집 예정', OPEN: '모집 중', CLOSED: '모집 마감', ANNOUNCED: '발표 완료' };
  function statusTag(status) {
    return el('span', 'hs-status is-' + status, STATUS_TEXT[status] || status);
  }

  // ---------- 탭 ----------
  var tabs = root.querySelectorAll('.hs-tab');
  function showTab(name) {
    tabs.forEach(function (t) {
      var on = t.dataset.tab === name;
      t.classList.toggle('is-active', on);
      t.setAttribute('aria-selected', on ? 'true' : 'false');
    });
    root.querySelectorAll('.hs-panel').forEach(function (p) { p.hidden = p.dataset.panel !== name; });
    if (history.replaceState) history.replaceState(null, '', name === 'home' ? location.pathname : '#' + name);
    if (window.HS && window.HS.onTab) window.HS.onTab(name);
  }
  tabs.forEach(function (t) { t.addEventListener('click', function () { showTab(t.dataset.tab); }); });
  root.addEventListener('click', function (e) {
    var go = e.target.closest('[data-go-tab]');
    if (go) showTab(go.dataset.goTab);
  });
  // 다른 단계(공고 상세)에서 쓰도록 열어 둔다
  window.HS = window.HS || {};
  window.HS.showTab = showTab;
  window.HS.openDetail = window.HS.openDetail || function () { showTab('complexes'); };

  // ---------- 청약통장 (API 65·67) ----------
  var form = document.getElementById('hs-deposit-form');
  var amountInput = form.querySelector('input[name="amount"]');
  var submitBtn = form.querySelector('[data-deposit-submit]');
  var msg = document.getElementById('hs-deposit-msg');
  var policy = { minAmount: 20000, maxAmount: 500000, dailyRecognizedLimit: 250000 };

  function loadAccount() {
    return MB.api(API + '/account').then(function (acc) {
      if (acc.depositPolicy) policy = acc.depositPolicy;
      bind('cash', fmt(acc.cash));
      bind('balance', fmt(acc.balance));
      bind('paidCount', fmt(acc.paidCount));
      bind('recognizedAmount', fmt(acc.recognizedAmount));
      bind('paidTodayText', acc.paidToday ? '오늘 납입 완료' : '오늘 납입할 수 있어요');
      bind('limitText', '하루 최대 ' + fmt(policy.dailyRecognizedLimit) + '까지 반영');
      bind('policyText', '하루 1회, 1회 ' + fmt(policy.minAmount) + ' ~ ' + fmt(policy.maxAmount) + ' 캐시');
      amountInput.min = policy.minAmount;
      amountInput.max = policy.maxAmount;
      setPaidToday(acc.paidToday);
    }).catch(function (err) {
      MB.toast(err.message, 'error');
    });
  }

  function setPaidToday(paid) {
    submitBtn.disabled = !!paid;
    amountInput.disabled = !!paid;
    submitBtn.textContent = paid ? '오늘은 이미 납입했어요' : '납입하기';
  }

  function loadDeposits() {
    var box = list('deposits');
    return MB.api(API + '/account/deposits?page=0&size=5').then(function (page) {
      box.replaceChildren();
      var items = (page && page.items) || [];
      if (!items.length) {
        box.appendChild(el('li', 'hs-empty', '아직 납입한 내역이 없어요.'));
        return;
      }
      items.forEach(function (d) {
        var li = el('li', 'hs-deposit-item');
        li.appendChild(el('span', 'hs-deposit-item__date', d.paidDate.replace(/-/g, '.')));
        li.appendChild(el('span', 'hs-deposit-item__amount', fmt(d.amount) + ' 캐시'));
        li.appendChild(el('span', 'hs-deposit-item__sub', '인정 ' + fmt(d.recognizedAmount) + ' · ' + d.paidCountAfter + '회차'));
        box.appendChild(li);
      });
    }).catch(function (err) {
      box.replaceChildren(el('li', 'hs-empty', err.message));
    });
  }

  // ---------- 납입 (API 66) ----------
  form.querySelectorAll('[data-amount]').forEach(function (chip) {
    chip.addEventListener('click', function () {
      amountInput.value = chip.dataset.amount;
      showMsg('');
    });
  });

  function showMsg(text) {
    msg.textContent = text;
    amountInput.closest('.hs-input').classList.toggle('is-error', !!text);
  }

  // 입력값만 보고 알 수 있는 오류는 보내기 전에 안내한다 (서버도 같은 규칙으로 다시 확인한다, SP-70)
  function checkAmount(value) {
    if (!value) return '납입할 금액을 입력해 주세요';
    if (!/^\d+$/.test(value)) return '금액은 숫자로만 입력해 주세요';
    var n = Number(value);
    if (n < policy.minAmount) return '최소 ' + fmt(policy.minAmount) + ' 캐시부터 납입할 수 있어요';
    if (n > policy.maxAmount) return '한 번에 최대 ' + fmt(policy.maxAmount) + ' 캐시까지 납입할 수 있어요';
    return '';
  }

  form.addEventListener('submit', function (e) {
    e.preventDefault();
    var value = amountInput.value.trim();
    var problem = checkAmount(value);
    showMsg(problem);
    if (problem) {
      amountInput.focus();
      return;
    }
    submitBtn.disabled = true;
    MB.api(API + '/account/deposits', {
      method: 'POST',
      headers: { 'Idempotency-Key': newKey() },
      body: { amount: Number(value) }
    }).then(function (r) {
      var extra = r.recognizedAmount < r.amount ? ' (인정 ' + fmt(r.recognizedAmount) + ')' : '';
      MB.toast(fmt(r.amount) + ' 캐시를 납입했어요' + extra + ' · ' + r.paidCount + '회차');
      amountInput.value = '';
      return Promise.all([loadAccount(), loadDeposits()]);
    }).catch(function (err) {
      showMsg(err.message);
      MB.toast(err.message, 'error');
      submitBtn.disabled = false;
    });
  });

  // ---------- 캘린더 · 모집 중 단지 (API 63) ----------
  var EVENTS = [
    { key: 'noticeDate', cls: 'is-notice', label: '청약 공고', short: '공고' },
    { key: 'applyEndDate', cls: 'is-close', label: '접수 마감', short: '마감' },
    { key: 'announceDate', cls: 'is-announce', label: '당첨자 발표', short: '발표' }
  ];

  function eventsOn(date) {
    var out = [];
    state.complexes.forEach(function (c) {
      EVENTS.forEach(function (ev) {
        if (c[ev.key] === date) out.push({ ev: ev, complex: c });
      });
    });
    return out;
  }

  function renderCalendar() {
    bind('calTitle', state.year + '년 ' + state.month + '월');
    var grid = list('calendar');
    grid.replaceChildren();
    var first = new Date(state.year, state.month - 1, 1);
    var startDay = new Date(state.year, state.month - 1, 1 - first.getDay());   // 그 주 일요일부터
    var todayIso = iso(TODAY.y, TODAY.m, TODAY.d);
    for (var i = 0; i < 42; i++) {
      var d = new Date(startDay.getFullYear(), startDay.getMonth(), startDay.getDate() + i);
      if (i >= 35 && d.getMonth() !== first.getMonth()) break;              // 6번째 주가 모두 다음 달이면 그리지 않는다
      var date = iso(d.getFullYear(), d.getMonth() + 1, d.getDate());
      var cell = el('button', 'hs-day');
      cell.type = 'button';
      cell.dataset.date = date;
      if (d.getMonth() !== first.getMonth()) cell.classList.add('is-other');
      if (date === todayIso) cell.classList.add('is-today');
      if (date === state.selected) cell.classList.add('is-selected');
      cell.appendChild(el('span', 'hs-day__num', String(d.getDate())));
      var evs = eventsOn(date);
      evs.slice(0, 3).forEach(function (x) {
        var chip = el('span', 'hs-ev ' + x.ev.cls);
        chip.appendChild(el('i'));
        chip.appendChild(el('span', null, x.ev.short + ' ' + x.complex.name));
        cell.appendChild(chip);
      });
      if (evs.length > 3) cell.appendChild(el('span', 'hs-muted hs-small', '+' + (evs.length - 3)));
      cell.setAttribute('aria-label', date + (evs.length ? ' 일정 ' + evs.length + '개' : ' 일정 없음'));
      grid.appendChild(cell);
    }
    renderSelected();
  }

  function renderSelected() {
    var box = list('selEvents');
    box.replaceChildren();
    if (!state.selected) {
      bind('selDateTitle', '날짜를 눌러 일정을 확인해 보세요');
      return;
    }
    var p = parseDate(state.selected);
    bind('selDateTitle', p.m + '월 ' + p.d + '일 일정');
    var evs = eventsOn(state.selected);
    if (!evs.length) {
      box.appendChild(el('p', 'hs-muted', '이 날짜에는 일정이 없어요. 색이 표시된 날짜를 눌러 보세요.'));
      return;
    }
    evs.forEach(function (x) {
      var row = el('div', 'hs-sel-ev');
      row.appendChild(el('span', 'hs-sel-ev__kind ' + x.ev.cls, x.ev.label));
      var who = el('div', 'hs-sel-ev__who');
      who.appendChild(el('strong', null, x.complex.name));
      who.appendChild(el('span', null, '접수 ' + shortDate(x.complex.applyStartDate) + ' ~ ' + shortDate(x.complex.applyEndDate)
          + ' · 발표 ' + shortDate(x.complex.announceDate)));
      row.appendChild(who);
      row.appendChild(statusTag(x.complex.status));
      row.appendChild(detailButton(x.complex.complexId));
      box.appendChild(row);
    });
  }

  function detailButton(id) {
    var btn = el('button', 'btn btn-secondary', '상세보기');
    btn.type = 'button';
    btn.addEventListener('click', function () { window.HS.openDetail(id); });
    return btn;
  }

  function renderOpenComplexes(items) {
    var box = list('openComplexes');
    box.replaceChildren();
    if (!items.length) {
      var empty = el('div', 'card hs-empty-card');
      empty.appendChild(el('strong', null, '지금 모집 중인 단지가 없어요'));
      empty.appendChild(el('p', 'hs-muted', '청약 캘린더에서 다음 공고 일정을 확인해 보세요.'));
      box.appendChild(empty);
      return;
    }
    items.forEach(function (c) { box.appendChild(complexCard(c, false)); });
  }

  // 단지 카드 (모집 중 단지·청약 단지 목록이 같이 쓴다). withApply 면 신청 버튼도 단다
  function complexCard(c, withApply) {
    var card = el('div', 'card hs-cx');
    var img = el('div', 'hs-cx__img', '단지 이미지');
    img.appendChild(statusTag(c.status));
    if (c.applied) img.appendChild(el('span', 'hs-applied', '신청 완료'));
    card.appendChild(img);
    var body = el('div', 'hs-cx__body');
    body.appendChild(el('span', 'hs-cx__name', c.name));
    var rows = el('dl', 'hs-cx__rows');
    [['접수 기간', shortDate(c.applyStartDate) + ' ~ ' + shortDate(c.applyEndDate)],
      ['접수 마감', dotted(c.applyEndDate)],
      ['당첨자 발표', dotted(c.announceDate)]].forEach(function (r) {
      rows.appendChild(el('dt', null, r[0]));
      rows.appendChild(el('dd', null, r[1]));
    });
    body.appendChild(rows);
    var actions = el('div', 'hs-cx__actions');
    actions.appendChild(detailButton(c.complexId));
    if (withApply) {
      // 신청 기간(모집 중)이고 아직 신청하지 않았을 때만 누를 수 있다 (SP-73, #58 완료 조건)
      var apply = el('button', 'btn btn-primary', applyLabel(c.status, c.applied));
      apply.type = 'button';
      apply.disabled = !(c.status === 'OPEN' && !c.applied);
      apply.addEventListener('click', function () { window.HS.openDetail(c.complexId, true); });
      actions.appendChild(apply);
    }
    body.appendChild(actions);
    card.appendChild(body);
    return card;
  }

  function dotted(text) {
    return String(text || '').replace(/-/g, '.');
  }

  function applyLabel(status, applied) {
    if (applied) return '신청 완료';
    if (status === 'OPEN') return '신청하기';
    if (status === 'UPCOMING') return '접수 전이에요';
    return '접수 마감';
  }

  function loadMonth() {
    if (LOCKED) {                                       // 레벨이 모자라면 API 를 부르지 않는다 (서버도 LEVEL_LOCKED 로 막는다)
      state.complexes = [];
      renderCalendar();
      return;
    }
    var month = state.year + '-' + String(state.month).padStart(2, '0');
    MB.api(API + '/complexes?month=' + month).then(function (data) {
      state.complexes = (data && data.items) || [];
      renderCalendar();
      if (state.year === TODAY.y && state.month === TODAY.m) {
        renderOpenComplexes(state.complexes.filter(function (c) { return c.status === 'OPEN'; }));
      }
    }).catch(function (err) {
      state.complexes = [];
      renderCalendar();
      MB.toast(err.message, 'error');
    });
  }

  root.querySelectorAll('[data-cal]').forEach(function (btn) {
    btn.addEventListener('click', function () {
      state.month += btn.dataset.cal === 'next' ? 1 : -1;
      if (state.month === 0) { state.month = 12; state.year -= 1; }
      if (state.month === 13) { state.month = 1; state.year += 1; }
      state.selected = null;
      loadMonth();
    });
  });
  list('calendar').addEventListener('click', function (e) {
    var cell = e.target.closest('.hs-day');
    if (!cell) return;
    state.selected = cell.dataset.date;
    renderCalendar();
  });

  // 레벨이 모자랄 때 흐리게 보여 줄 예시 카드 (실제 데이터가 아니다)
  function renderLockedSamples() {
    var sample = [1, 2, 3].map(function (i) {
      return {
        complexId: 0, name: '가상 단지 ' + i, status: 'OPEN', applied: false,
        applyStartDate: iso(TODAY.y, TODAY.m, 1), applyEndDate: iso(TODAY.y, TODAY.m, 10),
        announceDate: iso(TODAY.y, TODAY.m, 17)
      };
    });
    renderOpenComplexes(sample);
  }

  // ---------- 청약 단지 : 목록(63) → 상세(64) → 가점 계산(68) → 신청(69) ----------
  var panel = root.querySelector('[data-panel="complexes"]');
  var cx = { year: TODAY.y, month: TODAY.m, items: [], loaded: false, status: '', detail: null, input: null };

  function view(name) {
    panel.querySelectorAll('[data-view]').forEach(function (v) { v.hidden = v.dataset.view !== name; });
  }
  function setD(name, text) {
    panel.querySelectorAll('[data-d="' + name + '"]').forEach(function (n) { n.textContent = text; });
  }
  function won(n) {                                      // 320000000 → 3억 2,000만 원
    var eok = Math.floor(n / 100000000);
    var man = Math.floor((n % 100000000) / 10000);
    return (eok ? eok + '억 ' : '') + (man ? fmt(man) + '만 ' : '') + '원';
  }

  // 탭으로 들어오면 목록부터 (Lv.12 미만이면 잠금 안내만 있다)
  window.HS.onTab = function (name) {
    if (name !== 'complexes' || LOCKED) return;
    view('list');
    if (!cx.loaded) loadList();
  };

  function loadList() {
    bind('listTitle', cx.year + '년 ' + cx.month + '월');
    var month = cx.year + '-' + String(cx.month).padStart(2, '0');
    return MB.api(API + '/complexes?month=' + month).then(function (data) {
      cx.items = (data && data.items) || [];
      cx.loaded = true;
      renderList();
    }).catch(function (err) {
      cx.items = [];
      renderList();
      MB.toast(err.message, 'error');
    });
  }

  function renderList() {
    var items = cx.items.filter(function (c) { return !cx.status || c.status === cx.status; });
    bind('listCount', String(items.length));
    var box = list('complexList');
    box.replaceChildren();
    if (!items.length) {
      var empty = el('div', 'card hs-empty-card');
      empty.appendChild(el('strong', null, '조건에 맞는 단지가 없어요'));
      empty.appendChild(el('p', 'hs-muted', '다른 달이나 모집 상태를 골라 보세요.'));
      box.appendChild(empty);
      return;
    }
    items.forEach(function (c) { box.appendChild(complexCard(c, true)); });
  }

  if (!LOCKED) {
    panel.querySelector('[data-filter="status"]').addEventListener('change', function (e) {
      cx.status = e.target.value;
      renderList();
    });
    panel.querySelectorAll('[data-list-month]').forEach(function (btn) {
      btn.addEventListener('click', function () {
        cx.month += btn.dataset.listMonth === 'next' ? 1 : -1;
        if (cx.month === 0) { cx.month = 12; cx.year -= 1; }
        if (cx.month === 13) { cx.month = 1; cx.year += 1; }
        loadList();
      });
    });
    panel.addEventListener('click', function (e) {
      var back = e.target.closest('[data-back]');
      if (!back) return;
      if (back.dataset.back === 'detail' && cx.detail) openDetail(cx.detail.complexId);
      else { view('list'); if (!cx.loaded) loadList(); }
    });
  }

  // ----- 상세 (API 64) -----
  function canApply(c) {
    return c.status === 'OPEN' && !c.myApplication;
  }

  function openDetail(id, thenApply) {
    if (LOCKED || !id) { showTab('complexes'); return; }
    showTab('complexes');
    MB.api(API + '/complexes/' + id).then(function (c) {
      cx.detail = c;
      renderDetail(c);
      if (thenApply && canApply(c)) openApply();
      else view('detail');
      root.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }).catch(function (err) {
      MB.toast(err.message, 'error');
    });
  }
  window.HS.openDetail = openDetail;

  function renderDetail(c) {
    panel.querySelector('[data-d="statusTag"]').replaceChildren(statusTag(c.status));
    setD('name', c.name);
    setD('location', c.location);
    setD('supplyType', c.supplyType);
    setD('areaType', c.areaType);
    setD('salePrice', won(c.salePrice));
    setD('supplyUnits', fmt(c.supplyUnits) + '세대');
    setD('applyPeriod', dotted(c.applyStartDate) + ' ~ ' + dotted(c.applyEndDate));
    setD('announceDate', dotted(c.announceDate));
    // 경쟁률은 접수가 끝난 뒤에만 온다 (SP-74). 신청자 ÷ 공급 세대
    setD('competition', c.competitionRate == null ? '접수 마감 후 공개' : c.competitionRate.toFixed(2) + ' : 1');

    var checks = list('checks');
    checks.replaceChildren();
    var periodReason = c.status === 'UPCOMING'
        ? '접수는 ' + dotted(c.applyStartDate) + '부터예요'
        : '접수가 ' + dotted(c.applyEndDate) + '에 끝났어요';
    [{ ok: true, label: 'Lv.' + root.dataset.unlockLevel + ' 이상' },
      { ok: c.status === 'OPEN', label: c.status === 'OPEN' ? '신청 기간이에요 (~' + dotted(c.applyEndDate) + ')' : '신청 기간', reason: periodReason },
      { ok: !c.myApplication, label: '이 단지 첫 신청', reason: '이미 신청한 단지예요. 단지마다 한 번만 신청할 수 있어요' }
    ].forEach(function (ck) {
      var li = el('li', 'hs-check' + (ck.ok ? '' : ' is-bad'));
      li.appendChild(el('span', 'hs-check__icon', ck.ok ? '✓' : '!'));
      var text = el('span', 'hs-check__text');
      text.appendChild(el('span', null, ck.label));
      if (!ck.ok && ck.reason) text.appendChild(el('span', 'hs-check__reason', ck.reason));
      li.appendChild(text);
      checks.appendChild(li);
    });

    var note = panel.querySelector('[data-d="appliedNote"]');
    var historyBtn = panel.querySelector('[data-d="historyBtn"]');
    var mine = c.myApplication;
    note.hidden = !mine;
    historyBtn.hidden = !mine;
    if (mine) note.textContent = '이미 신청했어요 · 접수번호 ' + mine.receiptNo + ' · 신청 가점 ' + mine.totalScore + '점';

    var start = panel.querySelector('[data-start-apply]');
    start.disabled = !canApply(c);
    start.textContent = mine ? '이미 신청한 단지예요' : c.status === 'OPEN' ? '가점 계산하고 신청하기' : '지금은 신청 기간이 아니에요';
  }

  if (!LOCKED) {
    panel.querySelector('[data-start-apply]').addEventListener('click', function () {
      if (cx.detail && canApply(cx.detail)) openApply();
    });
  }

  // ----- 가점 계산 (API 68) -----
  var scoreForm = document.getElementById('hs-score-form');

  function openApply() {
    var c = cx.detail;
    bind('applyFor', c.name + ' · 접수 ~' + dotted(c.applyEndDate) + ' · 발표 ' + dotted(c.announceDate));
    resetScore();
    view('apply');
  }

  function resetScore() {
    cx.input = null;
    panel.querySelector('[data-score]').hidden = true;
    panel.querySelector('[data-score-empty]').hidden = false;
  }

  function fieldMsg(name, text) {
    var m = scoreForm.querySelector('[data-msg="' + name + '"]');
    m.textContent = text || '';
    scoreForm.querySelector('input[name="' + name + '"]').closest('.hs-input').classList.toggle('is-error', !!text);
  }

  // 입력값만 보고 알 수 있는 범위 (SP-71: 무주택 0~50년, 부양가족 0~20명). 서버도 같은 규칙으로 다시 확인한다
  function readScoreInput() {
    var ok = true;
    var out = {};
    [['homelessYears', 50, '무주택 기간은 0~50년으로 입력해 주세요'], ['dependents', 20, '부양가족 수는 0~20명으로 입력해 주세요']]
        .forEach(function (f) {
          var raw = scoreForm.querySelector('input[name="' + f[0] + '"]').value.trim();
          var valid = /^\d+$/.test(raw) && Number(raw) <= f[1];
          fieldMsg(f[0], valid ? '' : f[2]);
          if (!valid) ok = false;
          out[f[0]] = Number(raw);
        });
    return ok ? out : null;
  }

  function renderScore(s) {
    panel.querySelector('[data-score-empty]').hidden = true;
    panel.querySelector('[data-score]').hidden = false;
    ['totalScore', 'maxScore', 'homelessScore', 'dependentScore', 'accountScore'].forEach(function (k) {
      panel.querySelector('[data-s="' + k + '"]').textContent = s[k];
    });
    panel.querySelector('[data-s="baseDate"]').textContent = dotted(s.baseDate);
    [['homeless', s.homelessScore, 32], ['dependent', s.dependentScore, 35], ['account', s.accountScore, 17]]
        .forEach(function (b) {
          panel.querySelector('[data-bar="' + b[0] + '"]').style.width = Math.round(b[1] / b[2] * 100) + '%';
        });
  }

  if (!LOCKED) {
    scoreForm.addEventListener('input', resetScore);          // 값을 바꾸면 다시 계산해야 신청할 수 있다
    scoreForm.addEventListener('submit', function (e) {
      e.preventDefault();
      var input = readScoreInput();
      if (!input) return;
      MB.api(API + '/score-preview', { method: 'POST', body: input }).then(function (s) {
        cx.input = input;
        renderScore(s);
      }).catch(function (err) {
        MB.toast(err.message, 'error');
      });
    });

    // ----- 신청 (API 69). 신청 뒤에는 취소·수정할 수 없다 (SP-73) -----
    var applyBtn = panel.querySelector('[data-apply]');
    applyBtn.addEventListener('click', function () {
      var c = cx.detail;
      if (!c || !cx.input) return;
      applyBtn.disabled = true;
      MB.api(API + '/complexes/' + c.complexId + '/applications', { method: 'POST', body: cx.input })
          .then(function (r) {
            bind('doneName', c.name);
            bind('doneReceipt', r.receiptNo);
            bind('doneScore', String(r.totalScore));
            bind('doneAnnounce', dotted(c.announceDate));
            view('done');
            MB.toast('청약 신청이 완료됐어요 · 접수번호 ' + r.receiptNo);
            cx.loaded = false;                                  // 목록·캘린더의 "신청 완료" 표시를 새로 받는다
            loadMonth();
          })
          .catch(function (err) {
            MB.toast(err.message, 'error');
            // 그사이 기간이 끝났거나 이미 신청했으면 상세를 다시 보여 준다
            if (err.code === 'NOT_IN_APPLY_PERIOD' || err.code === 'ALREADY_APPLIED') openDetail(c.complexId);
          })
          .finally(function () { applyBtn.disabled = false; });
    });
  }

  // ---------- 시작 ----------
  loadAccount();
  loadDeposits();
  if (LOCKED) renderLockedSamples();
  loadMonth();
  var startTab = location.hash.replace('#', '');           // 주소 끝 #complexes, #history 로 바로 열기 (알림 링크 등)
  if (startTab === 'complexes' || startTab === 'history') showTab(startTab);
})();
