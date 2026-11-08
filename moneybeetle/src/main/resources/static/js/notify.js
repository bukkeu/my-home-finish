/* comment.
*   담당 : DBA ② (공통 레이아웃, 이슈 #49)
*   알림 벨 (원본 mb-notify.js) - 목업 데이터 대신 서버 API 를 쓴다
*   - 벨 버튼([data-mb-bell])을 누르면 오른쪽 알림 패널이 열린다
*   - GET   /api/v1/notifications           { unreadCount, items:[{ notificationId, title, linkUrl, isRead, createdAt }] }
*   - PATCH /api/v1/notifications/{id}/read  알림 하나 읽음
*   - PATCH /api/v1/notifications/read-all   모두 읽음
*   알림 API(#14)가 아직 없으면 패널은 "새 알림이 없어요"로 보인다 (오류를 화면에 내지 않는다).
*   "알림 끄기"는 이 브라우저의 표시 설정이라 localStorage 에 둔다 (서버 데이터가 아니다).
*  */
(function () {
  if (window.__mbNotify) return;
  window.__mbNotify = true;

  var MUTE_KEY = 'mb-notify-mute';
  var ov, panel, list, items = [], unread = 0;

  function muted() { try { return localStorage.getItem(MUTE_KEY) === '1'; } catch (e) { return false; } }
  function setMuted(v) {
    try { localStorage.setItem(MUTE_KEY, v ? '1' : '0'); } catch (e) { /* 저장이 막힌 브라우저는 이번 화면에서만 적용 */ }
    document.documentElement.classList.toggle('mb-muted', v);
  }
  function badge() { document.documentElement.classList.toggle('mb-unread', unread > 0); }

  function ago(iso) {
    var s = Math.max(0, (Date.now() - new Date(iso).getTime()) / 1000);
    if (s < 60) return '방금';
    if (s < 3600) return Math.floor(s / 60) + '분 전';
    if (s < 86400) return Math.floor(s / 3600) + '시간 전';
    return Math.floor(s / 86400) + '일 전';
  }

  function build() {
    if (panel) return;
    ov = document.createElement('div'); ov.className = 'mbn-ov';
    panel = document.createElement('aside');
    panel.className = 'mbn-panel'; panel.setAttribute('role', 'dialog'); panel.setAttribute('aria-label', '알림'); panel.setAttribute('aria-hidden', 'true');
    panel.innerHTML =
      '<div class="mbn-head"><strong>알림</strong><button class="mbn-btn" data-act="all">모두 읽음</button>' +
      '<button class="mbn-x" data-act="close" aria-label="닫기"><svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"><path d="M6 6l12 12M18 6L6 18"/></svg></button></div>' +
      '<div class="mbn-mute"><span>알림 끄기<small>꺼도 알림은 쌓이고 새 알림 표시만 사라져요</small></span><button class="mbn-sw" role="switch" data-act="mute" aria-label="알림 표시"></button></div>' +
      '<div class="mbn-list"></div>';
    document.body.appendChild(ov); document.body.appendChild(panel);
    list = panel.querySelector('.mbn-list');
    ov.addEventListener('click', close);
    panel.addEventListener('click', onPanelClick);
  }

  function render() {
    if (!panel) return;
    var sw = panel.querySelector('[data-act=mute]');
    sw.setAttribute('aria-checked', String(!muted()));
    list.textContent = '';
    if (!items.length) {
      var empty = document.createElement('div'); empty.className = 'mbn-empty'; empty.textContent = '새 알림이 없어요';
      list.appendChild(empty); return;
    }
    items.forEach(function (n) {
      var a = document.createElement('a');
      a.className = 'mbn-item' + (n.isRead ? '' : ' new');
      a.href = n.linkUrl || '#'; a.setAttribute('data-id', n.notificationId);
      var dot = document.createElement('span'); dot.className = 'mbn-dot';
      var txt = document.createElement('span'); txt.className = 'mbn-txt';
      var title = document.createElement('span'); title.className = 'mbn-title'; title.textContent = n.title;   // textContent: HTML 로 해석하지 않는다
      txt.appendChild(title);
      var time = document.createElement('span'); time.className = 'mbn-time'; time.textContent = ago(n.createdAt);
      a.appendChild(dot); a.appendChild(txt); a.appendChild(time);
      list.appendChild(a);
    });
  }

  function load() {
    if (!window.MB || !MB.api) return Promise.resolve();
    return MB.api('/api/v1/notifications?page=0&size=20').then(function (data) {
      items = (data && data.items) || [];
      unread = (data && data.unreadCount) || 0;
    }, function () { items = []; unread = 0; }).then(function () { badge(); render(); });
  }

  function onPanelClick(e) {
    var act = e.target.closest('[data-act]');
    if (act) {
      var a = act.getAttribute('data-act');
      if (a === 'close') return close();
      if (a === 'mute') { setMuted(!muted()); return render(); }
      if (a === 'all') {
        return MB.api('/api/v1/notifications/read-all', { method: 'PATCH' }).then(function () {
          items.forEach(function (n) { n.isRead = true; }); unread = 0; badge(); render();
        }, function (err) { MB.toast(err.message, 'error'); });
      }
    }
    var item = e.target.closest('.mbn-item.new');
    if (item) {
      var id = item.getAttribute('data-id');
      // keepalive: 링크로 이동하는 중에도 읽음 요청이 취소되지 않게 한다
      MB.api('/api/v1/notifications/' + id + '/read', { method: 'PATCH', keepalive: true }).catch(function () { /* 읽음 실패는 이동을 막지 않는다 */ });
    }
  }

  function open() { build(); render(); ov.classList.add('on'); panel.classList.add('on'); panel.setAttribute('aria-hidden', 'false'); load(); }
  function close() { if (!panel) return; ov.classList.remove('on'); panel.classList.remove('on'); panel.setAttribute('aria-hidden', 'true'); }

  document.addEventListener('click', function (e) { if (e.target.closest('[data-mb-bell]')) open(); });
  document.addEventListener('keydown', function (e) { if (e.key === 'Escape') close(); });
  if (muted()) document.documentElement.classList.add('mb-muted');
  // 화면이 열릴 때 안 읽은 알림 수만 가져와 벨 표시를 갱신한다
  if (document.querySelector('[data-mb-bell]')) load();
})();
