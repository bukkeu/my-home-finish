/* comment.
*   담당 : DBA ② 김예린 (#61, 홈 콘텐츠 탭 - 경제 용어·오늘의 용어)
*   - 날짜별 오늘의 경제 용어: GET /api/v1/admin/term-schedules?month= (API 99), PUT /api/v1/admin/term-schedules/{date} (API 100)
*     지정하지 않은 날은 자동 선정 결과를 미리 보여 주고(isAuto), 5개를 골라 저장하면 지정한 날이 된다. 지난 날짜는 바꿀 수 없다 (SP-46~47).
*   - 경제 용어 관리: GET·POST /api/v1/admin/economic-terms (API 95·96), PUT·DELETE .../{termId} (API 97·98)
*   오류는 서버가 주는 문구(PAST_DATE_LOCKED, TERMS_NOT_ENOUGH 등)를 그대로 보여 준다. 화면에서 값을 직접 만들어 저장하지 않는다.
*  */
(function () {
  'use strict';
  var el = MBA.el, byId = MBA.byId;
  var PICK = 5;

  var state = {
    month: MBA.seoulYearMonth(),
    days: [],
    terms: [],
    picked: null,        // 선택한 날짜 yyyy-MM-dd
    selected: [],        // 그 날짜에 고른 용어 번호들
    draft: { id: null, term: '', description: '', isActive: true }
  };

  function activeTerms() { return state.terms.filter(function (t) { return t.isActive; }); }
  function termName(id) {
    for (var i = 0; i < state.terms.length; i++) if (state.terms[i].economicTermId === id) return state.terms[i].term;
    return '(삭제된 용어)';
  }
  function isPast(date) { return date < MBA.seoulToday(); }
  function shiftMonth(delta) {
    var parts = state.month.split('-');
    var d = new Date(Date.UTC(Number(parts[0]), Number(parts[1]) - 1 + delta, 1));
    state.month = d.getUTCFullYear() + '-' + String(d.getUTCMonth() + 1).padStart(2, '0');
  }
  function dayLabel(date) {
    var d = new Date(date + 'T00:00:00Z');
    var names = ['일', '월', '화', '수', '목', '금', '토'];
    return Number(date.slice(5, 7)) + '월 ' + Number(date.slice(8, 10)) + '일 (' + names[d.getUTCDay()] + ')';
  }

  /* ---------- 날짜별 용어 ---------- */

  function renderMonth() {
    byId('adm-month-label').textContent = state.month.replace('-', '년 ') + '월';
    var box = byId('adm-days');
    MBA.clear(box);
    if (!state.days.length) { box.appendChild(el('p', 'adm-empty', '이 달의 일정을 불러오지 못했어요.')); return; }
    state.days.forEach(function (day) {
      var button = el('button', 'adm-day' + (day.date === state.picked ? ' is-picked' : '') + (isPast(day.date) ? ' is-past' : ''));
      button.type = 'button';
      button.setAttribute('aria-pressed', day.date === state.picked ? 'true' : 'false');
      var top = el('span', 'adm-day__top');
      top.appendChild(el('strong', null, dayLabel(day.date)));
      var tag = el('span', 'adm-tag' + (day.isAuto ? '' : ' adm-tag--set'), day.isAuto ? '자동' : '지정');
      top.appendChild(tag);
      if (isPast(day.date)) top.appendChild(el('span', 'adm-tag adm-tag--off', '지남'));
      button.appendChild(top);
      button.appendChild(el('span', 'adm-day__words', day.terms.map(function (t) { return t.term; }).join(' · ') || '-'));
      button.addEventListener('click', function () { pickDay(day); });
      box.appendChild(button);
    });
    // 처음 열 때는 오늘 이후 첫 날짜가 보이게 목록을 내려 둔다 (지난 날짜는 바꿀 수 없어서)
    var next = box.querySelector('.adm-day:not(.is-past)');
    if (next && !state.picked) box.scrollTop = Math.max(0, next.offsetTop - box.offsetTop);
  }

  function loadMonth() {
    return MB.api('/api/v1/admin/term-schedules?month=' + encodeURIComponent(state.month)).then(function (data) {
      state.days = (data && data.days) || [];
      renderMonth();
    }, function (err) {
      state.days = [];
      renderMonth();
      MB.toast(MBA.errorText(err), 'error');
    });
  }

  function pickDay(day) {
    state.picked = day.date;
    state.selected = day.terms.map(function (t) { return t.economicTermId; });
    renderMonth();
    renderEditor();
    byId('adm-editor').scrollIntoView({ block: 'nearest' });
  }

  function renderEditor() {
    var box = byId('adm-editor');
    box.hidden = !state.picked;
    if (!state.picked) return;
    var locked = isPast(state.picked);
    byId('adm-editor-title').textContent = dayLabel(state.picked) + ' 용어 지정';
    byId('adm-editor-count').textContent = locked
      ? '지난 날짜는 바꿀 수 없어요'
      : PICK + '개를 골라 주세요 · 선택 ' + state.selected.length + ' / ' + PICK;
    byId('adm-editor-error').textContent = '';

    var slots = byId('adm-slots');
    MBA.clear(slots);
    state.selected.forEach(function (id, index) {
      var slot = el('div', 'adm-slot');
      slot.appendChild(el('span', 'adm-slot__no', String(index + 1)));
      slot.appendChild(el('span', 'adm-slot__name', termName(id)));
      if (!locked) {
        var remove = el('button', 'btn btn-ghost adm-btn-sm', '빼기');
        remove.type = 'button';
        remove.setAttribute('aria-label', termName(id) + ' 빼기');
        remove.addEventListener('click', function () { state.selected.splice(index, 1); renderEditor(); });
        slot.appendChild(remove);
      }
      slots.appendChild(slot);
    });

    var select = byId('adm-add-term');
    MBA.clear(select);
    select.appendChild(el('option', null, '+ 용어 추가'));
    select.firstChild.value = '';
    activeTerms().filter(function (t) { return state.selected.indexOf(t.economicTermId) < 0; }).forEach(function (t) {
      var option = el('option', null, t.term);
      option.value = String(t.economicTermId);
      select.appendChild(option);
    });
    select.hidden = locked || state.selected.length >= PICK;
    byId('adm-save-day').hidden = locked;
  }

  function saveDay() {
    var error = byId('adm-editor-error');
    if (state.selected.length !== PICK) { error.textContent = '용어 ' + PICK + '개를 골라 주세요.'; return; }
    var button = byId('adm-save-day');
    if (button.disabled) return;
    button.disabled = true;   // 느린 응답 중에 다시 눌러 같은 PUT 이 겹치지 않게 막는다
    MB.api('/api/v1/admin/term-schedules/' + encodeURIComponent(state.picked), {
      method: 'PUT', body: { economicTermIds: state.selected }
    }).then(function () {
      MB.toast('오늘의 용어를 저장했어요');
      return loadMonth();
    }).then(function () {
      var day = state.days.filter(function (d) { return d.date === state.picked; })[0];
      if (day) pickDay(day);
    }, function (err) { error.textContent = MBA.errorText(err); })
      .finally(function () { button.disabled = false; });
  }

  /* ---------- 용어 목록 ---------- */

  function renderTerms() {
    byId('adm-term-count').textContent = '용어 ' + state.terms.length + '개 (사용 중 ' + activeTerms().length + '개)';
    var box = byId('adm-terms');
    MBA.clear(box);
    if (!state.terms.length) { box.appendChild(el('p', 'adm-empty', '등록된 용어가 없어요')); return; }
    state.terms.forEach(function (t, index) {
      var row = el('div', 'adm-row');
      row.appendChild(el('span', 'adm-row__no', String(index + 1)));
      var main = el('span', 'adm-row__main');
      var title = el('strong', null, t.term);
      if (!t.isActive) title.appendChild(el('span', 'adm-tag adm-tag--off', '사용 안 함'));
      main.appendChild(title);
      main.appendChild(el('span', 'adm-row__sub', t.description));
      row.appendChild(main);
      var edit = el('button', 'btn btn-secondary adm-btn-sm', '수정');
      edit.type = 'button';
      edit.setAttribute('aria-label', t.term + ' 수정');
      edit.addEventListener('click', function () { startEdit(t); });
      var del = el('button', 'btn btn-ghost adm-btn-sm adm-btn-danger', '삭제');
      del.type = 'button';
      del.setAttribute('aria-label', t.term + ' 삭제');
      del.addEventListener('click', function () { removeTerm(t); });
      row.appendChild(edit);
      row.appendChild(del);
      box.appendChild(row);
    });
  }

  // 한 페이지는 최대 100건(API 95)이라, 100건 미만인 마지막 페이지가 나올 때까지 이어서 받아 모은다
  var TERM_PAGE_SIZE = 100;
  function fetchTermPages(page, collected) {
    return MB.api('/api/v1/admin/economic-terms?page=' + page + '&size=' + TERM_PAGE_SIZE).then(function (data) {
      var items = (data && data.items) || [];
      var all = collected.concat(items);
      return items.length < TERM_PAGE_SIZE ? all : fetchTermPages(page + 1, all);
    });
  }

  function loadTerms() {
    return fetchTermPages(0, []).then(function (terms) {
      state.terms = terms;
      renderTerms();
      renderEditor();
    }, function (err) {
      MBA.clear(byId('adm-terms'));
      byId('adm-terms').appendChild(el('p', 'adm-empty', '용어를 불러오지 못했어요. ' + MBA.errorText(err)));
    });
  }

  function renderDraft() {
    byId('adm-term-word').value = state.draft.term;
    byId('adm-term-desc').value = state.draft.description;
    byId('adm-term-active').checked = state.draft.isActive;
    byId('adm-term-active-wrap').hidden = state.draft.id == null;
    byId('adm-term-save').textContent = state.draft.id == null ? '등록' : '수정 저장';
    byId('adm-term-cancel').hidden = state.draft.id == null;
    byId('adm-term-error').textContent = '';
  }

  function startEdit(t) {
    state.draft = { id: t.economicTermId, term: t.term, description: t.description, isActive: t.isActive };
    renderDraft();
    byId('adm-term-word').focus();
  }

  function saveTerm() {
    var term = byId('adm-term-word').value.trim();
    var description = byId('adm-term-desc').value.trim();
    var error = byId('adm-term-error');
    if (!term || !description) { error.textContent = '용어와 뜻을 모두 입력해 주세요.'; return; }
    var button = byId('adm-term-save');
    if (button.disabled) return;
    button.disabled = true;   // 연타로 같은 용어가 두 번 등록·수정 요청되지 않게 막는다
    var creating = state.draft.id == null;
    var request = creating
      ? MB.api('/api/v1/admin/economic-terms', { method: 'POST', body: { term: term, description: description } })
      : MB.api('/api/v1/admin/economic-terms/' + state.draft.id, {
          method: 'PUT', body: { term: term, description: description, isActive: byId('adm-term-active').checked }
        });
    request.then(function () {
      MB.toast(creating ? '용어를 등록했어요' : '용어를 수정했어요');
      state.draft = { id: null, term: '', description: '', isActive: true };
      renderDraft();
      return Promise.all([loadTerms(), loadMonth()]);
    }, function (err) { error.textContent = MBA.errorText(err); })
      .finally(function () { button.disabled = false; });
  }

  function removeTerm(t) {
    MBA.confirm('"' + t.term + '" 용어를 삭제할까요?', '이미 날짜에 지정된 적이 있는 용어는 지우지 않고 "사용 안 함"으로 바꿔요.', '삭제').then(function (yes) {
      if (!yes) return null;
      return MB.api('/api/v1/admin/economic-terms/' + t.economicTermId, { method: 'DELETE' }).then(function () {
        MB.toast('용어를 정리했어요');
        return Promise.all([loadTerms(), loadMonth()]);
      }, function (err) { MB.toast(MBA.errorText(err), 'error'); });
    });
  }

  /* ---------- 연결 ---------- */

  byId('adm-month-prev').addEventListener('click', function () { shiftMonth(-1); state.picked = null; renderEditor(); loadMonth(); });
  byId('adm-month-next').addEventListener('click', function () { shiftMonth(1); state.picked = null; renderEditor(); loadMonth(); });
  byId('adm-add-term').addEventListener('change', function (event) {
    var id = Number(event.target.value);
    if (id && state.selected.length < PICK) { state.selected.push(id); renderEditor(); }
  });
  byId('adm-save-day').addEventListener('click', saveDay);
  byId('adm-term-save').addEventListener('click', saveTerm);
  byId('adm-term-cancel').addEventListener('click', function () {
    state.draft = { id: null, term: '', description: '', isActive: true };
    renderDraft();
  });

  renderDraft();
  loadTerms().then(loadMonth);
})();
