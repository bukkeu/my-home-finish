/* comment.
*   담당 : DBA ① 신아림 (#60, 강사 강의 만들기·고치기 /instructor/course-create[?courseId=])
*   #374 목업(InstructorCourseCreate.dc.html)과 같게 : 이재성이 넘겨받아 작업
*   - 6구역이 한 화면에 모두 보이고, 오른쪽 작성 현황 카드(퍼센트 + 항목 5개)와 임시 저장 · 검수 요청 버튼이 따라온다
*   - 버튼 한 번에 바뀐 구역만 서버 API 순서대로 저장한다 : 강의(API 73·75) → 썸네일(77) → 영상 파일(78 부속) → 영상·요약글(78)
*     → 테스트 퀴즈(79) → 퀴즈(80) → (검수 요청이면) 검수 요청(81). 중간에 실패하면 거기서 멈추고, 앞에서 저장된 구역은 그대로 남는다
*   - 썸네일·영상 파일은 고를 때 바로 올리지 않고 들고 있다가 저장할 때 올린다 (새 강의는 저장 전에 강의 번호가 없다)
*   - 영상 길이는 파일에서 읽어 '길이(분)' 칸에 채운다. 영상은 파일 업로드만 (영상 URL 은 넣지 않음, 팀 합의 #371)
*   - 4·5 퀴즈 : 문제 파일(CSV·엑셀·텍스트)을 서버(API 150, #368)가 읽어 문항을 돌려주면 미리 보여 주고, 저장할 때 API 79·80 으로 보낸다
*     서버에 읽기 API 가 아직 없으면(404) CSV·텍스트는 화면에서 읽는다 (엑셀은 서버가 있어야 읽는다)
*   - 검수 중(REVIEW)이면 모든 입력을 막는다. 반려(REJECTED)면 반려 사유, 허가(APPROVED)면 "고치면 다시 검수" 안내
*   - 저장하지 않은 내용이 있으면 화면을 떠날 때 묻는다
*  */
(function () {
  var root = document.querySelector('[data-cc-root]');
  if (!root || !window.MB) return;

  var LESSON_MAX = 3;
  var TEST_COUNT = 5;
  var MAIN_MIN = 5;
  var MAIN_MAX = 15;
  var TITLE_MAX = 60;
  var LEVELS = [['BEGINNER', '입문'], ['ELEMENTARY', '초급'], ['INTERMEDIATE', '중급'], ['ADVANCED', '고급']];
  var BACK = '/instructor/dashboard?tab=courses';
  var QUIZ_ACCEPT = '.csv,.xlsx,.txt,text/csv,text/plain,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet';

  var params = new URLSearchParams(location.search);
  var st;
  var keySeq = 0;

  function fresh() {
    return {
      courseId: Number(params.get('courseId')) || null,
      editing: !!Number(params.get('courseId')),   // 처음 열 때 고치기였는지 (새 강의는 저장한 뒤에도 '강의 생성')
      course: null,                 // API 74 응답 (저장된 상태)
      categories: [],
      form: { topId: null, categoryId: null, title: '', description: '', level: 'BEGINNER', policy: false, infoBaseDate: '', infoSource: '' },
      thumb: null,                  // 새로 고른 썸네일 { file, url }
      lessons: [],                  // { key, lessonId, title, minutes, durationSeconds, summary, videoUrl, file, fileName }
      quiz: { test: blankQuiz(), main: blankQuiz() },
      dirty: {},                    // 저장 안 한 구역 : info, thumb, lessons, test, main
      busy: false,
      status: '', statusIsProgress: false,
      savedAt: '',
      done: false,
      lessonKey: null               // 영상 저장 Idempotency-Key 와 그때 보낸 내용
    };
  }
  function blankQuiz() { return { questions: null, saved: 0, fileName: '', fileSize: 0, error: '', reading: false, showAll: false }; }
  function blankLesson() { return { key: ++keySeq, lessonId: null, title: '', minutes: '', durationSeconds: 0, summary: '', videoUrl: '', file: null, fileName: '' }; }

  /* ---------- 도우미 ---------- */

  function h(tag, attrs, children) {
    var el = document.createElement(tag);
    Object.keys(attrs || {}).forEach(function (key) {
      var v = attrs[key];
      if (v === null || v === undefined || v === false) return;
      if (key === 'text') el.textContent = v;
      else if (key === 'class') el.className = v;
      else if (key === 'value') el.value = v;
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

  function svg(paths, size, stroke) {
    var ns = 'http://www.w3.org/2000/svg';
    var s = document.createElementNS(ns, 'svg');
    s.setAttribute('width', size || 22); s.setAttribute('height', size || 22); s.setAttribute('viewBox', '0 0 24 24');
    s.setAttribute('fill', 'none'); s.setAttribute('stroke', stroke || '#263B1A'); s.setAttribute('stroke-width', '1.6');
    s.setAttribute('stroke-linecap', 'round'); s.setAttribute('stroke-linejoin', 'round'); s.setAttribute('aria-hidden', 'true');
    paths.forEach(function (d) { var p = document.createElementNS(ns, 'path'); p.setAttribute('d', d); s.appendChild(p); });
    return s;
  }
  /* replaceChildren 는 null 을 글자 "null" 로 넣으므로 빈 값은 빼고 넣는다 */
  function fill(el) {
    var nodes = [].slice.call(arguments, 1).filter(function (x) { return x !== null && x !== undefined && x !== false; });
    el.replaceChildren.apply(el, nodes);
  }

  var UPLOAD_ICON = ['M12 16V4M7 9l5-5 5 5', 'M4 16v3a1 1 0 0 0 1 1h14a1 1 0 0 0 1-1v-3'];

  function errorText(err, fallback) {
    if (err && Array.isArray(err.fields) && err.fields.length && err.fields[0].message) return err.fields[0].message;
    return (err && err.message) || fallback;
  }

  function newKey() {
    if (window.crypto && crypto.randomUUID) return crypto.randomUUID();
    return 'k-' + Date.now() + '-' + Math.random().toString(36).slice(2, 10);
  }

  function locked() { return !!(st.course && st.course.status === 'REVIEW'); }
  function off() { return locked() || st.busy; }
  function isDirty() { return Object.keys(st.dirty).length > 0; }

  /* 입력할 때마다 화면 전체를 다시 그리면 포커스가 사라지므로, 작성 현황 카드만 바꾼다 */
  function touch(part) {
    st.dirty[part] = true;
    drawAside();
  }

  function field(label, input, opts) {
    var o = opts || {};
    var count = null;
    if (o.max) {
      count = h('span', { class: 'cc-f__count', text: (input.value || '').length.toLocaleString('ko-KR') + ' / ' + o.max.toLocaleString('ko-KR') + (o.unit || '') });
      input.addEventListener('input', function () { count.textContent = input.value.length.toLocaleString('ko-KR') + ' / ' + o.max.toLocaleString('ko-KR') + (o.unit || ''); });
    }
    return h('div', { class: 'cc-f' }, [
      h('label', { class: 'cc-f__label', for: input.id || null }, [
        h('span', {}, [label, o.required ? h('b', { text: ' *' }) : null, o.optional ? h('span', { class: 'cc-f__opt', text: o.optional }) : null]), count]),
      input,
      o.hint ? h('span', { class: 'cc-f__hint', text: o.hint }) : null
    ]);
  }

  var idSeq = 0;
  function input(attrs, onInput) {
    var el = h(attrs.rows ? 'textarea' : 'input', Object.assign({ id: 'cc-in-' + (++idSeq), class: 'cc-in' + (attrs.sm ? ' cc-in--sm' : '') + (attrs.extra ? ' ' + attrs.extra : ''),
      disabled: off() }, attrs, { sm: null, extra: null, rows: null }));
    if (onInput) el.addEventListener('input', function () { onInput(el.value); });
    return el;
  }

  function section(no, title, desc, body, pill) {
    return h('section', { class: 'cc-sec', 'aria-labelledby': 'cc-sec-' + no }, [
      h('div', { class: 'cc-sec__head' }, [
        h('span', { class: 'cc-sec__num', text: String(no) }),
        h('div', { class: 'cc-sec__title' }, [h('h2', { id: 'cc-sec-' + no, text: title }), desc ? h('p', { text: desc }) : null]),
        pill ? h('span', { class: 'cc-pill', 'data-cc-pill': pill.key || null, text: pill.text }) : null
      ])
    ].concat(body));
  }

  function minutesText(sec) {
    var s = Math.max(0, Math.round(sec || 0));
    return s < 60 ? s + '초' : Math.floor(s / 60) + '분' + (s % 60 ? ' ' + (s % 60) + '초' : '');
  }

  /* ---------- 불러오기 ---------- */

  function load() {
    root.replaceChildren(h('div', { class: 'ins-state', text: '강의 정보를 불러오는 중이에요…' }));
    Promise.all([
      MB.api('/api/v1/categories'),
      st.courseId ? MB.api('/api/v1/instructor/courses/' + st.courseId) : Promise.resolve(null)
    ]).then(function (res) {
      st.categories = res[0] || [];
      if (res[1]) applyCourse(res[1], true);
      else st.lessons = [blankLesson()];
      draw();
    }).catch(function (err) {
      root.replaceChildren(h('div', { class: 'ins-state ins-state--error', role: 'alert' }, [
        errorText(err, '강의 정보를 불러오지 못했어요.'), h('br'),
        h('button', { type: 'button', class: 'btn btn-secondary', text: '다시 시도', onclick: load }), ' ',
        h('a', { class: 'btn btn-ghost', href: BACK, text: '강의 관리로' })
      ]));
    });
  }

  function topOf(categoryId) {
    var top = st.categories.find(function (t) {
      return (t.children || []).some(function (s) { return s.categoryId === categoryId; });
    });
    return top ? top.categoryId : null;
  }

  function subName(categoryId) {
    var name = '';
    st.categories.forEach(function (t) {
      (t.children || []).forEach(function (s) { if (s.categoryId === categoryId) name = s.name; });
    });
    return name;
  }

  /* 서버 상태로 맞춘다. first 가 아니면 저장하지 않은 구역의 입력은 건드리지 않는다 */
  function applyCourse(c, first) {
    st.course = c;
    if (first || !st.dirty.info) {
      st.form = {
        topId: topOf(c.categoryId), categoryId: c.categoryId, title: c.title || '', description: c.description || '',
        level: c.level || 'BEGINNER', policy: !!(c.infoBaseDate || c.infoSource), infoBaseDate: c.infoBaseDate || '', infoSource: c.infoSource || ''
      };
    }
    if (first || !st.dirty.lessons) {
      st.lessons = (c.lessons || []).slice().sort(function (a, b) { return a.sortOrder - b.sortOrder; }).map(function (l) {
        var lesson = blankLesson();
        lesson.lessonId = l.lessonId; lesson.title = l.title || ''; lesson.videoUrl = l.videoUrl || '';
        lesson.durationSeconds = l.durationSeconds || 0; lesson.minutes = l.durationSeconds ? String(Math.max(1, Math.round(l.durationSeconds / 60))) : '';
        lesson.summary = l.summary || '';
        return lesson;
      });
      if (!st.lessons.length) st.lessons = [blankLesson()];
    }
    var quizzes = (c.quizzes || []).slice().sort(function (a, b) { return a.sortOrder - b.sortOrder; });
    ['test', 'main'].forEach(function (type) {
      var q = st.quiz[type];
      var mine = quizzes.filter(function (x) { return x.quizType === (type === 'test' ? 'TEST' : 'MAIN'); });
      q.saved = mine.length;
      if (first || !st.dirty[type]) {
        q.questions = mine.length ? mine.map(function (x) { return fromSaved(x, type); }) : null;
        q.fileName = ''; q.error = '';
      }
    });
  }

  /* API 74 의 퀴즈(보기에 isCorrect)를 저장 요청 모양(API 79·80)으로 */
  function fromSaved(x, type) {
    var cs = (x.choices || []).slice().sort(function (a, b) { return a.sortOrder - b.sortOrder; });
    var right = cs.findIndex(function (c) { return c.isCorrect === true || c.correct === true; });
    if (type === 'test') return { content: x.content || '', answer: right >= 0 ? cs[right].content : '', explanation: x.explanation || '' };
    return { content: x.content || '', choices: [0, 1, 2, 3].map(function (i) { return cs[i] ? cs[i].content : ''; }), answerIndex: right >= 0 ? right : null,
      explanation: x.explanation || '' };
  }

  /* ---------- 작성 현황 ---------- */

  function lessonHasVideo(l) { return !!(l.file || l.videoUrl); }
  function lessonDone(l) { return !!(l.title.trim() && lessonHasVideo(l) && l.summary.trim()); }
  function policyOk() { var f = st.form; return !f.policy || !!(f.infoBaseDate && f.infoSource.trim()); }
  function quizCount(type) { var q = st.quiz[type].questions; return q ? q.length : 0; }
  function testOk() { return quizCount('test') === TEST_COUNT; }
  function mainOk() { var n = quizCount('main'); return n >= MAIN_MIN && n <= MAIN_MAX; }

  function checks() {
    var f = st.form;
    var doneLessons = st.lessons.filter(lessonDone).length;
    return [
      { label: '카테고리·소제목', ok: !!f.categoryId, note: f.categoryId ? subName(f.categoryId) : '' },
      { label: '강의 제목·소개', ok: !!(f.title.trim() && f.description.trim() && policyOk()), note: '' },
      { label: '영상과 요약글', ok: st.lessons.length >= 1 && st.lessons.length <= LESSON_MAX && doneLessons === st.lessons.length,
        note: doneLessons + ' / ' + st.lessons.length + '개' },
      { label: '테스트 퀴즈 OX', ok: testOk(), note: quizNote('test') },
      { label: '퀴즈 4지선다', ok: mainOk(), note: quizNote('main') }
    ];
  }

  function quizNote(type) {
    var q = st.quiz[type];
    if (!q.questions) return '파일 없음';
    return q.questions.length + '문제';
  }

  /* 검수 요청 전에 막을 것 (목업 순서대로 첫 번째 하나) */
  function problem() {
    var f = st.form;
    if (!f.categoryId) return '카테고리와 소제목을 골라 주세요';
    if (!f.title.trim()) return '강의 제목을 입력해 주세요';
    if (!f.description.trim()) return '강의 소개를 입력해 주세요';
    if (!policyOk()) return '제도 정보 강의는 정보 기준일과 출처가 필요해요';
    var bad = st.lessons.findIndex(function (l) { return !lessonDone(l); });
    if (bad >= 0) return '영상 ' + (bad + 1) + '의 제목, 영상 파일, 요약글을 입력해 주세요';
    if (!st.quiz.test.questions) return '테스트 퀴즈(OX 5문제) 파일을 올려 주세요';
    if (!testOk()) return '테스트 퀴즈는 5문제여야 해요. 지금 ' + quizCount('test') + '문제예요';
    if (!st.quiz.main.questions) return '퀴즈(4지선다) 파일을 올려 주세요';
    if (!mainOk()) return '퀴즈는 5~15문제여야 해요. 지금 ' + quizCount('main') + '문제예요';
    return '';
  }

  /* ---------- 화면 ---------- */

  function draw() {
    document.title = (st.editing ? '강의 수정' : '강의 생성') + ' · Money Beetle';
    if (st.done) { root.replaceChildren(drawHead(), drawDone()); return; }
    fill(root,
      drawHead(),
      drawBanner(),
      h('div', { class: 'cc-layout' }, [
        h('div', { class: 'cc-main' }, [drawCategory(), drawInfo(), drawLessons(), drawQuiz('test'), drawQuiz('main'), drawSubmit()]),
        h('aside', { class: 'cc-aside', 'aria-label': '작성 현황', 'data-cc-aside': '' })
      ]),
      st.modal ? drawModal() : null
    );
    drawAside();
  }

  function drawHead() {
    var s = st.done ? 'REVIEW' : (st.course ? st.course.status : 'DRAFT');
    var steps = [['작성 중', s === 'DRAFT' || s === 'REJECTED' ? 'on' : 'done'],
      ['검수 중', s === 'REVIEW' ? 'on' : (s === 'APPROVED' ? 'done' : '')],
      ['허가 · 반려', s === 'APPROVED' ? 'on' : '']];
    return h('header', { class: 'cc-head' }, [
      h('a', { class: 'cc-back', href: BACK, 'aria-label': '강의 관리로 돌아가기' }, [svg(['m15 6-6 6 6 6'], 18)]),
      h('div', { class: 'cc-head__text' }, [
        h('h1', { text: st.editing ? '강의 수정' : '강의 생성' }),
        h('span', { text: '카테고리와 소제목을 정하고, 영상·요약글·퀴즈를 채워 검수를 요청해요' })
      ]),
      h('div', { class: 'cc-steps', 'aria-label': '검수 단계' }, steps.map(function (t) {
        return h('span', { class: 'cc-step' + (t[1] ? ' is-' + t[1] : ''), text: (t[1] === 'done' ? '✓ ' : '') + t[0] });
      }))
    ]);
  }

  function drawBanner() {
    var c = st.course;
    if (!c) return null;
    if (c.status === 'REJECTED') return h('div', { class: 'cc-banner cc-banner--rejected', role: 'note' }, [
      h('b', { text: '반려되었어요. 사유를 확인하고 고쳐서 다시 요청해 주세요' }),
      c.rejectReason ? h('span', { text: '사유 · ' + c.rejectReason }) : null
    ]);
    if (c.status === 'REVIEW') return h('div', { class: 'cc-banner cc-banner--review', role: 'note' }, [
      h('b', { text: '관리자가 검수하고 있어요' }), h('span', { text: '검수 중에는 강의를 고칠 수 없어요. 결과가 나오면 알려 드려요.' })]);
    if (c.status === 'APPROVED') return h('div', { class: 'cc-banner cc-banner--approved', role: 'note' }, [
      h('b', { text: '허가된 강의예요' }), h('span', { text: '내용을 고쳐 저장하면 다시 검수를 받고, 허가될 때까지 수강생에게 보이지 않아요.' })]);
    return null;
  }

  /* 1. 분류 */
  function drawCategory() {
    var f = st.form;
    var top = st.categories.find(function (t) { return t.categoryId === f.topId; });
    var chips = function (items, selected, onPick, label, sub) {
      return h('div', { class: 'cc-chips', role: 'radiogroup', 'aria-label': label }, items.map(function (it) {
        return h('button', { type: 'button', role: 'radio', class: 'cc-chip' + (sub ? ' cc-chip--sub' : ''), 'aria-checked': String(it.categoryId === selected),
          disabled: off(), text: it.name, onclick: function () { onPick(it.categoryId); st.dirty.info = true; draw(); } });
      }));
    };
    return section(1, '분류', '카테고리를 고르면 그 아래 소제목을 고를 수 있어요', [
      h('div', { class: 'cc-f' }, [h('span', { class: 'cc-f__label' }, [h('span', {}, ['카테고리', h('b', { text: ' *' })])]),
        chips(st.categories, f.topId, function (id) { if (f.topId !== id) { f.topId = id; f.categoryId = null; } }, '카테고리')]),
      h('div', { class: 'cc-f' }, [h('span', { class: 'cc-f__label' }, [h('span', {}, ['소제목', h('b', { text: ' *' })])]),
        top ? chips(top.children || [], f.categoryId, function (id) { f.categoryId = id; }, '소제목', true)
          : h('span', { class: 'cc-f__hint', style: 'font-size:13px', text: '먼저 카테고리를 골라 주세요' }),
        h('span', { class: 'cc-f__hint', text: '원하는 소제목이 없으면 관리자에게 추가를 요청해 주세요' })])
    ]);
  }

  /* 2. 강의 정보 : 제목·소개·썸네일·제도 정보·난이도 */
  function drawInfo() {
    var f = st.form;
    var titleMax = Math.max(TITLE_MAX, f.title.length);   // 예전에 60자 넘게 만든 강의도 잘리지 않게
    var savedThumb = st.course && st.course.thumbnailUrl && /^\/uploads\//.test(st.course.thumbnailUrl) ? st.course.thumbnailUrl : '';
    var shown = st.thumb ? st.thumb.url : savedThumb;
    var thumbFile = h('input', { type: 'file', accept: 'image/png,image/jpeg', class: 'cc-file', disabled: off(), 'aria-label': '썸네일 이미지 선택' });
    var thumbMsg = h('p', { class: 'cc-qfile__err', role: 'alert' });
    thumbFile.addEventListener('change', function () {
      var file = thumbFile.files && thumbFile.files[0];
      if (!file) return;
      if (!/\.(png|jpe?g)$/i.test(file.name)) { thumbMsg.textContent = 'jpg·png 이미지만 올릴 수 있어요'; thumbFile.value = ''; return; }
      if (file.size > 2 * 1024 * 1024) { thumbMsg.textContent = '이미지는 2MB 이하로 올려 주세요'; thumbFile.value = ''; return; }
      if (st.thumb) URL.revokeObjectURL(st.thumb.url);
      st.thumb = { file: file, url: URL.createObjectURL(file) };
      st.dirty.thumb = true;
      draw();
    });
    var thumbBox = h('label', { class: 'cc-thumb' + (shown ? ' has-img' : ''), style: shown ? 'background-image:url("' + shown.replace(/"/g, '') + '")' : null },
      [thumbFile, h('span', { text: '이미지 선택' })]);

    var levelSeg = h('div', { class: 'cc-seg', role: 'radiogroup', 'aria-label': '강의 난이도' }, LEVELS.map(function (lv) {
      return h('button', { type: 'button', role: 'radio', 'aria-checked': String(f.level === lv[0]), text: lv[1], disabled: off(),
        onclick: function () { f.level = lv[0]; st.dirty.info = true; draw(); } });
    }));

    var policyRows = f.policy ? [
      h('div', { class: 'cc-grid' }, [
        field('정보 기준일', input({ type: 'date', value: f.infoBaseDate, sm: true }, function (v) { f.infoBaseDate = v; touch('info'); }), { required: true }),
        field('출처', input({ maxlength: '200', value: f.infoSource, placeholder: '예) 주택도시기금 홈페이지', sm: true }, function (v) { f.infoSource = v; touch('info'); }), { required: true })
      ]),
      h('span', { class: 'cc-f__hint', text: '기준일과 출처는 수강생 화면의 강의 상세에 함께 표시돼요' })
    ] : [];

    return section(2, '강의 정보', '수강생이 가장 먼저 보는 제목과 소개예요', [
      field('강의 제목', input({ maxlength: String(titleMax), value: f.title, placeholder: '예) 청약통장 기초와 가점 계산' }, function (v) { f.title = v; touch('info'); }),
        { required: true, max: titleMax }),
      field('강의 소개', input({ rows: '5', maxlength: '2000', value: f.description, placeholder: '이 강의에서 무엇을 배우는지, 어떤 수강생에게 맞는지 적어 주세요' },
        function (v) { f.description = v; touch('info'); }), { required: true }),
      h('div', { class: 'cc-f' }, [
        h('span', { class: 'cc-f__label' }, [h('span', {}, ['강의 썸네일', h('span', { class: 'cc-f__opt', text: ' · 선택 · jpg·png 2MB 이하' })])]),
        h('div', { class: 'cc-thumbrow' }, [
          thumbBox,
          st.thumb ? h('button', { type: 'button', class: 'cc-text-btn', text: '삭제', disabled: off(), onclick: function () {
            URL.revokeObjectURL(st.thumb.url); st.thumb = null; delete st.dirty.thumb; draw();
          } }) : null
        ]),
        thumbMsg
      ]),
      h('div', { class: 'cc-f' }, [h('span', { class: 'cc-f__label' }, [h('span', { text: '강의 난이도' })]), levelSeg]),
      h('div', { class: 'cc-policy' }, [
        h('button', { type: 'button', role: 'checkbox', class: 'cc-check', 'aria-checked': String(f.policy), disabled: off(), onclick: function () {
          f.policy = !f.policy; st.dirty.info = true; draw();
        } }, [h('span', { class: 'cc-check__box', 'aria-hidden': 'true', text: f.policy ? '✓' : '' }), '청년 정책 같은 제도 정보를 다루는 강의예요'])
      ].concat(policyRows))
    ]);
  }

  /* 3. 영상과 요약글 */
  function drawLessons() {
    var cards = st.lessons.map(function (l, i) { return lessonCard(l, i); });
    return section(3, '영상과 요약글', '영상은 1~3개까지 등록하고, 영상마다 요약글을 써요', cards.concat([
      h('div', { class: 'cc-add' }, [
        h('button', { type: 'button', class: 'cc-soft-btn', text: '+ 영상 추가', disabled: off() || st.lessons.length >= LESSON_MAX,
          onclick: function () { st.lessons.push(blankLesson()); st.dirty.lessons = true; draw(); } }),
        h('span', { text: '영상은 3개까지 등록할 수 있어요 · mp4 500MB 이하' })
      ])
    ]), { text: st.lessons.length + ' / 3개' });
  }

  function lessonCard(l, i) {
    var ok = lessonDone(l);
    var state = h('span', { class: 'cc-video__state' + (ok ? ' is-ok' : ''), 'data-cc-vstate': l.key, text: ok ? '입력 완료' : '제목, 영상, 요약글을 모두 입력해 주세요' });
    var refresh = function () {
      var done = lessonDone(l);
      state.className = 'cc-video__state' + (done ? ' is-ok' : '');
      state.textContent = done ? '입력 완료' : '제목, 영상, 요약글을 모두 입력해 주세요';
    };
    var file = h('input', { type: 'file', accept: 'video/mp4', class: 'cc-file', disabled: off(), 'aria-label': '영상 ' + (i + 1) + ' 파일 선택' });
    var fileMsg = h('p', { class: 'cc-qfile__err', role: 'alert', text: l.error || '' });
    file.addEventListener('change', function () {
      var f = file.files && file.files[0];
      if (!f) return;
      l.error = '';
      if (!/\.mp4$/i.test(f.name) && f.type !== 'video/mp4') { l.error = 'mp4 파일만 올릴 수 있어요'; draw(); return; }
      if (f.size > 500 * 1024 * 1024) { l.error = '영상은 500MB 이하로 올려 주세요'; draw(); return; }
      readDuration(f).then(function (sec) {
        if (sec < 1) throw { message: '영상 길이를 확인해 주세요' };
        l.file = f; l.fileName = f.name; l.durationSeconds = sec; l.minutes = String(Math.max(1, Math.round(sec / 60)));
        st.dirty.lessons = true;
      }).catch(function (err) { l.error = errorText(err, '영상을 읽지 못했어요'); }).then(draw);
    });
    var label = l.file ? l.fileName + ' · ' + minutesText(l.durationSeconds) + ' (저장할 때 올라가요)'
      : l.videoUrl ? '저장된 영상 · ' + minutesText(l.durationSeconds) + ' · 다른 파일로 바꾸기' : '영상 파일 선택 (MP4)';

    return h('div', { class: 'cc-video' }, [
      h('div', { class: 'cc-video__head' }, [
        h('span', { class: 'cc-video__no', text: '영상 ' + (i + 1) }),
        state,
        h('button', { type: 'button', class: 'cc-text-btn', text: '삭제', disabled: off() || st.lessons.length <= 1, onclick: function () {
          st.lessons.splice(i, 1); st.dirty.lessons = true; draw();
        } })
      ]),
      h('div', { class: 'cc-video__grid' }, [
        field('영상 제목', input({ maxlength: '100', value: l.title, placeholder: '예) 1강. 청약통장이 뭐예요?', sm: true }, function (v) { l.title = v; refresh(); touch('lessons'); })),
        field('길이(분)', input({ type: 'number', min: '1', value: l.minutes, sm: true, 'aria-describedby': null }, function (v) {
          l.minutes = v;
          var n = Number(v);
          if (n > 0) l.durationSeconds = Math.round(n * 60);
          touch('lessons');
        }))
      ]),
      h('label', { class: 'cc-drop' + (off() ? ' is-disabled' : '') }, [file, svg(UPLOAD_ICON), label]),
      fileMsg,
      field('영상 요약글', input({ rows: '5', maxlength: '2000', value: l.summary, extra: 'cc-in--summary', placeholder: '이 영상의 핵심 내용을 수강생이 복습할 수 있게 정리해 주세요' },
        function (v) { l.summary = v; refresh(); touch('lessons'); }), { max: 2000, unit: '자' })
    ]);
  }

  /* 영상 길이는 파일을 브라우저에서 열어 읽는다 (영상 저장 요청에 durationSeconds 가 필요) */
  function readDuration(f) {
    return new Promise(function (resolve, reject) {
      var v = document.createElement('video');
      var url = URL.createObjectURL(f);
      v.preload = 'metadata';
      v.onloadedmetadata = function () { URL.revokeObjectURL(url); resolve(Math.round(v.duration || 0)); };
      v.onerror = function () { URL.revokeObjectURL(url); reject({ message: '영상 길이를 읽지 못했어요. mp4 파일인지 확인해 주세요' }); };
      v.src = url;
    });
  }

  /* 4·5. 퀴즈 : 문제 파일 올리기 → 미리 보기 */
  function drawQuiz(type) {
    var q = st.quiz[type];
    var isTest = type === 'test';
    var ok = isTest ? testOk() : mainOk();
    var file = h('input', { type: 'file', accept: QUIZ_ACCEPT, class: 'cc-file', disabled: off() || q.reading, 'aria-label': (isTest ? '테스트 퀴즈' : '퀴즈') + ' 문제 파일 선택' });
    file.addEventListener('change', function () {
      var f = file.files && file.files[0];
      if (f) readQuizFile(type, f);
    });
    var pill = !q.questions ? '파일 없음' : ok ? (q.fileName ? '파일 등록 완료' : '저장됨 ' + q.questions.length + '문제') : '문제 수 확인 필요';
    var dropText = q.reading ? '파일을 읽는 중이에요…' : q.questions ? '다른 파일로 바꾸기' : '문제 파일을 올려 주세요 (클릭해서 선택)';
    var info = q.questions ? h('div', { class: 'cc-qfile__ok' }, [
      h('b', { text: '✓ ' + (q.fileName || '저장된 문제') }),
      h('span', { text: (q.fileSize ? (q.fileSize / 1024).toFixed(1) + 'KB · ' : '') + '문제 ' + q.questions.length + '개 확인' + (q.fileName ? ' · 저장해야 반영돼요' : '') }),
      h('i'),
      q.fileName ? h('button', { type: 'button', class: 'cc-text-btn', style: 'height:auto;padding:0;font-size:13px', text: '파일 지우기', disabled: off(), onclick: function () {
        // 지우면 저장된 문제(있으면)로 돌아간다
        st.quiz[type] = blankQuiz();
        delete st.dirty[type];
        if (st.course) applyCourse(st.course, false);
        draw();
      } }) : null
    ]) : null;
    var hint = isTest
      ? '파일 안에 문제 5개와 각 문제의 정답(O 또는 X), 해설이 들어 있어야 해요. 표 파일은 첫 줄에 \'문제, 정답, 해설\' 순서로 적어 주세요. 파일은 CSV, 엑셀, 텍스트를 올릴 수 있어요.'
      : '파일 안에 문제 5~15개와 각 문제의 보기 4개, 정답 번호(1~4), 해설이 들어 있어야 해요. 표 파일은 첫 줄에 \'문제, 보기1, 보기2, 보기3, 보기4, 정답, 해설\' 순서로 적어 주세요. CSV, 엑셀, 텍스트를 올릴 수 있어요.';
    return section(isTest ? 4 : 5, isTest ? '테스트 퀴즈 · OX 5문제' : '퀴즈 · 4지선다 5~15문제',
      isTest ? '영상을 보기 전에 푸는 맛보기예요.\n틀려도 불이익이 없어요.\n문제 파일을 올려 주세요' : '영상을 본 뒤 푸는 퀴즈예요.\n문제 파일을 올려 주세요 (5~15문제)', [
        h('div', { class: 'cc-qfile' }, [
          h('label', { class: 'cc-drop cc-drop--quiz' + (off() || q.reading ? ' is-disabled' : '') }, [file, svg(UPLOAD_ICON, 24), dropText]),
          h('p', { class: 'cc-qfile__err', role: 'alert', text: q.error }),
          info,
          q.questions ? previewList(type, q) : null,
          h('span', { class: 'cc-f__hint', text: hint })
        ])
      ], { text: pill });
  }

  function previewList(type, q) {
    var all = q.questions;
    var shown = q.showAll ? all : all.slice(0, 3);
    var list = h('ol', { class: 'cc-qlist', 'aria-label': '문항 미리 보기' }, shown.map(function (x, i) {
      if (type === 'test') {
        return h('li', {}, [h('span', { class: 'cc-qlist__q', text: (i + 1) + '. ' + x.content }), h('span', { class: 'cc-qlist__a', text: '정답 ' + x.answer }),
          x.explanation ? h('span', { class: 'cc-qlist__e', text: '해설 · ' + x.explanation }) : null]);
      }
      return h('li', {}, [h('span', { class: 'cc-qlist__q', text: (i + 1) + '. ' + x.content }),
        h('ol', { class: 'cc-qlist__c' }, (x.choices || []).map(function (c, k) { return h('li', { class: k === x.answerIndex ? 'is-answer' : '', text: c + (k === x.answerIndex ? ' (정답)' : '') }); })),
        x.explanation ? h('span', { class: 'cc-qlist__e', text: '해설 · ' + x.explanation }) : null]);
    }));
    var more = all.length > 3 ? h('button', { type: 'button', class: 'cc-soft-btn cc-qmore', text: q.showAll ? '접기' : '문제 ' + all.length + '개 모두 보기',
      onclick: function () { q.showAll = !q.showAll; draw(); } }) : null;
    return h('div', { class: 'cc-qfile' }, [list, more]);
  }

  /* 서버(API 150)가 파일을 읽는다. 아직 서버에 없으면(404) CSV·텍스트만 화면에서 읽는다 */
  function readQuizFile(type, f) {
    var q = st.quiz[type];
    q.error = '';
    if (!/\.(csv|xlsx|txt)$/i.test(f.name)) { q.error = 'CSV, 엑셀(xlsx), 텍스트(txt) 파일만 올릴 수 있어요'; draw(); return; }
    if (f.size > 1024 * 1024) { q.error = '문제 파일은 1MB 이하로 올려 주세요'; draw(); return; }
    q.reading = true;
    draw();
    var data = new FormData();
    data.append('file', f);
    data.append('type', type === 'test' ? 'TEST' : 'MAIN');
    MB.api('/api/v1/instructor/quiz-files/parse', { method: 'POST', body: data }).then(function (res) {
      return (res && res.questions) || [];
    }, function (err) {
      if (err && err.status === 404 && /\.(csv|txt)$/i.test(f.name)) return readLocally(type, f);
      if (err && err.status === 404) throw { message: '엑셀 파일은 아직 읽을 수 없어요. CSV(UTF-8)나 텍스트로 저장해 올려 주세요' };
      throw err;
    }).then(function (questions) {
      st.quiz[type] = Object.assign(blankQuiz(), { questions: normalize(type, questions), saved: q.saved, fileName: f.name, fileSize: f.size });
      st.dirty[type] = true;
    }).catch(function (err) {
      q.error = errorText(err, '파일을 읽지 못했어요');
    }).then(function () { st.quiz[type].reading = false; draw(); });
  }

  function normalize(type, questions) {
    return questions.map(function (x) {
      if (type === 'test') return { content: x.content || '', answer: String(x.answer || '').toUpperCase(), explanation: x.explanation || '' };
      return { content: x.content || '', choices: (x.choices || []).slice(0, 4), answerIndex: x.answerIndex, explanation: x.explanation || '' };
    });
  }

  /* 서버 읽기 API 가 없을 때만 쓰는 CSV·텍스트 읽기 (규칙은 API 150 과 같다 : 첫 줄은 '문제'로 시작하는 머리글) */
  function readLocally(type, f) {
    return new Promise(function (resolve, reject) {
      var reader = new FileReader();
      reader.onload = function () {
        try {
          var text = String(reader.result || '').replace(/^﻿/, '');
          var rows = parseCsv(text, /\.txt$/i.test(f.name) && /\t/.test(text.split(/\r?\n/)[0] || '') ? '\t' : ',')
            .map(function (r) { return r.map(function (c) { return c.trim(); }); })
            .filter(function (r) { return r.some(function (c) { return c; }); });
          if (!rows.length || rows[0][0] !== '문제') throw { message: '1번째 줄은 머리글(' + (type === 'test' ? '문제, 정답, 해설' : '문제, 보기1, 보기2, 보기3, 보기4, 정답, 해설') + ')이어야 해요' };
          rows.shift();
          if (type === 'test') {
            if (rows.length !== TEST_COUNT) throw { message: '테스트 퀴즈는 5문제가 필요해요. 파일에는 ' + rows.length + '문제가 있어요' };
            resolve(rows.map(function (r, i) {
              var a = String(r[1] || '').toUpperCase();
              if (a !== 'O' && a !== 'X') throw { message: (i + 2) + '번째 줄: 정답은 O 또는 X 로 써 주세요' };
              if (!r[0] || !r[2]) throw { message: (i + 2) + '번째 줄: 문제와 해설을 채워 주세요' };
              return { content: r[0], answer: a, explanation: r[2] };
            }));
          } else {
            if (rows.length < MAIN_MIN || rows.length > MAIN_MAX) throw { message: '퀴즈는 5~15문제가 필요해요. 파일에는 ' + rows.length + '문제가 있어요' };
            resolve(rows.map(function (r, i) {
              var n = Number(r[5]);
              if (!(n >= 1 && n <= 4)) throw { message: (i + 2) + '번째 줄: 정답은 보기 번호 1~4 중 하나로 써 주세요' };
              if (!r[0] || !r[6] || [1, 2, 3, 4].some(function (k) { return !r[k]; })) throw { message: (i + 2) + '번째 줄: 문제·보기 4개·해설을 채워 주세요' };
              return { content: r[0], choices: [r[1], r[2], r[3], r[4]], answerIndex: n - 1, explanation: r[6] };
            }));
          }
        } catch (e) { reject(e); }
      };
      reader.onerror = function () { reject({ message: '파일을 읽지 못했어요' }); };
      reader.readAsText(f, 'utf-8');
    });
  }

  /* 큰따옴표로 감싼 칸(구분자·줄바꿈 포함)을 지원하는 작은 CSV 읽기 */
  function parseCsv(text, sep) {
    var rows = [], row = [], cell = '', quoted = false;
    for (var i = 0; i < text.length; i++) {
      var ch = text[i];
      if (quoted) {
        if (ch === '"' && text[i + 1] === '"') { cell += '"'; i++; }
        else if (ch === '"') quoted = false;
        else cell += ch;
      } else if (ch === '"') quoted = true;
      else if (ch === sep) { row.push(cell); cell = ''; }
      else if (ch === '\n' || ch === '\r') {
        if (ch === '\r' && text[i + 1] === '\n') i++;
        row.push(cell); rows.push(row); row = []; cell = '';
      } else cell += ch;
    }
    if (cell || row.length) { row.push(cell); rows.push(row); }
    return rows;
  }

  /* 6. 검수 요청 */
  function drawSubmit() {
    return section(6, '검수 요청', '임시 저장하면 ‘작성 중’으로 남고, 검수 요청하면 관리자가 확인해요', [
      h('p', { class: 'cc-error' + (st.statusIsProgress ? ' is-progress' : ''), role: 'alert', 'aria-live': 'polite', 'data-cc-status': '', text: st.status }),
      h('div', { class: 'cc-actions' }, [
        h('a', { class: 'cc-btn cc-btn--ghost', href: BACK, text: '취소' }),
        saveButton(false), saveButton(true)
      ])
    ]);
  }

  function saveButton(submit, small) {
    var requestable = !st.course || st.course.status === 'DRAFT' || st.course.status === 'REJECTED' || st.course.status === 'APPROVED';
    return h('button', { type: 'button', class: 'cc-btn' + (submit ? ' cc-btn--primary' : ''), text: submit ? '검수 요청' : '임시 저장',
      disabled: off() || (submit && !requestable), onclick: function () { submit ? askSubmit() : save(false); } });
  }

  function drawAside() {
    var aside = root.querySelector('[data-cc-aside]');
    if (!aside) return;
    var list = checks();
    var done = list.filter(function (c) { return c.ok; }).length;
    var pct = Math.round(done / list.length * 100);
    aside.replaceChildren(
      h('div', { class: 'cc-card' }, [
        h('div', { class: 'cc-card__top' }, [h('h3', { text: '작성 현황' }), h('strong', { text: pct + '%' })]),
        h('div', { class: 'cc-bar', role: 'progressbar', 'aria-valuemin': '0', 'aria-valuemax': '100', 'aria-valuenow': String(pct), 'aria-label': '작성 현황' },
          [h('span', { style: 'width:' + pct + '%' })]),
        h('ul', { class: 'cc-checks' }, list.map(function (c) {
          return h('li', { class: c.ok ? 'is-ok' : '' }, [h('span', { class: 'cc-checks__mark', 'aria-hidden': 'true', text: c.ok ? '✓' : '' }),
            h('span', { class: 'cc-checks__label', text: c.label + (c.ok ? '' : ' (아직)') }), h('span', { class: 'cc-checks__note', text: c.note })]);
        }))
      ]),
      h('div', { class: 'cc-aside__btns' }, [saveButton(false), saveButton(true)]),
      h('span', { class: 'cc-saved', text: st.savedAt || (isDirty() ? '저장하지 않은 내용이 있어요' : '') })
    );
  }

  function setStatus(text, progress) {
    st.status = text; st.statusIsProgress = !!progress;
    var el = root.querySelector('[data-cc-status]');
    if (el) { el.textContent = text; el.className = 'cc-error' + (progress ? ' is-progress' : ''); }
  }

  /* ---------- 저장 ---------- */

  function askSubmit() {
    var p = problem();
    if (p) { setStatus(p); scrollToStatus(); return; }
    st.modal = true;
    draw();
  }

  function scrollToStatus() {
    var el = root.querySelector('[data-cc-status]');
    if (el && el.scrollIntoView) el.scrollIntoView({ block: 'center', behavior: 'smooth' });
  }

  function drawModal() {
    var close = function () { st.modal = false; draw(); };
    var box = h('div', { class: 'cc-modal__box', role: 'dialog', 'aria-modal': 'true', 'aria-labelledby': 'cc-modal-title' }, [
      h('h2', { id: 'cc-modal-title', text: '검수를 요청할까요?' }),
      h('p', { text: '요청하면 관리자가 영상, 요약글, 퀴즈를 확인해요. 검수 중에는 수정할 수 없고, 허가된 강의만 수강생에게 보여요.' }),
      h('div', { class: 'cc-modal__btns' }, [
        h('button', { type: 'button', class: 'cc-btn cc-btn--ghost', text: '취소', onclick: close }),
        h('button', { type: 'button', class: 'cc-btn cc-btn--primary', text: '요청하기', onclick: function () { st.modal = false; save(true); } })
      ])
    ]);
    box.addEventListener('click', function (e) { e.stopPropagation(); });
    var wrap = h('div', { class: 'cc-modal', onclick: close }, [box]);
    wrap.addEventListener('keydown', function (e) { if (e.key === 'Escape') close(); });
    setTimeout(function () { var b = box.querySelector('.cc-btn--primary'); if (b) b.focus(); }, 0);
    return wrap;
  }

  /* 버튼 한 번에 바뀐 구역을 차례로 저장한다. submit 이면 마지막에 검수를 요청한다 */
  function save(submit) {
    if (st.busy) return;
    var f = st.form;
    if (!f.categoryId) { setStatus('카테고리와 소제목을 골라 주세요'); scrollToStatus(); return; }
    if (!f.title.trim()) { setStatus(submit ? '강의 제목을 입력해 주세요' : '임시 저장하려면 강의 제목을 입력해 주세요'); scrollToStatus(); return; }
    st.busy = true;
    var skipped = [];
    draw();
    setStatus('저장하는 중이에요…', true);

    var chain = Promise.resolve()
      .then(saveInfo)
      .then(saveThumb)
      .then(function () { return saveLessons(submit, skipped); })
      .then(function () { return saveQuiz('test'); })
      .then(function () { return saveQuiz('main'); })
      .then(function () {
        if (!submit) return null;
        setStatus('검수를 요청하는 중이에요…', true);
        // 허가된 강의는 고쳐 저장하는 순간 서버가 다시 검수(REVIEW)로 돌린다. 이미 검수 중이면 요청하지 않는다
        return MB.api('/api/v1/instructor/courses/' + st.courseId).then(function (c) {
          if (c && c.status === 'REVIEW') return null;
          return MB.api('/api/v1/instructor/courses/' + st.courseId + '/review-request', { method: 'POST' });
        });
      });

    chain.then(function () {
      return MB.api('/api/v1/instructor/courses/' + st.courseId).then(function (c) { applyCourse(c, false); }, function () { /* 다음에 다시 맞춘다 */ });
    }).then(function () {
      st.busy = false;
      if (submit) {
        st.done = true; st.doneTitle = f.title.trim(); st.dirty = {};
        MB.toast('검수를 요청했어요. 관리자가 확인하면 알려 드려요.');
        draw(); window.scrollTo(0, 0);
        return;
      }
      var t = new Date().toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit' });
      st.savedAt = t + '에 임시 저장했어요 · 상태: ' + statusLabel();
      st.status = skipped.length ? skipped.join(' ') : ''; st.statusIsProgress = false;
      MB.toast('임시 저장했어요');
      draw();
    }).catch(function (err) {
      st.busy = false;
      st.status = errorText(err, '저장하지 못했어요. 다시 시도해 주세요.'); st.statusIsProgress = false;
      // 앞에서 저장된 구역(강의가 막 만들어진 경우 등)이 화면에 보이도록 서버 상태를 다시 받는다
      var again = st.courseId ? MB.api('/api/v1/instructor/courses/' + st.courseId).then(function (c) { applyCourse(c, false); }, function () {}) : Promise.resolve();
      again.then(function () { draw(); scrollToStatus(); });
    });
  }

  function statusLabel() {
    var s = st.course && st.course.status;
    return s === 'REVIEW' ? '검수 중' : s === 'APPROVED' ? '허가' : s === 'REJECTED' ? '반려' : '작성 중';
  }

  function saveInfo() {
    var f = st.form;
    if (st.courseId && !st.dirty.info) return null;
    var body = { categoryId: f.categoryId, title: f.title.trim(), description: f.description.trim() || null, level: f.level,
      infoBaseDate: f.policy ? (f.infoBaseDate || null) : null, infoSource: f.policy ? (f.infoSource.trim() || null) : null };
    setStatus('강의 정보를 저장하는 중이에요…', true);
    if (!st.courseId) {
      return MB.api('/api/v1/instructor/courses', { method: 'POST', body: body }).then(function (res) {
        st.courseId = res.courseId;
        history.replaceState(null, '', '/instructor/course-create?courseId=' + st.courseId);   // 새로고침해도 이 강의로 이어서
        delete st.dirty.info;
      });
    }
    return MB.api('/api/v1/instructor/courses/' + st.courseId, { method: 'PUT', body: body }).then(function () { delete st.dirty.info; });
  }

  function saveThumb() {
    if (!st.thumb) return null;
    setStatus('썸네일을 올리는 중이에요…', true);
    var data = new FormData();
    data.append('file', st.thumb.file);
    return MB.api('/api/v1/instructor/courses/' + st.courseId + '/thumbnail', { method: 'POST', body: data }).then(function () {
      URL.revokeObjectURL(st.thumb.url);
      st.thumb = null;
      delete st.dirty.thumb;
    });
  }

  /* 영상 : 새로 고른 파일을 하나씩 올린 뒤 1~3개를 한꺼번에 저장한다. 임시 저장 때 덜 채운 영상이 있으면 영상 구역은 건너뛴다 */
  function saveLessons(submit, skipped) {
    if (!st.dirty.lessons) return null;
    var ready = st.lessons.every(function (l) { return l.title.trim() && lessonHasVideo(l) && l.durationSeconds > 0; });
    if (!ready) {
      if (submit) throw { message: '영상의 제목과 영상 파일을 모두 넣어 주세요' };
      skipped.push('영상은 제목과 영상 파일을 모두 넣으면 저장돼요.');
      return null;
    }
    var uploads = st.lessons.filter(function (l) { return l.file; });
    var chain = Promise.resolve();
    uploads.forEach(function (l, n) {
      chain = chain.then(function () {
        return sendVideo(l.file, function (p) { setStatus('영상 ' + (st.lessons.indexOf(l) + 1) + ' 올리는 중 ' + p + '% (' + (n + 1) + ' / ' + uploads.length + ')', true); })
          .then(function (res) { l.videoUrl = res.videoUrl; l.file = null; });
      });
    });
    return chain.then(function () {
      setStatus('영상과 요약글을 저장하는 중이에요…', true);
      var lessons = st.lessons.map(function (l, i) {
        var item = { sortOrder: i + 1, title: l.title.trim(), videoUrl: l.videoUrl, durationSeconds: l.durationSeconds, summary: l.summary.trim() || null };
        if (l.lessonId) item.lessonId = l.lessonId;
        return item;
      });
      var payload = JSON.stringify(lessons);
      // 같은 내용을 다시 보내면 같은 키(응답이 사라졌을 때 영상이 두 번 만들어지지 않게), 내용이 바뀌면 새 키
      if (!st.lessonKey || st.lessonKey.payload !== payload) st.lessonKey = { key: newKey(), payload: payload };
      return MB.api('/api/v1/instructor/courses/' + st.courseId + '/lessons', {
        method: 'PUT', headers: { 'Idempotency-Key': st.lessonKey.key }, body: { lessons: lessons }
      }).then(function (res) {
        (res && res.lessonIds || []).forEach(function (id, i) { if (st.lessons[i]) st.lessons[i].lessonId = id; });
        st.lessonKey = null;
        delete st.dirty.lessons;
      });
    });
  }

  /* 진행률을 보여 주려고 XMLHttpRequest 로 올린다 (CSRF 헤더 포함). 응답은 { data: { videoUrl } } */
  function sendVideo(f, onProgress) {
    return new Promise(function (resolve, reject) {
      var xhr = new XMLHttpRequest();
      xhr.open('POST', '/api/v1/instructor/courses/' + st.courseId + '/lessons/video');
      xhr.setRequestHeader('Accept', 'application/json');
      var token = document.querySelector('meta[name="_csrf"]');
      var header = document.querySelector('meta[name="_csrf_header"]');
      if (token && header) xhr.setRequestHeader(header.content, token.content);
      xhr.upload.onprogress = function (e) { if (e.lengthComputable) onProgress(Math.round(e.loaded / e.total * 100)); };
      xhr.onload = function () {
        var body = null;
        try { body = JSON.parse(xhr.responseText); } catch (e) { /* 본문 없음 */ }
        if (xhr.status === 401) { location.href = '/instructor/login?next=' + encodeURIComponent(location.pathname + location.search); return; }
        if (xhr.status >= 200 && xhr.status < 300 && body && body.data) resolve(body.data);
        else reject((body && body.error) || { message: '영상을 올리지 못했어요.' });
      };
      xhr.onerror = function () { reject({ message: '네트워크 연결을 확인하고 다시 시도해 주세요.' }); };
      var data = new FormData();
      data.append('file', f);
      xhr.send(data);
    });
  }

  function saveQuiz(type) {
    if (!st.dirty[type] || !st.quiz[type].questions) return null;
    var isTest = type === 'test';
    setStatus((isTest ? '테스트 퀴즈' : '퀴즈') + '를 저장하는 중이에요…', true);
    var questions = st.quiz[type].questions.map(function (x) {
      return isTest ? { content: x.content, answer: x.answer, explanation: x.explanation }
        : { content: x.content, choices: x.choices, answerIndex: x.answerIndex, explanation: x.explanation };
    });
    return MB.api('/api/v1/instructor/courses/' + st.courseId + (isTest ? '/test-quiz' : '/quiz'), { method: 'PUT', body: { questions: questions } })
      .then(function () { st.quiz[type].fileName = ''; st.quiz[type].fileSize = 0; delete st.dirty[type]; });
  }

  /* 검수 요청 완료 */
  function drawDone() {
    var icon = h('span', { class: 'cc-done__icon' }, [svg(['m5 12 5 5 9-10'], 30)]);
    icon.firstChild.setAttribute('stroke-width', '2.4');
    return h('section', { class: 'cc-done' }, [
      icon,
      h('h2', { text: '검수를 요청했어요' }),
      h('p', {}, ['‘' + st.doneTitle + '’ 강의를 관리자가 확인하고 있어요.', h('br'), '허가되면 수강생에게 공개되고, 반려되면 사유와 함께 알려 드려요.', h('br'), '검수 중에는 강의를 고칠 수 없어요.']),
      h('div', { class: 'cc-done__btns' }, [
        h('a', { class: 'cc-btn cc-btn--primary', href: BACK, text: '내 강의 관리로' }),
        h('a', { class: 'cc-soft-btn', style: 'height:46px;padding:0 24px;font-size:15px', href: '/instructor/course-create', text: '새 강의 만들기' })
      ])
    ]);
  }

  // 저장하지 않은 내용이 있으면 떠나기 전에 묻는다
  window.addEventListener('beforeunload', function (e) {
    if ((isDirty() && !st.done) || st.busy) {
      e.preventDefault();
      e.returnValue = '';
    }
  });

  st = fresh();
  load();
})();
