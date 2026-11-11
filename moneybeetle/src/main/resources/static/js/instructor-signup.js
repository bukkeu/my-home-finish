/* comment.
*   담당 : DBA ① 신아림 (#59, 강사 가입 /instructor/signup)
*   단계: 1 이메일 인증(API 5-1 코드 받기 → API 5 확인) → 2 비밀번호 → 3 약관 동의(API 1) → 4 강사 정보 → 신청(API 12) → 완료
*   - 서버가 돌려준 오류 코드로 그 입력이 있는 단계로 돌아가 안내한다
*   - 강사명은 회원 닉네임으로도 쓰인다(#30): 2~12자 한글·영문·숫자
*   - 글자는 모두 textContent 로 넣는다 (약관 본문 포함)
*  */
(function () {
  var box = document.querySelector('[data-ins-signup]');
  if (!box || !window.MB) return;
  var stepsEl = document.querySelector('[data-ins-steps]');
  var titleEl = document.querySelector('[data-ins-title]');

  var STEPS = ['이메일 인증', '비밀번호', '약관 동의', '강사 정보'];
  var PASSWORD = /^(?=.*[A-Za-z])(?=.*\d)\S{8,20}$/u;   // SignupRules.PASSWORD_PATTERN 과 같다. u: 이모지 같은 글자도 서버처럼 한 글자로 센다
  var NICKNAME = /^[가-힣A-Za-z0-9]{2,12}$/;
  var STEP_OF_CODE = {
    EMAIL_INVALID: 0, EMAIL_DUPLICATED: 0, IDENTITY_NOT_VERIFIED: 0,
    PASSWORD_INVALID: 1, PASSWORD_MISMATCH: 1,
    TERMS_NOT_FOUND: 2, REQUIRED_TERMS_NOT_AGREED: 2,
    NICKNAME_INVALID: 3, NICKNAME_DUPLICATED: 3, FORBIDDEN_WORD: 3
  };
  var STEP_OF_FIELD = { email: 0, password: 1, passwordConfirm: 1, passwordConfirmed: 1, agreedTermsIds: 2, displayName: 3, field: 3 };

  var st = {
    step: 0, email: '', codeSent: false, verified: false, password: '', password2: '',
    terms: null, agreed: {}, displayName: '', field: '', done: false, busy: false, error: '', cooldown: 0
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

  function field(label, input, hint) {
    return h('label', { class: 'ins-field' }, [h('span', { text: label }), input, hint ? h('small', { class: 'ins-hint', text: hint }) : null]);
  }

  function actions(back, nextLabel, onNext, disabled) {
    return h('div', { class: 'ins-actions' }, [
      back ? h('button', { type: 'button', class: 'btn btn-secondary', text: '이전', onclick: function () { go(st.step - 1); } }) : null,
      h('button', { type: 'submit', class: 'btn btn-primary ins-submit', text: nextLabel, disabled: disabled || st.busy, onclick: onNext })
    ]);
  }

  function go(step) {
    st.step = Math.max(0, Math.min(STEPS.length - 1, step));
    st.error = '';
    draw();
  }

  function draw() {
    fill(stepsEl, STEPS.map(function (label, i) {
      return h('li', { class: 'ins-step' + (i < st.step || st.done ? ' is-done' : '') + (i === st.step && !st.done ? ' is-now' : ''),
        'aria-current': i === st.step && !st.done ? 'step' : null }, [h('span', { class: 'ins-step__no', text: String(i + 1) }), label]);
    }));
    if (st.done) { titleEl.textContent = '가입 신청 완료'; fill(box, drawDone()); return; }
    titleEl.textContent = STEPS[st.step];
    var view = [drawEmail, drawPassword, drawTerms, drawProfile][st.step]();
    fill(box, h('form', { class: 'ins-form', novalidate: true, onsubmit: function (e) { e.preventDefault(); } }, [
      view, h('p', { class: 'ins-error', role: 'alert', text: st.error })
    ]));
    var first = box.querySelector('input:not([disabled])');
    if (first && document.activeElement === document.body) first.focus();
  }

  /* ---------- 1. 이메일 인증 ---------- */

  function drawEmail() {
    var email = h('input', { class: 'ins-input', type: 'email', autocomplete: 'email', placeholder: 'teacher@moneybeetle.kr', value: st.email,
      disabled: st.verified, oninput: function () { st.email = email.value.trim(); } });
    var code = h('input', { class: 'ins-input', inputmode: 'numeric', maxlength: '6', placeholder: '6자리 인증번호', autocomplete: 'one-time-code' });
    var sendLabel = st.cooldown > 0 ? '다시 받기 (' + st.cooldown + '초)' : st.codeSent ? '인증번호 다시 받기' : '인증번호 받기';

    return h('div', { class: 'ins-stack' }, [
      field('먼저 이메일 주소를 입력해 주세요', email, '이 이메일이 강사 계정 아이디가 돼요'),
      st.verified ? h('p', { class: 'ins-ok', text: '✓ 이메일 인증을 마쳤어요' }) : h('button', { type: 'button', class: 'btn btn-secondary',
        text: sendLabel, disabled: st.busy || st.cooldown > 0, onclick: function () { sendCode(); } }),
      st.codeSent && !st.verified ? h('div', { class: 'ins-code' }, [
        field('메일로 받은 인증번호', code, '5분 안에 입력해 주세요. 메일이 안 보이면 스팸함도 확인해 주세요'),
        h('button', { type: 'button', class: 'btn btn-primary', text: '확인', disabled: st.busy, onclick: function () { verify(code.value.trim()); } })
      ]) : null,
      st.verified ? actions(false, '다음', function () { go(1); }) : null
    ]);
  }

  function startCooldown(sec) {
    st.cooldown = sec;
    clearInterval(timer);
    timer = setInterval(function () {
      st.cooldown -= 1;
      if (st.cooldown <= 0) { st.cooldown = 0; clearInterval(timer); }
      if (st.step === 0 && !st.done) draw();
    }, 1000);
  }

  function sendCode() {
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(st.email)) { st.error = '이메일 형식이 올바르지 않아요.'; draw(); return; }
    st.busy = true; st.error = ''; draw();
    MB.api('/api/v1/auth/identity-verification/request', { method: 'POST', body: { email: st.email } }).then(function () {
      st.codeSent = true;
      startCooldown(60);   // 같은 이메일은 60초에 한 번 (API 5-1)
      MB.toast('인증번호를 보냈어요. 메일을 확인해 주세요.');
    }).catch(function (err) {
      st.error = errorText(err, '인증번호를 보내지 못했어요.');
    }).then(function () { st.busy = false; draw(); });
  }

  function verify(code) {
    if (!/^\d{6}$/.test(code)) { st.error = '인증번호 6자리를 입력해 주세요.'; draw(); return; }
    st.busy = true; st.error = ''; draw();
    MB.api('/api/v1/auth/identity-verification', { method: 'POST', body: { email: st.email, code: code } }).then(function (res) {
      st.verified = !!(res && res.verified);
      if (!st.verified) st.error = '인증번호가 맞지 않아요.';
    }).catch(function (err) {
      st.error = errorText(err, '인증하지 못했어요.');
      if (err && err.code === 'IDENTITY_CODE_EXPIRED') st.codeSent = false;   // 새 번호를 받아야 한다
    }).then(function () { st.busy = false; draw(); });
  }

  /* ---------- 2. 비밀번호 ---------- */

  function drawPassword() {
    var p1 = h('input', { class: 'ins-input', type: 'password', autocomplete: 'new-password', placeholder: '8~20자, 영문+숫자', value: st.password });
    var p2 = h('input', { class: 'ins-input', type: 'password', autocomplete: 'new-password', placeholder: '비밀번호 확인', value: st.password2 });
    var mismatch = h('small', { class: 'ins-hint ins-hint--warn', 'aria-live': 'polite' });
    function check() {
      st.password = p1.value; st.password2 = p2.value;
      mismatch.textContent = p2.value && p1.value !== p2.value ? '비밀번호가 일치하지 않아요' : '';
    }
    p1.addEventListener('input', check);
    p2.addEventListener('input', check);
    return h('div', { class: 'ins-stack' }, [
      field('비밀번호를 만들어 주세요', p1, '8~20자, 영문과 숫자를 함께 써 주세요'),
      field('비밀번호 확인', p2), mismatch,
      actions(true, '다음', function () {
        check();
        if (!PASSWORD.test(st.password)) { st.error = '비밀번호는 8~20자의 영문과 숫자를 모두 포함해야 해요.'; draw(); return; }
        if (st.password !== st.password2) { st.error = '비밀번호가 서로 달라요.'; draw(); return; }
        go(2);
      })
    ]);
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
      // 불러오지 못했으면 빈 목록으로 넘어가지 않고, 다시 시도할 수 있게 한다 (오류 문구는 아래 ins-error 에 보인다)
      if (st.termsFailed) {
        return h('div', { class: 'ins-state' }, [
          h('button', { type: 'button', class: 'btn btn-secondary', text: '약관 다시 불러오기',
            onclick: function () { st.termsFailed = false; st.error = ''; draw(); } }),
          h('br'),
          h('button', { type: 'button', class: 'btn btn-ghost', text: '이전', onclick: function () { go(st.step - 1); } })
        ]);
      }
      loadTerms().then(draw).catch(function (err) {
        st.termsFailed = true;
        st.error = errorText(err, '약관을 불러오지 못했어요.');
        draw();
      });
      return h('div', { class: 'ins-state', text: '약관을 불러오는 중이에요…' });
    }
    var all = st.terms.length > 0 && st.terms.every(function (t) { return st.agreed[t.termsId]; });
    var requiredOk = st.terms.filter(function (t) { return t.isRequired; }).every(function (t) { return st.agreed[t.termsId]; });
    return h('div', { class: 'ins-stack' }, [
      h('button', { type: 'button', class: 'ins-check ins-check--all', 'aria-pressed': String(all), onclick: function () {
        st.terms.forEach(function (t) { st.agreed[t.termsId] = !all; }); draw();
      } }, [h('span', { class: 'ins-check__mark', 'aria-hidden': 'true', text: all ? '✓' : '' }), '전체 동의']),
      h('div', { class: 'ins-terms' }, st.terms.map(function (t) {
        var on = !!st.agreed[t.termsId];
        return h('details', { class: 'ins-term' }, [
          h('summary', {}, [
            h('button', { type: 'button', class: 'ins-check', 'aria-pressed': String(on), onclick: function (e) {
              e.preventDefault(); st.agreed[t.termsId] = !on; draw();
            } }, [h('span', { class: 'ins-check__mark', 'aria-hidden': 'true', text: on ? '✓' : '' }), t.title]),
            h('span', { class: 'ins-tag' + (t.isRequired ? ' ins-tag--req' : ''), text: t.isRequired ? '필수' : '선택' })
          ]),
          h('p', { class: 'ins-term__body', text: t.content || '' })
        ]);
      })),
      actions(true, '다음', function () {
        if (!requiredOk) { st.error = '필수 약관에 모두 동의해 주세요.'; draw(); return; }
        go(3);
      })
    ]);
  }

  /* ---------- 4. 강사 정보 → 신청 ---------- */

  function drawProfile() {
    var name = h('input', { class: 'ins-input', maxlength: '12', placeholder: '강의에 표시될 이름', value: st.displayName,
      oninput: function () { st.displayName = name.value.trim(); } });
    var fieldInput = h('input', { class: 'ins-input', maxlength: '100', placeholder: '예: 청약, ETF, 주식', value: st.field,
      oninput: function () { st.field = fieldInput.value.trim(); } });
    return h('div', { class: 'ins-stack' }, [
      field('강사명', name, '2~12자 한글·영문·숫자. 회원 닉네임으로도 쓰여서 이미 있는 이름은 쓸 수 없어요'),
      field('전문 분야', fieldInput),
      h('p', { class: 'ins-info', text: '가입 후 관리자가 강사 권한을 승인하면 강사 센터에 로그인해 강의를 등록할 수 있어요.' }),
      actions(true, st.busy ? '신청하는 중…' : '가입 신청하기', submit)
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
      if (err && err.code === 'IDENTITY_NOT_VERIFIED') { st.verified = false; st.codeSent = false; }   // 세션이 바뀌었으면 다시 인증
      if (step !== undefined) st.step = step;
      st.error = errorText(err, '가입 신청을 하지 못했어요. 다시 시도해 주세요.');
    }).then(function () { st.busy = false; draw(); });
  }

  function drawDone() {
    return h('div', { class: 'ins-done' }, [
      h('span', { class: 'ins-done__mark', 'aria-hidden': 'true', text: '✓' }),
      h('p', { class: 'ins-done__title', text: st.displayName + ' 강사님, 가입 신청을 받았어요' }),
      h('p', { class: 'ins-info', text: '관리자가 확인하고 강사 권한을 승인하면 강사 센터에 로그인할 수 있어요. 승인 전에는 강사 화면에 들어올 수 없어요.' }),
      h('a', { class: 'btn btn-primary ins-submit', href: '/instructor/login', text: '강사 로그인으로' })
    ]);
  }

  draw();
})();
