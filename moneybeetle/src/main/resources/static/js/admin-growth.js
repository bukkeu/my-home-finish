/* comment.
*   담당 : DBA ② 김예린 (#61, 성장·캐시 탭)
*   - 활동별 캐시·경험치 지급 규칙: GET /api/v1/admin/reward-rules (API 108), PUT /api/v1/admin/reward-rules/{ruleId} (API 109)
*     규칙마다 캐시·경험치·사용 여부를 따로 저장한다. 저장하면 서버가 작업 기록(관리자 작업 기록)을 남긴다.
*   - 캐시 수동 지급·회수: 회원 찾기 GET /api/v1/admin/members?keyword= (API 101), POST /api/v1/admin/members/{memberId}/cash-adjustments (API 110)
*     사유가 필수이고(200자 이하) 금액은 1 이상 10억 이하다. 회수로 0 미만이 되면 서버가 거절한다 (SP-17).
*  */
(function () {
  'use strict';
  var el = MBA.el, byId = MBA.byId;
  var MAX_AMOUNT = 1000000000;
  var MAX_REASON = 200;

  var adj = { member: null, type: 'GIVE' };

  /* ---------- 보상 규칙 ---------- */

  function ruleRow(rule) {
    var row = el('div', 'adm-row adm-row--wrap');
    var main = el('span', 'adm-row__main');
    main.appendChild(el('strong', null, rule.name));
    main.appendChild(el('span', 'adm-row__sub', rule.code));
    row.appendChild(main);

    var cash = el('input', 'adm-input adm-input--sm');
    cash.type = 'number'; cash.min = '0'; cash.step = '1'; cash.value = String(rule.cashAmount);
    cash.setAttribute('aria-label', rule.name + ' 캐시');
    var exp = el('input', 'adm-input adm-input--sm');
    exp.type = 'number'; exp.min = '0'; exp.step = '1'; exp.value = String(rule.expAmount);
    exp.setAttribute('aria-label', rule.name + ' 경험치');
    var activeWrap = el('label', 'adm-check');
    var active = el('input');
    active.type = 'checkbox'; active.checked = rule.isActive;
    activeWrap.appendChild(active);
    activeWrap.appendChild(document.createTextNode('사용'));
    var save = el('button', 'btn btn-secondary adm-btn-sm', '저장');
    save.type = 'button';
    save.setAttribute('aria-label', rule.name + ' 저장');

    var cols = el('span', 'adm-rule-cols');
    var cashLabel = el('label', 'adm-check', '캐시');
    cashLabel.appendChild(cash);
    var expLabel = el('label', 'adm-check', '경험치');
    expLabel.appendChild(exp);
    cols.appendChild(cashLabel);
    cols.appendChild(expLabel);
    cols.appendChild(activeWrap);
    cols.appendChild(save);
    row.appendChild(cols);

    save.addEventListener('click', function () {
      var cashValue = Number(cash.value);
      var expValue = Number(exp.value);
      if (!Number.isInteger(cashValue) || !Number.isInteger(expValue) || cashValue < 0 || expValue < 0) {
        MB.toast('캐시와 경험치는 0 이상의 정수로 입력해 주세요.', 'error');
        return;
      }
      save.disabled = true;
      MB.api('/api/v1/admin/reward-rules/' + rule.rewardRuleId, {
        method: 'PUT', body: { cashAmount: cashValue, expAmount: expValue, isActive: active.checked }
      }).then(function () {
        MB.toast(rule.name + ' 규칙을 저장했어요');
      }, function (err) {
        MB.toast(MBA.errorText(err), 'error');
      }).then(function () { save.disabled = false; });
    });
    return row;
  }

  function loadRules() {
    var box = byId('adm-rules');
    MB.api('/api/v1/admin/reward-rules').then(function (data) {
      MBA.clear(box);
      var rules = (data && data.rules) || [];
      if (!rules.length) { box.appendChild(el('p', 'adm-empty', '등록된 보상 규칙이 없어요')); return; }
      rules.forEach(function (rule) { box.appendChild(ruleRow(rule)); });
    }, function (err) {
      MBA.clear(box);
      box.appendChild(el('p', 'adm-empty', '보상 규칙을 불러오지 못했어요. ' + MBA.errorText(err)));
    });
  }

  /* ---------- 캐시 수동 지급·회수 ---------- */

  function renderPicked() {
    var box = byId('adm-picked');
    MBA.clear(box);
    if (!adj.member) { box.hidden = true; return; }
    box.hidden = false;
    box.appendChild(el('strong', null, adj.member.nickname));
    box.appendChild(el('span', 'adm-row__sub', adj.member.email + ' · 현재 잔액 ' + MBA.won(adj.member.cash) + ' 캐시'));
  }

  function renderResults(items) {
    var box = byId('adm-member-results');
    MBA.clear(box);
    if (!items.length) { box.appendChild(el('p', 'adm-empty', '찾은 회원이 없어요')); return; }
    items.forEach(function (member) {
      var button = el('button', 'adm-pick');
      button.type = 'button';
      button.setAttribute('aria-pressed', adj.member && adj.member.memberId === member.memberId ? 'true' : 'false');
      var main = el('span', 'adm-row__main');
      main.appendChild(el('strong', null, member.nickname));
      main.appendChild(el('span', 'adm-row__sub', member.email + ' · ' + MBA.won(member.cash) + ' 캐시'));
      button.appendChild(main);
      button.addEventListener('click', function () {
        adj.member = member;
        renderPicked();
        renderResults(items);
        byId('adm-adj-error').textContent = '';
      });
      box.appendChild(button);
    });
  }

  function searchMembers() {
    var keyword = byId('adm-member-keyword').value.trim();
    var box = byId('adm-member-results');
    if (!keyword) { MBA.clear(box); box.appendChild(el('p', 'adm-empty', '닉네임이나 이메일로 찾아 주세요')); return; }
    MB.api('/api/v1/admin/members?role=MEMBER&size=8&keyword=' + encodeURIComponent(keyword)).then(function (data) {
      renderResults((data && data.items) || []);
    }, function (err) {
      MBA.clear(box);
      box.appendChild(el('p', 'adm-empty', '회원을 찾지 못했어요. ' + MBA.errorText(err)));
    });
  }

  function setType(type) {
    adj.type = type;
    var buttons = byId('adm-adj-type').querySelectorAll('button');
    buttons.forEach(function (b) { b.setAttribute('aria-pressed', b.getAttribute('data-type') === type ? 'true' : 'false'); });
    byId('adm-adj-submit').textContent = type === 'GIVE' ? '지급하기' : '회수하기';
  }

  function submitAdjust() {
    var error = byId('adm-adj-error');
    var amount = Number(byId('adm-adj-amount').value);
    var reason = byId('adm-adj-reason').value.trim();
    if (!adj.member) { error.textContent = '회원을 먼저 골라 주세요.'; return; }
    if (!Number.isInteger(amount) || amount < 1 || amount > MAX_AMOUNT) { error.textContent = '금액은 1 이상 ' + MBA.won(MAX_AMOUNT) + ' 이하의 정수로 입력해 주세요.'; return; }
    if (!reason) { error.textContent = '사유를 입력해 주세요.'; return; }
    if (reason.length > MAX_REASON) { error.textContent = '사유는 ' + MAX_REASON + '자 이하로 입력해 주세요.'; return; }
    var verb = adj.type === 'GIVE' ? '지급' : '회수';
    MBA.confirm(adj.member.nickname + ' 님에게 ' + MBA.won(amount) + ' 캐시를 ' + verb + '할까요?', '사유: ' + reason + '\n작업 기록이 남아요.', verb + '하기').then(function (yes) {
      if (!yes) return null;
      var submit = byId('adm-adj-submit');
      submit.disabled = true;
      return MB.api('/api/v1/admin/members/' + adj.member.memberId + '/cash-adjustments', {
        method: 'POST', body: { type: adj.type, amount: amount, reason: reason }
      }).then(function (result) {
        adj.member.cash = result.balanceAfter;
        MB.toast(adj.member.nickname + ' 님 캐시를 ' + verb + '했어요 (잔액 ' + MBA.won(result.balanceAfter) + ')');
        byId('adm-adj-amount').value = '';
        byId('adm-adj-reason').value = '';
        error.textContent = '';
        renderPicked();
      }, function (err) {
        error.textContent = MBA.errorText(err);
      }).then(function () { submit.disabled = false; });
    });
  }

  byId('adm-member-search').addEventListener('click', searchMembers);
  byId('adm-member-keyword').addEventListener('keydown', function (event) {
    if (event.key === 'Enter') { event.preventDefault(); searchMembers(); }
  });
  byId('adm-adj-type').querySelectorAll('button').forEach(function (b) {
    b.addEventListener('click', function () { setType(b.getAttribute('data-type')); });
  });
  byId('adm-adj-submit').addEventListener('click', submitAdjust);

  setType('GIVE');
  renderPicked();
  loadRules();
})();
