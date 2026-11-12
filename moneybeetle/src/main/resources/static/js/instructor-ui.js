/* comment.
*   담당 : DBA ① 신아림 (#360, 강사 센터 공통 부품)
*   강사 센터 탭 파일(instructor-mypage.js · instructor-qna.js)이 같이 쓰는 작은 도구 (window.MBIns)
*   - h(tag, attrs, children) : 요소 만들기. 글자는 text 로만 넣는다 (사용자 글을 HTML 로 해석하지 않음)
*   - fill(el, ...)            : 자식 바꿔 넣기 (배열은 펼치고 null·false 는 건너뛴다)
*   - toast(message)           : 화면 아래 가운데 알림 (원본 InstructorDashboard.dc.html 의 toast 모양)
*   - confirm(message, onOk)   : 삭제 확인 창 (원본의 confirm 모양, Esc·바깥 클릭으로 닫기)
*   - date(value)              : '2026-11-03T10:00' → '2026.11.03'
*   - errorText(err, fallback) : 서버 오류 문구 (입력값 오류면 첫 칸의 문구)
*   - tabs                     : 탭 이름 → 그리기 함수. instructor-dashboard.js 가 data-tab 에 맞는 것을 부른다
*  */
(function () {
  if (window.MBIns) return;

  function h(tag, attrs, children) {
    var el = document.createElement(tag);
    Object.keys(attrs || {}).forEach(function (key) {
      var v = attrs[key];
      if (v === null || v === undefined || v === false) return;
      if (key === 'text') el.textContent = v;
      else if (key === 'class') el.className = v;
      else if (key === 'value') el.value = v;
      else if (key.indexOf('on') === 0 && typeof v === 'function') el.addEventListener(key.slice(2), v);
      else el.setAttribute(key, v === true ? '' : v);
    });
    (children || []).forEach(function (c) {
      if (c === null || c === undefined || c === false) return;
      el.appendChild(typeof c === 'string' ? document.createTextNode(c) : c);
    });
    return el;
  }

  function fill(el) {
    var nodes = [];
    [].slice.call(arguments, 1).forEach(function add(x) {
      if (Array.isArray(x)) x.forEach(add);
      else if (x !== null && x !== undefined && x !== false) nodes.push(x);
    });
    el.replaceChildren.apply(el, nodes);
  }

  function date(value) {
    var m = String(value || '').match(/^(\d{4})-(\d{2})-(\d{2})/);
    return m ? m[1] + '.' + m[2] + '.' + m[3] : '';
  }

  function errorText(err, fallback) {
    if (err && Array.isArray(err.fields) && err.fields.length && err.fields[0].message) return err.fields[0].message;
    return (err && err.message) || fallback;
  }

  var toastEl = null;
  var toastTimer = null;
  function toast(message) {
    if (!toastEl) {
      toastEl = h('div', { class: 'ins-toast', role: 'status', 'aria-live': 'polite' });
      document.body.appendChild(toastEl);
    }
    toastEl.textContent = message;
    toastEl.classList.add('is-on');
    clearTimeout(toastTimer);
    toastTimer = setTimeout(function () { toastEl.classList.remove('is-on'); }, 2600);
  }

  function confirmBox(message, onOk) {
    var before = document.activeElement;
    var cancel = h('button', { type: 'button', class: 'ins-confirm__cancel', text: '취소' });
    var ok = h('button', { type: 'button', class: 'ins-confirm__ok', text: '삭제' });
    var box = h('div', { class: 'ins-confirm', role: 'dialog', 'aria-modal': 'true', 'aria-labelledby': 'ins-confirm-msg' }, [
      h('span', { class: 'ins-confirm__msg', id: 'ins-confirm-msg', text: message }),
      h('span', { class: 'ins-confirm__sub', text: '삭제하면 되돌릴 수 없어요.' }),
      h('div', { class: 'ins-confirm__actions' }, [cancel, ok])
    ]);
    var layer = h('div', { class: 'ins-confirm-layer' }, [box]);
    function close() {
      document.removeEventListener('keydown', onKey);
      layer.remove();
      if (before && before.focus) before.focus();
    }
    function onKey(e) { if (e.key === 'Escape') close(); }
    layer.addEventListener('click', function (e) { if (e.target === layer) close(); });
    cancel.addEventListener('click', close);
    ok.addEventListener('click', function () { close(); onOk(); });
    document.addEventListener('keydown', onKey);
    document.body.appendChild(layer);
    cancel.focus();
  }

  window.MBIns = { h: h, fill: fill, date: date, errorText: errorText, toast: toast, confirm: confirmBox, tabs: {} };
})();
