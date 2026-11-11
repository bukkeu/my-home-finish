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

  /** 시안처럼 "14분"(영상 총 시간)과 소주제를 보여 주려고 강의 상세(API 31)에서 값을 받아 온다. 못 받으면 영상 개수·강사 이름을 그대로 둔다 */
  function enrichStarter(courseId, countNode, pathNode) {
    api('/api/v1/courses/' + encodeURIComponent(courseId)).then(function (detail) {
      var lessons = (detail && detail.lessons) || [];
      var seconds = lessons.reduce(function (sum, lesson) { return sum + (lesson.durationSeconds || 0); }, 0);
      if (seconds > 0) countNode.textContent = Math.max(1, Math.round(seconds / 60)) + '분';
      if (detail && detail.categoryName) pathNode.textContent = detail.categoryName;
    }, function () { /* 상세를 못 받아도 카드는 그대로 보인다 */ });
  }

  /** 강의 목록 API(30)는 categoryId 가 필수라서, 카테고리(29)를 앞에서부터 하나씩 조회해 3개가 모일 때까지 모은다 */
  function fetchStarterCourses(categories, collected) {
    if (collected.length >= 3 || !categories.length) return Promise.resolve(collected.slice(0, 3));
    var url = '/api/v1/courses?categoryId=' + encodeURIComponent(categories[0].categoryId) + '&page=0&size=3';
    return api(url).then(function (data) {
      return fetchStarterCourses(categories.slice(1), collected.concat((data && data.items) || []));
    });
  }

  function loadStarters() {
    var box = byId('hm-starters');
    api('/api/v1/categories').then(function (data) {
      var groups = Array.isArray(data) ? data : ((data && data.categories) || []);
      return fetchStarterCourses(groups, []);
    }).then(function (items) {
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
        var count = el('span', 'hm-course__count', '영상 ' + (course.lessonCount || 0) + '개');
        thumb.appendChild(count);
        var body = el('span', 'hm-course__body');
        body.appendChild(el('strong', null, course.title));
        var path = el('span', null, course.instructorName || '');
        body.appendChild(path);
        card.appendChild(thumb);
        card.appendChild(body);
        box.appendChild(card);
        enrichStarter(course.courseId, count, path);
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
    video.appendChild(el('span', 'hm-continue__tag', course.lessonNumber ? course.lessonNumber + '강 이어보기' : '이어보기'));
    var play = el('span', 'hm-continue__play');
    play.innerHTML = '<svg width="26" height="26" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><path d="M8 5v14l11-7z"/></svg>';
    video.appendChild(play);
    // 영상 길이를 알면 "08:24 / 14:30"과 진행률 막대, 모르면 "08:24부터"만 보여 준다
    var pos = course.positionSeconds;
    var dur = course.durationSeconds;
    var hasLength = pos != null && dur != null && dur > 0;
    if (pos != null) video.appendChild(el('span', 'hm-continue__time', hasLength ? mmss(pos) + ' / ' + mmss(dur) : mmss(pos) + '부터'));
    var percent = hasLength ? Math.max(0, Math.min(100, Math.round(pos * 100 / dur))) : null;
    if (hasLength) {
      var bar = el('span', 'hm-continue__bar');
      bar.setAttribute('role', 'progressbar');
      bar.setAttribute('aria-label', '이 영상 시청 진행률');
      bar.setAttribute('aria-valuemin', '0');
      bar.setAttribute('aria-valuemax', '100');
      bar.setAttribute('aria-valuenow', String(percent));
      bar.style.setProperty('--pct', percent + '%');
      bar.appendChild(el('i'));
      video.appendChild(bar);
    }
    card.appendChild(video);
    card.appendChild(el('div', 'card-title', course.title));
    var where = course.categoryName && course.lessonNumber && course.lessonCount
      ? course.categoryName + ' · ' + course.lessonCount + '강 중 ' + course.lessonNumber + '강'
      : (course.lessonTitle ? '마지막으로 본 영상 · ' + course.lessonTitle : '아직 본 영상이 없어요');
    card.appendChild(el('p', 'card-body', where));
    if (hasLength) {
      var remainMinutes = Math.max(0, Math.ceil((dur - pos) / 60));
      card.appendChild(el('span', 'card-meta', percent + '% 시청 · 남은 시간 ' + remainMinutes + '분'));
    }
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
  function slot(card, name) { return card.querySelector('[data-slot="' + name + '"]'); }

  /** 모의투자 카드: 시즌 이름·턴·D-day. 요약이 없으면(잠김·시즌 없음) 카드의 고정 설명을 그대로 둔다 */
  function fillTrading(card, season) {
    if (!season) return;
    slot(card, 'kicker').textContent = '모의 투자 · ' + season.name;
    slot(card, 'body').textContent = season.totalTurns + '턴 진행. 시즌이 끝나면 순자산·수익률 랭킹이 공개돼요.';
    var meta;
    if (season.status === 'RUNNING' || season.status === 'HOLIDAY') {
      meta = (season.daysLeft > 0 ? 'D-' + season.daysLeft : 'D-day') + ' · 현재 ' + season.currentTurn + '/' + season.totalTurns + '턴';
      if (season.currentTurn <= 1) meta += ' · 첫 턴 종료 전까지 참가 가능';
    } else if (season.status === 'SETTLING') {
      meta = '시즌이 끝나 정산 중이에요';
    } else if (season.status === 'ENDED') {
      meta = '시즌이 끝났어요. 다음 시즌을 준비 중이에요';
    } else {
      meta = '시즌을 준비 중이에요';
    }
    slot(card, 'meta').textContent = meta;
  }

  /** 모의 청약 카드: 접수 중인 공고 수·내 납입 횟수 */
  function fillHousing(card, summary) {
    if (!summary) return;
    slot(card, 'title').textContent = summary.openComplexes > 0 ? '접수 중인 공고 ' + summary.openComplexes + '건' : '지금 접수 중인 공고가 없어요';
    if (summary.paidCount != null) {
      slot(card, 'body').textContent = '청약통장 납입 ' + summary.paidCount + '회차 · 하루 1회, 1회 20,000~500,000 캐시를 납입할 수 있어요.';
    }
  }

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
        if (card.dataset.train === 'trading') fillTrading(card, training.tradingSeason);
        else fillHousing(card, training.housingSummary);
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
