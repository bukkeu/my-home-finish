/* comment.
 * 담당: DBA ② 김예린 (#61, 관리자 로그인 화면)
 * 관리자 로그인 (API 83): POST /api/v1/admin/auth/login 은 일반 로그인(API 6)과 같은 폼 로그인이라
 * 요청이 JSON 이 아니라 폼 형식(application/x-www-form-urlencoded)이고 CSRF 토큰 헤더가 필요하다.
 * - 성공 { memberId, role: "ADMIN" } → /admin/dashboard 로 이동한다. ?next= 가 /admin/ 으로 시작하는 같은 사이트 주소면 그쪽으로 간다.
 * - 실패 LOGIN_FAILED / LOGIN_LOCKED / FORBIDDEN 은 서버 메시지({ error: { code, message } })를 알림 상자에 보여 준다.
 *   FORBIDDEN 은 "관리자 권한이 없는 계정"이다. 잠금(5회 연속 실패 1분, 이후 5회마다 5분)은 서버가 정하므로 화면은 카운트다운을 하지 않는다.
 * - ?expired 로 오면 "로그인 시간이 지났어요"를 먼저 보여 준다. 서버가 준 글은 모두 textContent 로 넣는다.
 */
(function () {
  'use strict';
  var form = document.getElementById('admin-login-form');
  if (!form) return;

  var MESSAGES = {
    LOGIN_FAILED: '이메일 또는 비밀번호를 확인해 주세요.',
    FORBIDDEN: '관리자 권한이 없는 계정이에요.'
  };
  var email = document.getElementById('admin-email');
  var password = document.getElementById('admin-pw');
  var submit = document.getElementById('admin-login-submit');
  var busy = false;

  function show(id, text) {
    var node = document.getElementById(id);
    node.textContent = text || '';
    node.hidden = !text;
  }
  function alertBox(text, kind) {
    var box = document.getElementById('admin-login-alert');
    box.className = 'al-alert' + (kind ? ' al-alert--' + kind : '');
    document.getElementById('admin-login-alert-text').textContent = text || '';
    box.hidden = !text;
  }
  function invalid(input, errorId, text) {
    input.setAttribute('aria-invalid', text ? 'true' : 'false');
    show(errorId, text);
  }
  function csrfHeaders() {
    var headers = { Accept: 'application/json', 'Content-Type': 'application/x-www-form-urlencoded' };
    var token = document.querySelector('meta[name="_csrf"]');
    var name = document.querySelector('meta[name="_csrf_header"]');
    if (token && name) headers[name.content] = token.content;
    return headers;
  }
  /** 같은 사이트의 관리자 화면 주소만 돌아갈 곳으로 쓴다 (다른 사이트·사용자 화면으로 보내지 않는다) */
  function nextUrl() {
    var fallback = '/admin/dashboard';
    var next = new URLSearchParams(window.location.search).get('next') || '';
    if (next.charAt(0) !== '/') return fallback;
    try {
      // 브라우저가 해석하는 대로 주소를 풀어서 본다 (/admin/../user, %2e%2e, 역슬래시 같은 우회를 막는다)
      var url = new URL(next, window.location.origin);
      var path = url.pathname;
      var isLogin = path === '/admin/login' || path === '/admin/login/';
      if (url.origin === window.location.origin && path.indexOf('/admin/') === 0 && !isLogin) {
        return path + url.search + url.hash;   // 검사한 주소 그대로 쓰고, ? 와 # 도 살린다
      }
    } catch (e) { /* 주소를 해석하지 못하면 기본 화면으로 */ }
    return fallback;
  }

  if (new URLSearchParams(window.location.search).has('expired')) alertBox('로그인 시간이 지났어요. 다시 로그인해 주세요.', 'info');

  email.addEventListener('input', function () { invalid(email, 'admin-email-error', ''); });
  password.addEventListener('input', function () { invalid(password, 'admin-pw-error', ''); });

  form.addEventListener('submit', function (event) {
    event.preventDefault();
    if (busy) return;
    var mail = email.value.trim();
    var pw = password.value;
    invalid(email, 'admin-email-error', mail ? '' : '이메일을 입력해 주세요');
    invalid(password, 'admin-pw-error', pw ? '' : '비밀번호를 입력해 주세요');
    if (!mail || !pw) return alertBox('', '');

    busy = true;
    submit.disabled = true;
    submit.textContent = '확인 중…';
    alertBox('', '');
    fetch('/api/v1/admin/auth/login', {
      method: 'POST', credentials: 'same-origin', headers: csrfHeaders(),
      body: new URLSearchParams({ email: mail, password: pw })
    }).then(function (response) {
      return response.json().catch(function () { return {}; }).then(function (body) {
        if (response.ok && body.success !== false) return body;
        return Promise.reject((body && body.error) || { code: 'LOGIN_FAILED' });
      });
    }).then(function () {
      window.location.assign(nextUrl());
    }).catch(function (error) {
      password.value = '';
      var code = error && error.code;
      var text = MESSAGES[code] || (error && error.message) || '로그인하지 못했어요. 잠시 뒤 다시 시도해 주세요.';
      // 잠금은 서버 메시지를 그대로 보여 준다 (잠금 시간이 규칙에 따라 달라진다)
      alertBox(code === 'LOGIN_LOCKED' && error.message ? error.message : text, code === 'LOGIN_LOCKED' ? 'lock' : '');
      busy = false;
      submit.disabled = false;
      submit.textContent = '로그인';
    });
  });

  document.querySelectorAll('[data-password-toggle]').forEach(function (button) {
    button.addEventListener('click', function () {
      var input = document.getElementById(button.dataset.passwordToggle);
      var show = input.type === 'password';
      input.type = show ? 'text' : 'password';
      button.textContent = show ? '숨기기' : '보기';
      button.setAttribute('aria-label', show ? '비밀번호 숨기기' : '비밀번호 표시');
    });
  });
})();
