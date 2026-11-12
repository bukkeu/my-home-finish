/* comment.
*   담당 : PM 이재성 (#56, 통합 검색 화면)
*   templates/home/search.html 의 결과 영역을 API 28 (GET /api/v1/search?keyword=) 로 채운다. 요청은 공통 MB.api(common.js)로 보낸다.
*   - 검색어는 주소의 q (공통 헤더 검색창이 보내는 이름) 에서 읽는다. 검색 버튼은 같은 주소로 다시 이동(GET)한다.
*   - 응답 courses(강의 제목·카테고리 경로), stocks(종목 이름·분야), terms(경제 용어) 를 종류별로 보여 주고 종류 칩으로 거른다(화면에서만).
*   - 종목은 모의투자가 해금된 회원에게만 서버가 결과에 넣어 준다 (INV-01, NFR-09).
*   - 강의는 서버가 최근 강의부터 최대 50개만 준다 (CourseService.SEARCH_LIMIT, #296). 50개를 받으면 더 있을 수 있으니
*     건수를 "50건+"로 보이고 안내 한 줄을 붙인다 (#341, 추가 쿼리 없이 화면에서만).
*   서버가 준 문구는 모두 textContent 로 넣는다 (HTML 로 해석하지 않는다). 모양은 디자인(UserSearch.dc.html)과 같다.
*  */
(function () {
  'use strict';
  if (!window.MB || !window.MB.api) return;

  /** 서버가 주는 강의 최대 개수 (CourseService.SEARCH_LIMIT 과 같아야 한다). 이만큼 받으면 더 있을 수 있다 */
  var COURSE_LIMIT = 50;

  var GROUPS = [
    { key: 'courses', label: '강의', badge: '', limit: COURSE_LIMIT, href: function (i) { return '/user/lecture-detail?courseId=' + encodeURIComponent(i.courseId); },
      title: function (i) { return i.title; }, desc: function () { return ''; },
      tags: function (i) { return String(i.categoryName || '').split('›').map(function (s) { return s.trim(); }).filter(Boolean); } },
    { key: 'stocks', label: '종목', badge: 'is-stock', href: function (i) { return '/user/demo-trading?stockId=' + encodeURIComponent(i.stockId); },
      title: function (i) { return i.name; }, desc: function () { return ''; },
      tags: function (i) { return i.sectorName ? [i.sectorName] : []; } },
    { key: 'terms', label: '경제 용어', badge: 'is-term', href: null,
      title: function (i) { return i.term; }, desc: function (i) { return i.description || ''; },
      tags: function () { return []; } }
  ];

  var S = { q: '', filter: 'all', data: null };

  function $(id) { return document.getElementById(id); }
  function el(tag, cls, text) {
    var n = document.createElement(tag);
    if (cls) n.className = cls;
    if (text != null) n.textContent = text;
    return n;
  }
  function show(node, on) { node.hidden = !on; }

  /** 검색어와 같은 글자를 디자인처럼 형광펜(srch-hit)으로 칠한다 (대소문자 구분 없음) */
  function highlight(parent, text, q) {
    var lower = String(text).toLowerCase();
    var word = q.toLowerCase();
    var from = 0;
    var at;
    while (word && (at = lower.indexOf(word, from)) >= 0) {
      if (at > from) parent.appendChild(document.createTextNode(text.slice(from, at)));
      parent.appendChild(el('span', 'srch-hit', text.slice(at, at + word.length)));
      from = at + word.length;
    }
    if (from < text.length) parent.appendChild(document.createTextNode(text.slice(from)));
  }

  function notice(title, desc, isError) {
    var box = el('div', 'srch-empty' + (isError ? ' is-error' : ''));
    box.append(el('strong', null, title), el('span', null, desc));
    return box;
  }

  /** 종류별 건수와, 상한에 닿아 실제로는 더 있을 수 있는지(more) */
  function counts(d) {
    var c = { all: 0, more: {} };
    GROUPS.forEach(function (g) {
      c[g.key] = (d[g.key] || []).length;
      c.all += c[g.key];
      c.more[g.key] = !!g.limit && c[g.key] >= g.limit;
      if (c.more[g.key]) c.more.all = true;
    });
    return c;
  }

  /** 건수 글자 : 더 있을 수 있으면 맨 뒤에 + (예: countText(c, 'courses', '건') → 50건+, 칩은 50+) */
  function countText(c, key, unit) {
    return c[key] + (unit || '') + (c.more[key] ? '+' : '');
  }

  function renderHead(c) {
    var title = $('srch-title');
    title.replaceChildren(document.createTextNode('‘'), el('span', null, S.q), document.createTextNode('’ 검색 결과'));
    $('srch-count').textContent = '전체 ' + countText(c, 'all', '건');
    show($('srch-head'), true);
  }

  function renderChips(c) {
    var box = $('srch-chips');
    var items = [{ key: 'all', label: '전체' }].concat(GROUPS);
    box.replaceChildren.apply(box, items.map(function (g) {
      var b = el('button', 'srch-chip');
      b.type = 'button';
      b.dataset.srchFilter = g.key;
      b.setAttribute('aria-pressed', String(S.filter === g.key));
      b.append(el('span', null, g.label), el('small', null, countText(c, g.key)));
      return b;
    }));
    show(box, true);
  }

  function renderBody() {
    var d = S.data;
    var c = counts(d);
    var body = $('srch-result');
    renderHead(c);
    renderChips(c);
    if (!c.all) {
      var none = notice('', '철자를 확인하거나 다른 단어로 검색해 보세요.');
      var strong = none.querySelector('strong');
      strong.append(document.createTextNode('‘'), el('span', null, S.q), document.createTextNode('’에 맞는 결과가 없어요'));
      body.replaceChildren(none);
      return;
    }
    var sections = [];
    GROUPS.forEach(function (g) {
      var list = d[g.key] || [];
      if (!list.length || (S.filter !== 'all' && S.filter !== g.key)) return;
      var section = el('section', 'srch-group');
      var head = el('div', 'srch-group__head');
      head.append(el('span', 'srch-badge ' + g.badge, g.label), el('strong', null, countText(c, g.key, '건')));
      var box = el('div', 'srch-list');
      list.forEach(function (item) {
        var link = g.href ? el('a', 'srch-card') : el('div', 'srch-card');
        if (g.href) link.href = g.href(item);
        var title = el('span', 'srch-card__title');
        highlight(title, String(g.title(item) || ''), S.q);
        link.appendChild(title);
        var desc = g.desc(item);
        if (desc) {
          var d2 = el('span', 'srch-card__desc');
          highlight(d2, desc, S.q);
          link.appendChild(d2);
        }
        var tags = g.tags(item);
        if (tags.length) {
          var meta = el('span', 'srch-card__meta');
          tags.forEach(function (t) { meta.appendChild(el('span', null, t)); });
          link.appendChild(meta);
        }
        box.appendChild(link);
      });
      section.appendChild(head);
      if (c.more[g.key]) {
        section.appendChild(el('p', 'srch-group__note',
            '최근 ' + g.label + ' ' + g.limit + '건까지만 보여 드려요. 검색어를 더 자세히 입력해 보세요.'));
      }
      section.appendChild(box);
      sections.push(section);
    });
    body.replaceChildren.apply(body, sections);
  }

  function search() {
    var body = $('srch-result');
    body.replaceChildren(notice('검색하는 중이에요', '잠시만 기다려 주세요.'));
    return MB.api('/api/v1/search?keyword=' + encodeURIComponent(S.q)).then(function (d) {
      S.data = d || {};
      renderBody();
    }).catch(function (err) {
      show($('srch-head'), false);
      show($('srch-chips'), false);
      body.replaceChildren(notice('검색 결과를 불러오지 못했어요', (err && err.message) || '잠시 뒤에 다시 시도해 주세요.', true));
    });
  }

  function init() {
    var params = new URLSearchParams(window.location.search);
    S.q = (params.get('q') || params.get('keyword') || '').trim();
    $('srch-q').value = S.q;
    $('srch-chips').addEventListener('click', function (e) {
      var b = e.target.closest('button[data-srch-filter]');
      if (!b || !S.data) return;
      S.filter = b.dataset.srchFilter;
      renderBody();
    });
    if (!S.q) {
      $('srch-result').replaceChildren(notice('검색어를 입력해 주세요', '강의 이름이나 종목 이름으로 찾아볼 수 있어요.'));
      $('srch-q').focus();
      return;
    }
    search();
  }

  init();
})();
