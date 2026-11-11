/* comment.
*   담당 : DBA ① 신아림 (#253, 관리자 회원·강사 탭 #37 · 문의 게시판 탭 #38)
*   조각 : templates/fragments/admin/members.html (틀 admin/dashboard.html?view=members|board, #61)
*   회원·강사 (?view=members&tab=users|applications)
*   - 사용자·강사 목록 : GET /api/v1/admin/members?role=&status=&keyword= (API 101, 관리자 계정은 서버가 뺀다)
*   - 계정 정지·해제 : PATCH .../{memberId}/status { status, reason } (API 102, 정지 사유 필수 200자)
*   - 강사 권한 부여·회수 : PATCH .../{memberId}/role { role } (API 103)
*   - 강사 가입 신청 : GET /api/v1/admin/instructor-applications?status= (API 104), 승인 POST .../{memberId}/approve (API 105)
*   문의 게시판 (?view=board)
*   - 목록 : GET /api/v1/admin/inquiries?status=&hidden=&deleted= (API 106, 숨긴 글도 원문 제목)
*   - 내용·답글 : GET /api/v1/inquiries/{id} (API 49), 답글 POST .../{id}/replies (API 52, 관리자 답글이면 답변 완료)
*   - 숨김·해제 : PATCH /api/v1/admin/inquiries/{id}/hidden { hidden } (API 107)
*   오류는 서버 문구를 그대로 보여 준다. 되돌리기 어려운 작업은 확인 창(MBA.confirm)을 거친다
*  */
(function () {
  'use strict';

  if (!window.MB || !window.MBA) return;
  var PAGE_SIZE = 20;

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

  function badge(text, kind) { return h('span', { class: 'ao-badge ao-badge--' + kind, text: text }); }

  function stateBox(text, retry) {
    return h('div', { class: 'ao-state', role: retry ? 'alert' : null }, [
      h('span', { text: text }),
      retry ? h('button', { type: 'button', class: 'btn btn-secondary', text: '다시 불러오기', onclick: retry }) : null
    ]);
  }

  /* 쪽 단위 목록. 화면에서 항목이 빠져도(필터에 안 맞게 바뀜) 다음 쪽 경계가 어긋나지 않게
   * 다음 쪽은 지금 개수로 정하고, 겹쳐 받은 항목은 keyOf 로 거른다 */
  function pager(urlOf, keyOf) {
    var p = { items: [], total: 0, seq: 0 };
    p.reset = function () { p.items = []; p.total = 0; p.seq++; };
    p.next = function () {
      var seq = p.seq;
      var page = Math.floor(p.items.length / PAGE_SIZE);
      var url = urlOf();
      return MB.api(url + (url.indexOf('?') < 0 ? '?' : '&') + 'page=' + page + '&size=' + PAGE_SIZE).then(function (res) {
        if (seq !== p.seq) return false;                 // 그사이 필터를 바꿨으면 버린다
        var seen = {};
        p.items.forEach(function (it) { seen[keyOf(it)] = true; });
        p.items = p.items.concat(((res && res.items) || []).filter(function (it) { return !seen[keyOf(it)]; }));
        p.total = (res && typeof res.totalElements === 'number') ? res.totalElements : p.items.length;
        return true;
      });
    };
    p.hasMore = function () { return p.items.length < p.total; };
    return p;
  }

  function moreButton(p, redraw) {
    if (!p.hasMore()) return null;
    var btn = h('button', { type: 'button', class: 'btn btn-secondary ao-more', text: '더 보기' });
    btn.addEventListener('click', function () {
      btn.disabled = true;
      btn.textContent = '불러오는 중…';
      p.next().then(function (fresh) { if (fresh) redraw(); }, function (err) {
        MB.toast(errText(err, '목록을 더 불러오지 못했어요.'), 'error');
        btn.disabled = false;
        btn.textContent = '더 보기';
      });
    });
    return btn;
  }

  function query(params) {
    return Object.keys(params).filter(function (k) { return params[k] !== '' && params[k] !== null && params[k] !== undefined; })
      .map(function (k) { return encodeURIComponent(k) + '=' + encodeURIComponent(params[k]); }).join('&');
  }

  function setUrlTab(view, tab) {
    if (!history.replaceState) return;
    var q = new URLSearchParams(location.search);
    q.set('view', view);
    if (tab) q.set('tab', tab); else q.delete('tab');
    history.replaceState(null, '', location.pathname + '?' + q.toString());
  }

  /* =================== 회원·강사 =================== */

  var members = document.querySelector('[data-ao-members]');
  if (members) initMembers(members);

  function initMembers(root) {
    var ROLE = { MEMBER: '학습자', INSTRUCTOR: '강사', ADMIN: '관리자' };
    var STATUS = { ACTIVE: ['정상', 'ok'], SUSPENDED: ['정지', 'bad'], WITHDRAWN: ['탈퇴', 'off'] };
    var loaded = {};
    var filter = { role: '', status: '', keyword: '' };
    var users = pager(function () { return '/api/v1/admin/members?' + query(filter); }, function (u) { return u.memberId; });
    var busy = {};
    var suspending = null;                                 // 정지 사유를 입력 중인 회원 ID
    var usersBox = root.querySelector('[data-ao-users]');
    var form = root.querySelector('[data-ao-user-filter]');

    function showTab(tab, keepUrl) {
      if (tab !== 'applications') tab = 'users';
      root.querySelectorAll('[data-ao-tab]').forEach(function (b) { b.setAttribute('aria-pressed', String(b.dataset.aoTab === tab)); });
      root.querySelectorAll('[data-ao-pane]').forEach(function (p) { p.hidden = p.dataset.aoPane !== tab; });
      if (!keepUrl) setUrlTab('members', tab);
      if (!loaded[tab]) {
        loaded[tab] = true;
        if (tab === 'users') loadUsers(); else loadApps();
      }
    }
    root.querySelectorAll('[data-ao-tab]').forEach(function (b) {
      b.addEventListener('click', function () { showTab(b.dataset.aoTab); });
    });

    form.addEventListener('submit', function (e) {
      e.preventDefault();
      filter.role = form.role.value;
      filter.status = form.status.value;
      filter.keyword = form.keyword.value.trim();
      loadUsers();
    });
    form.role.addEventListener('change', function () { form.requestSubmit ? form.requestSubmit() : form.dispatchEvent(new Event('submit')); });
    form.status.addEventListener('change', function () { form.requestSubmit ? form.requestSubmit() : form.dispatchEvent(new Event('submit')); });

    function loadUsers() {
      users.reset();
      suspending = null;
      fill(usersBox, h('p', { class: 'adm-empty', text: '불러오고 있어요…' }));
      users.next().then(function (fresh) { if (fresh) drawUsers(); }, function (err) {
        fill(usersBox, stateBox(errText(err, '사용자 목록을 불러오지 못했어요.'), loadUsers));
      });
    }

    function drawUsers() {
      if (!users.items.length) {
        fill(usersBox, h('div', { class: 'ao-table' }, [h('p', { class: 'ao-state', text: '해당하는 사용자가 없어요.' })]));
        return;
      }
      fill(usersBox, [
        h('div', { class: 'ao-table ao-table--users', role: 'table', 'aria-label': '사용자 ' + users.total + '명' }, [
          h('div', { class: 'ao-tr ao-tr--head', role: 'row' }, ['닉네임', '이메일', '레벨', '권한', '상태', '캐시', '관리'].map(function (t, i) {
            return h('span', { role: 'columnheader', style: i === 6 ? 'text-align:right' : null, text: t });
          }))
        ].concat(users.items.map(userRow))),
        moreButton(users, drawUsers)
      ]);
    }

    function userRow(u) {
      var st = STATUS[u.status] || [u.status, 'off'];
      var withdrawn = u.status === 'WITHDRAWN';
      var isBusy = !!busy[u.memberId];
      var actions = withdrawn ? [h('span', { class: 'ao-td-sub', text: '탈퇴한 계정은 바꿀 수 없어요' })] : [
        u.status === 'SUSPENDED'
          ? h('button', { type: 'button', class: 'btn btn-secondary adm-btn-sm', text: '정지 해제', disabled: isBusy, onclick: function () { activate(u); } })
          : h('button', { type: 'button', class: 'btn btn-ghost adm-btn-sm adm-btn-danger', text: '정지', disabled: isBusy,
            'aria-expanded': String(suspending === u.memberId), onclick: function () { suspending = suspending === u.memberId ? null : u.memberId; drawUsers(); } }),
        u.role === 'INSTRUCTOR'
          ? h('button', { type: 'button', class: 'btn btn-ghost adm-btn-sm', text: '강사 회수', disabled: isBusy, onclick: function () { changeRole(u, 'MEMBER'); } })
          : h('button', { type: 'button', class: 'btn btn-ghost adm-btn-sm', text: '강사 부여', disabled: isBusy, onclick: function () { changeRole(u, 'INSTRUCTOR'); } })
      ];
      return h('div', { class: 'ao-tr', role: 'row' }, [
        h('strong', { class: 'ao-td-title', role: 'cell', text: u.nickname || '-' }),
        h('span', { class: 'ao-td-sub', role: 'cell', text: u.email || '-' }),
        h('span', { role: 'cell' }, [h('span', { class: 'ao-label', text: '레벨' }), 'Lv.' + (u.level || 1)]),
        h('span', { role: 'cell' }, [badge(ROLE[u.role] || u.role, u.role === 'INSTRUCTOR' ? 'ok' : 'line')]),
        h('span', { role: 'cell' }, [badge(st[0], st[1])]),
        h('span', { role: 'cell' }, [h('span', { class: 'ao-label', text: '캐시' }), Number(u.cash || 0).toLocaleString('ko-KR')]),
        h('div', { class: 'ao-td-actions', role: 'cell' }, actions),
        suspending === u.memberId ? suspendForm(u) : null
      ]);
    }

    /* 정지 사유 입력 (줄 아래에 펼친다. 사유는 필수 200자) */
    function suspendForm(u) {
      var input = h('textarea', { class: 'ao-textarea', maxlength: '200', placeholder: '예) 부적절한 문의 반복', 'aria-label': u.nickname + ' 정지 사유' });
      var error = h('span', { class: 'adm-error', role: 'alert' });
      var ok = h('button', { type: 'button', class: 'btn btn-primary adm-btn-sm', text: '정지하기' });
      ok.addEventListener('click', function () {
        var reason = input.value.trim();
        if (!reason) { input.setAttribute('aria-invalid', 'true'); error.textContent = '정지 사유를 입력해 주세요.'; input.focus(); return; }
        MBA.confirm(u.nickname + ' 님 계정을 정지할까요?', '바로 로그인할 수 없게 되고, 이미 로그인한 세션도 끊겨요.\n\n사유: ' + reason, '정지').then(function (yes) {
          if (!yes) return;
          ok.disabled = true;
          error.textContent = '';
          input.removeAttribute('aria-invalid');
          save(u, '/status', { status: 'SUSPENDED', reason: reason }, function () { u.status = 'SUSPENDED'; suspending = null; }, '계정을 정지했어요.', error, function () { ok.disabled = false; });
        });
      });
      setTimeout(function () { input.focus(); }, 0);
      return h('div', { class: 'ao-inline' }, [
        h('label', { class: 'adm-field' }, ['정지 사유', h('small', { text: '필수, 200자까지. 관리자 작업 기록에 남아요.' }), input]),
        error,
        h('div', { class: 'adm-actions' }, [
          h('button', { type: 'button', class: 'btn btn-ghost adm-btn-sm', text: '취소', onclick: function () { suspending = null; drawUsers(); } }),
          ok
        ])
      ]);
    }

    function activate(u) {
      MBA.confirm(u.nickname + ' 님 정지를 해제할까요?', '다시 로그인할 수 있게 되고 정지 사유는 지워져요.', '정지 해제').then(function (yes) {
        if (yes) save(u, '/status', { status: 'ACTIVE' }, function () { u.status = 'ACTIVE'; }, '정지를 해제했어요.');
      });
    }

    function changeRole(u, role) {
      var give = role === 'INSTRUCTOR';
      MBA.confirm(give ? u.nickname + ' 님에게 강사 권한을 줄까요?' : u.nickname + ' 님의 강사 권한을 회수할까요?',
        give ? '다음 로그인부터 강사 센터를 쓸 수 있어요.' : '만든 강의는 남지만 강사 화면은 바로 막혀요. 로그인한 강사 세션도 끊겨요.',
        give ? '강사 부여' : '강사 회수').then(function (yes) {
        if (yes) save(u, '/role', { role: role }, function () { u.role = role; }, give ? '강사 권한을 줬어요.' : '강사 권한을 회수했어요.');
      });
    }

    /* 바꾼 뒤 지금 필터에 안 맞게 된 사람(예: '정지' 목록에서 해제)은 목록에서 뺀다 */
    function save(u, path, body, apply, done, errorEl, onFail) {
      busy[u.memberId] = true;
      if (!errorEl) drawUsers();                        // 사유 입력 칸이 있으면 다시 그리지 않는다 (입력한 사유 유지)
      MB.api('/api/v1/admin/members/' + u.memberId + path, { method: 'PATCH', body: body }).then(function () {
        apply();
        if ((filter.status && filter.status !== u.status) || (filter.role && filter.role !== u.role)) {
          users.items = users.items.filter(function (x) { return x.memberId !== u.memberId; });
          users.total = Math.max(0, users.total - 1);
        }
        MB.toast(done);
        loaded.applications = false;                     // 강사 신청 목록은 다음에 열 때 새로 받는다
      }, function (err) {
        var msg = errText(err, '바꾸지 못했어요.');
        if (errorEl) errorEl.textContent = msg; else MB.toast(msg, 'error');
        if (onFail) onFail();
      }).then(function () {
        delete busy[u.memberId];
        if (!errorEl || !errorEl.textContent) drawUsers();   // 실패해서 사유 칸에 문구를 띄웠으면 그대로 둔다
      });
    }

    /* ----- 강사 가입 신청 (API 104·105) ----- */
    var appsBox = root.querySelector('[data-ao-apps]');
    var appStatus = root.querySelector('[data-ao-app-status]');
    var appCount = root.querySelector('[data-ao-app-count]');
    var apps = pager(function () { return '/api/v1/admin/instructor-applications?status=' + appStatus.value; }, function (a) { return a.memberId; });
    var appBusy = {};
    appStatus.addEventListener('change', loadApps);

    function loadApps() {
      apps.reset();
      fill(appsBox, h('p', { class: 'adm-empty', text: '불러오고 있어요…' }));
      apps.next().then(function (fresh) { if (fresh) drawApps(); }, function (err) {
        fill(appsBox, stateBox(errText(err, '강사 가입 신청을 불러오지 못했어요.'), loadApps));
      });
    }

    function drawApps() {
      if (appStatus.value === 'PENDING') {
        appCount.hidden = !apps.total;
        appCount.textContent = String(apps.total);
      }
      if (!apps.items.length) {
        fill(appsBox, h('div', { class: 'adm-box' }, [h('p', { class: 'adm-empty', text: appStatus.value === 'PENDING' ? '승인 대기 중인 신청이 없어요.' : '해당하는 신청이 없어요.' })]));
        return;
      }
      fill(appsBox, [
        h('div', { class: 'adm-box' }, [h('div', { class: 'adm-rows' }, apps.items.map(function (a) {
          var pending = (a.approvalStatus || appStatus.value) === 'PENDING';
          return h('div', { class: 'adm-row adm-row--wrap' }, [
            h('div', { class: 'adm-row__main' }, [
              h('strong', { text: (a.displayName || '-') + (a.email ? ' · ' + a.email : '') }),
              h('span', { class: 'adm-row__sub', text: '분야 ' + (a.field || '-') + ' · 신청 ' + day(a.createdAt) })
            ]),
            pending ? h('button', { type: 'button', class: 'btn btn-primary adm-btn-sm', text: appBusy[a.memberId] ? '승인 중…' : '승인',
              disabled: !!appBusy[a.memberId], onclick: function () { approve(a); } })
              : badge(a.approvalStatus === 'REVOKED' ? '회수됨' : '승인됨', a.approvalStatus === 'REVOKED' ? 'off' : 'ok')
          ]);
        }))]),
        moreButton(apps, drawApps)
      ]);
    }

    function approve(a) {
      MBA.confirm((a.displayName || '이 신청자') + ' 님을 강사로 승인할까요?', '다음 로그인부터 강사 센터를 쓸 수 있어요.', '승인').then(function (yes) {
        if (!yes) return;
        appBusy[a.memberId] = true;
        drawApps();
        MB.api('/api/v1/admin/instructor-applications/' + a.memberId + '/approve', { method: 'POST', body: {} }).then(function () {
          apps.items = apps.items.filter(function (x) { return x.memberId !== a.memberId; });
          apps.total = Math.max(0, apps.total - 1);
          MB.toast('강사로 승인했어요.');
          loaded.users = false;                          // 권한이 바뀌었으니 사용자 목록은 다음에 새로 받는다
        }, function (err) {
          MB.toast(errText(err, '승인하지 못했어요.'), 'error');
          if (err && (err.code === 'APPLICATION_NOT_PENDING' || err.code === 'APPLICATION_NOT_FOUND')) loadApps();
        }).then(function () { delete appBusy[a.memberId]; drawApps(); });
      });
    }

    // 승인 대기 수는 탭을 열지 않아도 보이게 한 번 센다
    MB.api('/api/v1/admin/instructor-applications?status=PENDING&page=0&size=1').then(function (res) {
      var n = (res && res.totalElements) || 0;
      appCount.hidden = !n;
      appCount.textContent = String(n);
    }).catch(function () { /* 숫자는 보조 정보 */ });

    showTab(new URLSearchParams(location.search).get('tab'), true);
  }

  /* =================== 문의 게시판 =================== */

  var board = document.querySelector('[data-ao-board]');
  if (board) initBoard(board);

  function initBoard(root) {
    var FILTERS = {
      all: { deleted: 'false' },
      waiting: { status: 'WAITING', deleted: 'false' },
      hidden: { hidden: 'true', deleted: 'false' },
      deleted: { deleted: 'true' }
    };
    var current = 'all';
    var posts = pager(function () { return '/api/v1/admin/inquiries?' + query(FILTERS[current]); }, function (q) { return q.inquiryId; });
    var open = {}, details = {}, loading = {}, errors = {}, busy = {};
    var box = root.querySelector('[data-ao-posts]');

    root.querySelectorAll('[data-ao-filter]').forEach(function (b) {
      b.addEventListener('click', function () {
        if (current === b.dataset.aoFilter) return;
        current = b.dataset.aoFilter;
        root.querySelectorAll('[data-ao-filter]').forEach(function (x) { x.setAttribute('aria-pressed', String(x === b)); });
        load();
      });
    });

    function load() {
      posts.reset();
      fill(box, h('p', { class: 'adm-empty', text: '불러오고 있어요…' }));
      posts.next().then(function (fresh) { if (fresh) draw(); }, function (err) {
        fill(box, stateBox(errText(err, '문의를 불러오지 못했어요.'), load));
      });
    }

    function draw() {
      if (!posts.items.length) {
        fill(box, h('div', { class: 'adm-box' }, [h('p', { class: 'ao-state', text: '해당하는 글이 없어요.' })]));
        return;
      }
      fill(box, [
        h('span', { class: 'adm-note', text: '모두 ' + posts.total.toLocaleString('ko-KR') + '건' }),
        h('div', { class: 'ao-posts' }, posts.items.map(post)),
        moreButton(posts, draw)
      ]);
    }

    function post(q) {
      var id = q.inquiryId;
      var isOpen = !!open[id];
      var detailId = 'ao-post-' + id;
      return h('article', { class: 'ao-post' + (q.isHidden ? ' is-hidden' : '') }, [
        h('div', { class: 'ao-post__tags' }, [
          q.isDeleted ? badge('삭제됨', 'off') : null,
          q.isHidden ? badge('숨김', 'bad') : null,
          q.status === 'ANSWERED' ? badge('답변 완료', 'ok') : badge('답변 대기', 'wait'),
          h('span', { text: (q.courseId ? '강의 문의 #' + q.courseId : '사이트 문의' + (q.category ? ' · ' + q.category : '')) + ' · ' + (q.writerNickname || '알 수 없음') + ' · ' + day(q.createdAt) })
        ]),
        h('span', { class: 'ao-post__title', text: q.title || '(제목 없음)' }),
        isOpen ? h('div', { class: 'ao-sec', id: detailId }, detail(q)) : null,
        h('div', { class: 'adm-actions', style: 'justify-content:flex-start' }, [
          q.isDeleted ? null : h('button', { type: 'button', class: 'btn btn-ghost adm-btn-sm', 'aria-expanded': String(isOpen),
            'aria-controls': isOpen ? detailId : null, text: isOpen ? '접기' : '내용·답글 보기', onclick: function () { toggle(q); } }),
          q.isDeleted ? h('span', { class: 'ao-td-sub', text: '작성자나 관리자가 삭제한 글이에요.' })
            : h('button', { type: 'button', class: 'btn adm-btn-sm ' + (q.isHidden ? 'btn-secondary' : 'btn-ghost adm-btn-danger'),
              text: busy[id] ? '처리 중…' : (q.isHidden ? '숨김 해제' : '숨기기'), disabled: !!busy[id], onclick: function () { setHidden(q, !q.isHidden); } })
        ])
      ]);
    }

    function detail(q) {
      var id = q.inquiryId;
      if (loading[id]) return [h('span', { class: 'ao-td-sub', text: '내용을 불러오는 중이에요…' })];
      if (errors[id]) return [h('span', { class: 'adm-error', role: 'alert', text: errors[id] }),
        h('button', { type: 'button', class: 'btn btn-secondary adm-btn-sm', text: '다시 불러오기', onclick: function () { fetchDetail(q); } })];
      var d = details[id];
      if (!d) return [];
      var replies = d.replies || [];
      return [
        h('p', { class: 'ao-post__body', text: d.content || '' }),
        replies.length ? h('div', { class: 'ao-sec' }, replies.map(function (r) {
          var w = r.writer || {};
          var who = w.role === 'ADMIN' ? '관리자' : w.role === 'INSTRUCTOR' ? '강사 ' + (w.nickname || '') : (w.nickname || '작성자');
          return h('div', { class: 'ao-reply' }, [h('b', { text: who + ' · ' + day(r.createdAt) }), r.content || '']);
        })) : h('span', { class: 'ao-td-sub', text: '아직 답글이 없어요.' }),
        replyForm(q)
      ];
    }

    /* 관리자 답글 (API 52). 관리자가 달면 답변 완료가 되고 작성자에게 알림이 간다 */
    function replyForm(q) {
      var input = h('textarea', { class: 'ao-textarea', maxlength: '1000', placeholder: '답글을 입력해 주세요 (1,000자까지)', 'aria-label': '답글' });
      var error = h('span', { class: 'adm-error', role: 'alert' });
      var send = h('button', { type: 'button', class: 'btn btn-primary adm-btn-sm', text: '답글 달기' });
      send.addEventListener('click', function () {
        var text = input.value.trim();
        if (!text) { input.setAttribute('aria-invalid', 'true'); error.textContent = '답글을 입력해 주세요.'; return; }
        send.disabled = true;
        MB.api('/api/v1/inquiries/' + q.inquiryId + '/replies', { method: 'POST', body: { content: text } }).then(function () {
          MB.toast('답글을 달았어요. 작성자에게 알림이 갔어요.');
          q.status = 'ANSWERED';
          if (current === 'waiting') {
            posts.items = posts.items.filter(function (x) { return x.inquiryId !== q.inquiryId; });
            posts.total = Math.max(0, posts.total - 1);
          }
          fetchDetail(q);
        }, function (err) {
          error.textContent = errText(err, '답글을 달지 못했어요.');
          send.disabled = false;
        });
      });
      return h('div', { class: 'ao-inline' }, [input, error, h('div', { class: 'adm-actions' }, [send])]);
    }

    function toggle(q) {
      var id = q.inquiryId;
      if (open[id]) { delete open[id]; draw(); return; }
      open[id] = true;
      if (details[id]) draw(); else fetchDetail(q);
    }

    function fetchDetail(q) {
      var id = q.inquiryId;
      loading[id] = true;
      delete errors[id];
      draw();
      MB.api('/api/v1/inquiries/' + id).then(function (d) { details[id] = d || {}; }, function (err) {
        errors[id] = errText(err, '내용을 불러오지 못했어요.');
      }).then(function () { delete loading[id]; draw(); });
    }

    function setHidden(q, hidden) {
      var id = q.inquiryId;
      if (busy[id]) return;
      var go = hidden
        ? MBA.confirm('이 글을 숨길까요?', '사용자 목록에서 빠지고, 상세는 "관리자에 의해 숨김 처리된 글이에요"로 보여요. 언제든 해제할 수 있어요.\n\n' + (q.title || ''), '숨기기')
        : Promise.resolve(true);
      go.then(function (yes) {
        if (!yes) return;
        busy[id] = true;
        draw();
        MB.api('/api/v1/admin/inquiries/' + id + '/hidden', { method: 'PATCH', body: { hidden: hidden } }).then(function () {
          q.isHidden = hidden;
          if (current === 'hidden' && !hidden) {
            posts.items = posts.items.filter(function (x) { return x.inquiryId !== id; });
            posts.total = Math.max(0, posts.total - 1);
          }
          MB.toast(hidden ? '글을 숨겼어요.' : '숨김을 해제했어요.');
        }, function (err) {
          MB.toast(errText(err, '숨김 상태를 바꾸지 못했어요.'), 'error');
        }).then(function () { delete busy[id]; draw(); });
      });
    }

    load();
  }
})();
