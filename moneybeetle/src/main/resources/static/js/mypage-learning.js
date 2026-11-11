/* comment.
*   담당 : DBA ① 신아림 (#71, 마이페이지 학습 기록·스크랩·오답노트·Q&A 탭)
*   조각 : templates/fragments/mypage/learning.html (틀 member/mypage.html, #54)
*   - 틀이 탭을 열 때마다 보내는 'mypage:tab' 이벤트(detail.tab)를 받아, 그 탭을 처음 열 때만 불러온다
*   - 퀴즈 오답노트(wrong) : API 44 목록 · 45 다시 풀기(한 문제씩 채점, 기록 없음) · 46 삭제
*   - 강의 스크랩(scrap)   : API 38 목록 · 37 해제 · 강의 상세로 이동
*   - 학습 기록(learning)  : API 34 최근 시청(이어보기 → 강의 상세 &lessonId=, 저장된 위치부터 재생) · 35 학습 완료
*   - Q&A·문의 모음(qna)   : API 149 내가 쓴 문의 · 49 내용·답변 펼쳐 보기 · 해당 강의 보러 가기
*   - 주소 해시 #tab-wrong · #tab-scrap · #tab-history · #tab-qna 로 들어와도 그 탭을 연다 (틀의 ?tab= · #이름과 함께)
*  */
(function () {
  'use strict';

  if (!window.MB) return;
  var boxes = {};
  document.querySelectorAll('[data-ml]').forEach(function (el) { boxes[el.dataset.ml] = el.querySelector('[data-ml-body]'); });
  if (!Object.keys(boxes).length) return;

  var PAGE_SIZE = 20;
  var HASH_TO_TAB = { 'tab-wrong': 'wrong', 'tab-scrap': 'scrap', 'tab-history': 'learning', 'tab-qna': 'qna' };
  var loaders = { wrong: loadWrong, scrap: loadScrap, learning: loadHistory, qna: loadQna };
  var started = {};

  /* ---------- 도구 ---------- */

  function h(tag, attrs, children) {
    var el = document.createElement(tag);
    Object.keys(attrs || {}).forEach(function (key) {
      var v = attrs[key];
      if (v === null || v === undefined || v === false) return;
      if (key === 'text') el.textContent = v;
      else if (key === 'class') el.className = v;
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

  function day(value) {
    if (!value) return '';
    var d = new Date(value);
    if (isNaN(d)) return '';
    var today = new Date();
    if (d.toDateString() === today.toDateString()) return '오늘';
    var md = (d.getMonth() + 1) + '월 ' + d.getDate() + '일';
    return d.getFullYear() === today.getFullYear() ? md : d.getFullYear() + '년 ' + md;
  }

  function errorText(err, fallback) {
    return (err && err.message) || fallback;
  }

  function stateBox(text, retry) {
    return h('div', { class: 'card ml-state', role: retry ? 'alert' : null }, [
      h('span', { text: text }),
      retry ? h('button', { type: 'button', class: 'btn btn-secondary', text: '다시 불러오기', onclick: retry }) : null
    ]);
  }

  function emptyBox(text, link) {
    return h('div', { class: 'card ml-state' }, [
      h('span', { text: text }),
      link ? h('a', { class: 'btn btn-primary', href: link.href, text: link.text }) : null
    ]);
  }

  function badge(text, kind) {
    return h('span', { class: 'ml-badge ml-badge--' + kind, text: text });
  }

  /* 쪽 단위 목록: 처음 한 쪽을 받고 "더 보기"로 다음 쪽을 이어 붙인다.
   * 화면에서 항목을 지우면 서버 목록도 한 칸씩 당겨지므로, 다음에 받을 쪽은 지금 화면에 있는 개수로 정하고
   * 그래서 다시 받게 되는 항목은 keyOf(고유 ID)로 걸러 낸다 (지운 뒤 "더 보기"에서 항목이 빠지거나 겹치지 않게) */
  function pager(url, keyOf) {
    var p = { items: [], total: 0 };
    p.next = function () {
      var page = Math.floor(p.items.length / PAGE_SIZE);
      return MB.api(url + (url.indexOf('?') < 0 ? '?' : '&') + 'page=' + page + '&size=' + PAGE_SIZE).then(function (res) {
        var data = res || {};
        var seen = {};
        p.items.forEach(function (it) { seen[keyOf(it)] = true; });
        p.items = p.items.concat((data.items || []).filter(function (it) { return !seen[keyOf(it)]; }));
        p.total = typeof data.totalElements === 'number' ? data.totalElements : p.items.length;
      });
    };
    p.hasMore = function () { return p.items.length < p.total; };
    return p;
  }

  function moreButton(p, redraw) {
    if (!p.hasMore()) return null;
    var btn = h('button', { type: 'button', class: 'btn btn-secondary ml-more', text: '더 보기' });
    btn.addEventListener('click', function () {
      btn.disabled = true;
      btn.textContent = '불러오는 중…';
      p.next().then(redraw, function (err) {
        MB.toast(errorText(err, '목록을 더 불러오지 못했어요.'), 'error');
        btn.disabled = false;
        btn.textContent = '더 보기';
      });
    });
    return btn;
  }

  /* 목록에서 하나를 지웠을 때: 화면 목록과 전체 개수를 함께 줄인다 */
  function dropItem(p, match) {
    var before = p.items.length;
    p.items = p.items.filter(function (it) { return !match(it); });
    if (p.items.length < before) p.total = Math.max(0, p.total - 1);
  }

  /* ---------- 퀴즈 오답노트 (API 44·45·46) ---------- */

  var wrong = { p: null, open: {}, picked: {}, result: {}, grading: {}, asking: {}, deleting: {} };

  function loadWrong() {
    var box = boxes.wrong;
    wrong.p = pager('/api/v1/members/me/wrong-notes', function (it) { return it.wrongNoteId; });
    fill(box, stateBox('오답노트를 불러오는 중이에요…'));
    wrong.p.next().then(drawWrong, function (err) {
      fill(box, stateBox(errorText(err, '오답노트를 불러오지 못했어요.'), loadWrong));
    });
  }

  function drawWrong() {
    var p = wrong.p;
    if (!p.items.length) {
      fill(boxes.wrong, emptyBox('틀린 문제가 없어요. 강의를 듣고 퀴즈를 풀어 보세요.', { href: '/user/lectures', text: '강의 보러 가기' }));
      return;
    }
    fill(boxes.wrong, [
      h('div', { class: 'ml-chips' }, [
        h('span', { class: 'ml-chip ml-chip--warn', text: '오답 ' + p.total.toLocaleString('ko-KR') + '문제' }),
        h('span', { class: 'ml-hint', text: '다시 풀어서 맞히면 오답노트에서 지울 수 있어요.' })
      ]),
      h('div', { class: 'ml-list' }, p.items.map(wrongCard)),
      moreButton(p, drawWrong)
    ]);
  }

  function wrongCard(n) {
    var id = n.wrongNoteId;
    var retrying = !!wrong.open[id];
    var result = wrong.result[id];
    var choices = n.choices || [];
    var kicker = [n.categoryName, n.courseTitle].filter(Boolean).join(' · ');

    var answer = retrying ? null : h('div', { class: 'ml-answer' }, [
      h('span', { class: 'ml-mine', text: '내 답: ' + (n.myChoice || '풀지 않았어요') }),
      h('span', { class: 'ml-correct', text: '정답: ' + (n.correctChoice || '-') })
    ]);

    var retry = null;
    if (retrying) {
      var correctId = result && result.correctChoiceId;
      retry = h('div', { class: 'ml-retry', role: 'group', 'aria-label': '다시 풀기 보기' }, [
        h('span', { class: 'ml-retry__title', text: result && result.isCorrect ? '정답이에요!' : '정답을 골라 보세요' }),
        choices.length ? h('div', { class: 'ml-choices' }, choices.map(function (c, i) {
          var picked = wrong.picked[id] === c.choiceId;
          var cls = 'ml-choice';
          if (result && result.isCorrect && c.choiceId === correctId) cls += ' is-correct';
          else if (result && !result.isCorrect && picked) cls += ' is-wrong';
          return h('button', { type: 'button', class: cls, 'aria-pressed': String(picked),
            disabled: wrong.grading[id] || (result && result.isCorrect),
            onclick: function () { gradeWrong(n, c.choiceId); } }, [
            h('b', { text: String(i + 1) }), c.content
          ]);
        })) : h('span', { class: 'ml-hint', text: '이 문제는 보기를 불러올 수 없어 다시 풀 수 없어요.' }),
        result && !result.isCorrect ? h('span', { class: 'ml-miss', role: 'status', text: '아쉬워요, 다시 골라 보세요.' }) : null,
        result && result.message ? h('span', { class: 'ml-miss', role: 'status', text: result.message }) : null,
        result && result.isCorrect && n.explanation ? h('p', { class: 'ml-why', text: n.explanation }) : null
      ]);
    }

    var asking = !!wrong.asking[id];
    var actions = h('div', { class: 'ml-actions' }, asking ? [
      h('span', { class: 'ml-hint', text: '이 문제를 오답노트에서 지울까요? 되돌릴 수 없어요.' }),
      h('button', { type: 'button', class: 'btn btn-primary ml-danger', text: wrong.deleting[id] ? '지우는 중…' : '지우기',
        disabled: wrong.deleting[id], onclick: function () { deleteWrong(n); } }),
      h('button', { type: 'button', class: 'btn btn-ghost', text: '취소', disabled: wrong.deleting[id],
        onclick: function () { delete wrong.asking[id]; drawWrong(); } })
    ] : [
      h('button', { type: 'button', class: 'btn btn-secondary', text: retrying ? '그만 풀기' : '다시 풀기',
        disabled: !retrying && !choices.length, onclick: function () { toggleRetry(id); } }),
      h('a', { class: 'btn btn-ghost', href: '/user/lecture-detail?courseId=' + n.courseId, text: '강의 보기' }),
      h('button', { type: 'button', class: 'btn btn-ghost ml-muted', text: '오답 지우기',
        onclick: function () { wrong.asking[id] = true; drawWrong(); } })
    ]);

    return h('article', { class: 'card ml-wrong' }, [
      h('div', { class: 'ml-row-head' }, [
        h('span', { class: 'ml-kicker', text: kicker }),
        h('span', { class: 'ml-date', text: day(n.createdAt) })
      ]),
      h('p', { class: 'ml-question', text: n.content }),
      answer,
      retrying ? null : (n.explanation ? h('p', { class: 'ml-why', text: n.explanation }) : null),
      retry,
      actions
    ]);
  }

  function toggleRetry(id) {
    if (wrong.open[id]) {
      delete wrong.open[id]; delete wrong.picked[id]; delete wrong.result[id];
    } else {
      wrong.open[id] = true;
    }
    drawWrong();
  }

  function gradeWrong(n, choiceId) {
    var id = n.wrongNoteId;
    if (wrong.grading[id]) return;
    wrong.grading[id] = true;
    wrong.picked[id] = choiceId;
    delete wrong.result[id];
    drawWrong();
    MB.api('/api/v1/members/me/wrong-notes/retry', {
      method: 'POST', body: { answers: [{ questionId: n.questionId, choiceId: choiceId }] }
    }).then(function (res) {
      var r = ((res && res.results) || []).find(function (x) { return x.questionId === n.questionId; });
      // 강의가 비공개가 되는 등으로 채점 대상에서 빠지면 결과가 비어서 온다
      wrong.result[id] = r || { isCorrect: false, message: '지금은 이 문제를 채점할 수 없어요. 강의가 비공개로 바뀌었을 수 있어요.' };
    }, function (err) {
      wrong.result[id] = { isCorrect: false, message: errorText(err, '채점하지 못했어요. 다시 골라 주세요.') };
    }).then(function () {
      delete wrong.grading[id];
      drawWrong();
    });
  }

  function deleteWrong(n) {
    var id = n.wrongNoteId;
    if (wrong.deleting[id]) return;
    wrong.deleting[id] = true;
    drawWrong();
    MB.api('/api/v1/members/me/wrong-notes/' + id, { method: 'DELETE' }).then(function () {
      dropItem(wrong.p, function (it) { return it.wrongNoteId === id; });
      MB.toast('오답노트에서 지웠어요.');
    }, function (err) {
      MB.toast(errorText(err, '오답을 지우지 못했어요.'), 'error');
    }).then(function () {
      delete wrong.deleting[id]; delete wrong.asking[id];
      drawWrong();
    });
  }

  /* ---------- 강의 스크랩 (API 38·37) ---------- */

  var scrap = { p: null, busy: {} };

  function loadScrap() {
    scrap.p = pager('/api/v1/members/me/scraps', function (it) { return it.courseId; });
    fill(boxes.scrap, stateBox('스크랩한 강의를 불러오는 중이에요…'));
    scrap.p.next().then(drawScrap, function (err) {
      fill(boxes.scrap, stateBox(errorText(err, '스크랩한 강의를 불러오지 못했어요.'), loadScrap));
    });
  }

  function drawScrap() {
    var p = scrap.p;
    if (!p.items.length) {
      fill(boxes.scrap, emptyBox('아직 스크랩한 강의가 없어요. 강의 상세에서 스크랩해 보세요.', { href: '/user/lectures', text: '강의 보러 가기' }));
      return;
    }
    fill(boxes.scrap, [
      h('section', { class: 'card ml-rows', 'aria-label': '스크랩한 강의 ' + p.total + '개' }, p.items.map(function (c) {
        var busy = !!scrap.busy[c.courseId];
        return h('div', { class: 'ml-row' }, [
          h('div', { class: 'ml-row__text' }, [
            h('span', { class: 'ml-kicker', text: c.categoryName || '' }),
            h('span', { class: 'ml-title', text: c.title }),
            h('span', { class: 'ml-meta', text: day(c.scrappedAt) + ' 스크랩' })
          ]),
          h('div', { class: 'ml-row__side' }, [
            h('a', { class: 'btn btn-secondary', href: '/user/lecture-detail?courseId=' + c.courseId, text: '강의 보기' }),
            h('button', { type: 'button', class: 'btn btn-ghost ml-muted', text: busy ? '해제하는 중…' : '스크랩 해제',
              disabled: busy, onclick: function () { unscrap(c); } })
          ])
        ]);
      })),
      moreButton(p, drawScrap)
    ]);
  }

  function unscrap(c) {
    if (scrap.busy[c.courseId]) return;
    scrap.busy[c.courseId] = true;
    drawScrap();
    MB.api('/api/v1/courses/' + c.courseId + '/scrap', { method: 'DELETE' }).then(function () {
      dropItem(scrap.p, function (it) { return it.courseId === c.courseId; });
      MB.toast('스크랩을 해제했어요.');
    }, function (err) {
      MB.toast(errorText(err, '스크랩을 해제하지 못했어요.'), 'error');
    }).then(function () {
      delete scrap.busy[c.courseId];
      drawScrap();
    });
  }

  /* ---------- 학습 기록 (API 34·35) ---------- */

  var hist = { watch: null, done: null };

  function loadHistory() {
    hist.watch = pager('/api/v1/members/me/watch-histories', function (it) { return it.lessonId; });
    hist.done = pager('/api/v1/members/me/completed-courses', function (it) { return it.courseId; });
    fill(boxes.learning, stateBox('학습 기록을 불러오는 중이에요…'));
    Promise.all([hist.watch.next(), hist.done.next()]).then(drawHistory, function (err) {
      fill(boxes.learning, stateBox(errorText(err, '학습 기록을 불러오지 못했어요.'), loadHistory));
    });
  }

  function drawHistory() {
    var w = hist.watch, d = hist.done;
    if (!w.items.length && !d.items.length) {
      fill(boxes.learning, emptyBox('아직 본 강의가 없어요. 첫 강의를 시작해 보세요.', { href: '/user/lectures', text: '강의 보러 가기' }));
      return;
    }
    fill(boxes.learning, [
      h('h2', { class: 'ml-h2', text: '최근 본 영상' }),
      w.items.length ? h('section', { class: 'card ml-rows', 'aria-label': '최근 본 영상' }, w.items.map(function (r) {
        return h('div', { class: 'ml-row' }, [
          h('div', { class: 'ml-row__text' }, [
            h('span', { class: 'ml-kicker', text: r.courseTitle || '' }),
            h('span', { class: 'ml-title', text: r.lessonTitle }),
            h('span', { class: 'ml-meta', text: day(r.lastWatchedAt) + ' 시청' })
          ]),
          h('div', { class: 'ml-row__side' }, [
            r.isCompleted ? badge('시청 완료', 'done') : badge('시청 중', 'doing'),
            h('a', { class: 'btn btn-secondary', href: '/user/lecture-detail?courseId=' + r.courseId + '&lessonId=' + r.lessonId,
              text: r.isCompleted ? '다시 보기' : '이어보기' })
          ])
        ]);
      })) : h('div', { class: 'card ml-state', text: '최근 본 영상이 없어요.' }),
      moreButton(w, drawHistory),
      h('h2', { class: 'ml-h2', text: '학습 완료한 강의' }),
      d.items.length ? h('section', { class: 'card ml-rows', 'aria-label': '학습 완료한 강의' }, d.items.map(function (c) {
        return h('div', { class: 'ml-row' }, [
          h('div', { class: 'ml-row__text' }, [
            h('span', { class: 'ml-title', text: c.title }),
            h('span', { class: 'ml-meta', text: day(c.completedAt) + ' 학습 완료' })
          ]),
          h('div', { class: 'ml-row__side' }, [
            badge('학습 완료', 'done'),
            h('a', { class: 'btn btn-secondary', href: '/user/lecture-detail?courseId=' + c.courseId, text: '강의 보기' })
          ])
        ]);
      })) : h('div', { class: 'card ml-state', text: '아직 학습을 완료한 강의가 없어요. 영상을 다 보고 퀴즈를 통과하면 완료돼요.' }),
      moreButton(d, drawHistory)
    ]);
  }

  /* ---------- Q&A·문의 모음 (API 149·49) ---------- */

  var qna = { p: null, open: {}, details: {}, loading: {}, errors: {} };
  var STATUS = { WAITING: ['답변 대기', 'doing'], ANSWERED: ['답변 완료', 'done'] };

  function loadQna() {
    qna.p = pager('/api/v1/members/me/inquiries', function (it) { return it.inquiryId; });
    fill(boxes.qna, stateBox('내가 쓴 문의를 불러오는 중이에요…'));
    qna.p.next().then(drawQna, function (err) {
      fill(boxes.qna, stateBox(errorText(err, '내가 쓴 문의를 불러오지 못했어요.'), loadQna));
    });
  }

  function drawQna() {
    var p = qna.p;
    fill(boxes.qna, [
      h('div', { class: 'card ml-qna-head' }, [
        h('div', { class: 'ml-qna-head__row' }, [
          h('span', { class: 'ml-qna-head__title', text: 'Q&A·문의 글 모음 · ' + p.total.toLocaleString('ko-KR') + '건' }),
          h('a', { class: 'btn btn-primary', href: '/user/inquiry', text: '문의 게시판 가기' })
        ]),
        h('span', { class: 'ml-hint', text: '내가 남긴 강의 Q&A와 문의 게시판 글을 한곳에서 모아 봐요. 새 문의는 문의 게시판이나 강의 상세의 "강의 문의" 탭에서 쓸 수 있어요.' })
      ]),
      p.items.length ? h('div', { class: 'card ml-qna-list' }, p.items.map(qnaItem))
        : h('div', { class: 'card ml-state', text: '아직 남긴 문의가 없어요.' }),
      moreButton(p, drawQna)
    ]);
  }

  function qnaItem(q) {
    var id = q.inquiryId;
    var st = q.isHidden ? ['숨김', 'hidden'] : (STATUS[q.status] || [q.status, 'doing']);
    var open = !!qna.open[id];
    var detailId = 'ml-qna-detail-' + id;
    return h('article', { class: 'ml-qna' }, [
      h('div', { class: 'ml-qna__tags' }, [
        badge(st[0], st[1]),
        h('span', { class: 'ml-src', text: q.courseId ? '강의 Q&A' : '문의 게시판' }),
        h('span', { class: 'ml-date', text: day(q.createdAt) })
      ]),
      h('span', { class: 'ml-title', text: q.title }),
      q.courseId ? h('span', { class: 'ml-meta', text: '강의 · ' + (q.courseTitle || '지금은 볼 수 없는 강의예요') }) : null,
      open ? h('div', { class: 'ml-qna__detail', id: detailId }, qnaDetail(id)) : null,
      h('div', { class: 'ml-actions' }, [
        h('button', { type: 'button', class: 'btn btn-ghost', 'aria-expanded': String(open), 'aria-controls': open ? detailId : null,
          text: open ? '접기' : '내용·답변 보기', onclick: function () { toggleQna(id); } }),
        h('a', { class: 'btn btn-ghost', href: '/user/inquiry/' + id, text: '문의 게시판에서 보기' }),
        q.courseTitle ? h('a', { class: 'btn btn-secondary ml-push', href: '/user/lecture-detail?courseId=' + q.courseId, text: '해당 강의 보러 가기' }) : null
      ])
    ]);
  }

  function qnaDetail(id) {
    if (qna.loading[id]) return [h('span', { class: 'ml-hint', text: '내용을 불러오는 중이에요…' })];
    if (qna.errors[id]) {
      return [h('span', { class: 'ml-miss', role: 'alert', text: qna.errors[id] }),
        h('button', { type: 'button', class: 'btn btn-secondary', text: '다시 불러오기', onclick: function () { fetchQna(id); } })];
    }
    var d = qna.details[id];
    if (!d) return [];
    var replies = d.replies || [];
    return [
      h('p', { class: 'ml-qna__body', text: d.content || '' }),
      replies.length ? h('div', { class: 'ml-replies' }, replies.map(function (r) {
        var w = r.writer || {};
        var by = w.role === 'INSTRUCTOR' ? '강사 ' + (w.nickname || '') : w.role === 'ADMIN' ? '관리자' : (w.nickname || '나');
        return h('div', { class: 'ml-reply' }, [h('b', { text: by + ' · ' + day(r.createdAt) }), h('span', { text: r.content })]);
      })) : h('span', { class: 'ml-hint', text: '아직 답변이 없어요. 답변이 등록되면 알림으로 알려 드려요.' })
    ];
  }

  function toggleQna(id) {
    if (qna.open[id]) { delete qna.open[id]; drawQna(); return; }
    qna.open[id] = true;
    if (qna.details[id]) drawQna();
    else fetchQna(id);
  }

  function fetchQna(id) {
    qna.loading[id] = true;
    delete qna.errors[id];
    drawQna();
    MB.api('/api/v1/inquiries/' + id).then(function (d) {
      qna.details[id] = d || {};
    }, function (err) {
      qna.errors[id] = errorText(err, '내용을 불러오지 못했어요.');
    }).then(function () {
      delete qna.loading[id];
      drawQna();
    });
  }

  /* ---------- 탭 열기 ---------- */

  function open(tab) {
    if (!loaders[tab] || !boxes[tab] || started[tab]) return;
    started[tab] = true;
    loaders[tab]();
  }

  document.addEventListener('mypage:tab', function (e) { open(e.detail && e.detail.tab); });

  // #tab-wrong 처럼 이슈에서 약속한 해시로 들어오면 틀의 탭 버튼을 눌러 그 탭으로 옮긴다 (틀은 ?tab=·#wrong 만 안다)
  // 틀이 첫 탭을 열면서 주소를 ?tab= 으로 바꿔 해시가 지워지므로, 처음 해시는 이 스크립트가 돌 때(틀의 첫 이벤트 전) 기억해 둔다
  var firstHash = location.hash;
  function followHash(hash) {
    var tab = HASH_TO_TAB[String(hash || location.hash).replace('#', '')];
    if (!tab) return;
    var btn = document.querySelector('.mp-tabs [data-tab="' + tab + '"]');
    if (btn) btn.click();
  }
  window.addEventListener('hashchange', function () { followHash(); });
  // 틀이 첫 탭을 연 다음(첫 'mypage:tab' 이벤트 뒤)에 옮겨야 틀의 첫 탭 열기에 덮이지 않는다
  if (window.MyPage && window.MyPage.current) followHash(firstHash);
  else {
    var onFirst = function () {
      document.removeEventListener('mypage:tab', onFirst);
      setTimeout(function () { followHash(firstHash); }, 0);
    };
    document.addEventListener('mypage:tab', onFirst);
  }

  // 이 스크립트가 틀의 첫 이벤트보다 늦게 준비된 경우에도 지금 열린 탭을 불러온다
  if (window.MyPage && window.MyPage.current) open(window.MyPage.current);
})();
