/* comment.
*   담당 : DBA ① 신아림 (#360, 강사 Q&A 관리 /instructor/dashboard?tab=qna)
*   원본 : InstructorDashboard.dc.html 의 qna 보기 (상태 탭 · 왼쪽 목록 · 오른쪽 상세와 답변)
*   쓰는 API
*   - 82 내 강의 문의 GET /api/v1/instructor/inquiries (답변 대기 먼저, 그다음 최신순). 학생 이름 writerNickname 은 #370
*   - 49 상세 GET /api/v1/inquiries/{id} (본문 · 작성자 · 답변), 52 답변 등록 POST /api/v1/inquiries/{id}/replies
*   - 답변 수정 PATCH · 삭제 DELETE /api/v1/inquiries/{id}/replies/{replyId} (#370)
*   - ?inquiryId= 로 들어오면 그 문의를 먼저 연다 (대시보드 '최근 질문'에서 넘어올 때)
*   - 사용자 문의 게시판(inquiry-board.js)과 모양이 달라 강사 화면은 이 파일이 따로 그린다
*  */
(function () {
  var I = window.MBIns;
  if (!I || !window.MB) return;
  var h = I.h, fill = I.fill;

  var PAGE_SIZE = 50;
  var ANSWER_MAX = 1000;
  var TABS = [['all', '전체'], ['WAITING', '답변 대기'], ['ANSWERED', '답변 완료']];
  var STATUS = { WAITING: ['답변 대기', 'qa-pill--wait'], ANSWERED: ['답변 완료', 'qa-pill--done'] };

  function fetchAll(page, items) {
    return MB.api('/api/v1/instructor/inquiries?page=' + page + '&size=' + PAGE_SIZE).then(function (res) {
      var all = items.concat((res && res.items) || []);
      return res && page + 1 < res.totalPages ? fetchAll(page + 1, all) : all;
    });
  }

  /* 답변 수정·삭제 API(#370)가 아직 서버에 없으면 404·405 가 온다 */
  function replyError(err, fallback) {
    if (err && (err.status === 404 || err.status === 405)) return '답변 수정·삭제는 준비 중이에요';
    return I.errorText(err, fallback);
  }

  function pill(status, big) {
    var s = STATUS[status] || [status, ''];
    return h('span', { class: 'qa-pill ' + s[1] + (big ? ' qa-pill--big' : ''), text: s[0] });
  }

  function draw(root) {
    var params = new URLSearchParams(location.search);
    var st = { items: [], tab: 'all', selId: Number(params.get('inquiryId')) || null, details: {}, editing: false, draft: '', error: '' };
    var els = { tabs: h('section', { class: 'qa-tabs', role: 'tablist', 'aria-label': '답변 상태' }), list: h('div', { class: 'qa-list' }), detail: h('div', { class: 'qa-detail-wrap' }) };

    fill(root, h('h1', { class: 'mp-sr', text: 'Q&A 관리' }), h('div', { class: 'ins-state', text: '문의를 불러오는 중이에요…' }));

    function load() {
      fetchAll(0, []).then(function (items) {
        st.items = items;
        if (!st.selId || !find(st.selId)) st.selId = items.length ? items[0].inquiryId : null;
        fill(root, h('h1', { class: 'mp-sr', text: 'Q&A 관리' }), els.tabs, h('section', { class: 'qa-grid' }, [els.list, els.detail]));
        drawAll();
        if (st.selId) openDetail(st.selId);
      }).catch(function (err) {
        fill(root, h('div', { class: 'ins-state ins-state--error', role: 'alert' }, [
          I.errorText(err, '문의를 불러오지 못했어요.'), h('br'),
          h('button', { type: 'button', class: 'btn btn-secondary', text: '다시 시도', onclick: load })
        ]));
      });
    }

    function find(id) { return st.items.filter(function (q) { return q.inquiryId === id; })[0]; }
    function filtered() { return st.items.filter(function (q) { return st.tab === 'all' || q.status === st.tab; }); }
    function student(q) {
      var d = st.details[q.inquiryId];
      return q.writerNickname || (d && d.writer && d.writer.nickname) || '';
    }

    function drawAll() { drawTabs(); drawList(); drawDetail(); }

    function drawTabs() {
      var n = { all: st.items.length, WAITING: 0, ANSWERED: 0 };
      st.items.forEach(function (q) { if (n[q.status] !== undefined) n[q.status]++; });
      fill(els.tabs, TABS.map(function (t) {
        var on = st.tab === t[0];
        return h('button', { type: 'button', role: 'tab', 'aria-selected': on ? 'true' : 'false', class: 'qa-tab' + (on ? ' is-on' : ''), onclick: function () {
          st.tab = t[0];
          // 지금 연 문의가 새 탭에 없으면 첫 문의를 열고, 하나도 없으면 오른쪽을 비운다
          var visible = filtered();
          if (!visible.some(function (q) { return q.inquiryId === st.selId; })) {
            st.selId = visible.length ? visible[0].inquiryId : null;
            st.editing = false; st.draft = ''; st.error = '';
          }
          drawTabs();
          drawList();
          if (st.selId) openDetail(st.selId); else drawDetail();
        } }, [t[1], h('span', { class: 'qa-tab__n', text: String(n[t[0]]) })]);
      }));
    }

    function drawList() {
      var rows = filtered();
      fill(els.list, rows.length ? rows.map(function (q) {
        var on = q.inquiryId === st.selId;
        return h('button', { type: 'button', class: 'qa-row' + (on ? ' is-on' : ''), 'aria-current': on ? 'true' : null, onclick: function () {
          st.selId = q.inquiryId;
          st.editing = false; st.draft = ''; st.error = '';
          drawList();
          openDetail(q.inquiryId);
        } }, [
          h('span', { class: 'qa-row__top' }, [pill(q.status), h('span', { class: 'qa-row__course', text: q.courseTitle || '' }), h('span', { class: 'qa-row__date', text: I.date(q.createdAt) })]),
          h('span', { class: 'qa-row__title', text: q.title }),
          h('span', { class: 'qa-row__student', text: student(q) })
        ]);
      }) : h('div', { class: 'qa-empty', text: '해당하는 질문이 없어요' }));
    }

    function openDetail(id) {
      if (st.details[id]) { drawDetail(); return; }
      drawDetail();
      MB.api('/api/v1/inquiries/' + id).then(function (d) {
        st.details[id] = d;
        if (st.selId === id) { drawDetail(); drawList(); }
      }).catch(function (err) {
        if (st.selId === id) fill(els.detail, h('div', { class: 'qa-detail' }, [h('p', { class: 'qa-err', text: I.errorText(err, '문의를 불러오지 못했어요') })]));
      });
    }

    function reload(id, message) {
      delete st.details[id];
      return Promise.all([MB.api('/api/v1/inquiries/' + id), fetchAll(0, [])]).then(function (r) {
        st.details[id] = r[0];
        st.items = r[1];
        drawTabs();
        drawList();
        // 그사이 다른 문의를 열어 답변을 쓰고 있으면 그 편집기와 초안은 건드리지 않는다
        if (st.selId === id) {
          st.editing = false; st.draft = ''; st.error = '';
          drawDetail();
        }
        if (message) I.toast(message);
      });
    }

    function drawDetail() {
      var q = st.selId && find(st.selId);
      if (!q) { fill(els.detail); return; }
      var d = st.details[q.inquiryId];
      if (!d) { fill(els.detail, h('div', { class: 'qa-detail' }, [h('div', { class: 'ins-state', text: '불러오는 중이에요…' })])); return; }

      var name = (d.writer && d.writer.nickname) || student(q) || '수강생';
      var replies = d.replies || [];
      var answer = replies.length ? replies[replies.length - 1] : null;    // 목업처럼 답변 하나를 보여 준다 (가장 최근 답변)
      var showEditor = !answer || st.editing;

      var parts = [
        h('div', { class: 'qa-detail__top' }, [pill(d.status || q.status, true), h('span', { class: 'qa-detail__course', text: q.courseTitle || '' })]),
        h('h2', { class: 'qa-detail__title', text: d.title || q.title }),
        h('div', { class: 'qa-detail__who' }, [h('span', { class: 'qa-avatar', text: name.charAt(0) }), h('b', { text: name }), h('span', { text: '· 작성일 ' + I.date(d.createdAt || q.createdAt) })]),
        h('p', { class: 'qa-detail__body', text: d.content || '' })
      ];

      if (answer && !st.editing) {
        parts.push(h('div', { class: 'qa-answer' }, [
          h('span', { class: 'qa-answer__head', text: '강사 답변 · ' + I.date(answer.createdAt) }),
          h('p', { class: 'qa-answer__text', text: answer.content }),
          h('div', { class: 'qa-answer__actions' }, [
            h('button', { type: 'button', class: 'qa-btn qa-btn--soft', text: '답변 수정', onclick: function () {
              st.editing = true; st.draft = answer.content; st.error = '';
              drawDetail();
            } }),
            h('button', { type: 'button', class: 'qa-btn qa-btn--danger', text: '답변 삭제', onclick: function () {
              I.confirm('등록한 답변을 삭제할까요?', function () {
                MB.api('/api/v1/inquiries/' + q.inquiryId + '/replies/' + answer.replyId, { method: 'DELETE' }).then(function () {
                  // 삭제는 끝났다. 다시 불러오기만 실패하면 삭제 실패로 안내하지 않는다
                  reload(q.inquiryId, '답변을 삭제했어요').catch(function () { I.toast('답변을 삭제했어요. 화면을 새로고침해 주세요'); });
                }, function (err) { I.toast(replyError(err, '답변을 삭제하지 못했어요')); });
              });
            } })
          ])
        ]));
      }

      if (showEditor) {
        var editing = st.editing && answer;
        var area = h('textarea', { class: 'qa-textarea', id: 'qa-answer', value: st.draft, maxlength: String(ANSWER_MAX), placeholder: '수강생에게 보여질 답변을 작성해 주세요' });
        var err = h('span', { class: 'qa-err', role: 'alert', text: st.error });
        var save = h('button', { type: 'button', class: 'qa-btn qa-btn--primary', text: editing ? '수정 완료' : '답변 등록' });
        area.addEventListener('input', function () { st.draft = area.value; if (st.error) { st.error = ''; err.textContent = ''; } });
        save.addEventListener('click', function () {
          var text = area.value.trim();
          if (!text) { st.error = '답변 내용을 입력해 주세요'; err.textContent = st.error; return; }
          if (save.disabled) return;
          save.disabled = true;
          var req = editing
            ? MB.api('/api/v1/inquiries/' + q.inquiryId + '/replies/' + answer.replyId, { method: 'PATCH', body: { content: text } })
            : MB.api('/api/v1/inquiries/' + q.inquiryId + '/replies', { method: 'POST', body: { content: text } });
          var done = editing ? '답변을 수정했어요' : '답변을 등록했어요';
          req.then(function () {
            // 저장은 끝났다. 다시 불러오기만 실패하면 저장 실패로 안내하지 않고, 버튼도 다시 켜지 않는다 (같은 답변을 또 등록하지 않게)
            reload(q.inquiryId, done).catch(function () { I.toast(done + '. 화면을 새로고침해 주세요'); });
          }, function (e) {
            st.error = editing ? replyError(e, '답변을 저장하지 못했어요') : I.errorText(e, '답변을 저장하지 못했어요');
            err.textContent = st.error;
            save.disabled = false;
          });
        });
        parts.push(h('div', { class: 'qa-editor' }, [
          h('label', { class: 'qa-editor__label', for: 'qa-answer', text: editing ? '답변 수정' : '답변 작성' }),
          area, err,
          h('div', { class: 'qa-editor__actions' }, [
            editing ? h('button', { type: 'button', class: 'qa-btn qa-btn--ghost', text: '취소', onclick: function () {
              st.editing = false; st.draft = ''; st.error = '';
              drawDetail();
            } }) : null,
            save
          ])
        ]));
      }

      fill(els.detail, h('div', { class: 'qa-detail', 'aria-live': 'polite' }, parts));
    }

    load();
  }

  I.tabs.qna = draw;
})();
