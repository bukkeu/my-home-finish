/* Money Beetle 관리자 고정 사이드바 너비 조절 — 오른쪽 경계를 끌어서 조절, 더블클릭하면 기본값 */
(function () {
  if (window.__mbSideResize) return; window.__mbSideResize = true;
  var KEY = 'mb-admin-sidebar-w', MIN = 200, MAX = 420, DEF = 280;
  function clamp(v) { return Math.max(MIN, Math.min(MAX, Math.round(v))); }
  function saved() { try { var v = Number(localStorage.getItem(KEY)); return v ? clamp(v) : null; } catch (e) { return null; } }
  function apply(aside, w) { aside.style.width = w + 'px'; aside.style.minWidth = w + 'px'; aside.style.maxWidth = w + 'px'; }
  function setup(aside) {
    if (aside.__mbRz) return; aside.__mbRz = true;
    if (getComputedStyle(aside).position === 'static') aside.style.position = 'relative';
    var w = saved(); if (w) apply(aside, w);
    var h = document.createElement('div');
    h.setAttribute('role', 'separator'); h.setAttribute('aria-orientation', 'vertical'); h.setAttribute('aria-label', '메뉴 너비 조절'); h.tabIndex = 0;
    h.title = '끌어서 메뉴 너비 조절 · 더블클릭하면 기본값';
    h.style.cssText = 'position:absolute;top:0;right:0;width:8px;height:100%;cursor:col-resize;z-index:50;touch-action:none;background:transparent;transition:background .15s';
    var line = document.createElement('div');
    line.style.cssText = 'position:absolute;top:0;bottom:0;right:0;width:2px;border-radius:1px;background:transparent;transition:background .15s';
    h.appendChild(line);
    var on = function () { line.style.background = '#9fe870'; }, off = function () { if (!h.__drag) line.style.background = 'transparent'; };
    h.addEventListener('mouseenter', on); h.addEventListener('mouseleave', off);
    h.addEventListener('pointerdown', function (e) {
      e.preventDefault(); h.__drag = true; on(); h.setPointerCapture(e.pointerId);
      var x0 = e.clientX, w0 = aside.getBoundingClientRect().width;
      document.body.style.cursor = 'col-resize'; document.body.style.userSelect = 'none';
      var mv = function (ev) { apply(aside, clamp(w0 + ev.clientX - x0)); };
      var up = function () {
        h.__drag = false; off(); document.body.style.cursor = ''; document.body.style.userSelect = '';
        h.removeEventListener('pointermove', mv); h.removeEventListener('pointerup', up); h.removeEventListener('pointercancel', up);
        try { localStorage.setItem(KEY, String(Math.round(aside.getBoundingClientRect().width))); } catch (e2) {}
      };
      h.addEventListener('pointermove', mv); h.addEventListener('pointerup', up); h.addEventListener('pointercancel', up);
    });
    h.addEventListener('dblclick', function () { apply(aside, DEF); try { localStorage.setItem(KEY, String(DEF)); } catch (e) {} });
    h.addEventListener('keydown', function (e) {
      if (e.key !== 'ArrowLeft' && e.key !== 'ArrowRight') return; e.preventDefault();
      var nw = clamp(aside.getBoundingClientRect().width + (e.key === 'ArrowRight' ? 16 : -16)); apply(aside, nw);
      try { localStorage.setItem(KEY, String(nw)); } catch (e2) {}
    });
    aside.appendChild(h);
  }
  function scan() { var a = document.querySelectorAll('aside[data-mb-pinned]'); for (var i = 0; i < a.length; i++) setup(a[i]); }
  new MutationObserver(scan).observe(document.documentElement, { childList: true, subtree: true });
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', scan); else scan();
})();
