/* Money Beetle 공통 햄버거 Drawer — 스타일·슬라이드·열기/닫기 동작 (메뉴 데이터는 각 페이지가 역할별로 가짐) */
(function () {
  if (window.__mbDrawer) return; window.__mbDrawer = true;
  var css = [
    '[data-mb-drawer]{scrollbar-width:thin;scrollbar-color:#D5E1CC transparent}',
    '[data-mb-drawer] nav{line-height:1.4}',
    '[data-mb-drawer] nav button,[data-mb-drawer] nav a{font-size:15px!important;font-weight:600!important;line-height:1.4!important;border-radius:12px!important}',
    '[data-mb-close]:hover{background:#EEF5E7!important}',
    '[data-mb-drawer] :focus-visible,[data-mb-close]:focus-visible{outline:2px solid #9FE870;outline-offset:2px}',
    '@media (prefers-reduced-motion:reduce){[data-mb-drawer],[data-mb-overlay]{transition:none!important}}'
  ].join('\n');
  var st = document.createElement('style'); st.textContent = css; document.head.appendChild(st);

  var prevOverflow = null;
  function drawer() { return document.querySelector('[data-mb-drawer]'); }
  function isOpen() { var d = drawer(); return !!d && d.getAttribute('data-open') === 'true'; }
  function closeBtn() { return document.querySelector('[data-mb-drawer] [data-mb-close]'); }
  function sync() {
    var open = isOpen();
    if (open && prevOverflow === null) { prevOverflow = document.body.style.overflow; document.body.style.overflow = 'hidden'; var c = closeBtn(); if (c) setTimeout(function () { c.focus({ preventScroll: true }); }, 0); }
    if (!open && prevOverflow !== null) { document.body.style.overflow = prevOverflow; prevOverflow = null; var m = document.querySelector('button[aria-label="메뉴"]'); if (m && document.activeElement === document.body) m.focus({ preventScroll: true }); }
  }
  new MutationObserver(sync).observe(document.documentElement, { attributes: true, subtree: true, attributeFilter: ['data-open'] });
  document.addEventListener('keydown', function (e) {
    if (e.key === 'Escape' && isOpen()) { var c = closeBtn(); if (c) c.click(); }
  });
  document.addEventListener('click', function (e) {
    var d = drawer(); if (!d || !isOpen() || !d.contains(e.target)) return;
    var t = e.target.closest('a[href],button'); if (!t || t.hasAttribute('data-mb-close') || t.hasAttribute('data-mb-keep')) return;
    var isLink = t.tagName === 'A' && t.getAttribute('href') && t.getAttribute('href') !== '#';
    if (isLink || t.closest('nav')) setTimeout(function () { var c = closeBtn(); if (c && isOpen()) c.click(); }, 60);
  });
})();
