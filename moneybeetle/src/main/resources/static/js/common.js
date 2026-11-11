/* comment.
*   담당 : DBA ② 김예린 (#49, 공통 레이아웃)
*   공통 fetch 래퍼(ApiResponse 처리, 401 이면 로그인 이동), 토스트
*   - MB.api(url, options)  : 성공하면 응답의 data 를 돌려주고, 실패하면 { code, message, fields } 를 던진다 (fields 는 입력칸별 오류, 없으면 null. 네트워크 실패는 code 'NETWORK_ERROR'). POST·PUT·PATCH·DELETE 에는 CSRF 헤더를 붙인다
*   - MB.toast(message, kind) : 오른쪽 아래에 잠깐 보이는 알림 (kind: 'error' 이면 붉은 테두리)
*   응답 형식: { success, data, error: { code, message } } (API 명세서 1-2)
*  */
(function () {
  if (window.MB && window.MB.__common) return;
  var MB = window.MB = window.MB || {};
  MB.__common = true;

  MB.api = function (url, options) {
    var opts = Object.assign({ credentials: 'same-origin', headers: {} }, options || {});
    opts.headers = Object.assign({ 'Accept': 'application/json' }, opts.headers);
    // CSRF 보호가 켜져 있어서 데이터를 바꾸는 요청(POST·PUT·PATCH·DELETE)에는 토큰 헤더를 함께 보낸다.
    // 토큰은 headFragment 가 <meta name="_csrf">·<meta name="_csrf_header"> 로 내려 준다.
    var method = (opts.method || 'GET').toUpperCase();
    if (method !== 'GET' && method !== 'HEAD') {
      var token = document.querySelector('meta[name="_csrf"]');
      var header = document.querySelector('meta[name="_csrf_header"]');
      if (token && header) opts.headers[header.content] = token.content;
    }
    if (opts.body && typeof opts.body === 'object' && !(opts.body instanceof FormData)) {
      opts.headers['Content-Type'] = 'application/json';
      opts.body = JSON.stringify(opts.body);
    }
    // 서버에 닿지 못한 경우(네트워크 끊김·타임아웃, 응답 본문을 읽다가 끊긴 경우 등)도 응답 오류와 같은 모양으로 던진다
    var networkError = function () {
      return Promise.reject({ code: 'NETWORK_ERROR', message: '네트워크 연결을 확인하고 다시 시도해 주세요.', fields: null });
    };
    return fetch(url, opts).catch(networkError).then(function (res) {
      if (res.status === 401) {
        // 세션 만료·비로그인: 로그인 화면으로 보내고, 로그인 뒤 원래 화면으로 돌아오게 주소를 남긴다
        window.location.href = '/login?next=' + encodeURIComponent(window.location.pathname + window.location.search);
        return Promise.reject({ code: 'UNAUTHORIZED', message: '로그인이 필요해요.', fields: null });
      }
      if (res.status === 204) return null;
      // 본문이 JSON이 아닌 경우(빈 본문 등, SyntaxError)는 null 로 둔다.
      // 본문을 읽다가 실패한 경우는 성공 응답이면 네트워크 오류로 던지고, HTTP 오류 응답(res.ok 가 false)이면
      // 이미 상태 코드를 받았으므로 null 로 두어 아래의 공통 오류 경로(code 'ERROR')로 보낸다.
      return res.json().catch(function (e) {
        return e instanceof SyntaxError || !res.ok ? null : networkError();
      }).then(function (body) {
        if (res.ok && (!body || body.success !== false)) return body && 'data' in body ? body.data : body;
        var err = (body && body.error) || {};
        // fields: 입력칸별 오류(@Valid 실패). 있으면 화면이 입력칸 아래에 문구를 보여 줄 수 있다
        return Promise.reject({ code: err.code || 'ERROR', message: err.message || '요청을 처리하지 못했어요.', fields: err.fields || null, status: res.status });
      });
    });
  };

  var box;
  MB.toast = function (message, kind) {
    if (!box) {
      box = document.createElement('div');
      box.className = 'mb-toast-box';
      box.setAttribute('aria-live', 'polite');
      document.body.appendChild(box);
    }
    var t = document.createElement('div');
    t.className = 'mb-toast' + (kind === 'error' ? ' mb-toast--error' : '');
    t.textContent = message;               // textContent: 서버·사용자 문구를 HTML 로 해석하지 않는다
    box.appendChild(t);
    requestAnimationFrame(function () { t.classList.add('on'); });
    setTimeout(function () {
      t.classList.remove('on');
      setTimeout(function () { t.remove(); }, 200);
    }, 3200);
  };
})();
