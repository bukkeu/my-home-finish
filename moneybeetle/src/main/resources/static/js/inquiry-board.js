/* comment.
*   담당 : DBA ① 신아림 (#55, 문의 게시판)
*   문의 게시판 그리기 (window.MBInquiry). 두 곳에서 같이 쓴다
*   - 사이트 문의 게시판 /user/inquiry          : mount(box, { mode: 'site', openId })
*   - 강의 상세의 '강의 문의' 탭 (#52 화면)     : mount(box, { mode: 'course', courseId })
*   API : GET·POST /api/v1/inquiries, GET·PATCH·DELETE /api/v1/inquiries/{id}, POST /{id}/replies (API 47~52)
*   - 볼 수 있는 사람은 작성자, 그 강의의 강사, 관리자 (서버가 정한다, SP-40). 그래서 공개/비공개 선택은 두지 않는다
*   - 수정은 작성자만, 답변이 달리기 전에만 (INQUIRY_ALREADY_ANSWERED). 삭제는 작성자만 (화면에서는 한 번 더 확인)
*   - 제목 50자, 내용 1,000자, 하루 10개 (INQUIRY_DAILY_LIMIT)
*   - 글자는 모두 textContent 로 넣는다 (사용자 글을 HTML 로 해석하지 않음)
*  */
(function () {
  if (window.MBInquiry) return;

  var CATEGORIES = ['강의', '학습·퀴즈', '모의 투자', '모의 청약', '계정·캐시', '기타'];   // 화면설계 5.2.6
  var TITLE_MAX = 50;
  var CONTENT_MAX = 1000;
  var PAGE_SIZE = 50;
  var STATUS = { WAITING: '답변 대기', ANSWERED: '답변 완료' };
  var TABS = [['all', '전체'], ['WAITING', '답변 대기'], ['ANSWERED', '답변 완료']];

  /* 자식 요소를 바꿔 넣는다. 배열은 펼치고 null·false 는 건너뛴다 (replaceChildren 은 null 을 "null" 글자로 넣는다) */
  function fill(el) {
    var nodes = [];
    [].slice.call(arguments, 1).forEach(function add(x) {
      if (Array.isArray(x)) x.forEach(add);
      else if (x !== null && x !== undefined && x !== false) nodes.push(x);
    });
    el.replaceChildren.apply(el, nodes);
  }

  var meRequest = null;   // 내 회원 ID (수정·삭제 버튼을 내 글에만 보이려고). 한 번만 받는다
  function loadMe() {
    if (!meRequest) {
      meRequest = MB.api('/api/v1/members/me').then(function (me) { return me && me.memberId; })
        .catch(function () { return null; });
    }
    return meRequest;
  }

  function h(tag, attrs, children) {
    var el = document.createElement(tag);
    Object.keys(attrs || {}).forEach(function (key) {
      var value = attrs[key];
      if (value === null || value === undefined || value === false) return;
      if (key === 'text') el.textContent = value;
      else if (key === 'class') el.className = value;
      else if (key === 'value') el.value = value;
      else if (key.indexOf('on') === 0 && typeof value === 'function') el.addEventListener(key.slice(2), value);
      else el.setAttribute(key, value === true ? '' : value);
    });
    (children || []).forEach(function (child) {
      if (child === null || child === undefined || child === false) return;
      el.appendChild(typeof child === 'string' ? document.createTextNode(child) : child);
    });
    return el;
  }

  function date(value) {
    if (!value) return '';
    var m = String(value).match(/^(\d{4})-(\d{2})-(\d{2})(?:T(\d{2}):(\d{2}))?/);
    return m ? m[1] + '.' + m[2] + '.' + m[3] + (m[4] ? ' ' + m[4] + ':' + m[5] : '') : String(value);
  }

  function errorText(err, fallback) {
    if (err && err.fields) {
      var first = Object.keys(err.fields)[0];
      if (first) return err.fields[first];
    }
    return (err && err.message) || fallback;
  }

  /* 글자 수가 보이는 입력칸 */
  function field(label, input, max) {
    var count = h('span', { class: 'inq-count', text: input.value.length + ' / ' + max.toLocaleString('ko-KR') });
    input.addEventListener('input', function () { count.textContent = input.value.length + ' / ' + max.toLocaleString('ko-KR'); });
    return h('label', { class: 'inq-field' }, [h('span', { class: 'inq-field__label' }, [h('span', { text: label }), count]), input]);
  }

  function mount(box, options) {
    var opts = options || {};
    var isCourse = opts.mode === 'course';
    var st = { items: [], tab: 'all', writing: false, openId: opts.openId || null, details: {}, meId: null, loading: true, error: null };

    var els = {
      form: h('div'),
      tabs: h('div', { class: 'inq-tabs', role: 'tablist', 'aria-label': '답변 상태' }),
      list: h('div', { class: 'inq-list', 'aria-live': 'polite' }),
      extra: h('div')
    };

    fill(box, 
      h('div', { class: 'inq-top' }, [
        h('p', { class: 'inq-note', text: isCourse
          ? '이 강의에 대해 궁금한 점을 강사에게 남겨 주세요. 작성자·강사·관리자만 볼 수 있어요.'
          : '강의나 서비스를 이용하다 궁금한 점을 남겨 주세요. 작성자와 관리자만 볼 수 있고, 관리자가 확인한 뒤 답변을 드려요.' }),
        h('button', { type: 'button', class: 'btn btn-primary', text: '문의 작성', onclick: function () { st.writing = !st.writing; drawForm(); } })
      ]),
      els.form, els.extra, els.tabs, els.list
    );

    /* ---------- 불러오기 ---------- */

    function listUrl(page) {
      return '/api/v1/inquiries?' + (isCourse ? 'courseId=' + opts.courseId + '&' : '') + 'page=' + page + '&size=' + PAGE_SIZE;
    }

    function fetchAll(page, items) {
      return MB.api(listUrl(page)).then(function (res) {
        var all = items.concat((res && res.items) || []);
        return res && page + 1 < res.totalPages ? fetchAll(page + 1, all) : all;
      });
    }

    function load() {
      st.loading = true;
      st.error = null;
      drawList();
      Promise.all([fetchAll(0, []), loadMe()]).then(function (res) {
        st.items = res[0];
        st.meId = res[1];
        st.loading = false;
        drawList();
        if (st.openId) openDetail(st.openId, true);
      }).catch(function (err) {
        st.loading = false;
        st.error = err;
        drawList();
      });
    }

    /* ---------- 작성 ---------- */

    function drawForm() {
      if (!st.writing) { fill(els.form); return; }
      var category = isCourse ? null : h('select', { class: 'inq-input', 'aria-label': '분류' },
        CATEGORIES.map(function (c) { return h('option', { value: c, text: c }); }));
      var title = h('input', { class: 'inq-input', maxlength: String(TITLE_MAX), placeholder: '궁금한 내용을 한 줄로 적어 주세요' });
      var content = h('textarea', { class: 'inq-input', maxlength: String(CONTENT_MAX), rows: '6',
        placeholder: '상황을 자세히 적어 주시면 더 정확하게 답변드릴 수 있어요' });
      var msg = h('p', { class: 'inq-error', role: 'alert' });
      var submit = h('button', { type: 'button', class: 'btn btn-primary', text: '등록하기' });

      submit.addEventListener('click', function () {
        var body = { title: title.value.trim(), content: content.value.trim() };
        if (!body.title || !body.content) { msg.textContent = '제목과 내용을 모두 입력해 주세요.'; return; }
        if (isCourse) body.courseId = opts.courseId; else body.category = category.value;
        submit.disabled = true;
        msg.textContent = '';
        MB.api('/api/v1/inquiries', { method: 'POST', body: body }).then(function (res) {
          MB.toast('문의를 등록했어요. 답변이 달리면 알림으로 알려 드려요.');
          st.writing = false;
          st.openId = res && res.inquiryId;
          drawForm();
          load();
        }).catch(function (err) {
          msg.textContent = errorText(err, '문의를 등록하지 못했어요. 다시 시도해 주세요.');
          submit.disabled = false;
        });
      });

      fill(els.form, h('section', { class: 'inq-form', 'aria-label': '새 문의' }, [
        h('div', { class: 'inq-form__head' }, [h('strong', { text: '새 문의' }), h('span', { class: 'inq-count', text: '하루 10개까지 남길 수 있어요' })]),
        category ? h('label', { class: 'inq-field' }, [h('span', { class: 'inq-field__label' }, [h('span', { text: '분류' })]), category]) : null,
        field('제목', title, TITLE_MAX),
        field('내용', content, CONTENT_MAX),
        msg,
        h('div', { class: 'inq-actions' }, [
          h('button', { type: 'button', class: 'btn btn-secondary', text: '취소', onclick: function () { st.writing = false; drawForm(); } }),
          submit
        ])
      ]));
      title.focus();
    }

    /* ---------- 목록 ---------- */

    function shownItems() {
      return st.tab === 'all' ? st.items : st.items.filter(function (i) { return i.status === st.tab; });
    }

    function drawList() {
      fill(els.tabs, TABS.map(function (t) {
        var count = t[0] === 'all' ? st.items.length : st.items.filter(function (i) { return i.status === t[0]; }).length;
        return h('button', { type: 'button', role: 'tab', class: 'inq-tab', 'aria-selected': String(st.tab === t[0]),
          onclick: function () { st.tab = t[0]; drawList(); } }, [t[1], h('span', { class: 'inq-tab__n', text: String(count) })]);
      }));

      if (st.loading) { fill(els.list, h('div', { class: 'inq-state', text: '문의를 불러오는 중이에요…' })); return; }
      if (st.error) {
        fill(els.list, h('div', { class: 'inq-state inq-state--error', role: 'alert' }, [
          errorText(st.error, '문의를 불러오지 못했어요.'), h('br'),
          h('button', { type: 'button', class: 'btn btn-secondary', text: '다시 시도', onclick: load })
        ]));
        return;
      }
      var rows = shownItems();
      if (!rows.length) {
        fill(els.list, h('div', { class: 'inq-state', text: st.items.length ? '해당하는 문의가 없어요.'
          : (isCourse ? '아직 남긴 강의 문의가 없어요.' : '아직 남긴 문의가 없어요.') }));
      } else {
        fill(els.list, rows.map(renderRow));
      }
      // 알림 링크(/user/inquiry/{id})로 연 문의가 이 목록에 없으면(강의 문의 등) 위에 따로 보여 준다
      var outside = st.openId && !st.items.some(function (i) { return i.inquiryId === st.openId; });
      fill(els.extra, outside && st.details[st.openId] ? renderStandalone(st.openId) : h('span'));
    }

    function renderRow(item) {
      var open = st.openId === item.inquiryId;
      var detailBox = h('div', { class: 'inq-detail', id: 'inq-detail-' + item.inquiryId });
      if (open) fillDetail(detailBox, item.inquiryId);
      return h('article', { class: 'inq-row' + (open ? ' is-open' : '') }, [
        h('button', { type: 'button', class: 'inq-row__head', 'aria-expanded': String(open), 'aria-controls': 'inq-detail-' + item.inquiryId,
          onclick: function () { toggle(item.inquiryId); } }, [
          h('span', { class: 'inq-status inq-status--' + String(item.status).toLowerCase(), text: STATUS[item.status] || item.status }),
          h('span', { class: 'inq-row__text' }, [
            h('strong', { class: 'inq-row__title', text: item.title }),
            h('span', { class: 'inq-meta', text: [item.category, item.writerNickname, date(item.createdAt)].filter(Boolean).join(' · ') })
          ]),
          h('span', { class: 'inq-row__caret', 'aria-hidden': 'true', text: open ? '▴' : '▾' })
        ]),
        open ? detailBox : null
      ]);
    }

    function renderStandalone(id) {
      var d = st.details[id];
      var detailBox = h('div', { class: 'inq-detail' });
      fillDetail(detailBox, id);
      return h('article', { class: 'inq-row is-open inq-row--linked' }, [
        h('div', { class: 'inq-row__head' }, [
          h('span', { class: 'inq-status inq-status--' + String(d.status).toLowerCase(), text: STATUS[d.status] || d.status }),
          h('span', { class: 'inq-row__text' }, [
            h('strong', { class: 'inq-row__title', text: d.title }),
            h('span', { class: 'inq-meta', text: (d.courseId ? '강의 문의 · ' : '') + date(d.createdAt) })
          ]),
          d.courseId ? h('a', { class: 'btn btn-secondary', href: '/user/lecture-detail?courseId=' + d.courseId, text: '해당 강의 보러 가기' }) : null
        ]),
        detailBox
      ]);
    }

    function toggle(id) {
      st.openId = st.openId === id ? null : id;
      drawList();
    }

    function openDetail(id, scroll) {
      return MB.api('/api/v1/inquiries/' + id).then(function (d) {
        st.details[id] = d;
        st.openId = id;
        drawList();
        if (scroll) {
          var el = document.getElementById('inq-detail-' + id) || els.extra;
          if (el && el.scrollIntoView) el.scrollIntoView({ behavior: 'smooth', block: 'center' });
        }
      }).catch(function (err) {
        if (st.openId === id && !st.items.some(function (i) { return i.inquiryId === id; })) st.openId = null;
        MB.toast(errorText(err, '문의를 열지 못했어요.'), 'error');
        drawList();
      });
    }

    /* ---------- 상세 ---------- */

    function fillDetail(detailBox, id) {
      var d = st.details[id];
      if (!d) {
        fill(detailBox, h('div', { class: 'inq-state', text: '문의를 불러오는 중이에요…' }));
        MB.api('/api/v1/inquiries/' + id).then(function (res) {
          st.details[id] = res;
          if (st.openId === id) fillDetail(detailBox, id);
        }).catch(function (err) {
          fill(detailBox, h('div', { class: 'inq-state inq-state--error', role: 'alert' }, [
            errorText(err, '문의를 불러오지 못했어요.'), h('br'),
            h('button', { type: 'button', class: 'btn btn-secondary', text: '다시 시도', onclick: function () { fillDetail(detailBox, id); } })
          ]));
        });
        return;
      }
      var mine = st.meId != null && d.writer && d.writer.memberId === st.meId;
      var answers = (d.replies || []).filter(function (r) { return r.writer && r.writer.role !== 'MEMBER'; });
      var comments = (d.replies || []).filter(function (r) { return !r.writer || r.writer.role === 'MEMBER'; });

      fill(detailBox, 
        h('p', { class: 'inq-body', text: d.content }),
        answers.length ? h('div', { class: 'inq-answers' }, answers.map(function (r) {
          return h('div', { class: 'inq-answer' }, [
            h('div', { class: 'inq-answer__head' }, [
              h('span', { class: 'inq-answer__who', text: r.writer.role === 'INSTRUCTOR' ? '강사 답변' : '관리자 답변' }),
              h('span', { class: 'inq-meta', text: [r.writer.nickname, date(r.createdAt)].filter(Boolean).join(' · ') })
            ]),
            h('p', { class: 'inq-body', text: r.content })
          ]);
        })) : h('div', { class: 'inq-waiting' }, [
          isCourse || d.courseId ? '강사가 확인하고 있어요.' : '관리자가 확인하고 있어요.', h('br'), '답변이 등록되면 알림으로 알려 드려요.'
        ]),
        comments.length ? h('div', { class: 'inq-comments' }, [h('span', { class: 'inq-field__label', text: '댓글 ' + comments.length })]
          .concat(comments.map(function (c) {
            return h('div', { class: 'inq-comment' }, [
              h('b', { text: (c.writer && c.writer.nickname) || '작성자' }),
              h('span', { class: 'inq-comment__body', text: c.content }),
              h('span', { class: 'inq-meta', text: date(c.createdAt) })
            ]);
          }))) : null,
        d.isHidden ? null : renderReplyBox(d, mine),
        mine ? renderOwnerActions(d) : null
      );
    }

    function refreshDetail(id) {
      delete st.details[id];
      return openDetail(id, false).then(function () {
        // 목록의 상태(답변 대기/완료)·제목도 서버 기준으로 맞춘다
        var d = st.details[id];
        st.items.forEach(function (i) { if (i.inquiryId === id && d) { i.status = d.status; i.title = d.title; } });
        drawList();
      });
    }

    /* 작성자는 댓글, 강사·관리자(내 글이 아님)는 답변을 남긴다. 답변이 달리면 서버가 '답변 완료'로 바꾸고 작성자에게 알린다 */
    function renderReplyBox(d, mine) {
      var input = h('textarea', { class: 'inq-input', rows: '2', maxlength: String(CONTENT_MAX), 'aria-label': mine ? '댓글' : '답변',
        placeholder: mine ? '추가로 궁금한 점을 댓글로 남겨 주세요' : '답변을 남겨 주세요. 등록하면 작성자에게 알림이 가요' });
      var send = h('button', { type: 'button', class: mine ? 'btn btn-secondary' : 'btn btn-primary', text: mine ? '댓글 남기기' : '답변 남기기' });
      send.addEventListener('click', function () {
        var content = input.value.trim();
        if (!content) { MB.toast('댓글 내용을 입력해 주세요.', 'error'); return; }
        send.disabled = true;
        MB.api('/api/v1/inquiries/' + d.inquiryId + '/replies', { method: 'POST', body: { content: content } }).then(function () {
          refreshDetail(d.inquiryId);
        }).catch(function (err) {
          MB.toast(errorText(err, mine ? '댓글을 남기지 못했어요.' : '답변을 남기지 못했어요.'), 'error');
          send.disabled = false;
        });
      });
      return h('div', { class: 'inq-reply' }, [input, send]);
    }

    function renderOwnerActions(d) {
      var bar = h('div', { class: 'inq-owner' });
      function idle() {
        fill(bar, 
          d.status === 'WAITING' ? h('button', { type: 'button', class: 'btn btn-ghost', text: '수정', onclick: function () { edit(); } }) : null,
          h('button', { type: 'button', class: 'btn btn-ghost inq-danger', text: '문의 삭제', onclick: confirmDelete })
        );
      }
      function confirmDelete() {
        var yes = h('button', { type: 'button', class: 'btn btn-secondary inq-danger', text: '삭제할게요' });
        yes.addEventListener('click', function () {
          yes.disabled = true;
          MB.api('/api/v1/inquiries/' + d.inquiryId, { method: 'DELETE' }).then(function () {
            MB.toast('문의를 삭제했어요.');
            st.items = st.items.filter(function (i) { return i.inquiryId !== d.inquiryId; });
            delete st.details[d.inquiryId];
            st.openId = null;
            drawList();
          }).catch(function (err) {
            MB.toast(errorText(err, '삭제하지 못했어요.'), 'error');
            idle();
          });
        });
        fill(bar, h('span', { class: 'inq-meta', text: '삭제한 문의는 되돌릴 수 없어요. 삭제할까요?' }),
          h('button', { type: 'button', class: 'btn btn-ghost', text: '취소', onclick: idle }), yes);
      }
      function edit() {
        var title = h('input', { class: 'inq-input', maxlength: String(TITLE_MAX), value: d.title });
        var content = h('textarea', { class: 'inq-input', maxlength: String(CONTENT_MAX), rows: '5', value: d.content });
        var msg = h('p', { class: 'inq-error', role: 'alert' });
        var save = h('button', { type: 'button', class: 'btn btn-primary', text: '저장' });
        save.addEventListener('click', function () {
          var body = { title: title.value.trim(), content: content.value.trim() };
          if (!body.title || !body.content) { msg.textContent = '제목과 내용을 모두 입력해 주세요.'; return; }
          save.disabled = true;
          MB.api('/api/v1/inquiries/' + d.inquiryId, { method: 'PATCH', body: body }).then(function () {
            MB.toast('문의를 수정했어요.');
            refreshDetail(d.inquiryId);
          }).catch(function (err) {
            msg.textContent = errorText(err, '수정하지 못했어요.');
            save.disabled = false;
            if (err && err.code === 'INQUIRY_ALREADY_ANSWERED') refreshDetail(d.inquiryId);
          });
        });
        fill(bar, h('div', { class: 'inq-form inq-form--edit' }, [
          field('제목', title, TITLE_MAX), field('내용', content, CONTENT_MAX), msg,
          h('div', { class: 'inq-actions' }, [h('button', { type: 'button', class: 'btn btn-secondary', text: '취소', onclick: idle }), save])
        ]));
      }
      idle();
      return bar;
    }

    load();
  }

  window.MBInquiry = { mount: mount };
})();
