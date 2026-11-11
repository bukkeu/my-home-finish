/* comment.
 * 담당: DBA ② 김예린 (#69, 마이페이지 설정 탭)
 * fragments/mypage/settings.html 의 #mg-settings 안을 API 로 채우고 저장·변경·탈퇴를 처리한다.
 * 서버가 준 글은 textContent 로만 넣고, 오류 문구는 서버의 오류 메시지({ code, message })를 그대로 보여 준다.
 * - GET   /api/v1/members/me                                (API 13)
 * - PATCH /api/v1/members/me                                (API 14) 닉네임·생년월일·성별
 * - POST  /api/v1/members/me/profile-image                  (API 15) multipart, jpg·png 2MB 이하
 * - PATCH /api/v1/members/me/password                       (API 16)
 * - DELETE /api/v1/members/me                               (API 17) 비밀번호가 있으면 { password }, 없으면(간편 로그인) 이메일 코드 확인 뒤
 * - POST  /api/v1/members/me/withdraw-verification/request  (API 17-1), /withdraw-verification (API 17-2)
 * 프로필을 바꾸면 document 에 mg:profile-changed 이벤트를 보낸다 (같은 화면의 프로필 요약이 값을 다시 그린다).
 */
(function () {
  'use strict';
  var root = document.getElementById('mg-settings');
  if (!root || !window.MB) return;

  var api = MB.api;
  var MAX_IMAGE = 2 * 1024 * 1024;
  var ALLOWED_IMAGE = ['image/jpeg', 'image/png'];
  var PASSWORD_RULE = /^(?=.*[A-Za-z])(?=.*\d)\S{8,20}$/;
  var me = null;

  function byId(id) { return document.getElementById(id); }
  function say(id, text, ok) {
    var node = byId(id);
    node.textContent = text || '';
    node.classList.toggle('is-ok', Boolean(ok));
  }
  function errorText(error) {
    return (error && error.message) || '요청을 처리하지 못했어요. 잠시 뒤 다시 시도해 주세요.';
  }
  /** 요청 중에는 버튼을 막아 같은 요청을 두 번 보내지 않는다 (끝나면 다시 누를 수 있다) */
  function busy(button, task) {
    if (button.disabled) return;
    button.disabled = true;
    Promise.resolve().then(task).then(function () { button.disabled = false; }, function () { button.disabled = false; });
  }
  function notifyChanged() {
    document.dispatchEvent(new CustomEvent('mg:profile-changed', { detail: { nickname: me.nickname, profileImageUrl: me.profileImageUrl } }));
  }

  // ------------------------------------------------------------------ 프로필 이미지 · 개인정보
  function drawAvatar() {
    var avatar = byId('mg-avatar');
    avatar.textContent = '';
    if (me.profileImageUrl) {
      var img = document.createElement('img');
      img.setAttribute('src', me.profileImageUrl);
      img.setAttribute('alt', '프로필 사진');
      avatar.appendChild(img);
    } else {
      avatar.textContent = me.nickname ? me.nickname.charAt(0) : '?';
    }
  }

  function fillProfile() {
    byId('mg-email').value = me.email || '';
    byId('mg-nickname').value = me.nickname || '';
    byId('mg-birth').value = me.birthDate || '';
    byId('mg-gender').value = me.gender || '';
    drawAvatar();
  }

  /** 헤더의 닉네임·프로필 글자도 바로 바꿔 준다 (서버가 그린 값이라 새로고침 전까지는 그대로이기 때문) */
  function refreshHeader() {
    var name = document.querySelector('.mb-profile__name');
    if (name) name.textContent = me.nickname;
    var avatar = document.querySelector('.mb-profile .mb-avatar');
    if (avatar && me.nickname) avatar.textContent = me.nickname.charAt(0);
  }

  function setupProfile() {
    byId('mg-profile-form').addEventListener('submit', function (event) {
      event.preventDefault();
      if (!me) return;   // 아직 내 정보를 받기 전이다
      var nickname = byId('mg-nickname').value.trim();
      var birthDate = byId('mg-birth').value;
      if (!nickname) return say('mg-profile-msg', '닉네임을 입력해 주세요.', false);
      if (!birthDate) return say('mg-profile-msg', '생년월일을 입력해 주세요.', false);
      say('mg-profile-msg', '저장하고 있어요…', true);
      busy(byId('mg-profile-save'), function () {
        return api('/api/v1/members/me', { method: 'PATCH', body: { nickname: nickname, birthDate: birthDate, gender: byId('mg-gender').value || null } })
          .then(function (result) {
            me.nickname = (result && result.nickname) || nickname;
            me.birthDate = birthDate;
            me.gender = byId('mg-gender').value || me.gender;
            say('mg-profile-msg', '저장했어요.', true);
            drawAvatar();
            refreshHeader();
            notifyChanged();
          })
          .catch(function (error) { say('mg-profile-msg', errorText(error), false); });
      });
    });

    byId('mg-photo-input').addEventListener('change', function (event) {
      var input = event.target;
      var file = input.files && input.files[0];
      input.value = '';
      if (!file || !me) return;
      if (ALLOWED_IMAGE.indexOf(file.type) < 0) return say('mg-photo-msg', 'jpg 또는 png 파일만 올릴 수 있어요.', false);
      if (file.size > MAX_IMAGE) return say('mg-photo-msg', '2MB 이하의 사진만 올릴 수 있어요.', false);
      var form = new FormData();
      form.append('file', file);
      say('mg-photo-msg', '올리고 있어요…', true);
      api('/api/v1/members/me/profile-image', { method: 'POST', body: form }).then(function (result) {
        me.profileImageUrl = result && result.profileImageUrl;
        drawAvatar();
        say('mg-photo-msg', '프로필 사진을 바꿨어요.', true);
        notifyChanged();
      }, function (error) { say('mg-photo-msg', errorText(error), false); });
    });
  }

  // ------------------------------------------------------------------ 비밀번호 변경
  function setupPassword() {
    root.addEventListener('click', function (event) {
      var toggle = event.target.closest('[data-mg-toggle]');
      if (!toggle) return;
      var input = byId(toggle.dataset.mgToggle);
      var show = input.type === 'password';
      input.type = show ? 'text' : 'password';
      toggle.textContent = show ? '숨기기' : '보기';
      toggle.setAttribute('aria-label', show ? '비밀번호 숨기기' : '비밀번호 표시');
    });

    byId('mg-pw-form').addEventListener('submit', function (event) {
      event.preventDefault();
      var current = byId('mg-pw-current').value;
      var next = byId('mg-pw-new').value;
      var confirm = byId('mg-pw-confirm').value;
      if (!current) return say('mg-pw-msg', '현재 비밀번호를 입력해 주세요.', false);
      if (!PASSWORD_RULE.test(next)) return say('mg-pw-msg', '새 비밀번호는 8~20자의 영문과 숫자를 함께 사용해 주세요.', false);
      if (next !== confirm) return say('mg-pw-msg', '새 비밀번호가 서로 달라요.', false);
      say('mg-pw-msg', '변경하고 있어요…', true);
      busy(byId('mg-pw-save'), function () {
        return api('/api/v1/members/me/password', { method: 'PATCH', body: { currentPassword: current, newPassword: next, newPasswordConfirm: confirm } })
          .then(function () {
            ['mg-pw-current', 'mg-pw-new', 'mg-pw-confirm'].forEach(function (id) { byId(id).value = ''; });
            say('mg-pw-msg', '비밀번호를 바꿨어요.', true);
          })
          .catch(function (error) { say('mg-pw-msg', errorText(error), false); });
      });
    });
  }

  // ------------------------------------------------------------------ 탈퇴
  function setupWithdraw() {
    var dialog = byId('mg-wd');
    var stepPassword = byId('mg-wd-step-password');
    var stepCode = byId('mg-wd-step-code');
    var submit = byId('mg-wd-submit');
    var send = byId('mg-wd-send');
    var opener = null;
    var codeMode = false;
    var timer = null;

    function reset() {
      codeMode = false;
      stepPassword.hidden = false;
      stepCode.hidden = true;
      byId('mg-wd-password').value = '';
      byId('mg-wd-code').value = '';
      say('mg-wd-msg', '', false);
      window.clearInterval(timer);
      send.disabled = false;
      send.textContent = '인증번호 받기';
    }
    function open(trigger) {
      opener = trigger;
      reset();
      dialog.hidden = false;
      byId('mg-wd-password').focus();
    }
    function close() {
      dialog.hidden = true;
      reset();
      if (opener) opener.focus();
    }
    function done() { window.location.assign('/'); }

    function withdraw(password) {
      var options = { method: 'DELETE' };
      if (password) options.body = { password: password };
      return api('/api/v1/members/me', options);
    }

    byId('mg-leave').addEventListener('click', function (event) { open(event.currentTarget); });
    byId('mg-wd-cancel').addEventListener('click', close);
    dialog.addEventListener('click', function (event) { if (event.target === dialog) close(); });
    document.addEventListener('keydown', function (event) {
      if (event.key === 'Escape' && !dialog.hidden) close();
    });

    send.addEventListener('click', function () {
      send.disabled = true;
      api('/api/v1/members/me/withdraw-verification/request', { method: 'POST' }).then(function () {
        say('mg-wd-msg', '가입한 이메일로 인증번호를 보냈어요. 5분 안에 입력해 주세요.', true);
        byId('mg-wd-code').focus();
        var seconds = 60;
        send.textContent = '다시 받기 (' + seconds + '초)';
        window.clearInterval(timer);
        timer = window.setInterval(function () {
          seconds -= 1;
          if (seconds <= 0) {
            window.clearInterval(timer);
            send.disabled = false;
            send.textContent = '인증번호 다시 받기';
            return;
          }
          send.textContent = '다시 받기 (' + seconds + '초)';
        }, 1000);
      }, function (error) {
        send.disabled = false;
        say('mg-wd-msg', errorText(error), false);
      });
    });

    submit.addEventListener('click', function () {
      busy(submit, function () {
        if (!codeMode) {
          // 비밀번호로 확인한다. 비워 두면 서버가 비밀번호 없는 간편 로그인 회원인지 알려 준다 (WITHDRAW_VERIFICATION_REQUIRED)
          return withdraw(byId('mg-wd-password').value).then(done, function (error) {
            if (error && error.code === 'WITHDRAW_VERIFICATION_REQUIRED') {
              codeMode = true;
              stepPassword.hidden = true;
              stepCode.hidden = false;
              say('mg-wd-msg', '비밀번호가 없는 계정이에요. 가입한 이메일로 인증번호를 받아 입력해 주세요.', true);
              return;
            }
            say('mg-wd-msg', errorText(error), false);
          });
        }
        var code = byId('mg-wd-code').value.trim();
        if (!/^\d{6}$/.test(code)) return say('mg-wd-msg', '인증번호 6자리를 입력해 주세요.', false);
        return api('/api/v1/members/me/withdraw-verification', { method: 'POST', body: { code: code } })
          .then(function () { return withdraw(''); })
          .then(done, function (error) { say('mg-wd-msg', errorText(error), false); });
      });
    });
  }

  // ------------------------------------------------------------------ 시작
  setupProfile();
  setupPassword();
  setupWithdraw();
  api('/api/v1/members/me').then(function (data) {
    me = data || {};
    byId('mg-settings-status').textContent = '';
    fillProfile();
  }, function (error) {
    byId('mg-settings-status').textContent = errorText(error);
  });
})();
