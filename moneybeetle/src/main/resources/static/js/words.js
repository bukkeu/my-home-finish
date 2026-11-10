/* comment.
 * 담당: DBA ② 김예린 (#51, 오늘의 경제단어 팝업)
 * 헤더의 "오늘의 경제단어" 버튼([data-mb-words])을 누르면 오늘의 용어 5개를 팝업으로 보여 준다 (원본 UserHome 의 wordsOpen).
 * - GET /api/v1/economic-terms/today (API 20)  { date, terms:[{ economicTermId, term, description }] }
 * - 홈 화면([data-mb-home])에서는 "오늘 하루 보지 않기"를 누르지 않았으면 처음 열 때 자동으로 팝업을 띄운다.
 *   "오늘 하루 보지 않기"는 이 브라우저의 표시 설정이라 localStorage 에 오늘 날짜로 둔다 (서버 데이터가 아니다).
 * - 용어 글은 textContent 로만 넣는다 (HTML 로 해석하지 않는다).
 */
(function () {
  'use strict';
  if (window.__mbWords) return;
  window.__mbWords = true;

  var HIDE_KEY = 'mb-words-hide';
  var backdrop, list, loaded = false, opener;

  function today() { return new Date().toDateString(); }
  function hiddenToday() {
    try { return localStorage.getItem(HIDE_KEY) === today(); } catch (e) { return false; }
  }
  function hideToday() {
    try { localStorage.setItem(HIDE_KEY, today()); } catch (e) { /* 저장이 막힌 브라우저는 이번 화면에서만 닫는다 */ }
  }
  function el(tag, cls, text) {
    var node = document.createElement(tag);
    if (cls) node.className = cls;
    if (text != null) node.textContent = text;
    return node;
  }

  function build() {
    if (backdrop) return;
    backdrop = el('div', 'mb-dialog-backdrop');
    backdrop.hidden = true;
    var dialog = el('div', 'mb-dialog mb-words');
    dialog.setAttribute('role', 'dialog');
    dialog.setAttribute('aria-modal', 'true');
    dialog.setAttribute('aria-labelledby', 'mb-words-title');

    var head = el('div', 'mb-words__head');
    var title = el('span', 'tag tag-accent mb-words__title', '오늘의 경제단어');
    title.id = 'mb-words-title';
    var close = el('button', 'btn btn-ghost btn-icon');
    close.type = 'button';
    close.setAttribute('aria-label', '닫기');
    close.setAttribute('data-words-close', '');
    close.innerHTML = '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" aria-hidden="true"><path d="M18 6 6 18M6 6l12 12"/></svg>';
    head.appendChild(title);
    head.appendChild(el('span', 'mb-words__space'));
    head.appendChild(close);

    list = el('div', 'mb-words__list');
    list.appendChild(el('p', 'mb-words__empty', '불러오고 있어요…'));

    var actions = el('div', 'mb-words__actions');
    var hide = el('button', 'btn btn-ghost', '오늘 하루 보지 않기');
    hide.type = 'button';
    hide.setAttribute('data-words-hide', '');
    var ok = el('button', 'btn btn-primary', '확인');
    ok.type = 'button';
    ok.setAttribute('data-words-close', '');
    actions.appendChild(hide);
    actions.appendChild(ok);

    dialog.appendChild(head);
    dialog.appendChild(list);
    dialog.appendChild(actions);
    backdrop.appendChild(dialog);
    document.body.appendChild(backdrop);

    backdrop.addEventListener('click', function (event) {
      if (event.target === backdrop || event.target.closest('[data-words-close]')) close_();
      else if (event.target.closest('[data-words-hide]')) { hideToday(); close_(); }
    });
    document.addEventListener('keydown', function (event) {
      if (!backdrop || backdrop.hidden) return;
      if (event.key === 'Escape') { close_(); return; }
      if (event.key === 'Tab') trapFocus(event);
    });
  }

  /** 팝업이 열려 있는 동안 Tab 이 팝업 밖으로 나가지 않게 한다 (처음 항목에서 Shift+Tab 은 마지막으로, 마지막에서 Tab 은 처음으로) */
  function trapFocus(event) {
    var items = Array.prototype.filter.call(backdrop.querySelectorAll('button, [href], input, [tabindex]:not([tabindex="-1"])'),
      function (node) { return !node.disabled && node.offsetParent !== null; });
    if (!items.length) return;
    var first = items[0];
    var last = items[items.length - 1];
    var active = document.activeElement;
    if (!backdrop.contains(active)) {
      event.preventDefault();
      first.focus();
    } else if (event.shiftKey && active === first) {
      event.preventDefault();
      last.focus();
    } else if (!event.shiftKey && active === last) {
      event.preventDefault();
      first.focus();
    }
  }

  function render(terms) {
    list.textContent = '';
    if (!terms.length) {
      list.appendChild(el('p', 'mb-words__empty', '오늘의 경제단어가 아직 없어요.'));
      return;
    }
    terms.forEach(function (term) {
      var row = el('div', 'mb-words__row');
      row.appendChild(el('span', 'mb-words__term', term.term));
      row.appendChild(el('span', 'mb-words__desc', term.description));
      list.appendChild(row);
    });
  }

  function load() {
    if (loaded || !window.MB || !MB.api) return;
    loaded = true;
    MB.api('/api/v1/economic-terms/today').then(function (data) {
      render((data && data.terms) || []);
    }, function () {
      loaded = false;   // 다음에 열 때 다시 시도한다
      list.textContent = '';
      list.appendChild(el('p', 'mb-words__empty', '오늘의 경제단어를 불러오지 못했어요.'));
    });
  }

  function open_(trigger) {
    build();
    opener = trigger || null;
    backdrop.hidden = false;
    load();
    var ok = backdrop.querySelector('.btn-primary');
    if (ok) ok.focus();
  }

  function close_() {
    if (!backdrop) return;
    backdrop.hidden = true;
    if (opener && opener.focus) opener.focus();   // 열었던 버튼으로 포커스를 돌려준다
    opener = null;
  }

  document.addEventListener('click', function (event) {
    var button = event.target.closest('[data-mb-words]');
    if (button) open_(button);
  });

  function autoOpen() {
    // 홈 화면은 오늘 하루 보지 않기를 하지 않았으면 처음에 한 번 보여 준다
    if (document.querySelector('[data-mb-home]') && document.querySelector('[data-mb-words]') && !hiddenToday()) open_(null);
  }
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', autoOpen);
  else autoOpen();
})();
