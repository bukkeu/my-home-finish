/* Money Beetle 중앙 설정 — 팀 확정 시 이 파일만 수정 (모든 페이지 공통) */
(function () {
  window.MB_CONFIG = window.MB_CONFIG || {
    level: { xpPerLevel: 150, maxLevel: 20 },
    tiers: [ { from: 1, name: '고시원' }, { from: 4, name: '원룸' }, { from: 8, name: '투룸' }, { from: 12, name: '아파트' }, { from: 17, name: '내 집' } ],
    unlock: { demoTrading: 8, demoHousing: 12 },
    // 캐시 보상 — 확정 (#76, 정책 정의서 SP-12~14)
    attendance: { bonusFromStreak: 5, bonusCash: 10000, timezone: 'Asia/Seoul' },
    reward: { courseCompleteCash: 30000, levelUpCashPerLevel: 10000 },
    // 청약통장 납입 — 확정 (#76, 정책 정의서 SP-70)
    housingDeposit: { min: 20000, max: 500000, dailyRecognized: 250000, perDayCount: 1 },
    // 시즌 이월금·랭킹 보상 — 확정 (#76, 정책 정의서 SP-63·64): 최종 순자산 전액 이월, 상한 없음
    trading: { carryoverRate: 100, carryoverCap: null, rankingCash: [50000, 30000, 10000] }
  };
  window.MB_CONFIG.tierOf = function (lv) { var t = window.MB_CONFIG.tiers, n = t[0].name; for (var i = 0; i < t.length; i++) if (lv >= t[i].from) n = t[i].name; return n; };
  window.MB_CONFIG.levelOf = function (totalXp) { var c = window.MB_CONFIG.level, lv = Math.min(c.maxLevel, Math.floor((totalXp || 0) / c.xpPerLevel) + 1); return { level: lv, xpInLevel: lv >= c.maxLevel ? c.xpPerLevel : (totalXp || 0) % c.xpPerLevel, xpPerLevel: c.xpPerLevel }; };
})();
