/* comment.
*   담당 : PM (#62, 관리자 모의투자 운영 화면)
*   templates/admin/trading.html 의 탭별 데이터를 관리자 API(API 명세서 114~141)로 불러오고 저장한다.
*   - 요청은 common.js 의 MB.api(CSRF 헤더·{success,data,error} 처리)를 쓴다.
*     CSV 내려받기·올리기만 파일·오류 행(data.errors)을 다뤄야 해서 fetch 를 직접 쓴다.
*   - 서버 문구는 textContent 로만 넣는다 (HTML 로 해석하지 않는다).
*   - 실패하면 영역마다 error.message(API 명세서 4장 문구)를 보여 주고, 화면 전체는 멈추지 않는다.
*  */
(function () {
  'use strict';
  var root = document.getElementById('at-root');
  if (!root || !window.MB || !MB.api) return;

  var BASE = '/api/v1/admin/trading';
  var TURNS = 12;
  var STATUS = [['READY', '준비'], ['RUNNING', '진행 중'], ['HOLIDAY', '휴장'], ['SETTLING', '정산'], ['ENDED', '종료']];
  var STATUS_TONE = { READY: 'soft', RUNNING: 'mint', HOLIDAY: 'gray', SETTLING: 'amber', ENDED: 'dark' };
  // SP-65: READY → RUNNING ⇄ HOLIDAY → SETTLING → ENDED (API 117)
  var NEXT_STATUS = {
    READY: [['RUNNING', '진행 시작']],
    RUNNING: [['HOLIDAY', '휴장으로 바꾸기'], ['SETTLING', '정산 시작']],
    HOLIDAY: [['RUNNING', '휴장 끝내기'], ['SETTLING', '정산 시작']],
    SETTLING: [['ENDED', '시즌 종료']],
    ENDED: []
  };
  var TABS = ['season', 'turn', 'stocks', 'prices', 'news', 'people', 'ranking'];

  var state = {
    seasons: [], season: null, tab: 'season',
    sectors: [], stocks: [], marketLoaded: false,
    prices: null, edits: {}, mode: 'price',
    news: [], newsTurn: null, newsEdit: null, newsSel: { sectorIds: [], stockIds: [] },
    stockEditId: null, rankType: 'ASSET'
  };

  /* ---------- 작은 도구 ---------- */
  function $(id) { return document.getElementById(id); }
  function el(tag, attrs, children) {
    var node = document.createElement(tag);
    Object.keys(attrs || {}).forEach(function (k) {
      var v = attrs[k];
      if (v == null || v === false) return;
      if (k === 'text') node.textContent = v;
      else if (k === 'class') node.className = v;
      else if (k.indexOf('on') === 0) node.addEventListener(k.slice(2), v);
      else node.setAttribute(k, v === true ? '' : v);
    });
    (children || []).forEach(function (c) { if (c) node.appendChild(typeof c === 'string' ? document.createTextNode(c) : c); });
    return node;
  }
  function fill(node, children) { node.textContent = ''; children.forEach(function (c) { if (c) node.appendChild(c); }); }
  function items(data) { return Array.isArray(data) ? data : (data && data.items) || []; }
  function fmt(n) { return n == null || n === '' ? '-' : Number(n).toLocaleString('ko-KR'); }
  function pct(n) { return n == null ? '-' : (n > 0 ? '+' : '') + Number(n).toFixed(2) + '%'; }
  function tone(n) { return n == null ? '' : n >= 0 ? 'up' : 'down'; }
  function statusLabel(code) { var s = STATUS.filter(function (x) { return x[0] === code; })[0]; return s ? s[1] : (code || '-'); }
  function pill(text, t) { return el('span', { class: 'at-pill ' + (t || 'soft'), text: text }); }
  function dateTime(iso) { return iso ? String(iso).replace(/^\d{4}-(\d{2})-(\d{2})T(\d{2}:\d{2}).*$/, '$1.$2 $3') : '-'; }
  function api(path, opts) { return MB.api(BASE + path, opts); }
  function seasonPath(suffix) { return '/seasons/' + state.season.seasonId + suffix; }
  // 시즌별 요청의 순번. 시즌을 A -> B -> A 로 바꿔도 먼저 보낸 요청의 늦은 응답이 지금 화면을 덮어쓰지 않게 한다
  var req = { turn: 0, prices: 0, news: 0, people: 0, stats: 0, rank: 0 };
  var personReq = 0;   // 참가자 상세 요청 순번: 가장 나중에 고른 참가자의 응답만 그린다
  function begin(key) { return { token: ++req[key], seasonId: state.season ? state.season.seasonId : null }; }
  function stale(key, t) { return t.token !== req[key] || !state.season || state.season.seasonId !== t.seasonId; }
  function errText(err) {
    if (err && err.fields && err.fields.length) return err.fields[0].message || err.message;
    return (err && err.message) || '요청을 처리하지 못했어요.';
  }
  function toastError(err) { MB.toast(errText(err), 'error'); }
  function emptyRow(tbody, colspan, text, isError) {
    fill(tbody, [el('tr', {}, [el('td', { colspan: colspan, class: 'at-empty' + (isError ? ' error' : ''), text: text })])]);
  }
  function emptyBox(box, text, isError) { fill(box, [el('div', { class: 'at-empty' + (isError ? ' error' : ''), text: text })]); }
  function formError(form, msg) {
    var e = form.querySelector('[data-at-error]');
    e.textContent = msg || '';
    e.hidden = !msg;
  }
  /** 요청하는 동안 버튼을 잠가 두 번 눌리지 않게 한다 */
  function busy(btn, promise) {
    if (btn) btn.disabled = true;
    return promise.then(function (v) { if (btn) btn.disabled = false; return v; },
      function (e) { if (btn) btn.disabled = false; throw e; });
  }
  function noSeasonText() {
    if (state.seasonsError) return '시즌을 불러오지 못해서 보여 줄 수 없어요';
    return state.seasons.length ? '시즌을 골라 주세요' : '먼저 시즌 관리에서 시즌을 만들어 주세요';
  }

  /* ---------- 확인창 ---------- */
  var modal = $('at-modal');
  var modalOk = modal.querySelector('[data-at-modal="ok"]');
  var modalResolve = null;
  function confirmModal(o) {
    $('at-modal-title').textContent = o.title;
    $('at-modal-body').textContent = o.body || '';
    var list = $('at-modal-list');
    fill(list, (o.list || []).map(function (t) { return el('li', { text: t }); }));
    list.hidden = !(o.list && o.list.length);
    modalOk.textContent = o.ok || '확인';
    modalOk.className = 'at-btn ' + (o.danger ? 'danger' : 'primary');
    modal.hidden = false;
    modalOk.focus();
    return new Promise(function (resolve) { modalResolve = resolve; });
  }
  function closeModal(value) {
    modal.hidden = true;
    var r = modalResolve;
    modalResolve = null;
    if (r) r(value);
  }
  modal.addEventListener('click', function (e) {
    var b = e.target.closest('[data-at-modal]');
    if (b) closeModal(b.getAttribute('data-at-modal') === 'ok');
    else if (e.target === modal) closeModal(false);
  });
  document.addEventListener('keydown', function (e) { if (e.key === 'Escape' && !modal.hidden) closeModal(false); });

  /* ---------- 14일 달력 (월요일 시작, 7일·14일째 일요일은 휴장) ---------- */
  function isoOf(d) { return d.getFullYear() + '-' + String(d.getMonth() + 1).padStart(2, '0') + '-' + String(d.getDate()).padStart(2, '0'); }
  function buildDays(start) {
    var out = [], t = 0;
    for (var i = 0; i < 14; i++) {
      var d = new Date(start + 'T00:00:00');
      d.setDate(d.getDate() + i);
      var sunday = i === 6 || i === 13;
      if (!sunday) t += 1;
      out.push({ iso: isoOf(d), label: (d.getMonth() + 1) + '.' + String(d.getDate()).padStart(2, '0'), dow: '월화수목금토일'.charAt(i % 7),
        turn: sunday ? null : t, kind: i === 13 ? '랭킹 공개일' : i === 6 ? '휴장' : t + '턴' });
    }
    return out;
  }
  function renderCal(box, days, classOf) {
    fill(box, days.map(function (d) {
      return el('div', { class: 'at-cal-cell ' + classOf(d) }, [el('small', { text: d.dow + ' ' + d.label }), el('strong', { text: d.kind })]);
    }));
  }

  /* ---------- 탭 ---------- */
  var tabButtons = root.querySelectorAll('[data-at-tab]');
  function openTab(tab) {
    state.tab = tab;
    tabButtons.forEach(function (b) { b.setAttribute('aria-selected', String(b.getAttribute('data-at-tab') === tab)); });
    root.querySelectorAll('[data-at-panel]').forEach(function (p) { p.hidden = p.getAttribute('data-at-panel') !== tab; });
    try { history.replaceState(null, '', '#' + tab); } catch (e) { /* 주소 표시만 못 바꾼다 */ }
    loadTab();
  }
  function loadTab() {
    var loaders = { season: renderSeasonTab, turn: loadTurn, stocks: loadStocksTab, prices: loadPrices, news: loadNews, people: loadPeople, ranking: loadRanking };
    loaders[state.tab]();
  }
  tabButtons.forEach(function (b) { b.addEventListener('click', function () { openTab(b.getAttribute('data-at-tab')); }); });

  /* =====================================================================
   * 시즌 (API 114~117)
   * ===================================================================== */
  var seasonSelect = $('at-season-select');

  function pickSeason(list, wantedId) {
    var byId = function (id) { return list.filter(function (s) { return String(s.seasonId) === String(id); })[0]; };
    var latest = list.slice().sort(function (a, b) { return b.seasonId - a.seasonId; });
    return byId(wantedId) || (state.season && byId(state.season.seasonId))
      || latest.filter(function (s) { return ['RUNNING', 'HOLIDAY', 'SETTLING'].indexOf(s.status) >= 0; })[0]
      || latest.filter(function (s) { return s.status === 'READY'; })[0] || latest[0] || null;
  }

  function loadSeasons(wantedId) {
    return api('/seasons').then(function (data) {
      state.seasonsError = false;
      state.seasons = items(data);
      state.season = pickSeason(state.seasons, wantedId);
      renderSeasonSelect();
    }, function (err) {
      state.seasonsError = true;
      state.seasons = [];
      state.season = null;
      fill(seasonSelect, [el('option', { value: '', text: '시즌을 불러오지 못했어요' })]);
      emptyRow($('at-season-rows'), 5, errText(err), true);
    }).then(renderHeader);
  }

  function renderSeasonSelect() {
    seasonSelect.disabled = !state.seasons.length;
    if (!state.seasons.length) {
      fill(seasonSelect, [el('option', { value: '', text: '아직 시즌이 없어요' })]);
    } else {
      fill(seasonSelect, state.seasons.map(function (s) {
        return el('option', { value: s.seasonId, text: s.name + ' · ' + statusLabel(s.status), selected: state.season && s.seasonId === state.season.seasonId });
      }));
    }
    var copy = $('at-copy-select');
    fill(copy, [copy.options[0]].concat(state.seasons.map(function (s) { return el('option', { value: s.seasonId, text: s.name }); })));
  }

  function renderHeader() {
    var s = state.season;
    var st = $('at-season-status');
    st.textContent = s ? statusLabel(s.status) : '-';
    st.className = 'at-pill ' + (s ? STATUS_TONE[s.status] || 'gray' : 'gray');
    $('at-season-turn').textContent = (s ? s.currentTurn : '-') + ' / ' + TURNS + '턴';
  }

  seasonSelect.addEventListener('change', function () {
    var wanted = seasonSelect.value;
    var previous = state.season ? String(state.season.seasonId) : '';
    // 저장하지 않은 가격이 있으면 시즌을 바꾸기 전에 묻는다 (바꾸면 입력한 값이 사라진다)
    var go = Object.keys(state.edits).length
      ? confirmModal({ title: '저장하지 않은 가격이 있어요', body: '시즌을 바꾸면 입력한 가격이 사라져요.', ok: '시즌 바꾸기', danger: true })
      : Promise.resolve(true);
    go.then(function (ok) {
      if (!ok) { seasonSelect.value = previous; return; }
      state.season = pickSeason(state.seasons, wanted);
      state.edits = {};
      state.prices = null;   // 새 가격표를 받기 전에는 이전 시즌 가격표로 저장 버튼이 켜져 있지 않게 한다
      updatePriceStatus();
      state.newsTurn = null;
      closeNewsForm();
      renderHeader();
      loadTab();
    });
  });

  function renderSeasonTab() {
    var s = state.season;
    // 시즌 목록
    var rows = $('at-season-rows');
    if (state.seasons.length) {
      fill(rows, state.seasons.map(function (x) {
        return el('tr', { class: s && x.seasonId === s.seasonId ? 'hl' : '' }, [
          el('td', {}, [el('strong', { text: x.name })]), el('td', { text: x.startDate || '-' }),
          el('td', {}, [pill(statusLabel(x.status), STATUS_TONE[x.status])]),
          el('td', { text: x.currentTurn + ' / ' + TURNS }), el('td', { text: fmt(x.participants) + '명' })
        ]);
      }));
    } else if (!rows.querySelector('.error')) {
      emptyRow(rows, 5, '아직 만든 시즌이 없어요. 새 시즌을 만들어 주세요');
    }
    // 상태 단계와 바꿀 수 있는 다음 상태
    var cur = s ? STATUS.map(function (x) { return x[0]; }).indexOf(s.status) : -1;
    fill($('at-status-steps'), STATUS.map(function (x, i) {
      return el('div', { class: 'at-step' + (i < cur ? ' done' : i === cur ? ' on' : '') }, [el('b', { text: String(i + 1) }), el('span', { text: x[1] })]);
    }));
    var actions = $('at-status-actions');
    if (!s) {
      fill(actions, [el('span', { class: 'at-muted', text: noSeasonText() })]);
    } else {
      var next = NEXT_STATUS[s.status] || [];
      fill(actions, next.length ? next.map(function (n) {
        return el('button', { type: 'button', class: 'at-btn ' + (n[0] === 'SETTLING' ? 'soft' : 'primary'), text: n[1], onclick: function (e) { changeStatus(n[0], e.currentTarget); } });
      }) : [el('span', { class: 'at-muted', text: '마지막 상태예요' })]);
    }
    // 설정 폼: 시즌 목록 응답에 설정값이 있으면 채운다 (없으면 비워 두고 안내 문구를 placeholder 로 보여 준다)
    var f = $('at-settings-form');
    f.carryoverRate.value = s && s.carryoverRate != null ? s.carryoverRate : '';
    f.carryoverCap.value = s && s.carryoverCap != null ? s.carryoverCap : '';
    f.unlockLevel.value = s && s.unlockLevel != null ? s.unlockLevel : '';
    f.querySelector('[type=submit]').disabled = !s || s.status === 'SETTLING' || s.status === 'ENDED';
    formError(f, '');
  }

  function changeStatus(to, btn) {
    var s = state.season;
    confirmModal({ title: '시즌 상태를 ‘' + statusLabel(to) + '’(으)로 바꿀까요?', body: '시즌 상태는 정해진 순서로만 바뀌고 거꾸로 돌아갈 수 없어요.', ok: '변경하기' })
      .then(function (ok) {
        if (!ok) return;
        return busy(btn, api(seasonPath('/status'), { method: 'PATCH', body: { status: to } })).then(function () {
          MB.toast('시즌 상태를 ‘' + statusLabel(to) + '’(으)로 바꿨어요');
          return loadSeasons(s.seasonId).then(renderSeasonTab);
        }, toastError);
      });
  }

  $('at-settings-form').addEventListener('submit', function (e) {
    e.preventDefault();
    var f = e.currentTarget;
    if (!state.season) return formError(f, noSeasonText());
    var rate = f.carryoverRate.value.trim(), cap = f.carryoverCap.value.trim(), level = f.unlockLevel.value.trim();
    if (!/^\d{1,3}(\.\d{1,2})?$/.test(rate) || Number(rate) > 100) return formError(f, '이월 비율은 0~100 사이, 소수 둘째 자리까지 입력해 주세요');
    if (cap !== '' && !/^\d+$/.test(cap)) return formError(f, '이월 상한은 0 이상의 정수로 입력하거나 비워 두세요');
    if (!/^\d+$/.test(level) || Number(level) < 1 || Number(level) > 20) return formError(f, '참여 해금 레벨은 1~20 사이로 입력해 주세요');
    formError(f, '');
    var body = { carryoverRate: Number(rate), carryoverCap: cap === '' ? null : Number(cap), unlockLevel: Number(level) };
    var target = state.season;   // 요청을 보낸 시즌 (저장하는 사이 다른 시즌으로 바꿔도 이 시즌에 반영한다)
    busy(f.querySelector('[type=submit]'), api(seasonPath('/settings'), { method: 'PATCH', body: body })).then(function () {
      // 목록 응답에 설정값이 없어서, 탭을 이동했다 돌아오면 폼이 비지 않게 저장한 값을 시즌에 담아 둔다
      target.carryoverRate = body.carryoverRate;
      target.carryoverCap = body.carryoverCap;
      target.unlockLevel = body.unlockLevel;
      MB.toast('시즌 설정을 저장했어요');
    }, function (err) { formError(f, errText(err)); });
  });

  function isMonday(v) { return !!v && new Date(v + 'T00:00:00').getDay() === 1; }
  var createForm = $('at-create-form');
  createForm.startDate.addEventListener('input', function () {
    var v = createForm.startDate.value, ok = isMonday(v), cal = $('at-create-cal');
    $('at-monday-error').hidden = !v || ok;
    cal.hidden = !ok;
    if (ok) renderCal(cal, buildDays(v), function (d) { return d.turn == null ? 'off' : 'future'; });
  });
  createForm.addEventListener('submit', function (e) {
    e.preventDefault();
    var name = createForm.elements.name.value.trim(), start = createForm.startDate.value, copy = createForm.copyFromSeasonId.value;
    if (!name) return formError(createForm, '시즌 이름을 입력해 주세요');
    if (!isMonday(start)) return formError(createForm, '시즌 시작일은 월요일이어야 해요');
    formError(createForm, '');
    var body = { name: name, startDate: start, copyFromSeasonId: copy ? Number(copy) : null };
    busy(createForm.querySelector('[type=submit]'), api('/seasons', { method: 'POST', body: body })).then(function (data) {
      MB.toast(name + '을(를) 만들었어요. 가격과 뉴스를 입력해 주세요');
      createForm.reset();
      $('at-create-cal').hidden = true;
      return loadSeasons(data && data.seasonId).then(renderSeasonTab);
    }, function (err) { formError(createForm, errText(err)); });
  });

  /* =====================================================================
   * 턴 진행 (API 118~120) — 점검(next-check)을 통과해야만 버튼이 켜진다 (ADM-14)
   * ===================================================================== */
  var nextBtn = $('at-next-btn');

  function loadTurn() {
    var s = state.season, t = begin('turn');
    nextBtn.disabled = true;
    if (!s) {
      $('at-next-title').textContent = '다음 턴 진행';
      emptyBox($('at-checks'), noSeasonText());
      $('at-next-block').textContent = '';
      emptyBox($('at-turn-cal'), noSeasonText());
      emptyRow($('at-turn-log'), 3, noSeasonText());
      return;
    }
    emptyBox($('at-checks'), '점검 중…');
    $('at-next-block').textContent = '';
    api(seasonPath('/turns/next-check')).then(function (d) { if (!stale('turn', t)) renderCheck(d); },
      function (err) { if (!stale('turn', t)) renderCheck(null, err); });
    api(seasonPath('/turns')).then(function (d) { if (!stale('turn', t)) renderTurns(d); },
      function (err) { if (!stale('turn', t)) renderTurns(null, err); });
  }

  function renderCheck(check, err) {
    var s = state.season, T = s.currentTurn, next = check && check.nextTurn ? check.nextTurn : T + 1;
    $('at-next-title').textContent = next <= TURNS ? T + '턴 → ' + next + '턴' : '모든 턴이 끝났어요';
    nextBtn.textContent = next <= TURNS ? next + '턴으로 넘기기' : '턴 진행 완료';
    var block = '';
    if (err) {
      emptyBox($('at-checks'), errText(err), true);
      block = '점검 결과를 불러오지 못해 턴을 넘길 수 없어요';
    } else {
      var missing = check.missingPrices || [], news = check.newsCount || 0;
      var rows = [
        ['시즌 상태', s.status === 'RUNNING', s.status === 'RUNNING' ? '진행 중' : statusLabel(s.status) + ' · 진행 중일 때만 넘길 수 있어요'],
        ['휴장일', !check.isHoliday, check.isHoliday ? '오늘은 휴장일이라 잠겨 있어요' : '거래일'],
        [next + '턴 가격', missing.length === 0, missing.length ? '비어 있는 종목 ' + missing.length + '개' : '모두 입력됨'],
        [next + '턴 뉴스', news > 0, news + '개 등록 · 턴당 2~3개']
      ];
      var nodes = rows.map(function (r) {
        return el('div', { class: 'at-checkrow ' + (r[1] ? 'ok' : 'no') }, [el('b', { text: r[1] ? '✓' : '!' }), el('span', { text: r[0] }), el('small', { text: r[2] })]);
      });
      if (missing.length) {
        nodes.push(el('p', { class: 'at-muted' }, ['비어 있는 종목: ' + missing.join(', ') + ' ',
          el('button', { type: 'button', class: 'at-btn soft sm', text: '가격 입력하기', onclick: function () { openTab('prices'); } })]));
      }
      if (!news) {
        nodes.push(el('p', { class: 'at-muted' }, [el('button', { type: 'button', class: 'at-btn soft sm', text: '뉴스 등록하기', onclick: function () { openTab('news'); } })]));
      }
      fill($('at-checks'), nodes);
      if (next > TURNS) block = '남은 턴이 없어요';
      else if (s.status !== 'RUNNING') block = '시즌이 진행 중일 때만 턴을 넘길 수 있어요';
      else if (check.isHoliday) block = '휴장일에는 턴을 넘길 수 없어요';
      else if (missing.length) block = '다음 턴에 가격이 비어 있는 종목이 ' + missing.length + '개 있어요';
      else if (!news) block = '다음 턴 뉴스를 먼저 등록해 주세요';
      else if (check.ready !== true) block = '다음 턴 준비가 끝나지 않았어요';
    }
    $('at-turn-dot').hidden = !block || s.status !== 'RUNNING';
    $('at-next-block').textContent = block;
    nextBtn.disabled = !!block;
  }

  nextBtn.addEventListener('click', function () {
    var s = state.season, next = s.currentTurn + 1;
    confirmModal({ title: next + '턴으로 넘길까요?', body: '새 가격과 뉴스가 모든 참가자에게 공개되고, 전원의 총자산 스냅샷이 저장돼요. 되돌릴 수 없어요.', ok: '턴 넘기기' })
      .then(function (ok) {
        if (!ok) return;
        return busy(nextBtn, api(seasonPath('/turns/next'), { method: 'POST', body: { expectedCurrentTurn: s.currentTurn } })).then(function (data) {
          MB.toast((data && data.currentTurn ? data.currentTurn : next) + '턴으로 넘겼어요');
        }, toastError).then(function () { return loadSeasons(s.seasonId); }).then(loadTurn);
      });
  });
  $('at-check-reload').addEventListener('click', function () { loadSeasons().then(loadTurn); });

  function renderTurns(data, err) {
    var s = state.season, T = s.currentTurn, turns = data ? (data.turns || items(data)) : [];
    var byDate = {};
    turns.forEach(function (t) { byDate[t.tradeDate] = t; });
    if (s.startDate) {
      renderCal($('at-turn-cal'), buildDays(s.startDate), function (d) {
        if (d.turn == null) return 'off';
        var t = byDate[d.iso];
        if (t && t.state === 'DONE') return 'past';
        if (t && t.state === 'HOLIDAY') return 'off';
        if (t && (t.state === 'CURRENT' || t.state === 'TODAY')) return 'today';
        // 진행 기록이 없으면 시즌의 현재 턴으로 구분한다
        return d.turn < T ? 'past' : d.turn === T ? 'today' : 'future';
      });
    } else {
      emptyBox($('at-turn-cal'), '시즌 시작일이 없어요');
    }
    var log = turns.filter(function (t) { return t.progressedAt; });
    if (err) emptyRow($('at-turn-log'), 3, errText(err), true);
    else if (!log.length) emptyRow($('at-turn-log'), 3, '아직 넘긴 턴이 없어요');
    else {
      fill($('at-turn-log'), log.slice().reverse().map(function (t) {
        return el('tr', {}, [el('td', { text: dateTime(t.progressedAt) }), el('td', { text: t.progressedBy || '-' }),
          el('td', {}, [(t.turnNo - 1) + '턴 → ', el('strong', { text: t.turnNo + '턴' })])]);
      }));
    }
  }

  /* =====================================================================
   * 분야·종목·기업정보 (API 121~126)
   * ===================================================================== */
  function loadMarket(force) {
    if (state.marketLoaded && !force) return Promise.resolve();
    return Promise.all([api('/sectors'), api('/stocks')]).then(function (r) {
      state.sectors = items(r[0]);
      state.stocks = items(r[1]);
      state.marketLoaded = true;
    });
  }
  function sectorName(id) { var x = state.sectors.filter(function (s) { return s.sectorId === id; })[0]; return x ? x.name : '-'; }

  function loadStocksTab() {
    loadMarket(true).then(renderMarket, function (err) {
      emptyBox($('at-sector-list'), errText(err), true);
      emptyRow($('at-stock-rows'), 6, errText(err), true);
    });
  }

  function renderMarket() {
    var counts = {};
    state.stocks.forEach(function (st) { if (st.isActive !== false) counts[st.sectorId] = (counts[st.sectorId] || 0) + 1; });
    var list = $('at-sector-list');
    if (!state.sectors.length) emptyBox(list, '등록된 분야가 없어요');
    else {
      fill(list, state.sectors.map(function (sec) {
        var n = counts[sec.sectorId] || 0, active = sec.isActive !== false;
        var name = el('input', { type: 'text', value: sec.name, maxlength: 30, 'aria-label': '분야 이름' });
        var use = el('input', { type: 'checkbox', checked: active });
        return el('div', { class: 'at-sector' + (active && n !== 2 ? ' bad' : '') + (active ? '' : ' off') }, [
          el('small', { text: String(sec.sortOrder != null ? sec.sortOrder : '') }), name,
          el('small', { text: n + '종목' }), el('label', {}, [use, '사용']),
          el('button', { type: 'button', class: 'at-btn ghost sm', text: '저장', onclick: function (e) {
            var v = name.value.trim();
            if (!v) return MB.toast('분야 이름을 입력해 주세요', 'error');
            busy(e.currentTarget, api('/sectors/' + sec.sectorId, { method: 'PUT', body: { name: v, isActive: use.checked, sortOrder: sec.sortOrder } }))
              .then(function () { MB.toast('분야를 저장했어요'); loadStocksTab(); }, toastError);
          } })
        ]);
      }));
    }
    var rows = $('at-stock-rows');
    if (!state.stocks.length) emptyRow(rows, 6, '등록된 종목이 없어요');
    else {
      fill(rows, state.stocks.map(function (st, i) {
        return el('tr', { class: state.stockEditId === st.stockId ? 'hl' : '' }, [
          el('td', { text: String(i + 1) }), el('td', {}, [el('strong', { text: st.name })]),
          el('td', { text: st.sectorName || sectorName(st.sectorId) }), el('td', { text: fmt(st.startPrice) }),
          el('td', {}, [st.isActive === false ? pill('사용 안 함', 'gray') : pill('사용', 'soft')]),
          el('td', {}, [el('button', { type: 'button', class: 'at-btn ghost sm', text: '수정·기업정보', onclick: function () { editStock(st); } })])
        ]);
      }));
    }
    var sel = $('at-stock-form').sectorId, keep = sel.value;
    fill(sel, [el('option', { value: '', text: '분야를 골라 주세요' })].concat(state.sectors.map(function (sec) {
      return el('option', { value: sec.sectorId, text: sec.name + (sec.isActive === false ? ' (사용 안 함)' : '') });
    })));
    sel.value = keep;
  }

  function editStock(st) {
    var f = $('at-stock-form');
    state.stockEditId = st ? st.stockId : null;
    $('at-stock-form-title').textContent = st ? '종목 수정 · ' + st.name : '종목 등록';
    ['name', 'startPrice', 'intro', 'business', 'revenue', 'operatingProfit'].forEach(function (k) { f.elements[k].value = st && st[k] != null ? st[k] : ''; });
    f.sectorId.value = st ? st.sectorId : '';
    formError(f, '');
    renderMarket();
    if (st) f.elements.name.focus();
  }
  $('at-stock-new').addEventListener('click', function () { editStock(null); });
  $('at-stock-cancel').addEventListener('click', function () { editStock(null); });

  $('at-stock-form').addEventListener('submit', function (e) {
    e.preventDefault();
    var f = e.currentTarget, price = f.startPrice.value.trim();
    if (!f.sectorId.value) return formError(f, '분야를 골라 주세요');
    if (!f.elements.name.value.trim()) return formError(f, '종목명을 입력해 주세요');
    if (!/^\d+$/.test(price) || Number(price) < 1) return formError(f, '시작 가격은 1 이상의 정수로 입력해 주세요');
    formError(f, '');
    var body = { sectorId: Number(f.sectorId.value), name: f.elements.name.value.trim(), startPrice: Number(price),
      intro: f.intro.value, business: f.business.value, revenue: f.revenue.value, operatingProfit: f.operatingProfit.value };
    var id = state.stockEditId;
    var req = id ? api('/stocks/' + id, { method: 'PUT', body: body }) : api('/stocks', { method: 'POST', body: body });
    // STOCK_LIMIT_EXCEEDED(분야당 사용 중 종목 2개)·STOCK_DUPLICATED 는 서버 문구를 그대로 보여 준다
    busy(f.querySelector('[type=submit]'), req).then(function (data) {
      MB.toast(id ? '종목을 저장했어요' : '종목을 등록했어요');
      if (!id && data && data.stockId) state.stockEditId = data.stockId;
      loadStocksTab();
    }, function (err) { formError(f, errText(err)); });
  });

  $('at-sector-form').addEventListener('submit', function (e) {
    e.preventDefault();
    var f = e.currentTarget, name = f.elements.name.value.trim(), order = f.sortOrder.value.trim();
    if (!name) return MB.toast('분야 이름을 입력해 주세요', 'error');
    busy(f.querySelector('[type=submit]'), api('/sectors', { method: 'POST', body: { name: name, sortOrder: order ? Number(order) : null } }))
      .then(function () { MB.toast('분야를 추가했어요'); f.reset(); loadStocksTab(); }, toastError);
  });

  /* =====================================================================
   * 턴별 가격 (API 127~130) — 현재 턴 이하는 잠금, ±30% 경고는 confirm=true 로 다시 저장
   * ===================================================================== */
  var priceRows = $('at-price-rows');

  function loadPrices(force) {
    var t = begin('prices');
    if (!state.season) { fill($('at-price-head'), []); return emptyRow(priceRows, 1, noSeasonText()); }
    // 탭을 다녀온 것만으로는 저장하지 않은 입력을 지우지 않는다. 저장·CSV 올리기 뒤(force)와 시즌 변경은 새로 불러온다
    if (!force && state.prices && state.prices.seasonId === state.season.seasonId && Object.keys(state.edits).length) {
      return renderPrices();
    }
    emptyRow(priceRows, 1, '불러오는 중…');
    api(seasonPath('/prices')).then(function (data) {
      if (stale('prices', t)) return;
      state.prices = { seasonId: t.seasonId, currentTurn: data && data.currentTurn != null ? data.currentTurn : state.season.currentTurn, rows: (data && data.rows) || [] };
      state.edits = {};
      renderPrices();
    }, function (err) {
      if (stale('prices', t)) return;
      state.prices = null; fill($('at-price-head'), []); emptyRow(priceRows, 1, errText(err), true); updatePriceStatus();
    });
  }

  function editKey(stockId, turn) { return stockId + ':' + turn; }
  /** 이 칸 앞의 가장 가까운 가격 (입력 중인 가격 포함) */
  function prevPrice(row, turn) {
    for (var k = turn - 1; k >= 1; k--) {
      var ed = state.edits[editKey(row.stockId, k)];
      if (ed && ed.mode === 'price') return ed.value;
      if (row.prices[k - 1] != null) return row.prices[k - 1];
    }
    return null;
  }
  function isWarn(row, turn) {
    var ed = state.edits[editKey(row.stockId, turn)];
    if (!ed) return false;
    if (ed.mode === 'rate') return Math.abs(ed.value) > 30;
    var p = prevPrice(row, turn);
    return ed.value <= 0 || (p && Math.abs(ed.value / p - 1) > 0.3);
  }

  function renderPrices() {
    var P = state.prices, T = P.currentTurn;
    $('at-price-desc').textContent = T > 0 ? '지난 턴과 현재 턴(1~' + T + '턴)은 잠겨 있어요' : '아직 시작 전이라 모든 턴을 입력할 수 있어요';
    var head = [el('th', { class: 'sticky', text: '종목' })];
    for (var t = 1; t <= TURNS; t++) {
      head.push(el('th', { class: t <= T ? 'lockh' : '' }, [t <= T ? el('img', { src: '/images/lock.png', alt: '잠김' }) : null, t + '턴']));
    }
    fill($('at-price-head'), [el('tr', {}, head)]);
    if (!P.rows.length) { emptyRow(priceRows, TURNS + 1, '종목이 없어요. 분야·종목에서 먼저 등록해 주세요'); return updatePriceStatus(); }
    fill(priceRows, P.rows.map(function (row) {
      var cells = [el('td', { class: 'sticky', text: row.stockName })];
      for (var t = 1; t <= TURNS; t++) {
        var v = row.prices[t - 1];
        if (t <= T) { cells.push(el('td', { class: 'lock', text: fmt(v) })); continue; }
        var ed = state.edits[editKey(row.stockId, t)];
        var shown = ed && ed.mode === state.mode ? ed.value : (state.mode === 'price' && v != null ? v : '');
        var input = el('input', { type: 'number', value: shown, 'data-stock': row.stockId, 'data-turn': t,
          step: state.mode === 'price' ? 1 : 0.01, placeholder: state.mode === 'rate' ? (v != null ? fmt(v) : '%') : '-',
          'aria-label': row.stockName + ' ' + t + '턴 ' + (state.mode === 'rate' ? '변동률' : '가격') });
        cells.push(el('td', { class: cellClass(row, t) }, [input]));
      }
      return el('tr', {}, cells);
    }));
    updatePriceStatus();
  }
  function cellClass(row, t) {
    var ed = state.edits[editKey(row.stockId, t)];
    return (ed ? 'dirty ' : row.prices[t - 1] == null ? 'empty ' : '') + (isWarn(row, t) ? 'warn' : '');
  }
  function rowOf(stockId) { return state.prices.rows.filter(function (r) { return String(r.stockId) === String(stockId); })[0]; }

  priceRows.addEventListener('input', function (e) {
    var input = e.target;
    if (!input.matches('input[data-stock]')) return;
    var stockId = Number(input.getAttribute('data-stock')), turn = Number(input.getAttribute('data-turn')), raw = input.value.trim();
    var key = editKey(stockId, turn), row = rowOf(stockId);
    var orig = row.prices[turn - 1];
    // 빈 칸으로 지우거나 원래 가격과 같으면 바뀐 것이 아니다 (API 로 가격을 지울 수는 없다)
    if (raw === '' || isNaN(Number(raw)) || (state.mode === 'price' && Number(raw) === orig)) delete state.edits[key];
    else state.edits[key] = { stockId: stockId, turnNo: turn, mode: state.mode, value: Number(raw) };
    // 이 행의 뒤 칸 경고도 다시 계산한다 (포커스를 잃지 않게 표 전체를 다시 그리지 않는다)
    input.closest('tr').querySelectorAll('input[data-stock]').forEach(function (inp) {
      inp.parentNode.className = cellClass(row, Number(inp.getAttribute('data-turn')));
    });
    updatePriceStatus();
  });

  function updatePriceStatus() {
    var keys = Object.keys(state.edits), warns = 0;
    keys.forEach(function (k) { var ed = state.edits[k]; if (isWarn(rowOf(ed.stockId), ed.turnNo)) warns += 1; });
    $('at-price-status').textContent = keys.length
      ? keys.length + '칸 바뀜' + (warns ? ' · ±30%를 넘는 칸 ' + warns + '개 (저장할 때 다시 확인해요)' : '')
      : '바뀐 칸 없음';
    $('at-price-reset').disabled = !keys.length;
    $('at-price-save').disabled = !keys.length;
  }

  root.querySelectorAll('[data-at-mode]').forEach(function (b) {
    b.addEventListener('click', function () {
      var mode = b.getAttribute('data-at-mode');
      if (mode === state.mode) return;
      if (Object.keys(state.edits).length) return MB.toast('저장하거나 되돌린 뒤에 입력 방식을 바꿔 주세요', 'error');
      state.mode = mode;
      root.querySelectorAll('[data-at-mode]').forEach(function (x) { x.classList.toggle('on', x === b); });
      $('at-rate-help').hidden = mode !== 'rate';
      if (state.prices) renderPrices();
    });
  });
  $('at-price-reset').addEventListener('click', function () { state.edits = {}; renderPrices(); });

  function savePrices(confirm) {
    var cells = Object.keys(state.edits).map(function (k) {
      var ed = state.edits[k];
      return ed.mode === 'rate' ? { stockId: ed.stockId, turnNo: ed.turnNo, changeRate: ed.value } : { stockId: ed.stockId, turnNo: ed.turnNo, price: ed.value };
    });
    var bad = cells.filter(function (c) { return c.price != null && (c.price < 1 || c.price % 1 !== 0); });
    if (bad.length) return MB.toast('가격은 1원 이상의 정수로 입력해 주세요', 'error');
    return busy($('at-price-save'), api(seasonPath('/prices'), { method: 'PUT', body: { confirm: confirm, cells: cells } })).then(function (res) {
      var warnings = (res && res.warnings) || [];
      if (!confirm && warnings.length && !(res && res.saved)) {
        return confirmModal({
          title: '한 턴에 ±30%를 넘는 변동이에요. 그대로 저장할까요?', body: '아래 칸의 가격을 확인해 주세요.', ok: '그대로 저장',
          list: warnings.map(function (w) { var r = rowOf(w.stockId); return (r ? r.stockName : w.stockId) + ' ' + w.turnNo + '턴 · ' + w.message; })
        }).then(function (ok) { if (ok) return savePrices(true); });
      }
      MB.toast(((res && res.saved) || 0) + '칸을 저장했어요');
      loadPrices(true);
    }, toastError);
  }
  $('at-price-save').addEventListener('click', function () { savePrices(false); });

  /** 파일·오류 행을 다루는 CSV 요청만 fetch 를 직접 쓴다 (MB.api 는 오류의 data 를 넘겨주지 않는다) */
  function rawFetch(url, opts) {
    var o = Object.assign({ credentials: 'same-origin', headers: { Accept: 'application/json, text/csv' } }, opts || {});
    if (o.method && o.method !== 'GET') {
      var token = document.querySelector('meta[name="_csrf"]'), header = document.querySelector('meta[name="_csrf_header"]');
      if (token && header) o.headers[header.content] = token.content;
    }
    return fetch(url, o).catch(function () { return Promise.reject({ message: '네트워크 연결을 확인하고 다시 시도해 주세요.' }); });
  }
  function readJson(res) { return res.json().catch(function () { return null; }); }

  $('at-csv-export').addEventListener('click', function (e) {
    if (!state.season) return MB.toast(noSeasonText(), 'error');
    var name = state.season.name;
    busy(e.currentTarget, rawFetch(BASE + seasonPath('/prices/export')).then(function (res) {
      if (!res.ok) return readJson(res).then(function (b) { throw { message: (b && b.error && b.error.message) || '가격표를 내려받지 못했어요.' }; });
      return res.blob().then(function (blob) {
        var a = el('a', { href: URL.createObjectURL(blob), download: name + '_가격표.csv' });
        document.body.appendChild(a);
        a.click();
        setTimeout(function () { URL.revokeObjectURL(a.href); a.remove(); }, 0);
      });
    })).catch(toastError);
  });

  $('at-csv-import').addEventListener('change', function (e) {
    var input = e.target, file = input.files && input.files[0], box = $('at-csv-result');
    input.value = '';
    if (!file) return;
    if (!state.season) return MB.toast(noSeasonText(), 'error');
    if (Object.keys(state.edits).length) return MB.toast('저장하거나 되돌린 뒤에 CSV를 올려 주세요', 'error');
    var form = new FormData();
    form.append('file', file);
    input.disabled = true;   // 요청 중 다시 올리지 못하게 잠근다 (같은 가격표가 두 번 저장되는 것 방지)
    rawFetch(BASE + seasonPath('/prices/import'), { method: 'POST', body: form }).then(function (res) {
      return readJson(res).then(function (body) {
        if (res.ok && (!body || body.success !== false)) {
          var data = body && 'data' in body ? body.data : body;
          var warnings = (data && data.warnings) || [];
          showCsvResult((data && data.saved || 0) + '칸을 저장했어요' + (warnings.length ? ' · ±30%를 넘는 칸이 있어요' : ''),
            warnings.map(function (w) { var r = rowOf(w.stockId); return (r ? r.stockName : w.stockId) + ' ' + w.turnNo + '턴 · ' + w.message; }));
          MB.toast('CSV를 저장했어요');
          loadPrices(true);
          return;
        }
        // CSV_INVALID: 전체가 취소되고 오류 행이 data.errors 로 온다 (API 130)
        var err = (body && body.error) || {}, rows = (body && body.data && body.data.errors) || [];
        showCsvResult((err.message || 'CSV를 올리지 못했어요') + (rows.length ? ' · 아무것도 저장하지 않았어요' : ''),
          rows.map(function (r) { return r.line + '행 · ' + r.message; }));
      });
    }).catch(function (err) { showCsvResult(errText(err), []); })
      .then(function () { input.disabled = false; });
    function showCsvResult(title, list) {
      fill(box, [el('strong', { text: title }), list.length ? el('ul', {}, list.map(function (t) { return el('li', { text: t }); })) : null]);
      box.hidden = false;
    }
  });

  /* =====================================================================
   * 뉴스 (API 131~134) — 지난 턴(현재 턴 이하) 뉴스는 제목·본문·메모만 수정
   * ===================================================================== */
  var newsForm = $('at-news-form');

  function loadNews() {
    var t = begin('news');
    if (!state.season) { fill($('at-news-turns'), []); return emptyBox($('at-news-list'), noSeasonText()); }
    emptyBox($('at-news-list'), '불러오는 중…');
    Promise.all([loadMarket(false), api(seasonPath('/news'))]).then(function (r) {
      if (stale('news', t)) return;
      state.news = items(r[1]);
      renderNews();
    }, function (err) {
      if (stale('news', t)) return;
      fill($('at-news-turns'), []); emptyBox($('at-news-list'), errText(err), true);
    });
  }

  function renderNews() {
    var T = state.season.currentTurn;
    if (state.newsTurn == null) state.newsTurn = Math.min(T + 1, TURNS) || 1;
    var counts = {};
    state.news.forEach(function (n) { counts[n.turnNo] = (counts[n.turnNo] || 0) + 1; });
    var chips = [];
    for (var t = 1; t <= TURNS; t++) {
      (function (turn) {
        var c = counts[turn] || 0;
        chips.push(el('button', { type: 'button', class: (state.newsTurn === turn ? 'on ' : '') + (turn > T && c < 2 ? 'short' : ''),
          onclick: function () { state.newsTurn = turn; renderNews(); } }, [turn + '턴', el('small', { text: c + '개' })]));
      })(t);
    }
    fill($('at-news-turns'), chips);
    var turn = state.newsTurn, list = state.news.filter(function (n) { return n.turnNo === turn; });
    var nodes = [];
    if (turn > T && list.length < 2) nodes.push(el('p', { class: 'at-err', text: turn + '턴 뉴스가 ' + list.length + '개예요 · 턴당 2~3개를 내요' }));
    list.forEach(function (n) {
      var past = n.turnNo <= T;
      nodes.push(el('div', { class: 'at-news' }, [
        el('div', { class: 'at-row' }, [pill(past ? '공개됨' : '공개 예정', past ? 'gray' : 'mint')].concat((n.sectors || []).map(function (s) { return pill(s.name, 'soft'); }))),
        el('strong', { text: n.title }), el('p', { text: n.content }),
        el('small', { text: '관련 종목 · ' + ((n.stocks || []).map(function (s) { return s.name; }).join(', ') || '없음') }),
        n.adminMemo ? el('small', { class: 'at-memo', text: '관리자 메모 · ' + n.adminMemo }) : null,
        el('div', { class: 'at-row' }, [
          el('button', { type: 'button', class: 'at-btn soft sm', text: past ? '오타 수정' : '수정', onclick: function () { openNewsForm(n); } }),
          past ? null : el('button', { type: 'button', class: 'at-btn danger-ghost sm', text: '삭제', onclick: function (e) { deleteNews(n, e.currentTarget); } })
        ])
      ]));
    });
    if (!list.length) nodes.push(el('div', { class: 'at-empty', text: turn + '턴에 등록된 뉴스가 없어요' }));
    fill($('at-news-list'), nodes);
  }

  function openNewsForm(n) {
    var T = state.season.currentTurn, past = !!(n && n.turnNo <= T);
    if (!n && T >= TURNS) return MB.toast('남은 턴이 없어서 뉴스를 등록할 수 없어요', 'error');
    state.newsEdit = n || null;
    state.newsSel = {
      sectorIds: n ? (n.sectors || []).map(function (s) { return s.id; }) : [],
      stockIds: n ? (n.stocks || []).map(function (s) { return s.id; }) : []
    };
    var opts = [];
    if (past) opts.push(el('option', { value: n.turnNo, text: n.turnNo + '턴' }));
    else for (var t = T + 1; t <= TURNS; t++) opts.push(el('option', { value: t, text: t + '턴' }));
    fill(newsForm.turnNo, opts);
    newsForm.turnNo.value = n ? n.turnNo : Math.max(state.newsTurn, T + 1);
    newsForm.turnNo.disabled = past;
    newsForm.elements.title.value = n ? n.title : '';
    newsForm.content.value = n ? n.content : '';
    newsForm.adminMemo.value = n && n.adminMemo ? n.adminMemo : '';
    $('at-news-form-title').textContent = !n ? '뉴스 등록' : past ? '지난 턴 뉴스 · 오타 수정' : '뉴스 수정';
    $('at-news-form-desc').textContent = past ? '지난 턴 뉴스는 제목·본문·관리자 메모만 고칠 수 있어요. 턴·관련 분야·종목은 바꿀 수 없어요' : '제목, 본문, 관련 분야·종목을 입력해요';
    formError(newsForm, '');
    renderNewsChips(past);
    $('at-news-placeholder').hidden = true;
    newsForm.hidden = false;
    newsForm.elements.title.focus();
  }
  function closeNewsForm() { state.newsEdit = null; newsForm.hidden = true; $('at-news-placeholder').hidden = false; $('at-news-form-title').textContent = '뉴스 편집'; }

  function renderNewsChips(past) {
    var sel = state.newsSel;
    var toggle = function (arr, id) { var i = arr.indexOf(id); if (i >= 0) arr.splice(i, 1); else arr.push(id); };
    fill($('at-news-sectors'), state.sectors.filter(function (s) { return s.isActive !== false || sel.sectorIds.indexOf(s.sectorId) >= 0; }).map(function (s) {
      return el('button', { type: 'button', class: sel.sectorIds.indexOf(s.sectorId) >= 0 ? 'on' : '', disabled: past, text: s.name, onclick: function () {
        toggle(sel.sectorIds, s.sectorId);
        // 분야를 빼면 그 분야의 종목도 함께 뺀다
        sel.stockIds = sel.stockIds.filter(function (id) { var st = state.stocks.filter(function (x) { return x.stockId === id; })[0]; return st && sel.sectorIds.indexOf(st.sectorId) >= 0; });
        renderNewsChips(past);
      } });
    }));
    var stocks = state.stocks.filter(function (st) { return sel.sectorIds.indexOf(st.sectorId) >= 0; });
    fill($('at-news-stocks'), stocks.length ? stocks.map(function (st) {
      return el('button', { type: 'button', class: sel.stockIds.indexOf(st.stockId) >= 0 ? 'on' : '', disabled: past, text: st.name, onclick: function () { toggle(sel.stockIds, st.stockId); renderNewsChips(past); } });
    }) : [el('small', { class: 'at-muted', text: '분야를 먼저 골라 주세요' })]);
  }

  $('at-news-new').addEventListener('click', function () { if (state.season) openNewsForm(null); else MB.toast(noSeasonText(), 'error'); });
  $('at-news-cancel').addEventListener('click', closeNewsForm);

  newsForm.addEventListener('submit', function (e) {
    e.preventDefault();
    var n = state.newsEdit, title = newsForm.elements.title.value.trim(), content = newsForm.content.value.trim();
    if (!title || !content) return formError(newsForm, '제목과 본문을 입력해 주세요');
    formError(newsForm, '');
    var body = { turnNo: Number(newsForm.turnNo.value), title: title, content: content,
      sectorIds: state.newsSel.sectorIds.slice(), stockIds: state.newsSel.stockIds.slice(), adminMemo: newsForm.adminMemo.value.trim() || null };
    var req = n ? api('/news/' + n.newsId, { method: 'PUT', body: body }) : api(seasonPath('/news'), { method: 'POST', body: body });
    // PAST_TURN_LOCKED: 지난 턴으로 옮기거나 지난 턴의 분야·종목을 바꾸려 하면 서버가 거절한다
    busy(newsForm.querySelector('[type=submit]'), req).then(function () {
      MB.toast('뉴스를 저장했어요');
      state.newsTurn = body.turnNo;
      closeNewsForm();
      loadNews();
    }, function (err) { formError(newsForm, errText(err)); });
  });

  function deleteNews(n, btn) {
    confirmModal({ title: '뉴스를 삭제할까요?', body: n.title, ok: '삭제', danger: true }).then(function (ok) {
      if (!ok) return;
      return busy(btn, api('/news/' + n.newsId, { method: 'DELETE' })).then(function () {
        MB.toast('뉴스를 삭제했어요');
        if (state.newsEdit && state.newsEdit.newsId === n.newsId) closeNewsForm();
        loadNews();
      }, toastError);
    });
  }

  /* =====================================================================
   * 참가자 (API 135~136)
   * ===================================================================== */
  function loadPeople() {
    var rows = $('at-people-rows'), t = begin('people');
    personReq += 1;   // 보내 둔 참가자 상세 요청을 무효로 만들고 상세 영역을 처음 상태로 돌린다
    $('at-person-title').textContent = '참가자 상세';
    emptyBox($('at-person-detail'), '왼쪽에서 참가자를 골라 주세요');
    if (!state.season) return emptyRow(rows, 5, noSeasonText());
    emptyRow(rows, 5, '불러오는 중…');
    api(seasonPath('/participants?sort=netAsset&page=0&size=50')).then(function (data) {
      if (stale('people', t)) return;
      var list = items(data);
      if (!list.length) return emptyRow(rows, 5, '아직 참가자가 없어요');
      fill(rows, list.map(function (p) {
        return el('tr', { class: 'click', tabindex: 0, onclick: function (e) { selectPerson(p, e.currentTarget); },
          onkeydown: function (e) { if (e.key === 'Enter') selectPerson(p, e.currentTarget); } }, [
          el('td', {}, [el('strong', { text: p.nickname })]), el('td', { text: 'Lv.' + p.level }),
          el('td', { text: fmt(p.investAmount) }), el('td', { text: fmt(p.netAsset) }),
          el('td', { class: tone(p.returnRate), text: pct(p.returnRate) })
        ]);
      }));
    }, function (err) { if (!stale('people', t)) emptyRow(rows, 5, errText(err), true); });
  }

  function selectPerson(p, tr) {
    var token = ++personReq;
    tr.parentNode.querySelectorAll('tr').forEach(function (x) { x.classList.toggle('hl', x === tr); });
    $('at-person-title').textContent = p.nickname + ' 상세';
    var box = $('at-person-detail');
    emptyBox(box, '불러오는 중…');
    api('/participants/' + p.participantId).then(function (d) {
      if (token !== personReq) return;
      var holdings = (d && d.holdings) || [], orders = (d && d.orders) || [];
      fill(box, [
        el('h4', { class: 'at-sub', text: '보유 종목' }),
        el('div', { class: 'at-list' }, holdings.length ? holdings.map(function (h) {
          return el('div', { class: 'at-li' }, [el('span', { text: (h.stockName || '-') + ' · ' + fmt(h.quantity) + '주' }), el('small', { text: h.evaluationAmount != null ? fmt(h.evaluationAmount) + ' 캐시' : '' })]);
        }) : [el('div', { class: 'at-empty', text: '보유 종목이 없어요' })]),
        el('h4', { class: 'at-sub', text: '거래 내역 · 매매 이유' }),
        el('div', { class: 'at-list' }, orders.length ? orders.map(function (o) {
          var buy = o.orderType === 'BUY';
          return el('div', { class: 'at-li' }, [
            el('span', {}, [el('b', { class: buy ? 'up' : 'down', text: buy ? '매수' : '매도' }), ' ' + (o.turnNo || '-') + '턴 · ' + (o.stockName || '-') + ' ' + fmt(o.quantity) + '주 · ' + fmt(o.price)]),
            o.reason ? pill(o.reason, 'soft') : null
          ]);
        }) : [el('div', { class: 'at-empty', text: '거래 내역이 없어요' })])
      ]);
    }, function (err) { if (token === personReq) emptyBox(box, errText(err), true); });
  }

  /* =====================================================================
   * 정산·랭킹·통계 (API 137~139, 141) — 최소 구성
   * ===================================================================== */
  function loadRanking() {
    var s = state.season, settle = $('at-settle-btn'), publish = $('at-publish-btn');
    settle.disabled = !s || s.status !== 'SETTLING';
    publish.disabled = !s || (s.status !== 'SETTLING' && s.status !== 'ENDED');
    $('at-settle-hint').textContent = !s ? noSeasonText() : s.status === 'SETTLING' || s.status === 'ENDED'
      ? '랭킹 공개는 정산을 마친 뒤에만 할 수 있어요' : '시즌 상태가 ‘정산’일 때 실행할 수 있어요 · 현재 ' + statusLabel(s.status);
    loadRankPreview();
    var stats = $('at-stats'), t = begin('stats');
    if (!s) return emptyBox(stats, noSeasonText());
    emptyBox(stats, '불러오는 중…');
    api(seasonPath('/statistics')).then(function (d) {
      if (stale('stats', t)) return;
      d = d || {};
      var tops = d.topStocks || [], reasons = d.reasonRatios || [];
      fill(stats, [
        el('div', { class: 'at-mini' }, [
          el('div', {}, [el('span', { text: '평균 수익률' }), el('strong', { class: tone(d.averageReturnRate), text: pct(d.averageReturnRate) })]),
          el('div', {}, [el('span', { text: '급락 턴에 판 사람' }), el('strong', { text: d.crashTurnSellRate != null ? Number(d.crashTurnSellRate).toFixed(1) + '%' : '-' })])
        ]),
        el('h4', { class: 'at-sub', text: '많이 산 종목' }),
        el('div', { class: 'at-list' }, tops.length ? tops.map(function (t, i) { return el('div', { class: 'at-li' }, [el('span', { text: (i + 1) + '. ' + t.stockName }), el('small', { text: fmt(t.quantity) + '주' })]); }) : [el('div', { class: 'at-empty', text: '아직 거래가 없어요' })]),
        el('h4', { class: 'at-sub', text: '매매 이유 비율' }),
        el('div', { class: 'at-list' }, reasons.map(function (r) { return el('div', { class: 'at-li' }, [el('span', { text: r.reason }), el('small', { text: Number(r.ratio || 0).toFixed(1) + '%' })]); }))
      ]);
    }, function (err) { if (!stale('stats', t)) emptyBox(stats, errText(err), true); });
  }

  function loadRankPreview() {
    var rows = $('at-rank-rows'), t = begin('rank');
    if (!state.season) return emptyRow(rows, 4, noSeasonText());
    emptyRow(rows, 4, '불러오는 중…');
    api(seasonPath('/rankings/preview?type=' + state.rankType)).then(function (d) {
      if (stale('rank', t)) return;
      var list = items(d);
      if (!list.length) return emptyRow(rows, 4, '아직 랭킹에 오른 참가자가 없어요');
      fill(rows, list.map(function (r) {
        return el('tr', {}, [el('td', {}, [el('strong', { text: String(r.rank) })]), el('td', { text: r.nickname }),
          el('td', { text: fmt(r.netAsset) }), el('td', { class: tone(r.returnRate), text: pct(r.returnRate) })]);
      }));
    }, function (err) { if (!stale('rank', t)) emptyRow(rows, 4, errText(err), true); });
  }
  root.querySelectorAll('[data-at-rank]').forEach(function (b) {
    b.addEventListener('click', function () {
      state.rankType = b.getAttribute('data-at-rank');
      root.querySelectorAll('[data-at-rank]').forEach(function (x) { x.classList.toggle('on', x === b); });
      loadRankPreview();
    });
  });

  $('at-settle-btn').addEventListener('click', function (e) {
    var btn = e.currentTarget, s = state.season;
    confirmModal({ title: '정산을 실행할까요?', body: '최종 순자산·수익률·순위·이월금을 한 번에 확정해요. 실행한 뒤에는 되돌릴 수 없어요.', ok: '정산 실행' }).then(function (ok) {
      if (!ok) return;
      return busy(btn, api(seasonPath('/settle'), { method: 'POST' })).then(function (d) {
        MB.toast((d && d.participants != null ? fmt(d.participants) + '명의 ' : '') + '정산을 마쳤어요');
        return loadSeasons(s.seasonId).then(loadRanking);
      }, toastError);
    });
  });
  $('at-publish-btn').addEventListener('click', function (e) {
    var btn = e.currentTarget, s = state.season;
    confirmModal({ title: '랭킹을 공개할까요?', body: '모든 사용자에게 ' + s.name + ' 순자산·수익률 랭킹이 공개돼요. 되돌릴 수 없어요.', ok: '랭킹 공개' }).then(function (ok) {
      if (!ok) return;
      return busy(btn, api(seasonPath('/rankings/publish'), { method: 'POST' })).then(function () {
        MB.toast('랭킹을 공개했어요');
      }, toastError);
    });
  });

  /* ---------- 시작 ---------- */
  var first = (location.hash || '').slice(1);
  loadSeasons().then(function () { openTab(TABS.indexOf(first) >= 0 ? first : 'season'); });
})();
