/* comment.
*   담당 : DBA ① 신아림 (#59, 강사 로그인 /instructor/login)
*   - 로그인은 Spring Security 폼 로그인이라 폼 형식(email, password) + CSRF 헤더로 보낸다 (API 6).
*     MB.api 는 본문을 JSON 으로 바꾸고 401 이면 학생 로그인으로 보내므로 여기서는 fetch 를 직접 쓴다
*   - 응답 role 이 INSTRUCTOR 가 아니면(승인 전 강사·학생 계정) 바로 로그아웃하고 안내한다
*   - #372 : '로그인 상태 유지'(목업 기본 켜짐)를 켜면 remember-me=true 를 같이 보낸다. 서버 기능은 #367 (그 전에는 서버가 무시한다)
*  */
(function () {
  var form = document.querySelector('[data-ins-login]');
  if (!form) return;
  var errorEl = form.querySelector('[data-ins-error]');
  var submit = form.querySelector('button[type="submit"]');
  var pw = form.querySelector('input[name="password"]');
  var toggle = form.querySelector('[data-ins-pw-toggle]');
  var keep = form.querySelector('[data-ins-keep]');

  var MESSAGES = {
    LOGIN_FAILED: '이메일 또는 비밀번호가 맞지 않아요.',
    LOGIN_LOCKED: '로그인을 여러 번 실패해 잠시 잠겼어요. 잠시 뒤 다시 시도해 주세요.',
    ACCOUNT_SUSPENDED: '이용이 정지된 계정이에요. 관리자에게 문의해 주세요.',
    ACCOUNT_WITHDRAWN: '탈퇴한 계정이에요.'
  };

  function csrfHeaders() {
    var token = document.querySelector('meta[name="_csrf"]');
    var header = document.querySelector('meta[name="_csrf_header"]');
    var headers = { 'Accept': 'application/json', 'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8' };
    if (token && header) headers[header.content] = token.content;
    return headers;
  }

  /* 오류 문구를 보이고, 문제가 있는 입력칸에 aria-invalid 를 단다 (문구는 aria-describedby 로 두 칸에 연결돼 있다) */
  function showError(text, fields) {
    errorEl.textContent = text || '';
    [form.email, pw].forEach(function (el) {
      el.setAttribute('aria-invalid', fields && fields.indexOf(el) >= 0 ? 'true' : 'false');
    });
  }

  /* 강사 권한이 없는 계정으로 로그인됐으면 세션을 바로 끝낸다.
   * 로그인하면 세션과 CSRF 토큰이 새로 바뀌므로, 이 화면을 한 번 더 받아 새 토큰을 읽은 뒤 로그아웃한다 */
  function logout() {
    return fetch(location.pathname, { credentials: 'same-origin' }).then(function (res) {
      if (!res.ok) throw new Error('CSRF 토큰을 다시 받지 못했어요');
      return res.text();
    }).then(function (html) {
      var doc = new DOMParser().parseFromString(html, 'text/html');
      ['_csrf', '_csrf_header'].forEach(function (name) {
        var fresh = doc.querySelector('meta[name="' + name + '"]');
        var old = document.querySelector('meta[name="' + name + '"]');
        if (!fresh || !old || !fresh.getAttribute('content')) throw new Error('CSRF 토큰이 없어요');
        old.setAttribute('content', fresh.getAttribute('content'));
      });
      return fetch('/api/v1/auth/logout', { method: 'POST', credentials: 'same-origin', headers: csrfHeaders() });
    }).then(function (res) {
      // fetch 로그아웃은 성공하면 204 (API 7). 그 밖이면 세션이 남아 있을 수 있어 실패로 본다
      if (res.status !== 204) throw new Error('로그아웃하지 못했어요');
    });
  }

  toggle.addEventListener('click', function () {
    var show = pw.type === 'password';
    pw.type = show ? 'text' : 'password';
    toggle.textContent = show ? '숨기기' : '보기';
    toggle.setAttribute('aria-pressed', String(show));
  });

  keep.addEventListener('click', function () {
    keep.setAttribute('aria-pressed', String(keep.getAttribute('aria-pressed') !== 'true'));
  });

  [form.email, pw].forEach(function (el) { el.addEventListener('input', function () { showError(''); }); });

  form.addEventListener('submit', function (e) {
    e.preventDefault();
    if (submit.disabled) return;   // 처리 중에 엔터로 다시 들어온 제출은 무시한다
    var email = form.email.value.trim();
    var password = pw.value;
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) { showError('이메일 주소를 확인해 주세요', [form.email]); form.email.focus(); return; }   // 목업 문구
    if (password.length < 8) { showError('비밀번호를 8자 이상 입력해 주세요', [pw]); pw.focus(); return; }
    showError('');
    submit.disabled = true;

    var body = new URLSearchParams();
    body.set('email', email);
    body.set('password', password);
    if (keep.getAttribute('aria-pressed') === 'true') body.set('remember-me', 'true');
    var next = new URLSearchParams(location.search).get('next');
    if (next && next.indexOf('/instructor/') === 0) body.set('next', next);

    fetch('/api/v1/auth/login', { method: 'POST', credentials: 'same-origin', headers: csrfHeaders(), body: body })
      .then(function (res) {
        return res.json().catch(function () { return null; }).then(function (json) { return { ok: res.ok, json: json }; });
      })
      .then(function (r) {
        var data = r.json && r.json.data;
        if (r.ok && data) {
          if (data.role === 'INSTRUCTOR') {
            location.href = data.redirectUrl && data.redirectUrl.indexOf('/instructor/') === 0 ? data.redirectUrl : '/instructor/dashboard';
            return;
          }
          // 승인 전 강사·학생 계정: 강사 화면에 들어오지 못하게 로그아웃하고 안내
          return logout().then(function () {
            showError(data.role === 'ADMIN'
              ? '관리자 계정은 관리자 로그인에서 들어와 주세요.'
              : '강사 권한이 없는 계정이에요. 강사 가입을 신청했다면 관리자 승인 뒤에 로그인할 수 있어요.');
          }, function () {
            showError('강사 권한이 없는 계정인데 로그아웃하지 못했어요. 로그인 상태가 남아 있을 수 있으니 페이지를 새로고침한 뒤 다시 시도해 주세요.');
          });
        }
        var code = r.json && r.json.error && r.json.error.code;
        showError(MESSAGES[code] || (r.json && r.json.error && r.json.error.message) || '로그인하지 못했어요. 다시 시도해 주세요.',
          code === 'LOGIN_FAILED' ? [form.email, pw] : null);
      })
      .catch(function () { showError('네트워크 연결을 확인하고 다시 시도해 주세요.'); })
      .then(function () { submit.disabled = false; });
  });
})();
