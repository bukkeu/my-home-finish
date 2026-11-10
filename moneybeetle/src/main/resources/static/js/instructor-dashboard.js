/* comment.
*   담당 : DBA ① 신아림 (#59, 강사 대시보드 /instructor/dashboard?tab=)
*   탭(사이드바 강사 메뉴와 같은 주소)
*   - dashboard : 요약 숫자(전체 강의·검수 중·공개 중·답변 대기 문의), 바로가기, 최근 강의, 최근 문의
*   - courses   : 내 강의 목록·검수 상태(API 72). 상태별 거르기, 검수 요청(API 81), 삭제(API 76), 수정은 강의 만들기 화면(#60)
*   - qna       : 내 강의 문의(API 82) 목록·펼치기·답변 (inquiry-board.js 의 window.MBInquiry, 강사 모드)
*   - mypage    : 내 강사 정보 요약 (API 13)
*   본인 강의와 그 강의의 문의만 보인다 (서버가 로그인한 강사 기준으로 거른다, TCH-06~08)
*  */
(function () {
  var root = document.querySelector('[data-ins-dashboard]');
  if (!root || !window.MB) return;

  var TAB = root.getAttribute('data-tab') || 'dashboard';
  var STATUS = {
    DRAFT: { text: '작성 중', cls: 'ins-badge--draft' },
    REVIEW: { text: '검수 중', cls: 'ins-badge--review' },
    APPROVED: { text: '승인', cls: 'ins-badge--ok' },
    REJECTED: { text: '반려', cls: 'ins-badge--rejected' }
  };
  var COURSE_TABS = [['all', '전체'], ['DRAFT', '작성 중'], ['REVIEW', '검수 중'], ['APPROVED', '승인'], ['REJECTED', '반려']];
  var TONES = ['#E3F3D2', '#DCEFF0', '#EEF0DA', '#E6EBF6', '#F2EAD9'];
  var CREATE_URL = '/instructor/course-create';

  var st = { courses: null, inbox: null, courseTab: 'all', error: null };
  var params = new URLSearchParams(location.search);

  function h(tag, attrs, children) {
    var el = document.createElement(tag);
    Object.keys(attrs || {}).forEach(function (key) {
      var v = attrs[key];
      if (v === null || v === undefined || v === false) return;
      if (key === 'text') el.textContent = v;
      else if (key === 'class') el.className = v;
      else if (key === 'style') el.setAttribute('style', v);
      else if (key.indexOf('on') === 0 && typeof v === 'function') el.addEventListener(key.slice(2), v);
      else el.setAttribute(key, v === true ? '' : v);
    });
    (children || []).forEach(function (c) {
      if (c === null || c === undefined || c === false) return;
      el.appendChild(typeof c === 'string' ? document.createTextNode(c) : c);
    });
    return el;
  }

  function fill(el) {
    var nodes = [];
    [].slice.call(arguments, 1).forEach(function add(x) {
      if (Array.isArray(x)) x.forEach(add);
      else if (x !== null && x !== undefined && x !== false) nodes.push(x);
    });
    el.replaceChildren.apply(el, nodes);
  }

  function errorText(err, fallback) { return (err && err.message) || fallback; }

  function date(value) {
    var m = String(value || '').match(/^(\d{4})-(\d{2})-(\d{2})/);
    return m ? m[1] + '.' + m[2] + '.' + m[3] : '';
  }

  function isPublic(c) { return c.isPublic === true || c['public'] === true; }

  function badge(status) {
    var b = STATUS[status] || { text: status, cls: '' };
    return h('span', { class: 'ins-badge ' + b.cls, text: b.text });
  }

  function thumbStyle(c, i) {
    var tone = TONES[i % TONES.length];
    if (c.thumbnailUrl && /^\/uploads\//.test(c.thumbnailUrl)) {
      return 'background-image:url("' + c.thumbnailUrl.replace(/"/g, '') + '");background-color:' + tone;
    }
    return 'background-color:' + tone;
  }

  function head(title, desc, action) {
    return h('div', { class: 'ins-head' }, [
      h('div', {}, [h('span', { class: 'ins-kicker', text: '강사 센터' }), h('h1', { text: title }), desc ? h('p', { class: 'ins-desc', text: desc }) : null]),
      action || null
    ]);
  }

  function createButton() {
    return h('a', { class: 'btn btn-primary', href: CREATE_URL, text: '+ 강의 만들기' });
  }

  function errorBox(err, retry) {
    return h('div', { class: 'ins-state ins-state--error', role: 'alert' }, [
      errorText(err, '불러오지 못했어요.'), h('br'),
      h('button', { type: 'button', class: 'btn btn-secondary', text: '다시 시도', onclick: retry })
    ]);
  }

  /* ---------- 불러오기 ---------- */

  function loadCourses() {
    return MB.api('/api/v1/instructor/courses').then(function (res) { st.courses = (res && res.items) || []; });
  }

  function loadInbox() {
    return MB.api('/api/v1/instructor/inquiries?page=0&size=5').then(function (res) { st.inbox = res || { waitingCount: 0, items: [] }; });
  }

  function start() {
    fill(root, h('div', { class: 'ins-state', text: '강사 센터를 불러오는 중이에요…' }));
    if (TAB === 'qna') { drawQna(); return; }
    if (TAB === 'mypage') { drawMypage(); return; }
    var jobs = TAB === 'dashboard' ? [loadCourses(), loadInbox()] : [loadCourses()];
    Promise.all(jobs).then(function () {
      if (TAB === 'courses') drawCourses(); else drawDashboard();
    }).catch(function (err) { fill(root, errorBox(err, start)); });
  }

  /* ---------- 대시보드 ---------- */

  function drawDashboard() {
    var cs = st.courses;
    var stats = [
      { label: '전체 강의', value: cs.length, href: '?tab=courses' },
      { label: '공개 중', value: cs.filter(function (c) { return c.status === 'APPROVED' && isPublic(c); }).length, href: '?tab=courses' },
      { label: '검수 중', value: cs.filter(function (c) { return c.status === 'REVIEW'; }).length, href: '?tab=courses&status=REVIEW' },
      { label: '답변 대기 문의', value: st.inbox.waitingCount || 0, href: '?tab=qna', warn: (st.inbox.waitingCount || 0) > 0 }
    ];
    var rejected = cs.filter(function (c) { return c.status === 'REJECTED'; });

    fill(root,
      head('대시보드', '내 강의와 수강생 문의를 한눈에 확인해요.', createButton()),
      rejected.length ? h('a', { class: 'ins-alert', href: '?tab=courses&status=REJECTED' }, [
        h('b', { text: '반려된 강의가 ' + rejected.length + '개 있어요.' }), ' 반려 사유를 확인하고 고쳐서 다시 검수를 요청해 주세요 ›'
      ]) : null,
      h('section', { class: 'ins-stats', 'aria-label': '요약' }, stats.map(function (s) {
        return h('a', { class: 'ins-stat' + (s.warn ? ' is-warn' : ''), href: s.href }, [
          h('span', { class: 'ins-stat__label', text: s.label }), h('b', { class: 'ins-stat__value', text: String(s.value) })
        ]);
      })),
      h('div', { class: 'ins-grid' }, [
        h('section', { class: 'ins-panel', 'aria-labelledby': 'ins-recent-courses' }, [
          h('div', { class: 'ins-panel__head' }, [h('h2', { id: 'ins-recent-courses', text: '최근 강의' }), h('a', { href: '?tab=courses', text: '전체 보기' })]),
          cs.length ? h('div', { class: 'ins-mini' }, cs.slice(0, 5).map(function (c, i) {
            return h('a', { class: 'ins-mini__row', href: courseHref(c) }, [
              h('span', { class: 'ins-thumb ins-thumb--sm', style: thumbStyle(c, i), 'aria-hidden': 'true' }),
              h('span', { class: 'ins-mini__text' }, [h('b', { text: c.title }), h('small', { text: '수강생 ' + c.learners + '명 · 답변 대기 ' + c.waitingInquiries + '개' })]),
              badge(c.status)
            ]);
          })) : emptyCourses()
        ]),
        h('section', { class: 'ins-panel', 'aria-labelledby': 'ins-recent-qna' }, [
          h('div', { class: 'ins-panel__head' }, [h('h2', { id: 'ins-recent-qna', text: '최근 문의' }), h('a', { href: '?tab=qna', text: '전체 보기' })]),
          (st.inbox.items || []).length ? h('div', { class: 'ins-mini' }, st.inbox.items.map(function (q) {
            return h('a', { class: 'ins-mini__row', href: '?tab=qna&inquiryId=' + q.inquiryId }, [
              h('span', { class: 'ins-mini__text' }, [h('b', { text: q.title }), h('small', { text: [q.courseTitle, date(q.createdAt)].filter(Boolean).join(' · ') })]),
              h('span', { class: 'ins-badge ' + (q.status === 'WAITING' ? 'ins-badge--review' : 'ins-badge--ok'), text: q.status === 'WAITING' ? '답변 대기' : '답변 완료' })
            ]);
          })) : h('div', { class: 'ins-state', text: '아직 내 강의에 달린 문의가 없어요.' })
        ])
      ])
    );
  }

  function emptyCourses() {
    return h('div', { class: 'ins-state' }, ['아직 만든 강의가 없어요.', h('br'), h('a', { class: 'btn btn-primary', href: CREATE_URL, text: '첫 강의 만들기' })]);
  }

  /* 승인되고 공개된 강의는 수강생 화면으로, 그 외는 편집 화면(#60)으로 */
  function courseHref(c) {
    return c.status === 'APPROVED' && isPublic(c) ? '/user/lecture-detail?courseId=' + c.courseId : CREATE_URL + '?courseId=' + c.courseId;
  }

  /* ---------- 강의 관리 ---------- */

  function drawCourses() {
    var wanted = params.get('status');
    if (wanted && STATUS[wanted]) { st.courseTab = wanted; params.delete('status'); }
    var cs = st.courses;
    var shown = st.courseTab === 'all' ? cs : cs.filter(function (c) { return c.status === st.courseTab; });

    var list = h('div', { class: 'ins-courses', role: 'tabpanel', id: 'ins-course-list', 'aria-live': 'polite' },
      !cs.length ? [emptyCourses()]
        : !shown.length ? [h('div', { class: 'ins-state', text: '이 상태의 강의가 없어요.' })]
          : shown.map(function (c) { return courseRow(c, cs.indexOf(c)); }));

    fill(root,
      head('강의 관리', '검수 상태를 확인하고, 작성 중·반려된 강의는 고쳐서 검수를 요청해요.', createButton()),
      h('div', { class: 'ins-tabs', role: 'tablist', 'aria-label': '검수 상태' }, COURSE_TABS.map(function (t) {
        var n = t[0] === 'all' ? cs.length : cs.filter(function (c) { return c.status === t[0]; }).length;
        return h('button', { type: 'button', role: 'tab', class: 'ins-tab', id: 'ins-ctab-' + t[0], 'aria-controls': 'ins-course-list',
          'aria-selected': String(st.courseTab === t[0]),
          onclick: function () { st.courseTab = t[0]; drawCourses(); var b = document.getElementById('ins-ctab-' + t[0]); if (b) b.focus(); } },
          [t[1], h('span', { class: 'ins-tab__n', text: String(n) })]);
      })),
      list
    );
  }

  function courseRow(c, i) {
    var canRequest = c.status === 'DRAFT' || c.status === 'REJECTED';
    var actions = h('div', { class: 'ins-row__actions' });
    var msg = h('p', { class: 'ins-error', role: 'alert' });

    function idle() {
      fill(actions,
        c.status === 'APPROVED' && isPublic(c) ? h('a', { class: 'btn btn-ghost', href: '/user/lecture-detail?courseId=' + c.courseId, text: '강의 보기' }) : null,
        c.status !== 'REVIEW' ? h('a', { class: 'btn btn-secondary', href: CREATE_URL + '?courseId=' + c.courseId, text: '수정' }) : null,
        canRequest ? h('button', { type: 'button', class: 'btn btn-primary', text: '검수 요청', onclick: requestReview }) : null,
        c.status !== 'REVIEW' ? h('button', { type: 'button', class: 'btn btn-ghost ins-danger', text: '삭제', onclick: confirmDelete }) : null
      );
    }

    function requestReview(e) {
      var btn = e.currentTarget;
      btn.disabled = true;
      msg.textContent = '';
      MB.api('/api/v1/instructor/courses/' + c.courseId + '/review-request', { method: 'POST' }).then(function () {
        MB.toast('검수를 요청했어요. 관리자가 확인하면 알려 드려요.');
        return loadCourses().then(drawCourses);
      }).catch(function (err) {
        msg.textContent = errorText(err, '검수를 요청하지 못했어요.');
        btn.disabled = false;
      });
    }

    function confirmDelete() {
      var yes = h('button', { type: 'button', class: 'btn btn-secondary ins-danger', text: '삭제할게요' });
      yes.addEventListener('click', function () {
        yes.disabled = true;
        MB.api('/api/v1/instructor/courses/' + c.courseId, { method: 'DELETE' }).then(function () {
          MB.toast('강의를 삭제했어요.');
          st.courses = st.courses.filter(function (x) { return x.courseId !== c.courseId; });
          drawCourses();
        }).catch(function (err) {
          msg.textContent = errorText(err, '삭제하지 못했어요.');   // 학습 기록이 있으면 COURSE_HAS_LEARNERS
          idle();
        });
      });
      fill(actions, h('span', { class: 'ins-meta', text: '삭제하면 되돌릴 수 없어요. 삭제할까요?' }),
        h('button', { type: 'button', class: 'btn btn-ghost', text: '취소', onclick: idle }), yes);
    }

    idle();
    return h('article', { class: 'ins-row' }, [
      h('span', { class: 'ins-thumb', style: thumbStyle(c, i), 'aria-hidden': 'true' }),
      h('div', { class: 'ins-row__main' }, [
        h('div', { class: 'ins-row__title' }, [badge(c.status), h('b', { text: c.title })]),
        h('span', { class: 'ins-meta', text: [
          c.status === 'APPROVED' ? (isPublic(c) ? '공개 중' : '비공개') : null,
          '수강생 ' + c.learners + '명',
          '답변 대기 문의 ' + c.waitingInquiries + '개'
        ].filter(Boolean).join(' · ') }),
        c.status === 'REJECTED' && c.rejectReason ? h('p', { class: 'ins-reject' }, [h('b', { text: '반려 사유 ' }), c.rejectReason]) : null,
        c.status === 'REVIEW' ? h('p', { class: 'ins-meta', text: '관리자가 검수하고 있어요. 검수 중에는 고칠 수 없어요.' }) : null,
        msg
      ]),
      actions
    ]);
  }

  /* ---------- Q&A 관리 ---------- */

  function drawQna() {
    var board = h('div', { class: 'inq-board' });
    fill(root, head('Q&A 관리', '내 강의에 달린 수강생 문의예요.'), board);
    if (!window.MBInquiry) { fill(board, h('div', { class: 'ins-state', text: '문의 화면을 불러오지 못했어요.' })); return; }
    var openId = Number(params.get('inquiryId')) || null;
    window.MBInquiry.mount(board, { mode: 'instructor', openId: openId });
  }

  /* ---------- 마이페이지 ---------- */

  function drawMypage() {
    MB.api('/api/v1/members/me').then(function (me) {
      fill(root,
        head('마이페이지', '강사 계정 정보예요.'),
        h('section', { class: 'ins-panel ins-me' }, [
          h('span', { class: 'ins-me__avatar', 'aria-hidden': 'true', text: (me.nickname || '?').charAt(0) }),
          h('div', {}, [
            h('b', { class: 'ins-me__name', text: me.nickname + ' 강사' }),
            h('p', { class: 'ins-meta', text: me.email || '' })
          ])
        ]),
        h('p', { class: 'ins-meta', text: '비밀번호 변경과 탈퇴는 마이페이지 설정 화면에서 할 수 있어요.' })
      );
    }).catch(function (err) { fill(root, errorBox(err, drawMypage)); });
  }

  start();
})();
