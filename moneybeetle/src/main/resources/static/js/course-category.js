/* comment.
*   담당 : DBA ① 신아림 (#52, 강의 목록 /user/lectures)
*   GET /api/v1/categories 로 카테고리·소주제를, GET /api/v1/courses?categoryId= 로 그 카테고리 강의를 (마지막 페이지까지) 받아 그린다.
*   - cat=money|estate (사이드바 링크) → 첫 번째 / 두 번째 카테고리. categoryId= 로 직접 고를 수도 있다
*   - 소주제 칩은 화면 안에서 거른다(전체 강의를 한 번에 받아 둠). 검색어는 제목·강사·소주제에 대해 거른다
*   - 카드형/목록형 선택은 localStorage(mb-lecture-view)에 저장한다 (막혀 있어도 화면은 동작)
*  */
(function () {
  var root = document.querySelector('[data-lec-category]');
  if (!root || !window.MB || !window.MBLec) return;
  var L = window.MBLec;
  var h = L.h;

  // 카테고리·소주제 소개 문구 (화면설계 시안 문구. DB에 설명 칸이 없어 이름으로 찾고, 없으면 비워 둔다)
  var DESCRIPTIONS = {
    '돈 굴리기': '월급 관리부터 ETF까지, 내 집 마련 자금을 불리는 방법을 차례대로 배워요.',
    '부동산': '청약부터 전·월세, 대출까지 첫 집을 구하는 과정을 배워요.',
    '월급 쪼개기': '월급이 들어오면 먼저 나누는 습관',
    '예금·적금': '가장 안전하게 돈을 모으는 방법',
    '국내 주식': '주식이 무엇인지부터 차근차근',
    '미국 주식': '환율까지 함께 보는 해외 투자',
    'ETF·분산 투자': '한 바구니에 담지 않는 투자',
    '청약': '내 집 마련의 첫 단추',
    '전·월세': '계약 전에 꼭 확인할 것들',
    '대출': '내게 맞는 대출 고르기'
  };
  var CAT_KEYS = ['money', 'estate'];   // 사이드바 링크의 cat 값 → 카테고리 순서
  var VIEW_KEY = 'mb-lecture-view';
  var PAGE_SIZE = 100;

  var params = new URLSearchParams(location.search);
  var state = { categories: [], top: null, courses: [], sub: 'all', q: params.get('q') || '', view: loadView() };

  var els = {
    tabs: root.querySelector('[data-lec-tabs]'),
    names: root.querySelectorAll('[data-lec-cat-name]'),
    desc: root.querySelector('[data-lec-cat-desc]'),
    done: root.querySelector('[data-lec-done]'),
    total: root.querySelector('[data-lec-total]'),
    doneBar: root.querySelector('[data-lec-done-bar]'),
    chips: root.querySelector('[data-lec-chips]'),
    q: root.querySelector('[data-lec-q]'),
    shown: root.querySelector('[data-lec-shown]'),
    sections: root.querySelector('[data-lec-sections]'),
    viewButtons: root.querySelectorAll('[data-lec-view]')
  };

  function loadView() {
    try { return localStorage.getItem(VIEW_KEY) === 'list' ? 'list' : 'grid'; } catch (e) { return 'grid'; }
  }
  function saveView(view) {
    try { localStorage.setItem(VIEW_KEY, view); } catch (e) { /* 저장소가 막혀 있어도 화면은 그대로 */ }
  }

  /* 주소의 cat·categoryId 로 상위 카테고리를 고른다. 모르면 첫 번째 */
  function pickTop(categories) {
    var byId = Number(params.get('categoryId'));
    var found = categories.find(function (c) { return c.categoryId === byId; });
    if (found) return found;
    var idx = CAT_KEYS.indexOf(params.get('cat'));
    return categories[idx >= 0 && idx < categories.length ? idx : 0] || null;
  }

  function catKeyOf(top) {
    var idx = state.categories.indexOf(top);
    return idx >= 0 && idx < CAT_KEYS.length ? 'cat=' + CAT_KEYS[idx] : 'categoryId=' + top.categoryId;
  }

  /* 카테고리 강의를 마지막 페이지까지 받아 합친다 (한 페이지 최대 100개) */
  function fetchCourses(categoryId, number, courses) {
    return MB.api('/api/v1/courses?categoryId=' + categoryId + '&page=' + number + '&size=' + PAGE_SIZE)
      .then(function (page) {
        var all = courses.concat((page && page.items) || []);
        return page && number + 1 < page.totalPages ? fetchCourses(categoryId, number + 1, all) : all;
      });
  }

  function load() {
    els.sections.replaceChildren(h('div', { class: 'lec-state', text: '강의를 불러오는 중이에요…' }));
    MB.api('/api/v1/categories').then(function (categories) {
      state.categories = categories || [];
      state.top = pickTop(state.categories);
      if (!state.top) {
        els.sections.replaceChildren(h('div', { class: 'lec-state', text: '아직 열린 강의 카테고리가 없어요.' }));
        return null;
      }
      return fetchCourses(state.top.categoryId, 0, []);
    }).then(function (courses) {
      if (!state.top) return;
      state.courses = courses || [];
      render();
    }).catch(function (err) {
      els.sections.replaceChildren(L.errorBox(err, load));
    });
  }

  function matches(course, subName) {
    var q = state.q.trim().toLowerCase();
    if (!q) return true;
    return [course.title, course.instructorName, subName].some(function (v) {
      return v && String(v).toLowerCase().indexOf(q) >= 0;
    });
  }

  function render() {
    var top = state.top;
    var subs = top.children || [];
    els.names.forEach(function (el) { el.textContent = top.name; });
    els.desc.textContent = DESCRIPTIONS[top.name] || '';
    document.title = top.name + ' · Money Beetle';

    // 상위 탭 (전체 강의 탭은 두지 않는다, 화면설계 5.2.3)
    els.tabs.replaceChildren.apply(els.tabs, state.categories.map(function (c) {
      return h('a', { class: 'lec-tab', href: '/user/lectures?' + catKeyOf(c),
        'aria-current': c === top ? 'page' : null, text: c.name });
    }));

    var done = state.courses.filter(function (c) { return c.status === 'COMPLETED'; }).length;
    els.done.textContent = done;
    els.total.textContent = state.courses.length;
    els.doneBar.style.width = L.percent(done, state.courses.length) + '%';

    // 소주제 칩: 첫 칩은 전체
    var chips = [{ key: 'all', no: state.courses.length, name: '전체' }].concat(subs.map(function (s, i) {
      return { key: String(s.categoryId), no: i + 1, name: s.name };
    }));
    els.chips.replaceChildren.apply(els.chips, chips.map(function (chip) {
      return h('button', { type: 'button', class: 'lec-chip', 'aria-pressed': String(state.sub === chip.key),
        onclick: function () { state.sub = chip.key; render(); } }, [
        h('span', { class: 'lec-chip__no', text: String(chip.no) }), chip.name
      ]);
    }));

    els.viewButtons.forEach(function (btn) {
      btn.setAttribute('aria-pressed', String(btn.getAttribute('data-lec-view') === state.view));
    });

    // 소주제별 구역
    var shown = 0;
    var sections = subs.map(function (sub, i) {
      if (state.sub !== 'all' && state.sub !== String(sub.categoryId)) return null;
      var all = state.courses.filter(function (c) { return c.categoryId === sub.categoryId; });
      var items = all.filter(function (c) { return matches(c, sub.name); });
      shown += items.length;
      if (state.q.trim() && !items.length && state.sub === 'all') return null;   // 검색 중에는 결과 없는 소주제를 숨긴다
      return renderSection(sub, i, all, items);
    }).filter(Boolean);
    els.shown.textContent = shown;

    if (!sections.length) {
      els.sections.replaceChildren(h('div', { class: 'lec-state', text: '검색 결과가 없어요. 다른 단어로 찾아보세요.' }));
      return;
    }
    els.sections.replaceChildren.apply(els.sections, sections);
  }

  function renderSection(sub, i, all, items) {
    var doneCount = all.filter(function (c) { return c.status === 'COMPLETED'; }).length;
    var body;
    if (!all.length) {
      body = h('div', { class: 'lec-state' }, ['아직 등록된 강의가 없어요.', h('br'), '새로운 강의가 등록되면 알려드릴게요.']);
    } else if (!items.length) {
      body = h('div', { class: 'lec-state', text: '이 소주제에는 검색 결과가 없어요.' });
    } else if (state.view === 'list') {
      body = h('div', { class: 'lec-list' }, items.map(function (c) { return renderRow(c, sub, i); }));
    } else {
      body = h('div', { class: 'lec-grid' }, items.map(function (c) { return renderCard(c, sub, i); }));
    }
    return h('section', { class: 'lec-section', 'aria-labelledby': 'lec-sub-' + sub.categoryId }, [
      h('div', { class: 'lec-section__head' }, [
        h('div', { class: 'lec-section__title' }, [
          h('span', { class: 'lec-section__num', text: String(i + 1).padStart(2, '0') }),
          h('h2', { id: 'lec-sub-' + sub.categoryId, text: sub.name }),
          DESCRIPTIONS[sub.name] ? h('p', { class: 'lec-section__desc', text: DESCRIPTIONS[sub.name] }) : null
        ]),
        h('span', { class: 'lec-section__done', text: '완료 ' + doneCount + ' / ' + all.length + '강' })
      ]),
      body
    ]);
  }

  function metaText(c) {
    return (c.instructorName ? c.instructorName + ' 강사 · ' : '') + '영상 ' + c.lessonCount + '개';
  }

  function detailHref(c) {
    return '/user/lecture-detail?courseId=' + c.courseId;
  }

  function renderCard(c, sub, i) {
    var inProgress = c.status === 'IN_PROGRESS';
    var pct = L.percent(c.watchedLessonCount, c.lessonCount);
    return h('a', { class: 'lec-card', href: detailHref(c) }, [
      h('div', { class: 'lec-thumb', style: L.thumbStyle(c.thumbnailUrl, i) }, [
        L.badge(c.status),
        c.thumbnailUrl ? null : h('span', { class: 'lec-thumb__sub', text: sub.name })
      ]),
      h('div', { class: 'lec-card__body' }, [
        h('span', { class: 'lec-title', text: c.title }),
        h('span', { class: 'lec-meta', text: metaText(c) }),
        inProgress ? h('div', { class: 'lec-progress' }, [
          h('div', { class: 'lec-bar lec-bar--thin' }, [h('span', { style: 'width:' + pct + '%' })]),
          h('span', { text: '영상 ' + c.watchedLessonCount + '/' + c.lessonCount + ' · 이어보기' })
        ]) : null,
        c.infoBaseDate ? h('span', { class: 'lec-base', text: '정보 기준일 ' + c.infoBaseDate }) : null
      ])
    ]);
  }

  function renderRow(c, sub, i) {
    var pct = c.status === 'COMPLETED' ? 100 : L.percent(c.watchedLessonCount, c.lessonCount);
    return h('a', { class: 'lec-row', href: detailHref(c) }, [
      h('span', { class: 'lec-row__thumb', style: L.thumbStyle(c.thumbnailUrl, i), text: c.thumbnailUrl ? '' : sub.name }),
      h('span', { class: 'lec-row__text' }, [
        h('span', { class: 'lec-title', text: c.title }),
        h('span', { class: 'lec-meta', text: metaText(c) + ' · ' + sub.name }),
        c.infoBaseDate ? h('span', { class: 'lec-base', text: '정보 기준일 ' + c.infoBaseDate }) : null
      ]),
      h('span', { class: 'lec-row__side' }, [
        h('span', { class: 'lec-row__state' }, [
          L.badge(c.status),
          h('span', { class: 'lec-row__pct' }, [
            h('span', { class: 'lec-bar lec-bar--thin' }, [h('span', { style: 'width:' + pct + '%' })]),
            h('span', { text: pct + '%' })
          ])
        ]),
        h('span', { 'aria-hidden': 'true', text: '›' })
      ])
    ]);
  }

  els.viewButtons.forEach(function (btn) {
    btn.addEventListener('click', function () {
      state.view = btn.getAttribute('data-lec-view') === 'list' ? 'list' : 'grid';
      saveView(state.view);
      if (state.top) render();
    });
  });
  els.q.value = state.q;
  els.q.addEventListener('input', function () {
    state.q = els.q.value;
    if (state.top) render();
  });

  load();
})();
