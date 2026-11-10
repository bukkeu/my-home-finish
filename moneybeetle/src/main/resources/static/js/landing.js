/* comment.
 * 담당: DBA ② 김예린 (#51, 서비스 소개 메인)
 * "내 집까지 가는 길"의 실제로 보이는 내용(넓은 화면은 구불구불한 길, 좁은 화면은 세로 목록)이 25% 이상 화면에 들어오면
 * #road 에 .is-shown 을 붙여 길과 역이 차례로 나타나게 한다 (원본 Main.dc.html 의 IntersectionObserver).
 * - 큰 구역 전체(제목·여백 포함) 대신 보이는 내용 요소를 관찰해서, 화면이 낮아도 시작 조건을 채울 수 있다.
 * - 화면 너비가 좁음/넓음 경계를 넘으면 관찰 대상을 새로 보이는 요소로 바꾼다. 애니메이션이 시작되면 관찰과 resize 감지를 모두 정리한다.
 * - 움직임 줄이기 설정이거나 IntersectionObserver 가 없는 브라우저는 처음부터 보여 준다 (CSS 도 같은 설정에서 전환을 끈다).
 */
(function () {
  'use strict';
  var road = document.getElementById('road');
  if (!road) return;

  var reduce = window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  if (reduce || !('IntersectionObserver' in window) || !window.matchMedia) {
    road.classList.add('is-shown');
    return;
  }

  var THRESHOLD = 0.25;
  var narrowQuery = window.matchMedia('(max-width: 759px)');
  var observer = null;

  function visibleContent() {
    return road.querySelector(narrowQuery.matches ? '.lane' : '.road__wide');
  }

  function stop() {
    if (observer) observer.disconnect();
    observer = null;
    if (narrowQuery.removeEventListener) narrowQuery.removeEventListener('change', observe);
    else narrowQuery.removeListener(observe);
  }

  function observe() {
    if (observer) observer.disconnect();
    var target = visibleContent();
    if (!target) return;
    observer = new IntersectionObserver(function (entries) {
      // 화면에 닿기만 한 것이 아니라 정해 둔 비율 이상 들어왔을 때만 시작한다
      if (entries.some(function (entry) { return entry.isIntersecting && entry.intersectionRatio >= THRESHOLD; })) {
        road.classList.add('is-shown');
        stop();
      }
    }, { threshold: [0, THRESHOLD, 1] });
    observer.observe(target);
  }

  if (narrowQuery.addEventListener) narrowQuery.addEventListener('change', observe);
  else narrowQuery.addListener(observe);
  observe();
})();
