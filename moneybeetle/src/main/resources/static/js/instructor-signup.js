/* comment.
*   담당 : DBA ① 신아림 (#59 강사 가입 /instructor/signup, #372 목업과 똑같이)
*   원본 : InstructorSignup.dc.html (막대형 4단계 · 큰 입력칸 · '다음' 버튼 · '이전 단계로')
*   단계: 1 이메일(+ 인증번호, API 5-1 코드 받기 → API 5 확인) → 2 비밀번호 → 3 약관 동의(API 1) → 4 강사 정보 → 신청(API 12) → 완료
*   - 목업에는 인증번호 칸이 없지만 본인인증이 필수라(DP-44) 1단계 '다음'을 처음 누르면 번호를 보내고 같은 모양의 칸을 연다
*   - '다음'은 그 단계 입력이 맞을 때만 눌린다 (목업처럼 흐리게)
*   - 서버가 돌려준 오류 코드로 그 입력이 있는 단계로 돌아가 안내한다
*   - 강사명은 회원 닉네임으로도 쓰인다(#30): 2~12자 한글·영문·숫자
*   - 글자는 모두 textContent 로 넣는다 (약관 본문 포함)
*  */
(function () {
  var box = document.querySelector('[data-ins-signup]');
  if (!box || !window.MB) return;
  var stepsEl = document.querySelector('[data-ins-steps]');
  var titleEl = document.querySelector('[data-ins-title]');

  var STEPS = ['이메일', '비밀번호', '약관 동의', '강사 정보'];
  var TITLES = ['강사 계정 만들기', '비밀번호 설정', '약관 동의', '강사 정보 입력'];
  var EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
  var PASSWORD = /^(?=.*[A-Za-z])(?=.*\d)\S{8,20}$/u;   // SignupRules.PASSWORD_PATTERN 과 같다. u: 이모지 같은 글자도 서버처럼 한 글자로 센다
  var NICKNAME = /^[가-힣A-Za-z0-9]{2,12}$/;
  var STEP_OF_CODE = {
    EMAIL_INVALID: 0, EMAIL_DUPLICATED: 0, IDENTITY_NOT_VERIFIED: 0,
    PASSWORD_INVALID: 1, PASSWORD_MISMATCH: 1,
    TERMS_NOT_FOUND: 2, REQUIRED_TERMS_NOT_AGREED: 2,
    NICKNAME_INVALID: 3, NICKNAME_DUPLICATED: 3, FORBIDDEN_WORD: 3
  };
  var STEP_OF_FIELD = { email: 0, password: 1, passwordConfirm: 1, passwordConfirmed: 1, agreedTermsIds: 2, displayName: 3, field: 3 };
  var EYE_OPEN = 'M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7S2 12 2 12z';
  var EYE_OFF = ['M10.7 5.1A10 10 0 0 1 12 5c6.5 0 10 7 10 7a17 17 0 0 1-3 3.9M6.6 6.6C3.9 8.4 2 12 2 12s3.5 7 10 7a9.7 9.7 0 0 0 5.4-1.6', 'M9.9 9.9a3 3 0 0 0 4.2 4.2M3 3l18 18'];

  var st = {
    step: 0, email: '', codeSent: false, verified: false, code: '', cooldown: 0,
    password: '', password2: '', show1: false, show2: false,
    terms: null, termsFailed: false, agreed: {}, opened: {},
    displayName: '', field: '', done: false, busy: false, error: ''
  };
  var timer = null;

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

  function svg(paths, circle) {
    var ns = 'http://www.w3.org/2000/svg';
    var el = document.createElementNS(ns, 'svg');
    [['width', '26'], ['height', '26'], ['viewBox', '0 0 24 24'], ['fill', 'none'], ['stroke', 'currentColor'], ['stroke-width', '1.5'],
      ['stroke-linecap', 'round'], ['stroke-linejoin', 'round'], ['aria-hidden', 'true']].forEach(function (a) { el.setAttribute(a[0], a[1]); });
    paths.forEach(function (d) { var p = document.createElementNS(ns, 'path'); p.setAttribute('d', d); el.appendChild(p); });
    if (circle) { var c = document.createElementNS(ns, 'circle'); c.setAttribute('cx', '12'); c.setAttribute('cy', '12'); c.setAttribute('r', '3'); el.appendChild(c); }
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

  function errorText(err, fallback) {
    // 입력값 오류(@Valid)면 error.fields 는 [{ field, message }] 목록이다 (GlobalExceptionHandler)
    if (err && Array.isArray(err.fields) && err.fields.length && err.fields[0].message) return err.fields[0].message;
    return (err && err.message) || fallback;
  }

  function check(on, cls) {
    return h('span', { class: 'ia-check' + (cls ? ' ' + cls : ''), 'aria-hidden': 'true', text: '✓' });
  }

  /* 단계마다 '다음'을 누를 수 있는지 (목업 ok 와 같다) */
  function canNext() {
    if (st.busy) return false;
    if (st.step === 0) return EMAIL.test(st.email) && (!st.codeSent || st.verified || /^\d{6}$/.test(st.code));
    if (st.step === 1) return PASSWORD.test(st.password) && st.password === st.password2;
    if (st.step === 2) return !!st.terms && st.terms.filter(function (t) { return t.isRequired; }).every(function (t) { return st.agreed[t.termsId]; });
    return NICKNAME.test(st.displayName) && !!st.field;   // 전문 분야도 필수
  }

  function refreshNext() {
    var btn = box.querySelector('[data-ins-next]');
    if (btn) btn.disabled = !canNext();
  }

  function go(step) {
    st.step = Math.max(0, Math.min(STEPS.length - 1, step));
    st.error = '';
    draw(true);
  }

  /* 다시 그리면 버튼이 새로 만들어지므로, 초점이 있던 요소(같은 id)로 초점을 되돌린다.
   * focus 가 true(단계 이동)이면 첫 입력칸, 입력칸이 없는 단계(약관)면 제목에 초점을 준다 */
  function draw(focus) {
    var focusedId = !focus && document.activeElement && box.contains(document.activeElement) ? document.activeElement.id : '';
    fill(stepsEl, STEPS.map(function (label, i) {
      var now = i === st.step && !st.done;
      return h('li', { class: 'ia-step' + (i < st.step || st.done ? ' is-done' : '') + (now ? ' is-now' : ''), 'aria-current': now ? 'step' : null, text: (i + 1) + ' ' + label });
    }));
    if (st.done) { titleEl.textContent = '가입 신청 완료'; fill(box, drawDone()); return; }
    titleEl.textContent = TITLES[st.step];
    var view = [drawEmail, drawPassword, drawTerms, drawProfile][st.step]();
    var next = h('button', { type: 'submit', class: 'ia-next', 'data-ins-next': '', text: nextLabel() });
    next.disabled = !canNext();
    fill(box, h('form', { novalidate: true, class: 'ia-signup-form', onsubmit: function (e) { e.preventDefault(); if (canNext()) onNext(); } }, [
      view,
      h('p', { class: 'ia-signup-error', role: 'alert', text: st.error }),
      next,
      st.step > 0 ? h('button', { type: 'button', class: 'ia-back', text: '이전 단계로', onclick: function () { go(st.step - 1); } }) : null
    ]));
    if (focus) {
      var first = box.querySelector('input:not([disabled])');
      if (first) first.focus();
      else { titleEl.tabIndex = -1; titleEl.focus(); }
    } else if (focusedId) {
      var again = document.getElementById(focusedId);
      if (again && !again.disabled) again.focus();
    }
  }

  function nextLabel() {
    if (st.step === 3) return st.busy ? '신청하는 중…' : '가입 신청';
    if (st.step === 0 && st.busy) return st.codeSent ? '확인하는 중…' : '보내는 중…';
    return '다음';
  }

  function onNext() {
    if (st.step === 0) {
      if (st.verified) { go(1); return; }
      if (!st.codeSent) { sendCode(); return; }
      verify();
      return;
    }
    if (st.step === 3) { submit(); return; }
    go(st.step + 1);
  }

  /* ---------- 1. 이메일 (+ 인증번호) ---------- */

  function drawEmail() {
    var email = h('input', { class: 'ia-big', type: 'email', id: 'ia-su-email', autocomplete: 'email', placeholder: 'teacher@moneybeetle.kr', value: st.email,
      disabled: st.verified || st.codeSent || st.busy, oninput: function () { st.email = email.value.trim(); st.error = ''; refreshNext(); } });
    var parts = [
      h('label', { class: 'ia-big-label', for: 'ia-su-email', text: '먼저 이메일 주소를 입력해 주세요' }),
      email,
      h('span', { class: 'ia-hint', text: '이 이메일이 아이디가 돼요' })
    ];
    if (st.verified) {
      parts.push(h('span', { class: 'ia-hint ia-hint--ok', text: '✓ 이메일 인증을 마쳤어요' }));
    } else if (st.codeSent) {
      // 입력한 인증번호는 st.code 에 남겨, 오류 안내 등으로 화면을 다시 그려도 지워지지 않게 한다
      var code = h('input', { class: 'ia-big ia-big--follow', id: 'ia-su-code', inputmode: 'numeric', maxlength: '6', placeholder: '6자리 인증번호', autocomplete: 'one-time-code',
        value: st.code, oninput: function () { st.code = code.value.replace(/\D/g, ''); st.error = ''; refreshNext(); } });
      parts.push(
        h('label', { class: 'ia-big-label ia-big-label--gap', for: 'ia-su-code', text: '메일로 받은 인증번호를 입력해 주세요' }),
        code,
        h('span', { class: 'ia-hint', text: '인증번호를 보냈어요. 5분 안에 입력해 주세요. 메일이 안 보이면 스팸함도 확인해 주세요' }),
        h('div', { class: 'ia-links' }, [
        h('button', { type: 'button', class: 'ia-resend', 'data-ins-send': '', text: sendLabel(), disabled: st.busy || st.cooldown > 0, onclick: function () { sendCode(); } }),
        h('button', { type: 'button', class: 'ia-resend', text: '이메일 다시 입력', disabled: st.busy, onclick: function () {
          st.codeSent = false; st.code = ''; st.error = ''; draw(true);
        } })
        ])
      );
    }
    return h('div', { class: 'ia-block' }, parts);
  }

  function sendLabel() {
    return st.cooldown > 0 ? '인증번호 다시 받기 (' + st.cooldown + '초)' : '인증번호 다시 받기';
  }

  /* 타이머는 '다시 받기' 버튼만 고친다. 화면 전체를 다시 그리면 입력 중인 인증번호와 포커스가 사라진다 */
  function updateSendButton() {
    var btn = box.querySelector('[data-ins-send]');
    if (!btn) return;
    btn.textContent = sendLabel();
    btn.disabled = st.busy || st.cooldown > 0;
  }

  function startCooldown(sec) {
    st.cooldown = sec;
    clearInterval(timer);
    timer = setInterval(function () {
      st.cooldown -= 1;
      if (st.cooldown <= 0) { st.cooldown = 0; clearInterval(timer); }
      if (st.step === 0 && !st.done) updateSendButton();
    }, 1000);
  }

  function sendCode() {
    if (!EMAIL.test(st.email)) { st.error = '이메일 주소를 확인해 주세요'; draw(); return; }
    st.busy = true; st.error = ''; draw();
    MB.api('/api/v1/auth/identity-verification/request', { method: 'POST', body: { email: st.email } }).then(function () {
      st.codeSent = true;
      st.code = '';   // 새 번호를 보냈으니 이전에 쓰던 번호는 지운다
      startCooldown(60);   // 같은 이메일은 60초에 한 번 (API 5-1). 보냈다는 안내는 인증번호 칸 아래 문구로 (알림이 아래 안내 띠를 가리지 않게)
    }).catch(function (err) {
      st.error = errorText(err, '인증번호를 보내지 못했어요.');
    }).then(function () {
      st.busy = false;
      draw();
      var code = box.querySelector('#ia-su-code');
      if (code) code.focus();
    });
  }

  function verify() {
    if (!/^\d{6}$/.test(st.code)) { st.error = '인증번호 6자리를 입력해 주세요.'; draw(); return; }
    st.busy = true; st.error = ''; draw();
    MB.api('/api/v1/auth/identity-verification', { method: 'POST', body: { email: st.email, code: st.code } }).then(function (res) {
      st.verified = !!(res && res.verified);
      if (!st.verified) st.error = '인증번호가 맞지 않아요.';
    }).catch(function (err) {
      st.error = errorText(err, '인증하지 못했어요.');
      if (err && err.code === 'IDENTITY_CODE_EXPIRED') { st.codeSent = false; st.code = ''; }   // 새 번호를 받아야 한다
    }).then(function () {
      st.busy = false;
      if (st.verified) { clearInterval(timer); go(1); } else draw();
    });
  }

  /* ---------- 2. 비밀번호 ---------- */

  function pwInput(key, showKey, id, placeholder, extraCls) {
    var input = h('input', { class: 'ia-big', id: id, type: st[showKey] ? 'text' : 'password', autocomplete: 'new-password', placeholder: placeholder, value: st[key], maxlength: '20' });
    var eye = h('button', { type: 'button', class: 'ia-eye', 'aria-label': st[showKey] ? '비밀번호 가리기' : '비밀번호 보기', 'aria-controls': id },
      [st[showKey] ? svg(EYE_OFF) : svg([EYE_OPEN], true)]);
    eye.addEventListener('click', function () {
      st[showKey] = !st[showKey];
      input.type = st[showKey] ? 'text' : 'password';
      eye.setAttribute('aria-label', st[showKey] ? '비밀번호 가리기' : '비밀번호 보기');
      fill(eye, st[showKey] ? svg(EYE_OFF) : svg([EYE_OPEN], true));
    });
    return { input: input, wrap: h('div', { class: 'ia-big-pw ' + extraCls }, [input, eye]) };
  }

  function drawPassword() {
    var p1 = pwInput('password', 'show1', 'ia-su-pw', '8자 이상, 영문+숫자', 'ia-big-pw--first');
    var p2 = pwInput('password2', 'show2', 'ia-su-pw2', '비밀번호 확인', 'ia-big-pw--second');
    var rule = h('span', { class: 'ia-hint', id: 'ia-su-rule', text: '8자 이상, 영문과 숫자를 함께 써 주세요' });
    var mismatch = h('span', { class: 'ia-hint ia-hint--bad', 'aria-live': 'polite' });
    p1.input.setAttribute('aria-describedby', 'ia-su-rule');
    function sync() {
      st.password = p1.input.value; st.password2 = p2.input.value; st.error = '';
      var ok = PASSWORD.test(st.password);
      rule.classList.toggle('ia-hint--bad', !!st.password && !ok);
      mismatch.textContent = ok && st.password2 && st.password !== st.password2 ? '비밀번호가 일치하지 않아요' : '';
      mismatch.hidden = !mismatch.textContent;
      refreshNext();
    }
    p1.input.addEventListener('input', sync);
    p2.input.addEventListener('input', sync);
    var block = h('div', { class: 'ia-block' }, [
      h('label', { class: 'ia-big-label', for: 'ia-su-pw', text: '비밀번호를 만들어 주세요' }),
      p1.wrap, rule, p2.wrap, mismatch
    ]);
    p2.input.setAttribute('aria-label', '비밀번호 확인');
    rule.classList.toggle('ia-hint--bad', !!st.password && !PASSWORD.test(st.password));
    mismatch.hidden = true;
    return block;
  }

  /* ---------- 3. 약관 동의 ---------- */

  function loadTerms() {
    if (st.terms) return Promise.resolve(st.terms);
    return MB.api('/api/v1/agreements').then(function (res) {
      st.terms = (res && res.agreements) || [];
      return st.terms;
    });
  }

  function drawTerms() {
    if (!st.terms) {
      // 불러오지 못했으면 빈 목록으로 넘어가지 않고, 다시 시도할 수 있게 한다 (오류 문구는 아래에 보인다)
      if (st.termsFailed) {
        return h('div', { class: 'ia-block ia-block--terms' }, [
          h('button', { type: 'button', class: 'ia-all', text: '약관 다시 불러오기', onclick: function () { st.termsFailed = false; st.error = ''; draw(); } })
        ]);
      }
      loadTerms().then(function () { draw(); }).catch(function (err) {
        st.termsFailed = true;
        st.error = errorText(err, '약관을 불러오지 못했어요.');
        draw();
      });
      return h('div', { class: 'ia-block ia-block--terms' }, [h('span', { class: 'ia-hint', text: '약관을 불러오는 중이에요…' })]);
    }
    var all = st.terms.length > 0 && st.terms.every(function (t) { return st.agreed[t.termsId]; });
    var rows = [];
    st.terms.forEach(function (t) {
      var on = !!st.agreed[t.termsId];
      var bodyId = 'ia-term-' + t.termsId;
      rows.push(h('div', { class: 'ia-term' }, [
        h('button', { type: 'button', class: 'ia-term__btn', id: 'ia-term-agree-' + t.termsId, 'aria-pressed': String(on), onclick: function () { st.agreed[t.termsId] = !on; st.error = ''; draw(); } },
          [check(on), h('span', { class: 'ia-term__name', text: t.title })]),
        t.content ? h('button', { type: 'button', class: 'ia-term__view', id: 'ia-term-view-' + t.termsId, 'aria-expanded': String(!!st.opened[t.termsId]), 'aria-controls': bodyId,
          text: st.opened[t.termsId] ? '접기' : '보기', onclick: function () { st.opened[t.termsId] = !st.opened[t.termsId]; draw(); } }) : null,
        h('span', { class: 'ia-term__tag' + (t.isRequired ? ' ia-term__tag--req' : ''), text: t.isRequired ? '필수' : '선택' })
      ]));
      if (st.opened[t.termsId]) rows.push(h('p', { class: 'ia-term__body', id: bodyId, text: t.content || '' }));
    });
    return h('div', { class: 'ia-block ia-block--terms' }, [
      h('button', { type: 'button', class: 'ia-all', id: 'ia-term-all', 'aria-pressed': String(all), onclick: function () {
        st.terms.forEach(function (t) { st.agreed[t.termsId] = !all; }); st.error = ''; draw();
      } }, [check(all), '전체 동의'])
    ].concat(rows));
  }

  /* ---------- 4. 강사 정보 → 신청 ---------- */

  function drawProfile() {
    var name = h('input', { class: 'ia-big', id: 'ia-su-name', maxlength: '12', placeholder: '강의에 표시될 이름', value: st.displayName,
      oninput: function () { st.displayName = name.value.trim(); st.error = ''; refreshNext(); } });
    var fieldInput = h('input', { class: 'ia-big', id: 'ia-su-field', maxlength: '100', placeholder: '예: 청약, ETF, 주식', value: st.field,
      oninput: function () { st.field = fieldInput.value.trim(); st.error = ''; refreshNext(); } });
    return h('div', { class: 'ia-block ia-block--gap' }, [
      h('label', { class: 'ia-big-label', for: 'ia-su-name', text: '강사명' }),
      name,
      h('label', { class: 'ia-big-label ia-big-label--gap', for: 'ia-su-field', text: '전문 분야' }),
      fieldInput,
      h('span', { class: 'ia-hint', text: '가입 후 관리자가 강사 권한을 승인하면 강의를 등록할 수 있어요' })
    ]);
  }

  function submit() {
    if (!NICKNAME.test(st.displayName)) { st.error = '강사명은 2~12자의 한글·영문·숫자만 쓸 수 있어요.'; draw(); return; }
    if (!st.field) { st.error = '전문 분야를 입력해 주세요.'; draw(); return; }
    st.busy = true; st.error = ''; draw();
    MB.api('/api/v1/auth/instructor-signup', { method: 'POST', body: {
      email: st.email, password: st.password, passwordConfirm: st.password2, displayName: st.displayName, field: st.field,
      agreedTermsIds: Object.keys(st.agreed).filter(function (k) { return st.agreed[k]; }).map(Number)
    } }).then(function () {
      st.done = true;
      clearInterval(timer);
    }).catch(function (err) {
      var step = STEP_OF_CODE[err && err.code];
      if (step === undefined && err && Array.isArray(err.fields) && err.fields.length) step = STEP_OF_FIELD[err.fields[0].field];
      if (err && err.code === 'IDENTITY_NOT_VERIFIED') { st.verified = false; st.codeSent = false; st.code = ''; }   // 세션이 바뀌었으면 다시 인증
      if (step !== undefined) st.step = step;
      st.error = errorText(err, '가입 신청을 하지 못했어요. 다시 시도해 주세요.');
    }).then(function () { st.busy = false; draw(); });
  }

  function drawDone() {
    return h('div', { class: 'ia-done' }, [
      h('span', { class: 'ia-done__mark', 'aria-hidden': 'true', text: '✓' }),
      h('p', { class: 'ia-done__title', text: st.displayName + ' 강사님, 가입 신청을 받았어요' }),
      h('p', { class: 'ia-done__text', text: '관리자가 확인하고 강사 권한을 승인하면 강사 센터에 로그인할 수 있어요. 승인 전에는 강사 화면에 들어올 수 없어요.' }),
      h('a', { class: 'ia-next', href: '/instructor/login', text: '강사 로그인으로' })
    ]);
  }

  draw();
})();
