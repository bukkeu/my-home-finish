/* Money Beetle 사용자 알림 — 우측 상단 종 버튼([data-mb-bell]) + 오른쪽 알림 패널 + 우측 하단 토스트 (목업 데이터) */
(function () {
  if (window.__mbNotify) return; window.__mbNotify = true;
  var BASE = [
    { id: 'n5', kind: '문의 답변', title: '관리자가 문의에 답변했어요', body: '‘청약통장 인정금액이 잔액보다 적게 나와요’에 답변이 등록됐어요.', time: '방금', href: 'UserInquiry.dc.html?open=3' },
    { id: 'n4', kind: '오늘의 경제 용어', title: '오늘의 경제 용어 5개가 도착했어요', body: '복리, 실수령액, 배당 등 오늘의 용어를 확인해 보세요.', time: '1시간 전', href: 'UserHome-vr2.dc.html' },
    { id: 'n3', kind: '모의 투자', title: '시즌 7 · 5턴이 시작됐어요', body: '새 턴의 가상 시세와 뉴스가 공개됐어요.', time: '3시간 전', href: 'UserDemoTrading.dc.html' },
    { id: 'n2', kind: '모의 청약', title: '새 청약 공고가 올라왔어요', body: '머니숲 리버뷰 공고가 게시됐어요. 지금부터 신청할 수 있어요.', time: '어제', href: 'UserDemoHousingSubscription.dc.html' },
    { id: 'n1', kind: '학습', title: '이어서 들을 강의가 있어요', body: '‘금리와 채권 가격’ 3강 중 2강까지 들었어요.', time: '2일 전', href: 'UserLectureDetail.dc.html' }
  ];
  function extra() { try { return JSON.parse(localStorage.getItem('mb-notify-extra') || '[]'); } catch (e) { return []; } }
  function all() { return extra().concat(BASE); }
  var KEY = 'mb-notify-read';
  function readSet() { try { return JSON.parse(localStorage.getItem(KEY) || '[]'); } catch (e) { return []; } }
  function saveSet(a) { try { localStorage.setItem(KEY, JSON.stringify(a)); } catch (e) {} }
  var MUTE = 'mb-notify-mute';
  function muted() { try { return localStorage.getItem(MUTE) === '1'; } catch (e) { return false; } }
  function setMuted(v) { try { localStorage.setItem(MUTE, v ? '1' : '0'); } catch (e) {} }
  function unread() { var r = readSet(); return all().filter(function (n) { return r.indexOf(n.id) < 0; }); }
  function esc(s) { return String(s).replace(/[&<>"]/g, function (c) { return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]; }); }

  var css = [
    '[data-mb-bell]{position:relative}',
    'html.mb-muted [data-mb-bell]::before{content:"";position:absolute;left:50%;top:50%;width:2px;height:22px;margin:-11px 0 0 -1px;border-radius:2px;background:currentColor;box-shadow:0 0 0 1.5px #fff;transform:rotate(-45deg);pointer-events:none}',
    'html.mb-muted [data-mb-bell]{color:#687161}',
    'html.mb-unread:not(.mb-muted) [data-mb-bell]::after{content:"";position:absolute;top:7px;right:8px;width:8px;height:8px;border-radius:50%;background:#E5484D;box-shadow:0 0 0 2px #fff}',
    '.mbn-ov{position:fixed;inset:0;z-index:90;background:rgba(38,59,26,.18);opacity:0;pointer-events:none;transition:opacity 180ms ease-out}',
    '.mbn-ov.on{opacity:1;pointer-events:auto}',
    '.mbn-panel{position:fixed;top:12px;right:12px;bottom:12px;z-index:91;width:min(380px,calc(100vw - 24px));background:#fff;border-radius:20px;box-shadow:0 18px 48px rgba(38,59,26,.18);display:flex;flex-direction:column;transform:translateX(calc(100% + 24px));transition:transform 250ms ease-out;font-family:inherit;color:#263B1A}',
    '.mbn-panel.on{transform:none}',
    '.mbn-head{display:flex;align-items:center;gap:8px;padding:20px 20px 12px}',
    '.mbn-head strong{flex:1;font-size:18px;font-weight:800}',
    '.mbn-btn{all:unset;cursor:pointer;font-size:13px;font-weight:600;color:#687161;padding:6px 10px;border-radius:999px}',
    '.mbn-btn:hover{background:#EEF5E7;color:#263B1A}',
    '.mbn-x{all:unset;cursor:pointer;width:34px;height:34px;border-radius:50%;display:grid;place-items:center;color:#263B1A}',
    '.mbn-x:hover{background:#EEF5E7}',
    '.mbn-mute{display:flex;align-items:center;gap:12px;margin:0 20px 12px;padding:12px 14px;border-radius:14px;background:#F7FAF3;border:1px solid #D5E1CC;font-size:14px;font-weight:700}',
    '.mbn-mute span{flex:1;display:flex;flex-direction:column;gap:2px}',
    '.mbn-mute small{font-size:12px;font-weight:500;color:#687161}',
    '.mbn-sw{all:unset;cursor:pointer;flex:none;width:44px;height:26px;border-radius:999px;background:#9FE870;position:relative;transition:background 180ms ease-out}',
    '.mbn-sw::after{content:"";position:absolute;top:3px;left:21px;width:20px;height:20px;border-radius:50%;background:#fff;box-shadow:0 1px 3px rgba(38,59,26,.25);transition:left 180ms ease-out}',
    '.mbn-sw[aria-checked=false]{background:#D5E1CC}',
    '.mbn-sw[aria-checked=false]::after{left:3px}',
    '.mbn-sw:focus-visible{outline:2px solid #263B1A;outline-offset:2px}',
    '.mbn-list{flex:1;overflow-y:auto;padding:4px 12px 16px;display:flex;flex-direction:column;gap:6px}',
    '.mbn-item{display:flex;gap:12px;padding:14px;border-radius:14px;text-decoration:none;color:inherit;background:#fff;border:1px solid #D5E1CC}',
    '.mbn-item:hover{background:#F7FAF3}',
    '.mbn-item.new{background:#EAF7D8;border-color:#B9EF8E}',
    '.mbn-dot{flex:none;width:8px;height:8px;border-radius:50%;margin-top:6px;background:transparent}',
    '.mbn-item.new .mbn-dot{background:#9FE870;box-shadow:0 0 0 3px rgba(159,232,112,.35)}',
    '.mbn-txt{flex:1;min-width:0;display:flex;flex-direction:column;gap:3px}',
    '.mbn-kind{font-size:12px;font-weight:700;color:#687161}',
    '.mbn-title{font-size:14px;font-weight:700;line-height:1.4}',
    '.mbn-body{font-size:13px;line-height:1.5;color:#687161;word-break:keep-all}',
    '.mbn-time{flex:none;font-size:12px;color:#687161}',
    '.mbn-toast{position:fixed;right:20px;bottom:20px;z-index:92;width:min(360px,calc(100vw - 40px));background:#fff;border-radius:16px;box-shadow:0 14px 36px rgba(38,59,26,.2);border:1px solid #D5E1CC;padding:16px 16px 16px 18px;display:flex;gap:12px;align-items:flex-start;transform:translateX(calc(100% + 40px));transition:transform 250ms ease-out;color:#263B1A;cursor:pointer}',
    '.mbn-toast.on{transform:none}',
    '.mbn-ticon{flex:none;width:36px;height:36px;border-radius:50%;background:#9FE870;display:grid;place-items:center;color:#263B1A}',
    '@media (prefers-reduced-motion:reduce){.mbn-panel,.mbn-toast,.mbn-ov{transition:none}}'
  ].join('\n');
  var st = document.createElement('style'); st.textContent = css; document.head.appendChild(st);

  var BELL = '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9"></path><path d="M10.3 21a1.94 1.94 0 0 0 3.4 0"></path></svg>';
  var ov, panel, list, toast;
  function build() {
    if (panel) return;
    ov = document.createElement('div'); ov.className = 'mbn-ov';
    panel = document.createElement('aside'); panel.className = 'mbn-panel'; panel.setAttribute('role', 'dialog'); panel.setAttribute('aria-label', '알림'); panel.setAttribute('aria-hidden', 'true');
    panel.innerHTML = '<div class="mbn-head"><strong>알림</strong><button class="mbn-btn" data-act="all">모두 읽음</button><button class="mbn-x" data-act="close" aria-label="닫기"><svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"><path d="M18 6 6 18M6 6l12 12"></path></svg></button></div><div class="mbn-mute"><span>알림 받기<small class="mbn-mute-note"></small></span><button class="mbn-sw" role="switch" data-act="mute" aria-label="알림 받기"></button></div><div class="mbn-list"></div>';
    list = panel.querySelector('.mbn-list');
    document.body.appendChild(ov); document.body.appendChild(panel);
    ov.addEventListener('click', close);
    panel.addEventListener('click', function (e) {
      var a = e.target.closest('[data-act]');
      if (a && a.getAttribute('data-act') === 'close') close();
      if (a && a.getAttribute('data-act') === 'mute') { setMuted(!muted()); render(); }
      if (a && a.getAttribute('data-act') === 'all') { saveSet(all().map(function (n) { return n.id; })); render(); }
      var it = e.target.closest('.mbn-item'); if (it) { var r = readSet(); if (r.indexOf(it.dataset.id) < 0) { r.push(it.dataset.id); saveSet(r); } }
    });
  }
  function render() {
    var r = readSet();
    list.innerHTML = all().map(function (n) {
      var isNew = r.indexOf(n.id) < 0;
      return '<a class="mbn-item' + (isNew ? ' new' : '') + '" data-id="' + n.id + '" href="' + n.href + '"><span class="mbn-dot"></span><span class="mbn-txt"><span class="mbn-kind">' + esc(n.kind) + '</span><span class="mbn-title">' + esc(n.title) + '</span><span class="mbn-body">' + esc(n.body) + '</span></span><span class="mbn-time">' + esc(n.time) + '</span></a>';
    }).join('');
    var m = muted(); panel.querySelector('.mbn-sw').setAttribute('aria-checked', String(!m));
    panel.querySelector('.mbn-mute-note').textContent = m ? '알림이 꺼져 있어요. 새 알림 팝업과 빨간 점이 표시되지 않아요.' : '새 알림이 오면 화면 오른쪽 아래에 알려드려요.';
    dot();
  }
  function dot() { document.documentElement.classList.toggle('mb-unread', unread().length > 0); document.documentElement.classList.toggle('mb-muted', muted()); }
  function open() { build(); render(); hideToast(); ov.classList.add('on'); panel.classList.add('on'); panel.setAttribute('aria-hidden', 'false'); var x = panel.querySelector('.mbn-x'); setTimeout(function () { x.focus({ preventScroll: true }); }, 0); }
  function close() { if (!panel) return; ov.classList.remove('on'); panel.classList.remove('on'); panel.setAttribute('aria-hidden', 'true'); }
  function hideToast() { if (toast) toast.classList.remove('on'); }
  function showToast() {
    if (muted()) return; var u = unread(); if (!u.length) return;
    try { if (sessionStorage.getItem('mb-notify-toast')) return; sessionStorage.setItem('mb-notify-toast', '1'); } catch (e) {}
    var n = u[0];
    toast = document.createElement('div'); toast.className = 'mbn-toast'; toast.setAttribute('role', 'status');
    toast.innerHTML = '<span class="mbn-ticon">' + BELL + '</span><span class="mbn-txt"><span class="mbn-kind">' + esc(n.kind) + ' · ' + esc(n.time) + '</span><span class="mbn-title">' + esc(n.title) + '</span><span class="mbn-body">' + esc(n.body) + '</span></span><button class="mbn-x" aria-label="알림 닫기" style="width:28px;height:28px"><svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><path d="M18 6 6 18M6 6l12 12"></path></svg></button>';
    document.body.appendChild(toast);
    toast.addEventListener('click', function (e) { if (e.target.closest('.mbn-x')) { hideToast(); return; } open(); });
    setTimeout(function () { toast.classList.add('on'); }, 900);
    setTimeout(hideToast, 6900);
  }
  document.addEventListener('click', function (e) { if (e.target.closest('[data-mb-bell]')) { e.preventDefault(); open(); } });
  document.addEventListener('keydown', function (e) { if (e.key === 'Escape' && panel && panel.classList.contains('on')) close(); });
  function init() { dot(); showToast(); }
  window.addEventListener('storage', function (e) { if (e.key === 'mb-notify-extra' || e.key === KEY || e.key === MUTE) { dot(); if (panel && panel.classList.contains('on')) render(); } });
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', init); else init();
})();
