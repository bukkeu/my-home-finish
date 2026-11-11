/* comment.
*   담당 : SCM 서수영 (#333, 공통 오류 화면)
*   오류 화면의 "이전 페이지" 버튼 (EP-04)
*   - 이 탭에 뒤로 갈 기록이 있으면 뒤로 가고, 없으면 버튼의 data-home(역할별 첫 화면)으로 간다
*   - 뒤로 갈 기록 확인 (#334 리뷰)
*       Navigation API 를 지원하는 브라우저 : navigation.canGoBack
*       지원하지 않는 브라우저 : 이 사이트의 다른 화면에서 왔는지(document.referrer)로 대신 판단한다
*   - history.length 는 "앞으로 가기" 기록도 세고, document.referrer 는 새 탭으로 연 링크에도 남아서
*     둘만으로는 뒤로 갈 기록이 있는지 알 수 없다
*  */
(function () {
  'use strict';
  var backButton = document.querySelector('[data-error-back]');
  if (!backButton) return;

  function canGoBack() {
    if (window.navigation && typeof window.navigation.canGoBack === 'boolean') {
      return window.navigation.canGoBack;
    }
    return document.referrer.indexOf(window.location.origin + '/') === 0;
  }

  backButton.addEventListener('click', function () {
    if (canGoBack()) {
      window.history.back();
      return;
    }
    window.location.href = backButton.dataset.home || '/';
  });
})();
