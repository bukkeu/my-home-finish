/* comment.
*   담당 : PM 이재성 (#57, 모의투자 화면)
*   templates/trading/trading.html 의 데이터를 API 53~62 로 채운다. 요청은 공통 MB.api(common.js)로 보낸다 (CSRF·응답 형식·401 처리).
*   - 53 GET  /api/v1/trading/home                       : 시즌·턴 달력, 현금, 순자산, 보유 종목 (participation 이 null 이면 참가 버튼)
*   - 54 POST /api/v1/trading/seasons/{id}/participations : 시즌 참가
*   - 55 GET  /sectors, 56 GET /stocks(sort·sectorId·keyword), 57 GET /stocks/{id}, 58 GET /news(turnNo·stockId)
*   - 59 GET  /trade-reasons, 60 POST /orders(Idempotency-Key), 61 GET /orders, 62 GET /seasons/{id}/rankings
*   403 LEVEL_LOCKED  -> 본문을 흐리게 하고 잠금 안내 (NFR-09), NO_RUNNING_SEASON -> "다음 시즌을 준비 중이에요"
*   서버·사용자 문구는 모두 textContent 로 넣는다 (HTML 로 해석하지 않는다).
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
  var WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토'];
  var TONES = ['mint', 'orange', 'blue', 'purple', 'pink', 'yellow'];

  var S = {
    season: null, participation: null, stocks: [], sectors: null, reasons: null,
    stock: null, side: 'BUY', reasonId: null, rankType: 'ASSET', marketTab: 'popular',
    busy: false, timer: null, locked: false
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
  function rate(r) {
    if (r == null) return el('span', 'trd-flat', '-');
    var v = Number(r);
    var cls = v > 0 ? 'trd-up' : v < 0 ? 'trd-down' : 'trd-flat';
    return el('span', cls, (v > 0 ? '▲ +' : v < 0 ? '▼ ' : '') + v.toFixed(2) + '%');
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

  /** 화면 전체에 걸리는 오류 (잠금·시즌 없음). 처리했으면 true */
  function fatal(err) {
    if (!err) return false;
    if (err.code === 'LEVEL_LOCKED') { lock(err.message); return true; }
    if (err.code === 'NO_RUNNING_SEASON') {
      fullState(MSG.NO_RUNNING_SEASON, '진행 중인 시즌이 없어요. 시즌이 시작되면 이곳에서 참가할 수 있어요.');
      return true;
    }
    return false;
  }
  /** 각 영역의 오류: 잠금·시즌 없음이면 화면 전체로, 아니면 그 영역에만 문구를 둔다 */
  function sectionError(node) {
    return function (err) { if (!fatal(err)) note(node, errText(err), true); };
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

  function go(view) {
    show($('trd-home'), view === 'home');
    show($('trd-all'), view === 'all');
    show($('trd-detail'), view === 'detail');
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
      var last = i === all.length - 1 && day.turnNo == null;
      var cell = el('div', 'trd-turn is-' + String(day.state || '').toLowerCase() + (last ? ' is-settle' : ''));
      var wd = WEEKDAYS[new Date(day.date + 'T00:00:00').getDay()];
      cell.appendChild(el('span', 'trd-turn__wd', (day.state === 'DONE' ? '✓ ' : '') + wd));
      cell.appendChild(el('small', null, day.turnNo != null ? day.turnNo + '턴' : last ? '정산·랭킹' : '휴장'));
      cell.title = day.date;
      cal.appendChild(cell);
    });
    startCountdown(season.nextTurnAt, season.status);
  }

  /** 다음 턴까지 남은 시간. 0 이 되면 홈과 보고 있는 종목을 다시 불러온다 (INV-05) */
  function startCountdown(nextTurnAt, status) {
    clearInterval(S.timer);
    var out = $('trd-next-turn');
    if (status === 'SETTLING') { out.textContent = '정산을 기다리는 중이에요'; return; }
    if (status === 'ENDED') { out.textContent = '시즌이 끝났어요'; return; }
    if (!nextTurnAt) { out.textContent = '마지막 턴이에요'; return; }
    var target = new Date(nextTurnAt).getTime();
    var tick = function () {
      var left = Math.max(0, Math.floor((target - Date.now()) / 1000));
      var hh = String(Math.floor(left / 3600)).padStart(2, '0');
      var mm = String(Math.floor(left % 3600 / 60)).padStart(2, '0');
      var ss = String(left % 60).padStart(2, '0');
      out.textContent = '다음 턴까지 ' + hh + ':' + mm + ':' + ss;
      if (left === 0) { clearInterval(S.timer); setTimeout(refreshAll, 1500); }
    };
    tick();
    S.timer = setInterval(tick, 1000);
  }

  function renderBalance(season, p) {
    show($('trd-joined'), !!p);
    show($('trd-join'), !p);
    if (p) {
      $('trd-cash').textContent = fmt(p.cash);
      $('trd-invest').textContent = won(p.investAmount);
    } else if (season.status === 'SETTLING' || season.status === 'ENDED') {
      // 정산 중·종료 시즌은 참가할 수 없다 (서버도 JOIN_CLOSED)
      $('trd-join-title').textContent = MSG.JOIN_CLOSED;
      show($('trd-join-btn'), false);
    } else {
      $('trd-join-title').textContent = season.name + '에 참가할까요?';
      $('trd-join-btn').textContent = season.name + ' 참가하기 →';
      show($('trd-join-btn'), true);
    }
  }

  function renderPortfolio(p, holdings) {
    $('trd-net').textContent = p ? fmt(p.netAsset) : '-';
    $('trd-rate').replaceChildren(p ? rate(p.returnRate) : '');
    $('trd-holding-count').textContent = '보유 종목 ' + holdings.length + '개';
    var list = $('trd-holdings');
    if (!p) { note(list, '시즌에 참가하면 내 주식이 여기에 보여요'); return; }
    if (!holdings.length) { note(list, '아직 보유한 종목이 없어요. 오늘의 가상 시세에서 종목을 골라 보세요'); return; }
    list.replaceChildren.apply(list, holdings.map(function (h) {
      var row = el('button', 'trd-row3');
      row.type = 'button';
      row.dataset.trdStock = h.stockId;
      var name = el('span', 'trd-row3__name');
      name.append(avatar(h.stockName, h.stockId), el('span', null, h.stockName), el('small', 'trd-meta', h.quantity + '주'));
      row.append(name, el('strong', null, won(h.evaluation)), rate(h.returnRate));
      return row;
    }));
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
    var list = $('trd-trade-list');
    $('trd-trade-count').textContent = '';
    if (!S.participation) { note(list, '시즌에 참가하면 거래 내역이 여기에 보여요'); return; }
    loading(list);
    api('/orders?seasonId=' + S.season.seasonId + '&page=0&size=20').then(function (d) {
      var items = d.items || [];
      $('trd-trade-count').textContent = '총 ' + fmt(d.totalCount != null ? d.totalCount : items.length) + '건';
      if (!items.length) { note(list, '아직 거래하지 않았어요'); return; }
      list.replaceChildren.apply(list, items.map(function (t) {
        var row = el('div', 'trd-trade');
        var buy = t.orderType === 'BUY';
        row.append(el('span', 'trd-badge', t.turnNo + '턴'), el('strong', null, t.stockName),
          el('span', buy ? 'trd-up' : 'trd-down', buy ? '매수' : '매도'), el('span', null, fmt(t.quantity) + '주'),
          el('span', null, won(t.price)), el('span', 'trd-reason', t.reason || '-'));
        return row;
      }));
    }).catch(sectionError(list));
  }

  /* ---------- 뉴스 (API 58) ---------- */
  function newsItem(n, withContent) {
    // 홈의 뉴스는 관련 종목의 뉴스 탭으로 이어 준다 (종목 상세 안의 뉴스는 이어 주지 않는다)
    var stock = withContent ? null : S.stocks.find(function (s) { return (n.stocks || []).indexOf(s.name) >= 0; });
    var item = el(stock ? 'button' : 'div', 'trd-news-item');
    if (stock) { item.type = 'button'; item.dataset.trdStock = stock.stockId; item.dataset.trdOpenTab = 'news'; }
    var tags = el('div', 'trd-tags');
    (n.sectors || []).forEach(function (s) { tags.appendChild(el('span', 'trd-tag is-sector', s)); });
    (n.stocks || []).forEach(function (s) { tags.appendChild(el('span', 'trd-tag', s)); });
    item.append(el('span', 'trd-badge', n.turnNo + '턴'), el('h3', null, n.title));
    if (withContent && n.content) item.appendChild(el('p', null, n.content));
    item.appendChild(tags);
    return item;
  }

  function loadTurnNews() {
    var list = $('trd-news-list');
    $('trd-news-turn').textContent = S.season.currentTurn + '턴';
    loading(list);
    api('/news?turnNo=' + S.season.currentTurn).then(function (d) {
      var items = d.items || [];
      if (!items.length) { note(list, '이번 턴에 공개된 뉴스가 없어요'); return; }
      list.replaceChildren.apply(list, items.slice(0, 5).map(function (n) { return newsItem(n, false); }));
    }).catch(sectionError(list));
  }

  /* ---------- 시세·분야 (API 55, 56) ---------- */
  function avatar(name, id) {
    return el('span', 'trd-avatar is-' + TONES[Number(id || 0) % TONES.length], (name || '?').charAt(0));
  }
  function stockRow(s) {
    var row = el('button', 'trd-stock');
    row.type = 'button';
    row.dataset.trdStock = s.stockId;
    var name = el('span', 'trd-stock__name');
    name.append(el('strong', null, s.name), el('small', null, s.sectorName || ''));
    var price = el('span', 'trd-stock__price');
    price.append(el('strong', null, s.price == null ? '가격 준비 중' : won(s.price)), rate(s.changeRate));
    row.append(avatar(s.name, s.stockId), name, price);
    return row;
  }
  function fetchStocks(query) {
    return api('/stocks' + (query ? '?' + query : '')).then(function (d) {
      if (d.turnNo != null) $('trd-market-turn').textContent = d.turnNo + '턴 기준';
      return d.stocks || [];
    });
  }
  function fetchSectors() {
    if (S.sectors) return Promise.resolve(S.sectors);
    return api('/sectors').then(function (d) { S.sectors = d.sectors || []; return S.sectors; });
  }

  function loadMarket() {
    var list = $('trd-market-list');
    var q = $('trd-search').value.trim();
    loading(list);
    if (q) {
      fetchStocks('keyword=' + encodeURIComponent(q)).then(function (stocks) {
        if (!stocks.length) { note(list, '검색 결과가 없어요. 종목명을 다시 확인해 주세요'); return; }
        list.replaceChildren.apply(list, stocks.slice(0, 8).map(stockRow));
      }).catch(sectionError(list));
      return;
    }
    if (S.marketTab === 'sector') {
      fetchSectors().then(function (sectors) {
        if (!sectors.length) { note(list, '등록된 분야가 없어요'); return; }
        list.replaceChildren.apply(list, sectors.map(function (sec) {
          var row = el('button', 'trd-stock');
          row.type = 'button';
          row.dataset.trdSector = sec.sectorId;
          var name = el('span', 'trd-stock__name');
          name.append(el('strong', null, sec.name),
            el('small', null, (sec.stocks || []).map(function (s) { return s.name; }).join(' · ')));
          var cnt = el('span', 'trd-stock__price');
          cnt.append(el('strong', null, (sec.stocks || []).length + '종목'), el('small', 'trd-meta', '›'));
          row.append(avatar(sec.name, sec.sectorId), name, cnt);
          return row;
        }));
      }).catch(sectionError(list));
      return;
    }
    fetchStocks('sort=popular').then(function (stocks) {
      if (!stocks.length) { note(list, '아직 등록된 종목이 없어요'); return; }
      list.replaceChildren.apply(list, stocks.slice(0, 8).map(stockRow));
    }).catch(sectionError(list));
  }

  /* ---------- 전체 종목 (INV-06) ---------- */
  function showAll(sectorId) {
    go('all');
    var list = $('trd-all-list');
    var chips = $('trd-sector-chips');
    loading(list);
    fetchSectors().then(function (sectors) {
      var all = el('button', sectorId ? '' : 'is-on', '전체');
      all.type = 'button'; all.dataset.trdSector = '';
      chips.replaceChildren(all);
      sectors.forEach(function (sec) {
        var c = el('button', String(sec.sectorId) === String(sectorId) ? 'is-on' : '', sec.name);
        c.type = 'button'; c.dataset.trdSector = sec.sectorId;
        chips.appendChild(c);
      });
    }).catch(function (err) { fatal(err); chips.replaceChildren(); });
    fetchStocks(sectorId ? 'sectorId=' + encodeURIComponent(sectorId) : '').then(function (stocks) {
      if (!sectorId) S.stocks = stocks;
      $('trd-all-title').textContent = sectorId ? (stocks[0] ? stocks[0].sectorName : '분야') + ' 종목' : '전체 ' + stocks.length + '개 종목';
      $('trd-all-meta').textContent = $('trd-market-turn').textContent;
      if (!stocks.length) { note(list, '이 분야에는 아직 종목이 없어요'); return; }
      var groups = {};
      var order = [];
      stocks.forEach(function (s) {
        var key = s.sectorName || '기타';
        if (!groups[key]) { groups[key] = []; order.push(key); }
        groups[key].push(s);
      });
      list.replaceChildren.apply(list, order.map(function (key) {
        var g = el('div', 'trd-group');
        var h = el('h3', null, key);
        h.appendChild(el('small', null, groups[key].length + '종목'));
        g.appendChild(h);
        groups[key].forEach(function (s) { g.appendChild(stockRow(s)); });
        return g;
      }));
    }).catch(sectionError(list));
  }

  /* ---------- 종목 상세 (API 57, 58) ---------- */
  function openStock(stockId, tab) {
    go('detail');
    setTab(tab || 'chart');
    $('trd-d-name').textContent = '불러오는 중이에요';
    loading($('trd-chart'));
    $('trd-price-table').replaceChildren();
    return loadStock(stockId);
  }

  function loadStock(stockId) {
    loadStockNews(stockId);
    return api('/stocks/' + encodeURIComponent(stockId)).then(function (d) {
      S.stock = d;
      $('trd-d-initial').textContent = (d.name || '?').charAt(0);
      $('trd-d-name').textContent = d.name;
      $('trd-d-sector').textContent = '국내 주식 · ' + (d.sectorName || '');
      $('trd-d-price').textContent = fmt(d.price);
      $('trd-d-change').replaceChildren(rate(d.changeRate));
      renderChart(d.priceHistory || []);
      renderCompany(d);
      resetOrder();
    }).catch(function (err) {
      if (fatal(err)) return;
      $('trd-d-name').textContent = '종목을 불러오지 못했어요';
      note($('trd-chart'), errText(err), true);
    });
  }

  function loadStockNews(stockId) {
    var list = $('trd-d-news');
    loading(list);
    api('/news?stockId=' + encodeURIComponent(stockId)).then(function (d) {
      var items = d.items || [];
      if (!items.length) { note(list, '아직 이 종목의 뉴스가 없어요'); return; }
      list.replaceChildren.apply(list, items.map(function (n) { return newsItem(n, true); }));
    }).catch(sectionError(list));
  }

  function setTab(tab) {
    document.querySelectorAll('[data-trd-tab]').forEach(function (b) {
      b.classList.toggle('is-on', b.dataset.trdTab === tab);
      b.setAttribute('aria-selected', String(b.dataset.trdTab === tab));
    });
    document.querySelectorAll('[data-trd-pane]').forEach(function (p) { show(p, p.dataset.trdPane === tab); });
  }

  /** 턴별 가격 꺾은선 (SVG, 라이브러리 없음) */
  function renderChart(points) {
    var box = $('trd-chart');
    var table = $('trd-price-table');
    if (!points.length) { note(box, '아직 가격 흐름이 없어요'); table.replaceChildren(); return; }
    var W = 640, H = 240, L = 16, R = 16, T = 28, B = 32;
    var prices = points.map(function (p) { return p.price; });
    var min = Math.min.apply(null, prices), max = Math.max.apply(null, prices);
    var span = max - min || Math.max(1, max * 0.1);
    var x = function (i) { return points.length === 1 ? W / 2 : L + i * (W - L - R) / (points.length - 1); };
    var y = function (v) { return T + (max - v) * (H - T - B) / span; };
    var up = prices[prices.length - 1] >= prices[0];
    var NS = 'http://www.w3.org/2000/svg';
    var svg = document.createElementNS(NS, 'svg');
    svg.setAttribute('viewBox', '0 0 ' + W + ' ' + H);
    svg.setAttribute('role', 'img');
    svg.setAttribute('aria-label', (S.stock ? S.stock.name + ' ' : '') + '1턴부터 ' + points[points.length - 1].turnNo + '턴까지 가격 흐름');
    var add = function (tag, attrs, text) {
      var n = document.createElementNS(NS, tag);
      Object.keys(attrs).forEach(function (k) { n.setAttribute(k, attrs[k]); });
      if (text != null) n.textContent = text;
      svg.appendChild(n);
      return n;
    };
    [0, 0.5, 1].forEach(function (f) { add('line', { x1: L, x2: W - R, y1: T + f * (H - T - B), y2: T + f * (H - T - B), 'class': 'trd-grid' }); });
    var d = points.map(function (p, i) { return (i ? 'L' : 'M') + x(i).toFixed(1) + ' ' + y(p.price).toFixed(1); }).join(' ');
    add('path', { d: d + ' L' + x(points.length - 1).toFixed(1) + ' ' + (H - B) + ' L' + x(0).toFixed(1) + ' ' + (H - B) + ' Z', 'class': 'trd-area' });
    add('path', { d: d, 'class': 'trd-line ' + (up ? 'is-up' : 'is-down') });
    points.forEach(function (p, i) {
      var lastPt = i === points.length - 1;
      add('circle', { cx: x(i), cy: y(p.price), r: lastPt ? 6 : 3.5, 'class': 'trd-pt' + (lastPt ? ' is-now' : '') });
      add('text', { x: x(i), y: H - 8, 'class': 'trd-axis', 'text-anchor': 'middle' }, p.turnNo + '턴');
    });
    var lp = points[points.length - 1];
    add('text', { x: Math.min(x(points.length - 1), W - 60), y: Math.max(14, y(lp.price) - 12), 'class': 'trd-axis is-strong', 'text-anchor': 'middle' }, won(lp.price));
    box.replaceChildren(svg);

    table.replaceChildren.apply(table, points.map(function (p, i) {
      var cell = el('div', i === points.length - 1 ? 'is-now' : '');
      var prev = i ? points[i - 1].price : null;
      cell.append(el('span', null, p.turnNo + '턴' + (i === points.length - 1 ? ' · 현재' : '')), el('strong', null, won(p.price)),
        rate(prev ? Math.round((p.price - prev) / prev * 10000) / 100 : null));
      return cell;
    }));
  }

  function renderCompany(d) {
    var box = $('trd-company');
    var c = d.company;
    box.replaceChildren();
    var head = el('div', 'trd-company__head');
    head.append(el('h2', null, d.name), el('span', 'trd-meta', '국내 · ' + (d.sectorName || '') + ' · 가상 기업'));
    box.appendChild(head);
    if (!c) { box.appendChild(el('p', 'trd-empty', '기업정보가 아직 없어요')); return; }
    if (c.intro) box.appendChild(el('p', 'trd-company__intro', c.intro));
    var facts = el('div', 'trd-facts');
    [['분야', d.sectorName], ['매출', c.revenue], ['영업이익', c.operatingProfit]].forEach(function (f) {
      if (!f[1]) return;
      var row = el('div');
      row.append(el('span', null, f[0]), el('strong', null, f[1]));
      facts.appendChild(row);
    });
    box.appendChild(facts);
    if (c.business) {
      box.appendChild(el('h3', null, '주요 사업'));
      box.appendChild(el('p', 'trd-company__intro', c.business));
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
    document.querySelectorAll('[data-trd-side]').forEach(function (b) { b.classList.toggle('is-on', b.dataset.trdSide === S.side); });
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
    if (!h || !h.quantity) { note(box, '아직 보유하지 않은 종목이에요'); box.classList.add('is-empty'); return; }
    box.classList.remove('is-empty');
    var profit = S.stock.price != null ? (S.stock.price - h.avgPrice) * h.quantity : null;
    var pr = profit != null && h.avgPrice ? Math.round((S.stock.price - h.avgPrice) / h.avgPrice * 10000) / 100 : null;
    var cells = [['보유 수량', fmt(h.quantity) + '주'], ['평균 단가', won(h.avgPrice)]];
    box.replaceChildren(el('span', 'trd-badge', '보유 중'));
    cells.forEach(function (c) {
      var d = el('div'); d.append(el('small', null, c[0]), el('strong', null, c[1])); box.appendChild(d);
    });
    var pl = el('div');
    var plText = el('strong', profit > 0 ? 'trd-up' : profit < 0 ? 'trd-down' : '', profit == null ? '-' : (profit > 0 ? '+' : '') + won(profit));
    pl.append(el('small', null, '평가손익'), plText, rate(pr));
    box.appendChild(pl);
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
    $('trd-o-total').textContent = won(qty * price);
    $('trd-o-avail-label').textContent = buy ? '주문 가능 금액' : '매도 가능 수량';
    $('trd-o-avail').textContent = buy ? (S.participation ? won(S.participation.cash) : '-') : fmt(max) + '주';
    $('trd-o-block').textContent = block;
    show($('trd-o-block'), !!block);
    var over = !block && qty > max;
    if (over) { errBox.textContent = buy ? MSG.NOT_ENOUGH_CASH : MSG.NOT_ENOUGH_QUANTITY; show(errBox, true); }
    else if (errBox.dataset.from !== 'server') show(errBox, false);
    document.querySelectorAll('[data-trd-reason]').forEach(function (c) {
      var on = String(c.dataset.trdReason) === String(S.reasonId);
      c.classList.toggle('is-on', on);
      c.setAttribute('aria-checked', String(on));
    });
    var btn = $('trd-o-submit');
    btn.className = 'btn trd-submit ' + (buy ? 'is-buy' : 'is-sell');
    btn.disabled = !!block || over || !S.reasonId || S.busy;
    btn.textContent = block ? '지금은 주문할 수 없어요'
      : S.reasonId ? (buy ? '매수' : '매도') + ' · ' + fmt(qty) + '주 ' + won(qty * price) : '매매 이유를 골라주세요';
  }

  function orderError(text) {
    var errBox = $('trd-o-error');
    errBox.textContent = text;
    errBox.dataset.from = 'server';
    show(errBox, true);
  }

  function submitOrder(e) {
    e.preventDefault();
    var d = S.stock;
    if (!d || S.busy || blockReason()) return;
    if (!S.reasonId) { orderError(MSG.REASON_REQUIRED); return; }
    var qty = Math.max(1, parseInt($('trd-qty').value, 10) || 1);
    var side = S.side;
    S.busy = true;
    updateOrder();
    api('/orders', {
      method: 'POST',
      headers: { 'Idempotency-Key': uuid() },   // 제출할 때마다 새 키 (DP-37)
      body: { stockId: d.stockId, orderType: side, quantity: qty, turnNo: d.turnNo, reasonId: Number(S.reasonId) }
    }).then(function () {
      delete $('trd-o-error').dataset.from;
      MB.toast(d.name + ' ' + fmt(qty) + '주를 ' + (side === 'BUY' ? '샀어요' : '팔았어요'));
      refreshAll(true);
    }).catch(function (err) {
      if (fatal(err)) return;
      orderError(errText(err));
      // 턴이 넘어가 가격이 바뀌었으면 현재 가격과 시즌 정보를 다시 불러온다 (INV-12)
      if (err.code === 'STALE_TURN_PRICE' || err.code === 'MARKET_HOLIDAY' || err.code === 'SEASON_CLOSED') {
        refreshAll(true, true);
      }
    }).then(function () { S.busy = false; updateOrder(); });
  }

  /* ---------- 랭킹 (API 62) ---------- */
  function loadRanking() {
    var list = $('trd-rank-list');
    var me = $('trd-my-rank');
    show(me, false);
    loading(list);
    api('/seasons/' + S.season.seasonId + '/rankings?type=' + S.rankType + '&page=0&size=10').then(function (d) {
      if (d.seasonName) $('trd-rank-season').textContent = d.seasonName;
      var asset = S.rankType === 'ASSET';
      var items = d.items || [];
      if (!items.length) { note(list, '아직 랭킹이 없어요'); return; }
      list.replaceChildren.apply(list, items.map(function (r) {
        var row = el('div', 'trd-rank-row');
        var who = el('span', 'trd-rank-row__who');
        who.append(el('span', null, r.nickname), el('span', 'trd-tag', r.title || ''));
        row.append(el('span', 'trd-rank-no r' + r.rank, ({ 1: '🥇', 2: '🥈', 3: '🥉' })[r.rank] || String(r.rank)), who,
          asset ? el('strong', null, won(r.netAsset)) : rate(r.returnRate));
        return row;
      }));
      if (d.me) {
        me.replaceChildren(el('strong', null, '나 · ' + (d.me.rank != null ? d.me.rank + '위' : '순위 없음')),
          asset ? el('strong', null, won(d.me.netAsset)) : rate(d.me.returnRate));
        show(me, true);
      }
    }).catch(function (err) {
      if (err.code === 'RANKING_NOT_PUBLISHED') { note(list, MSG.RANKING_NOT_PUBLISHED); return; }
      sectionError(list)(err);
    });
  }

  /* ---------- 다시 불러오기·이벤트 ---------- */
  /** 홈 정보를 다시 받고 영역을 새로 그린다. keepDetail 이면 보고 있는 종목도 다시 받는다 */
  function refreshAll(keepDetail, skipOrderReset) {
    return loadHome().then(function () {
      loadTrades();
      loadTurnNews();
      loadMarket();
      if (keepDetail && S.stock && !$('trd-detail').hidden) {
        var errBox = $('trd-o-error');
        var keepText = skipOrderReset ? errBox.textContent : null;
        loadStock(S.stock.stockId).then(function () { if (keepText) orderError(keepText); });
      }
    }).catch(function (err) {
      if (!fatal(err)) MB.toast(errText(err), 'error');
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
      document.querySelectorAll('[data-trd-market]').forEach(function (b) { b.classList.toggle('is-on', b === t); });
      $('trd-search').value = '';
      loadMarket();
      return;
    }
    if (ds.trdRank) {
      S.rankType = ds.trdRank;
      document.querySelectorAll('[data-trd-rank]').forEach(function (b) { b.classList.toggle('is-on', b === t); });
      loadRanking();
      return;
    }
    if (ds.trdTab) { setTab(ds.trdTab); return; }
    if (ds.trdSide) { S.side = ds.trdSide; delete $('trd-o-error').dataset.from; resetOrder(); return; }
    if (ds.trdStep) {
      var q = $('trd-qty');
      q.value = Math.max(1, (parseInt(q.value, 10) || 1) + Number(ds.trdStep));
      updateOrder();
      return;
    }
    if (ds.trdPct) { $('trd-qty').value = Math.max(1, Math.floor(maxQty() * Number(ds.trdPct))); updateOrder(); return; }
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
    ['trd-holdings', 'trd-trade-list', 'trd-news-list', 'trd-market-list', 'trd-rank-list'].forEach(function (id) { loading($(id)); });

    loadHome().then(function () {
      loadTrades();
      loadMarket();
      loadRanking();
      // 뉴스 → 종목 연결에 전체 종목 이름이 필요해서 먼저 받아 둔다
      fetchStocks('').then(function (stocks) { S.stocks = stocks; }).catch(function () {}).then(loadTurnNews);
    }).catch(function (err) {
      if (!fatal(err)) fullState('모의투자 정보를 불러오지 못했어요', errText(err));
    });
  }

  init();
})();
