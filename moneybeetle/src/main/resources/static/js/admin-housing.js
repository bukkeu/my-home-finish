/* comment.
*  담당 : SCM 서수영 (#63, 관리자 모의 청약 운영 화면)
*  templates/admin/housing.html 의 동작. 데이터는 운영 API 로 불러온다 (MB.api, common.js)
*  - 공고 관리 : 목록 GET /api/v1/admin/housing/complexes (142), 등록 POST (143), 수정 PUT /{id} (144), 삭제 DELETE /{id} (145)
*  - 신청자가 있거나 산정한 공고는 일정·공급 세대 수를 바꿀 수 없다 (SP-76, #48). 신청자가 있으면 삭제도 안 된다
*  - 화면에서 먼저 막고, 서버도 같은 규칙으로 다시 막는다 (오류 코드의 안내 문구를 그대로 보여 준다)
*  */
(function () {
  'use strict';

  var root = document.getElementById('ah');
  if (!root || !window.MB) return;

  var API = '/api/v1/admin/housing/complexes';
  var TODAY = root.dataset.today;                       // 'yyyy-MM-dd' (서울)
  var STATUS_TEXT = { UPCOMING: '접수 전', OPEN: '접수 중', CLOSED: '마감 · 발표 전', ANNOUNCED: '발표 완료' };

  var state = { items: [], loaded: false, status: '', q: '', editing: null };

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
  function dotted(text) {                               // 2026-11-03 → 2026.11.03
    return String(text || '').replace(/-/g, '.');
  }
  function won(n) {                                     // 320000000 → 3억 2,000만
    var eok = Math.floor(n / 100000000);
    var man = Math.floor((n % 100000000) / 10000);
    var rest = n % 10000;
    var out = (eok ? eok + '억 ' : '') + (man ? fmt(man) + '만' : '');
    return (out || fmt(rest)).trim() + ' 캐시';
  }
  function statusTag(status) {
    return el('span', 'ah-status is-' + status, STATUS_TEXT[status] || status);
  }
  function bind(name, value) {
    root.querySelectorAll('[data-bind="' + name + '"]').forEach(function (n) { n.textContent = value; });
  }
  function find(id) {
    return state.items.filter(function (c) { return c.complexId === id; })[0] || null;
  }
  // 일정·세대 수를 바꿀 수 없는 공고인가? (신청자가 있거나 이미 산정함)
  function isScheduleLocked(c) {
    return c.applicants > 0 || c.computed;
  }

  // ---------- 확인창 (되돌릴 수 없는 작업 전에) ----------
  var dialog = document.getElementById('ah-confirm');
  function confirmBox(title, body, okLabel) {
    dialog.querySelector('[data-confirm-title]').textContent = title;
    dialog.querySelector('[data-confirm-body]').textContent = body;
    dialog.querySelector('[data-confirm-ok]').textContent = okLabel || '확인';
    dialog.returnValue = '';
    return new Promise(function (resolve) {
      dialog.addEventListener('close', function onClose() {
        dialog.removeEventListener('close', onClose);
        resolve(dialog.returnValue === 'ok');
      });
      dialog.showModal();
    });
  }

  // ---------- 탭 ----------
  var tabs = root.querySelectorAll('.ah-tab');
  function showTab(name) {
    tabs.forEach(function (t) {
      var on = t.dataset.tab === name;
      t.classList.toggle('is-active', on);
      t.setAttribute('aria-selected', on ? 'true' : 'false');
    });
    root.querySelectorAll('.ah-panel').forEach(function (p) { p.hidden = p.dataset.panel !== name; });
    if (history.replaceState) history.replaceState(null, '', name === 'notices' ? location.pathname : '#' + name);
    if (onTab[name]) onTab[name]();
  }
  tabs.forEach(function (t) { t.addEventListener('click', function () { showTab(t.dataset.tab); }); });

  // 탭을 열 때 · 목록을 새로 받았을 때 할 일 (탭마다 하나씩 등록한다)
  var onTab = {};
  var onData = [];

  // ---------- 목록 (API 142) ----------
  var listSeq = 0;
  function loadComplexes() {
    var seq = ++listSeq;
    return MB.api(API).then(function (data) {
      if (seq !== listSeq) return;
      state.items = (data && data.items) || [];
      state.loaded = true;
      renderSummary();
      renderList();
      onData.forEach(function (fn) { fn(); });
    }).catch(function (err) {
      if (seq !== listSeq) return;
      var box = root.querySelector('[data-list="notices"]');
      box.replaceChildren(el('div', 'card ah-empty', '공고를 불러오지 못했어요. ' + err.message));
      MB.toast(err.message, 'error');
    });
  }

  function renderSummary() {
    var counts = { ALL: state.items.length, UPCOMING: 0, OPEN: 0, CLOSED: 0, ANNOUNCED: 0 };
    state.items.forEach(function (c) { counts[c.status] = (counts[c.status] || 0) + 1; });
    Object.keys(counts).forEach(function (k) {
      var n = root.querySelector('[data-count="' + k + '"]');
      if (n) n.textContent = String(counts[k]);
    });
    root.querySelectorAll('.ah-stat').forEach(function (b) {
      b.classList.toggle('is-active', b.dataset.stat === state.status);
    });
  }

  function renderList() {
    var q = state.q.trim().toLowerCase();
    var items = state.items.filter(function (c) {
      return (!state.status || c.status === state.status) && (!q || c.name.toLowerCase().indexOf(q) >= 0);
    });
    bind('noticeCount', String(items.length));
    var box = root.querySelector('[data-list="notices"]');
    box.replaceChildren();
    if (!items.length) {
      box.appendChild(el('div', 'card ah-empty', state.items.length ? '조건에 맞는 공고가 없어요.' : '등록된 공고가 없어요. 왼쪽에서 첫 공고를 등록해 보세요.'));
      return;
    }
    items.forEach(function (c) { box.appendChild(noticeCard(c)); });
  }

  function noticeCard(c) {
    var card = el('article', 'card ah-item' + (state.editing === c.complexId ? ' is-selected' : ''));
    var head = el('div', 'ah-item__head');
    head.appendChild(el('strong', null, c.name));
    head.appendChild(statusTag(c.status));
    if (c.computed && c.status !== 'ANNOUNCED') head.appendChild(el('span', 'ah-badge is-gold', '산정 완료'));
    card.appendChild(head);
    card.appendChild(el('p', 'ah-item__meta',
        [c.location, c.supplyType, c.areaType, won(c.salePrice), fmt(c.supplyUnits) + '세대'].join(' · ')));

    var sched = el('dl', 'ah-sched');
    [['공고일', dotted(c.noticeDate)], ['신청 기간', dotted(c.applyStartDate) + ' ~ ' + dotted(c.applyEndDate)],
      ['당첨자 발표', dotted(c.announceDate)], ['신청자', fmt(c.applicants) + '명']].forEach(function (kv) {
      var d = el('div');
      d.appendChild(el('dt', null, kv[0]));
      d.appendChild(el('dd', null, kv[1]));
      sched.appendChild(d);
    });
    card.appendChild(sched);

    if (isScheduleLocked(c)) {
      card.appendChild(el('p', 'ah-item__note', c.computed
          ? '당첨자를 산정한 공고라 일정·공급 세대 수는 바꿀 수 없어요.'
          : '신청자가 있어 일정·공급 세대 수는 바꿀 수 없고 삭제할 수 없어요. 이름·위치·면적·분양가·공급 유형은 바꿀 수 있어요.'));
    }

    var actions = el('div', 'ah-actions');
    var edit = el('button', 'btn btn-secondary', '수정');
    edit.type = 'button';
    edit.addEventListener('click', function () { startEdit(c.complexId); });
    actions.appendChild(edit);

    var apps = el('button', 'btn btn-secondary', '신청 현황 ' + fmt(c.applicants) + '건');
    apps.type = 'button';
    apps.addEventListener('click', function () { openApplicants(c.complexId); });
    actions.appendChild(apps);

    var del = el('button', 'btn btn-ghost', '삭제');
    del.type = 'button';
    del.disabled = c.applicants > 0;
    if (del.disabled) del.title = '신청자가 있는 공고는 삭제할 수 없어요';
    del.addEventListener('click', function () { removeComplex(c); });
    actions.appendChild(del);
    card.appendChild(actions);
    return card;
  }

  // 요약 숫자를 누르면 그 상태로 거른다 (같은 걸 다시 누르면 전체)
  root.querySelectorAll('.ah-stat').forEach(function (b) {
    b.addEventListener('click', function () {
      state.status = state.status === b.dataset.stat ? '' : b.dataset.stat;
      root.querySelector('[data-filter="status"]').value = state.status;
      showTab('notices');
      renderSummary();
      renderList();
    });
  });
  root.querySelector('[data-filter="status"]').addEventListener('change', function (e) {
    state.status = e.target.value;
    renderSummary();
    renderList();
  });
  root.querySelector('[data-filter="q"]').addEventListener('input', function (e) {
    state.q = e.target.value;
    renderList();
  });

  // ---------- 등록·수정 폼 (API 143·144) ----------
  var form = document.getElementById('ah-form');
  var submitBtn = form.querySelector('[data-form-submit]');
  var cancelBtn = form.querySelector('[data-form-cancel]');
  var formMsg = form.querySelector('[data-form-msg]');
  var lockNote = form.querySelector('[data-lock-note]');
  var TEXT_FIELDS = ['name', 'location', 'supplyType', 'areaType'];
  var NUMBER_FIELDS = ['salePrice', 'supplyUnits'];
  var DATE_FIELDS = ['noticeDate', 'applyStartDate', 'applyEndDate', 'announceDate'];
  var LOCKED_FIELDS = ['supplyUnits'].concat(DATE_FIELDS);

  function input(name) {
    return form.querySelector('[name="' + name + '"]');
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
    form.classList.remove('is-editing');
    form.querySelector('[data-form-title]').textContent = '새 공고 등록';
    form.querySelector('[data-form-editing]').hidden = true;
    submitBtn.textContent = '공고 등록';
    cancelBtn.hidden = true;
    renderList();
  }

  function startEdit(id) {
    var c = find(id);
    if (!c) return;
    resetForm();
    state.editing = id;
    TEXT_FIELDS.concat(DATE_FIELDS).forEach(function (n) { input(n).value = c[n] || ''; });
    NUMBER_FIELDS.forEach(function (n) { input(n).value = String(c[n]); });
    var locked = isScheduleLocked(c);
    setLocked(locked);
    lockNote.hidden = !locked;
    lockNote.textContent = c.computed
        ? '당첨자를 산정한 공고라 일정·공급 세대 수는 바꿀 수 없어요. (신청이 다시 열리지 않도록)'
        : '신청자가 ' + fmt(c.applicants) + '명 있어 일정·공급 세대 수는 바꿀 수 없어요. 신청 당시 조건을 지키기 위해서예요.';
    form.classList.add('is-editing');
    form.querySelector('[data-form-title]').textContent = c.name + ' 수정';
    form.querySelector('[data-form-editing]').hidden = false;
    submitBtn.textContent = '수정 저장';
    cancelBtn.hidden = false;
    renderList();
    form.scrollIntoView({ behavior: 'smooth', block: 'start' });
    input('name').focus({ preventScroll: true });
  }
  cancelBtn.addEventListener('click', resetForm);

  // 입력값만 보고 알 수 있는 규칙 (API 143 : 빈 칸 없음, 금액·세대 수 1 이상, 일정 순서). 서버도 다시 확인한다
  function readForm() {
    clearErrors();
    var body = {};
    var problems = [];
    TEXT_FIELDS.forEach(function (n) {
      var v = input(n).value.trim();
      if (!v) { input(n).classList.add('is-error'); problems.push('빈 칸을 채워 주세요'); }
      body[n] = v;
    });
    NUMBER_FIELDS.forEach(function (n) {
      var raw = input(n).value.replace(/,/g, '').trim();
      var ok = /^\d+$/.test(raw) && Number(raw) >= 1;
      if (!ok) { input(n).classList.add('is-error'); problems.push('분양가와 공급 세대 수는 1 이상의 숫자로 입력해 주세요'); }
      body[n] = Number(raw);
    });
    DATE_FIELDS.forEach(function (n) {
      var v = input(n).value;
      if (!v) { input(n).classList.add('is-error'); problems.push('일정 4개를 모두 입력해 주세요'); }
      body[n] = v;
    });
    if (!problems.length) {
      for (var i = 1; i < DATE_FIELDS.length; i++) {
        if (body[DATE_FIELDS[i - 1]] > body[DATE_FIELDS[i]]) {   // 'yyyy-MM-dd' 는 글자 순서 = 날짜 순서
          input(DATE_FIELDS[i]).classList.add('is-error');
          problems.push('일정 순서를 확인해 주세요 (공고일 ≤ 신청 시작일 ≤ 신청 마감일 ≤ 당첨자 발표일)');
          break;
        }
      }
    }
    if (problems.length) {
      formMsg.textContent = problems[0];
      return null;
    }
    return body;
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
      MB.toast(id ? '공고를 수정했어요' : '공고를 등록했어요 · ' + body.name);
      resetForm();
      return loadComplexes();
    }).catch(function (err) {
      formMsg.textContent = err.message;
      MB.toast(err.message, 'error');
      // 그사이 신청자가 생겼거나 산정됐으면 목록을 새로 받아 잠금 상태를 맞춘다
      if (err.code === 'COMPLEX_HAS_APPLICANTS' || err.code === 'ALREADY_COMPUTED' || err.code === 'NOT_FOUND') {
        loadComplexes().then(function () { if (id && find(id)) startEdit(id); else resetForm(); });
      }
    }).finally(function () {
      submitBtn.disabled = false;
    });
  });

  // ---------- 삭제 (API 145, Soft Delete) ----------
  function removeComplex(c) {
    confirmBox('공고를 삭제할까요?', c.name + '\n삭제한 공고는 사용자 화면과 이 목록에서 사라져요.', '삭제')
        .then(function (ok) {
          if (!ok) return null;
          return MB.api(API + '/' + c.complexId, { method: 'DELETE' }).then(function () {
            MB.toast('공고를 삭제했어요 · ' + c.name);
            if (state.editing === c.complexId) resetForm();
            return loadComplexes();
          });
        })
        .catch(function (err) {
          MB.toast(err.message, 'error');
          loadComplexes();
        });
  }

  // ---------- 신청 현황 (API 146) ----------
  // 산정 전 : 가점 → 납입회차 → 먼저 신청한 순 / 산정 뒤 : 산정 순위 순 (서버가 정렬해서 준다)
  var appPanel = root.querySelector('[data-panel="applicants"]');
  var appSelect = appPanel.querySelector('[data-app-complex]');
  var appState = { complexId: null };
  var RESULT_TEXT = { WIN: '당첨', LOSE: '낙첨' };

  function fillComplexOptions() {
    var keep = appState.complexId;
    appSelect.replaceChildren();
    state.items.forEach(function (c) {
      var o = el('option', null, c.name + ' · ' + (STATUS_TEXT[c.status] || c.status) + ' · 신청 ' + fmt(c.applicants) + '명');
      o.value = String(c.complexId);
      appSelect.appendChild(o);
    });
    if (keep && find(keep)) appSelect.value = String(keep);
  }
  onData.push(fillComplexOptions);

  function openApplicants(id) {
    appState.complexId = id;
    showTab('applicants');
  }

  onTab.applicants = function () {
    if (!state.loaded) return;                          // 목록을 받은 뒤 onData 가 다시 부른다
    fillComplexOptions();
    if (!appState.complexId || !find(appState.complexId)) {
      // 처음 열면 신청자가 있는 가장 최근 단지부터
      var first = state.items.filter(function (c) { return c.applicants > 0; })[0] || state.items[0];
      appState.complexId = first ? first.complexId : null;
    }
    if (appState.complexId) appSelect.value = String(appState.complexId);
    loadApplicants();
  };
  onData.push(function () {
    if (!appPanel.hidden) onTab.applicants();
  });
  appSelect.addEventListener('change', function () {
    appState.complexId = Number(appSelect.value);
    loadApplicants();
  });

  function setAppMessage(text) {
    var body = appPanel.querySelector('[data-list="applicants"]');
    var row = el('tr');
    var cell = el('td', 'ah-empty', text);
    cell.colSpan = 7;
    row.appendChild(cell);
    body.replaceChildren(row);
  }

  var appSeq = 0;                                       // 단지를 빠르게 바꿀 때 늦게 온 이전 단지 응답을 버린다
  function loadApplicants() {
    var c = find(appState.complexId);
    var summary = appPanel.querySelector('[data-app-summary]');
    if (!c) {
      summary.textContent = '';
      setAppMessage('등록된 공고가 없어요.');
      return Promise.resolve();
    }
    var competition = c.supplyUnits ? (c.applicants / c.supplyUnits).toFixed(2) : '0.00';
    summary.textContent = '공급 ' + fmt(c.supplyUnits) + '세대 · 신청 ' + fmt(c.applicants) + '명 · 경쟁률 ' + competition + ' : 1'
        + (c.computed ? ' · 산정 완료' : ' · 산정 전');
    appPanel.querySelector('[data-app-rank-head]').textContent = c.computed ? '순위' : '순서';
    setAppMessage('불러오는 중이에요…');
    var seq = ++appSeq;
    return MB.api(API + '/' + c.complexId + '/applications').then(function (data) {
      if (seq !== appSeq) return;
      renderApplicants((data && data.items) || [], c);
    }).catch(function (err) {
      if (seq !== appSeq) return;
      setAppMessage('신청자를 불러오지 못했어요. ' + err.message);
      MB.toast(err.message, 'error');
    });
  }

  function renderApplicants(items, c) {
    var body = appPanel.querySelector('[data-list="applicants"]');
    if (!items.length) {
      setAppMessage(c.status === 'UPCOMING' ? '아직 접수 전이에요.' : '신청자가 없어요.');
      return;
    }
    body.replaceChildren();
    items.forEach(function (a, i) {
      var tr = el('tr');
      tr.appendChild(el('td', null, String(a.resultRank != null ? a.resultRank : i + 1)));
      tr.appendChild(el('td', null, a.nickname));
      tr.appendChild(el('td', null, a.receiptNo));
      tr.appendChild(el('td', 'is-num', a.totalScore + '점'));
      tr.appendChild(el('td', 'is-num', fmt(a.paidCount) + '회'));
      tr.appendChild(el('td', null, dotted(String(a.appliedAt).slice(0, 16).replace('T', ' '))));
      var result = el('td');
      result.appendChild(el('span', 'ah-result' + (a.result ? ' is-' + a.result : ''), RESULT_TEXT[a.result] || '산정 전'));
      tr.appendChild(result);
      body.appendChild(tr);
    });
  }

  // ---------- 시작 ----------
  loadComplexes();
  var startTab = location.hash.replace('#', '');         // 주소 끝 #applicants, #results 로 바로 열기
  if (startTab === 'applicants' || startTab === 'results') showTab(startTab);
})();
