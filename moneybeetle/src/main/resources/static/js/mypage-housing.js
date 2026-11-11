/* comment.
*  담당 : SCM 서수영 (#70, 마이페이지 나의 청약통장 탭)
*  templates/fragments/mypage/housing.html 의 동작. 모의 청약 화면(#58, housing.js)과 같은 통장 API 를 쓴다
*  - 통장 현황 : GET /api/v1/housing/account (65)
*  - 납입 : POST /api/v1/housing/account/deposits (66, Idempotency-Key) — 확인창에서 "납입하기"를 눌러야 보낸다
*  - 납입 내역 : GET /api/v1/housing/account/deposits (67, 최신순)
*  - 틀(mypage.js)이 탭을 열 때 보내는 'mypage:tab' 이벤트에서 tab 이 housing 이면 불러온다 (열 때마다 새 값)
*  - 레벨 조건 없음 (Lv.12 미만도 사용)
*  - 납입 결과 알림은 공통 MB.toast 를 쓴다 (시안의 아래 가운데 짙은 알림 대신, 팀 결정 2026-11-11)
*  */
(function () {
  'use strict';

  var root = document.getElementById('mh');
  if (!root || !window.MB) return;

  var API = '/api/v1/housing';
  var EMPTY_TEXT = '아직 납입 내역이 없어요.';
  var account = null;                                   // 마지막으로 불러온 통장 (API 65)
  var policy = { minAmount: 20000, maxAmount: 500000, dailyRecognizedLimit: 250000 };
  var amountInput = q('amount');
  var submitBtn = q('submit');
  var dialog = document.getElementById('mh-confirm');

  // ---------- 도우미 ----------
  function q(name) {
    return root.querySelector('[data-h="' + name + '"]') || document.querySelector('#mh-confirm [data-h="' + name + '"]');
  }
  function setText(name, text) {
    var n = q(name);
    if (n) n.textContent = text;
    return n;
  }
  function fmt(n) {
    return Number(n || 0).toLocaleString('ko-KR');
  }
  function el(tag, cls, text) {
    var n = document.createElement(tag);
    if (cls) n.className = cls;
    if (text != null) n.textContent = text;
    return n;
  }
  // 시안 날짜 "2026.11.02(월)". 서버 날짜 'yyyy-MM-dd' 를 그대로 나눠 쓴다 (브라우저 시간대와 상관없게)
  function fmtDate(text) {
    if (!text) return '-';
    var p = String(text).split('-');
    var day = new Date(Number(p[0]), Number(p[1]) - 1, Number(p[2])).getDay();
    return p[0] + '.' + p[1] + '.' + p[2] + '(' + '일월화수목금토'.charAt(day) + ')';
  }
  function newKey() {                                   // 중복 요청 방지 키 (DP-37)
    if (window.crypto && crypto.randomUUID) return crypto.randomUUID();
    return 'mh-' + Date.now() + '-' + Math.random().toString(16).slice(2);
  }
  function amount() {
    return parseInt(String(amountInput.value).replace(/\D/g, ''), 10) || 0;
  }

  // ---------- 통장 현황 (API 65) ----------
  function loadAccount() {
    return MB.api(API + '/account').then(function (acc) {
      account = acc;
      if (acc.depositPolicy) policy = acc.depositPolicy;
      setText('balance', fmt(acc.balance));
      // 시안의 "누적 납입금액" : 통장에서 돈이 빠져나가는 기능이 없어 납입한 금액이 모두 잔액에 남는다 → 잔액과 같은 값
      setText('total', fmt(acc.balance));
      setText('recognized', fmt(acc.recognizedAmount));
      setText('count', acc.paidCount + '회차');
      setText('lastDate', fmtDate(acc.lastPaidDate));
      var today = setText('today', acc.paidToday ? '납입 완료' : '미납입');
      today.classList.toggle('is-unpaid', !acc.paidToday);
      setText('cash', fmt(acc.cash));
      setText('minText', fmt(policy.minAmount));
      setText('maxText', fmt(policy.maxAmount));
      setText('limitText', fmt(policy.dailyRecognizedLimit));
      setText('limitHint', fmt(policy.dailyRecognizedLimit));
      showCash(acc.cash);
      preview();
    }).catch(function (err) {
      MB.toast(err.message, 'error');
    });
  }

  // 보유 캐시가 바뀌면 같은 화면의 헤더 캐시·프로필 카드 캐시도 같은 값으로 맞춘다
  function showCash(cash) {
    var nodes = document.querySelectorAll('.mb-header .mb-cash span, #mp [data-p="cash"]');
    nodes.forEach(function (n) { n.textContent = fmt(cash); });
  }

  // ---------- 납입 내역 (API 67) ----------
  function loadDeposits() {
    var box = q('hist');
    return MB.api(API + '/account/deposits?page=0&size=20').then(function (page) {
      var items = (page && page.items) || [];
      box.querySelectorAll('.mh-row').forEach(function (n) { n.remove(); });
      var empty = q('empty');
      empty.textContent = EMPTY_TEXT;                   // 앞서 실패했을 때의 오류 문구를 지운다
      empty.hidden = items.length > 0;
      items.forEach(function (d) {
        box.appendChild(el('span', 'mh-row', fmtDate(d.paidDate) + ' · ' + d.paidCountAfter + '회차'));
        box.appendChild(el('span', 'mh-row is-num', fmt(d.amount)));
        box.appendChild(el('span', 'mh-row is-num', fmt(d.recognizedAmount)));
      });
    }).catch(function (err) {
      // 실패하면 예전 내역을 지우고 오류 문구만 보인다 (모의 청약 화면 housing.js 의 납입 내역과 같은 방식)
      box.querySelectorAll('.mh-row').forEach(function (n) { n.remove(); });
      var empty = q('empty');
      empty.textContent = err.message;
      empty.hidden = false;
    });
  }

  // ---------- 금액 입력 · 예상 결과 ----------
  // 입력값과 불러온 통장만으로 알 수 있는 문제는 보내기 전에 안내한다 (서버도 같은 규칙으로 다시 확인한다, SP-70)
  function problemOf(amt) {
    if (!account) return '';
    if (account.paidToday) return '오늘은 이미 납입했어요. 내일 다시 납입할 수 있어요.';
    if (amt && amt < policy.minAmount) return '최소 ' + fmt(policy.minAmount) + ' 캐시부터 납입할 수 있어요.';
    if (amt > policy.maxAmount) return '한 번에 최대 ' + fmt(policy.maxAmount) + ' 캐시까지 납입할 수 있어요.';
    if (amt > account.cash) return '보유 캐시가 부족해요. (보유 ' + fmt(account.cash) + ' 캐시)';
    return '';
  }

  function calcRows(amt) {
    var balance = account ? account.balance : 0;
    return [
      ['납입 예정금액', fmt(amt) + ' 캐시', false],
      ['실제 통장 적립금액', fmt(amt) + ' 캐시', false],
      ['이번 납입 인정금액', fmt(Math.min(amt, policy.dailyRecognizedLimit)) + ' 캐시', true],
      ['납입 후 예상 잔액', fmt(balance + amt) + ' 캐시', false]
    ];
  }

  function drawCalc(box, amt) {
    box.replaceChildren();
    calcRows(amt).forEach(function (r) {
      var row = el('div', 'mh-calc__row');
      row.appendChild(el('span', null, r[0]));
      row.appendChild(el('b', r[2] ? 'is-recognized' : null, r[1]));
      box.appendChild(row);
    });
  }

  function preview() {
    var amt = amount();
    var problem = problemOf(amt);
    var error = q('error');
    error.textContent = problem;
    error.hidden = !problem;
    amountInput.closest('.mh-input').classList.toggle('is-error', !!problem);
    drawCalc(q('calc'), amt);
    submitBtn.disabled = sending || !account || !!problem || !amt;   // 보내는 중에는 금액을 고쳐도 다시 누를 수 없다
  }

  // 결과를 모르는 납입(네트워크 끊김 등)을 다시 보낼 때는 같은 Idempotency-Key 를 쓴다 (DP-37, housing.js 와 같은 방식)
  // 금액을 고치면 새 납입이므로 키를 버린다. 성공했거나 결과가 분명한 실패면 다음 납입은 새 키로 보낸다
  var depositKey = null;
  var sending = false;                                  // 납입 요청을 보내는 중인지
  var UNKNOWN_RESULT = ['NETWORK_ERROR', 'ERROR', 'DUPLICATE_REQUEST'];

  // 시안처럼 입력하는 동안 천 단위 쉼표를 붙인다
  amountInput.addEventListener('input', function () {
    var digits = amountInput.value.replace(/\D/g, '');
    amountInput.value = digits ? fmt(parseInt(digits, 10)) : '';
    depositKey = null;
    preview();
  });
  root.querySelectorAll('.mh-quick [data-amount]').forEach(function (b) {
    b.addEventListener('click', function () {
      var text = fmt(b.dataset.amount);
      if (amountInput.value !== text) depositKey = null;   // 금액이 실제로 바뀔 때만 새 납입
      amountInput.value = text;
      preview();
    });
  });

  // ---------- 확인창 → 납입 (API 66) ----------
  submitBtn.addEventListener('click', function () {
    var amt = amount();
    if (!amt || problemOf(amt)) return;
    setText('confirmAmount', fmt(amt));
    setText('nextCount', String(account.paidCount + 1));
    drawCalc(q('confirmCalc'), amt);
    dialog.showModal();
  });
  // 확인창 바깥(배경)을 누르면 닫는다 (시안의 배경 클릭 닫기)
  dialog.addEventListener('click', function (e) {
    if (e.target === dialog) dialog.close('cancel');
  });
  dialog.addEventListener('close', function () {
    if (dialog.returnValue === 'ok') deposit(amount());
    dialog.returnValue = '';
  });

  function deposit(amt) {
    sending = true;
    submitBtn.disabled = true;
    if (!depositKey) depositKey = newKey();
    MB.api(API + '/account/deposits', {
      method: 'POST',
      headers: { 'Idempotency-Key': depositKey },
      body: { amount: amt }
    }).then(function (r) {
      depositKey = null;
      amountInput.value = '';
      var extra = r.recognizedAmount < r.amount ? ' (인정 ' + fmt(r.recognizedAmount) + ' 캐시)' : '';
      MB.toast(fmt(r.amount) + ' 캐시를 납입했어요 · ' + r.paidCount + '회차 인정' + extra);
      return Promise.all([loadAccount(), loadDeposits()]);
    }).catch(function (err) {
      if (UNKNOWN_RESULT.indexOf(err.code) < 0) depositKey = null;   // 결과가 분명한 실패만 키를 버린다
      MB.toast(err.message, 'error');                                // 하루 1회 초과(이미 납입) 등 서버 안내 그대로
      return loadAccount();                                          // 다른 탭·화면에서 납입했을 수도 있어 다시 불러온다
    }).finally(function () {
      sending = false;
      preview();                                                     // 다시 불러오기마저 실패해도 버튼을 지금 상태로 되살린다 (같은 키로 재시도 가능)
    });
  }

  // ---------- 시작 : 탭이 열릴 때마다 새 값 ----------
  document.addEventListener('mypage:tab', function (e) {
    if (e.detail && e.detail.tab === 'housing') {
      loadAccount();
      loadDeposits();
    }
  });
  if (window.MyPage && window.MyPage.current === 'housing') {   // 이벤트보다 늦게 준비된 경우
    loadAccount();
    loadDeposits();
  }
})();
