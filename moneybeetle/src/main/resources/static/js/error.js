/* comment.
*   담당 : SCM 서수영 (#333, 공통 오류 화면)
*   오류 화면의 "이전 페이지" 버튼 (EP-04)
*   - 이 탭에 뒤로 갈 기록이 있으면 뒤로 가고, 없으면 버튼의 data-home(역할별 첫 화면)으로 간다
*   - 뒤로 갈 기록은 Navigation API 의 navigation.canGoBack 으로 확인한다 (#334 리뷰)
*   - Navigation API 를 지원하지 않는 예전 브라우저는 기록이 있는지 알 수 없어 늘 홈으로 간다
*       history.length 는 "앞으로 가기" 기록도 세고, document.referrer 는 새 탭으로 연 링크에도 남고,
*       "뒤로 가 본 뒤 잠시 기다리기"는 이전 화면이 늦게 열리면 홈으로 가 버려서 쓰지 않는다
*  */
(function () {
  'use strict';
  var backButton = document.querySelector('[data-error-back]');
  if (!backButton) return;

  function canGoBack() {
    return !!window.navigation && window.navigation.canGoBack === true;
  }

  backButton.addEventListener('click', function () {
    if (canGoBack()) {
      window.history.back();
      return;
    }
    window.location.href = backButton.dataset.home || '/';
  });
})();
