/* comment.
*   담당 : PM 이재성 (#57, 모의투자 화면)
*   templates/trading/trading.html 의 데이터를 API 53~62 로 채운다. 요청은 공통 MB.api(common.js)로 보낸다 (CSRF·응답 형식·401 처리).
*   - 53 GET  /api/v1/trading/home                       : 시즌·턴 달력, 현금, 순자산, 보유 종목 (participation 이 null 이면 참가 버튼)
*   - 54 POST /api/v1/trading/seasons/{id}/participations : 시즌 참가
*   - 55 GET  /sectors, 56 GET /stocks(sort·sectorId·keyword), 57 GET /stocks/{id}, 58 GET /news(turnNo·stockId)
*   - 59 GET  /trade-reasons, 60 POST /orders(Idempotency-Key), 61 GET /orders, 62 GET /seasons/{id}/rankings
*   403 LEVEL_LOCKED  -> 본문을 흐리게 하고 잠금 안내 (NFR-09), NO_RUNNING_SEASON -> "다음 시즌을 준비 중이에요"
*   서버·사용자 문구는 모두 textContent 로 넣는다 (HTML 로 해석하지 않는다).
*   화면 마크업·클래스 이름은 디자인(UserDemoTrading.dc.html, new1/App.jsx)과 같다.
*  */
(function () {
  'use strict';
  if (!window.MB || !window.MB.api) return;

  var API = '/api/v1/trading';
  // API 명세서 4장 에러 코드 표의 문구 (주문·참가에서 그대로 보여 준다)
  var MSG = {
    NOT_ENOUGH_CASH: '현금이 부족해요',
    NOT_ENOUGH_QUANTITY: '보유한 수량보다 많이 팔 수 없어요',
    REASON_REQUIRED: '매매 이유를 골라 주세요',
    STALE_TURN_PRICE: '가격이 바뀌었어요. 현재 가격을 확인해 주세요',
    MARKET_HOLIDAY: '오늘은 휴장일이에요. 다음 거래일에 다시 거래할 수 있어요',
    SEASON_CLOSED: '시즌이 끝났어요. 결과를 기다려 주세요',
    ALREADY_JOINED: '이미 이번 시즌에 참가했어요',
    JOIN_CLOSED: '이번 시즌 참가는 마감됐어요',
    NO_RUNNING_SEASON: '다음 시즌을 준비 중이에요',
    RANKING_NOT_PUBLISHED: '랭킹은 시즌이 끝나면 공개돼요'
  };
  // 시즌 상태별 제목 꼬리말과 안내 띠 (API 53 season.status : RUNNING·HOLIDAY·SETTLING·ENDED). 정산 중·종료에도 홈이 열린다
  var STATUS_LABEL = { RUNNING: '진행 중', HOLIDAY: '휴장 중', SETTLING: '정산 중', ENDED: '종료' };
  var STATUS_NOTICE = {
    HOLIDAY: MSG.MARKET_HOLIDAY + ' · 시세와 뉴스는 그대로 볼 수 있어요',
    SETTLING: MSG.SEASON_CLOSED + ' · 시세와 뉴스는 마지막 턴까지 볼 수 있어요',
    ENDED: '시즌이 끝났어요 · 랭킹과 결과를 확인해 보세요'
  };
  var REFRESH_DELAY_MS = 1500;      // 다음 턴 시각이 지나고 서버가 턴을 넘길 시간을 잠깐 기다린다
  var REFRESH_RETRY_MS = 10000;     // 그래도 같은 시각이면 10초마다 다시 확인한다
  var SETTLE_POLL_MS = 60000;       // 정산 중 홈 확인 간격
  var WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토'];
  var TONES = ['mint', 'orange', 'blue', 'purple', 'pink', 'yellow'];
  // 디자인의 랭킹 행 안쪽 배치 (디자인이 인라인 스타일로 둔 값)
  var RANK_NAME_STYLE = 'display:flex;flex-direction:column;align-items:flex-start;gap:4px;min-width:0';
  var RANK_WHO_STYLE = 'display:flex;align-items:center;gap:6px;min-width:0;white-space:nowrap';
  var RANK_NICK_STYLE = 'overflow:hidden;text-overflow:ellipsis';

  var S = {
    season: null, participation: null, stocks: [], sectors: null, reasons: null,
    stock: null, side: 'BUY', reasonId: null, rankType: 'ASSET', marketTab: 'popular',
    busy: false, timer: null, locked: false,
    stopped: false,     // 잠금·시즌 없음으로 화면이 바뀌면 true -> 턴·정산 확인을 더 하지 않는다
    pendingOrder: null  // 결과를 모르는 주문 { sig, key } -> 같은 내용으로 다시 내면 같은 Idempotency-Key 를 쓴다
  };

  /* ---------- 작은 도우미 ---------- */
  function $(id) { return document.getElementById(id); }
  function el(tag, cls, text) {
    var n = document.createElement(tag);
    if (cls) n.className = cls;
    if (text != null) n.textContent = text;
    return n;
  }
  function fmt(n) { return n == null ? '-' : Number(n).toLocaleString('ko-KR'); }
  function won(n) { return fmt(n) + ' 캐시'; }
  /** 등락 문구: 디자인처럼 ▲ +2.8% / ▼ −0.9% (소수 첫째 자리, 음수는 −) */
  function rateText(v) {
    return (v > 0 ? '▲ +' : v < 0 ? '▼ −' : '') + Math.abs(v).toFixed(1) + '%';
  }
  function rateClass(v) { return v > 0 ? 'up' : v < 0 ? 'down' : 'trd-flat'; }
  /** 등락 표시 요소. 디자인이 자리마다 다른 태그(span·b·small·strong)를 써서 tag 로 고른다 */
  function rate(r, tag) {
    if (r == null) return el(tag || 'span', 'trd-flat', '-');
    var v = Number(r);
    return el(tag || 'span', rateClass(v), rateText(v));
  }
  /** 자리가 정해진 요소(#id)에 등락을 채운다 */
  function setRate(node, r) {
    if (r == null) { node.className = 'trd-flat'; node.textContent = '-'; return; }
    var v = Number(r);
    node.className = rateClass(v);
    node.textContent = rateText(v);
  }
  function note(node, text, isError) {
    node.replaceChildren(el('p', 'trd-empty' + (isError ? ' is-error' : ''), text));
  }
  function loading(node) { note(node, '불러오는 중이에요'); }
  function errText(err) { return (err && MSG[err.code]) || (err && err.message) || '요청을 처리하지 못했어요.'; }
  function show(node, on) { node.hidden = !on; }
  function uuid() {
    if (window.crypto && crypto.randomUUID) return crypto.randomUUID();
    var b = crypto.getRandomValues(new Uint8Array(16));
    b[6] = (b[6] & 15) | 64; b[8] = (b[8] & 63) | 128;
    var h = Array.prototype.map.call(b, function (x) { return (x + 256).toString(16).slice(1); }).join('');
    return h.slice(0, 8) + '-' + h.slice(8, 12) + '-' + h.slice(12, 16) + '-' + h.slice(16, 20) + '-' + h.slice(20);
  }
  function api(path, opts) { return MB.api(API + path, opts); }
  function pad2(n) { return String(n).padStart(2, '0'); }

  /** 화면 전체에 걸리는 오류 (잠금·시즌 없음). 처리했으면 true */
  function fatal(err) {
    if (!err) return false;
    if (err.code === 'LEVEL_LOCKED') { stopTimers(); lock(err.message); return true; }
    if (err.code === 'NO_RUNNING_SEASON') {
      stopTimers();
      fullState(MSG.NO_RUNNING_SEASON, '진행 중인 시즌이 없어요. 시즌이 시작되면 이곳에서 참가할 수 있어요.');
      return true;
    }
    return false;
  }
  /** 각 영역의 오류: 잠금·시즌 없음이면 화면 전체로, 아니면 그 영역에만 문구를 둔다 */
  function sectionError(node) {
    return function (err) { if (!fatal(err)) note(node, errText(err), true); };
  }

  /** 화면 전체 오류 뒤에는 카운트다운·정산 확인을 멈춘다 (다시 예약하지 않는다) */
  function stopTimers() {
    S.stopped = true;
    clearInterval(S.timer);
    clearTimeout(S.timer);
  }

  function lock(message) {
    if (S.locked) return;
    S.locked = true;
    var lv = (window.MB_CONFIG && MB_CONFIG.unlock && MB_CONFIG.unlock.demoTrading) || 8;
    $('trd-lock').querySelector('strong').textContent = '모의투자는 Lv.' + lv + '부터 이용할 수 있어요';
    if (message) $('trd-lock-desc').textContent = message + ' · 강의를 완료하고 경험치를 모아 보세요.';
    $('trd-app').dataset.state = 'locked';
    $('trd-body').classList.add('mb-blur');
    $('trd-body').setAttribute('aria-hidden', 'true');
    show($('trd-lock'), true);
  }

  function fullState(title, desc) {
    $('trd-state-title').textContent = title;
    $('trd-state-desc').textContent = desc || '';
    show($('trd-state'), true);
    ['trd-home', 'trd-all', 'trd-detail'].forEach(function (id) { show($(id), false); });
    $('trd-app').dataset.state = 'empty';
  }

  /** 화면 전환. 디자인처럼 홈은 home-page, 나머지는 detail-page 클래스를 쓴다 */
  function go(view) {
    show($('trd-home'), view === 'home');
    show($('trd-all'), view === 'all');
    show($('trd-detail'), view === 'detail');
    var app = $('trd-app');
    app.classList.toggle('home-page', view === 'home');
    app.classList.toggle('detail-page', view !== 'home');
    window.scrollTo(0, 0);
  }

  /* ---------- 모의투자 홈 (API 53) ---------- */
  function loadHome() {
    return api('/home').then(function (d) {
      S.season = d.season;
      S.participation = d.participation || null;
      $('trd-app').dataset.state = 'ready';
      renderSeason(d.season);
      renderBalance(d.season, S.participation);
      renderPortfolio(S.participation, d.holdings || []);
      return d;
    });
  }

  function renderSeason(season) {
    var closed = season.status === 'SETTLING' || season.status === 'ENDED';
    var num = /(\d+)/.exec(season.name || '');
    $('trd-eyebrow').textContent = 'VIRTUAL MARKET' + (num ? ' · SEASON ' + num[1] : '');
    $('trd-season-title').textContent = season.name + ' ' + (STATUS_LABEL[season.status] || STATUS_LABEL.RUNNING);
    $('trd-turn').textContent = season.currentTurn + '턴 / ' + season.totalTurns + '턴';
    var bar = $('trd-season-notice');
    var notice = STATUS_NOTICE[season.status] || '';
    bar.textContent = notice;
    show(bar, !!notice);

    var end = season.endDate ? new Date(season.endDate + 'T00:00:00') : null;
    var today = new Date(); today.setHours(0, 0, 0, 0);
    var days = end ? Math.round((end - today) / 86400000) : null;
    $('trd-dday').textContent = closed ? STATUS_LABEL[season.status]
      : days == null ? '-' : days > 0 ? 'D−' + days : 'D-DAY';

    var cal = $('trd-calendar');
    cal.replaceChildren();
    (season.calendar || []).forEach(function (day, i, all) {
      var settle = i === all.length - 1 && day.turnNo == null;
      var state = String(day.state || '').toLowerCase();
      var cls = settle ? 'settlement' : state === 'done' ? 'complete' : state === 'holiday' ? 'closed' : state;
      var cell = el('div', 'turn-cell ' + cls);
      var wd = el('span', 'weekday');
      if (state === 'done') wd.appendChild(el('i', null, '✓'));
      wd.appendChild(document.createTextNode(WEEKDAYS[new Date(day.date + 'T00:00:00').getDay()]));
      cell.appendChild(wd);
      cell.appendChild(el('small', null, day.turnNo != null ? day.turnNo + '턴' : settle ? '정산 · 랭킹 공개' : '휴장'));
      cell.title = day.date;
      cal.appendChild(cell);
    });
    startCountdown(season.nextTurnAt, season.status);
  }

  /** 다음 턴까지 남은 시간. 0 이 되면 홈과 보고 있는 종목을 다시 불러온다 (INV-05) */
  function startCountdown(nextTurnAt, status) {
    clearInterval(S.timer);
    clearTimeout(S.timer);
    if (S.stopped) return;
    var out = $('trd-next-turn');
    if (status === 'SETTLING') { out.textContent = '정산을 기다리는 중이에요'; scheduleSettlePoll(); return; }
    if (status === 'ENDED') { out.textContent = '시즌이 끝났어요'; return; }
    if (!nextTurnAt) { out.textContent = '마지막 턴이에요'; return; }
    var target = new Date(nextTurnAt).getTime();
    var tick = function () {
      var left = Math.max(0, Math.floor((target - Date.now()) / 1000));
      var hh = pad2(Math.floor(left / 3600));
      var mm = pad2(Math.floor(left % 3600 / 60));
      var ss = pad2(left % 60);
      // 디자인처럼 남은 시간만 굵게 보여 준다
      out.replaceChildren(document.createTextNode('다음 턴까지 '), el('b', null, hh + ':' + mm + ':' + ss));
      if (left === 0) {
        clearInterval(S.timer);
        // 같은 시각으로 또 0이 되면 서버가 아직 턴을 넘기지 않은 것(또는 PC 시계가 빠른 것)이다 -> 천천히 다시 확인한다.
        // 주문·참가 뒤의 일반 새로고침은 0이 되지 않으므로 이 판단에 끼지 않는다
        var repeated = S.expiredTarget === target;
        S.expiredTarget = target;
        setTimeout(function () {
          if (S.stopped) return;
          refreshAll(true).then(function (ok) {
            if (!ok) startCountdown(nextTurnAt, status);   // 새로고침이 실패했으면 멈추지 않고 다시 확인한다
          });
        }, repeated ? REFRESH_RETRY_MS : REFRESH_DELAY_MS);
      }
    };
    // 반복 타이머를 먼저 걸어야 이미 지난 시각으로 시작한 첫 tick 이 그 타이머를 끌 수 있다 (새로고침 중복 예약 방지)
    S.timer = setInterval(tick, 1000);
    tick();
  }

  /** 정산 중에는 1분마다 홈을 다시 불러, 종료(랭킹 공개)가 되면 랭킹도 불러온다. 조회가 실패해도 계속 확인한다 */
  function scheduleSettlePoll() {
    if (S.stopped) return;
    S.settlePending = true;
    clearTimeout(S.timer);
    S.timer = setTimeout(pollSettle, SETTLE_POLL_MS);
  }
  function pollSettle() {
    S.settlePending = false;
    refreshAll(true).then(function () {
      var status = S.season && S.season.status;
      if (status === 'ENDED') loadRanking();
      else if (status === 'SETTLING' && !S.settlePending && !S.stopped) scheduleSettlePoll();
    });
  }

  function renderBalance(season, p) {
    var canJoin = season.status !== 'SETTLING' && season.status !== 'ENDED';
    show($('trd-joined'), !!p);
    show($('trd-join'), !p);
    show($('trd-start-btn'), !!p);
    show($('trd-join-btn'), !p && canJoin);
    if (p) {
      $('trd-cash').textContent = fmt(p.cash);
      $('trd-invest').textContent = won(p.investAmount);
      // 시작 자금 구성 (= 모은 캐시 + 이월금) : API 가 두 값을 주면 디자인처럼 풀어 보여 준다
      var parts = $('trd-invest-parts');
      parts.replaceChildren();
      if (p.savedCash != null && p.carryoverIn != null) {
        parts.append(document.createTextNode(' = 모은 캐시 '), el('strong', null, won(p.savedCash)),
          document.createTextNode(' + 이월금 '), el('strong', null, won(p.carryoverIn)));
      }
    } else if (!canJoin) {
      // 정산 중·종료 시즌은 참가할 수 없다 (서버도 JOIN_CLOSED)
      $('trd-join-title').textContent = MSG.JOIN_CLOSED;
    } else {
      $('trd-join-title').textContent = season.name + '에 참가할까요?';
      $('trd-join-btn').replaceChildren(document.createTextNode(season.name + ' 참가하기 '), el('span', null, '→'));
    }
  }

  function renderPortfolio(p, holdings) {
    $('trd-net').textContent = p ? fmt(p.netAsset) : '-';
    var rateBox = $('trd-rate');
    if (p) setRate(rateBox, p.returnRate); else { rateBox.className = ''; rateBox.textContent = ''; }
    var box = $('trd-holdings');
    var head = el('div', 'holding-head');
    head.append(el('span', null, '보유 종목 ' + holdings.length + '개'), el('span', null, '평가금액'), el('span', null, '수익률'));
    box.replaceChildren(head);
    if (!p) { box.appendChild(el('p', 'trd-empty', '시즌에 참가하면 내 주식이 여기에 보여요')); return; }
    if (!holdings.length) { box.appendChild(el('p', 'trd-empty', '아직 보유한 종목이 없어요. 오늘의 가상 시세에서 종목을 골라 보세요')); return; }
    holdings.forEach(function (h, i) {
      var row = el('button', 'holding-row');
      row.type = 'button';
      row.dataset.trdStock = h.stockId;
      var name = el('span');
      name.append(el('i', 'mini-logo color-' + (i % TONES.length), (h.stockName || '?').charAt(0)), document.createTextNode(h.stockName));
      row.append(name, el('strong', null, won(h.evaluation)), rate(h.returnRate, 'b'));
      box.appendChild(row);
    });
  }

  function join() {
    if (!S.season || S.busy) return;
    var btn = $('trd-join-btn');
    var errBox = $('trd-join-error');
    S.busy = true; btn.disabled = true; show(errBox, false);
    api('/seasons/' + S.season.seasonId + '/participations', { method: 'POST' })
      .then(function () {
        MB.toast(S.season.name + '에 참가했어요. 투자 가능 금액이 확정됐어요');
        refreshAll();
      })
      .catch(function (err) {
        if (fatal(err)) return;
        errBox.textContent = errText(err); show(errBox, true);
        if (err.code === 'ALREADY_JOINED') refreshAll();
      })
      .then(function () { S.busy = false; btn.disabled = false; });
  }

  /* ---------- 거래 내역 (API 61) ---------- */
  function loadTrades() {
    var box = $('trd-trade-list');
    var count = $('trd-trade-count');
    show(count, false);
    var head = el('div', 'trade-row trade-head');
    ['턴', '종목', '구분', '수량', '체결가', '매매 이유'].forEach(function (t) { head.appendChild(el('span', null, t)); });
    var withHead = function (node) { box.replaceChildren(head, node); };
    if (!S.participation) { withHead(el('p', 'trd-empty', '시즌에 참가하면 거래 내역이 여기에 보여요')); return; }
    withHead(el('p', 'trd-empty', '불러오는 중이에요'));
    api('/orders?seasonId=' + S.season.seasonId + '&page=0&size=20').then(function (d) {
      var items = d.items || [];
      count.textContent = '총 ' + fmt(d.totalCount != null ? d.totalCount : items.length) + '건';
      show(count, true);
      if (!items.length) { withHead(el('p', 'trd-empty', '아직 거래하지 않았어요')); return; }
      box.replaceChildren(head);
      items.forEach(function (t) {
        var row = el('div', 'trade-row');
        var buy = t.orderType === 'BUY';
        var turn = el('span');
        turn.appendChild(el('b', 'turn-badge', t.turnNo + '턴'));
        row.append(turn, el('strong', null, t.stockName), el('span', buy ? 'trade-buy' : 'trade-sell', buy ? '매수' : '매도'),
          el('span', null, fmt(t.quantity) + '주'), el('span', null, won(t.price)), el('span', 'trade-reason', t.reason || '-'));
        box.appendChild(row);
      });
    }).catch(function (err) { if (!fatal(err)) withHead(el('p', 'trd-empty is-error', errText(err))); });
  }

  /* ---------- 뉴스 (API 58) ---------- */
  function loadTurnNews() {
    var box = $('trd-news-box');
    var head = el('div', 'news-card-header');
    var title = el('div');
    title.append(el('h2', null, '이번 턴 뉴스'), el('span', 'turn-badge', S.season.currentTurn + '턴'));
    head.append(title, el('p', null, '가격에 영향을 줄 소식을 확인해 보세요'));
    box.replaceChildren(head, el('p', 'trd-empty', '불러오는 중이에요'));
    api('/news?turnNo=' + S.season.currentTurn).then(function (d) {
      var items = d.items || [];
      if (!items.length) { box.replaceChildren(head, el('p', 'trd-empty', '이번 턴에 공개된 뉴스가 없어요')); return; }
      box.replaceChildren(head);
      items.slice(0, 3).forEach(function (n, i) {
        // 홈의 뉴스는 관련 종목의 뉴스 탭으로 이어 준다
        var stock = S.stocks.find(function (s) { return (n.stocks || []).indexOf(s.name) >= 0; });
        var card = el('button', 'news-card');
        card.type = 'button';
        if (stock) { card.dataset.trdStock = stock.stockId; card.dataset.trdOpenTab = 'news'; }
        var body = el('div', 'news-row-content');
        var tags = el('div', 'tag-row');
        (n.sectors || []).forEach(function (s) { tags.appendChild(el('span', 'sector-tag', s)); });
        (n.stocks || []).forEach(function (s) { tags.appendChild(el('span', null, s)); });
        body.append(el('span', 'turn-badge', n.turnNo + '턴'), el('h3', null, n.title), tags);
        card.append(el('div', 'news-index', pad2(i + 1)), body, el('span', 'arrow', '↗'));
        box.appendChild(card);
      });
    }).catch(function (err) { if (!fatal(err)) box.replaceChildren(head, el('p', 'trd-empty is-error', errText(err))); });
  }

  /* ---------- 시세·분야 (API 55, 56) ---------- */
  function avatar(name, id) {
    return el('span', 'stock-avatar ' + TONES[Number(id || 0) % TONES.length], (name || '?').charAt(0));
  }
  function stockRow(s) {
    var row = el('button', 'stock-row');
    row.type = 'button';
    row.dataset.trdStock = s.stockId;
    var name = el('span', 'stock-name');
    name.append(el('strong', null, s.name), el('small', null, s.sectorName || ''));
    var price = el('span', 'stock-price');
    price.append(el('strong', null, s.price == null ? '가격 준비 중' : won(s.price)), rate(s.changeRate, 'small'));
    row.append(avatar(s.name, s.stockId), name, price);
    return row;
  }
  /** 종목 목록 요청. 화면·공유 상태는 건드리지 않고 { stocks, turnNo } 만 돌려준다: 늦게 온 이전 응답이 화면을 덮지 않도록 호출한 쪽이 최신 요청인지 확인한 뒤 반영한다 */
  function fetchStocks(query) {
    return api('/stocks' + (query ? '?' + query : '')).then(function (d) {
      return { stocks: d.stocks || [], turnNo: d.turnNo };
    });
  }
  function applyTurn(turnNo) {
    if (turnNo != null) $('trd-market-turn').textContent = turnNo + '턴 기준';
  }
  /** 영역 오류 처리: 최신 요청이면 그 영역에 문구를 두고, 늦은 요청이면 화면 전체 오류(잠금·시즌 없음)만 처리한다 */
  function sectionErrorIf(isCurrent, node) {
    var show = sectionError(node);
    return function (err) { if (isCurrent()) show(err); else fatal(err); };
  }
  function fetchSectors() {
    if (S.sectors) return Promise.resolve(S.sectors);
    return api('/sectors').then(function (d) { S.sectors = d.sectors || []; return S.sectors; });
  }

  function loadMarket() {
    var list = $('trd-market-list');
    var q = $('trd-search').value.trim();
    // 검색어·탭을 바꾸면 이전 요청의 늦은 응답은 목록·턴 표시를 바꾸지 못한다
    var req = S.marketReq = (S.marketReq || 0) + 1;
    var current = function () { return req === S.marketReq; };
    loading(list);
    if (q) {
      fetchStocks('keyword=' + encodeURIComponent(q)).then(function (r) {
        if (!current()) return;
        applyTurn(r.turnNo);
        var stocks = r.stocks;
        if (!stocks.length) { note(list, '검색 결과가 없어요. 종목명을 다시 확인해 주세요'); return; }
        list.replaceChildren.apply(list, stocks.slice(0, 8).map(stockRow));
      }).catch(sectionErrorIf(current, list));
      return;
    }
    if (S.marketTab === 'sector') {
      fetchSectors().then(function (sectors) {
        if (!current()) return;
        if (!sectors.length) { note(list, '등록된 분야가 없어요'); return; }
        list.replaceChildren.apply(list, sectors.map(function (sec) {
          var row = el('button', 'stock-row');
          row.type = 'button';
          row.dataset.trdSector = sec.sectorId;
          var name = el('span', 'stock-name');
          name.append(el('strong', null, sec.name),
            el('small', null, (sec.stocks || []).map(function (s) { return s.name; }).join(' · ')));
          var cnt = el('span', 'stock-price');
          cnt.append(el('strong', null, (sec.stocks || []).length + '종목'), el('small', 'trd-flat', '›'));
          row.append(avatar(sec.name, sec.sectorId), name, cnt);
          return row;
        }));
      }).catch(sectionErrorIf(current, list));
      return;
    }
    fetchStocks('sort=popular').then(function (r) {
      if (!current()) return;
      applyTurn(r.turnNo);
      var stocks = r.stocks;
      if (!stocks.length) { note(list, '아직 등록된 종목이 없어요'); return; }
      list.replaceChildren.apply(list, stocks.slice(0, 8).map(stockRow));
    }).catch(sectionErrorIf(current, list));
  }

  /* ---------- 전체 종목 (INV-06) ---------- */
  /** 머리말 오른쪽: "5턴 기준 · 15개 분야". 시세와 분야 목록은 따로 도착하므로 둘 다 도착한 뒤에 채운다 */
  function updateAllMeta(sectorId) {
    var count = (S.sectors || []).length;
    $('trd-all-meta').textContent = $('trd-market-turn').textContent + (count && !sectorId ? ' · ' + count + '개 분야' : '');
  }
  function showAll(sectorId, keyword) {
    go('all');
    var list = $('trd-all-list');
    var chips = $('trd-sector-chips');
    // 분야·검색어를 바꾸면 이전 요청의 늦은 응답은 목록·분야 버튼·공유 상태(S.stocks)를 바꾸지 못한다
    var req = S.allReq = (S.allReq || 0) + 1;
    var current = function () { return req === S.allReq; };
    loading(list);
    fetchSectors().then(function (sectors) {
      if (!current()) return;
      var all = el('button', sectorId || keyword ? '' : 'selected', '전체');
      all.type = 'button'; all.dataset.trdSector = '';
      chips.replaceChildren(all);
      sectors.forEach(function (sec) {
        var c = el('button', String(sec.sectorId) === String(sectorId) ? 'selected' : '', sec.name);
        c.type = 'button'; c.dataset.trdSector = sec.sectorId;
        chips.appendChild(c);
      });
      updateAllMeta(sectorId);
    }).catch(function (err) { fatal(err); if (current()) chips.replaceChildren(); });
    var query = sectorId ? 'sectorId=' + encodeURIComponent(sectorId) : keyword ? 'keyword=' + encodeURIComponent(keyword) : '';
    fetchStocks(query).then(function (r) {
      if (!current()) return;
      applyTurn(r.turnNo);
      var stocks = r.stocks;
      if (!sectorId && !keyword) S.stocks = stocks;
      $('trd-all-title').textContent = sectorId ? (stocks[0] ? stocks[0].sectorName : '분야') + ' 종목'
        : keyword ? '‘' + keyword + '’ 검색 결과 ' + stocks.length + '개' : '전체 ' + stocks.length + '개 종목';
      updateAllMeta(sectorId);
      if (!stocks.length) { note(list, keyword ? '검색 결과가 없어요. 종목명을 다시 확인해 주세요' : '이 분야에는 아직 종목이 없어요'); return; }
      var groups = {};
      var order = [];
      stocks.forEach(function (s) {
        var key = s.sectorName || '기타';
        if (!groups[key]) { groups[key] = []; order.push(key); }
        groups[key].push(s);
      });
      list.replaceChildren.apply(list, order.map(function (key) {
        var g = el('div', 'sector-group');
        var h = el('h3', null, key);
        h.appendChild(el('small', null, groups[key].length + '종목'));
        g.appendChild(h);
        groups[key].forEach(function (s) { g.appendChild(stockRow(s)); });
        return g;
      }));
    }).catch(sectionErrorIf(current, list));
  }

  /* ---------- 종목 상세 (API 57, 58) ---------- */
  function openStock(stockId, tab) {
    go('detail');
    var info = tab === 'news' || tab === 'company';
    setTab(info ? 'info' : 'chart');
    setInfo(tab === 'news' ? 'news' : 'company');
    $('trd-d-name').textContent = '불러오는 중이에요';
    loading($('trd-chart'));
    $('trd-price-table').replaceChildren();
    return loadStock(stockId);
  }

  function loadStock(stockId) {
    var req = S.stockReq = (S.stockReq || 0) + 1;   // 늦게 온 이전 종목 응답이 화면을 덮지 않게 마지막 요청만 반영한다
    S.selectedStockId = stockId;                     // 응답 전에도 '지금 고른 종목'을 기억한다 (새로고침이 이전 종목을 다시 받지 않게)
    loadStockNews(stockId);
    return api('/stocks/' + encodeURIComponent(stockId)).then(function (d) {
      if (req !== S.stockReq) return;
      S.stock = d;
      $('trd-d-initial').textContent = (d.name || '?').charAt(0);
      $('trd-d-name').textContent = d.name;
      var crumb = $('trd-d-sector');
      crumb.replaceChildren(document.createTextNode('국내 주식 '), el('b', null, '·'), document.createTextNode(' ' + (d.sectorName || '')));
      var tags = $('trd-d-tags');
      tags.replaceChildren(el('span', null, d.sectorName || ''), el('span', null, '국내'), el('span', 'virtual', '가상 기업'));
      $('trd-d-price').textContent = fmt(d.price);
      setRate($('trd-d-change'), d.changeRate);
      renderChart(d.priceHistory || []);
      renderCompany(d);
      renderNewsImpact();
      resetOrder();
    }).catch(function (err) {
      if (req !== S.stockReq) return;
      if (fatal(err)) return;
      $('trd-d-name').textContent = '종목을 불러오지 못했어요';
      note($('trd-chart'), errText(err), true);
    });
  }

  function loadStockNews(stockId) {
    var list = $('trd-d-news');
    var req = S.newsReq = (S.newsReq || 0) + 1;
    loading(list);
    api('/news?stockId=' + encodeURIComponent(stockId)).then(function (d) {
      if (req !== S.newsReq) return;
      S.stockNews = d.items || [];
      renderNewsImpact();
    }).catch(function (err) {
      if (req !== S.newsReq) return;
      S.stockNews = null;
      sectionError(list)(err);
    });
  }

  /** 종목 뉴스 타임라인: 뉴스가 공개된 다음 턴의 가격 변화를 함께 보여 준다 (가격 흐름에서 계산) */
  function renderNewsImpact() {
    var list = $('trd-d-news');
    var items = S.stockNews;
    var now = $('trd-d-news-now');
    if (!items) return;
    var cur = S.season ? S.season.currentTurn : 0;
    now.replaceChildren(el('span', 'now-dot'), document.createTextNode('현재 '), el('b', null, cur + '턴'),
      document.createTextNode(' 진행 중 · 전체 ' + cur + '개 턴'));
    if (!items.length) { note(list, '아직 이 종목의 뉴스가 없어요'); return; }
    var history = (S.stock && S.stock.priceHistory) || [];
    var byTurn = {};
    history.forEach(function (p) { byTurn[p.turnNo] = p.price; });
    list.replaceChildren.apply(list, items.slice().sort(function (a, b) { return b.turnNo - a.turnNo; }).map(function (n) {
      var article = el('article', n.turnNo === cur ? 'current' : '');
      var marker = el('div', 'timeline-marker');
      marker.append(el('span', null, n.turnNo + '턴'), el('i'));
      var card = el('div', 'timeline-card');
      var top = el('div', 'timeline-top');
      top.appendChild(el('span', 'turn-badge', n.turnNo + '턴'));
      if (n.turnNo === cur) top.appendChild(el('span', 'now-badge', '현재 턴'));
      var wrap = el('div', 'impact-wrap');
      var from = byTurn[n.turnNo], to = byTurn[n.turnNo + 1];
      var impact = el('span', 'impact');
      if (from && to) {
        var v = Math.round((to - from) / from * 1000) / 10;
        impact.className = 'impact ' + (v > 0 ? 'up' : v < 0 ? 'down' : '');
        impact.textContent = '다음 턴 ' + rateText(v);
      } else {
        impact.textContent = '다음 턴 ?';
      }
      wrap.appendChild(impact);
      top.appendChild(wrap);
      card.append(top, el('h3', null, n.title));
      if (n.content) card.appendChild(el('p', null, n.content));
      (n.stocks || []).forEach(function (s) { card.appendChild(el('span', 'company-tag', s)); });
      (n.sectors || []).forEach(function (s) { card.appendChild(el('span', 'company-tag sector', '관련 분야 · ' + s)); });
      article.append(marker, card);
      return article;
    }));
  }

  function setTab(tab) {
    document.querySelectorAll('[data-trd-tab]').forEach(function (b) {
      b.classList.toggle('active', b.dataset.trdTab === tab);
      b.setAttribute('aria-selected', String(b.dataset.trdTab === tab));
    });
    document.querySelectorAll('[data-trd-pane]').forEach(function (p) { show(p, p.dataset.trdPane === tab); });
  }
  function setInfo(name) {
    document.querySelectorAll('[data-trd-info]').forEach(function (b) { b.classList.toggle('active', b.dataset.trdInfo === name); });
    document.querySelectorAll('[data-trd-info-pane]').forEach(function (p) { show(p, p.dataset.trdInfoPane === name); });
  }

  /** 턴별 가격 꺾은선 (SVG, 라이브러리 없음). 좌표·요소는 디자인(840×340)과 같다 */
  function renderChart(points) {
    var box = $('trd-chart');
    var table = $('trd-price-table');
    var sum = $('trd-d-season-rate');
    if (!points.length) { note(box, '아직 가격 흐름이 없어요'); table.replaceChildren(); sum.textContent = ''; return; }
    var W = 840, H = 340, L = 52, R = 790, TOP = 65, BOTTOM = 275;
    var prices = points.map(function (p) { return p.price; });
    var min = Math.min.apply(null, prices), max = Math.max.apply(null, prices);
    var x = function (i) { return points.length === 1 ? R : L + i * (R - L) / (points.length - 1); };
    var y = function (v) { return max === min ? (TOP + BOTTOM) / 2 : BOTTOM - (v - min) * (BOTTOM - TOP) / (max - min); };
    var NS = 'http://www.w3.org/2000/svg';
    var svg = document.createElementNS(NS, 'svg');
    svg.setAttribute('viewBox', '0 0 ' + W + ' ' + H);
    svg.setAttribute('role', 'img');
    svg.setAttribute('aria-label', '1턴부터 ' + points[points.length - 1].turnNo + '턴까지 ' + (S.stock ? S.stock.name + ' ' : '') + '가격 차트');
    var add = function (parent, tag, attrs, text) {
      var n = document.createElementNS(NS, tag);
      Object.keys(attrs).forEach(function (k) { n.setAttribute(k, attrs[k]); });
      if (text != null) n.textContent = text;
      parent.appendChild(n);
      return n;
    };
    var defs = add(svg, 'defs', {});
    var grad = add(defs, 'linearGradient', { id: 'area', x1: 0, x2: 0, y1: 0, y2: 1 });
    add(grad, 'stop', { offset: '0%', 'stop-color': '#9FE870', 'stop-opacity': '.5' });
    add(grad, 'stop', { offset: '100%', 'stop-color': '#9FE870', 'stop-opacity': '0' });
    var glow = add(defs, 'filter', { id: 'glow' });
    add(glow, 'feGaussianBlur', { stdDeviation: 4, result: 'blur' });
    var merge = add(glow, 'feMerge', {});
    add(merge, 'feMergeNode', { 'in': 'blur' });
    add(merge, 'feMergeNode', { 'in': 'SourceGraphic' });
    [55, 115, 175, 235, 295].forEach(function (gy) { add(svg, 'line', { x1: L, x2: R, y1: gy, y2: gy, 'class': 'gridline' }); });
    var path = points.map(function (p, i) { return (i ? 'L' : 'M') + x(i) + ' ' + y(p.price); }).join(' ');
    add(svg, 'path', { 'class': 'chart-area', d: path + ' L' + x(points.length - 1) + ' 295 L' + x(0) + ' 295Z' });
    add(svg, 'path', { 'class': 'chart-line', d: path });
    var last = points.length - 1;
    points.forEach(function (p, i) { if (i !== last) add(svg, 'circle', { cx: x(i), cy: y(p.price), r: 4, 'class': 'chart-dot' }); });
    add(svg, 'line', { x1: x(last), x2: x(last), y1: 60, y2: 295, 'class': 'current-line' });
    add(svg, 'circle', { cx: x(last), cy: y(points[last].price), r: 8, 'class': 'current-dot', filter: 'url(#glow)' });
    var label = add(svg, 'g', { 'class': 'price-label' });
    add(label, 'rect', { x: x(last) - 108, y: 22, width: 108, height: 30, rx: 7 });
    add(label, 'text', { x: x(last) - 54, y: 42 }, won(points[last].price));
    points.forEach(function (p, i) {
      add(svg, 'text', { x: x(i), y: 326, 'class': 'axis-label', 'text-anchor': i === 0 ? 'start' : i === last ? 'end' : 'middle' }, p.turnNo + '턴');
    });
    box.replaceChildren(svg);

    var first = prices[0], lastPrice = prices[last];
    if (first) setRate(sum, Math.round((lastPrice - first) / first * 1000) / 10); else sum.textContent = '';
    sum.tagName === 'STRONG' || (sum.style.fontWeight = '700');

    table.replaceChildren.apply(table, points.map(function (p, i) {
      var cell = el('div', i === last ? 'current' : '');
      var prev = i ? points[i - 1].price : null;
      var label = el('span', null, p.turnNo + '턴');
      if (i === last) label.appendChild(el('b', null, '현재'));
      cell.append(label, el('strong', null, won(p.price)),
        rate(prev ? Math.round((p.price - prev) / prev * 1000) / 10 : null, 'small'));
      return cell;
    }));
  }

  /** 종목정보 > 주요 정보 (디자인의 기업 소개·기본 정보·주요 사업. API 가 주는 값만 보여 준다) */
  function renderCompany(d) {
    var box = $('trd-company');
    var c = d.company;
    box.replaceChildren();
    var intro = el('div', 'company-intro');
    var title = el('div', 'company-title');
    title.append(el('h2', null, d.name), el('span', null, '국내 · ' + (d.sectorName || '') + ' · 가상 기업'));
    intro.appendChild(title);
    if (c && c.intro) intro.appendChild(el('p', null, c.intro));
    box.appendChild(intro);
    if (!c) { box.appendChild(el('p', 'trd-empty', '기업정보가 아직 없어요')); return; }

    var no = 0;
    var section = function (cls, name) {
      var s = el('section', 'info-section' + (cls ? ' ' + cls : ''));
      var t = el('div', 'info-title');
      no += 1;
      t.append(el('span', null, pad2(no)), el('h3', null, name));
      s.appendChild(t);
      box.appendChild(s);
      return s;
    };
    var facts = el('div', 'facts');
    [['분야', d.sectorName], ['시장', '국내'], ['매출', c.revenue], ['영업이익', c.operatingProfit]].forEach(function (f) {
      if (!f[1]) return;
      var row = el('div');
      row.append(el('span', null, f[0]), el('strong', null, f[1]));
      facts.appendChild(row);
    });
    section('', '기본 정보').appendChild(facts);

    var lines = (c.business || '').split(/\r?\n/).map(function (l) { return l.trim(); }).filter(Boolean);
    if (lines.length) {
      var list = el('div', 'business-list');
      lines.forEach(function (line, i) {
        var row = el('div');
        var p = el('p');
        p.appendChild(document.createTextNode(line));
        row.append(el('span', null, pad2(i + 1)), p);
        list.appendChild(row);
      });
      section('business-section', '주요 사업').appendChild(list);
    }
  }

  /* ---------- 매수·매도 (API 59, 60) ---------- */
  function loadReasons() {
    if (S.reasons) return Promise.resolve(S.reasons);
    return api('/trade-reasons').then(function (d) { S.reasons = d.reasons || []; return S.reasons; });
  }

  function maxQty() {
    var d = S.stock;
    if (!d || !d.price) return 0;
    if (S.side === 'SELL') return d.myHolding ? d.myHolding.quantity : 0;
    return S.participation ? Math.floor(S.participation.cash / d.price) : 0;
  }

  /** 주문할 수 없는 이유 (없으면 빈 문자열) */
  function blockReason() {
    if (!S.season) return '';
    if (S.season.status === 'HOLIDAY') return MSG.MARKET_HOLIDAY;
    if (S.season.status === 'SETTLING' || S.season.status === 'ENDED') return MSG.SEASON_CLOSED;
    if (!S.participation) return '시즌에 참가하면 거래할 수 있어요';
    if (!S.stock || S.stock.price == null) return '아직 이번 턴 가격이 없어요';
    return '';
  }

  function resetOrder() {
    S.reasonId = null;
    $('trd-qty').value = 1;
    delete $('trd-o-error').dataset.from;
    show($('trd-o-error'), false);
    document.querySelectorAll('[data-trd-side]').forEach(function (b) { b.classList.toggle('active', b.dataset.trdSide === S.side); });
    renderHoldingSummary();
    var box = $('trd-reasons');
    loading(box);
    loadReasons().then(function (reasons) {
      // 선택지에 매수·매도 구분(orderType)이 있으면 지금 탭에 맞는 것만 보여 준다
      var list = reasons.filter(function (r) { return !r.orderType || r.orderType === S.side; });
      if (!list.length) { note(box, '매매 이유 선택지가 없어요'); return; }
      box.replaceChildren.apply(box, list.map(function (r) {
        var chip = el('button', null, r.label);
        chip.type = 'button';
        chip.setAttribute('role', 'radio');
        chip.setAttribute('aria-checked', 'false');
        chip.dataset.trdReason = r.reasonId;
        return chip;
      }));
    }).catch(sectionError(box)).then(updateOrder);
  }

  function renderHoldingSummary() {
    var box = $('trd-d-holding');
    var h = S.stock && S.stock.myHolding;
    if (!h || !h.quantity) {
      box.className = 'holding-summary empty';
      box.replaceChildren(el('p', null, '아직 보유하지 않은 종목이에요'));
      return;
    }
    box.className = 'holding-summary';
    var profit = S.stock.price != null ? (S.stock.price - h.avgPrice) * h.quantity : null;
    var pr = profit != null && h.avgPrice ? Math.round((S.stock.price - h.avgPrice) / h.avgPrice * 1000) / 10 : null;
    var values = el('div', 'holding-summary-values');
    [['보유 수량', fmt(h.quantity) + '주'], ['평균 단가', won(h.avgPrice)]].forEach(function (c) {
      var d = el('div'); d.append(el('small', null, c[0]), el('strong', null, c[1])); values.appendChild(d);
    });
    var pl = el('div');
    var em = el('em', profit > 0 ? 'up' : profit < 0 ? 'down' : '',
      profit == null ? '-' : (profit > 0 ? '▲ +' : profit < 0 ? '▼ −' : '') + won(Math.abs(profit)) + ' ');
    if (pr != null) em.appendChild(el('span', null, '(' + (pr > 0 ? '+' : pr < 0 ? '−' : '') + Math.abs(pr).toFixed(1) + '%)'));
    pl.append(el('small', null, '평가손익'), em);
    values.appendChild(pl);
    box.replaceChildren(el('span', 'holding-badge', '보유 중'), values);
  }

  function updateOrder() {
    var d = S.stock;
    var qtyInput = $('trd-qty');
    var qty = Math.max(1, parseInt(qtyInput.value, 10) || 1);
    var max = maxQty();
    var price = d && d.price ? d.price : 0;
    var block = blockReason();
    var buy = S.side === 'BUY';
    var errBox = $('trd-o-error');
    $('trd-o-price').textContent = d ? won(d.price) : '-';
    $('trd-o-max').textContent = '최대 ' + fmt(max) + '주';
    $('trd-o-total-label').textContent = buy ? '총 주문 금액' : '예상 판매 금액';
    $('trd-o-total').replaceChildren(document.createTextNode(fmt(qty * price)), el('small', null, '캐시'));
    $('trd-o-avail-label').textContent = buy ? '주문 가능 금액' : '매도 가능 수량';
    $('trd-o-avail').textContent = buy ? (S.participation ? won(S.participation.cash) : '-') : fmt(max) + '주';
    var blockBox = $('trd-o-block');
    blockBox.textContent = block;
    blockBox.className = 'order-block';
    show(blockBox, !!block);
    // 결과를 모르는 주문과 같은 내용이면 보유 수량이 줄었어도(이미 체결됐을 수 있다) 같은 키로 다시 낼 수 있게 막지 않는다
    var over = !block && qty > max && !isPendingRetry(qty);
    // 서버가 준 안내(처리 여부 확인 등)가 떠 있으면 수량 오류가 덮어쓰지 않는다
    if (errBox.dataset.from !== 'server') {
      if (over) { errBox.textContent = buy ? MSG.NOT_ENOUGH_CASH : MSG.NOT_ENOUGH_QUANTITY; show(errBox, true); }
      else show(errBox, false);
    }
    document.querySelectorAll('[data-trd-reason]').forEach(function (c) {
      var on = String(c.dataset.trdReason) === String(S.reasonId);
      c.classList.toggle('selected', on);
      c.setAttribute('aria-checked', String(on));
    });
    var btn = $('trd-o-submit');
    btn.className = 'order-submit ' + (buy ? 'buy' : 'sell');
    btn.disabled = !!block || over || !S.reasonId || S.busy;
    btn.textContent = block ? '지금은 주문할 수 없어요'
      : S.reasonId ? (buy ? '매수' : '매도') + ' · ' + fmt(qty) + '주 ' + won(qty * price) : '매매 이유를 골라주세요';
  }

  function orderError(text, code) {
    var errBox = $('trd-o-error');
    errBox.textContent = text;
    errBox.dataset.from = 'server';
    errBox.dataset.code = code || '';
    show(errBox, true);
  }

  /** 수량·현금 부족 오류는 수량을 바꾸면 지운다. 주문 처리 여부 확인 안내 같은 다른 서버 안내는 남긴다 */
  function clearQuantityError() {
    var errBox = $('trd-o-error');
    if (errBox.dataset.code === 'NOT_ENOUGH_QUANTITY' || errBox.dataset.code === 'NOT_ENOUGH_CASH') delete errBox.dataset.from;
  }

  /** 주문 요청 본문. 결과를 모르는 주문을 다시 낼 때 같은 내용인지 비교하므로 키 순서를 바꾸지 않는다 */
  function orderBody(d, side, qty) {
    return { stockId: d.stockId, orderType: side, quantity: qty, turnNo: d.turnNo, reasonId: Number(S.reasonId) };
  }

  /** 지금 폼이 결과를 모르는 주문(pendingOrder)과 같은 내용인가 */
  function isPendingRetry(qty) {
    return !!(S.pendingOrder && S.stock && S.pendingOrder.sig === JSON.stringify(orderBody(S.stock, S.side, qty)));
  }

  /**
   * 응답을 못 받은 주문 뒤에 이 종목의 보유 수량만 다시 받는다.
   * 가격·턴·수량·이유·키는 그대로 둔다 (같은 주문을 같은 본문·키로 다시 내야 서버가 처음 결과를 돌려준다).
   * 새 주문에는 갱신된 수량 제한이 걸린다 (updateOrder)
   */
  function refreshStockHolding(stockId) {
    var req = S.stockReq;
    return api('/stocks/' + encodeURIComponent(stockId)).then(function (d) {
      if (req !== S.stockReq || !S.stock || S.stock.stockId !== d.stockId) return;   // 그사이 다른 종목을 열었으면 버린다
      S.stock.myHolding = d.myHolding;
      renderHoldingSummary();
      updateOrder();
    }).catch(function (err) { fatal(err); });   // 못 받아도 홈·내역은 이미 갱신됐고, 주문 화면은 그대로 둔다
  }

  function submitOrder(e) {
    e.preventDefault();
    var d = S.stock;
    if (!d || S.busy || blockReason()) return;
    if (!S.reasonId) { orderError(MSG.REASON_REQUIRED); return; }
    var qty = Math.max(1, parseInt($('trd-qty').value, 10) || 1);
    var side = S.side;
    var body = orderBody(d, side, qty);
    // 새 주문은 새 키, 결과를 모르는 주문을 같은 내용으로 다시 내면 같은 키 (서버가 처음 결과를 돌려준다, DP-37)
    var sig = JSON.stringify(body);
    var key = S.pendingOrder && S.pendingOrder.sig === sig ? S.pendingOrder.key : uuid();
    S.pendingOrder = { sig: sig, key: key };
    S.busy = true;
    updateOrder();
    api('/orders', {
      method: 'POST',
      headers: { 'Idempotency-Key': key },
      body: body
    }).then(function () {
      S.pendingOrder = null;
      delete $('trd-o-error').dataset.from;
      S.reasonId = null;
      MB.toast(d.name + ' ' + fmt(qty) + '주를 ' + (side === 'BUY' ? '샀어요' : '팔았어요'));
      // 새로고침(홈·종목)이 끝날 때까지 S.busy 로 제출 버튼을 잠근다 (같은 주문의 중복 체결 방지)
      return refreshAll(true);
    }, function (err) {
      // 결과를 모르는 오류만 키를 남겨 둔다 (같은 주문을 다시 내면 서버가 처음 결과를 돌려준다, DP-37)
      //  - NETWORK_ERROR : 응답을 받지 못했다
      //  - ERROR + 상태 없음 : 응답 본문을 읽다가 끊겼다
      //  - DUPLICATE_REQUEST : 처음 주문이 아직 처리 중이다
      // 본문 없는 5xx(code 'ERROR' + status 5xx, 프록시 같은 앞단 오류)는 같은 키로 다시 보내도 같은 오류만 돌아올 수 있어 키를 버리고 '처리 여부 확인' 으로 다룬다
      var noReply = err.code === 'NETWORK_ERROR' || (err.code === 'ERROR' && !err.status);
      var keepKey = noReply || err.code === 'DUPLICATE_REQUEST';
      if (!keepKey) S.pendingOrder = null;
      if (fatal(err)) return;
      // HTTP 4xx 는 서버가 이유를 알려 준 거절이다(예: 409 MARKET_CLOSED 장 운영 시간 밖). 서버 문구를 그대로 보여 주고 수량·이유는 지우지 않는다.
      // 5xx 처럼 서버가 주문을 저장했는지 알 수 없는 오류(MSG 에 없는 코드)는 내역을 다시 불러오고 확인을 안내한다
      var rejected = err.status >= 400 && err.status < 500;
      var unknown = !keepKey && !MSG[err.code] && !rejected;
      // 응답을 못 받으면 서버가 주문을 저장했을 수 있다. 수량·이유·키는 그대로 두므로
      // 같은 주문을 다시 누르면 서버가 처음 결과를 돌려줘 중복 체결되지 않는다
      orderError(noReply ? '응답을 받지 못해 주문이 처리됐는지 알 수 없어요. 내 거래 내역을 확인해 주세요. 같은 주문을 다시 누르면 중복 없이 처리돼요'
        : unknown ? '주문이 처리됐는지 확인하지 못했어요. 내 거래 내역을 확인한 뒤 다시 시도해 주세요' : errText(err), err.code);
      // 거래 내역·보유는 새로 불러오되 종목 화면은 다시 그리지 않는다 (다시 그리면 고른 수량·이유가 초기화돼 같은 주문을 다시 내기 어렵다).
      // 이 종목의 보유 수량만 따로 갱신한다: 매도가 체결됐다면 종목 화면의 매도 가능 수량도 줄어 있어야 새 주문이 맞게 막힌다
      if (noReply) return Promise.all([refreshAll(false), refreshStockHolding(d.stockId)]);
      // 턴이 넘어가 가격이 바뀌었으면 현재 가격과 시즌 정보를 다시 불러온다 (INV-12)
      if (unknown || err.code === 'STALE_TURN_PRICE' || err.code === 'MARKET_HOLIDAY' || err.code === 'SEASON_CLOSED') {
        // 처리 여부를 모르는 오류(5xx)는 이미 체결됐을 수 있다. 새로고침이 실패해도 같은 주문이 새 키로 바로 다시 나가지 않게
        // 매매 이유를 먼저 지워 내역을 확인한 뒤 이유를 다시 골라야 제출할 수 있게 한다
        if (unknown) S.reasonId = null;
        return refreshAll(true, true);
      }
    }).then(function () { S.busy = false; updateOrder(); });
  }

  /* ---------- 랭킹 (API 62) ---------- */
  /** 디자인은 "지난 시즌 랭킹"을 보여 준다. API 가 지난 시즌 번호(previousSeasonId)를 주면 그 시즌을, 아니면 이 시즌을 부른다 */
  function loadRanking() {
    var list = $('trd-rank-list');
    var me = $('trd-my-rank');
    var noteBox = $('trd-rank-note');
    show(me, false);
    loading(list);
    var previous = S.season.previousSeasonId != null;
    var seasonId = previous ? S.season.previousSeasonId : S.season.seasonId;
    $('trd-rank-title').textContent = previous ? '지난 시즌 랭킹' : '시즌 랭킹';
    noteBox.textContent = previous && S.season.status !== 'ENDED' ? '이번 시즌 랭킹은 시즌 종료 후 공개돼요' : '';
    api('/seasons/' + seasonId + '/rankings?type=' + S.rankType + '&page=0&size=10').then(function (d) {
      if (d.seasonName) {
        var num = /(\d+)/.exec(d.seasonName);
        $('trd-rank-season').textContent = num ? 'SEASON ' + num[1] : d.seasonName;
      }
      var asset = S.rankType === 'ASSET';
      var items = d.items || [];
      if (!items.length) { note(list, '아직 랭킹이 없어요'); return; }
      list.replaceChildren.apply(list, items.map(function (r) {
        var top = r.rank <= 3;
        var row = el('div', 'rank-row' + (top ? '' : ' rank-row-sm'));
        var who = el('span', 'rank-name');
        who.setAttribute('style', RANK_NAME_STYLE);
        var line = el('span');
        line.setAttribute('style', RANK_WHO_STYLE);
        var nick = el('span', null, r.nickname);
        nick.setAttribute('style', RANK_NICK_STYLE);
        line.appendChild(nick);
        if (r.title) {
          var title = el('small', null, r.title);
          title.style.whiteSpace = 'nowrap';
          line.appendChild(title);
        }
        who.appendChild(line);
        if (top) {
          var chip = el('span', 'gold-chip', '뱃지·칭호');
          chip.style.whiteSpace = 'nowrap';
          who.appendChild(chip);
        }
        var value = asset ? el('strong', null, won(r.netAsset)) : rate(r.returnRate, 'strong');
        row.append(el('span', 'rank-number r' + r.rank, ({ 1: '🥇', 2: '🥈', 3: '🥉' })[r.rank] || String(r.rank)), who, value);
        return row;
      }));
      if (d.me) {
        var label = el('span');
        label.append(el('strong', null, '나'), el('b', null, d.me.rank != null ? d.me.rank + '위' : '순위 없음'));
        me.replaceChildren(label, asset ? el('strong', null, won(d.me.netAsset)) : rate(d.me.returnRate, 'strong'));
        show(me, true);
      }
    }).catch(function (err) {
      if (err.code === 'RANKING_NOT_PUBLISHED') { note(list, MSG.RANKING_NOT_PUBLISHED); return; }
      sectionError(list)(err);
    });
  }

  /* ---------- 다시 불러오기·이벤트 ---------- */
  /** 홈 정보를 다시 받고 영역을 새로 그린다. keepDetail 이면 보고 있는 종목도 다시 받고, 그것까지 끝나야 끝난다 */
  function refreshAll(keepDetail, skipOrderReset) {
    return loadHome().then(function () {
      loadTrades();
      loadTurnNews();
      loadMarket();
      if (keepDetail && S.selectedStockId && !$('trd-detail').hidden) {
        var errBox = $('trd-o-error');
        var keepText = skipOrderReset ? errBox.textContent : null;
        var keepCode = errBox.dataset.code;
        return loadStock(S.selectedStockId).then(function () { if (keepText) orderError(keepText, keepCode); return true; });
      }
      return true;
    }).catch(function (err) {
      if (fatal(err)) return true;   // 잠금·시즌 없음은 화면이 이미 바뀌었으므로 다시 시도하지 않는다
      MB.toast(errText(err), 'error');
      return false;                  // 호출한 쪽(카운트다운)이 실패를 알고 다시 확인한다
    });
  }

  var searchTimer = null;
  function onClick(e) {
    var t = e.target.closest('button');
    if (!t || !$('trd-app').contains(t) || S.locked) return;
    var ds = t.dataset;
    if (ds.trdGo === 'home') { go('home'); return; }
    if (ds.trdGo === 'all') { showAll(''); return; }
    if (ds.trdStock) { openStock(ds.trdStock, ds.trdOpenTab); return; }
    if (ds.trdSector !== undefined) { showAll(ds.trdSector); return; }
    if (ds.trdMarket) {
      S.marketTab = ds.trdMarket;
      document.querySelectorAll('[data-trd-market]').forEach(function (b) { b.classList.toggle('selected', b === t); });
      $('trd-search').value = '';
      loadMarket();
      return;
    }
    if (ds.trdRank) {
      S.rankType = ds.trdRank;
      document.querySelectorAll('[data-trd-rank]').forEach(function (b) { b.classList.toggle('selected', b === t); });
      loadRanking();
      return;
    }
    if (ds.trdTab) { setTab(ds.trdTab); return; }
    if (ds.trdInfo) { setInfo(ds.trdInfo); return; }
    if (ds.trdSide) { S.side = ds.trdSide; delete $('trd-o-error').dataset.from; resetOrder(); return; }
    if (ds.trdStep) {
      var q = $('trd-qty');
      q.value = Math.max(1, (parseInt(q.value, 10) || 1) + Number(ds.trdStep));
      clearQuantityError();
      updateOrder();
      return;
    }
    if (ds.trdPct) { $('trd-qty').value = Math.max(1, Math.floor(maxQty() * Number(ds.trdPct))); clearQuantityError(); updateOrder(); return; }
    if (ds.trdReason) { S.reasonId = ds.trdReason; delete $('trd-o-error').dataset.from; updateOrder(); return; }
    if (t.id === 'trd-join-btn') join();
  }

  function init() {
    document.addEventListener('click', onClick);
    $('trd-order').addEventListener('submit', submitOrder);
    $('trd-qty').addEventListener('input', function () { delete $('trd-o-error').dataset.from; updateOrder(); });
    $('trd-search').addEventListener('input', function () {
      clearTimeout(searchTimer);
      searchTimer = setTimeout(loadMarket, 250);
    });
    // 전체 종목·종목 상세 위쪽 검색창: 엔터를 누르면 검색 결과를 전체 종목 화면으로 보여 준다
    document.querySelectorAll('.trd-compact-search').forEach(function (input) {
      input.addEventListener('keydown', function (ev) {
        if (ev.key !== 'Enter') return;
        ev.preventDefault();
        var q = input.value.trim();
        if (q) showAll('', q);
      });
    });
    ['trd-holdings', 'trd-trade-list', 'trd-market-list', 'trd-rank-list'].forEach(function (id) { loading($(id)); });

    loadHome().then(function () {
      // 통합 검색(/user/search)의 종목 링크: ?stockId=숫자 이면 그 종목 상세를 바로 연다
      var sid = new URLSearchParams(window.location.search).get('stockId');
      if (sid && /^\d+$/.test(sid) && !S.locked) openStock(sid);
      loadTrades();
      loadMarket();
      loadRanking();
      // 뉴스 → 종목 연결에 전체 종목 이름이 필요해서 먼저 받아 둔다
      fetchStocks('').then(function (r) {
        var stocks = S.stocks = r.stocks;
        applyTurn(r.turnNo);
        $('trd-all-link').replaceChildren(document.createTextNode('전체 ' + stocks.length + '개 종목 보기 '), el('span', null, '›'));
      }).catch(function () {}).then(loadTurnNews);
    }).catch(function (err) {
      if (!fatal(err)) fullState('모의투자 정보를 불러오지 못했어요', errText(err));
    });
  }

  init();
})();
