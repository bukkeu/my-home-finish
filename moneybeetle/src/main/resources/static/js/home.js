/* comment.
 * 담당: DBA ② 김예린 (#51, 사용자 홈 화면)
 * 홈 화면에 API 로 받은 값을 채운다. 서버·사용자가 준 글은 모두 textContent 로 넣는다 (HTML 로 해석하지 않는다).
 * - GET /api/v1/home                        API 19 : 이어서 볼 강의, 학습 수, 트레이닝 해금 (오늘의 용어는 words.js 가 API 20 으로 따로 보여 준다)
 * - GET /api/v1/members/me/watch-histories  API 34 : 학습 기록 (이력이 있을 때)
 * - GET /api/v1/categories                  API 29 : 강의 카테고리 칩
 * - GET /api/v1/courses                     API 30 : 처음 방문일 때 "지금 시작하기 좋은 강의"
 * 본 영상이 없고 이어서 볼 강의도 없으면 환영 화면(vr1)을, 아니면 이어서 듣기·학습 기록 화면(vr2)을 보인다.
 */
(function () {
  'use strict';
  var root = document.getElementById('hm');
  if (!root || !window.MB) return;

  var api = MB.api;
  var level = Number(root.dataset.level) || 1;
  var tier = root.dataset.tier || '';
  var status = document.getElementById('hm-status');

  function byId(id) { return document.getElementById(id); }
  function el(tag, cls, text) {
    var node = document.createElement(tag);
    if (cls) node.className = cls;
    if (text != null) node.textContent = text;
    return node;
  }
  function link(cls, href, text) {
    var a = el('a', cls, text);
    a.setAttribute('href', href);
    return a;
  }
  function courseUrl(courseId) { return '/user/lecture-detail?courseId=' + encodeURIComponent(courseId); }
  function mmss(seconds) {
    var s = Math.max(0, Number(seconds) || 0);
    return pad(Math.floor(s / 60)) + ':' + pad(s % 60);
  }
  function pad(n) { return n < 10 ? '0' + n : String(n); }
  function showError(error) {
    // API 오류({ code, message })의 문구만 보여 주고, 스크립트 자체의 오류(영어 메시지)는 일반 안내로 바꾼다
    var message = error && error.code && error.message;
    status.textContent = message || '홈을 불러오지 못했어요. 잠시 뒤 다시 시도해 주세요.';
  }

  /** 오늘 / 어제 / n일 전 (달력 날짜 기준) */
  function whenText(iso) {
    var then = new Date(iso);
    if (isNaN(then.getTime())) return '';
    var a = new Date(then.getFullYear(), then.getMonth(), then.getDate()).getTime();
    var now = new Date();
    var b = new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime();
    var days = Math.round((b - a) / 86400000);
    if (days <= 0) return '오늘';
    if (days === 1) return '어제';
    return days + '일 전';
  }

  // ------------------------------------------------------------------ 처음 방문 (vr1)
  var TOPICS = {
    money: { note: '돈 굴리기는 월급 쪼개기부터 ETF까지 소비·저축·투자를 차근차근 배워요.', cta: '돈 굴리기 강의 보기' },
    estate: { note: '부동산은 청약통장과 청약 제도부터 차근차근 배워요.', cta: '부동산 강의 보기' }
  };
  var STARTER_BG = ['#E4F6D2', '#B9EF8E', '#FBEBC0'];

  function setupFirst() {
    var buttons = root.querySelectorAll('.hm-pick');
    var note = byId('hm-topic-note');
    buttons.forEach(function (button) {
      button.addEventListener('click', function () {
        var key = button.dataset.topic;
        buttons.forEach(function (other) { other.setAttribute('aria-pressed', String(other === button)); });
        byId('hm-topic-text').textContent = TOPICS[key].note;
        var cta = byId('hm-topic-cta');
        cta.textContent = TOPICS[key].cta;
        cta.setAttribute('href', '/user/lectures?cat=' + key);
        note.hidden = false;
      });
    });
    byId('hm-go-topic').addEventListener('click', function (event) {
      event.preventDefault();
      var target = byId('hm-topic');
      var reduce = window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches;
      window.scrollTo({ top: target.getBoundingClientRect().top + window.scrollY - 90, behavior: reduce ? 'auto' : 'smooth' });
    });
    loadStarters();
  }

  function loadStarters() {
    var box = byId('hm-starters');
    api('/api/v1/courses?page=0&size=3').then(function (data) {
      var items = (data && data.items) || [];
      box.textContent = '';
      if (!items.length) {
        box.appendChild(el('p', 'hm-empty', '아직 공개된 강의가 없어요.'));
        return;
      }
      items.forEach(function (course, index) {
        var card = link('hm-course', courseUrl(course.courseId));
        var thumb = el('span', 'hm-course__thumb');
        thumb.style.setProperty('--bg', STARTER_BG[index % STARTER_BG.length]);
        if (course.thumbnailUrl) {
          var img = el('img');
          img.setAttribute('src', course.thumbnailUrl);
          img.setAttribute('alt', '');
          thumb.appendChild(img);
        } else {
          var icon = el('span', 'hm-course__icon');
          var circle = el('span');
          circle.innerHTML = '<svg width="30" height="30" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><path d="M8 5v14l11-7z"/></svg>';
          icon.appendChild(circle);
          thumb.appendChild(icon);
        }
        thumb.appendChild(el('span', 'hm-course__count', '영상 ' + (course.lessonCount || 0) + '개'));
        var body = el('span', 'hm-course__body');
        body.appendChild(el('strong', null, course.title));
        body.appendChild(el('span', null, course.instructorName || ''));
        card.appendChild(thumb);
        card.appendChild(body);
        box.appendChild(card);
      });
    }, function () {
      box.textContent = '';
      box.appendChild(el('p', 'hm-empty', '강의를 불러오지 못했어요.'));
    });
  }

  // ------------------------------------------------------------------ 이력이 있을 때 (vr2)
  function renderContinue(course) {
    var card = byId('hm-continue');
    if (!course) {
      var empty = el('div', 'hm-continue__empty');
      empty.appendChild(el('div', 'card-title', '이어서 들을 강의가 없어요'));
      empty.appendChild(el('p', 'card-body', '새 강의를 골라 학습을 이어가 보세요.'));
      empty.appendChild(link('btn btn-primary', '/user/lectures', '강의 보러 가기'));
      card.appendChild(empty);
      return;
    }
    var video = link('hm-continue__video', courseUrl(course.courseId));
    video.setAttribute('aria-label', course.title + ' 이어보기');
    video.appendChild(el('span', 'hm-continue__tag', '이어보기'));
    var play = el('span', 'hm-continue__play');
    play.innerHTML = '<svg width="26" height="26" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><path d="M8 5v14l11-7z"/></svg>';
    video.appendChild(play);
    if (course.positionSeconds != null) video.appendChild(el('span', 'hm-continue__time', mmss(course.positionSeconds) + '부터'));
    card.appendChild(video);
    card.appendChild(el('div', 'card-title', course.title));
    card.appendChild(el('p', 'card-body', course.lessonTitle ? '마지막으로 본 영상 · ' + course.lessonTitle : '아직 본 영상이 없어요'));
  }

  function loadHistory() {
    var box = byId('hm-history');
    api('/api/v1/members/me/watch-histories?page=0&size=5').then(function (data) {
      var items = (data && data.items) || [];
      box.textContent = '';
      if (!items.length) {
        box.appendChild(el('p', 'hm-empty', '아직 학습 기록이 없어요.'));
        return;
      }
      items.forEach(function (item) {
        var done = item.isCompleted === true || item.completed === true;
        var row = link('hm-row', courseUrl(item.courseId));
        var text = el('span', 'hm-row__text');
        text.appendChild(el('strong', null, item.lessonTitle));
        text.appendChild(el('span', null, item.courseTitle));
        row.appendChild(text);
        row.appendChild(el('span', 'hm-row__date', whenText(item.lastWatchedAt)));
        row.appendChild(el('span', 'tag' + (done ? ' is-done' : ''), done ? '학습 완료' : '시청 중'));
        box.appendChild(row);
      });
    }, function () {
      box.textContent = '';
      box.appendChild(el('p', 'hm-empty', '학습 기록을 불러오지 못했어요.'));
    });
  }

  function loadCategories() {
    var box = byId('hm-cats');
    api('/api/v1/categories').then(function (data) {
      // API 29 는 MB.api 가 data 를 풀어 주면 배열이다. { categories: [...] } 로 감싼 응답도 받아 준다
      var groups = Array.isArray(data) ? data : ((data && data.categories) || []);
      box.textContent = '';
      groups.forEach(function (group) {
        var cat = el('div', 'hm-cat');
        cat.appendChild(el('h6', null, group.name));
        var chips = el('div', 'hm-cat__chips');
        (group.children || []).forEach(function (child) {
          chips.appendChild(link('btn btn-secondary', '/user/lectures?categoryId=' + encodeURIComponent(group.categoryId), child.name));
        });
        cat.appendChild(chips);
        box.appendChild(cat);
      });
    }, function () {
      box.textContent = '';
      box.appendChild(el('p', 'hm-empty', '카테고리를 불러오지 못했어요.'));
    });
  }

  // ------------------------------------------------------------------ 트레이닝 (공통)
  function setupTraining(training) {
    var dialog = byId('hm-lock-dialog');
    var opener = null;
    function closeDialog() {
      dialog.hidden = true;
      if (opener) opener.focus();
    }
    dialog.addEventListener('click', function (event) {
      if (event.target === dialog || event.target.closest('[data-close]')) closeDialog();
    });
    document.addEventListener('keydown', function (event) {
      if (event.key === 'Escape' && !dialog.hidden) closeDialog();
    });

    root.querySelectorAll('[data-train]').forEach(function (card) {
      var info = training && training[card.dataset.train];
      if (!info) return;
      var go = card.querySelector('[data-go]');
      var lock = card.querySelector('[data-lock]');
      if (info.unlocked) {
        go.hidden = false;
        return;
      }
      var required = info.requiredLevel;
      card.querySelector('[data-lock-title]').textContent = 'Lv.' + required + '부터 참여할 수 있어요';
      card.querySelector('[data-lock-sub]').textContent = '현재 Lv.' + level + ' ' + tier;
      lock.hidden = false;
      lock.addEventListener('click', function () {
        opener = lock;
        // 모의 투자(받침 없음)는 '는', 모의 청약(받침 있음)은 '은'
        byId('hm-lock-title').textContent = card.dataset.name + (card.dataset.train === 'housing' ? '은' : '는') + ' Lv.' + required + '부터 열려요';
        byId('hm-lock-sub').textContent = '지금은 Lv.' + level + ' ' + tier + '예요.\n강의를 완료하고 경험치를 모아 보세요.';
        byId('hm-lock-peek').hidden = card.dataset.train !== 'housing';
        dialog.hidden = false;
        dialog.querySelector('[data-close]').focus();
      });
    });
    byId('hm-training').hidden = false;
  }

  // ------------------------------------------------------------------ 시작
  api('/api/v1/home').then(function (home) {
    home = home || {};
    var learning = home.learning || { watchedLessons: 0, completedCourses: 0 };
    var first = !home.continueCourse && !learning.watchedLessons;
    status.textContent = '';
    if (first) {
      byId('hm-first').hidden = false;
      setupFirst();
    } else {
      byId('hm-return').hidden = false;
      byId('hm-done').textContent = String(learning.completedCourses || 0);
      renderContinue(home.continueCourse);
      loadHistory();
      loadCategories();
    }
    setupTraining(home.training);
  }).catch(showError);
})();
