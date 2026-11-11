/* comment.
 * 담당: DBA ② 김예린 (#69, 마이페이지 캐시 내역·경험치·레벨·수상 내역 탭)
 * fragments/mypage/growth.html 의 #mg-growth 안을 API 로 채운다. 서버가 준 글은 모두 textContent 로 넣는다 (HTML 로 해석하지 않는다).
 * - GET /api/v1/members/me                   (API 13) 레벨·칭호·경험치·캐시·연속 출석, memberId
 * - GET /api/v1/members/me/cash-histories    (API 22) 캐시 내역. 이번 달 합계는 이번 달 내역을 모두 읽어 계산한다 (50개씩, 최대 10쪽)
 * - GET /api/v1/members/me/exp-histories     (API 23) 최근 경험치 내역 5건
 * - GET /api/v1/levels                       (API 24) 레벨·칭호 표
 * - GET /api/v1/members/{memberId}/profile   (API 18) 수상 내역 (1~3위만 메달)
 */
(function () {
  'use strict';
  var root = document.getElementById('mg-growth');
  if (!root || !window.MB) return;

  var api = MB.api;
  var PAGE_SIZE = 50;
  var MAX_PAGES = 10;
  var HOUSE_COLORS = ['#EAF7D8', '#B9EF8E', '#B9EF8E', '#9fe870', '#F2C14E'];   // 고시원·원룸·투룸·아파트·내 집 (원본 houseBg)
  var UNLOCK_NAMES = { TRADING: '모의 투자', HOUSING: '모의 청약' };
  var RANKING_NAMES = { ASSET: '순자산', RETURN: '수익률' };

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
  function houseIndex(level) {
    return level <= 3 ? 0 : level <= 7 ? 1 : level <= 11 ? 2 : level <= 16 ? 3 : 4;
  }

  function renderLevel(me, levels) {
    byId('mg-cash').textContent = won(me.cash);
    byId('mg-level-now').textContent = 'Lv.' + me.level;
    var tier = byId('mg-level-tier');
    tier.textContent = me.title || '';
    tier.style.setProperty('--house', HOUSE_COLORS[houseIndex(me.level)]);
    byId('mg-level-streak').textContent = me.attendance ? me.attendance.streak + '일째 출석 중' : '';

    var current = levels.filter(function (row) { return row.level === me.level; })[0];
    var next = levels.filter(function (row) { return row.level === me.level + 1; })[0];
    var pct = 100;
    var note;
    if (next && current) {
      var span = next.requiredExp - current.requiredExp;
      pct = span > 0 ? Math.max(0, Math.min(100, Math.round((me.exp - current.requiredExp) * 100 / span))) : 0;
      note = '다음 레벨(Lv.' + next.level + ')까지 ' + won(me.expToNextLevel) + ' XP · 레벨업하면 캐시 ' + won(next.levelupCash) + ' 지급';
    } else {
      note = '최고 레벨이에요. 지금까지 모은 경험치는 ' + won(me.exp) + ' XP예요.';
    }
    var bar = byId('mg-level-bar');
    bar.style.setProperty('--pct', pct + '%');
    bar.setAttribute('aria-valuenow', String(pct));
    byId('mg-level-note').textContent = note;
  }

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

  // ------------------------------------------------------------------ 수상 내역
  var MEDAL_POS = ['0% 50%', '50% 50%', '100% 50%'];
  var MEDAL_DISC = ['#F4C542', '#D9DCE3', '#E3A06F'];

  /** 시즌 이름에서 숫자만 뽑아 메달 가운데에 적는다 (예: "시즌 5" → 5) */
  function seasonNumber(name) {
    var m = String(name || '').match(/\d+/);
    return m ? m[0] : '';
  }

  function renderAwards(awards) {
    var box = byId('mg-awards');
    box.textContent = '';
    var medals = (awards || []).filter(function (award) { return award.rank >= 1 && award.rank <= 3; });
    if (!medals.length) {
      box.appendChild(el('p', 'mg-empty', '아직 수상 내역이 없어요. 모의 투자 시즌에서 3위 안에 들면 메달이 쌓여요.'));
      return;
    }
    medals.forEach(function (award) {
      var item = el('div', 'mg-award');
      var medal = el('span', 'mg-medal');
      medal.setAttribute('role', 'img');
      var label = award.seasonName + ' · ' + (RANKING_NAMES[award.rankingType] || award.rankingType) + ' ' + award.rank + '위';
      medal.setAttribute('aria-label', label);
      medal.style.setProperty('--pos', MEDAL_POS[award.rank - 1]);
      var disc = el('span', null, seasonNumber(award.seasonName));
      disc.style.setProperty('--disc', MEDAL_DISC[award.rank - 1]);
      disc.setAttribute('aria-hidden', 'true');
      medal.appendChild(disc);
      item.appendChild(medal);
      var text = el('span', 'mg-award__text');
      text.appendChild(el('strong', null, award.seasonName));
      text.appendChild(el('span', null, (RANKING_NAMES[award.rankingType] || award.rankingType) + ' ' + award.rank + '위'));
      item.appendChild(text);
      box.appendChild(item);
    });
  }

  // ------------------------------------------------------------------ 시작
  setupCash();
  Promise.all([api('/api/v1/members/me'), api('/api/v1/levels')]).then(function (results) {
    var me = results[0] || {};
    var levels = (results[1] && results[1].levels) || [];
    setStatus('');
    renderLevel(me, levels);
    renderLevelTable(levels, me.level);
    loadCash().catch(function (error) {
      setStatus(message(error));
    });
    loadExpHistory();
    if (me.memberId != null) {
      api('/api/v1/members/' + encodeURIComponent(me.memberId) + '/profile').then(function (profile) {
        renderAwards(profile && profile.awards);
      }, function () {
        byId('mg-awards').textContent = '';
        byId('mg-awards').appendChild(el('p', 'mg-empty', '수상 내역을 불러오지 못했어요.'));
      });
    }
  }).catch(function (error) {
    setStatus(message(error));
  });
})();
