/* comment.
*   담당 : DBA ② 김예린 (#61, 설정·작업 기록 탭)
*   - 서비스 설정: GET /api/v1/admin/settings (API 111), PUT /api/v1/admin/settings/{key} (API 112). 값은 문자열로 저장하고 항목별로 따로 저장한다.
*   - 관리자 작업 기록: GET /api/v1/admin/admin-logs (API 113). 대상 종류·기간으로 거르고 최신순으로 보여 주며, 한 번에 SIZE 건씩 더 불러온다.
*   서버가 보내는 key 는 그대로 쓰고, 아는 항목만 사람이 읽는 이름을 붙인다. 모르는 항목도 key 로 보여 준다.
*  */
(function () {
  'use strict';
  var el = MBA.el, byId = MBA.byId;
  var SIZE = 20;

  var LABELS = {
    QUIZ_PASS_SCORE: '퀴즈 통과 점수',
    UNLOCK_LEVEL_TRADING: '모의투자 해금 레벨',
    UNLOCK_LEVEL_HOUSING: '모의 청약 해금 레벨',
    INQUIRY_DAILY_LIMIT: '문의글 하루 작성 한도',
    LOGIN_MAX_FAIL: '로그인 잠금 실패 횟수',
    LOGIN_LOCK_MINUTES: '로그인 잠금 시간(분)'
  };

  /* ---------- 서비스 설정 ---------- */

  function settingRow(setting) {
    var row = el('div', 'adm-row adm-row--wrap');
    var main = el('span', 'adm-row__main');
    main.appendChild(el('strong', null, LABELS[setting.key] || setting.key));
    main.appendChild(el('span', 'adm-row__sub', setting.key));
    row.appendChild(main);
    var input = el('input', 'adm-input adm-input--sm');
    input.type = 'text';
    input.value = setting.value;
    input.setAttribute('aria-label', (LABELS[setting.key] || setting.key) + ' 값');
    var save = el('button', 'btn btn-secondary adm-btn-sm', '저장');
    save.type = 'button';
    save.setAttribute('aria-label', (LABELS[setting.key] || setting.key) + ' 저장');
    row.appendChild(input);
    row.appendChild(save);
    save.addEventListener('click', function () {
      var value = input.value.trim();
      if (!value) { MB.toast('값을 입력해 주세요.', 'error'); return; }
      save.disabled = true;
      MB.api('/api/v1/admin/settings/' + encodeURIComponent(setting.key), { method: 'PUT', body: { value: value } }).then(function () {
        MB.toast((LABELS[setting.key] || setting.key) + ' 값을 저장했어요');
        loadLogs(true);
      }, function (err) {
        MB.toast(MBA.errorText(err), 'error');
      }).then(function () { save.disabled = false; });
    });
    return row;
  }

  function loadSettings() {
    var box = byId('adm-settings');
    MB.api('/api/v1/admin/settings').then(function (data) {
      MBA.clear(box);
      var settings = (data && data.settings) || [];
      if (!settings.length) { box.appendChild(el('p', 'adm-empty', '등록된 설정이 없어요')); return; }
      settings.forEach(function (s) { box.appendChild(settingRow(s)); });
    }, function (err) {
      MBA.clear(box);
      box.appendChild(el('p', 'adm-empty', '설정을 불러오지 못했어요. ' + MBA.errorText(err)));
    });
  }

  /* ---------- 작업 기록 ---------- */

  var logState = { page: 0, query: '', loading: false };   // query: "조회"를 누를 때 저장한 조건 (더 보기가 이 조건을 쓴다)

  function detailText(detail) {
    if (detail == null) return '';
    try { return JSON.stringify(detail); } catch (e) { return ''; }
  }

  function logRow(item) {
    var row = el('div', 'adm-table__row');
    row.appendChild(el('span', null, String(item.createdAt || '').replace('T', ' ').slice(0, 19)));
    row.appendChild(el('span', null, item.adminNickname || '-'));
    row.appendChild(el('span', null, item.action + ' · ' + item.targetType + ' #' + item.targetId));
    row.appendChild(el('span', 'adm-table__detail', detailText(item.detail)));
    return row;
  }

  /** 지금 입력칸의 조건 (페이지 번호 빼고). "조회"를 누를 때만 읽어서 logState.query 에 저장한다 */
  function readFilter() {
    var params = [];
    var type = byId('adm-log-type').value.trim();
    var from = byId('adm-log-from').value;
    var to = byId('adm-log-to').value;
    if (type) params.push('targetType=' + encodeURIComponent(type));
    if (from) params.push('from=' + encodeURIComponent(from));
    if (to) params.push('to=' + encodeURIComponent(to));
    return params.join('&');
  }

  function loadLogs(reset) {
    if (logState.loading) return;   // 요청 중에는 조회·더 보기를 다시 보내지 않는다 (더 보기를 빠르게 두 번 눌러 쪽을 건너뛰지 않게)
    var page = reset ? 0 : logState.page + 1;
    if (reset) logState.query = readFilter();
    var box = byId('adm-logs');
    var more = byId('adm-log-more');
    var search = byId('adm-log-search');
    var query = (logState.query ? logState.query + '&' : '') + 'page=' + page + '&size=' + SIZE;
    logState.loading = true;
    more.disabled = true;
    search.disabled = true;
    MB.api('/api/v1/admin/admin-logs?' + query).then(function (data) {
      logState.page = page;   // 성공했을 때만 쪽 번호를 올린다 (실패하면 같은 쪽을 다시 받을 수 있다)
      var items = (data && data.items) || [];
      if (reset) {
        MBA.clear(box);
        var head = el('div', 'adm-table__row adm-table__row--head');
        ['시각', '관리자', '작업', '내용'].forEach(function (t) { head.appendChild(el('span', null, t)); });
        box.appendChild(head);
      }
      if (reset && !items.length) box.appendChild(el('p', 'adm-empty', '조건에 맞는 작업 기록이 없어요'));
      items.forEach(function (item) { box.appendChild(logRow(item)); });
      more.hidden = items.length < SIZE;   // 꽉 찬 쪽이면 다음 쪽이 더 있을 수 있다
    }, function (err) {
      if (reset) { MBA.clear(box); }
      box.appendChild(el('p', 'adm-empty', '작업 기록을 불러오지 못했어요. ' + MBA.errorText(err)));
      more.hidden = true;
    }).finally(function () {
      logState.loading = false;
      more.disabled = false;
      search.disabled = false;
    });
  }

  byId('adm-log-search').addEventListener('click', function () { loadLogs(true); });
  byId('adm-log-more').addEventListener('click', function () { loadLogs(false); });

  loadSettings();
  loadLogs(true);
})();
