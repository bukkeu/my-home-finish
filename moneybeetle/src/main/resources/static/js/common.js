/* comment.
*   담당 : DBA ② (공통 레이아웃, 이슈 #49)
*   공통 fetch 래퍼(ApiResponse 처리, 401 이면 로그인 이동), 토스트
*   - MB.api(url, options)  : 성공하면 응답의 data 를 돌려주고, 실패하면 { code, message } 를 던진다
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
    if (opts.body && typeof opts.body === 'object' && !(opts.body instanceof FormData)) {
      opts.headers['Content-Type'] = 'application/json';
      opts.body = JSON.stringify(opts.body);
    }
    return fetch(url, opts).then(function (res) {
      if (res.status === 401) {
        // 세션 만료·비로그인: 로그인 화면으로 보내고, 로그인 뒤 원래 화면으로 돌아오게 주소를 남긴다
        window.location.href = '/login?next=' + encodeURIComponent(window.location.pathname + window.location.search);
        return Promise.reject({ code: 'UNAUTHORIZED', message: '로그인이 필요해요.' });
      }
      if (res.status === 204) return null;
      return res.json().catch(function () { return null; }).then(function (body) {
        if (res.ok && (!body || body.success !== false)) return body && 'data' in body ? body.data : body;
        var err = (body && body.error) || {};
        return Promise.reject({ code: err.code || 'ERROR', message: err.message || '요청을 처리하지 못했어요.' });
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
