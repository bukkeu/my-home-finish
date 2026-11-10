/* comment.
 * 담당: DBA ② 김예린 (#50)
 * 로그인·회원가입·계정 찾기 화면의 API 연동.
 */
(function () {
  'use strict';
  var page = document.body.dataset.authPage;
  var api = window.MB.api;
  var messages = {
    LOGIN_FAILED: '이메일 또는 비밀번호를 확인해 주세요.',
    LOGIN_LOCKED: '로그인 시도가 많아 잠시 잠겼어요. 잠시 뒤 다시 시도해 주세요.',
    ACCOUNT_SUSPENDED: '이용이 제한된 계정이에요. 관리자에게 문의해 주세요.',
    ACCOUNT_WITHDRAWN: '탈퇴한 계정이에요.',
    EMAIL_DUPLICATED: '이미 가입된 이메일이에요. 이메일 로그인으로 진행해 주세요.',
    NICKNAME_DUPLICATED: '이미 사용 중인 닉네임이에요.',
    NICKNAME_INVALID: '닉네임은 2~12자의 한글·영문·숫자만 쓸 수 있어요.',
    PASSWORD_INVALID: '비밀번호는 8~20자의 영문과 숫자를 함께 사용해 주세요.',
    PASSWORD_MISMATCH: '비밀번호가 서로 달라요.',
    IDENTITY_CODE_INVALID: '인증번호가 맞지 않아요. 다시 확인해 주세요.',
    IDENTITY_CODE_EXPIRED: '인증번호가 만료됐어요. 새 인증번호를 받아 주세요.',
    IDENTITY_CODE_TOO_SOON: '인증번호는 1분에 한 번만 받을 수 있어요.',
    IDENTITY_MAIL_UNAVAILABLE: '인증 메일을 보낼 수 없어요. 잠시 뒤 다시 시도해 주세요.',
    IDENTITY_NOT_VERIFIED: '이메일 인증을 먼저 완료해 주세요.',
    OAUTH_FAILED: '간편 로그인에 실패했어요. 다시 시도해 주세요.',
    OAUTH_PROVIDER_UNAVAILABLE: '이 간편 로그인은 아직 설정되지 않았어요. 다른 방법으로 로그인해 주세요.',
    TOKEN_EXPIRED: '재설정 링크가 만료됐어요. 새 링크를 요청해 주세요.',
    TOKEN_USED: '이미 사용한 재설정 링크예요. 새 링크를 요청해 주세요.',
    MEMBER_NOT_FOUND: '입력한 정보와 일치하는 계정을 찾지 못했어요.'
  };

  function byId(id) { return document.getElementById(id); }
  function setMessage(id, message, success) {
    var node = byId(id);
    if (!node) return;
    node.textContent = message || '';
    node.classList.toggle('is-success', Boolean(success));
  }
  function errorText(error) {
    return messages[error && error.code] || (error && error.message) || '요청을 처리하지 못했어요. 잠시 뒤 다시 시도해 주세요.';
  }
  function runButton(button, task) {
    if (!button || button.disabled) return;
    button.disabled = true;
    Promise.resolve().then(task).finally(function () { if (button.dataset.locked !== 'true') button.disabled = false; });
  }
  function apiError(error, messageId) { setMessage(messageId, errorText(error), false); }
  function formBody(values) { return new URLSearchParams(values); }
  function csrfHeaders() {
    var headers = { Accept: 'application/json', 'Content-Type': 'application/x-www-form-urlencoded' };
    var token = document.querySelector('meta[name="_csrf"]');
    var csrfHeader = document.querySelector('meta[name="_csrf_header"]');
    if (token && csrfHeader) headers[csrfHeader.content] = token.content;
    return headers;
  }
  function authenticate(email, password, next) {
    var values = { email: email, password: password };
    if (next) values.next = next;
    return fetch('/api/v1/auth/login', { method: 'POST', credentials: 'same-origin', headers: csrfHeaders(),
      body: formBody(values) }).then(function (response) {
      return response.json().catch(function () { return {}; }).then(function (body) {
        if (!response.ok || body.success === false) return Promise.reject(body.error || { code: 'LOGIN_FAILED' });
        return body.data || body;
      });
    });
  }

  document.querySelectorAll('[data-password-toggle]').forEach(function (button) {
    button.addEventListener('click', function () {
      var input = byId(button.dataset.passwordToggle);
      var show = input.type === 'password';
      input.type = show ? 'text' : 'password';
      button.textContent = show ? '숨기기' : '보기';
      button.setAttribute('aria-label', show ? '비밀번호 숨기기' : '비밀번호 표시');
    });
  });

  function renderTerms(container, agreements) {
    container.replaceChildren();
    agreements.forEach(function (term) {
      var label = document.createElement('label');
      label.className = 'auth-term';
      var input = document.createElement('input');
      input.type = 'checkbox';
      input.value = term.termsId;
      input.dataset.required = String(Boolean(term.isRequired));
      input.setAttribute('aria-label', (term.isRequired ? '[필수] ' : '[선택] ') + term.title);
      var text = document.createElement('span');
      var strong = document.createElement('strong');
      strong.textContent = term.isRequired ? '[필수] ' : '[선택] ';
      text.append(strong, document.createTextNode(term.title));
      label.append(input, text);
      container.append(label);
    });
  }
  function loadTerms(container, messageId) {
    return api('/api/v1/agreements').then(function (result) {
      renderTerms(container, result.agreements || []);
    }).catch(function (error) {
      container.innerHTML = '<p class="auth-hint">약관을 불러오지 못했어요. 새로고침해 주세요.</p>';
      setMessage(messageId, errorText(error), false);
    });
  }
  function selectedTerms(container) {
    var boxes = Array.from(container.querySelectorAll('input[type="checkbox"]'));
    return {
      ids: boxes.filter(function (box) { return box.checked; }).map(function (box) { return Number(box.value); }),
      requiredOk: boxes.every(function (box) { return box.dataset.required !== 'true' || box.checked; })
    };
  }

  if (page === 'login') {
    var loginForm = byId('login-form');
    var query = new URLSearchParams(window.location.search);
    byId('login-next').value = query.get('next') || '';
    var oauthCode = query.get('oauthError');
    if (oauthCode) setMessage('login-message', errorText({ code: oauthCode }), false);
    loginForm.addEventListener('submit', function (event) {
      event.preventDefault();
      var email = byId('login-email').value.trim();
      var password = byId('login-password').value;
      var next = byId('login-next').value;
      if (!email || !password) return setMessage('login-message', '이메일과 비밀번호를 입력해 주세요.', false);
      setMessage('login-message', '로그인하고 있어요…', true);
      var button = loginForm.querySelector('button[type="submit"]');
      // 요청 중에는 버튼을 막아 같은 로그인을 두 번 보내지 않는다 (실패하면 다시 누를 수 있다)
      runButton(button, function () {
        return authenticate(email, password, next)
          .then(function (result) { button.dataset.locked = 'true'; window.location.assign(result.redirectUrl || '/user/home'); })
          .catch(function (error) { setMessage('login-message', errorText(error), false); });
      });
    });
  }

  if (page === 'signup') {
    var signupStep = 1;
    var emailVerified = false;
    var nicknameAvailable = false;
    var terms = byId('signup-terms');
    var codeButton = byId('request-code');
    var verifiedEmail = '';
    function showStep(step) {
      signupStep = step;
      document.querySelectorAll('[data-signup-step]').forEach(function (panel) { panel.hidden = Number(panel.dataset.signupStep) !== step; });
      document.querySelectorAll('[data-step-indicator]').forEach(function (item) {
        var number = Number(item.dataset.stepIndicator);
        item.classList.toggle('is-active', number === step);
        item.classList.toggle('is-done', number < step);
      });
    }
    function resetVerification() {
      emailVerified = false; verifiedEmail = '';
      byId('signup-code').disabled = true;
      byId('verify-code').disabled = true;
      byId('signup-next-1').disabled = true;
      setMessage('signup-email-message', '', false);
    }
    byId('signup-email').addEventListener('input', resetVerification);
    codeButton.addEventListener('click', function () {
      delete codeButton.dataset.locked;
      runButton(codeButton, function () {
        var email = byId('signup-email').value.trim();
        if (!email) return setMessage('signup-email-message', '이메일을 입력해 주세요.', false);
        return api('/api/v1/auth/check-email?email=' + encodeURIComponent(email)).then(function (result) {
          if (!result.available) throw { code: 'EMAIL_DUPLICATED' };
          return api('/api/v1/auth/identity-verification/request', { method: 'POST', body: { email: email } });
        }).then(function () {
          byId('signup-code').disabled = false;
          byId('verify-code').disabled = false;
          byId('signup-code').focus();
          setMessage('signup-email-message', '인증번호를 보냈어요. 5분 안에 입력해 주세요.', true);
          var seconds = 60;
          codeButton.dataset.locked = 'true';
          codeButton.textContent = '다시 받기 (' + seconds + '초)';
          var timer = window.setInterval(function () {
            seconds -= 1;
            if (seconds <= 0) { window.clearInterval(timer); delete codeButton.dataset.locked; codeButton.disabled = false; codeButton.textContent = '인증번호 다시 받기'; return; }
            codeButton.textContent = '다시 받기 (' + seconds + '초)';
          }, 1000);
        }).catch(function (error) { apiError(error, 'signup-email-message'); });
      });
    });
    byId('verify-code').addEventListener('click', function () {
      var button = byId('verify-code');
      runButton(button, function () {
        var email = byId('signup-email').value.trim();
        var code = byId('signup-code').value.trim();
        if (!/^\d{6}$/.test(code)) return setMessage('signup-email-message', '인증번호 6자리를 입력해 주세요.', false);
        return api('/api/v1/auth/identity-verification', { method: 'POST', body: { email: email, code: code } }).then(function (result) {
          if (!result.verified) throw { code: 'IDENTITY_CODE_INVALID' };
          emailVerified = true; verifiedEmail = email;
          byId('signup-next-1').disabled = false;
          setMessage('signup-email-message', '이메일 인증이 완료됐어요.', true);
          byId('signup-email').readOnly = true;
          codeButton.disabled = true;
          button.dataset.locked = 'true';
          button.disabled = true;
        }).catch(function (error) { apiError(error, 'signup-email-message'); });
      });
    });
    byId('signup-next-1').addEventListener('click', function () { if (emailVerified && byId('signup-email').value.trim() === verifiedEmail) showStep(2); });
    byId('signup-next-2').addEventListener('click', function () {
      var pw = byId('signup-password').value; var confirm = byId('signup-password-confirm').value;
      if (!/^(?=.*[A-Za-z])(?=.*\d)\S{8,20}$/.test(pw)) return setMessage('signup-password-message', messages.PASSWORD_INVALID, false);
      if (pw !== confirm) return setMessage('signup-password-message', messages.PASSWORD_MISMATCH, false);
      setMessage('signup-password-message', '', false); showStep(3);
    });
    document.querySelectorAll('[data-signup-back]').forEach(function (button) { button.addEventListener('click', function () { showStep(Number(button.dataset.signupBack)); }); });
    byId('agree-all').addEventListener('click', function () {
      var boxes = terms.querySelectorAll('input[type="checkbox"]');
      var all = Array.from(boxes).every(function (box) { return box.checked; });
      boxes.forEach(function (box) { box.checked = !all; });
    });
    byId('signup-next-3').addEventListener('click', function () {
      var selected = selectedTerms(terms);
      if (!selected.requiredOk) return setMessage('signup-terms-message', '필수 약관에 동의해 주세요.', false);
      if (!selected.ids.length) return setMessage('signup-terms-message', '약관을 하나 이상 선택해 주세요.', false);
      setMessage('signup-terms-message', '', false); showStep(4);
    });
    byId('check-nickname').addEventListener('click', function () {
      var nickname = byId('signup-nickname').value.trim();
      if (!nickname) return setMessage('signup-profile-message', '닉네임을 입력해 주세요.', false);
      api('/api/v1/auth/check-nickname?nickname=' + encodeURIComponent(nickname)).then(function (result) {
        nicknameAvailable = result.available;
        if (!result.available) throw { code: 'NICKNAME_DUPLICATED' };
        setMessage('signup-profile-message', '사용할 수 있는 닉네임이에요.', true);
      }).catch(function (error) { nicknameAvailable = false; apiError(error, 'signup-profile-message'); });
    });
    byId('signup-nickname').addEventListener('input', function () { nicknameAvailable = false; });
    byId('signup-submit').addEventListener('click', function () {
      var nickname = byId('signup-nickname').value.trim();
      var birthDate = byId('signup-birth').value;
      var selected = selectedTerms(terms);
      if (!emailVerified || byId('signup-email').value.trim() !== verifiedEmail) return setMessage('signup-profile-message', messages.IDENTITY_NOT_VERIFIED, false);
      if (!nicknameAvailable) return setMessage('signup-profile-message', '닉네임 중복 확인을 해 주세요.', false);
      if (!birthDate) return setMessage('signup-profile-message', '생년월일을 입력해 주세요.', false);
      if (!selected.requiredOk) return setMessage('signup-profile-message', '필수 약관에 동의해 주세요.', false);
      var button = byId('signup-submit');
      runButton(button, function () {
        return api('/api/v1/auth/signup', { method: 'POST', body: {
          email: verifiedEmail, password: byId('signup-password').value, passwordConfirm: byId('signup-password-confirm').value,
          nickname: nickname, birthDate: birthDate, gender: byId('signup-gender').value || null, agreedTermsIds: selected.ids
        } }).then(function () {
          return authenticate(verifiedEmail, byId('signup-password').value).then(function (result) {
            window.location.assign(result.redirectUrl || '/user/home');
          }).catch(function () {
            window.location.assign('/login?signup=complete');
          });
        })
          .catch(function (error) { apiError(error, 'signup-profile-message'); });
      });
    });
    loadTerms(terms, 'signup-terms-message');
    showStep(1);
  }

  if (page === 'find-email') {
    var findEmailForm = byId('find-email-form');
    findEmailForm.addEventListener('submit', function (event) {
      event.preventDefault();
      runButton(findEmailForm.querySelector('button[type="submit"]'), function () {
        return api('/api/v1/auth/find-email', { method: 'POST', body: { nickname: byId('find-nickname').value.trim(), birthDate: byId('find-birth').value } })
          .then(function (result) { setMessage('find-email-message', '가입한 이메일은 ' + result.maskedEmail + ' 이에요.', true); })
          .catch(function (error) { apiError(error, 'find-email-message'); });
      });
    });
  }

  if (page === 'password-reset') {
    var token = new URLSearchParams(window.location.search).get('token');
    var requestForm = byId('reset-request-form');
    var confirmForm = byId('reset-confirm-form');
    if (token) { requestForm.hidden = true; confirmForm.hidden = false; document.querySelector('#reset-title').textContent = '새 비밀번호를 설정해요'; }
    requestForm.addEventListener('submit', function (event) {
      event.preventDefault();
      runButton(requestForm.querySelector('button[type="submit"]'), function () {
        return api('/api/v1/auth/password-reset/request', { method: 'POST', body: { email: byId('reset-email').value.trim() } })
          .then(function () { setMessage('reset-request-message', '가입된 이메일이라면 재설정 링크를 보냈어요. 메일함을 확인해 주세요.', true); })
          .catch(function (error) { apiError(error, 'reset-request-message'); });
      });
    });
    confirmForm.addEventListener('submit', function (event) {
      event.preventDefault();
      var password = byId('reset-password').value;
      var confirm = byId('reset-password-confirm').value;
      if (password !== confirm) return setMessage('reset-confirm-message', messages.PASSWORD_MISMATCH, false);
      var confirmButton = confirmForm.querySelector('button[type="submit"]');
      runButton(confirmButton, function () {
        return api('/api/v1/auth/password-reset/confirm', { method: 'POST', body: { token: token, newPassword: password, newPasswordConfirm: confirm } })
          .then(function () { confirmButton.dataset.locked = 'true'; window.location.assign('/login?passwordReset=complete'); })
          .catch(function (error) { apiError(error, 'reset-confirm-message'); });
      });
    });
  }

  if (page === 'social-signup') {
    var socialTerms = byId('social-terms');
    var socialNicknameAvailable = false;
    api('/api/v1/auth/oauth-signup').then(function (profile) {
      byId('social-provider').value = profile.provider === 'google' ? 'Google' : '카카오';
      byId('social-email').value = profile.email;
      byId('social-nickname').value = profile.nickname || '';
    }).catch(function (error) {
      apiError(error, 'social-signup-message');
      window.setTimeout(function () { window.location.assign('/login?oauthError=OAUTH_FAILED'); }, 1200);
    });
    loadTerms(socialTerms, 'social-signup-message');
    byId('social-check-nickname').addEventListener('click', function () {
      api('/api/v1/auth/check-nickname?nickname=' + encodeURIComponent(byId('social-nickname').value.trim())).then(function (result) {
        if (!result.available) throw { code: 'NICKNAME_DUPLICATED' };
        socialNicknameAvailable = true; setMessage('social-signup-message', '사용할 수 있는 닉네임이에요.', true);
      }).catch(function (error) { socialNicknameAvailable = false; apiError(error, 'social-signup-message'); });
    });
    byId('social-nickname').addEventListener('input', function () { socialNicknameAvailable = false; });
    byId('social-signup-form').addEventListener('submit', function (event) {
      event.preventDefault();
      var selected = selectedTerms(socialTerms);
      if (!socialNicknameAvailable) return setMessage('social-signup-message', '닉네임 중복 확인을 해 주세요.', false);
      if (!byId('social-birth').value) return setMessage('social-signup-message', '생년월일을 입력해 주세요.', false);
      if (!selected.requiredOk || !selected.ids.length) return setMessage('social-signup-message', '필수 약관에 동의해 주세요.', false);
      var socialButton = byId('social-signup-form').querySelector('button[type="submit"]');
      runButton(socialButton, function () {
        return api('/api/v1/auth/oauth-signup', { method: 'POST', body: {
          nickname: byId('social-nickname').value.trim(), birthDate: byId('social-birth').value,
          gender: byId('social-gender').value || null, agreedTermsIds: selected.ids
        } }).then(function () { socialButton.dataset.locked = 'true'; window.location.assign('/user/home'); })
          .catch(function (error) { apiError(error, 'social-signup-message'); });
      });
    });
  }

  if (page === 'login') {
    if (new URLSearchParams(window.location.search).get('signup') === 'complete') setMessage('login-message', '회원가입이 완료됐어요. 로그인해 주세요.', true);
    if (new URLSearchParams(window.location.search).get('passwordReset') === 'complete') setMessage('login-message', '비밀번호를 변경했어요. 새 비밀번호로 로그인해 주세요.', true);
  }
})();
