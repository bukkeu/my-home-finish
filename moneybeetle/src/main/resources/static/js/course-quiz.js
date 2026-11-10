/* comment.
*   담당 : DBA ① 신아림 (#53, 강의 상세 안의 테스트 퀴즈·최종 퀴즈·결과)
*   강의 상세(course-detail.js)가 부르는 퀴즈 화면 그리기 (window.MBLecQuiz)
*   - renderTest(box, opts) : 시청 전 OX 테스트 (API 39·40). 점수·경험치·오답노트에 영향 없음, 횟수 제한 없음
*                             모든 문제에 답하고 '정답 확인'을 누르면(lessonsUnlocked) opts.onUnlocked() 로 영상을 연다
*   - renderQuiz(box, opts) : 최종 4지선다 퀴즈 (API 41·42). 영상을 모두 본 뒤에만 열린다(서버가 QUIZ_LOCKED 로 막는다)
*                             제출은 Idempotency-Key 를 붙여 두 번 눌러도 한 번만 채점된다. 결과(점수·해설·보상) 뒤 opts.onResult(result)
*   정답·해설은 채점 응답에만 있다 (문제 응답에는 없음, SP-35)
*  */
(function () {
  if (window.MBLecQuiz || !window.MBLec) return;
  var L = window.MBLec;
  var h = L.h;

  /* 자식 요소를 바꿔 넣는다. 배열은 펼치고 null·false 는 건너뛴다 (replaceChildren 은 null 을 "null" 글자로 넣는다) */
  function fill(el) {
    var nodes = [];
    [].slice.call(arguments, 1).forEach(function add(x) {
      if (Array.isArray(x)) x.forEach(add);
      else if (x !== null && x !== undefined && x !== false) nodes.push(x);
    });
    el.replaceChildren.apply(el, nodes);
  }

  var UNLOCK_NAMES = { TRADING: '모의투자', HOUSING: '모의청약' };

  function newKey() {
    if (window.crypto && crypto.randomUUID) return crypto.randomUUID();
    return 'q-' + Date.now() + '-' + Math.random().toString(36).slice(2, 10);
  }

  function choiceText(question, choiceId) {
    var c = (question.choices || []).find(function (x) { return x.choiceId === choiceId; });
    return c ? c.content : '답하지 않음';
  }

  function loading(box, text) {
    fill(box, h('div', { class: 'lec-state', text: text }));
  }

  /* ---------- 시청 전 테스트 (OX 5문제) ---------- */

  function renderTest(box, opts) {
    var courseId = opts.courseId;
    var st = { questions: [], picks: {}, result: null, checking: false };

    function load() {
      loading(box, '테스트 문제를 불러오는 중이에요…');
      MB.api('/api/v1/courses/' + courseId + '/test-quiz').then(function (res) {
        st.questions = (res && res.questions) || [];
        if (!st.questions.length) {
          // 강사가 테스트를 아직 안 만든 강의는 막지 않고 바로 영상을 연다
          opts.onUnlocked();
          fill(box, h('div', { class: 'lec-state' }, [
            '이 강의에는 시청 전 테스트가 없어요.', h('br'),
            h('button', { type: 'button', class: 'btn btn-primary', text: '영상 보러 가기', onclick: opts.onGoLessons })
          ]));
          return;
        }
        draw();
      }).catch(function (err) { fill(box, L.errorBox(err, load)); });
    }

    function answeredCount() {
      return st.questions.filter(function (q) { return st.picks[q.questionId]; }).length;
    }

    function check() {
      if (st.checking || answeredCount() < st.questions.length) return;
      st.checking = true;
      draw();
      MB.api('/api/v1/courses/' + courseId + '/test-quiz/grade', {
        method: 'POST',
        body: { answers: st.questions.map(function (q) { return { questionId: q.questionId, choiceId: st.picks[q.questionId] || null }; }) }
      }).then(function (res) {
        st.result = res;
        if (res && res.lessonsUnlocked) opts.onUnlocked();
      }).catch(function (err) {
        MB.toast((err && err.message) || '채점하지 못했어요. 다시 시도해 주세요.', 'error');
      }).then(function () { st.checking = false; draw(); });
    }

    function reset() {
      st.picks = {};
      st.result = null;
      draw();
    }

    function draw() {
      var byId = {};
      ((st.result && st.result.results) || []).forEach(function (r) { byId[r.questionId] = r; });
      var all = answeredCount() === st.questions.length;

      var rows = st.questions.map(function (q, i) {
        var r = byId[q.questionId];
        return h('div', { class: 'lec-ox' + (r ? (r.isCorrect ? ' is-correct' : ' is-wrong') : '') }, [
          h('div', { class: 'lec-ox__row' }, [
            h('span', { class: 'lec-quiz__no', text: String(i + 1) }),
            h('span', { class: 'lec-ox__q', text: q.content }),
            h('div', { class: 'lec-ox__picks', role: 'group', 'aria-label': (i + 1) + '번 답' }, (q.choices || []).map(function (c) {
              return h('button', { type: 'button', class: 'lec-ox__pick', 'aria-pressed': String(st.picks[q.questionId] === c.choiceId),
                disabled: !!st.result || st.checking, text: c.content,
                onclick: function () { st.picks[q.questionId] = c.choiceId; draw(); } });
            }))
          ]),
          r ? h('p', { class: 'lec-ox__exp' }, [
            h('b', { text: r.isCorrect ? '정답이에요' : '아쉬워요 · 정답은 ' + choiceText(q, r.correctChoiceId) }),
            r.explanation ? ' · ' + r.explanation : null
          ]) : null
        ]);
      });

      var unlocked = st.result && st.result.lessonsUnlocked;
      fill(box, 
        h('div', { class: 'lec-quiz__head' }, [
          h('span', { class: 'lec-quiz__chip lec-quiz__chip--test', text: '테스트 퀴즈' }),
          h('h2', { text: 'OX ' + st.questions.length + '문제' })
        ]),
        h('p', { class: 'lec-quiz__info' }, [
          '영상을 보기 전에 가볍게 풀어 보는 문제예요. ',
          h('b', { text: '모두 답하고 \'정답 확인\'을 누르면 영상이 열리고, 영상을 모두 보면 최종 퀴즈가 열려요.' }),
          ' 틀려도 점수·경험치·오답노트에 영향이 없고, 몇 번이든 다시 풀 수 있어요.'
        ]),
        h('div', { class: 'lec-quiz__list' }, rows),
        h('div', { class: 'lec-quiz__actions' }, [
          st.result
            ? h('button', { type: 'button', class: 'btn btn-secondary', text: '다시 풀기', onclick: reset })
            : h('button', { type: 'button', class: 'btn btn-primary', disabled: !all || st.checking,
              text: st.checking ? '채점하는 중…' : '정답 확인', onclick: check }),
          h('span', { class: 'lec-quiz__status', text: st.result
            ? st.questions.length + '문제 중 ' + st.result.correctCount + '개 맞혔어요'
            : answeredCount() + ' / ' + st.questions.length + ' 답함' }),
          unlocked ? h('button', { type: 'button', class: 'btn btn-primary lec-quiz__go', text: '영상 보러 가기 ›', onclick: opts.onGoLessons }) : null
        ])
      );
    }

    load();
  }

  /* ---------- 최종 퀴즈 (4지선다) ---------- */

  function renderQuiz(box, opts) {
    var courseId = opts.courseId;
    var st = { paper: null, picks: {}, index: 0, key: null, submitting: false, result: null };

    function head(count, passScore) {
      return h('div', { class: 'lec-quiz__head' }, [
        h('span', { class: 'lec-quiz__chip', text: '최종 퀴즈' }),
        h('h2', { text: count ? '4지선다 ' + count + '문제' : '4지선다 퀴즈' }),
        h('span', { class: 'lec-meta', text: (passScore || 70) + '점 이상이면 통과 · 학습 완료' })
      ]);
    }

    function locked(left) {
      fill(box, head(0), h('div', { class: 'lec-quiz__locked' }, [
        h('span', { class: 'lec-quiz__lock', 'aria-hidden': 'true', text: '🔒' }),
        h('b', { text: '영상을 모두 본 뒤에 풀 수 있어요' }),
        left > 0 ? h('span', { class: 'lec-meta', text: '남은 영상 ' + left + '개' }) : null,
        h('button', { type: 'button', class: 'btn btn-primary', text: '남은 영상 보러 가기', onclick: opts.onGoLessons })
      ]));
    }

    function load() {
      if (opts.leftLessons() > 0) { locked(opts.leftLessons()); return; }
      loading(box, '퀴즈를 불러오는 중이에요…');
      MB.api('/api/v1/courses/' + courseId + '/quiz').then(function (paper) {
        st.paper = paper;
        st.picks = {};
        st.index = 0;
        st.result = null;
        st.key = newKey();   // 이 문제지 한 번 제출에 쓰는 키 (두 번 눌러도 한 번만 채점)
        drawTaking();
      }).catch(function (err) {
        if (err && err.code === 'QUIZ_LOCKED') { locked(opts.leftLessons()); return; }
        if (err && err.code === 'NOT_FOUND') {
          fill(box, head(0), h('div', { class: 'lec-state', text: '아직 퀴즈 문제가 준비되지 않았어요. 강사가 문제를 올리면 풀 수 있어요.' }));
          return;
        }
        fill(box, head(0), L.errorBox(err, load));
      });
    }

    function questions() { return (st.paper && st.paper.questions) || []; }

    function answered() {
      return questions().filter(function (q) { return st.picks[q.questionId]; }).length;
    }

    function go(i) {
      st.index = Math.max(0, Math.min(questions().length - 1, i));
      drawTaking();
    }

    function drawTaking() {
      var qs = questions();
      var q = qs[st.index];
      var all = answered() === qs.length;
      fill(box, 
        head(qs.length, st.paper.passScore),
        h('nav', { class: 'lec-quiz__dots', 'aria-label': '문제 이동' }, qs.map(function (x, i) {
          return h('button', { type: 'button', class: 'lec-quiz__dot' + (st.picks[x.questionId] ? ' is-answered' : ''),
            'aria-current': i === st.index ? 'step' : null, 'aria-label': (i + 1) + '번 문제' + (st.picks[x.questionId] ? ' (답함)' : ''),
            text: String(i + 1), onclick: function () { go(i); } });
        })),
        h('div', { class: 'lec-quiz__q' }, [
          h('span', { class: 'lec-summary__title', text: '문제 ' + (st.index + 1) + ' / ' + qs.length }),
          h('p', { class: 'lec-quiz__text', text: q.content })
        ]),
        h('div', { class: 'lec-quiz__choices', role: 'radiogroup', 'aria-label': (st.index + 1) + '번 보기' }, (q.choices || []).map(function (c, i) {
          var on = st.picks[q.questionId] === c.choiceId;
          return h('button', { type: 'button', role: 'radio', class: 'lec-choice', 'aria-checked': String(on), disabled: st.submitting,
            onclick: function () {
              st.picks[q.questionId] = c.choiceId;
              if (st.index < qs.length - 1) go(st.index + 1); else drawTaking();   // 고르면 다음 문제로
            } }, [
            h('span', { class: 'lec-choice__mark', text: on ? '✓' : String(i + 1) }),
            h('span', { text: c.content })
          ]);
        })),
        h('div', { class: 'lec-quiz__actions' }, [
          h('button', { type: 'button', class: 'btn btn-secondary', disabled: st.index === 0, text: '‹ 이전 문제', onclick: function () { go(st.index - 1); } }),
          h('button', { type: 'button', class: 'btn btn-secondary', disabled: st.index === qs.length - 1, text: '다음 문제 ›', onclick: function () { go(st.index + 1); } }),
          h('span', { class: 'lec-quiz__status', text: answered() + ' / ' + qs.length + ' 답함' + (all ? '' : ' · 모두 답하면 제출할 수 있어요') }),
          h('button', { type: 'button', class: 'btn btn-primary lec-quiz__go', disabled: !all || st.submitting,
            text: st.submitting ? '채점하는 중…' : '제출하기', onclick: submit })
        ]),
        h('p', { class: 'lec-player-note', text: '제출하기 전에 이 화면을 나가면 기록되지 않고, 다시 들어오면 처음부터 시작해요.' })
      );
    }

    function submit() {
      if (st.submitting || answered() < questions().length) return;
      st.submitting = true;
      drawTaking();
      MB.api('/api/v1/courses/' + courseId + '/quiz/attempts', {
        method: 'POST',
        headers: { 'Idempotency-Key': st.key },
        body: { answers: questions().map(function (q) { return { questionId: q.questionId, choiceId: st.picks[q.questionId] || null }; }) }
      }).then(function (result) {
        st.result = result;
        st.submitting = false;
        drawResult();
        opts.onResult(result);
      }).catch(function (err) {
        st.submitting = false;
        if (err && err.code === 'QUIZ_LOCKED') { locked(opts.leftLessons()); return; }
        // 처리 중(DUPLICATE_REQUEST)·네트워크 오류는 같은 키로 다시 누르면 처음 결과를 받는다
        MB.toast((err && err.message) || '제출하지 못했어요. 다시 눌러 주세요.', 'error');
        drawTaking();
      });
    }

    function rewardLine(rw) {
      if (!rw || !rw.firstPass) return null;
      var parts = [];
      if (rw.exp) parts.push('경험치 +' + rw.exp);
      if (rw.cash) parts.push('캐시 +' + Number(rw.cash).toLocaleString('ko-KR'));
      var lines = [h('b', { text: '학습 완료 보상 · ' + (parts.join(' · ') || '지급 완료') })];
      if (rw.levelUp) {
        var lv = rw.levelUp;
        lines.push(h('span', { text: '레벨업! Lv.' + lv.from + ' → Lv.' + lv.to + (lv.title ? ' ' + lv.title : '')
          + (lv.levelupCash ? ' · 레벨업 캐시 +' + Number(lv.levelupCash).toLocaleString('ko-KR') : '') }));
        var names = (lv.unlocked || []).map(function (u) { return UNLOCK_NAMES[u] || u; });
        if (names.length) lines.push(h('span', { text: '새로 열린 기능: ' + names.join(', ') }));
      }
      return h('div', { class: 'lec-quiz__reward' }, lines);
    }

    function drawResult() {
      var r = st.result;
      var qById = {};
      questions().forEach(function (q, i) { qById[q.questionId] = { q: q, no: i + 1 }; });
      var wrong = (r.results || []).filter(function (x) { return !x.isCorrect; });
      var passScore = (st.paper && st.paper.passScore) || 70;
      var firstPass = r.rewards && r.rewards.firstPass;

      fill(box, 
        head(r.questionCount, passScore),
        h('div', { class: 'lec-quiz__score' + (r.isPassed ? ' is-passed' : '') }, [
          h('span', { class: 'lec-quiz__points' }, [String(r.score), h('small', { text: '점' })]),
          h('div', { class: 'lec-quiz__verdict' }, [
            h('b', { text: r.isPassed ? (firstPass ? '통과했어요! 학습 완료' : '통과했어요') : '아쉽게 통과하지 못했어요' }),
            h('span', { text: r.questionCount + '문제 중 ' + r.correctCount + '개 맞혔어요 · '
              + (r.isPassed ? (firstPass ? '경험치와 캐시를 받았어요' : '보상은 처음 통과할 때 한 번만 받아요')
                : passScore + '점 이상이면 통과예요. 횟수 제한 없이 다시 풀 수 있어요') })
          ])
        ]),
        rewardLine(r.rewards),
        wrong.length ? h('div', { class: 'lec-quiz__list' }, [
          h('b', { class: 'lec-quiz__sub', text: '틀린 문제 · 오답노트에 저장돼요 (같은 문제는 한 번만)' })
        ].concat(wrong.map(function (w) {
          var info = qById[w.questionId] || { q: { content: '', choices: [] }, no: '' };
          return h('div', { class: 'lec-wrong' }, [
            h('span', { class: 'lec-wrong__q', text: info.no + '. ' + info.q.content }),
            h('div', { class: 'lec-wrong__pair' }, [
              h('div', { class: 'lec-wrong__mine' }, [h('small', { text: '✕ 내 답' }), h('span', { text: choiceText(info.q, st.picks[w.questionId]) })]),
              h('div', { class: 'lec-wrong__right' }, [h('small', { text: '✓ 정답' }), h('span', { text: choiceText(info.q, w.correctChoiceId) })])
            ]),
            w.explanation ? h('p', { class: 'lec-wrong__exp' }, [h('b', { text: '해설 ' }), w.explanation]) : null
          ]);
        }))) : h('p', { class: 'lec-meta', text: '모두 맞혔어요!' }),
        h('div', { class: 'lec-quiz__actions' }, [
          h('button', { type: 'button', class: 'btn btn-primary', text: '다시 풀기', onclick: load }),
          h('a', { class: 'btn btn-secondary', href: opts.backHref(), text: '강의 목록으로' })
        ])
      );
    }

    load();
  }

  window.MBLecQuiz = { renderTest: renderTest, renderQuiz: renderQuiz };
})();
