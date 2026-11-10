/* comment.
*   담당 : DBA ① 신아림 (#52, 강의 화면 공통)
*   강의 목록·상세가 같이 쓰는 작은 도우미 (window.MBLec)
*   - h(tag, attrs, children) : 요소 만들기. 글자는 textContent 로만 넣어서 서버 문구를 HTML 로 해석하지 않는다
*   - badge(status)           : 학습 상태 배지 (✓ 학습 완료 / 학습 중 / 시작 전)
*   - tone(i)                 : 썸네일이 없을 때 쓰는 배경색 (디자인 시안의 5가지 톤)
*   - minutes(seconds)        : "8분" 같은 길이 표시
*  */
(function () {
  if (window.MBLec) return;

  var TONES = ['#E3F3D2', '#DCEFF0', '#EEF0DA', '#E6EBF6', '#F2EAD9'];
  var BADGES = {
    COMPLETED: { text: '✓ 학습 완료', cls: 'lec-badge--done' },
    IN_PROGRESS: { text: '학습 중', cls: 'lec-badge--doing' },
    NONE: { text: '시작 전', cls: 'lec-badge--todo' }
  };

  function h(tag, attrs, children) {
    var el = document.createElement(tag);
    Object.keys(attrs || {}).forEach(function (key) {
      var value = attrs[key];
      if (value === null || value === undefined || value === false) return;
      if (key === 'text') el.textContent = value;
      else if (key === 'class') el.className = value;
      else if (key === 'style') el.setAttribute('style', value);
      else if (key.indexOf('on') === 0 && typeof value === 'function') el.addEventListener(key.slice(2), value);
      else el.setAttribute(key, value === true ? '' : value);
    });
    (children || []).forEach(function (child) {
      if (child === null || child === undefined || child === false) return;
      el.appendChild(typeof child === 'string' ? document.createTextNode(child) : child);
    });
    return el;
  }

  function badge(status) {
    var b = BADGES[status] || BADGES.NONE;
    return h('span', { class: 'lec-badge ' + b.cls, text: b.text });
  }

  function tone(i) {
    return TONES[Math.abs(i || 0) % TONES.length];
  }

  /* 썸네일: 강사가 올린 이미지가 있으면 그 이미지, 없으면 톤 배경 */
  function thumbStyle(thumbnailUrl, i) {
    if (thumbnailUrl && /^\/uploads\//.test(thumbnailUrl)) {
      return 'background-image:url("' + thumbnailUrl.replace(/"/g, '') + '");background-color:' + tone(i);
    }
    return 'background-color:' + tone(i);
  }

  function minutes(seconds) {
    var s = Math.max(0, Math.round(seconds || 0));
    if (s < 60) return s + '초';
    var m = Math.round(s / 60);
    return m + '분';
  }

  function clock(seconds) {
    var s = Math.max(0, Math.floor(seconds || 0));
    var m = Math.floor(s / 60);
    return m + ':' + String(s % 60).padStart(2, '0');
  }

  function percent(part, total) {
    return total > 0 ? Math.round(Math.min(part, total) / total * 100) : 0;
  }

  /* 불러오기 실패 안내 + 다시 시도 버튼 */
  function errorBox(err, retry) {
    return h('div', { class: 'lec-state lec-state--error', role: 'alert' }, [
      (err && err.message) || '강의를 불러오지 못했어요.',
      h('br'),
      retry ? h('button', { type: 'button', class: 'btn btn-secondary', text: '다시 시도', onclick: retry }) : null
    ]);
  }

  window.MBLec = { h: h, badge: badge, tone: tone, thumbStyle: thumbStyle, minutes: minutes, clock: clock,
    percent: percent, errorBox: errorBox };
})();
