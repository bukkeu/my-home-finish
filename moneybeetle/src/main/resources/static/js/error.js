/* comment.
*   담당 : SCM 서수영 (#333, 공통 오류 화면)
*   오류 화면의 "이전 페이지" 버튼 (EP-04)
*   - 브라우저 기록이 있으면 뒤로 간다
*   - 새 탭으로 바로 열어 기록이 없으면 버튼의 data-home(역할별 첫 화면)으로 간다
*  */
(function () {
  'use strict';
  var backButton = document.querySelector('[data-error-back]');
  if (!backButton) return;

  backButton.addEventListener('click', function () {
    if (window.history.length > 1) {
      window.history.back();
      return;
    }
    window.location.href = backButton.dataset.home || '/';
  });
})();
