/* comment.
*  담당 : SCM 서수영 (#54, 마이페이지 틀)
*  templates/member/mypage.html 의 동작 : 프로필 카드 채우기 + 탭 전환. 탭 내용은 각 담당 조각의 JS 가 맡는다
*  - 프로필 : GET /api/v1/members/me (닉네임·이메일·레벨·칭호·경험치·캐시·연속 출석),
*             수상 내역은 GET /api/v1/members/{memberId}/profile 의 awards (없으면 숨김)
*  - 탭 : 처음 열 탭은 주소의 ?tab= 또는 #탭이름 (사이드바가 ?tab=wrong 처럼 연결). 없거나 모르는 이름이면 settings
*         탭을 바꾸면 주소를 ?tab=이름 으로 맞춘다 (새로고침·뒤로 가기에도 같은 탭)
*  - 조각 JS 와의 약속 (#54 합의)
*      탭이 열릴 때마다 document 에 'mypage:tab' 이벤트를 보낸다 : detail = { tab: 'scrap' }
*      처음 열린 탭도 DOMContentLoaded 때 한 번 보낸다. 조각의 <script> 는 defer 로 넣으면 이 이벤트보다 먼저 준비된다
*      지금 열린 탭은 window.MyPage.current 로도 알 수 있다
*  */
(function () {
  'use strict';

  var root = document.getElementById('mp');
  if (!root || !window.MB) return;

  var TABS = ['settings', 'growth', 'housing', 'wrong', 'scrap', 'learning', 'qna'];
  var buttons = root.querySelectorAll('.mp-tabs [data-tab]');
  var panels = root.querySelectorAll('.mp-panel[data-panel]');
  window.MyPage = { current: null };

  // ---------- 탭 ----------
  function startTab() {
    var fromQuery = new URLSearchParams(location.search).get('tab');
    var fromHash = location.hash.replace('#', '');
    if (TABS.indexOf(fromQuery) >= 0) return fromQuery;
    if (TABS.indexOf(fromHash) >= 0) return fromHash;
    return TABS[0];
  }

  function showTab(name, opts) {
    if (TABS.indexOf(name) < 0) name = TABS[0];
    buttons.forEach(function (b) {
      var on = b.dataset.tab === name;
      b.classList.toggle('btn-primary', on);
      b.classList.toggle('btn-secondary', !on);
      b.setAttribute('aria-selected', on ? 'true' : 'false');
      b.tabIndex = on ? 0 : -1;          // Tab 키로는 고른 탭에만 들어오고, 탭끼리는 방향키로 옮긴다
    });
    panels.forEach(function (p) { p.hidden = p.dataset.panel !== name; });
    window.MyPage.current = name;
    if (!(opts && opts.keepUrl) && history.replaceState) {
      history.replaceState(null, '', location.pathname + '?tab=' + name);
    }
    document.dispatchEvent(new CustomEvent('mypage:tab', { detail: { tab: name } }));
  }

  buttons.forEach(function (b, i) {
    b.addEventListener('click', function () { showTab(b.dataset.tab); });
    // ←·→ 로 이전·다음 탭, Home·End 로 처음·마지막 탭 (끝에서는 반대쪽으로 돌아간다)
    b.addEventListener('keydown', function (e) {
      var to = null;
      if (e.key === 'ArrowRight') to = (i + 1) % buttons.length;
      else if (e.key === 'ArrowLeft') to = (i - 1 + buttons.length) % buttons.length;
      else if (e.key === 'Home') to = 0;
      else if (e.key === 'End') to = buttons.length - 1;
      if (to === null) return;
      e.preventDefault();
      showTab(buttons[to].dataset.tab);
      buttons[to].focus();
    });
  });
  // 같은 화면에서 주소 끝 #탭이름 만 바뀌어도 그 탭으로
  window.addEventListener('hashchange', function () {
    var name = location.hash.replace('#', '');
    if (TABS.indexOf(name) >= 0) showTab(name);
  });

  // ---------- 프로필 카드 ----------
  function setP(name, text) {
    var n = root.querySelector('[data-p="' + name + '"]');
    if (n) n.textContent = text;
    return n;
  }
  function fmt(n) {
    return Number(n || 0).toLocaleString('ko-KR');
  }

  // 칭호(집) 태그 색 : 시안 houseBg 그대로 (Lv.17~ 금색, 12~ 연두, 4~ 옅은 연두, 그 아래 아주 옅은 연두)
  function houseBg(level) {
    return level >= 17 ? '#F2C14E' : level >= 12 ? '#9fe870' : level >= 4 ? '#B9EF8E' : '#EAF7D8';
  }

  // 시안의 "{exp} / 150 XP" = 지금 레벨에서 쌓은 경험치 / 다음 레벨까지 필요한 경험치.
  // 내 정보 API 의 exp 는 누적값이라, 레벨 표(API 24 GET /api/v1/levels)의 필요 경험치로 지금 레벨 구간을 구한다
  function showExp(me) {
    return MB.api('/api/v1/levels').then(function (data) {
      var levels = (data && data.levels) || [];
      var cur = levels.filter(function (l) { return l.level === me.level; })[0];
      var next = levels.filter(function (l) { return l.level === me.level + 1; })[0];
      if (!cur) throw new Error('no level');
      var inLevel = me.exp - cur.requiredExp;
      var gap = next ? next.requiredExp - cur.requiredExp : inLevel;   // 최고 레벨이면 쌓은 값만
      setP('exp', fmt(inLevel) + ' / ' + fmt(gap) + ' XP');
    }).catch(function () {
      setP('exp', fmt(me.exp) + ' XP');
    });
  }

  function showPhoto(url) {
    var img = root.querySelector('[data-p="image"]');
    img.hidden = !url;
    root.querySelector('[data-p="initial"]').hidden = !!url;
    if (url) img.src = url;
  }

  function loadProfile() {
    MB.api('/api/v1/members/me').then(function (me) {
      setP('nickname', me.nickname);
      // 시안 "minji@gmail.com · 구글 로그인". 간편 로그인은 구글만 남기기로 함 (카카오 제외, 팀 결정 2026-11-11)
      // 로그인 방식(loginProvider)은 내 정보 API 에 아직 없어 김예린 담당 이슈로 요청함. 값이 오기 전까지는 이메일만 보인다
      setP('email', (me.email || '') + (me.loginProvider === 'GOOGLE' ? ' · 구글 로그인' : ''));
      setP('initial', String(me.nickname || '?').charAt(0));
      showPhoto(me.profileImageUrl);
      setP('level', 'Lv.' + me.level);
      setP('title', me.title || '').style.background = houseBg(me.level);
      setP('cash', fmt(me.cash));
      setP('streak', (me.attendance ? me.attendance.streak : 0) + '일째 출석 중');
      showExp(me);
      return loadAwards(me.memberId);
    }).catch(function (err) {
      setP('nickname', '내 정보를 불러오지 못했어요');
      MB.toast(err.message, 'error');
    });
  }

  // 프로필 사진 변경 (API 15). 시안처럼 사진을 고르면 바로 바꾼다
  var photoInput = root.querySelector('[data-p="photoInput"]');
  photoInput.addEventListener('change', function () {
    var file = photoInput.files && photoInput.files[0];
    if (!file) return;
    var form = new FormData();
    form.append('file', file);
    photoInput.disabled = true;          // 올리는 동안 다른 사진을 또 고르지 못하게 (중복 요청 방지)
    MB.api('/api/v1/members/me/profile-image', { method: 'POST', body: form }).then(function (r) {
      showPhoto(r && r.profileImageUrl);
      MB.toast('프로필 사진을 바꿨어요');
    }).catch(function (err) {
      MB.toast(err.message, 'error');   // 2MB 초과(FILE_TOO_LARGE)·jpg·png 아님(FILE_TYPE_NOT_ALLOWED) 등
    }).finally(function () {
      photoInput.value = '';            // 같은 파일을 다시 골라도 change 가 오게
      photoInput.disabled = false;
    });
  });

  // 수상 내역 (모의투자 시즌 랭킹 1~3위 메달). 시안 그대로 : 메달 그림 위에 시즌 번호, 최근 시즌부터
  var MEDAL = {
    1: { pos: '0% 50%', disc: '#F4C542', num: '#5A3E0A' },
    2: { pos: '50% 50%', disc: '#D9DCE3', num: '#44474F' },
    3: { pos: '100% 50%', disc: '#E3A06F', num: '#5B2E14' }
  };
  function loadAwards(memberId) {
    if (!memberId) return null;
    return MB.api('/api/v1/members/' + memberId + '/profile').then(function (p) {
      var awards = ((p && p.awards) || []).filter(function (a) { return MEDAL[a.rank]; })
          .map(function (a) { return { season: parseInt(String(a.seasonName).replace(/\D/g, ''), 10) || 0, rank: a.rank }; })
          .sort(function (x, y) { return y.season - x.season; });
      var list = root.querySelector('[data-p="awardList"]');
      list.replaceChildren();
      awards.forEach(function (a) {
        var m = MEDAL[a.rank];
        var medal = document.createElement('span');
        medal.className = 'mp-medal';
        medal.setAttribute('role', 'img');
        medal.title = '시즌 ' + a.season + ' · ' + a.rank + '위';
        medal.setAttribute('aria-label', medal.title);
        medal.style.backgroundPosition = m.pos;
        var disc = document.createElement('span');
        disc.setAttribute('aria-hidden', 'true');
        disc.textContent = String(a.season);
        disc.style.background = m.disc;
        disc.style.color = m.num;
        disc.style.fontSize = String(a.season).length > 1 ? '12px' : '16px';
        medal.appendChild(disc);
        list.appendChild(medal);
      });
      root.querySelector('[data-p="awards"]').hidden = !awards.length;
    }).catch(function () {
      // 수상 내역은 보조 정보라 실패해도 카드의 나머지는 그대로 둔다
    });
  }

  // ---------- 시작 ----------
  loadProfile();
  var first = startTab();
  // 조각의 defer 스크립트가 준비된 뒤 첫 이벤트를 보낸다
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', function () { showTab(first, { keepUrl: !location.search && !location.hash }); });
  } else {
    showTab(first, { keepUrl: !location.search && !location.hash });
  }
})();
