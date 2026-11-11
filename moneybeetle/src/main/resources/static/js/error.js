/* comment.
*   담당 : SCM 서수영 (#333, 공통 오류 화면)
*   오류 화면의 "이전 페이지" 버튼 (EP-04)
*   - 우리 사이트의 다른 화면에서 왔으면(document.referrer 가 같은 주소) 뒤로 간다
*   - 새 탭으로 바로 열었거나 바깥 사이트에서 왔으면 버튼의 data-home(역할별 첫 화면)으로 간다
*   - history.length 는 "앞으로 가기" 기록도 세므로 뒤로 갈 곳이 있는지 판단할 수 없다 (#334 리뷰)
*  */
(function () {
  'use strict';
  var backButton = document.querySelector('[data-error-back]');
  if (!backButton) return;

  function cameFromThisSite() {
    return document.referrer.indexOf(window.location.origin + '/') === 0;
  }

  backButton.addEventListener('click', function () {
    if (cameFromThisSite()) {
      window.history.back();
      return;
    }
    window.location.href = backButton.dataset.home || '/';
  });
})();
