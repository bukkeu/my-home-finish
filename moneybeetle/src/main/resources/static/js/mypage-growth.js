/* comment.
 * 담당: DBA ② 김예린 (#69, 마이페이지 캐시 내역·경험치 내역·레벨 표 탭)
 * fragments/mypage/growth.html 의 #mg-growth 안을 API 로 채운다. 서버가 준 글은 모두 textContent 로 넣는다 (HTML 로 해석하지 않는다).
 * - GET /api/v1/members/me                   (API 13) 보유 캐시, 지금 레벨
 * - GET /api/v1/members/me/cash-histories    (API 22) 캐시 내역. 이번 달 합계는 이번 달 내역을 모두 읽어 계산한다 (50개씩, 최대 10쪽)
 * - GET /api/v1/members/me/exp-histories     (API 23) 최근 경험치 내역 5건
 * - GET /api/v1/levels                       (API 24) 레벨·칭호 표
 * 내 레벨·경험치·수상 내역은 틀(#54)의 프로필 카드가 보여 주므로 여기서 그리지 않는다.
 * 데이터는 틀이 탭을 열 때 보내는 mypage:tab 이벤트(detail.tab === 'growth')에서 처음 한 번만 불러온다. 화면이 처음부터 이 탭이면 바로 불러온다.
 */
(function () {
  'use strict';
  var root = document.getElementById('mg-growth');
  if (!root || !window.MB) return;

  var api = MB.api;
  var PAGE_SIZE = 50;
  var MAX_PAGES = 10;
  var UNLOCK_NAMES = { TRADING: '모의 투자', HOUSING: '모의 청약' };

  var cash = { items: [], page: 0, totalPages: 1, filter: 'all' };

  function byId(id) { return document.getElementById(id); }
  function el(tag, cls, text) {
    var node = document.createElement(tag);
    if (cls) node.className = cls;
    if (text != null) node.textContent = text;
    return node;
  }
  function won(n) { return Number(n || 0).toLocaleString('ko-KR'); }
  function pad(n) { return n < 10 ? '0' + n : String(n); }
  function dateText(iso) {
    var d = new Date(iso);
    return isNaN(d.getTime()) ? '' : d.getFullYear() + '.' + pad(d.getMonth() + 1) + '.' + pad(d.getDate());
  }
  function setStatus(text) { byId('mg-growth-status').textContent = text || ''; }
  function message(error) { return (error && error.code && error.message) || '불러오지 못했어요. 잠시 뒤 다시 시도해 주세요.'; }

  // ------------------------------------------------------------------ 캐시 내역
  function monthStart() {
    var now = new Date();
    return new Date(now.getFullYear(), now.getMonth(), 1).getTime();
  }

  function renderCash() {
    var list = byId('mg-cash-list');
    Array.prototype.slice.call(list.querySelectorAll('.mg-row, .mg-empty')).forEach(function (node) { node.remove(); });
    // 적립·사용은 금액의 부호로 가른다. historyType 은 EARN·USE 말고도 관리자 지급(ADMIN_GIVE)·회수(ADMIN_TAKE)가 있어서, 종류 값으로 거르면 놓친다
    var rows = cash.items.filter(function (item) {
      return cash.filter === 'all' || (cash.filter === 'EARN' ? item.amount > 0 : item.amount < 0);
    });
    if (!rows.length) {
      list.appendChild(el('p', 'mg-empty', '캐시 내역이 아직 없어요.'));
    }
    rows.forEach(function (item) {
      var earn = item.amount > 0;
      var row = el('div', 'mg-row');
      row.style.setProperty('--bg', earn ? '#EAF7D8' : '#FFF0D6');
      row.style.setProperty('--fg', earn ? '#3d6120' : '#8A5A00');
      row.appendChild(el('span', 'mg-row__kind', earn ? '적립' : '사용'));
      var text = el('span', 'mg-row__text');
      text.appendChild(el('strong', null, item.title));
      text.appendChild(el('span', null, dateText(item.createdAt)));
      row.appendChild(text);
      row.appendChild(el('span', 'mg-row__amount', (earn ? '+' : '−') + won(Math.abs(item.amount))));
      list.appendChild(row);
    });
    byId('mg-cash-more').hidden = cash.page + 1 >= cash.totalPages;
  }

  function sumThisMonth() {
    var start = monthStart();
    var inSum = 0, outSum = 0;
    cash.items.forEach(function (item) {
      if (new Date(item.createdAt).getTime() < start) return;
      if (item.amount > 0) inSum += item.amount; else outSum += Math.abs(item.amount);
    });
    byId('mg-cash-in').textContent = '+' + won(inSum);
    byId('mg-cash-out').textContent = '−' + won(outSum);
  }

  function fetchCashPage(page) {
    return api('/api/v1/members/me/cash-histories?page=' + page + '&size=' + PAGE_SIZE).then(function (data) {
      cash.items = cash.items.concat((data && data.items) || []);
      cash.page = page;
      cash.totalPages = (data && data.totalPages) || 1;
      return data;
    });
  }

  /** 이번 달 합계를 구하려고 이번 달 내역이 끝날 때(더 오래된 내역이 나올 때)까지 쪽을 넘겨 읽는다 */
  function loadCash() {
    var start = monthStart();
    function next(page) {
      return fetchCashPage(page).then(function () {
        var last = cash.items[cash.items.length - 1];
        var reachedOlder = last && new Date(last.createdAt).getTime() < start;
        if (!reachedOlder && page + 1 < cash.totalPages && page + 1 < MAX_PAGES) return next(page + 1);
      });
    }
    return next(0).then(function () { sumThisMonth(); renderCash(); });
  }

  function setupCash() {
    root.querySelectorAll('.mg-filter').forEach(function (button) {
      button.addEventListener('click', function () {
        cash.filter = button.dataset.filter;
        root.querySelectorAll('.mg-filter').forEach(function (other) { other.setAttribute('aria-pressed', String(other === button)); });
        renderCash();
      });
    });
    byId('mg-cash-more').addEventListener('click', function () {
      var button = byId('mg-cash-more');
      button.disabled = true;
      fetchCashPage(cash.page + 1).then(renderCash, function (error) { setStatus(message(error)); }).then(function () { button.disabled = false; });
    });
  }

  // ------------------------------------------------------------------ 경험치·레벨
  /** 레벨 → 집 종류 번호: 고시원 Lv.1~3, 원룸 4~7, 투룸 8~11, 아파트 12~16, 내 집 17~ (원본 houseBg) */
  function renderLevelTable(levels, myLevel) {
    var body = byId('mg-level-table');
    body.textContent = '';
    levels.forEach(function (row) {
      var tr = el('tr', row.level === myLevel ? 'is-now' : '');
      tr.appendChild(el('td', null, 'Lv.' + row.level));
      tr.appendChild(el('td', null, row.title));
      tr.appendChild(el('td', 'num', won(row.requiredExp) + ' XP'));
      tr.appendChild(el('td', 'num', row.levelupCash ? won(row.levelupCash) : '-'));
      tr.appendChild(el('td', null, (row.unlocks || []).map(function (name) { return UNLOCK_NAMES[name] || name; }).join(', ') || '-'));
      body.appendChild(tr);
    });
  }

  function loadExpHistory() {
    var list = byId('mg-exp-list');
    return api('/api/v1/members/me/exp-histories?page=0&size=5').then(function (data) {
      var items = (data && data.items) || [];
      if (!items.length) {
        list.appendChild(el('p', 'mg-empty', '경험치 내역이 아직 없어요.'));
        return;
      }
      items.forEach(function (item) {
        var row = el('div', 'mg-row');
        row.style.setProperty('--bg', '#EAF7D8');
        row.style.setProperty('--fg', '#3d6120');
        row.appendChild(el('span', 'mg-row__kind', 'XP'));
        var text = el('span', 'mg-row__text');
        text.appendChild(el('strong', null, item.title));
        text.appendChild(el('span', null, dateText(item.createdAt) + ' · Lv.' + item.levelAfter));
        row.appendChild(text);
        row.appendChild(el('span', 'mg-row__amount', '+' + won(item.amount)));
        list.appendChild(row);
      });
    }, function () {
      list.appendChild(el('p', 'mg-empty', '경험치 내역을 불러오지 못했어요.'));
    });
  }

  // ------------------------------------------------------------------ 시작
  function load() {
    setupCash();
    Promise.all([api('/api/v1/members/me'), api('/api/v1/levels')]).then(function (results) {
      var me = results[0] || {};
      var levels = (results[1] && results[1].levels) || [];
      setStatus('');
      byId('mg-cash').textContent = won(me.cash);
      renderLevelTable(levels, me.level);
      loadCash().catch(function (error) {
        setStatus(message(error));
      });
      loadExpHistory();
    }).catch(function (error) {
      setStatus(message(error));
    });
  }

  var started = false;
  function start() {
    if (started) return;
    started = true;
    load();
  }
  document.addEventListener('mypage:tab', function (event) {
    if (event.detail && event.detail.tab === 'growth') start();
  });
  var panel = root.closest('[data-panel]');
  if (panel && !panel.hidden) start();   // 틀이 이미 이 탭을 열어 둔 채로 시작한 경우
})();
