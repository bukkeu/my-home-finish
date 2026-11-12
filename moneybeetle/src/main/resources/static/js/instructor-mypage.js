/* comment.
*   담당 : DBA ① 신아림 (#360, 강사 마이페이지 /instructor/dashboard?tab=mypage)
*   원본 : InstructorDashboard.dc.html 의 mypage 보기 (프로필 카드 · 프로필 정보 · 계정 · 로그아웃)
*   쓰는 API
*   - 13 내 정보 GET /api/v1/members/me, 14 닉네임 PATCH /api/v1/members/me, 15 사진 POST /api/v1/members/me/profile-image,
*     16 비밀번호 PATCH /api/v1/members/me/password
*   - 강사 프로필 GET·PATCH /api/v1/instructor/profile (#365) : 이름 · 한 줄 소개 · 강사 소개 · 전문 분야
*   - 숫자 : 72 내 강의, 82 내 강의 문의, 퀴즈·요약글 목록 GET /api/v1/instructor/quizzes·summaries (#366)
*   - #365·#366 이 아직 없으면 그 값은 비워 두고(숫자는 '-') 나머지는 그대로 쓴다
*  */
(function () {
  var I = window.MBIns;
  if (!I || !window.MB) return;
  var h = I.h, fill = I.fill;

  var FIELDS = ['청약', 'ETF', '주식', '부동산', '예적금', '재테크 기초', '대출'];
  var PASSWORD_RULE = /^(?=.*[A-Za-z])(?=.*\d)\S{8,20}$/;   // 서버 규칙(API 16)과 같다

  function optional(promise) {
    return promise.then(function (v) { return { ok: true, value: v }; }, function () { return { ok: false, value: null }; });
  }

  function count(res, key) {
    if (!res.ok) return '-';
    var items = (res.value && res.value[key || 'items']) || [];
    return items.length;
  }

  function draw(root) {
    root.classList.add('ins-page--mp');
    Promise.all([
      MB.api('/api/v1/members/me'),
      optional(MB.api('/api/v1/instructor/profile')),
      optional(MB.api('/api/v1/instructor/courses')),
      optional(MB.api('/api/v1/instructor/quizzes')),
      optional(MB.api('/api/v1/instructor/summaries')),
      optional(MB.api('/api/v1/instructor/inquiries?page=0&size=1'))
    ]).then(function (r) {
      var inbox = r[5].value;
      var answered = !r[5].ok ? '-' : (inbox && typeof inbox.answeredCount === 'number')
        ? inbox.answeredCount : Math.max(0, ((inbox && inbox.totalElements) || 0) - ((inbox && inbox.waitingCount) || 0));
      render(root, r[0], r[1], [
        { label: '등록 강의', value: count(r[2]) },
        { label: '퀴즈', value: count(r[3]) },
        { label: '요약글', value: count(r[4]) },
        { label: '답변 완료', value: answered }
      ]);
    }).catch(function (err) {
      fill(root, h('div', { class: 'ins-state ins-state--error', role: 'alert' }, [
        I.errorText(err, '마이페이지를 불러오지 못했어요.'), h('br'),
        h('button', { type: 'button', class: 'btn btn-secondary', text: '다시 시도', onclick: function () { draw(root); } })
      ]));
    });
  }

  /* 위쪽 공통 머리(닉네임·첫 글자)도 바로 바꾼다 */
  function refreshHeader(nickname) {
    var name = document.querySelector('.mb-profile__name');
    if (name) name.textContent = nickname;
    var avatar = document.querySelector('.mb-profile .mb-avatar');
    if (avatar && !avatar.querySelector('img')) avatar.textContent = nickname.charAt(0);
  }

  function input(attrs) {
    return h('input', Object.assign({ class: 'mp-input' }, attrs));
  }

  function field(label, control, hint) {
    var id = 'mp-' + Math.random().toString(36).slice(2, 8);
    control.id = id;
    return h('div', { class: 'mp-field' }, [h('label', { class: 'mp-label', for: id, text: label }), control, hint ? h('span', { class: 'mp-hint', text: hint }) : null]);
  }

  function render(root, me, profileRes, stats) {
    var ready = profileRes.ok && profileRes.value;            // #365 강사 프로필 API 가 있으면 true
    var pf = {
      name: (ready && profileRes.value.displayName) || me.nickname || '',
      headline: (ready && profileRes.value.headline) || '',
      bio: (ready && profileRes.value.bio) || '',
      fields: ((ready && profileRes.value.fields) || []).slice()
    };

    /* ---------- 프로필 카드 ---------- */
    var photo = h('span', { class: 'mp-photo__img', 'aria-hidden': 'true' });
    var photoInput = h('input', { type: 'file', accept: 'image/jpeg,image/png', class: 'mp-photo__file', 'aria-label': '프로필 사진 바꾸기' });
    var cardName = h('span', { class: 'mp-card__name' });
    var cardHeadline = h('span', { class: 'mp-card__headline' });
    var cardFields = h('span', { class: 'mp-card__fields' });

    function showPhoto() {
      var url = me.profileImageUrl;
      if (url && /^\/uploads\//.test(url)) {
        photo.style.backgroundImage = 'url("' + url.replace(/"/g, '') + '")';
        photo.textContent = '';
      } else {
        photo.style.backgroundImage = '';
        photo.textContent = (pf.name || '강').charAt(0);
      }
    }
    function showCard() {
      cardName.textContent = pf.name + ' 강사';
      cardHeadline.textContent = pf.headline;
      cardFields.textContent = pf.fields.length ? pf.fields.join(' · ') : '전문 분야를 선택해 주세요';
      showPhoto();
    }

    photoInput.addEventListener('change', function () {
      var file = photoInput.files && photoInput.files[0];
      if (!file) return;
      var form = new FormData();
      form.append('file', file);
      photoInput.disabled = true;
      MB.api('/api/v1/members/me/profile-image', { method: 'POST', body: form }).then(function (r) {
        me.profileImageUrl = r && r.profileImageUrl;
        showPhoto();
        I.toast('프로필 사진을 바꿨어요');
      }).catch(function (err) {
        I.toast(I.errorText(err, '사진을 바꾸지 못했어요'));   // FILE_TOO_LARGE · FILE_TYPE_NOT_ALLOWED
      }).finally(function () {
        photoInput.value = '';
        photoInput.disabled = false;
      });
    });

    var card = h('section', { class: 'mp-box mp-card', 'aria-label': '강사 프로필' }, [
      h('label', { class: 'mp-photo', title: '사진 변경' }, [photoInput, photo, h('span', { class: 'mp-photo__over', text: '사진 변경' })]),
      h('div', { class: 'mp-card__who' }, [
        h('div', { class: 'mp-card__title' }, [cardName, h('span', { class: 'mp-card__role', text: '강사' })]),
        cardHeadline, cardFields
      ]),
      h('div', { class: 'mp-stats' }, stats.map(function (s) {
        return h('div', { class: 'mp-stat' }, [h('span', { class: 'mp-stat__value', text: String(s.value) }), h('span', { class: 'mp-stat__label', text: s.label })]);
      }))
    ]);

    /* ---------- 프로필 정보 ---------- */
    var nameIn = input({ type: 'text', value: pf.name, placeholder: '이름', maxlength: '20', disabled: !ready });
    var nickIn = input({ type: 'text', value: me.nickname || '', placeholder: '수강생에게 보일 이름', maxlength: '20', autocomplete: 'nickname' });
    var headIn = input({ type: 'text', value: pf.headline, placeholder: '예) 청약과 부동산을 쉽게 풀어 드려요', maxlength: '60', disabled: !ready });
    var bioIn = h('textarea', { class: 'mp-input mp-textarea', value: pf.bio, maxlength: '1000', disabled: !ready });
    var chips = h('div', { class: 'mp-chips', role: 'group', 'aria-label': '전문 분야' });
    var pfErr = h('span', { class: 'mp-err', role: 'alert' });
    var pfSave = h('button', { type: 'submit', class: 'mp-btn mp-btn--primary', text: '저장' });

    function drawChips() {
      fill(chips, FIELDS.map(function (k) {
        var on = pf.fields.indexOf(k) >= 0;
        return h('button', { type: 'button', class: 'mp-chip' + (on ? ' is-on' : ''), 'aria-pressed': on ? 'true' : 'false', disabled: !ready, text: k, onclick: function () {
          pf.fields = on ? pf.fields.filter(function (x) { return x !== k; }) : pf.fields.concat(k);
          pfErr.textContent = '';
          drawChips();
        } });
      }));
    }
    drawChips();
    [nameIn, nickIn, headIn, bioIn].forEach(function (el) { el.addEventListener('input', function () { pfErr.textContent = ''; }); });

    var profileForm = h('form', { class: 'mp-box mp-section', novalidate: true }, [
      h('div', {}, [h('h2', { class: 'mp-h2', text: '프로필 정보' }), h('p', { class: 'mp-sub', text: '수강생에게 보이는 강사 소개예요' })]),
      h('div', { class: 'mp-grid mp-grid--two' }, [field('이름', nameIn), field('닉네임', nickIn)]),
      field('한 줄 소개', headIn),
      field('강사 소개', bioIn),
      h('div', { class: 'mp-field' }, [h('span', { class: 'mp-label', text: '전문 분야' }), chips]),
      ready ? null : h('p', { class: 'mp-hint', text: '이름 · 한 줄 소개 · 강사 소개 · 전문 분야 저장은 준비 중이에요. 지금은 닉네임만 바꿀 수 있어요.' }),
      h('div', { class: 'mp-foot' }, [pfErr, pfSave])
    ]);

    profileForm.addEventListener('submit', function (e) {
      e.preventDefault();
      if (pfSave.disabled) return;
      var next = { name: nameIn.value.trim(), nick: nickIn.value.trim(), headline: headIn.value.trim(), bio: bioIn.value.trim() };
      if (!next.nick || (ready && !next.name)) { pfErr.textContent = '이름과 닉네임을 입력해 주세요'; return; }
      if (ready && !pf.fields.length) { pfErr.textContent = '전문 분야를 하나 이상 선택해 주세요'; return; }
      var jobs = [];
      if (next.nick !== me.nickname) {
        jobs.push(MB.api('/api/v1/members/me', { method: 'PATCH', body: { nickname: next.nick } }).then(function (r) {
          me.nickname = (r && r.nickname) || next.nick;
          refreshHeader(me.nickname);
        }));
      }
      if (ready) {
        jobs.push(MB.api('/api/v1/instructor/profile', { method: 'PATCH', body: { displayName: next.name, headline: next.headline, bio: next.bio, fields: pf.fields } }).then(function () {
          pf.name = next.name;
          pf.headline = next.headline;
          pf.bio = next.bio;
        }));
      }
      if (!jobs.length) { I.toast('바뀐 내용이 없어요'); return; }
      pfSave.disabled = true;
      Promise.all(jobs).then(function () {
        if (!ready) pf.name = me.nickname;
        showCard();
        I.toast('프로필을 저장했어요');
      }).catch(function (err) {
        pfErr.textContent = I.errorText(err, '저장하지 못했어요');   // NICKNAME_DUPLICATED · FORBIDDEN_WORD 등
      }).finally(function () { pfSave.disabled = false; });
    });

    /* ---------- 계정 ---------- */
    var pwCur = input({ type: 'password', placeholder: '현재 비밀번호', autocomplete: 'current-password', maxlength: '20' });
    var pwNext = input({ type: 'password', placeholder: '8자 이상, 영문+숫자', autocomplete: 'new-password', maxlength: '20' });
    var pwConf = input({ type: 'password', placeholder: '새 비밀번호 확인', autocomplete: 'new-password', maxlength: '20' });
    var pwErr = h('span', { class: 'mp-err', role: 'alert' });
    var pwSave = h('button', { type: 'submit', class: 'mp-btn mp-btn--secondary', text: '비밀번호 변경' });
    [pwCur, pwNext, pwConf].forEach(function (el) { el.addEventListener('input', function () { pwErr.textContent = ''; }); });

    var accountForm = h('form', { class: 'mp-box mp-section', novalidate: true }, [
      h('div', {}, [h('h2', { class: 'mp-h2', text: '계정' }), h('p', { class: 'mp-sub', text: '로그인에 쓰는 정보예요' })]),
      h('div', { class: 'mp-narrow' }, [field('아이디(이메일)', input({ type: 'text', value: me.email || '', disabled: true }), '아이디는 변경할 수 없어요')]),
      h('div', { class: 'mp-grid mp-grid--three' }, [field('현재 비밀번호', pwCur), field('새 비밀번호', pwNext), field('새 비밀번호 확인', pwConf)]),
      h('div', { class: 'mp-foot' }, [pwErr, pwSave])
    ]);

    accountForm.addEventListener('submit', function (e) {
      e.preventDefault();
      if (pwSave.disabled) return;
      if (!pwCur.value) { pwErr.textContent = '현재 비밀번호를 입력해 주세요'; return; }
      if (!PASSWORD_RULE.test(pwNext.value)) { pwErr.textContent = '새 비밀번호는 8자 이상, 영문과 숫자를 함께 써 주세요'; return; }
      if (pwNext.value !== pwConf.value) { pwErr.textContent = '새 비밀번호가 일치하지 않아요'; return; }
      pwSave.disabled = true;
      MB.api('/api/v1/members/me/password', { method: 'PATCH', body: { currentPassword: pwCur.value, newPassword: pwNext.value, newPasswordConfirm: pwConf.value } })
        .then(function () {
          pwCur.value = ''; pwNext.value = ''; pwConf.value = '';
          I.toast('비밀번호를 변경했어요');
        })
        .catch(function (err) { pwErr.textContent = I.errorText(err, '비밀번호를 바꾸지 못했어요'); })   // PASSWORD_WRONG 등
        .finally(function () { pwSave.disabled = false; });
    });

    /* ---------- 로그아웃 : 사이드바의 로그아웃과 같은 폼 요청 ---------- */
    var logout = h('form', { method: 'post', action: '/api/v1/auth/logout', class: 'mp-logout' }, [
      h('button', { type: 'submit', class: 'mp-logout__btn', text: '로그아웃' })
    ]);
    var token = document.querySelector('meta[name="_csrf"]');
    if (token) logout.appendChild(h('input', { type: 'hidden', name: '_csrf', value: token.content }));

    showCard();
    fill(root, h('h1', { class: 'mp-sr', text: '마이페이지' }), card, profileForm, accountForm, logout);
  }

  I.tabs.mypage = draw;
})();
