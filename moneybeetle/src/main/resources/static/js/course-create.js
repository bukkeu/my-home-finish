/* comment.
*   담당 : DBA ① 신아림 (#60, 강사 강의 만들기·고치기 /instructor/course-create[?courseId=])
*   구역마다 따로 저장한다 (서버 API 가 나뉘어 있다)
*   1 분류·강의 정보 : 처음 저장하면 강의를 만든다(API 73, 작성 중). 이후 고치기(API 75)
*   2 썸네일         : 고르면 바로 올린다(API 77, jpg·png 2MB)
*   3 영상·요약글     : 영상 파일을 한 개씩 먼저 올리고(진행률 표시) 1~3개를 한꺼번에 저장(API 78, Idempotency-Key)
*                      영상 길이는 브라우저가 파일에서 읽는다
*   4 테스트 퀴즈     : OX 5문제·정답·해설 (API 79)
*   5 퀴즈           : 4지선다 5~15문제·정답·해설 (API 80). 4·5는 CSV 파일로 한 번에 채울 수도 있다
*   6 검수 요청       : 저장된 상태(API 74)로 준비가 됐는지 보여 주고 요청(API 81)
*   - 검수 중(REVIEW)이면 모든 입력을 막는다. 반려(REJECTED)면 반려 사유, 허가(APPROVED)면 "고치면 다시 검수" 안내
*   - 저장하지 않은 구역이 있으면 화면을 떠날 때 묻는다
*  */
(function () {
  var root = document.querySelector('[data-cc-root]');
  if (!root || !window.MB) return;

  var LESSON_MAX = 3;
  var TEST_COUNT = 5;
  var MAIN_MIN = 5;
  var MAIN_MAX = 15;
  var STATUS = { DRAFT: '작성 중', REVIEW: '검수 중', APPROVED: '승인', REJECTED: '반려' };

  var params = new URLSearchParams(location.search);
  var st = {
    courseId: Number(params.get('courseId')) || null,
    course: null,          // API 74 응답 (저장된 상태)
    categories: [],
    form: { topId: null, categoryId: null, title: '', description: '', infoBaseDate: '', infoSource: '' },
    lessons: [],           // { key, lessonId, title, videoUrl, durationSeconds, summary, uploading, progress, fileName }
    test: [],              // { content, answer: 'O'|'X'|'', explanation }
    main: [],              // { content, choices: [4], answerIndex: 0..3|null, explanation }
    dirty: {},             // 저장 안 한 구역: info, lessons, test, main
    lessonKey: null        // 영상 저장 Idempotency-Key 와 그때 보낸 내용
  };
  var keySeq = 0;

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

  function fill(el) {
    var nodes = [];
    [].slice.call(arguments, 1).forEach(function add(x) {
      if (Array.isArray(x)) x.forEach(add);
      else if (x !== null && x !== undefined && x !== false) nodes.push(x);
    });
    el.replaceChildren.apply(el, nodes);
  }

  function errorText(err, fallback) {
    if (err && Array.isArray(err.fields) && err.fields.length && err.fields[0].message) return err.fields[0].message;
    return (err && err.message) || fallback;
  }

  function newKey() {
    if (window.crypto && crypto.randomUUID) return crypto.randomUUID();
    return 'k-' + Date.now() + '-' + Math.random().toString(36).slice(2, 10);
  }

  function locked() { return !!(st.course && st.course.status === 'REVIEW'); }

  function markDirty(part) { st.dirty[part] = true; }

  function field(label, input, opts) {
    var o = opts || {};
    var count = o.max ? h('span', { class: 'cc-count', text: (input.value || '').length + ' / ' + o.max.toLocaleString('ko-KR') }) : null;
    if (count) input.addEventListener('input', function () { count.textContent = input.value.length + ' / ' + o.max.toLocaleString('ko-KR'); });
    return h('label', { class: 'ins-field cc-field' }, [
      h('span', { class: 'cc-field__label' }, [h('span', {}, [label, o.required ? h('b', { class: 'cc-req', text: ' *' }) : null]), count]),
      input,
      o.hint ? h('small', { class: 'ins-hint', text: o.hint }) : null
    ]);
  }

  function input(attrs, onInput) {
    var el = h(attrs.rows ? 'textarea' : 'input', Object.assign({ class: 'ins-input' + (attrs.rows ? ' cc-textarea' : ''), disabled: locked() }, attrs));
    if (onInput) el.addEventListener('input', function () { onInput(el.value); });
    return el;
  }

  function section(no, title, desc, body, extra) {
    return h('section', { class: 'ins-panel cc-section', 'aria-labelledby': 'cc-sec-' + no }, [
      h('div', { class: 'cc-section__head' }, [
        h('span', { class: 'cc-num', text: String(no) }),
        h('div', {}, [h('h2', { id: 'cc-sec-' + no, text: title }), desc ? h('p', { class: 'ins-desc', text: desc }) : null]),
        extra || null
      ]),
      body
    ]);
  }

  function saveBar(label, onSave, part) {
    var msg = h('p', { class: 'ins-error', role: 'alert' });
    var unsaved = part && st.dirty[part] ? h('span', { class: 'cc-unsaved', text: '저장하지 않은 내용이 있어요' }) : null;
    var btn = h('button', { type: 'button', class: 'btn btn-primary', text: label, disabled: locked() });
    btn.addEventListener('click', function () {
      if (btn.disabled) return;
      msg.textContent = '';
      btn.disabled = true;
      Promise.resolve().then(onSave).then(function (text) {
        if (part) delete st.dirty[part];
        MB.toast(text || '저장했어요');   // 저장 뒤 화면을 서버 상태로 다시 그리므로 안내는 토스트로
        return reloadCourse();
      }).catch(function (err) {
        msg.textContent = errorText(err, '저장하지 못했어요. 다시 시도해 주세요.');
      }).then(function () { btn.disabled = locked(); });
    });
    return h('div', { class: 'cc-save' }, [msg, h('div', { class: 'cc-save__row' }, [unsaved, btn])]);
  }

  /* ---------- 불러오기 ---------- */

  function load() {
    fill(root, h('div', { class: 'ins-state', text: '강의 정보를 불러오는 중이에요…' }));
    Promise.all([
      MB.api('/api/v1/categories'),
      st.courseId ? MB.api('/api/v1/instructor/courses/' + st.courseId) : Promise.resolve(null)
    ]).then(function (res) {
      st.categories = res[0] || [];
      if (res[1]) applyCourse(res[1], true);
      else resetEditors();
      draw();
    }).catch(function (err) {
      fill(root, h('div', { class: 'ins-state ins-state--error', role: 'alert' }, [
        errorText(err, '강의 정보를 불러오지 못했어요.'), h('br'),
        h('button', { type: 'button', class: 'btn btn-secondary', text: '다시 시도', onclick: load }), ' ',
        h('a', { class: 'btn btn-ghost', href: '/instructor/dashboard?tab=courses', text: '강의 관리로' })
      ]));
    });
  }

  /* 저장 뒤 서버 상태(상태·반려 사유·저장된 영상 ID 등)를 다시 받는다. 저장하지 않은 구역의 입력은 건드리지 않는다 */
  function reloadCourse() {
    if (!st.courseId) return Promise.resolve();
    return MB.api('/api/v1/instructor/courses/' + st.courseId).then(function (c) {
      applyCourse(c, false);
      draw();
    }).catch(function () { /* 다음 저장 때 다시 맞춘다 */ });
  }

  function topOf(categoryId) {
    var top = st.categories.find(function (t) {
      return (t.children || []).some(function (s) { return s.categoryId === categoryId; });
    });
    return top ? top.categoryId : null;
  }

  function resetEditors() {
    st.lessons = [blankLesson()];
    st.test = range(TEST_COUNT).map(blankTest);
    st.main = range(MAIN_MIN).map(blankMain);
  }

  function range(n) { var a = []; for (var i = 0; i < n; i++) a.push(i); return a; }
  function blankLesson() { return { key: ++keySeq, lessonId: null, title: '', videoUrl: '', durationSeconds: 0, summary: '' }; }
  function blankTest() { return { content: '', answer: '', explanation: '' }; }
  function blankMain() { return { content: '', choices: ['', '', '', ''], answerIndex: null, explanation: '' }; }

  function applyCourse(c, first) {
    st.course = c;
    if (first || !st.dirty.info) {
      st.form = {
        topId: topOf(c.categoryId), categoryId: c.categoryId, title: c.title || '', description: c.description || '',
        infoBaseDate: c.infoBaseDate || '', infoSource: c.infoSource || ''
      };
    }
    if (first || !st.dirty.lessons) {
      st.lessons = (c.lessons || []).slice().sort(function (a, b) { return a.sortOrder - b.sortOrder; }).map(function (l) {
        return { key: ++keySeq, lessonId: l.lessonId, title: l.title || '', videoUrl: l.videoUrl || '', durationSeconds: l.durationSeconds || 0, summary: l.summary || '' };
      });
      if (!st.lessons.length) st.lessons = [blankLesson()];
    }
    var quizzes = (c.quizzes || []).slice().sort(function (a, b) { return a.sortOrder - b.sortOrder; });
    if (first || !st.dirty.test) {
      st.test = quizzes.filter(function (q) { return q.quizType === 'TEST'; }).map(function (q) {
        var right = (q.choices || []).find(function (x) { return x.isCorrect === true || x.correct === true; });
        return { content: q.content || '', answer: right ? right.content : '', explanation: q.explanation || '' };
      });
      while (st.test.length < TEST_COUNT) st.test.push(blankTest());
    }
    if (first || !st.dirty.main) {
      st.main = quizzes.filter(function (q) { return q.quizType === 'MAIN'; }).map(function (q) {
        var cs = (q.choices || []).slice().sort(function (a, b) { return a.sortOrder - b.sortOrder; });
        var idx = cs.findIndex(function (x) { return x.isCorrect === true || x.correct === true; });
        return { content: q.content || '', choices: [0, 1, 2, 3].map(function (i) { return cs[i] ? cs[i].content : ''; }),
          answerIndex: idx >= 0 ? idx : null, explanation: q.explanation || '' };
      });
      while (st.main.length < MAIN_MIN) st.main.push(blankMain());
    }
  }

  /* ---------- 화면 ---------- */

  function draw() {
    var c = st.course;
    var title = c ? '강의 고치기' : '강의 만들기';
    document.title = title + ' · Money Beetle';
    var sections = [drawInfo()];
    if (st.courseId) sections.push(drawThumb(), drawLessons(), drawTest(), drawMain(), drawReview());
    else sections.push(h('p', { class: 'cc-next ins-meta', text: '강의 정보를 저장하면 썸네일·영상·퀴즈를 이어서 등록할 수 있어요.' }));

    fill(root,
      h('div', { class: 'ins-head' }, [
        h('div', {}, [
          h('a', { class: 'cc-back', href: '/instructor/dashboard?tab=courses', text: '← 강의 관리' }),
          h('h1', { text: title }),
          c ? h('p', { class: 'ins-desc' }, [h('span', { class: 'ins-badge cc-status cc-status--' + c.status.toLowerCase(), text: STATUS[c.status] || c.status }), ' ', c.title]) : null
        ])
      ]),
      statusNotice(),
      sections
    );
  }

  function statusNotice() {
    var c = st.course;
    if (!c) return null;
    if (c.status === 'REVIEW') return h('div', { class: 'cc-notice cc-notice--review', role: 'note' }, [h('b', { text: '관리자가 검수하고 있어요. ' }), '검수 중에는 고칠 수 없어요. 결과가 나오면 알려 드려요.']);
    if (c.status === 'REJECTED') return h('div', { class: 'cc-notice cc-notice--rejected', role: 'note' }, [
      h('b', { text: '반려된 강의예요. ' }), '아래 사유를 고친 뒤 다시 검수를 요청해 주세요.',
      c.rejectReason ? h('p', { class: 'cc-reason', text: c.rejectReason }) : null
    ]);
    if (c.status === 'APPROVED') return h('div', { class: 'cc-notice', role: 'note' }, ['허가된 강의예요. 내용을 고쳐 저장하면 다시 검수를 받고, 허가될 때까지 수강생에게 보이지 않아요.']);
    return null;
  }

  /* 1. 분류·강의 정보 */
  function drawInfo() {
    var f = st.form;
    var top = st.categories.find(function (t) { return t.categoryId === f.topId; });
    var chips = function (items, selected, onPick, label) {
      return h('div', { class: 'cc-chips', role: 'radiogroup', 'aria-label': label }, items.map(function (it) {
        return h('button', { type: 'button', role: 'radio', class: 'cc-chip', 'aria-checked': String(it.categoryId === selected), disabled: locked(),
          text: it.name, onclick: function () { onPick(it.categoryId); markDirty('info'); draw(); } });
      }));
    };
    var body = h('div', { class: 'cc-stack' }, [
      h('div', { class: 'ins-field cc-field' }, [h('span', { class: 'cc-field__label' }, [h('span', {}, ['카테고리', h('b', { class: 'cc-req', text: ' *' })])]),
        chips(st.categories, f.topId, function (id) { f.topId = id; f.categoryId = null; }, '카테고리')]),
      h('div', { class: 'ins-field cc-field' }, [h('span', { class: 'cc-field__label' }, [h('span', {}, ['소제목', h('b', { class: 'cc-req', text: ' *' })])]),
        top ? chips(top.children || [], f.categoryId, function (id) { f.categoryId = id; }, '소제목')
          : h('small', { class: 'ins-hint', text: '카테고리를 먼저 골라 주세요' })]),
      field('강의 제목', input({ maxlength: '100', value: f.title, placeholder: '예) 청약통장 기초와 가점 계산' }, function (v) { f.title = v; markDirty('info'); }), { required: true, max: 100 }),
      field('강의 소개', input({ rows: '5', maxlength: '2000', value: f.description, placeholder: '이 강의에서 무엇을 배우는지, 어떤 수강생에게 맞는지 적어 주세요' }, function (v) { f.description = v; markDirty('info'); }), { max: 2000 }),
      h('div', { class: 'cc-grid2' }, [
        field('정보 기준일', input({ type: 'date', value: f.infoBaseDate }, function (v) { f.infoBaseDate = v; markDirty('info'); }), { hint: '정책·제도처럼 바뀌는 내용이면 적어 주세요' }),
        field('출처', input({ maxlength: '200', value: f.infoSource, placeholder: '예) 청약홈' }, function (v) { f.infoSource = v; markDirty('info'); }), { max: 200 })
      ]),
      saveBar(st.courseId ? '강의 정보 저장' : '저장하고 계속하기', saveInfo, 'info')
    ]);
    return section(1, '분류와 강의 정보', '카테고리와 소제목을 고르고, 수강생이 가장 먼저 보는 제목과 소개를 적어요.', body);
  }

  function saveInfo() {
    var f = st.form;
    if (!f.categoryId) throw { message: '카테고리와 소제목을 골라 주세요.' };
    if (!f.title.trim()) throw { message: '강의 제목을 입력해 주세요.' };
    var body = { categoryId: f.categoryId, title: f.title.trim(), description: f.description.trim() || null,
      infoBaseDate: f.infoBaseDate || null, infoSource: f.infoSource.trim() || null };
    if (!st.courseId) {
      return MB.api('/api/v1/instructor/courses', { method: 'POST', body: body }).then(function (res) {
        st.courseId = res.courseId;
        history.replaceState(null, '', '/instructor/course-create?courseId=' + st.courseId);   // 새로고침해도 이 강의로 이어서
        return '강의를 만들었어요. 이어서 썸네일과 영상을 등록해 주세요.';
      });
    }
    return MB.api('/api/v1/instructor/courses/' + st.courseId, { method: 'PUT', body: body }).then(function (res) {
      return res && res.status === 'REVIEW' && st.course && st.course.status === 'APPROVED' ? '저장했어요. 다시 검수를 받아요.' : '저장했어요';
    });
  }

  /* 2. 썸네일 */
  function drawThumb() {
    var url = st.course && st.course.thumbnailUrl;
    var msg = h('p', { class: 'ins-error', role: 'alert' });
    var file = h('input', { type: 'file', accept: 'image/png,image/jpeg', class: 'cc-file', disabled: locked() });
    file.addEventListener('change', function () {
      var f = file.files && file.files[0];
      if (!f) return;
      if (f.size > 2 * 1024 * 1024) { msg.textContent = '이미지는 2MB 이하로 올려 주세요.'; file.value = ''; return; }
      var data = new FormData();
      data.append('file', f);
      msg.textContent = '올리는 중이에요…';
      MB.api('/api/v1/instructor/courses/' + st.courseId + '/thumbnail', { method: 'POST', body: data }).then(function () {
        MB.toast('썸네일을 바꿨어요.');
        return reloadCourse();
      }).catch(function (err) { msg.textContent = errorText(err, '썸네일을 올리지 못했어요.'); file.value = ''; });
    });
    var preview = url && /^\/uploads\//.test(url)
      ? h('span', { class: 'cc-thumb', style: 'background-image:url("' + url.replace(/"/g, '') + '")', role: 'img', 'aria-label': '지금 썸네일' })
      : h('span', { class: 'cc-thumb cc-thumb--empty', text: '썸네일 없음' });
    var body = h('div', { class: 'cc-thumb-row' }, [
      preview,
      h('div', { class: 'cc-stack' }, [
        h('label', { class: 'btn btn-secondary cc-file-btn' + (locked() ? ' is-disabled' : '') }, [url ? '이미지 바꾸기' : '이미지 올리기', file]),
        h('small', { class: 'ins-hint', text: 'jpg·png, 2MB 이하. 16:9 비율을 권장해요. 고르면 바로 저장돼요.' }),
        msg
      ])
    ]);
    return section(2, '썸네일', '목록과 상세에 보이는 대표 이미지예요. 선택 사항이에요.', body);
  }

  /* 3. 영상·요약글 */
  function drawLessons() {
    var list = h('div', { class: 'cc-lessons' }, st.lessons.map(function (l, i) { return lessonCard(l, i); }));
    var add = h('button', { type: 'button', class: 'btn btn-secondary', text: '+ 영상 추가', disabled: locked() || st.lessons.length >= LESSON_MAX,
      onclick: function () { st.lessons.push(blankLesson()); markDirty('lessons'); draw(); } });
    var body = h('div', { class: 'cc-stack' }, [
      list,
      h('div', { class: 'cc-row' }, [add, h('span', { class: 'ins-meta', text: '영상은 1~3개, mp4 500MB 이하예요.' })]),
      saveBar('영상·요약글 저장', saveLessons, 'lessons')
    ]);
    var saved = st.course ? (st.course.lessons || []).length : 0;
    return section(3, '영상과 요약글', '영상을 순서대로 올리고 영상마다 요약글을 적어요. 영상 파일을 고르면 바로 올라가고, 저장을 눌러야 강의에 반영돼요.', body,
      h('span', { class: 'cc-tag', text: '저장됨 ' + saved + '개' }));
  }

  function lessonCard(l, i) {
    var msg = h('p', { class: 'ins-error', role: 'alert' });
    var progress = h('span', { class: 'cc-progress', 'aria-live': 'polite', text: l.uploading ? '올리는 중 ' + (l.progress || 0) + '%' : '' });
    var file = h('input', { type: 'file', accept: 'video/mp4', class: 'cc-file', disabled: locked() || l.uploading });
    file.addEventListener('change', function () {
      var f = file.files && file.files[0];
      if (f) uploadVideo(l, f, msg, progress);
    });
    var move = function (d) {
      var j = i + d;
      if (j < 0 || j >= st.lessons.length) return;
      var t = st.lessons[i]; st.lessons[i] = st.lessons[j]; st.lessons[j] = t;
      markDirty('lessons'); draw();
    };
    return h('article', { class: 'cc-card' }, [
      h('div', { class: 'cc-card__head' }, [
        h('b', { text: (i + 1) + '번째 영상' }),
        h('div', { class: 'cc-row' }, [
          h('button', { type: 'button', class: 'btn btn-ghost cc-icon', 'aria-label': '위로', text: '▲', disabled: locked() || i === 0, onclick: function () { move(-1); } }),
          h('button', { type: 'button', class: 'btn btn-ghost cc-icon', 'aria-label': '아래로', text: '▼', disabled: locked() || i === st.lessons.length - 1, onclick: function () { move(1); } }),
          h('button', { type: 'button', class: 'btn btn-ghost ins-danger', text: '삭제', disabled: locked() || st.lessons.length <= 1,
            onclick: function () { st.lessons.splice(i, 1); markDirty('lessons'); draw(); } })
        ])
      ]),
      field('영상 제목', input({ maxlength: '100', value: l.title, placeholder: '예) 1강. 청약통장이 뭐예요?' }, function (v) { l.title = v; markDirty('lessons'); }), { required: true, max: 100 }),
      h('div', { class: 'cc-video' }, [
        l.videoUrl && /^\/uploads\//.test(l.videoUrl)
          ? h('video', { class: 'cc-video__player', controls: true, preload: 'metadata', src: l.videoUrl, 'aria-label': (i + 1) + '번째 영상 미리보기' })
          : h('span', { class: 'cc-video__empty', text: '아직 올린 영상이 없어요' }),
        h('div', { class: 'cc-stack' }, [
          h('label', { class: 'btn btn-secondary cc-file-btn' + (locked() || l.uploading ? ' is-disabled' : '') }, [l.videoUrl ? '영상 바꾸기' : '영상 파일 올리기', file]),
          h('small', { class: 'ins-hint', text: l.fileName ? l.fileName + ' · ' + minutes(l.durationSeconds) : l.durationSeconds ? '길이 ' + minutes(l.durationSeconds) : 'mp4, 500MB 이하' }),
          progress, msg
        ])
      ]),
      field('영상 요약글', input({ rows: '4', maxlength: '2000', value: l.summary, placeholder: '이 영상의 핵심을 수강생이 복습할 수 있게 적어 주세요' }, function (v) { l.summary = v; markDirty('lessons'); }), { max: 2000 })
    ]);
  }

  function minutes(sec) {
    var s = Math.max(0, Math.round(sec || 0));
    return s < 60 ? s + '초' : Math.floor(s / 60) + '분' + (s % 60 ? ' ' + (s % 60) + '초' : '');
  }

  /* 영상 길이는 파일을 브라우저에서 열어 읽는다 (서버 저장 요청에 durationSeconds 가 필요) */
  function readDuration(f) {
    return new Promise(function (resolve, reject) {
      var v = document.createElement('video');
      var url = URL.createObjectURL(f);
      v.preload = 'metadata';
      v.onloadedmetadata = function () { URL.revokeObjectURL(url); resolve(Math.round(v.duration || 0)); };
      v.onerror = function () { URL.revokeObjectURL(url); reject({ message: '영상 길이를 읽지 못했어요. mp4 파일인지 확인해 주세요.' }); };
      v.src = url;
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

  function uploadVideo(l, f, msg, progress) {
    msg.textContent = '';
    if (!/\.mp4$/i.test(f.name) && f.type !== 'video/mp4') { msg.textContent = 'mp4 파일만 올릴 수 있어요.'; return; }
    if (f.size > 500 * 1024 * 1024) { msg.textContent = '영상은 500MB 이하로 올려 주세요.'; return; }
    l.uploading = true; l.progress = 0;
    progress.textContent = '영상 길이를 읽는 중이에요…';
    readDuration(f).then(function (sec) {
      if (sec < 1) throw { message: '영상 길이를 확인해 주세요.' };
      return sendVideo(f, function (p) { l.progress = p; progress.textContent = '올리는 중 ' + p + '%'; }).then(function (res) {
        l.videoUrl = res.videoUrl;
        l.durationSeconds = sec;
        l.fileName = f.name;
        markDirty('lessons');
      });
    }).catch(function (err) {
      msg.textContent = errorText(err, '영상을 올리지 못했어요.');
    }).then(function () {
      l.uploading = false;
      if (!msg.textContent) { MB.toast('영상을 올렸어요. 저장을 눌러야 강의에 반영돼요.'); draw(); }
      else progress.textContent = '';
    });
  }

  function saveLessons() {
    if (st.lessons.some(function (l) { return l.uploading; })) throw { message: '영상을 다 올린 뒤에 저장해 주세요.' };
    var lessons = st.lessons.map(function (l, i) {
      if (!l.title.trim()) throw { message: (i + 1) + '번째 영상의 제목을 입력해 주세요.' };
      if (!l.videoUrl) throw { message: (i + 1) + '번째 영상 파일을 올려 주세요.' };
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
      return '영상 ' + lessons.length + '개를 저장했어요';
    });
  }

  /* 4. 테스트 퀴즈 (OX 5문제) */
  function drawTest() {
    var cards = st.test.map(function (q, i) {
      return h('article', { class: 'cc-card' }, [
        h('div', { class: 'cc-card__head' }, [h('b', { text: '문제 ' + (i + 1) })]),
        field('문제', input({ maxlength: '500', value: q.content, placeholder: '예) 청약통장은 1인 1개만 만들 수 있다.' }, function (v) { q.content = v; markDirty('test'); }), { required: true }),
        h('div', { class: 'ins-field cc-field' }, [h('span', { class: 'cc-field__label' }, [h('span', {}, ['정답', h('b', { class: 'cc-req', text: ' *' })])]),
          h('div', { class: 'cc-chips', role: 'radiogroup', 'aria-label': '문제 ' + (i + 1) + ' 정답' }, ['O', 'X'].map(function (a) {
            return h('button', { type: 'button', role: 'radio', class: 'cc-chip cc-chip--ox', 'aria-checked': String(q.answer === a), text: a, disabled: locked(),
              onclick: function () { q.answer = a; markDirty('test'); draw(); } });
          }))]),
        field('해설', input({ rows: '2', value: q.explanation, placeholder: '정답인 이유를 짧게 적어 주세요' }, function (v) { q.explanation = v; markDirty('test'); }), { required: true })
      ]);
    });
    var body = h('div', { class: 'cc-stack' }, [
      csvImport('test'),
      h('div', { class: 'cc-quiz' }, cards),
      saveBar('테스트 퀴즈 저장', saveTest, 'test')
    ]);
    return section(4, '테스트 퀴즈 · OX 5문제', '영상을 보기 전에 푸는 맛보기예요. 틀려도 불이익이 없어요.', body, quizTag('TEST'));
  }

  function quizTag(type) {
    var n = st.course ? (st.course.quizzes || []).filter(function (q) { return q.quizType === type; }).length : 0;
    return h('span', { class: 'cc-tag', text: '저장됨 ' + n + '문제' });
  }

  function saveTest() {
    var questions = st.test.map(function (q, i) {
      if (!q.content.trim() || !q.answer || !q.explanation.trim()) throw { message: '문제 ' + (i + 1) + '의 문제·정답·해설을 모두 채워 주세요.' };
      return { content: q.content.trim(), answer: q.answer, explanation: q.explanation.trim() };
    });
    return MB.api('/api/v1/instructor/courses/' + st.courseId + '/test-quiz', { method: 'PUT', body: { questions: questions } })
      .then(function (res) { return '테스트 퀴즈 ' + ((res && res.count) || questions.length) + '문제를 저장했어요'; });
  }

  /* 5. 퀴즈 (4지선다 5~15문제) */
  function drawMain() {
    var cards = st.main.map(function (q, i) {
      return h('article', { class: 'cc-card' }, [
        h('div', { class: 'cc-card__head' }, [
          h('b', { text: '문제 ' + (i + 1) }),
          h('button', { type: 'button', class: 'btn btn-ghost ins-danger', text: '삭제', disabled: locked() || st.main.length <= MAIN_MIN,
            onclick: function () { st.main.splice(i, 1); markDirty('main'); draw(); } })
        ]),
        field('문제', input({ maxlength: '500', value: q.content, placeholder: '예) 청약 가점 항목이 아닌 것은?' }, function (v) { q.content = v; markDirty('main'); }), { required: true }),
        h('div', { class: 'cc-choices', role: 'group', 'aria-label': '문제 ' + (i + 1) + ' 보기' }, q.choices.map(function (c, k) {
          return h('div', { class: 'cc-choice' }, [
            h('button', { type: 'button', role: 'radio', class: 'cc-choice__mark', 'aria-checked': String(q.answerIndex === k), 'aria-label': '보기 ' + (k + 1) + '을 정답으로',
              text: q.answerIndex === k ? '✓' : String(k + 1), disabled: locked(), onclick: function () { q.answerIndex = k; markDirty('main'); draw(); } }),
            input({ maxlength: '200', value: c, placeholder: '보기 ' + (k + 1), 'aria-label': '보기 ' + (k + 1) }, function (v) { q.choices[k] = v; markDirty('main'); })
          ]);
        })),
        h('small', { class: 'ins-hint', text: '왼쪽 번호를 눌러 정답을 골라 주세요' }),
        field('해설', input({ rows: '2', value: q.explanation, placeholder: '정답인 이유를 짧게 적어 주세요' }, function (v) { q.explanation = v; markDirty('main'); }), { required: true })
      ]);
    });
    var body = h('div', { class: 'cc-stack' }, [
      csvImport('main'),
      h('div', { class: 'cc-quiz' }, cards),
      h('div', { class: 'cc-row' }, [
        h('button', { type: 'button', class: 'btn btn-secondary', text: '+ 문제 추가', disabled: locked() || st.main.length >= MAIN_MAX,
          onclick: function () { st.main.push(blankMain()); markDirty('main'); draw(); } }),
        h('span', { class: 'ins-meta', text: st.main.length + ' / ' + MAIN_MAX + '문제 (5문제 이상)' })
      ]),
      saveBar('퀴즈 저장', saveMain, 'main')
    ]);
    return section(5, '퀴즈 · 4지선다 5~15문제', '영상을 본 뒤 푸는 퀴즈예요. 70점 이상이면 학습 완료예요.', body, quizTag('MAIN'));
  }

  function saveMain() {
    var questions = st.main.map(function (q, i) {
      if (!q.content.trim() || q.choices.some(function (c) { return !c.trim(); }) || q.answerIndex === null || !q.explanation.trim()) {
        throw { message: '문제 ' + (i + 1) + '의 문제·보기 4개·정답·해설을 모두 채워 주세요.' };
      }
      return { content: q.content.trim(), choices: q.choices.map(function (c) { return c.trim(); }), answerIndex: q.answerIndex, explanation: q.explanation.trim() };
    });
    return MB.api('/api/v1/instructor/courses/' + st.courseId + '/quiz', { method: 'PUT', body: { questions: questions } })
      .then(function (res) { return '퀴즈 ' + ((res && res.count) || questions.length) + '문제를 저장했어요'; });
  }

  /* CSV(또는 쉼표로 나눈 텍스트) 파일로 문제를 한 번에 채운다. 채운 뒤 저장을 눌러야 반영된다
   *   테스트 퀴즈: 문제, 정답(O/X), 해설   /   퀴즈: 문제, 보기1, 보기2, 보기3, 보기4, 정답 번호(1~4), 해설 */
  function csvImport(type) {
    var msg = h('p', { class: 'ins-meta', 'aria-live': 'polite' });
    var file = h('input', { type: 'file', accept: '.csv,.txt,text/csv,text/plain', class: 'cc-file', disabled: locked() });
    file.addEventListener('change', function () {
      var f = file.files && file.files[0];
      if (!f) return;
      var reader = new FileReader();
      reader.onload = function () {
        try {
          var rows = parseCsv(String(reader.result || '')).filter(function (r) { return r.some(function (c) { return c.trim(); }); });
          if (rows.length && /문제/.test(rows[0][0] || '')) rows.shift();   // 첫 줄이 제목 줄이면 건너뛴다
          if (type === 'test') {
            if (rows.length !== TEST_COUNT) throw new Error('테스트 퀴즈는 문제가 정확히 5개여야 해요. (지금 ' + rows.length + '개)');
            st.test = rows.map(function (r, i) {
              var a = String(r[1] || '').trim().toUpperCase();
              if (a !== 'O' && a !== 'X') throw new Error((i + 1) + '번째 줄의 정답은 O 또는 X 로 적어 주세요.');
              return { content: (r[0] || '').trim(), answer: a, explanation: (r[2] || '').trim() };
            });
          } else {
            if (rows.length < MAIN_MIN || rows.length > MAIN_MAX) throw new Error('퀴즈는 문제가 5~15개여야 해요. (지금 ' + rows.length + '개)');
            st.main = rows.map(function (r, i) {
              var n = Number(String(r[5] || '').trim());
              if (!(n >= 1 && n <= 4)) throw new Error((i + 1) + '번째 줄의 정답은 1~4 중 하나로 적어 주세요.');
              return { content: (r[0] || '').trim(), choices: [1, 2, 3, 4].map(function (k) { return (r[k] || '').trim(); }), answerIndex: n - 1, explanation: (r[6] || '').trim() };
            });
          }
          markDirty(type);
          draw();
          MB.toast('파일에서 ' + rows.length + '문제를 채웠어요. 확인한 뒤 저장해 주세요.');
        } catch (e) {
          msg.textContent = e.message || '파일을 읽지 못했어요.';
          file.value = '';
        }
      };
      reader.onerror = function () { msg.textContent = '파일을 읽지 못했어요.'; };
      reader.readAsText(f, 'utf-8');
    });
    return h('div', { class: 'cc-import' }, [
      h('label', { class: 'btn btn-ghost cc-file-btn' + (locked() ? ' is-disabled' : '') }, ['CSV 파일로 채우기', file]),
      h('small', { class: 'ins-hint', text: type === 'test'
        ? '첫 줄에 "문제, 정답, 해설" 순서로 적고 5줄을 채워 주세요. 정답은 O 또는 X (UTF-8 CSV)'
        : '첫 줄에 "문제, 보기1, 보기2, 보기3, 보기4, 정답, 해설" 순서로 적고 5~15줄을 채워 주세요. 정답은 1~4 (UTF-8 CSV)' }),
      msg
    ]);
  }

  /* 큰따옴표로 감싼 칸(쉼표·줄바꿈 포함)을 지원하는 작은 CSV 읽기 */
  function parseCsv(text) {
    var rows = [], row = [], cell = '', quoted = false;
    text = text.replace(/^﻿/, '');
    for (var i = 0; i < text.length; i++) {
      var ch = text[i];
      if (quoted) {
        if (ch === '"' && text[i + 1] === '"') { cell += '"'; i++; }
        else if (ch === '"') quoted = false;
        else cell += ch;
      } else if (ch === '"') quoted = true;
      else if (ch === ',') { row.push(cell); cell = ''; }
      else if (ch === '\n' || ch === '\r') {
        if (ch === '\r' && text[i + 1] === '\n') i++;
        row.push(cell); rows.push(row); row = []; cell = '';
      } else cell += ch;
    }
    if (cell || row.length) { row.push(cell); rows.push(row); }
    return rows;
  }

  /* 6. 검수 요청 */
  function drawReview() {
    var c = st.course || {};
    var lessons = (c.lessons || []).length;
    var tests = (c.quizzes || []).filter(function (q) { return q.quizType === 'TEST'; }).length;
    var mains = (c.quizzes || []).filter(function (q) { return q.quizType === 'MAIN'; }).length;
    var checks = [
      { ok: !!c.title, text: '강의 정보' },
      { ok: lessons >= 1, text: '영상 ' + lessons + '개 (1~3개)' },
      { ok: tests === TEST_COUNT, text: '테스트 퀴즈 ' + tests + '문제 (5문제)' },
      { ok: mains >= MAIN_MIN && mains <= MAIN_MAX, text: '퀴즈 ' + mains + '문제 (5~15문제)' }
    ];
    var unsaved = Object.keys(st.dirty).length > 0;
    var ready = checks.every(function (x) { return x.ok; }) && !unsaved;
    var requestable = c.status === 'DRAFT' || c.status === 'REJECTED';
    var msg = h('p', { class: 'ins-error', role: 'alert' });
    var btn = h('button', { type: 'button', class: 'btn btn-primary', text: '검수 요청', disabled: !requestable || !ready });
    btn.addEventListener('click', function () {
      if (btn.disabled) return;
      btn.disabled = true; msg.textContent = '';
      MB.api('/api/v1/instructor/courses/' + st.courseId + '/review-request', { method: 'POST' }).then(function () {
        MB.toast('검수를 요청했어요. 관리자가 확인하면 알려 드려요.');
        return reloadCourse();
      }, function (err) {
        msg.textContent = errorText(err, '검수를 요청하지 못했어요.');
        btn.disabled = false;
      });
    });
    var body = h('div', { class: 'cc-stack' }, [
      h('ul', { class: 'cc-checks' }, checks.map(function (x) {
        return h('li', { class: x.ok ? 'is-ok' : '' }, [h('span', { class: 'cc-check', 'aria-hidden': 'true', text: x.ok ? '✓' : '·' }), x.text, x.ok ? null : ' (아직)']);
      })),
      unsaved ? h('p', { class: 'ins-meta cc-warn', text: '저장하지 않은 구역이 있어요. 각 구역의 저장 버튼을 먼저 눌러 주세요.' }) : null,
      !requestable ? h('p', { class: 'ins-meta', text: c.status === 'REVIEW' ? '검수 중이에요.' : '허가된 강의는 고쳐서 저장하면 자동으로 다시 검수를 받아요.' }) : null,
      msg,
      h('div', { class: 'cc-save__row' }, [
        h('a', { class: 'btn btn-ghost', href: '/instructor/dashboard?tab=courses', text: '강의 관리로' }),
        btn
      ])
    ]);
    return section(6, '검수 요청', '모두 저장했으면 검수를 요청해요. 관리자가 허가하면 수강생에게 공개돼요.', body);
  }

  // 저장하지 않은 내용이 있으면 떠나기 전에 묻는다
  window.addEventListener('beforeunload', function (e) {
    if (Object.keys(st.dirty).length || st.lessons.some(function (l) { return l.uploading; })) {
      e.preventDefault();
      e.returnValue = '';
    }
  });

  load();
})();
