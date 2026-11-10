/* comment.
*   담당 : DBA ① 신아림 (#52 강의 상세·영상 시청, #53 테스트 퀴즈·최종 퀴즈 /user/lecture-detail?courseId=)
*   - 강의 정보·목차(시청 전 테스트 → 영상 → 최종 퀴즈)·진도율·스크랩 버튼
*   - 학습 순서 잠금(화면설계 5.2.4, NFR-09): 테스트(OX)를 풀어야 영상이, 영상을 모두 봐야 최종 퀴즈가 열린다.
*     잠긴 단계는 목차에서 흐리게 보이고 누르면 먼저 할 일을 알려 준다
*     · 테스트는 서버에 기록하지 않으므로(API 40) 푼 강의는 브라우저(localStorage mb-ox-done)에 남긴다.
*       이미 영상을 본 강의(학습 기록이 있음)는 테스트를 다시 요구하지 않는다
*     · 최종 퀴즈 잠금은 서버도 막는다(QUIZ_LOCKED). 퀴즈 화면은 course-quiz.js (window.MBLecQuiz)
*   - 영상 재생: 처음 열면 마지막으로 본 영상(없으면 아직 안 본 첫 영상)을 고르고, 서버가 준 이어보기 위치부터 재생한다
*   - 시청 위치 저장(API 33): 재생 중 15초마다, 일시정지·끝·화면을 떠날 때. 완료 여부는 서버가 본 시간으로 정한다(#20)
*     → 끝까지 본 뒤에는 강의 정보를 다시 받아 목차·진도율을 서버 기준으로 갱신한다
*  */
(function () {
  var root = document.querySelector('[data-lec-detail]');
  if (!root || !window.MB || !window.MBLec || !window.MBLecQuiz) return;
  var L = window.MBLec;
  var h = L.h;

  var SAVE_EVERY_MS = 15000;
  var OX_KEY = 'mb-ox-done';   // 시청 전 테스트를 마친 강의 ID 목록 (브라우저 저장소)
  var MAX_STEP = 2;           // timeupdate 사이 이만큼(초)보다 크게 뛰면 건너뛰기로 본다 (2배속까지는 재생으로 인정)
  var courseId = Number(new URLSearchParams(location.search).get('courseId'));
  var body = root.querySelector('[data-lec-body]');
  var back = root.querySelector('[data-lec-back]');

  var state = {
    course: null, path: null, current: null, play: null, tab: 'summary', tocOpen: false,
    view: 'lesson', oxDone: false,   // view: 'ox'(시청 전 테스트) | 'lesson'(영상) | 'quiz'(최종 퀴즈)
    watched: 0, lastTime: 0, played: false, lastSavedAt: 0, pendingSaves: 0, refreshed: false
  };
  var video = null;
  var videoLessonId = null;   // 지금 video 요소가 속한 영상 ID (영상을 바꾸는 사이 옛 video 의 값이 새 영상으로 저장되지 않게)
  var saveQueue = Promise.resolve();   // 시청 위치 저장 요청을 보낸 순서대로 처리하기 위한 줄

  if (!courseId) {
    body.replaceChildren(h('div', { class: 'lec-state', text: '강의를 찾을 수 없어요. 강의 목록에서 다시 골라 주세요.' }));
    return;
  }

  /* ---------- 불러오기 ---------- */

  function load() {
    body.replaceChildren(h('div', { class: 'lec-state', text: '강의를 불러오는 중이에요…' }));
    Promise.all([
      MB.api('/api/v1/courses/' + courseId),
      MB.api('/api/v1/categories').catch(function () { return []; })   // 경로 표시용이라 실패해도 화면은 연다
    ]).then(function (res) {
      state.course = res[0];
      state.path = findPath(res[1] || [], state.course.categoryId);
      state.current = pickStartLesson(state.course);
      state.oxDone = isOxDone();
      render();
      showView(state.oxDone ? 'lesson' : 'ox', true);
    }).catch(function (err) {
      body.replaceChildren(L.errorBox(err, load));
    });
  }

  /* 소주제 ID로 '상위 카테고리 › 소주제' 와 목록 주소를 찾는다 */
  function findPath(categories, subId) {
    for (var i = 0; i < categories.length; i++) {
      var sub = (categories[i].children || []).find(function (s) { return s.categoryId === subId; });
      if (sub) return { top: categories[i], sub: sub, href: '/user/lectures?categoryId=' + categories[i].categoryId };
    }
    return null;
  }

  /* 처음 열 영상: 마지막으로 본 영상 → 아직 안 본 첫 영상 → 첫 영상 */
  function pickStartLesson(course) {
    var lessons = course.lessons || [];
    return lessons.find(function (l) { return l.lessonId === course.lastLessonId; })
        || lessons.find(function (l) { return !l.isCompleted; })
        || lessons[0] || null;
  }

  /* 시청 전 테스트를 마쳤는지: 이 브라우저에서 푼 적이 있거나, 이미 영상을 보기 시작한 강의 */
  function isOxDone() {
    var c = state.course;
    if (c.progressStatus || completedCount() > 0) return true;
    try { return (JSON.parse(localStorage.getItem(OX_KEY) || '[]') || []).indexOf(courseId) >= 0; } catch (e) { return false; }
  }

  function markOxDone() {
    state.oxDone = true;
    try {
      var done = JSON.parse(localStorage.getItem(OX_KEY) || '[]') || [];
      if (done.indexOf(courseId) < 0) localStorage.setItem(OX_KEY, JSON.stringify(done.concat(courseId).slice(-200)));
    } catch (e) { /* 저장소가 막혀 있어도 이번 화면에서는 열린다 */ }
    renderSide();
  }

  function leftLessons() {
    return (state.course.lessons || []).length - completedCount();
  }

  function backHref() {
    return state.path ? state.path.href : '/user/lectures';
  }

  /* 저장 뒤 강의 정보만 다시 받아 목차·진도율을 서버 기준으로 바꾼다 (재생 중인 영상은 그대로) */
  function refreshCourse() {
    return MB.api('/api/v1/courses/' + courseId).then(function (course) {
      state.course = course;
      if (state.current) {
        state.current = course.lessons.find(function (l) { return l.lessonId === state.current.lessonId; }) || state.current;
      }
      renderSide();
      if (state.view === 'lesson') renderNow();
    }).catch(function () { /* 다음 저장 때 다시 맞춰진다 */ });
  }

  /* ---------- 그리기 ---------- */

  var refs = {};

  function render() {
    var c = state.course;
    document.title = c.title + ' · Money Beetle';
    if (state.path) {
      back.href = state.path.href;
      back.textContent = '← ' + state.path.top.name;
    }
    var lessons = c.lessons || [];

    refs.progress = h('div', { class: 'lec-done' });
    refs.scrap = h('button', { type: 'button', class: 'btn btn-secondary lec-scrap', onclick: toggleScrap });
    refs.side = h('aside', { class: 'lec-panel lec-toc', 'aria-label': '강의 목차' });
    refs.now = h('div', { class: 'lec-now' });
    refs.playerBox = h('div');
    refs.content = h('div');
    refs.main = h('div', { class: 'lec-panel' });

    var head = h('section', { class: 'lec-head' }, [
      h('div', { class: 'lec-head__text' }, [
        h('span', { class: 'lec-crumb', text: '강의 › ' + (state.path ? state.path.top.name + ' › ' + state.path.sub.name : (c.categoryName || '')) }),
        h('h1', { text: c.title }),
        h('div', { class: 'lec-head__meta' }, [
          c.instructorName ? h('span', { text: c.instructorName + ' 강사' }) : null,
          h('span', { text: '영상 ' + lessons.length + '개' }),
          c.infoBaseDate ? h('span', { text: '정보 기준일 ' + c.infoBaseDate + (c.infoSource ? ' · 출처 ' + c.infoSource : '') }) : null
        ]),
        c.description ? h('p', { class: 'lec-head__desc', text: c.description }) : null
      ]),
      h('div', { class: 'lec-head__side' }, [refs.scrap, refs.progress])
    ]);

    body.replaceChildren(head, h('div', { class: 'lec-layout' }, [refs.main, refs.side]));
    renderScrap();
    renderSide();
  }

  /* 가운데 학습 영역을 바꾼다: 시청 전 테스트 / 영상 / 최종 퀴즈 */
  function showView(view, first) {
    if (state.view === 'lesson' && view !== 'lesson') leaveLesson();
    state.view = view;
    if (view === 'ox') {
      var oxBox = h('div', { class: 'lec-quiz' });
      refs.main.replaceChildren(oxBox);
      MBLecQuiz.renderTest(oxBox, { courseId: courseId, onUnlocked: markOxDone, onGoLessons: function () { showView('lesson'); } });
    } else if (view === 'quiz') {
      var quizBox = h('div', { class: 'lec-quiz' });
      refs.main.replaceChildren(quizBox);
      MBLecQuiz.renderQuiz(quizBox, { courseId: courseId, leftLessons: leftLessons, backHref: backHref,
        onGoLessons: goFirstUnwatched, onResult: function () { refreshCourse(); } });
    } else if (!state.current) {
      refs.main.replaceChildren(h('div', { class: 'lec-state', text: '아직 등록된 영상이 없어요.' }));
    } else {
      refs.main.replaceChildren(refs.now, refs.playerBox, refs.content);
      openLesson(state.current, first);
    }
    renderSide();
    if (!first) refs.main.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
  }

  /* 아직 시청 완료가 아닌 첫 영상으로 */
  function goFirstUnwatched() {
    var lesson = (state.course.lessons || []).find(function (l) { return !l.isCompleted; });
    if (lesson) state.current = lesson;
    showView('lesson');
  }

  /* 영상 화면을 떠날 때: 지금 위치를 저장하고 재생을 멈춘다 (멈춤 이벤트가 다시 저장하지 않게 videoLessonId 를 비운다) */
  function leaveLesson() {
    if (!video) return;
    saveProgress(false, { force: true });
    videoLessonId = null;
    video.pause();
  }

  /* 목차·버튼에서 영상을 고를 때 */
  function pickLesson(lesson) {
    if (!state.oxDone) { MB.toast('시청 전 테스트(OX)를 먼저 풀면 영상이 열려요.'); return; }
    if (state.view !== 'lesson') { state.current = lesson; showView('lesson'); return; }
    if (state.current && state.current.lessonId === lesson.lessonId) return;
    saveProgress(false, { force: true });
    openLesson(lesson, false);
  }

  function pickQuiz() {
    if (!state.oxDone) { MB.toast('시청 전 테스트(OX)를 먼저 풀어 주세요.'); return; }
    if (leftLessons() > 0) { MB.toast('영상을 모두 끝까지 보면 최종 퀴즈가 열려요.'); return; }
    if (state.view !== 'quiz') showView('quiz');
  }

  function completedCount() {
    return (state.course.lessons || []).filter(function (l) { return l.isCompleted; }).length;
  }

  function renderSide() {
    var c = state.course;
    var lessons = c.lessons || [];
    var done = completedCount();
    var pct = L.percent(done, lessons.length);
    var statusText = c.progressStatus === 'COMPLETED' ? '학습 완료' : done > 0 || c.progressStatus ? '학습 중' : '시작 전';

    refs.progress.replaceChildren(
      h('div', { class: 'lec-done__row' }, [
        h('span', { class: 'lec-done__label', text: statusText }),
        h('span', { class: 'lec-done__num' }, [h('b', { text: pct + '%' })])
      ]),
      h('div', { class: 'lec-bar' }, [h('span', { style: 'width:' + pct + '%' })]),
      h('span', { class: 'lec-meta', text: '영상 ' + done + ' / ' + lessons.length + ' 시청 완료' })
    );

    var toggle = h('button', { type: 'button', class: 'btn btn-ghost lec-toc__toggle', 'aria-expanded': String(state.tocOpen),
      text: state.tocOpen ? '접기' : '펼치기',
      onclick: function () { state.tocOpen = !state.tocOpen; renderSide(); } });
    refs.side.classList.toggle('is-open', state.tocOpen);

    var quizLocked = !state.oxDone || leftLessons() > 0;
    var completed = c.progressStatus === 'COMPLETED';

    function step(opts) {
      return h('button', { type: 'button', class: 'lec-step' + (opts.done ? ' lec-step--done' : '') + (opts.locked ? ' lec-step--locked' : ''),
        'aria-current': opts.now ? 'step' : null, 'aria-disabled': opts.locked ? 'true' : null, onclick: opts.onclick }, [
        h('span', { class: 'lec-step__mark', text: opts.done ? '✓' : opts.locked ? '🔒' : opts.mark }),
        h('span', { class: 'lec-step__text' }, [
          h('span', { class: 'lec-step__title', text: opts.title }),
          h('span', { class: 'lec-step__sub', text: opts.sub })
        ])
      ]);
    }

    var list = [
      h('div', { class: 'lec-toc__group', text: '시청 전 테스트' }),
      step({ mark: 'OX', title: 'OX 테스트', done: state.oxDone, now: state.view === 'ox',
        sub: state.oxDone ? '완료 · 다시 풀 수 있어요' : '불이익 없음 · 풀면 영상이 열려요', onclick: function () { if (state.view !== 'ox') showView('ox'); } }),
      h('div', { class: 'lec-toc__group', text: '영상 ' + lessons.length + '개' })
    ].concat(lessons.map(function (l, i) {
      var isNow = state.view === 'lesson' && state.current && state.current.lessonId === l.lessonId;
      return step({ mark: String(i + 1), title: l.title, done: l.isCompleted, locked: !state.oxDone, now: isNow,
        sub: !state.oxDone ? '잠김 · 테스트를 먼저 풀어요'
          : L.minutes(l.durationSeconds) + (l.isCompleted ? ' · 시청 완료' : isNow ? ' · 보는 중' : ''),
        onclick: function () { pickLesson(l); } });
    })).concat([
      h('div', { class: 'lec-toc__group', text: '최종 퀴즈' }),
      step({ mark: 'Q', title: '4지선다 퀴즈', done: completed, locked: quizLocked, now: state.view === 'quiz',
        sub: completed ? '통과 · 다시 풀 수 있어요' : quizLocked ? '잠김 · 영상을 모두 보면 열려요' : '70점 이상이면 통과',
        onclick: pickQuiz }),
      h('div', { class: 'lec-goal' }, [
        h('span', { class: 'lec-step__mark', text: completed ? '✓' : '★' }),
        h('span', { class: 'lec-step__text' }, [
          h('span', { class: 'lec-step__title', text: '학습 완료' }),
          h('span', { class: 'lec-step__sub', text: '영상을 모두 보고 퀴즈 70점 이상' })
        ])
      ])
    ]);

    refs.side.replaceChildren(
      h('div', { class: 'lec-toc__head' }, [h('h3', { text: '강의 목차' }), h('span', { class: 'lec-meta', text: done + ' / ' + lessons.length }), toggle]),
      h('div', { class: 'lec-bar lec-bar--thin' }, [h('span', { style: 'width:' + pct + '%' })]),
      h('div', { class: 'lec-toc__list' }, list)
    );
  }

  function renderScrap() {
    var on = !!state.course.isScrapped;
    refs.scrap.setAttribute('aria-pressed', String(on));
    refs.scrap.textContent = on ? '★ 스크랩함' : '☆ 스크랩';
  }

  function lessonIndex() {
    return state.course.lessons.findIndex(function (l) { return l.lessonId === state.current.lessonId; });
  }

  function renderNow() {
    if (!state.current || !refs.now) return;
    var lessons = state.course.lessons;
    var idx = lessonIndex();
    var prev = lessons[idx - 1];
    var next = lessons[idx + 1];
    refs.now.replaceChildren(
      h('span', { class: 'lec-now__chip', text: '영상 ' + (idx + 1) }),
      h('div', { class: 'lec-now__text' }, [
        h('span', { class: 'lec-now__idx', text: '학습 ' + (idx + 1) + ' / ' + lessons.length }),
        h('span', { class: 'lec-now__title', text: state.current.title })
      ]),
      h('div', { class: 'lec-now__nav' }, [
        h('button', { type: 'button', class: 'btn btn-secondary', disabled: !prev, text: '‹ 이전',
          onclick: function () { if (prev) pickLesson(prev); } }),
        h('button', { type: 'button', class: 'btn btn-secondary', disabled: !next, text: '다음 ›',
          onclick: function () { if (next) pickLesson(next); } })
      ])
    );
    renderContent();
  }

  function renderContent() {
    var lessons = state.course.lessons;
    var idx = lessonIndex();
    var next = lessons[idx + 1];
    var allDone = completedCount() === lessons.length;
    var summary = (state.current.summary || '').trim();
    refs.content.replaceChildren(
      h('div', { class: 'lec-content-tabs', role: 'tablist' }, [
        h('button', { type: 'button', role: 'tab', 'aria-selected': 'true', text: '영상 요약글' })
      ]),
      h('div', { class: 'lec-summary', role: 'tabpanel' }, [
        h('span', { class: 'lec-summary__title', text: '영상 ' + (idx + 1) + ' 요약' }),
        summary ? h('p', { class: 'lec-summary__body', text: summary }) : h('p', { class: 'lec-summary__body lec-muted', text: '이 영상에는 요약글이 없어요.' }),
        h('div', { class: 'lec-summary__actions' }, [
          next ? h('button', { type: 'button', class: 'btn btn-primary', text: '다음 영상 보기',
            onclick: function () { pickLesson(next); } }) : null,
          !next && allDone ? h('button', { type: 'button', class: 'btn btn-primary', text: '최종 퀴즈 풀기', onclick: pickQuiz }) : null,
          !next && allDone ? h('span', { class: 'lec-meta', text: '영상을 모두 봤어요! 최종 퀴즈에서 70점 이상이면 학습 완료예요.' }) : null,
          !next && !allDone ? h('span', { class: 'lec-meta', text: '영상을 모두 끝까지 보면 최종 퀴즈가 열려요.' }) : null
        ])
      ])
    );
  }

  /* ---------- 영상 재생·시청 위치 저장 ---------- */

  function openLesson(lesson, first) {
    state.current = lesson;
    state.watched = 0;
    state.played = false;
    state.lastSavedAt = Date.now();
    renderNow();
    renderSide();
    refs.playerBox.replaceChildren(h('div', { class: 'lec-state', text: '영상을 준비하는 중이에요…' }));
    // 앞 영상의 위치 저장이 끝난 뒤에 이어보기 위치를 받는다 (저장보다 먼저 읽으면 옛 위치가 온다)
    saveQueue.then(function () {
      return MB.api('/api/v1/courses/' + courseId + '/lessons/' + lesson.lessonId);
    }).then(function (play) {
      if (state.current.lessonId !== lesson.lessonId) return;   // 그사이 다른 영상을 골랐으면 버린다
      state.play = play;
      state.watched = play.watchedSeconds || 0;   // 서버가 인정한 본 시간에서 이어서 센다 (재생 위치와 따로)
      mountPlayer(play, lesson);
      if (!first) refs.playerBox.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
    }).catch(function (err) {
      refs.playerBox.replaceChildren(L.errorBox(err, function () { openLesson(lesson, first); }));
    });
  }

  function mountPlayer(play, lesson) {
    videoLessonId = lesson.lessonId;
    video = h('video', { controls: true, preload: 'metadata', playsinline: true, src: play.videoUrl,
      'aria-label': lesson.title + ' 영상' });
    video.addEventListener('loadedmetadata', function () {
      var pos = play.positionSeconds || 0;
      // 거의 끝에서 멈춘 영상은 처음부터 다시 본다
      if (pos > 0 && (!video.duration || pos < video.duration - 3)) video.currentTime = pos;
      state.lastTime = video.currentTime;
    });
    // 본 시간(watched)은 실제로 재생된 구간만 더한다. 건너뛰기(seek)는 기준점만 옮기고 더하지 않는다
    video.addEventListener('play', function () { state.played = true; state.lastTime = video.currentTime; });
    video.addEventListener('seeking', function () { state.lastTime = video.currentTime; });
    video.addEventListener('seeked', function () { state.lastTime = video.currentTime; });
    video.addEventListener('timeupdate', function () {
      if (!video.paused && !video.seeking) addWatched(video.currentTime);
      if (!video.paused && Date.now() - state.lastSavedAt >= SAVE_EVERY_MS) saveProgress(false);
    });
    video.addEventListener('pause', function () { if (!video.ended) saveProgress(false, { force: true }); });
    video.addEventListener('ended', function () {
      addWatched(video.duration || video.currentTime);   // 마지막 timeupdate 뒤 남은 짧은 재생분만 더한다
      saveProgress(true).then(refreshCourse);
    });
    video.addEventListener('error', function () {
      refs.playerBox.replaceChildren(h('div', { class: 'lec-state lec-state--error', role: 'alert', text: '영상을 재생할 수 없어요. 잠시 뒤 다시 시도해 주세요.' }));
    });
    refs.playerBox.replaceChildren(
      h('div', { class: 'lec-player' }, [
        video,
        lesson.isCompleted ? h('span', { class: 'lec-badge lec-badge--done lec-player__watched', text: '✓ 시청 완료' }) : null
      ]),
      h('p', { class: 'lec-player-note', text: '끝까지 보면 시청 완료로 기록되고, 마지막으로 본 위치가 저장돼요. 건너뛴 부분은 본 시간으로 치지 않아요.' })
    );
  }

  /* 직전 기준점에서 지금 위치까지 앞으로 재생된 만큼만 본 시간에 더한다.
   * timeupdate 는 0.25초쯤마다 오므로 한 번에 MAX_STEP 초보다 크게 뛰면 재생이 아니라 건너뛰기로 본다 */
  function addWatched(time) {
    var step = time - state.lastTime;
    if (step > 0 && step <= MAX_STEP) state.watched += step;
    state.lastTime = time;
  }

  /* 시청 위치 저장 (API 33). 완료 여부는 서버가 본 시간으로 정하므로 completed 는 참고값이다
   * - 요청은 saveQueue 로 한 줄로 세워 보낸 순서대로 서버에 도착하게 한다.
   *   그래서 끝까지 본 뒤의 저장(ended)이 먼저 보낸 주기 저장에 덮이지 않는다
   * - 위치는 지금 재생 위치(currentTime)를 그대로 보낸다. 되감은 위치도 이어보기 위치로 남는다
   * - 15초마다 하는 주기 저장만 앞 요청이 남아 있으면 건너뛴다. 일시정지·영상 전환(force)·종료·페이지 이탈은 건너뛰지 않는다
   * - 저장할 값은 부른 순간에 정해 둔다 (줄을 서는 사이 다른 영상으로 바뀌어도 원래 영상 위치가 간다)
   * - 페이지를 떠날 때(keepalive)는 기다릴 시간이 없어 줄을 서지 않고 바로 보낸다 */
  function saveProgress(ended, options) {
    var opts = options || {};
    if (!video || !state.current || videoLessonId !== state.current.lessonId) return Promise.resolve();
    // 열기만 하고 재생하지 않은 영상은 저장하지 않는다 (0초로 저장되면 강의의 이어보기 기준이 이 영상으로 바뀐다)
    if (!state.played && !ended) return Promise.resolve();
    if (state.pendingSaves > 0 && !ended && !opts.force && !opts.keepalive) return Promise.resolve();
    var url = '/api/v1/courses/' + courseId + '/lessons/' + state.current.lessonId + '/progress';
    var payload = {
      positionSeconds: ended ? Math.floor(video.duration || 0) : Math.floor(video.currentTime || 0),
      watchedSeconds: Math.floor(state.watched),
      completed: !!ended
    };
    state.lastSavedAt = Date.now();
    state.pendingSaves += 1;
    function send() {
      return MB.api(url, { method: 'PUT', keepalive: !!opts.keepalive, body: payload })
        .catch(function () { /* 다음 저장 때 다시 보낸다 */ });
    }
    // 어떤 이유로든 실패해도 줄이 막히거나 요청 수가 남지 않게 마지막에 한 번 더 받는다
    var request = (opts.keepalive ? send() : saveQueue.then(send))
      .catch(function () { /* 저장 실패는 다음 저장 때 다시 보낸다 */ })
      .then(function () { state.pendingSaves -= 1; });
    if (!opts.keepalive) saveQueue = request;
    return request;
  }

  /* ---------- 스크랩 ---------- */

  function toggleScrap() {
    var on = !!state.course.isScrapped;
    refs.scrap.disabled = true;
    MB.api('/api/v1/courses/' + courseId + '/scrap', { method: on ? 'DELETE' : 'POST' }).then(function () {
      state.course.isScrapped = !on;
      MB.toast(on ? '스크랩을 해제했어요.' : '스크랩했어요. 마이페이지에서 다시 볼 수 있어요.');
    }).catch(function (err) {
      if (err && err.code === 'ALREADY_SCRAPPED') state.course.isScrapped = true;
      else MB.toast((err && err.message) || '스크랩을 바꾸지 못했어요.', 'error');
    }).then(function () {
      refs.scrap.disabled = false;
      renderScrap();
    });
  }

  // 다른 화면으로 가거나 탭을 닫을 때 마지막 위치를 남긴다 (keepalive 로 페이지가 닫혀도 요청이 끝까지 간다)
  window.addEventListener('pagehide', function () { if (video && !video.ended) saveProgress(false, { keepalive: true }); });
  document.addEventListener('visibilitychange', function () {
    if (document.visibilityState === 'hidden' && video && !video.paused) saveProgress(false, { keepalive: true });
  });

  load();
})();
