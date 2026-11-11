/* comment.
*   담당 : DBA ② 김예린 (#61, 관리자 대시보드)
*   대시보드 탭: 숫자 카드, 처리할 일, 최근 작업 기록
*   - GET /api/v1/admin/dashboard (API 84)  : 검수 대기 강의, 답변 대기 문의, 진행 중 시즌, 학습자 수
*   - GET /api/v1/admin/admin-logs?size=5 (API 113) : 최근 작업 기록 (설정·작업 기록 탭의 같은 표)
*   season 이 null 이면 진행 중 시즌이 없다. todayOrders · nextTurnReady 가 null 이면 아직 집계되지 않은 값이라 "-" 로 보여 준다.
*  */
(function () {
  'use strict';
  var el = MBA.el, byId = MBA.byId;
  var DASH = '/admin/dashboard';

  function stat(label, value, sub, href, hot) {
    var button = el('button', 'adm-stat' + (hot ? ' adm-stat--hot' : ''));
    button.type = 'button';
    button.appendChild(el('span', 'adm-stat__label', label));
    button.appendChild(el('span', 'adm-stat__value', value));
    button.appendChild(el('span', 'adm-stat__sub', sub));
    button.addEventListener('click', function () { window.location.href = href; });
    return button;
  }

  function renderStats(data) {
    var box = byId('adm-stats');
    MBA.clear(box);
    var season = data.season;
    box.appendChild(stat('검수 대기 강의', MBA.won(data.reviewWaiting) + '개', '강의 운영에서 허가·반려해요', DASH + '?view=courses', data.reviewWaiting > 0));
    box.appendChild(stat('답변 대기 문의', MBA.won(data.inquiryWaiting) + '건', '문의 게시판에서 확인해요', DASH + '?view=board', data.inquiryWaiting > 0));
    box.appendChild(stat('진행 중 시즌', season ? season.currentTurn + '턴' : '없음', season ? '시즌 #' + season.seasonId : '모의투자 운영에서 시즌을 만들어요', '/admin/trading-management', false));
    box.appendChild(stat('참가자', season ? MBA.won(season.participants) + '명' : '-', season ? (season.todayOrders == null ? '오늘 주문은 집계 전이에요' : '오늘 주문 ' + MBA.won(season.todayOrders) + '건') : '진행 중인 시즌이 없어요', '/admin/trading-management', false));
    box.appendChild(stat('학습자', MBA.won(data.members.total) + '명', '탈퇴 ' + MBA.won(data.members.withdrawn) + '명 포함', DASH + '?view=members', false));
  }

  function todoLink(label, action, href) {
    var link = el('a', 'adm-row--link');
    link.href = href;
    link.appendChild(el('span', 'adm-row__dot'));
    link.appendChild(el('span', 'adm-row__main', label));
    link.appendChild(el('span', 'adm-row__go', action + ' →'));
    return link;
  }

  function renderTodos(data) {
    var box = byId('adm-todos');
    MBA.clear(box);
    var count = 0;
    if (data.reviewWaiting > 0) { box.appendChild(todoLink('검수 대기 강의 ' + data.reviewWaiting + '개', '검수하기', DASH + '?view=courses')); count++; }
    if (data.inquiryWaiting > 0) { box.appendChild(todoLink('답변 대기 문의 ' + data.inquiryWaiting + '건', '확인하기', DASH + '?view=board')); count++; }
    var season = data.season;
    if (season && season.nextTurnReady === false) {
      var missing = season.missing || { prices: 0, news: 0 };
      box.appendChild(todoLink('다음 턴 점검: 시세 ' + missing.prices + '개 · 뉴스 ' + missing.news + '개가 비어 있어요', '입력하기', '/admin/trading-management'));
      count++;
    }
    if (!count) box.appendChild(el('p', 'adm-empty', '모두 처리했어요'));
  }

  function logLine(item) {
    var row = el('div', 'adm-row');
    var main = el('span', 'adm-row__main');
    main.appendChild(el('strong', null, item.action + ' · ' + item.targetType + ' #' + item.targetId));
    main.appendChild(el('span', 'adm-row__sub', (item.adminNickname || '관리자') + ' · ' + String(item.createdAt || '').replace('T', ' ').slice(0, 16)));
    row.appendChild(main);
    return row;
  }

  function loadDashboard() {
    MB.api('/api/v1/admin/dashboard').then(function (data) {
      renderStats(data);
      renderTodos(data);
    }, function (err) {
      MBA.clear(byId('adm-stats'));
      byId('adm-stats').appendChild(el('p', 'adm-empty', '대시보드를 불러오지 못했어요. ' + MBA.errorText(err)));
      MBA.clear(byId('adm-todos'));
    });
  }

  function loadLogs() {
    var box = byId('adm-recent-logs');
    MB.api('/api/v1/admin/admin-logs?size=5').then(function (data) {
      MBA.clear(box);
      var items = (data && data.items) || [];
      if (!items.length) { box.appendChild(el('p', 'adm-empty', '아직 작업 기록이 없어요')); return; }
      items.forEach(function (item) { box.appendChild(logLine(item)); });
    }, function () {
      MBA.clear(box);
      box.appendChild(el('p', 'adm-empty', '작업 기록을 불러오지 못했어요.'));
    });
  }

  loadDashboard();
  loadLogs();
})();
