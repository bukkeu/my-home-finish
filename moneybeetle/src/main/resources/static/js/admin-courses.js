/* comment.
*   담당 : DBA ① 신아림 (#253, 관리자 강의 운영 탭 - #34 카테고리, #35 강의 검수·공개 관리)
*   조각 : templates/fragments/admin/courses.html (틀 admin/dashboard.html?view=courses, #61)
*   - 검수 대기 : GET /api/v1/admin/courses?status=REVIEW (API 89) → 고르면 GET .../{courseId} (API 90)로 영상·퀴즈(정답 포함)·검수 이력 확인
*                 허가하고 공개 POST .../approve (API 91), 반려 POST .../reject { reason } (API 92, 사유 필수)
*   - 전체 강의 : 상태별 목록(API 89), 공개·비공개 PATCH .../visibility { isPublic } (API 93, 허가된 강의만), 삭제 DELETE (API 94, 학습 기록 있으면 서버가 거절)
*   - 카테고리 : GET /api/v1/admin/categories (API 85), POST (86), PUT .../{categoryId} (87), DELETE (88, 연결이 있으면 서버가 비활성으로 바꿈)
*   - 하위 메뉴는 주소 &tab=review|all|categories 로 기억한다 (새로고침해도 같은 메뉴)
*   - 오류는 서버 문구를 그대로 보여 준다. 되돌릴 수 없는 작업(허가·반려·삭제)은 확인 창(MBA.confirm)을 거친다
*  */
(function () {
  'use strict';

  var root = document.querySelector('[data-ao-courses]');
  if (!root || !window.MB || !window.MBA) return;

  var API = '/api/v1/admin/courses';
  var CAT_API = '/api/v1/admin/categories';
  var PAGE_SIZE = 20;
  var TABS = ['review', 'all', 'categories'];
  var STATUS = { DRAFT: ['작성 중', 'off'], REVIEW: ['검수 중', 'wait'], APPROVED: ['허가', 'ok'], REJECTED: ['반려', 'bad'] };
  var TEST_COUNT = 5, MAIN_MIN = 5, MAIN_MAX = 15, LESSON_MIN = 1, LESSON_MAX = 3;

  var st = {
    tab: 'review',
    loaded: {},
    paths: {},                                        // 소주제 ID → '카테고리 › 소주제'
    queue: [], picked: null, detail: null, busy: false,
    list: { items: [], total: 0, status: '', busy: {} },
    cats: []
  };

  /* ---------- 도구 ---------- */

  function h(tag, attrs, children) {
    var el = document.createElement(tag);
    Object.keys(attrs || {}).forEach(function (key) {
      var v = attrs[key];
      if (v === null || v === undefined || v === false) return;
      if (key === 'text') el.textContent = v;
      else if (key === 'class') el.className = v;
      else if (key === 'value') el.value = v;
      else if (key.indexOf('on') === 0 && typeof v === 'function') el.addEventListener(key.slice(2), v);
      else el.setAttribute(key, v === true ? '' : v);
    });
    (children || []).forEach(function (c) {
      if (c === null || c === undefined || c === false) return;
      el.appendChild(typeof c === 'string' ? document.createTextNode(c) : c);
    });
    return el;
  }

  function fill(el, nodes) {
    el.replaceChildren.apply(el, (Array.isArray(nodes) ? nodes : [nodes]).filter(Boolean));
  }

  /* 서버 오류 문구. 입력 칸 오류(fields: [{ field, message }])가 있으면 그 문구를 붙인다 */
  function errText(err, fallback) {
    var text = (err && err.message) || fallback || '요청을 처리하지 못했어요.';
    var fields = err && err.fields;
    if (Array.isArray(fields) && fields.length) {
      text += ' (' + fields.map(function (f) { return f && f.message; }).filter(Boolean).join(', ') + ')';
    }
    return text;
  }

  function day(value) {
    if (!value) return '-';
    var d = new Date(value);
    if (isNaN(d)) return '-';
    return d.getFullYear() + '.' + String(d.getMonth() + 1).padStart(2, '0') + '.' + String(d.getDate()).padStart(2, '0');
  }

  function minutes(sec) {
    var s = Math.max(0, Math.round(sec || 0));
    return s < 60 ? s + '초' : Math.floor(s / 60) + '분' + (s % 60 ? ' ' + (s % 60) + '초' : '');
  }

  function badge(status) {
    var s = STATUS[status] || [status || '-', 'off'];
    return h('span', { class: 'ao-badge ao-badge--' + s[1], text: s[0] });
  }

  function stateBox(text, retry) {
    return h('div', { class: 'ao-state', role: retry ? 'alert' : null }, [
      h('span', { text: text }),
      retry ? h('button', { type: 'button', class: 'btn btn-secondary', text: '다시 불러오기', onclick: retry }) : null
    ]);
  }

  /* 쪽마다 받아 모두 모은다 (검수 대기·카테고리처럼 한 화면에 다 보여야 하는 목록) */
  function fetchAll(url, max) {
    var items = [];
    function next(page) {
      return MB.api(url + (url.indexOf('?') < 0 ? '?' : '&') + 'page=' + page + '&size=50').then(function (res) {
        items = items.concat((res && res.items) || []);
        var pages = (res && res.totalPages) || 0;
        if (page + 1 < pages && page + 1 < (max || 20)) return next(page + 1);
        return items;
      });
    }
    return next(0);
  }

  /* ---------- 하위 메뉴 ---------- */

  /* 같은 화면 안의 하위 메뉴 탭 (role=tab). ←·→ 로 옆 탭, Home·End 로 처음·끝 탭 */
  function bindTabs(root, onPick) {
    var tabs = Array.prototype.slice.call(root.querySelectorAll('[data-ao-tab]'));
    tabs.forEach(function (b, i) {
      b.addEventListener('click', function () { onPick(b.dataset.aoTab); });
      b.addEventListener('keydown', function (e) {
        var to = e.key === 'ArrowRight' ? (i + 1) % tabs.length : e.key === 'ArrowLeft' ? (i - 1 + tabs.length) % tabs.length
          : e.key === 'Home' ? 0 : e.key === 'End' ? tabs.length - 1 : -1;
        if (to < 0) return;
        e.preventDefault();
        onPick(tabs[to].dataset.aoTab);
        tabs[to].focus();
      });
    });
  }
  function markTabs(root, tab) {
    root.querySelectorAll('[data-ao-tab]').forEach(function (b) {
      var on = b.dataset.aoTab === tab;
      b.setAttribute('aria-selected', String(on));
      b.tabIndex = on ? 0 : -1;
    });
  }

  function showTab(tab, keepUrl) {
    if (TABS.indexOf(tab) < 0) tab = 'review';
    st.tab = tab;
    markTabs(root, tab);
    root.querySelectorAll('[data-ao-pane]').forEach(function (p) { p.hidden = p.dataset.aoPane !== tab; });
    if (!keepUrl && history.replaceState) {
      var q = new URLSearchParams(location.search);
      q.set('view', 'courses');
      q.set('tab', tab);
      history.replaceState(null, '', location.pathname + '?' + q.toString());
    }
    if (!st.loaded[tab]) {
      st.loaded[tab] = true;
      if (tab === 'review') loadQueue();
      else if (tab === 'all') loadList(true);
      else loadCats();
    }
  }

  bindTabs(root, function (tab) { if (st.tab !== tab) showTab(tab); });

  /* 카테고리 경로 (검수 상세에 '부동산 › 청약'으로 보이기). 실패해도 화면은 그대로 쓴다 */
  var pathsReady = null;
  function loadPaths() {
    if (!pathsReady) {
      pathsReady = MB.api(CAT_API).then(function (tree) {
        setPaths(tree);
      }).catch(function () { pathsReady = null; });
    }
    return pathsReady || Promise.resolve();
  }
  function setPaths(tree) {
    st.paths = {};
    (tree || []).forEach(function (top) {
      st.paths[top.categoryId] = top.name;
      (top.children || []).forEach(function (sub) { st.paths[sub.categoryId] = top.name + ' › ' + sub.name; });
    });
  }

  /* ---------- 검수 대기 (API 89·90·91·92) ---------- */

  var queueBox = root.querySelector('[data-ao-queue]');
  var detailBox = root.querySelector('[data-ao-detail]');
  var reviewCount = root.querySelector('[data-ao-review-count]');

  function loadQueue() {
    fill(queueBox, h('p', { class: 'adm-empty', text: '불러오고 있어요…' }));
    loadPaths();
    return fetchAll(API + '?status=REVIEW').then(function (items) {
      st.queue = items;
      drawQueue();
      if (st.picked && !items.some(function (c) { return c.courseId === st.picked; })) clearDetail();
    }, function (err) {
      fill(queueBox, stateBox(errText(err, '검수 대기 강의를 불러오지 못했어요.'), loadQueue));
    });
  }

  function drawQueue() {
    reviewCount.hidden = !st.queue.length;
    reviewCount.textContent = String(st.queue.length);
    if (!st.queue.length) {
      fill(queueBox, h('p', { class: 'adm-empty', text: '검수할 강의가 없어요.' }));
      return;
    }
    fill(queueBox, st.queue.map(function (c) {
      return h('button', { type: 'button', class: 'ao-pick', 'aria-current': String(st.picked === c.courseId),
        onclick: function () { pick(c.courseId); } }, [
        h('span', { class: 'ao-post__tags' }, [badge(c.status), h('span', { text: '요청 ' + day(c.submittedAt) })]),
        h('strong', { text: c.title || '(제목 없음)' }),
        h('span', { class: 'ao-td-sub', text: (c.instructorName || '강사') + ' 강사' })
      ]);
    }));
  }

  function clearDetail() {
    st.picked = null;
    st.detail = null;
    fill(detailBox, h('p', { class: 'adm-empty', text: '왼쪽에서 검수할 강의를 골라 주세요.' }));
    drawQueue();
  }

  function pick(courseId) {
    if (st.busy) return;                                // 허가·반려 처리 중에는 다른 강의를 고르지 않는다
    st.picked = courseId;
    st.detail = null;
    drawQueue();
    fill(detailBox, h('p', { class: 'adm-empty', text: '강의 내용을 불러오고 있어요…' }));
    Promise.all([MB.api(API + '/' + courseId), loadPaths()]).then(function (res) {
      if (st.picked !== courseId) return;               // 그사이 다른 강의를 골랐으면 버린다
      st.detail = res[0];
      drawDetail();
      detailBox.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
    }, function (err) {
      if (st.picked !== courseId) return;
      fill(detailBox, stateBox(errText(err, '강의 내용을 불러오지 못했어요.'), function () { pick(courseId); }));
    });
  }

  function checkItem(ok, text) {
    return h('div', { class: 'ao-check' + (ok ? '' : ' is-bad') }, [h('b', { 'aria-hidden': 'true', text: ok ? '✓' : '!' }), text, ok ? null : h('span', { class: 'ao-sr', text: ' (확인 필요)' })]);
  }

  function quizBlock(q, i) {
    var choices = (q.choices || []).slice().sort(function (a, b) { return a.sortOrder - b.sortOrder; });
    return h('div', { class: 'ao-quiz' }, [
      h('p', {}, [h('b', { text: (i + 1) + '. ' }), q.content || '(문제 없음)']),
      h('ol', {}, choices.map(function (c) {
        return h('li', { class: c.isCorrect ? 'is-correct' : null }, [c.content || '', c.isCorrect ? ' ✓ 정답' : null]);
      })),
      h('p', { class: 'ao-meta', text: '해설: ' + ((q.explanation || '').trim() || '(비어 있음)') })
    ]);
  }

  function drawDetail() {
    var d = st.detail;
    var lessons = (d.lessons || []).slice().sort(function (a, b) { return a.sortOrder - b.sortOrder; });
    var quizzes = d.quizzes || [];
    var tests = quizzes.filter(function (q) { return q.quizType === 'TEST'; }).sort(function (a, b) { return a.sortOrder - b.sortOrder; });
    var mains = quizzes.filter(function (q) { return q.quizType === 'MAIN'; }).sort(function (a, b) { return a.sortOrder - b.sortOrder; });
    var noExplain = quizzes.filter(function (q) { return !(q.explanation || '').trim(); }).length;
    var lastReject = (d.reviews || []).filter(function (r) { return r.result === 'REJECTED' && r.reason; })[0];
    var inReview = d.status === 'REVIEW';

    var reason = h('textarea', { class: 'ao-textarea', maxlength: '500', placeholder: '예) 3강 영상 음질이 고르지 않아요. 다시 녹음해 주세요',
      'aria-label': '반려 사유', disabled: !inReview || st.busy });
    var error = h('span', { class: 'adm-error', role: 'alert' });
    reason.addEventListener('input', function () { reason.removeAttribute('aria-invalid'); error.textContent = ''; });

    var rejectBtn = h('button', { type: 'button', class: 'btn btn-ghost adm-btn-danger', text: '반려', disabled: !inReview || st.busy });
    var approveBtn = h('button', { type: 'button', class: 'btn btn-primary', text: '허가하고 공개', disabled: !inReview || st.busy });
    rejectBtn.addEventListener('click', function () {
      var text = reason.value.trim();
      if (!text) {
        reason.setAttribute('aria-invalid', 'true');
        error.textContent = '반려 사유를 입력해 주세요. 강사에게 그대로 전달돼요.';
        reason.focus();
        return;
      }
      MBA.confirm('이 강의를 반려할까요?', '강사에게 반려 사유가 알림으로 전달되고 강의는 비공개가 돼요.\n\n' + text, '반려').then(function (ok) {
        if (ok) decide(d, 'reject', { reason: text }, error);
      });
    });
    approveBtn.addEventListener('click', function () {
      var warn = checks.ok ? '' : '\n\n확인이 필요한 항목이 있어요. 그래도 허가할까요?';
      MBA.confirm('이 강의를 허가하고 공개할까요?', '허가하면 바로 수강생에게 공개돼요.' + warn, '허가하고 공개').then(function (ok) {
        if (ok) decide(d, 'approve', null, error);
      });
    });

    var checks = {
      lessons: lessons.length >= LESSON_MIN && lessons.length <= LESSON_MAX,
      tests: tests.length === TEST_COUNT,
      mains: mains.length >= MAIN_MIN && mains.length <= MAIN_MAX,
      explain: noExplain === 0
    };
    checks.ok = checks.lessons && checks.tests && checks.mains && checks.explain;

    fill(detailBox, [
      h('span', { class: 'ao-meta', text: [st.paths[d.categoryId], (d.instructorName || '강사') + ' 강사', '요청 ' + day(d.submittedAt)].filter(Boolean).join(' · ') }),
      h('h2', { text: d.title || '(제목 없음)' }),
      d.infoBaseDate || d.infoSource ? h('span', { class: 'ao-meta', text: '정보 기준일 ' + (d.infoBaseDate || '-') + (d.infoSource ? ' · 출처 ' + d.infoSource : '') }) : null,
      d.description ? h('p', { class: 'ao-desc', text: d.description }) : null,
      inReview ? null : h('p', { class: 'ao-prev', text: '지금은 검수 중인 강의가 아니에요. (' + (STATUS[d.status] || [d.status])[0] + ')' }),
      h('div', { class: 'ao-checks' }, [
        checkItem(checks.lessons, '영상 ' + lessons.length + '개 (1~3개)'),
        checkItem(checks.tests, '테스트 퀴즈 ' + tests.length + '문제 (5문제)'),
        checkItem(checks.mains, '퀴즈 ' + mains.length + '문제 (5~15문제)'),
        checkItem(checks.explain, noExplain ? '해설 빈 문제 ' + noExplain + '개' : '모든 문제에 해설 있음'),
        checkItem(!!d.thumbnailUrl, d.thumbnailUrl ? '썸네일 있음' : '썸네일 없음')
      ]),
      lastReject ? h('p', { class: 'ao-prev', text: '이전 반려 사유 · ' + day(lastReject.createdAt) + '\n' + lastReject.reason }) : null,
      h('div', { class: 'ao-sec' }, [h('h3', { text: '영상 ' + lessons.length + '개' })].concat(lessons.map(function (l, i) {
        var playable = l.videoUrl && /^\/uploads\//.test(l.videoUrl);
        return h('div', { class: 'ao-lesson' }, [
          h('b', { text: (i + 1) + '. ' + (l.title || '(제목 없음)') + ' · ' + minutes(l.durationSeconds) }),
          playable ? h('video', { controls: true, preload: 'none', src: l.videoUrl, 'aria-label': (i + 1) + '번째 영상' }) : h('span', { class: 'ao-meta', text: '영상 파일이 없어요' }),
          h('p', { class: 'ao-desc', text: (l.summary || '').trim() || '(요약글 없음)' })
        ]);
      }))),
      h('div', { class: 'ao-sec' }, [h('h3', { text: '테스트 퀴즈 (OX) ' + tests.length + '문제' })].concat(tests.map(quizBlock))),
      h('div', { class: 'ao-sec' }, [h('h3', { text: '퀴즈 ' + mains.length + '문제' })].concat(mains.map(quizBlock))),
      h('label', { class: 'adm-field' }, ['반려 사유', h('small', { text: '반려할 때만 필수예요. 강사에게 그대로 전달돼요 (500자).' }), reason]),
      error,
      h('div', { class: 'adm-actions' }, [rejectBtn, approveBtn])
    ]);
  }

  function decide(d, action, body, error) {
    if (st.busy) return;
    st.busy = true;
    drawDetailKeep();
    MB.api(API + '/' + d.courseId + '/' + action, { method: 'POST', body: body || {} }).then(function () {
      MB.toast(action === 'approve' ? '허가하고 공개했어요.' : '반려했어요. 강사에게 알림이 갔어요.');
      st.busy = false;
      st.queue = st.queue.filter(function (c) { return c.courseId !== d.courseId; });
      st.loaded.all = false;                               // 전체 강의 목록은 다음에 열 때 새로 받는다
      clearDetail();
      loadQueue();
    }, function (err) {
      st.busy = false;
      // 다시 그리지 않고 잠갔던 입력만 푼다 (입력한 반려 사유가 남는다)
      detailBox.querySelectorAll('button, textarea').forEach(function (el) { el.disabled = false; });
      if (error) error.textContent = errText(err, '처리하지 못했어요.');
      if (err && err.code === 'COURSE_NOT_IN_REVIEW') loadQueue();   // 다른 관리자가 먼저 처리한 경우
    });
  }

  /* 처리 중에는 버튼만 잠근다 (입력한 반려 사유는 그대로 둔다) */
  function drawDetailKeep() {
    detailBox.querySelectorAll('button, textarea').forEach(function (el) { el.disabled = true; });
  }

  /* ---------- 전체 강의 (API 89·93·94) ---------- */

  var listBox = root.querySelector('[data-ao-list]');
  var statusSelect = root.querySelector('[data-ao-status]');
  statusSelect.addEventListener('change', function () { st.list.status = statusSelect.value; loadList(true); });

  function loadList(reset) {
    var L = st.list;
    if (reset) { L.items = []; L.total = 0; fill(listBox, h('p', { class: 'adm-empty', text: '불러오고 있어요…' })); }
    var page = Math.floor(L.items.length / PAGE_SIZE);
    var status = L.status;
    return MB.api(API + '?' + (status ? 'status=' + status + '&' : '') + 'page=' + page + '&size=' + PAGE_SIZE).then(function (res) {
      if (status !== L.status) return;                     // 그사이 상태 필터를 바꿨으면 버린다
      var seen = {};
      L.items.forEach(function (c) { seen[c.courseId] = true; });
      L.items = L.items.concat(((res && res.items) || []).filter(function (c) { return !seen[c.courseId]; }));
      L.total = (res && typeof res.totalElements === 'number') ? res.totalElements : L.items.length;
      drawList();
    }, function (err) {
      if (status !== L.status) return;
      if (reset) fill(listBox, stateBox(errText(err, '강의 목록을 불러오지 못했어요.'), function () { loadList(true); }));
      else {
        MB.toast(errText(err, '강의 목록을 더 불러오지 못했어요.'), 'error');
        drawList();                                        // 잠긴 "더 보기" 버튼을 다시 켠다
      }
    });
  }

  function drawList() {
    var L = st.list;
    if (!L.items.length) {
      fill(listBox, h('div', { class: 'ao-table' }, [h('p', { class: 'ao-state', text: '해당하는 강의가 없어요.' })]));
      return;
    }
    var more = L.items.length < L.total ? h('button', { type: 'button', class: 'btn btn-secondary ao-more', text: '더 보기',
      onclick: function (e) { e.currentTarget.disabled = true; loadList(false); } }) : null;
    fill(listBox, [
      h('div', { class: 'ao-table ao-table--courses', role: 'table', 'aria-label': '강의 ' + L.total + '개' }, [
        h('div', { class: 'ao-tr ao-tr--head', role: 'row' }, ['강의', '강사', '상태', '검수 요청', '학습자', '공개', '관리'].map(function (t, i) {
          return h('span', { role: 'columnheader', style: i === 6 ? 'text-align:right' : null, text: t });
        }))
      ].concat(L.items.map(courseRow))),
      more
    ]);
  }

  function courseRow(c) {
    var busy = !!st.list.busy[c.courseId];
    var approved = c.status === 'APPROVED';
    var sw = h('button', { type: 'button', class: 'ao-switch', role: 'switch', 'aria-checked': String(!!c.isPublic),
      'aria-label': (c.title || '강의') + ' 공개', disabled: !approved || busy,
      title: approved ? null : '허가된 강의만 공개할 수 있어요',
      onclick: function () { toggleVisibility(c); } }, [h('span', { class: 'ao-switch__track', 'aria-hidden': 'true' }), c.isPublic ? '공개' : '비공개']);
    return h('div', { class: 'ao-tr', role: 'row' }, [
      h('div', { role: 'cell' }, [
        h('div', { class: 'ao-td-title', text: c.title || '(제목 없음)' }),
        approved && c.isPublic ? h('a', { class: 'ao-td-sub', href: '/user/lecture-detail?courseId=' + encodeURIComponent(c.courseId), target: '_blank', rel: 'noopener', text: '수강생 화면 보기' }) : null
      ]),
      h('span', { role: 'cell' }, [h('span', { class: 'ao-label', text: '강사' }), c.instructorName || '-']),
      h('span', { role: 'cell' }, [badge(c.status)]),
      h('span', { role: 'cell' }, [h('span', { class: 'ao-label', text: '검수 요청' }), day(c.submittedAt)]),
      h('span', { role: 'cell' }, [h('span', { class: 'ao-label', text: '학습자' }), (c.learners || 0).toLocaleString('ko-KR') + '명']),
      h('span', { role: 'cell' }, [sw]),
      h('div', { class: 'ao-td-actions', role: 'cell' }, [
        c.status === 'REVIEW' ? h('button', { type: 'button', class: 'btn btn-secondary adm-btn-sm', text: '검수하기',
          onclick: function () { showTab('review'); pick(c.courseId); } }) : null,
        h('button', { type: 'button', class: 'btn btn-ghost adm-btn-sm adm-btn-danger', text: busy ? '처리 중…' : '삭제', disabled: busy,
          onclick: function () { deleteCourse(c); } })
      ])
    ]);
  }

  function setBusy(id, on) {
    if (on) st.list.busy[id] = true; else delete st.list.busy[id];
    drawList();
  }

  function toggleVisibility(c) {
    if (st.list.busy[c.courseId]) return;
    var next = !c.isPublic;
    var go = next ? Promise.resolve(true)
      : MBA.confirm('이 강의를 비공개로 바꿀까요?', '수강생 목록·검색에서 사라져요. 학습 기록은 그대로 남아요.', '비공개로');
    go.then(function (ok) {
      if (!ok) return;
      setBusy(c.courseId, true);
      MB.api(API + '/' + c.courseId + '/visibility', { method: 'PATCH', body: { isPublic: next } }).then(function () {
        c.isPublic = next;
        MB.toast(next ? '공개했어요.' : '비공개로 바꿨어요.');
      }, function (err) {
        MB.toast(errText(err, '공개 상태를 바꾸지 못했어요.'), 'error');
      }).then(function () { setBusy(c.courseId, false); });
    });
  }

  function deleteCourse(c) {
    if (st.list.busy[c.courseId]) return;
    MBA.confirm('"' + (c.title || '강의') + '"를 삭제할까요?',
      '삭제하면 되돌릴 수 없어요. 시청·학습 기록이 있는 강의는 삭제되지 않아요.', '삭제').then(function (ok) {
      if (!ok) return;
      setBusy(c.courseId, true);
      MB.api(API + '/' + c.courseId, { method: 'DELETE' }).then(function () {
        st.list.items = st.list.items.filter(function (x) { return x.courseId !== c.courseId; });
        st.list.total = Math.max(0, st.list.total - 1);
        st.queue = st.queue.filter(function (x) { return x.courseId !== c.courseId; });
        if (st.loaded.review) drawQueue();
        MB.toast('강의를 삭제했어요.');
      }, function (err) {
        MB.toast(errText(err, '강의를 삭제하지 못했어요.'), 'error');
      }).then(function () { setBusy(c.courseId, false); });
    });
  }

  /* ---------- 카테고리 (API 85~88) ---------- */

  var catsBox = root.querySelector('[data-ao-cats]');
  var newName = root.querySelector('[data-ao-new-name]');
  var newOrder = root.querySelector('[data-ao-new-order]');
  var newSave = root.querySelector('[data-ao-new-save]');
  var newError = root.querySelector('[data-ao-new-error]');

  function loadCats() {
    fill(catsBox, h('p', { class: 'adm-empty', text: '불러오고 있어요…' }));
    return MB.api(CAT_API).then(function (tree) {
      st.cats = tree || [];
      setPaths(st.cats);
      drawCats();
    }, function (err) {
      fill(catsBox, stateBox(errText(err, '카테고리를 불러오지 못했어요.'), loadCats));
    });
  }

  function orderValue(input) {
    var raw = String(input.value).trim();
    if (raw === '') return null;
    var n = Number(raw);
    return Number.isInteger(n) && n >= 0 ? n : NaN;
  }

  /* 이름·순서 입력 확인. 잘못되면 그 칸을 표시하고 문구를 돌려준다 */
  function validate(nameInput, orderInput) {
    var name = nameInput.value.trim();
    var order = orderValue(orderInput);
    nameInput.removeAttribute('aria-invalid');
    orderInput.removeAttribute('aria-invalid');
    if (!name) { nameInput.setAttribute('aria-invalid', 'true'); return { error: '이름을 입력해 주세요.' }; }
    if (name.length > 30) { nameInput.setAttribute('aria-invalid', 'true'); return { error: '이름은 30자까지 쓸 수 있어요.' }; }
    if (order === null || isNaN(order)) { orderInput.setAttribute('aria-invalid', 'true'); return { error: '순서는 0 이상의 정수로 입력해 주세요.' }; }
    return { name: name, order: order };
  }

  function catRow(c, isSub, errorEl) {
    var name = h('input', { class: 'adm-input', type: 'text', maxlength: '30', value: c.name, 'aria-label': (isSub ? '소주제' : '카테고리') + ' 이름' });
    var order = h('input', { class: 'adm-input', type: 'number', min: '0', step: '1', value: String(c.sortOrder), 'aria-label': c.name + ' 순서' });
    var active = h('input', { type: 'checkbox' });
    active.checked = !!c.isActive;
    var save = h('button', { type: 'button', class: 'btn btn-secondary adm-btn-sm', text: '저장' });
    var del = h('button', { type: 'button', class: 'btn btn-ghost adm-btn-sm adm-btn-danger', text: '삭제' });

    save.addEventListener('click', function () {
      var v = validate(name, order);
      if (v.error) { errorEl.textContent = v.error; return; }
      errorEl.textContent = '';
      save.disabled = true;
      MB.api(CAT_API + '/' + c.categoryId, { method: 'PUT', body: { name: v.name, sortOrder: v.order, isActive: active.checked } }).then(function () {
        MB.toast('"' + v.name + '"을(를) 저장했어요.');
        return loadCats();
      }, function (err) {
        errorEl.textContent = errText(err, '저장하지 못했어요.');
        save.disabled = false;
      });
    });

    del.addEventListener('click', function () {
      var linked = (c.courseCount || 0) > 0 || ((c.children || []).length > 0);
      MBA.confirm('"' + c.name + '"을(를) 삭제할까요?', linked
        ? '연결된 강의나 소주제가 있어서 지워지지 않고 \'사용 안 함\'으로 바뀌어요. 사용자 메뉴에서 보이지 않게 돼요.'
        : '연결된 강의가 없어 바로 지워져요. 되돌릴 수 없어요.', linked ? '사용 안 함으로' : '삭제').then(function (ok) {
        if (!ok) return;
        del.disabled = true;
        MB.api(CAT_API + '/' + c.categoryId, { method: 'DELETE' }).then(function () {
          MB.toast(linked ? '"' + c.name + '"을(를) 사용 안 함으로 바꿨어요.' : '"' + c.name + '"을(를) 삭제했어요.');
          return loadCats();
        }, function (err) {
          errorEl.textContent = errText(err, '삭제하지 못했어요.');
          del.disabled = false;
        });
      });
    });

    return h('div', { class: 'ao-cat-row' + (isSub ? ' ao-cat-row--sub' : '') + (c.isActive ? '' : ' is-off') }, [
      name, order,
      h('label', { class: 'adm-check' }, [active, '사용']),
      save, del
    ]);
  }

  function drawCats() {
    if (!st.cats.length) {
      fill(catsBox, h('p', { class: 'adm-empty', text: '아직 카테고리가 없어요. 아래에서 추가해 주세요.' }));
      return;
    }
    fill(catsBox, st.cats.map(function (top) {
      var error = h('span', { class: 'adm-error', role: 'alert' });
      var subs = top.children || [];
      var subName = h('input', { class: 'adm-input', type: 'text', maxlength: '30', placeholder: '소주제 이름', 'aria-label': top.name + '에 추가할 소주제 이름' });
      var subOrder = h('input', { class: 'adm-input', type: 'number', min: '0', step: '1', placeholder: '순서', 'aria-label': '소주제 순서',
        value: String(subs.reduce(function (m, s) { return Math.max(m, s.sortOrder); }, 0) + 1) });
      var subAdd = h('button', { type: 'button', class: 'btn btn-secondary', text: '+ 소주제 추가' });
      subAdd.addEventListener('click', function () { addCategory(top.categoryId, subName, subOrder, error, subAdd); });
      var courseTotal = (top.courseCount || 0) + subs.reduce(function (sum, s) { return sum + (s.courseCount || 0); }, 0);
      return h('div', { class: 'adm-box ao-cat' }, [
        h('div', { class: 'adm-box__head' }, [h('h2', { text: top.name }), top.isActive ? null : h('span', { class: 'ao-badge ao-badge--off', text: '사용 안 함' })]),
        catRow(top, false, error),
        h('span', { class: 'adm-note', text: '소주제 ' + subs.length + '개 · 강의 ' + courseTotal + '개' }),
        subs.length ? h('div', { class: 'ao-sec' }, subs.map(function (s) {
          return h('div', { class: 'ao-sec' }, [
            catRow(s, true, error),
            h('span', { class: 'ao-td-sub', style: 'padding-left:17px', text: '강의 ' + (s.courseCount || 0) + '개' + (s.isActive ? '' : ' · 사용 안 함') })
          ]);
        })) : null,
        h('div', { class: 'ao-add' }, [subName, subOrder, subAdd]),
        error
      ]);
    }));
    newOrder.value = String(st.cats.reduce(function (m, c) { return Math.max(m, c.sortOrder); }, 0) + 1);
  }

  function addCategory(parentId, nameInput, orderInput, errorEl, button) {
    var v = validate(nameInput, orderInput);
    if (v.error) { errorEl.textContent = v.error; return; }
    errorEl.textContent = '';
    button.disabled = true;
    var body = { name: v.name, sortOrder: v.order };
    if (parentId) body.parentId = parentId;
    MB.api(CAT_API, { method: 'POST', body: body }).then(function () {
      MB.toast('"' + v.name + '"을(를) 추가했어요.');
      nameInput.value = '';
      return loadCats();
    }, function (err) {
      errorEl.textContent = errText(err, '추가하지 못했어요.');
    }).then(function () { button.disabled = false; });
  }

  newSave.addEventListener('click', function () { addCategory(null, newName, newOrder, newError, newSave); });
  newName.addEventListener('keydown', function (e) {
    if (e.key === 'Enter' && !e.isComposing) newSave.click();   // 한글 조합 중 Enter 는 무시 (마지막 글자가 빠지지 않게)
  });

  /* ---------- 시작 ---------- */
  showTab(new URLSearchParams(location.search).get('tab') || 'review', true);
})();
