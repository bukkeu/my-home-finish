/* comment.
*   담당 : DBA ② 김예린 (#49, 공통 설정)
*   프로토타입 mb-config.js 대체. 설정값의 기준은 서버(system_setting 테이블)다.
*   header 조각(fragments/layout/header :: scripts)이 서버 값을 window.MB_SERVER_CONFIG 로 먼저 넣어 주고,
*   이 파일은 그 값에 아래 기본값을 덮어 MB_CONFIG 를 만든다. 서버 값이 없으면(정적 미리보기) 기본값을 쓴다.
*   레벨·칭호 계산은 화면 표시용이다. 실제 레벨·해금 판단은 서버가 한다 (LevelCheckInterceptor).
*  */
(function () {
  var server = window.MB_SERVER_CONFIG || {};
  var cfg = window.MB_CONFIG = {
    level: Object.assign({ xpPerLevel: 150, maxLevel: 20 }, server.level),
    tiers: server.tiers || [
      { from: 1, name: '고시원' }, { from: 4, name: '원룸' }, { from: 8, name: '투룸' },
      { from: 12, name: '아파트' }, { from: 17, name: '내 집' }
    ],
    unlock: Object.assign({ demoTrading: 8, demoHousing: 12 }, server.unlock)
  };

  /** 레벨에 해당하는 칭호 이름 */
  cfg.tierOf = function (lv) {
    var name = cfg.tiers[0].name;
    for (var i = 0; i < cfg.tiers.length; i++) if (lv >= cfg.tiers[i].from) name = cfg.tiers[i].name;
    return name;
  };
})();
