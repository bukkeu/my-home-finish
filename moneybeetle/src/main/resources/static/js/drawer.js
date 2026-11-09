/* comment.
*   담당 : DBA ② 김예린 (#49, 공통 Drawer)
*   햄버거 메뉴 (원본 mb-drawer.js) + 관리자 고정 사이드바 열기(모바일)
*   - [data-mb-open] 버튼이 열고, [data-mb-close]·오버레이·Esc 가 닫는다 (화면설계 4.2)
*   - 열려 있는 동안 배경 스크롤을 잠그고, 닫으면 메뉴 버튼으로 포커스를 되돌린다
*   - [data-mb-toggle] 버튼은 바로 다음 .mb-nav__sub 를 접고 편다 (aria-expanded)
*   - [data-mb-lock] 링크는 레벨이 모자란 메뉴다. 이동하지 않고 필요한 레벨을 토스트로 알린다 (NFR-09)
*  */
(function () {
  if (window.__mbDrawer) return;
  window.__mbDrawer = true;

  var prevOverflow = null;
  var lastOpener = null;

  function panel() { return document.querySelector('[data-mb-drawer]'); }
  function overlay() { return document.querySelector('[data-mb-overlay]'); }
  function isOpen() { var p = panel(); return !!p && p.getAttribute('data-open') === 'true'; }

  function setOpen(open) {
    var p = panel(); if (!p) return;
    p.setAttribute('data-open', String(open));
    var o = overlay(); if (o) o.setAttribute('data-open', String(open));
    p.setAttribute('aria-hidden', String(!open));
    if (open) {
      prevOverflow = document.body.style.overflow;
      document.body.style.overflow = 'hidden';
      var c = p.querySelector('[data-mb-close]');
      if (c) setTimeout(function () { c.focus({ preventScroll: true }); }, 0);
    } else {
      document.body.style.overflow = prevOverflow || '';
      prevOverflow = null;
      if (lastOpener) lastOpener.focus({ preventScroll: true });
    }
  }

  document.addEventListener('click', function (e) {
    var openBtn = e.target.closest('[data-mb-open]');
    if (openBtn) { lastOpener = openBtn; setOpen(true); return; }

    if (e.target.closest('[data-mb-close]') || e.target.closest('[data-mb-overlay]')) { setOpen(false); return; }

    var lock = e.target.closest('[data-mb-lock]');
    if (lock) {
      e.preventDefault();
      if (window.MB && MB.toast) MB.toast('Lv.' + lock.getAttribute('data-mb-lock') + '부터 이용할 수 있어요.');
      return;
    }

    var toggle = e.target.closest('[data-mb-toggle]');
    if (toggle) {
      var sub = toggle.nextElementSibling;
      var expanded = toggle.getAttribute('aria-expanded') === 'true';
      toggle.setAttribute('aria-expanded', String(!expanded));
      if (sub) sub.hidden = expanded;
      return;
    }

    // 메뉴 링크를 누르면 이동하면서 Drawer 를 닫는다
    var link = e.target.closest('[data-mb-drawer] a[href]');
    if (link && isOpen() && link.getAttribute('href') !== '#') setTimeout(function () { setOpen(false); }, 60);
  });

  document.addEventListener('keydown', function (e) {
    if (e.key === 'Escape' && isOpen()) setOpen(false);
  });
})();
