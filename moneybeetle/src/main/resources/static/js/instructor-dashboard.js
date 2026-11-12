/* comment.
*   담당 : DBA ① 신아림 (#59 강사 대시보드 /instructor/dashboard?tab=, #373 목업과 똑같이)
*   원본 : InstructorDashboard.dc.html 의 dashboard · courses · quizzes · summaries 보기
*   탭(사이드바 강사 메뉴와 같은 주소)
*   - dashboard : 숫자 카드 4개(강의 · 퀴즈 · 요약글 · 답변 대기 Q&A), 강의 생성 · 퀴즈 생성 · 요약글 작성, 최근 등록한 콘텐츠, 최근 질문
*   - courses   : 상태 탭(전체 · 공개 · 비공개 · 임시 저장 + 검수 중 · 반려가 있을 때) + 제목 검색, 표(카테고리 · 난이도 · 구성 · 공개 스위치 · 수정/삭제)
*   - quizzes   : 강의별 4지선다 퀴즈 표 + 퀴즈 생성·수정 화면(원본 quizForm, API 80). 퀴즈 제목은 '강의명 퀴즈'(저장할 곳이 없음)
*   - summaries : 영상별 요약글 카드 + 요약글 쓰기·고치기·지우기(API 152)
*   - qna · mypage : instructor-qna.js · instructor-mypage.js 가 그린다 (#360, window.MBIns.tabs)
*   쓰는 API : 72 내 강의(#369 카테고리·난이도·구성·등록일), 76 삭제, 76-1 공개 켜기/끄기, 82 내 강의 문의(#370 학생 이름),
*             150 퀴즈 목록 · 151 요약글 목록 · 152 요약글 고치기(#366), 75 강의 상세(퀴즈 불러오기 · 요약글 쓸 영상 고르기), 80 퀴즈 저장
*   - #366 이 아직 서버에 없으면(404·405) 퀴즈·요약글은 '준비 중', 숫자는 '-'
*   - 본인 강의와 그 강의의 문의만 보인다 (서버가 로그인한 강사 기준으로 거른다, TCH-06~08)
*   - 원본은 화면 제목이 보이지 않아(머리 영역이 가려짐) 같게 했고, 화면 읽기용 제목만 숨겨서 넣는다
*  */
(function () {
  var root = document.querySelector('[data-ins-dashboard]');
  var I = window.MBIns;
  if (!root || !window.MB) return;

  var TAB = root.getAttribute('data-tab') || 'dashboard';
  var CREATE_URL = '/instructor/course-create';
  var LEVEL = { BEGINNER: '입문', ELEMENTARY: '초급', INTERMEDIATE: '중급', ADVANCED: '고급' };
  var TITLES = { dashboard: '대시보드', courses: '강의 관리', quizzes: '퀴즈 관리', summaries: '요약글 관리' };
  var SUMMARY_MAX = 2000;
  var params = new URLSearchParams(location.search);

  if (!I) {   // 공통 부품(instructor-ui.js)을 못 불러왔으면 빈 화면 대신 안내
    root.textContent = '화면을 불러오지 못했어요. 새로고침해 주세요.';
    return;
  }
  var h = I.h, fill = I.fill;

  /* ---------- 상태 이름 · 색 (원본 stc 와 같다) ---------- */

  function courseState(c) {
    if (c.status === 'APPROVED') return c.isPublic ? '공개' : '비공개';
    return { DRAFT: '임시 저장', REVIEW: '검수 중', REJECTED: '반려' }[c.status] || c.status;
  }

  function pill(text, big) {
    var cls = text === '공개' || text === '게시' || text === '답변 완료' ? 'db-pill--on'
      : text === '임시 저장' ? 'db-pill--draft'
        : text === '답변 대기' ? 'db-pill--wait'
          : text === '반려' ? 'db-pill--bad'
            : text === '등록' ? 'db-pill--plain' : '';
    return h('span', { class: 'db-pill ' + cls + (big ? ' db-pill--big' : ''), text: text });
  }

  /* 썸네일이 없을 때 첫 글자 칸 색 (원본 tones : 카테고리마다) */
  function tone(name) {
    var n = name || '';
    if (/부동산|전·월세|대출/.test(n)) return 'db-tone--dark';
    if (/청약|ETF/.test(n)) return 'db-tone--mid';
    if (/예금|적금/.test(n)) return 'db-tone--lime';
    return '';
  }

  function minutes(sec) { return Math.round((Number(sec) || 0) / 60); }

  function errorBox(err, retry) {
    return h('div', { class: 'ins-state ins-state--error', role: 'alert' }, [
      I.errorText(err, '불러오지 못했어요.'), h('br'),
      h('button', { type: 'button', class: 'btn btn-secondary', text: '다시 시도', onclick: retry })
    ]);
  }

  /* #366 처럼 아직 없을 수 있는 API : 404·405 면 { missing: true } */
  function optional(promise) {
    return promise.then(function (v) { return { ok: true, value: v }; }, function (err) {
      if (err && (err.status === 404 || err.status === 405)) return { ok: false, missing: true };
      throw err;
    });
  }

  function items(res) { return (res && res.ok && res.value && res.value.items) || []; }

  function srTitle() { return h('h1', { class: 'mp-sr', text: TITLES[TAB] || '강사 센터' }); }

  /* ---------- 시작 ---------- */

  function start() {
    var tab = I.tabs[TAB];   // Q&A·마이페이지는 따로 된 파일이 그린다 (#360)
    if (tab) { tab(root); return; }
    if (TAB === 'qna' || TAB === 'mypage') {   // 그 탭 파일을 못 불러왔으면 다른 화면을 대신 보여 주지 않는다
      fill(root, h('div', { class: 'ins-state ins-state--error', role: 'alert', text: '화면을 불러오지 못했어요. 새로고침해 주세요.' }));
      return;
    }
    fill(root, srTitle(), h('div', { class: 'ins-state', text: '강사 센터를 불러오는 중이에요…' }));
    ({ courses: drawCourses, quizzes: drawQuizzes, summaries: drawSummaries }[TAB] || drawDashboard)();
  }

  /* ---------- 대시보드 ---------- */

  function drawDashboard() {
    Promise.all([
      MB.api('/api/v1/instructor/courses'),
      optional(MB.api('/api/v1/instructor/quizzes')),
      optional(MB.api('/api/v1/instructor/summaries')),
      MB.api('/api/v1/instructor/inquiries?page=0&size=4')
    ]).then(function (r) {
      var cs = (r[0] && r[0].items) || [];
      var qz = items(r[1]), sm = items(r[2]);
      var inbox = r[3] || { waitingCount: 0, items: [] };
      var count = function (name) { return cs.filter(function (c) { return courseState(c) === name; }).length; };
      var waiting = inbox.waitingCount || 0;
      var stats = [
        { label: '전체 등록 강의', value: cs.length, sub: '공개 ' + count('공개') + ' · 비공개 ' + count('비공개') + ' · 임시 저장 ' + count('임시 저장'), href: '?tab=courses' },
        { label: '등록한 퀴즈', value: r[1].ok ? qz.length : '-',
          sub: r[1].ok ? '총 ' + qz.reduce(function (a, q) { return a + (q.testCount || 0) + (q.mainCount || 0); }, 0) + '문항' : '준비 중이에요', href: '?tab=quizzes' },
        { label: '작성한 요약글', value: r[2].ok ? sm.length : '-',
          sub: r[2].ok ? '게시 ' + sm.filter(function (m) { return m.published; }).length + ' · 임시 저장 ' + sm.filter(function (m) { return !m.published; }).length : '준비 중이에요',
          href: '?tab=summaries' },
        { label: '답변 대기 Q&A', value: waiting, sub: waiting ? '답변을 기다리는 질문이 있어요' : '모든 질문에 답변했어요', href: '?tab=qna', dark: waiting > 0 }
      ];

      var recent = cs.map(function (c) { return { type: '강의', title: c.title, at: c.createdAt, status: courseState(c), href: '?tab=courses' }; })
        .concat(qz.map(function (q) { return { type: '퀴즈', title: q.courseTitle + ' 퀴즈', at: q.updatedAt, status: '등록', href: '?tab=quizzes' }; }))
        .concat(sm.map(function (m) { return { type: '요약글', title: m.lessonTitle, at: m.updatedAt, status: m.published ? '게시' : '임시 저장', href: '?tab=summaries' }; }))
        .sort(function (a, b) { return String(b.at || '').localeCompare(String(a.at || '')); })
        .slice(0, 5);

      fill(root,
        srTitle(),
        h('section', { class: 'db-stats', 'aria-label': '요약' }, stats.map(function (s) {
          return h('a', { class: 'db-stat' + (s.dark ? ' db-stat--dark' : ''), href: s.href }, [
            h('span', { class: 'db-stat__label', text: s.label }),
            h('span', { class: 'db-stat__value', text: String(s.value) }),
            h('span', { class: 'db-stat__sub', text: s.sub })
          ]);
        })),
        h('section', { class: 'db-quick', 'aria-label': '바로가기' }, [
          h('a', { class: 'db-btn db-btn--primary', href: CREATE_URL, text: '강의 생성' }),
          h('a', { class: 'db-btn', href: '?tab=quizzes&new=1', text: '퀴즈 생성' }),
          h('a', { class: 'db-btn', href: '?tab=summaries&new=1', text: '요약글 작성' })
        ]),
        h('section', { class: 'db-two' }, [
          h('div', { class: 'db-box db-recent' }, [
            h('h2', { class: 'db-h2', text: '최근 등록한 콘텐츠' }),
            recent.length ? recent.map(function (x) {
              return h('a', { class: 'db-line', href: x.href }, [
                h('span', { class: 'db-type' + (x.type === '강의' ? ' db-type--course' : ''), text: x.type }),
                h('span', { class: 'db-line__text' }, [h('span', { class: 'db-line__title', text: x.title }), h('span', { class: 'db-line__meta', text: I.date(x.at) })]),
                pill(x.status)
              ]);
            }) : h('p', { class: 'db-empty-line', text: '아직 등록한 콘텐츠가 없어요' })
          ]),
          h('div', { class: 'db-box db-recent' }, [
            h('div', { class: 'db-box__head' }, [h('h2', { class: 'db-h2', text: '최근 질문' }), h('a', { class: 'db-more', href: '?tab=qna', text: '전체 보기' })]),
            (inbox.items || []).length ? inbox.items.slice(0, 4).map(function (q) {
              return h('a', { class: 'db-line', href: '?tab=qna&inquiryId=' + q.inquiryId }, [
                h('span', { class: 'db-line__text' }, [
                  h('span', { class: 'db-line__title', text: q.title }),
                  h('span', { class: 'db-line__meta', text: [q.writerNickname, q.courseTitle, I.date(q.createdAt)].filter(Boolean).join(' · ') })
                ]),
                pill(q.status === 'WAITING' ? '답변 대기' : '답변 완료')
              ]);
            }) : h('p', { class: 'db-empty-line', text: '아직 받은 질문이 없어요' })
          ])
        ])
      );
    }).catch(function (err) { fill(root, srTitle(), errorBox(err, drawDashboard)); });
  }

  /* ---------- 강의 관리 ---------- */

  var courseView = { tab: 'all', query: '' };

  function drawCourses() {
    MB.api('/api/v1/instructor/courses').then(function (res) {
      renderCourses((res && res.items) || []);
    }).catch(function (err) { fill(root, srTitle(), errorBox(err, drawCourses)); });
  }

  function renderCourses(cs) {
    var tabsEl = h('div', { class: 'db-tabs', role: 'tablist', 'aria-label': '공개 상태' });
    var search = h('input', { class: 'db-search', type: 'search', placeholder: '강의 제목 검색', 'aria-label': '강의 제목 검색', value: courseView.query });
    var body = h('div', { class: 'db-table__body', 'aria-live': 'polite' });

    function names() {
      var base = ['전체', '공개', '비공개', '임시 저장'];
      ['검수 중', '반려'].forEach(function (n) { if (cs.some(function (c) { return courseState(c) === n; })) base.push(n); });
      return base;
    }

    function drawTabs() {
      fill(tabsEl, names().map(function (n) {
        var key = n === '전체' ? 'all' : n;
        var on = courseView.tab === key;
        var cnt = key === 'all' ? cs.length : cs.filter(function (c) { return courseState(c) === n; }).length;
        return h('button', { type: 'button', role: 'tab', class: 'db-tab' + (on ? ' is-on' : ''), 'aria-selected': String(on), id: 'db-ctab-' + key,
          onclick: function () { courseView.tab = key; drawTabs(); drawRows(); var b = document.getElementById('db-ctab-' + key); if (b) b.focus(); } },
          [n, h('span', { class: 'db-tab__n', text: String(cnt) })]);
      }));
    }

    function drawRows() {
      var q = courseView.query.trim();
      var rows = cs.filter(function (c) {
        return (courseView.tab === 'all' || courseState(c) === courseView.tab) && (!q || c.title.indexOf(q) >= 0);
      });
      fill(body, rows.length ? rows.map(courseRow) : h('div', { class: 'db-empty', text: cs.length ? '조건에 맞는 강의가 없어요' : '아직 만든 강의가 없어요. 강의 생성에서 첫 강의를 만들어 보세요' }));
    }

    function courseRow(c) {
      var state = courseState(c);
      var approved = c.status === 'APPROVED';
      var thumb = h('span', { class: 'db-thumb ' + tone(c.categoryName), 'aria-hidden': 'true' });
      if (c.thumbnailUrl && /^\/uploads\//.test(c.thumbnailUrl)) thumb.style.backgroundImage = 'url("' + c.thumbnailUrl.replace(/"/g, '') + '")';
      else thumb.textContent = (c.categoryName || c.title || '강').charAt(0);

      var sw = h('button', { type: 'button', class: 'db-switch' + (approved && c.isPublic ? ' is-on' : ''), role: 'switch',
        'aria-checked': String(approved && c.isPublic), 'aria-label': c.title + ' 공개 상태 변경',
        title: approved ? null : '관리자 검수를 통과한 강의만 공개할 수 있어요', disabled: !approved });
      sw.addEventListener('click', function () {
        if (sw.disabled) return;
        sw.disabled = true;
        var next = !c.isPublic;
        MB.api('/api/v1/instructor/courses/' + c.courseId + '/visibility', { method: 'PATCH', body: { isPublic: next } }).then(function (r) {
          c.isPublic = r && typeof r.isPublic === 'boolean' ? r.isPublic : next;
          I.toast(c.isPublic ? '강의를 공개했어요' : '강의를 비공개로 바꿨어요');
          drawTabs(); drawRows();
          var again = document.querySelector('[data-db-switch="' + c.courseId + '"]');
          if (again) again.focus();
        }).catch(function (err) {
          sw.disabled = false;
          I.toast(I.errorText(err, '공개 상태를 바꾸지 못했어요'));   // COURSE_NOT_APPROVED 등
        });
      });
      sw.setAttribute('data-db-switch', String(c.courseId));
      sw.appendChild(h('span', { class: 'db-switch__knob', 'aria-hidden': 'true' }));

      var actions = [
        c.status !== 'REVIEW' ? h('a', { class: 'db-act', href: CREATE_URL + '?courseId=' + c.courseId, text: '수정' })
          : h('span', { class: 'db-act db-act--off', title: '검수 중에는 고칠 수 없어요', text: '수정' }),
        c.status !== 'REVIEW' ? h('button', { type: 'button', class: 'db-act db-act--danger', text: '삭제', onclick: function () {
          I.confirm("'" + c.title + "' 강의를 삭제할까요?", function () {
            MB.api('/api/v1/instructor/courses/' + c.courseId, { method: 'DELETE' }).then(function () {
              cs = cs.filter(function (x) { return x.courseId !== c.courseId; });
              I.toast('강의를 삭제했어요');
              drawTabs(); drawRows();
            }).catch(function (err) { I.toast(I.errorText(err, '강의를 삭제하지 못했어요')); });   // COURSE_HAS_LEARNERS 등
          });
        } }) : null
      ];

      var meta = '등록 ' + I.date(c.createdAt) + (c.status === 'REJECTED' && c.rejectReason ? ' · 반려 사유 : ' + c.rejectReason : '');
      return h('div', { class: 'db-row' }, [
        h('div', { class: 'db-row__course' }, [thumb, h('span', { class: 'db-row__text' }, [
          h('span', { class: 'db-row__title', text: c.title }), h('span', { class: 'db-row__meta', text: meta })
        ])]),
        h('span', { class: 'db-cell db-cell--strong', text: c.categoryName || '-' }),
        h('span', { class: 'db-cell', text: LEVEL[c.level] || '-' }),
        h('span', { class: 'db-cell', text: (c.lessonCount || 0) + '개 강의 · ' + minutes(c.totalDurationSeconds) + '분' }),
        h('div', { class: 'db-row__state' }, [sw, pill(state)]),
        h('div', { class: 'db-row__actions' }, actions)
      ]);
    }

    search.addEventListener('input', function () { courseView.query = search.value; drawRows(); });
    drawTabs();
    drawRows();
    fill(root, srTitle(),
      h('section', { class: 'db-bar' }, [tabsEl, search]),
      h('section', { class: 'db-table db-table--courses' }, [h('div', { class: 'db-table__inner' }, [
        h('div', { class: 'db-head', role: 'presentation' }, ['강의', '카테고리', '난이도', '구성', '공개 상태', '관리'].map(function (t, i) {
          return h('span', { class: i === 5 ? 'db-head__right' : null, text: t });
        })),
        body
      ])])
    );
  }

  /* ---------- 퀴즈 관리 ---------- */

  function drawQuizzes() {
    Promise.all([optional(MB.api('/api/v1/instructor/quizzes')), MB.api('/api/v1/instructor/courses')]).then(function (r) {
      var cs = (r[1] && r[1].items) || [];
      var want = Number(params.get('courseId')) || null;
      if (params.get('new') === '1' || want) { drawQuizForm(cs, want); return; }   // 퀴즈 생성·수정은 목록 대신 퀴즈 화면
      if (r[0].missing) { fill(root, srTitle(), h('div', { class: 'db-empty db-empty--box', text: '퀴즈 목록은 준비 중이에요' })); return; }
      var qz = items(r[0]);
      fill(root, srTitle(),
        h('section', { class: 'db-table db-table--quiz' }, [h('div', { class: 'db-table__inner' }, [
          h('div', { class: 'db-head' }, ['퀴즈', '연결된 강의', '문항', '등록일', '관리'].map(function (t, i) { return h('span', { class: i === 4 ? 'db-head__right' : null, text: t }); })),
          qz.length ? qz.map(function (q) {
            return h('div', { class: 'db-row' }, [
              h('span', { class: 'db-row__title', text: q.courseTitle + ' 퀴즈' }),
              h('span', { class: 'db-cell db-cell--ellipsis', text: q.courseTitle }),
              h('span', { class: 'db-cell db-cell--strong', text: (q.mainCount || 0) + '문항',
                title: '4지선다 ' + (q.mainCount || 0) + '문항 · 테스트 OX ' + (q.testCount || 0) + '문항(강의 만들기에서 관리)' }),
              h('span', { class: 'db-cell', text: I.date(q.updatedAt) }),
              h('div', { class: 'db-row__actions' }, [h('a', { class: 'db-act', href: '?tab=quizzes&courseId=' + q.courseId, text: '수정' })])
            ]);
          }) : h('div', { class: 'db-empty', text: '등록된 퀴즈가 없어요. 퀴즈 생성 버튼으로 시작해 보세요' })
        ])])
      );
    }).catch(function (err) { fill(root, srTitle(), errorBox(err, drawQuizzes)); });
  }

  /* 퀴즈 생성·수정 (원본 quizForm 보기). 퀴즈는 강의마다 4지선다 한 벌이라 '연결할 강의'를 고르면 그 강의의 퀴즈를 불러와 고친다
   * - 저장 : API 80 PUT /api/v1/instructor/courses/{id}/quiz (5~15문제, 보기 4개, 정답 1개, 해설 필수 - 서버 규칙 TCH-04 그대로)
   * - 퀴즈 제목은 저장할 곳이 없어 '강의명 퀴즈'로 정해진다(읽기 전용). 퀴즈 삭제는 0문제로 비울 수 없어 넣지 않았다
   * - 테스트 OX 5문제는 강의 만들기 화면에서 관리한다 */
  var QUIZ_MIN = 5, QUIZ_MAX = 15, MARKS = ['①', '②', '③', '④'];

  function blankQuestion() { return { text: '', options: ['', '', '', ''], answer: -1, exp: '' }; }

  function toQuestions(detail) {
    return ((detail && detail.quizzes) || []).filter(function (q) { return q.quizType === 'MAIN'; })
      .sort(function (a, b) { return (a.sortOrder || 0) - (b.sortOrder || 0); })
      .map(function (q) {
        var ch = (q.choices || []).slice().sort(function (a, b) { return (a.sortOrder || 0) - (b.sortOrder || 0); });
        return { text: q.content || '', options: [0, 1, 2, 3].map(function (i) { return ch[i] ? ch[i].content : ''; }),
          answer: ch.findIndex(function (c) { return c.isCorrect; }), exp: q.explanation || '' };
      });
  }

  function drawQuizForm(cs, courseId) {
    var editable = cs.filter(function (c) { return c.status !== 'REVIEW'; });   // 검수 중인 강의는 고칠 수 없다
    var st = { courseId: courseId ? String(courseId) : '', questions: [blankQuestion()], loaded: false, editing: false };
    var titleIn = h('input', { class: 'db-input', id: 'db-qf-title', type: 'text', readonly: true, placeholder: '연결할 강의를 고르면 정해져요' });
    var courseSel = h('select', { class: 'db-select db-select--full', id: 'db-qf-course' },
      [h('option', { value: '', text: editable.length ? '강의를 선택해 주세요' : '퀴즈를 붙일 강의가 없어요' })].concat(editable.map(function (c) {
        return h('option', { value: String(c.courseId), text: c.title });
      })));
    var info = h('p', { class: 'db-hint', 'aria-live': 'polite' });
    var list = h('div', { class: 'db-qlist' });
    var addBtn = h('button', { type: 'button', class: 'db-btn db-btn--lg db-btn--add', text: '+ 문제 추가' });
    var err = h('span', { class: 'db-form-err', role: 'alert' });
    var save = h('button', { type: 'button', class: 'db-btn db-btn--primary db-btn--lg', text: '퀴즈 등록' });

    function done(q) { return q.text.trim() && q.options.every(function (o) { return o.trim(); }) && q.answer >= 0; }

    function courseOf(id) { return cs.filter(function (c) { return String(c.courseId) === String(id); })[0]; }

    function drawQuestions(focusId) {
      fill(list, st.questions.map(function (q, i) {
        var stateEl = h('span', { class: 'db-qstate' + (done(q) ? '' : ' is-todo'), text: done(q) ? '입력 완료' : '정답과 보기를 모두 입력해 주세요' });
        function sync() { stateEl.textContent = done(q) ? '입력 완료' : '정답과 보기를 모두 입력해 주세요'; stateEl.classList.toggle('is-todo', !done(q)); err.textContent = ''; }
        var text = h('textarea', { class: 'db-textarea db-textarea--sm', id: 'db-q' + i + '-text', value: q.text, placeholder: '문제를 입력해 주세요', maxlength: '500' });
        text.addEventListener('input', function () { q.text = text.value; sync(); });
        var exp = h('textarea', { class: 'db-textarea db-textarea--sm db-textarea--exp', id: 'db-q' + i + '-exp', value: q.exp, placeholder: '정답의 이유를 수강생이 이해하기 쉽게 적어 주세요' });
        exp.addEventListener('input', function () { q.exp = exp.value; err.textContent = ''; });
        return h('section', { class: 'db-box db-qbox', 'aria-label': '문제 ' + (i + 1) }, [
          h('div', { class: 'db-qhead' }, [
            h('span', { class: 'db-qno', text: '문제 ' + (i + 1) }), stateEl,
            h('button', { type: 'button', class: 'db-act db-act--danger' + (st.questions.length > 1 ? '' : ' db-act--off'), id: 'db-q' + i + '-del', text: '문제 삭제',
              'aria-disabled': st.questions.length > 1 ? null : 'true', onclick: function () {
                if (st.questions.length <= 1) return;
                st.questions.splice(i, 1); err.textContent = '';
                drawQuestions('db-q' + Math.max(0, i - 1) + '-text'); drawAdd();
              } })
          ]),
          h('div', { class: 'db-field' }, [h('label', { class: 'db-label', for: 'db-q' + i + '-text', text: '문제' }), text]),
          h('div', { class: 'db-field db-field--opts', role: 'group', 'aria-label': '문제 ' + (i + 1) + ' 보기' }, [
            h('span', { class: 'db-label' }, ['보기 ', h('span', { class: 'db-label__aside', text: '· 왼쪽 원을 눌러 정답을 선택해요' })]),
            q.options.map(function (o, k) {
              var on = q.answer === k;
              var opt = h('input', { class: 'db-input db-opt' + (on ? ' is-answer' : ''), id: 'db-q' + i + '-o' + k, type: 'text', value: o, placeholder: '보기 ' + (k + 1), maxlength: '200',
                'aria-label': '문제 ' + (i + 1) + ' 보기 ' + (k + 1) });
              opt.addEventListener('input', function () { q.options[k] = opt.value; sync(); });
              return h('div', { class: 'db-optrow' }, [
                h('button', { type: 'button', class: 'db-pick' + (on ? ' is-on' : ''), id: 'db-q' + i + '-p' + k, 'aria-pressed': String(on),
                  'aria-label': '보기 ' + (k + 1) + '을 정답으로 선택', text: on ? '✓' : MARKS[k],
                  onclick: function () { q.answer = k; err.textContent = ''; drawQuestions('db-q' + i + '-p' + k); } }),
                opt
              ]);
            })
          ]),
          h('div', { class: 'db-field' }, [h('label', { class: 'db-label', for: 'db-q' + i + '-exp', text: '해설' }), exp])
        ]);
      }));
      if (focusId) { var f = document.getElementById(focusId); if (f) f.focus(); }
    }

    function drawAdd() {
      addBtn.disabled = st.questions.length >= QUIZ_MAX;
      addBtn.title = addBtn.disabled ? '퀴즈는 ' + QUIZ_MAX + '문제까지 만들 수 있어요' : '';
    }

    /* 강의를 고르면 그 강의의 퀴즈를 불러온다. 다 불러오기 전(또는 실패)에는 저장을 막는다 :
     * PUT 은 그 강의의 퀴즈를 통째로 바꾸므로, 앞서 고른 강의의 문제가 새 강의에 저장되면 안 된다 */
    function loadCourse() {
      var c = courseOf(st.courseId);
      st.loaded = false;
      titleIn.value = c ? c.title + ' 퀴즈' : '';
      err.textContent = '';
      st.editing = false;
      st.questions = [blankQuestion()];   // 앞서 고른 강의의 문제를 바로 비운다
      save.textContent = '퀴즈 등록';
      drawQuestions(); drawAdd();
      if (!c) { info.textContent = ''; return; }
      info.textContent = '불러오는 중이에요…';
      MB.api('/api/v1/instructor/courses/' + c.courseId).then(function (d) {
        if (String(c.courseId) !== st.courseId) return;   // 그사이 다른 강의를 골랐으면 버린다
        var qs = toQuestions(d);
        st.loaded = true;
        st.editing = qs.length > 0;
        st.questions = qs.length ? qs : [blankQuestion()];
        save.textContent = st.editing ? '수정 완료' : '퀴즈 등록';
        info.textContent = [
          st.editing ? '이 강의에 이미 있는 4지선다 퀴즈를 불러왔어요.' : '',
          '퀴즈는 ' + QUIZ_MIN + '~' + QUIZ_MAX + '문제로 만들어요.',
          c.status === 'APPROVED' ? '허가된 강의라 저장하면 관리자가 다시 검수해요.' : ''
        ].filter(Boolean).join(' ');
        drawQuestions(); drawAdd();
      }).catch(function (e) {
        if (String(c.courseId) !== st.courseId) return;
        info.textContent = '';
        err.textContent = I.errorText(e, '퀴즈를 불러오지 못했어요') + ' 강의를 다시 골라 주세요';
      });
    }

    courseSel.addEventListener('change', function () { st.courseId = courseSel.value; loadCourse(); });
    addBtn.addEventListener('click', function () {
      if (st.questions.length >= QUIZ_MAX) return;
      st.questions.push(blankQuestion()); err.textContent = '';
      drawQuestions('db-q' + (st.questions.length - 1) + '-text'); drawAdd();
    });

    save.addEventListener('click', function () {
      if (save.disabled) return;
      if (!st.courseId) { err.textContent = '연결할 강의를 선택해 주세요'; courseSel.focus(); return; }
      if (!st.loaded) { err.textContent = '강의의 퀴즈를 다 불러온 뒤에 저장해 주세요'; return; }
      var bad = st.questions.findIndex(function (q) { return !done(q); });
      if (bad >= 0) { err.textContent = '문제 ' + (bad + 1) + '의 문제, 보기 4개, 정답을 모두 입력해 주세요'; return; }
      var noExp = st.questions.findIndex(function (q) { return !q.exp.trim(); });
      if (noExp >= 0) { err.textContent = '문제 ' + (noExp + 1) + '의 해설을 입력해 주세요'; return; }
      if (st.questions.length < QUIZ_MIN) { err.textContent = '퀴즈는 ' + QUIZ_MIN + '문제 이상 만들어 주세요 (지금 ' + st.questions.length + '문제)'; return; }
      save.disabled = true;
      MB.api('/api/v1/instructor/courses/' + st.courseId + '/quiz', { method: 'PUT', body: { questions: st.questions.map(function (q) {
        return { content: q.text.trim(), choices: q.options.map(function (o) { return o.trim(); }), answerIndex: q.answer, explanation: q.exp.trim() };
      }) } }).then(function () {
        I.toast(st.editing ? '퀴즈를 수정했어요' : '퀴즈를 등록했어요');
        setTimeout(function () { location.href = '?tab=quizzes'; }, 600);
      }).catch(function (e) {
        err.textContent = I.errorText(e, '퀴즈를 저장하지 못했어요');   // QUIZ_COUNT_INVALID, 검수 중인 강의 등
        save.disabled = false;
      });
    });

    var target = st.courseId ? courseOf(st.courseId) : null;
    if (target && target.status !== 'REVIEW') { courseSel.value = st.courseId; courseSel.disabled = true; }
    else if (st.courseId) {   // 주소의 강의가 내 강의 목록에 없거나(삭제·잘못된 주소) 검수 중
      err.textContent = target ? '검수 중인 강의의 퀴즈는 고칠 수 없어요' : '강의를 찾을 수 없어요. 연결할 강의를 다시 선택해 주세요';
      st.courseId = '';
    }

    fill(root, h('h1', { class: 'mp-sr', text: courseId ? '퀴즈 수정' : '퀴즈 생성' }),
      h('section', { class: 'db-box db-form' }, [
        h('div', { class: 'db-sechead' }, [h('span', { class: 'db-secno', 'aria-hidden': 'true', text: '1' }), h('div', {}, [
          h('h2', { class: 'db-h2 db-h2--flat', text: '퀴즈 정보' }), h('p', { class: 'db-sub', text: '퀴즈 제목과 연결할 강의를 선택해요' })
        ])]),
        h('div', { class: 'db-grid2' }, [
          h('div', { class: 'db-field' }, [h('label', { class: 'db-label', for: 'db-qf-title' }, ['퀴즈 제목 ', h('b', { class: 'db-req', text: '*' })]), titleIn]),
          h('div', { class: 'db-field' }, [h('label', { class: 'db-label', for: 'db-qf-course' }, ['연결할 강의 ', h('b', { class: 'db-req', text: '*' })]), courseSel])
        ]),
        info
      ]),
      list,
      addBtn,
      h('div', { class: 'db-formbar' }, [
        h('span', { class: 'db-formbar__msg' }, [err]),
        h('button', { type: 'button', class: 'db-btn db-btn--ghost db-btn--lg', text: '취소', onclick: function () { location.href = '?tab=quizzes'; } }),
        save
      ])
    );
    drawAdd();
    if (st.courseId) loadCourse(); else drawQuestions();
    (courseSel.disabled ? titleIn : courseSel).focus();
  }

  /* ---------- 요약글 관리 ---------- */

  function drawSummaries() {
    Promise.all([optional(MB.api('/api/v1/instructor/summaries')), MB.api('/api/v1/instructor/courses')]).then(function (r) {
      var cs = (r[1] && r[1].items) || [];
      if (r[0].missing) { fill(root, srTitle(), h('div', { class: 'db-empty db-empty--box', text: '요약글 관리는 준비 중이에요' })); return; }
      var sm = items(r[0]);
      var wantLesson = Number(params.get('lessonId')) || null;
      if (params.get('new') === '1' || wantLesson) { drawSummaryForm(cs, sm, wantLesson ? sm.filter(function (m) { return m.lessonId === wantLesson; })[0] : null); return; }
      fill(root, srTitle(), sm.length
        ? h('section', { class: 'db-cards' }, sm.map(function (m) { return summaryCard(m, cs, sm); }))
        : h('div', { class: 'db-empty db-empty--box', text: '작성한 요약글이 없어요' }));
    }).catch(function (err) { fill(root, srTitle(), errorBox(err, drawSummaries)); });
  }

  function summaryCard(m, cs, sm) {
    var cover = h('div', { class: 'db-card__cover' });
    if (m.thumbnailUrl && /^\/uploads\//.test(m.thumbnailUrl)) {
      cover.appendChild(h('span', { class: 'db-card__img', style: 'background-image:url("' + m.thumbnailUrl.replace(/"/g, '') + '")' }));
    } else {
      cover.appendChild(h('span', { class: 'db-card__course', text: m.courseTitle }));
    }
    cover.appendChild(h('span', { class: 'db-card__play', 'aria-hidden': 'true' }, [playIcon()]));
    return h('article', { class: 'db-card' }, [
      cover,
      h('div', { class: 'db-card__body' }, [
        h('div', { class: 'db-card__top' }, [pill(m.published ? '게시' : '임시 저장'), h('span', { class: 'db-line__meta', text: I.date(m.updatedAt) })]),
        h('h3', { class: 'db-card__title', text: m.lessonTitle }),
        h('p', { class: 'db-card__text', text: m.summary }),
        h('span', { class: 'db-card__rel', text: '관련 강의 · ' + m.courseTitle }),
        h('div', { class: 'db-card__actions' }, [
          h('button', { type: 'button', class: 'db-act', text: '수정', onclick: function () { drawSummaryForm(cs, sm, m); } }),
          h('button', { type: 'button', class: 'db-act db-act--danger', text: '삭제', onclick: function () {
            I.confirm("'" + m.lessonTitle + "' 요약글을 삭제할까요?", function () {
              saveSummary(m.lessonId, '').then(function (r) {
                I.toast(r && r.courseStatus === 'REVIEW' ? '요약글을 삭제했어요. 강의는 다시 검수를 받아요' : '요약글을 삭제했어요');
                drawSummaries();
              }).catch(function (err) { I.toast(I.errorText(err, '요약글을 삭제하지 못했어요')); });
            });
          } })
        ])
      ])
    ]);
  }

  function playIcon() {
    var ns = 'http://www.w3.org/2000/svg';
    var svg = document.createElementNS(ns, 'svg');
    svg.setAttribute('width', '18'); svg.setAttribute('height', '18'); svg.setAttribute('viewBox', '0 0 24 24'); svg.setAttribute('fill', '#fff');
    var p = document.createElementNS(ns, 'path'); p.setAttribute('d', 'M8 5v14l11-7z'); svg.appendChild(p);
    return svg;
  }

  function saveSummary(lessonId, text) {
    return MB.api('/api/v1/instructor/lessons/' + lessonId + '/summary', { method: 'PATCH', body: { summary: text } });
  }

  /* 요약글 쓰기·고치기 : 요약글은 영상마다 하나라서 '관련 강의'와 '영상'을 고르고 본문을 쓴다 (원본의 제목 = 영상 제목) */
  function drawSummaryForm(cs, sm, current) {
    var editing = !!current;
    var courses = cs.filter(function (c) { return c.status !== 'REVIEW'; });
    var courseSel = h('select', { class: 'db-select', id: 'db-sf-course', disabled: editing },
      editing ? [h('option', { value: String(current.courseId), text: current.courseTitle })]
        : [h('option', { value: '', text: '강의를 골라 주세요' })].concat(courses.map(function (c) { return h('option', { value: String(c.courseId), text: c.title }); })));
    var lessonSel = h('select', { class: 'db-select db-select--title', id: 'db-sf-lesson', disabled: true },
      [h('option', { value: editing ? String(current.lessonId) : '', text: editing ? current.lessonTitle : '관련 강의를 먼저 골라 주세요' })]);
    var preview = h('span', { class: 'db-cover-preview', role: 'img', 'aria-label': '대표 이미지' });
    var body = h('textarea', { class: 'db-textarea', id: 'db-sf-body', maxlength: String(SUMMARY_MAX), value: editing ? current.summary : '',
      placeholder: '수강생이 빠르게 복습할 수 있도록 핵심 내용을 정리해 주세요' });
    var count = h('span', { class: 'db-label__aside', text: body.value.length + '자' });
    var err = h('span', { class: 'db-form-err', role: 'alert' });
    var note = h('span', { class: 'db-hint' });
    var save = h('button', { type: 'button', class: 'db-btn db-btn--primary db-btn--lg', text: '저장' });
    var lessons = [];

    function showCover(courseId) {
      var c = cs.filter(function (x) { return String(x.courseId) === String(courseId); })[0];
      var url = c && c.thumbnailUrl && /^\/uploads\//.test(c.thumbnailUrl) ? c.thumbnailUrl : null;
      preview.style.backgroundImage = url ? 'url("' + url.replace(/"/g, '') + '")' : '';
      preview.textContent = url ? '' : '강의 썸네일이 없어요';
      note.textContent = c && c.status === 'APPROVED' ? '허가된 강의라 저장하면 관리자가 다시 검수해요' : '';
    }

    courseSel.addEventListener('change', function () {
      err.textContent = '';
      showCover(courseSel.value);
      lessonSel.disabled = true;
      if (!courseSel.value) { fill(lessonSel, h('option', { value: '', text: '관련 강의를 먼저 골라 주세요' })); return; }
      fill(lessonSel, h('option', { value: '', text: '영상을 불러오는 중이에요…' }));
      var picked = courseSel.value;
      MB.api('/api/v1/instructor/courses/' + picked).then(function (d) {
        if (courseSel.value !== picked) return;   // 그사이 다른 강의를 골랐으면 늦게 온 응답은 버린다
        lessons = (d && d.lessons) || [];
        var done = sm.map(function (m) { return m.lessonId; });
        var open = lessons.filter(function (l) { return done.indexOf(l.lessonId) < 0; });
        fill(lessonSel, [h('option', { value: '', text: open.length ? '요약글을 쓸 영상을 골라 주세요' : '요약글이 없는 영상이 없어요' })]
          .concat(open.map(function (l) { return h('option', { value: String(l.lessonId), text: l.title }); })));
        lessonSel.disabled = !open.length;
      }).catch(function (e) {
        if (courseSel.value !== picked) return;
        fill(lessonSel, h('option', { value: '', text: '영상을 불러오지 못했어요' }));
        err.textContent = I.errorText(e, '영상을 불러오지 못했어요');
      });
    });
    body.addEventListener('input', function () { count.textContent = body.value.length + '자'; err.textContent = ''; });
    if (editing) showCover(current.courseId); else showCover('');

    var courseField = h('div', { class: 'db-field db-field--narrow' }, [h('label', { class: 'db-label', for: 'db-sf-course', text: '관련 강의 연결' }), courseSel]);

    function back() { location.href = '?tab=summaries'; }

    save.addEventListener('click', function () {
      if (save.disabled) return;
      var lessonId = editing ? current.lessonId : Number(lessonSel.value);
      if (!lessonId) { err.textContent = '요약글을 쓸 영상을 골라 주세요'; return; }
      if (!body.value.trim()) { err.textContent = '본문을 입력해 주세요'; return; }
      save.disabled = true;
      saveSummary(lessonId, body.value.trim()).then(function (r) {
        I.toast(r && r.courseStatus === 'REVIEW' && !editing ? '요약글을 저장했어요. 강의는 다시 검수를 받아요'
          : r && r.courseStatus === 'REVIEW' ? '요약글을 고쳤어요. 강의는 다시 검수를 받아요' : '요약글을 저장했어요');
        setTimeout(back, 600);
      }).catch(function (e) {
        err.textContent = I.errorText(e, '요약글을 저장하지 못했어요');   // 검수 중인 강의, 금칙어 등
        save.disabled = false;
      });
    });

    fill(root, h('h1', { class: 'mp-sr', text: editing ? '요약글 수정' : '요약글 작성' }),
      h('section', { class: 'db-box db-form' }, [
        // 새로 쓸 때는 영상을 고르려면 강의부터 골라야 해서 '관련 강의 연결'을 맨 위에 둔다 (고칠 때는 원본 순서대로 맨 아래)
        editing ? null : courseField,
        h('div', { class: 'db-field' }, [h('label', { class: 'db-label', for: 'db-sf-lesson' }, ['제목 ', h('b', { class: 'db-req', text: '*' })]), lessonSel]),
        h('div', { class: 'db-field' }, [
          h('span', { class: 'db-label' }, ['대표 이미지 ', h('span', { class: 'db-label__aside', text: '· 관련 강의의 썸네일이 쓰여요' })]),
          preview
        ]),
        h('div', { class: 'db-field' }, [h('label', { class: 'db-label db-label--split', for: 'db-sf-body' }, [h('span', {}, ['본문 ', h('b', { class: 'db-req', text: '*' })]), count]), body]),
        editing ? courseField : null
      ]),
      h('div', { class: 'db-formbar' }, [
        h('span', { class: 'db-formbar__msg' }, [err, note]),
        h('button', { type: 'button', class: 'db-btn db-btn--ghost db-btn--lg', text: '취소', onclick: back }),
        save
      ])
    );
    (editing ? body : courseSel).focus();
  }

  start();
})();
