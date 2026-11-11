/* comment.
*   담당 : DBA ② 김예린 (#61, 관리자 대시보드 틀과 탭)
*   관리자 탭들이 같이 쓰는 작은 도구. MB.api 와 MB.toast 는 common.js 가 준다.
*   - MBA.el(tag, className, text) : 요소 만들기. 글은 textContent 로만 넣어 서버·사용자 문구를 HTML 로 해석하지 않는다
*   - MBA.byId(id), MBA.clear(node), MBA.won(number) : 자주 쓰는 도우미
*   - MBA.seoulToday() / MBA.seoulYearMonth() : 서울 날짜(yyyy-MM-dd, yyyy-MM). 서버의 "오늘"도 서울 기준이다 (DP-42)
*   - MBA.confirm(title, body, okLabel) : 확인 창. 확인하면 true 로 이어지는 Promise 를 돌려준다
*   - MBA.errorText(err) : MB.api 가 던진 오류를 사람이 읽을 문구로 (입력칸별 오류가 있으면 그것도 붙인다)
*  */
(function () {
  'use strict';
  if (window.MBA) return;
  var MBA = window.MBA = {};

  MBA.el = function (tag, cls, text) {
    var node = document.createElement(tag);
    if (cls) node.className = cls;
    if (text != null) node.textContent = text;
    return node;
  };
  MBA.byId = function (id) { return document.getElementById(id); };
  MBA.clear = function (node) { while (node && node.firstChild) node.removeChild(node.firstChild); };
  MBA.won = function (n) { return Number(n || 0).toLocaleString('ko-KR'); };

  MBA.seoulToday = function () {
    try {
      return new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Seoul' }).format(new Date());
    } catch (e) {
      return new Date().toISOString().slice(0, 10);   // 시간대를 못 쓰는 환경은 UTC 날짜로
    }
  };
  MBA.seoulYearMonth = function () { return MBA.seoulToday().slice(0, 7); };

  MBA.errorText = function (err) {
    if (!err) return '요청을 처리하지 못했어요.';
    var text = err.message || '요청을 처리하지 못했어요.';
    if (err.fields && typeof err.fields === 'object') {
      var parts = Object.keys(err.fields).map(function (k) { return err.fields[k]; });
      if (parts.length) text += ' (' + parts.join(', ') + ')';
    }
    return text;
  };

  var modal;
  MBA.confirm = function (title, body, okLabel) {
    return new Promise(function (resolve) {
      if (!modal) {
        modal = MBA.el('div', 'adm-modal-backdrop');
        modal.hidden = true;
        document.body.appendChild(modal);
      }
      MBA.clear(modal);
      var opener = document.activeElement;
      var box = MBA.el('div', 'adm-modal');
      box.setAttribute('role', 'alertdialog');
      box.setAttribute('aria-modal', 'true');
      var heading = MBA.el('h2', null, title);
      heading.id = 'adm-modal-title';
      heading.setAttribute('tabindex', '-1');
      box.setAttribute('aria-labelledby', 'adm-modal-title');
      box.appendChild(heading);
      if (body) box.appendChild(MBA.el('p', null, body));
      var actions = MBA.el('div', 'adm-actions');
      var cancel = MBA.el('button', 'btn btn-ghost', '취소');
      cancel.type = 'button';
      var ok = MBA.el('button', 'btn btn-primary', okLabel || '확인');
      ok.type = 'button';
      actions.appendChild(cancel);
      actions.appendChild(ok);
      box.appendChild(actions);
      modal.appendChild(box);
      modal.hidden = false;
      heading.focus();

      function done(result) {
        modal.hidden = true;
        document.removeEventListener('keydown', onKey);
        if (opener && opener.focus) opener.focus();
        resolve(result);
      }
      function onKey(event) {
        if (event.key === 'Escape') done(false);
      }
      cancel.addEventListener('click', function () { done(false); });
      ok.addEventListener('click', function () { done(true); });
      modal.addEventListener('click', function (event) { if (event.target === modal) done(false); }, { once: true });
      document.addEventListener('keydown', onKey);
    });
  };
})();
