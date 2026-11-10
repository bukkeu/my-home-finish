/* comment.
*   담당 : DBA ① 신아림 (#52, 강의 상세·영상 시청 /user/lecture-detail?courseId=)
*   - 강의 정보·목차(영상별 시청 완료)·진도율·스크랩 버튼
*   - 영상 재생: 처음 열면 마지막으로 본 영상(없으면 아직 안 본 첫 영상)을 고르고, 서버가 준 이어보기 위치부터 재생한다
*   - 시청 위치 저장(API 33): 재생 중 15초마다, 일시정지·끝·화면을 떠날 때. 완료 여부는 서버가 본 시간으로 정한다(#20)
*     → 끝까지 본 뒤에는 강의 정보를 다시 받아 목차·진도율을 서버 기준으로 갱신한다
*  */
(function () {
  var root = document.querySelector('[data-lec-detail]');
  if (!root || !window.MB || !window.MBLec) return;
  var L = window.MBLec;
  var h = L.h;

  var SAVE_EVERY_MS = 15000;
  var courseId = Number(new URLSearchParams(location.search).get('courseId'));
  var body = root.querySelector('[data-lec-body]');
  var back = root.querySelector('[data-lec-back]');

  var state = {
    course: null, path: null, current: null, play: null, tab: 'summary', tocOpen: false,
    watchedMax: 0, lastSavedAt: 0, pendingSaves: 0, refreshed: false
  };
  var video = null;
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
      render();
      if (state.current) openLesson(state.current, true);
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

  /* 저장 뒤 강의 정보만 다시 받아 목차·진도율을 서버 기준으로 바꾼다 (재생 중인 영상은 그대로) */
  function refreshCourse() {
    return MB.api('/api/v1/courses/' + courseId).then(function (course) {
      state.course = course;
      state.current = course.lessons.find(function (l) { return l.lessonId === state.current.lessonId; }) || state.current;
      renderSide();
      renderNow();
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

    var main = lessons.length
      ? h('div', { class: 'lec-panel' }, [refs.now, refs.playerBox, refs.content])
      : h('div', { class: 'lec-panel' }, [h('div', { class: 'lec-state', text: '아직 등록된 영상이 없어요.' })]);

    body.replaceChildren(head, h('div', { class: 'lec-layout' }, [main, refs.side]));
    renderScrap();
    renderSide();
    renderNow();
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

    refs.side.replaceChildren(
      h('div', { class: 'lec-toc__head' }, [h('h3', { text: '강의 목차' }), h('span', { class: 'lec-meta', text: done + ' / ' + lessons.length }), toggle]),
      h('div', { class: 'lec-bar lec-bar--thin' }, [h('span', { style: 'width:' + pct + '%' })]),
      h('div', { class: 'lec-toc__list' }, [h('div', { class: 'lec-toc__group', text: '영상 ' + lessons.length + '개' })]
        .concat(lessons.map(function (l, i) {
          var isNow = state.current && state.current.lessonId === l.lessonId;
          return h('button', { type: 'button', class: 'lec-step' + (l.isCompleted ? ' lec-step--done' : ''),
            'aria-current': isNow ? 'step' : null,
            onclick: function () { if (!isNow) { saveProgress(false, { force: true }); openLesson(l, false); } } }, [
            h('span', { class: 'lec-step__mark', text: l.isCompleted ? '✓' : String(i + 1) }),
            h('span', { class: 'lec-step__text' }, [
              h('span', { class: 'lec-step__title', text: l.title }),
              h('span', { class: 'lec-step__sub', text: L.minutes(l.durationSeconds) + (l.isCompleted ? ' · 시청 완료' : isNow ? ' · 보는 중' : '') })
            ])
          ]);
        }))
        .concat([h('div', { class: 'lec-goal' }, [
          h('span', { class: 'lec-step__mark', text: c.progressStatus === 'COMPLETED' ? '✓' : '★' }),
          h('span', { class: 'lec-step__text' }, [
            h('span', { class: 'lec-step__title', text: '학습 완료' }),
            h('span', { class: 'lec-step__sub', text: '영상을 모두 보고 퀴즈 70점 이상' })
          ])
        ])]))
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
          onclick: function () { if (prev) { saveProgress(false, { force: true }); openLesson(prev, false); } } }),
        h('button', { type: 'button', class: 'btn btn-secondary', disabled: !next, text: '다음 ›',
          onclick: function () { if (next) { saveProgress(false, { force: true }); openLesson(next, false); } } })
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
            onclick: function () { saveProgress(false, { force: true }); openLesson(next, false); } }) : null,
          !next && allDone ? h('span', { class: 'lec-meta', text: '영상을 모두 봤어요! 최종 퀴즈에서 70점 이상이면 학습 완료예요.' }) : null,
          !next && !allDone ? h('span', { class: 'lec-meta', text: '영상을 모두 끝까지 보면 최종 퀴즈가 열려요.' }) : null
        ])
      ])
    );
  }

  /* ---------- 영상 재생·시청 위치 저장 ---------- */

  function openLesson(lesson, first) {
    state.current = lesson;
    state.watchedMax = 0;
    state.lastSavedAt = Date.now();
    renderNow();
    renderSide();
    refs.playerBox.replaceChildren(h('div', { class: 'lec-state', text: '영상을 준비하는 중이에요…' }));
    MB.api('/api/v1/courses/' + courseId + '/lessons/' + lesson.lessonId).then(function (play) {
      if (state.current.lessonId !== lesson.lessonId) return;   // 그사이 다른 영상을 골랐으면 버린다
      state.play = play;
      state.watchedMax = play.positionSeconds || 0;
      mountPlayer(play, lesson);
      if (!first) refs.playerBox.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
    }).catch(function (err) {
      refs.playerBox.replaceChildren(L.errorBox(err, function () { openLesson(lesson, first); }));
    });
  }

  function mountPlayer(play, lesson) {
    video = h('video', { controls: true, preload: 'metadata', playsinline: true, src: play.videoUrl,
      'aria-label': lesson.title + ' 영상' });
    video.addEventListener('loadedmetadata', function () {
      var pos = play.positionSeconds || 0;
      // 거의 끝에서 멈춘 영상은 처음부터 다시 본다
      if (pos > 0 && (!video.duration || pos < video.duration - 3)) video.currentTime = pos;
    });
    video.addEventListener('timeupdate', function () {
      state.watchedMax = Math.max(state.watchedMax, Math.floor(video.currentTime));
      if (!video.paused && Date.now() - state.lastSavedAt >= SAVE_EVERY_MS) saveProgress(false);
    });
    video.addEventListener('pause', function () { if (!video.ended) saveProgress(false, { force: true }); });
    video.addEventListener('ended', function () {
      state.watchedMax = Math.max(state.watchedMax, Math.floor(video.duration || 0));
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

  /* 시청 위치 저장 (API 33). 완료 여부는 서버가 본 시간으로 정하므로 completed 는 참고값이다
   * - 요청은 saveQueue 로 한 줄로 세워 보낸 순서대로 서버에 도착하게 한다.
   *   그래서 끝까지 본 뒤의 저장(ended)이 먼저 보낸 주기 저장에 덮이지 않는다
   * - 위치는 지금 재생 위치(currentTime)를 그대로 보낸다. 되감은 위치도 이어보기 위치로 남는다
   * - 15초마다 하는 주기 저장만 앞 요청이 남아 있으면 건너뛴다. 일시정지·영상 전환(force)·종료·페이지 이탈은 건너뛰지 않는다
   * - 저장할 값은 부른 순간에 정해 둔다 (줄을 서는 사이 다른 영상으로 바뀌어도 원래 영상 위치가 간다)
   * - 페이지를 떠날 때(keepalive)는 기다릴 시간이 없어 줄을 서지 않고 바로 보낸다 */
  function saveProgress(ended, options) {
    var opts = options || {};
    if (!video || !state.current) return Promise.resolve();
    if (state.pendingSaves > 0 && !ended && !opts.force && !opts.keepalive) return Promise.resolve();
    var url = '/api/v1/courses/' + courseId + '/lessons/' + state.current.lessonId + '/progress';
    var payload = {
      positionSeconds: ended ? Math.floor(video.duration || 0) : Math.floor(video.currentTime || 0),
      watchedSeconds: state.watchedMax,
      completed: !!ended
    };
    state.lastSavedAt = Date.now();
    state.pendingSaves += 1;
    function send() {
      return MB.api(url, { method: 'PUT', keepalive: !!opts.keepalive, body: payload })
        .catch(function () { /* 다음 저장 때 다시 보낸다 */ });
    }
    var request = opts.keepalive ? send() : saveQueue.then(send);
    if (!opts.keepalive) saveQueue = request;
    return request.then(function () { state.pendingSaves -= 1; });
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
