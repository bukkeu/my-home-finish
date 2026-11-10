/* comment.
 * 담당: DBA ② 김예린 (#51, 서비스 소개 메인)
 * "내 집까지 가는 길"이 화면에 25% 이상 들어오면 .is-shown 을 붙여 길과 역이 차례로 나타나게 한다 (원본 Main.dc.html 의 IntersectionObserver).
 * 움직임 줄이기 설정이거나 IntersectionObserver 가 없는 브라우저는 처음부터 보여 준다 (CSS 도 같은 설정에서 전환을 끈다).
 */
(function () {
  'use strict';
  var road = document.getElementById('road');
  if (!road) return;

  var reduce = window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  if (reduce || !('IntersectionObserver' in window)) {
    road.classList.add('is-shown');
    return;
  }
  var observer = new IntersectionObserver(function (entries) {
    if (entries.some(function (entry) { return entry.isIntersecting; })) {
      road.classList.add('is-shown');
      observer.disconnect();
    }
  }, { threshold: 0.25 });
  observer.observe(road);
})();
