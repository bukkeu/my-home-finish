/* Money Beetle 중앙 설정 — 팀 확정 시 이 파일만 수정 (모든 페이지 공통) */
(function () {
  window.MB_CONFIG = window.MB_CONFIG || {
    level: { xpPerLevel: 150, maxLevel: 20 },
    tiers: [ { from: 1, name: '고시원' }, { from: 4, name: '원룸' }, { from: 8, name: '투룸' }, { from: 12, name: '아파트' }, { from: 17, name: '내 집' } ],
    unlock: { demoTrading: 8, demoHousing: 12 },
    attendance: { bonusFromStreak: 5, bonusCash: 1000, timezone: 'Asia/Seoul' },
    // 청약통장 납입 — 미확정: 정책 정의서 SP-70 초안은 1회 1,000캐시, 현재 구현은 아래 값
    housingDeposit: { min: 20000, max: 500000, dailyRecognized: 250000, perDayCount: 1 }
  };
  window.MB_CONFIG.tierOf = function (lv) { var t = window.MB_CONFIG.tiers, n = t[0].name; for (var i = 0; i < t.length; i++) if (lv >= t[i].from) n = t[i].name; return n; };
  window.MB_CONFIG.levelOf = function (totalXp) { var c = window.MB_CONFIG.level, lv = Math.min(c.maxLevel, Math.floor((totalXp || 0) / c.xpPerLevel) + 1); return { level: lv, xpInLevel: lv >= c.maxLevel ? c.xpPerLevel : (totalXp || 0) % c.xpPerLevel, xpPerLevel: c.xpPerLevel }; };
})();
