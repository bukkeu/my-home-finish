/* comment.
*   담당 : SCM 서수영 (#333, 공통 오류 화면)
*   오류 화면의 "이전 페이지" 버튼 (EP-04)
*   - 이 탭에 뒤로 갈 기록이 있으면 뒤로 가고, 없으면 버튼의 data-home(역할별 첫 화면)으로 간다
*   - 뒤로 갈 기록 확인 (#334 리뷰)
*       Navigation API 를 지원하는 브라우저 : navigation.canGoBack 으로 미리 확인한다
*       지원하지 않는 브라우저 : 일단 뒤로 가 보고, 잠시 뒤에도 이 화면에 남아 있으면 홈으로 보낸다
*   - history.length 는 "앞으로 가기" 기록도 세고, document.referrer 는 새 탭으로 연 링크에도 남아서
*     둘로는 뒤로 갈 기록이 있는지 알 수 없다
*  */
(function () {
  'use strict';
  var BACK_WAIT_MS = 500;   // 뒤로 가기가 시작될 때까지 기다리는 시간
  var backButton = document.querySelector('[data-error-back]');
  if (!backButton) return;

  function goHome() {
    window.location.href = backButton.dataset.home || '/';
  }

  function supportsCanGoBack() {
    return !!window.navigation && typeof window.navigation.canGoBack === 'boolean';
  }

  // 뒤로 간 뒤 이 화면을 떠나면(pagehide) 홈 이동 예약을 지운다
  function goBackOrHome() {
    var timer = window.setTimeout(goHome, BACK_WAIT_MS);
    window.addEventListener('pagehide', function () {
      window.clearTimeout(timer);
    }, { once: true });
    window.history.back();
  }

  backButton.addEventListener('click', function () {
    if (!supportsCanGoBack()) {
      goBackOrHome();
      return;
    }
    if (window.navigation.canGoBack) {
      window.history.back();
      return;
    }
    goHome();
  });
})();
