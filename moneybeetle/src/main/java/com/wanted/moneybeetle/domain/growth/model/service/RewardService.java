package com.wanted.moneybeetle.domain.growth.model.service;

import com.wanted.moneybeetle.domain.growth.exception.GrowthErrorCode;
import com.wanted.moneybeetle.domain.growth.model.dao.CashHistoryRepository;
import com.wanted.moneybeetle.domain.growth.model.dao.ExpHistoryRepository;
import com.wanted.moneybeetle.domain.growth.model.dao.RewardRuleRepository;
import com.wanted.moneybeetle.domain.growth.model.dto.AdminCashChangeDTO;
import com.wanted.moneybeetle.domain.growth.model.dto.CashHistoryDTO;
import com.wanted.moneybeetle.domain.growth.model.dto.ExpHistoryDTO;
import com.wanted.moneybeetle.domain.growth.model.dto.RewardResultDTO;
import com.wanted.moneybeetle.domain.growth.model.entity.CashHistory;
import com.wanted.moneybeetle.domain.growth.model.entity.ExpHistory;
import com.wanted.moneybeetle.domain.growth.model.entity.RewardRule;
import com.wanted.moneybeetle.domain.member.model.dto.MemberBalanceDTO;
import com.wanted.moneybeetle.domain.member.model.entity.Member;
import com.wanted.moneybeetle.domain.member.model.service.MemberService;
import com.wanted.moneybeetle.domain.notification.model.entity.Notification;
import com.wanted.moneybeetle.domain.notification.model.service.NotificationService;
import com.wanted.moneybeetle.global.exception.BusinessException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import com.wanted.moneybeetle.global.security.UnlockFeature;
import com.wanted.moneybeetle.global.security.UnlockLevelProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/* comment.
*   담당 : DBA ② (성장·캐시·레벨)
*   다른 도메인이 부르는 보상 창구 (역할분담 문서 §7 연동 약속)
*   - grant(memberId, ruleCode, sourceId)             : 경험치·캐시 지급   [호출: 신아림 학습 완료·퀴즈, 출석]
*   - grantRankingReward(memberId, rank, awardId)     : 시즌 랭킹 1~3위 캐시 지급 (SP-64) [호출: 이재성 랭킹 공개]
*   - spendCash(memberId, sourceType, sourceId, amt)  : 캐시 사용         [호출: 이재성 시즌 참가, 서수영 청약통장 납입]
*   - adjustByAdmin(adminId, memberId, give, amt, why): 관리자 수동 지급·회수 [호출: growth.CashAdjustService, 이슈 #39]
*   - getBalance(memberId) / getBalanceSnapshot       : 회원 캐시 잔액·성장 정보 조회
*   - findCashHistories / findExpHistories            : 내역 조회 (API 22, 23)
*
*   진행 상태 (이슈 #10)
*   - 1차: 메서드 이름·파라미터·반환값 공개, 내역 조회 구현.
*   - 2차(지금): grant / spendCash / getBalance 구현.
*     · 회원의 캐시·경험치는 이 서비스가 직접 고치지 않고 member.MemberService 메서드로만 바꾼다.
*     · 바꾸기 전에 회원 행을 잠가(비관적 락) 같은 사람의 동시 요청이 끼어들지 못하게 하고, 같은 트랜잭션 안에서
*       이중 지급·잔액을 확인한다. 이력(cash_history·exp_history)의 UNIQUE 키가 마지막 방어선이다.
*     · 경험치를 받으면 레벨 정책표(level_policy)로 레벨을 다시 계산해 올리고, 오른 레벨마다 레벨업 캐시를 준다 (#11, SP-23).
*       결과의 levelFrom·levelTo·levelUpCash·unlocks 로 화면이 레벨업·해금 모달을 띄운다.
*       · 새 레벨로 열린 기능(unlocks)이 있으면 같은 트랜잭션에서 NotificationService.send 로 해금 알림을 보낸다 (#14).
*  */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RewardService {

    private static final List<String> EARN_TYPES = List.of(CashHistory.EARN, CashHistory.ADMIN_GIVE);
    private static final List<String> USE_TYPES = List.of(CashHistory.USE, CashHistory.ADMIN_TAKE);

    /** 보상 규칙 코드 -> 경험치 이력의 원인 종류. 여기에 없는 규칙은 경험치를 줄 수 없다 */
    private static final Map<String, String> EXP_SOURCE_BY_RULE = Map.of(
            RewardRule.COURSE_COMPLETE, ExpHistory.SOURCE_COURSE_COMPLETE,
            RewardRule.QUIZ_PERFECT, ExpHistory.SOURCE_QUIZ_PERFECT,
            RewardRule.ATTENDANCE_DAILY, ExpHistory.SOURCE_ATTENDANCE);

    /** 보상 규칙 코드 -> 캐시 이력의 원인 종류. 여기에 없는 규칙은 캐시를 줄 수 없다 */
    private static final Map<String, String> CASH_SOURCE_BY_RULE = Map.of(
            RewardRule.COURSE_COMPLETE, CashHistory.SOURCE_COURSE_COMPLETE,
            RewardRule.ATTENDANCE_STREAK, CashHistory.SOURCE_ATTENDANCE,
            RewardRule.RANKING_RANK_1, CashHistory.SOURCE_RANKING,
            RewardRule.RANKING_RANK_2, CashHistory.SOURCE_RANKING,
            RewardRule.RANKING_RANK_3, CashHistory.SOURCE_RANKING);

    /**
     * 이 규칙 코드가 이 캐시·경험치를 지급할 수 있는가. 0 인 쪽은 항상 지급할 수 있다 (줄 것이 없으므로).
     * 지급(grantInternal)과 관리자 규칙 수정(RewardRuleService, #244)이 같은 기준을 쓴다.
     * 예) ATTENDANCE_DAILY 는 경험치만, ATTENDANCE_STREAK·RANKING_RANK_1~3 은 캐시만 줄 수 있다
     */
    public static boolean canGrant(String ruleCode, long cash, long exp) {
        return (exp <= 0 || EXP_SOURCE_BY_RULE.containsKey(ruleCode))
                && (cash <= 0 || CASH_SOURCE_BY_RULE.containsKey(ruleCode));
    }

    /** 출석 경험치(ATTENDANCE_DAILY)가 하루에 받을 수 있는 최대 XP (SP-22). 규칙의 기본값을 바꿔도 이 값을 넘지 않는다 */
    public static final int ATTENDANCE_DAILY_EXP_CAP = 50;

    /** 캐시를 쓸 수 있는 원인 종류 (SP-16) */
    private static final List<String> USE_SOURCES = List.of(
            CashHistory.SOURCE_SEASON_JOIN, CashHistory.SOURCE_SUBSCRIPTION_DEPOSIT);

    private final RewardRuleRepository rewardRuleRepository;
    private final CashHistoryRepository cashHistoryRepository;
    private final ExpHistoryRepository expHistoryRepository;
    private final MemberService memberService;
    private final GrowthService growthService;
    private final NotificationService notificationService;
    private final UnlockLevelProvider unlockLevelProvider;

    /**
     * 활동 보상(캐시·경험치)을 지급한다.
     * 같은 (memberId, 활동, sourceId)로 다시 불러도 한 번만 지급되고, 두 번째부터는
     * {@link RewardResultDTO#isAlreadyGranted()} 가 true 인 결과를 돌려준다 (예외 없음).
     *
     * @param memberId 받는 회원
     * @param ruleCode 보상 규칙 코드 (RewardRule.COURSE_COMPLETE 등)
     * @param sourceId 원인 데이터 ID (강의 ID 등). 이중 지급 방지의 기준
     * @throws BusinessException REWARD_RULE_NOT_FOUND(사용 중인 규칙이 없음), REWARD_NOT_SUPPORTED(규칙 설정 오류),
     *                           MEMBER_NOT_FOUND(없는 회원)
     */
    @Transactional
    public RewardResultDTO grant(Long memberId, String ruleCode, Long sourceId) {
        if (memberId == null || sourceId == null) {
            throw new IllegalArgumentException("memberId 와 sourceId 가 필요합니다.");
        }
        RewardRule rule = rewardRuleRepository.findByCodeAndActiveTrue(ruleCode)
                .orElseThrow(() -> new BusinessException(GrowthErrorCode.REWARD_RULE_NOT_FOUND));
        return grantInternal(memberId, rule, sourceId, 1);
    }

    /**
     * 규칙의 경험치를 expMultiplier 배로 지급한다 (예: 연속 출석 일수 구간에 따라 10~50 XP, SP-22).
     * 캐시는 규칙 값 그대로이고, 이중 지급 방지·레벨업·해금 알림은 {@link #grant} 와 똑같이 처리한다.
     * 배수는 호출하는 쪽이 정하고(출석은 AttendanceService), 규칙 표에는 기본값만 둔다.
     * 출석 경험치(ATTENDANCE_DAILY)는 배수를 곱한 값이 하루 상한({@link #ATTENDANCE_DAILY_EXP_CAP} XP)을 넘지 않게 자른다 (SP-22).
     *
     * 규칙은 여기서 한 번만 읽는다. 사용 중인 규칙이 없거나 경험치 기본값이 0 이면 아무것도 바꾸지 않고 빈 결과를 돌려준다
     * (예외를 던지지 않으므로, 호출한 쪽의 트랜잭션 — 예: 출석 기록 — 이 되돌려지지 않는다).
     * 규칙을 읽은 뒤에 일어나는 오류(레벨 정책표 설정 오류 등)는 grant 와 같이 예외로 던진다.
     *
     * @param expMultiplier 경험치 배수 (1 이상)
     * @return 지급 결과. 지급할 규칙이 없으면(꺼짐·기본값 0) 비어 있다
     * @throws IllegalArgumentException 배수가 1 보다 작을 때
     */
    @Transactional
    public Optional<RewardResultDTO> grantWithExpMultiplier(Long memberId, String ruleCode, Long sourceId, int expMultiplier) {
        if (expMultiplier < 1) {
            throw new IllegalArgumentException("경험치 배수는 1 이상이어야 합니다: " + expMultiplier);
        }
        if (memberId == null || sourceId == null) {
            throw new IllegalArgumentException("memberId 와 sourceId 가 필요합니다.");
        }
        return rewardRuleRepository.findByCodeAndActiveTrue(ruleCode)
                .filter(rule -> rule.getExpAmount() > 0)
                .map(rule -> grantInternal(memberId, rule, sourceId, expMultiplier));
    }

    /** 읽어 둔 규칙으로 지급한다 (규칙을 다시 읽지 않는다). 호출하는 쪽이 사용 중인 규칙을 넘긴다 */
    private RewardResultDTO grantInternal(Long memberId, RewardRule rule, Long sourceId, int expMultiplier) {
        long cash = rule.getCashAmount();
        int exp = scaledExp(rule, expMultiplier);
        String expSource = EXP_SOURCE_BY_RULE.get(rule.getCode());
        String cashSource = CASH_SOURCE_BY_RULE.get(rule.getCode());
        if (!canGrant(rule.getCode(), cash, exp)) {
            throw new BusinessException(GrowthErrorCode.REWARD_NOT_SUPPORTED);
        }

        // 회원 행을 먼저 잠근다. 같은 회원의 다른 요청은 여기서 기다리므로 아래 이중 지급 확인이 안전하다
        MemberBalanceDTO before = memberService.lockBalance(memberId);

        // 이중 지급은 "지금 규칙이 얼마를 주는가"가 아니라 "이 활동의 이력이 이미 있는가"로 판단한다.
        // 관리자가 규칙 값을 바꿔 지금 지급량이 0이 되어도(예: 경험치만 주던 규칙을 캐시만 주도록 변경) 받은 기록이 있으면 다시 주지 않는다.
        // 이력 조회는 잠금 조회(FOR UPDATE)로 한다. 일반 조회는 바깥 트랜잭션이 이미 만든 REPEATABLE READ 스냅샷을 볼 수 있어서,
        // 회원 행을 잠근 사이에 다른 요청이 커밋한 이력을 못 볼 수 있기 때문이다.
        boolean alreadyGranted =
                (expSource != null && expHistoryRepository
                        .findWithLockByMemberIdAndSourceTypeAndSourceId(memberId, expSource, sourceId).isPresent())
                        || (cashSource != null && cashHistoryRepository
                        .findWithLockByMemberIdAndSourceTypeAndSourceId(memberId, cashSource, sourceId).isPresent());
        if (alreadyGranted) {
            return RewardResultDTO.alreadyGranted(before.getCash(), before.getExp(), before.getLevel());
        }
        if (!rule.hasReward()) {
            return RewardResultDTO.granted(0, 0, 0, before.getCash(), before.getExp(),
                    before.getLevel(), before.getLevel(), List.of());
        }

        MemberBalanceDTO after = memberService.addReward(memberId, cash, exp);
        if (cash > 0) {
            cashHistoryRepository.save(CashHistory.earn(
                    memberId, cashSource, sourceId, rule.getRewardRuleId(), cash, after.getCash()));
        }

        int levelFrom = before.getLevel();
        int levelTo = levelFrom;
        long levelUpCash = 0;
        long cashBalance = after.getCash();
        List<String> unlocks = List.of();
        if (exp > 0) {
            // 경험치로 레벨이 올랐으면 새 레벨까지 올리고, 오른 레벨마다 레벨업 캐시를 준다 (SP-23).
            // 이 작업은 위 회원 행 잠금 안에서 이어지므로 같은 트랜잭션이고, 이미 받은 활동은 위에서 걸러진다.
            LevelTable table = loadValidLevelTable();
            int reached = table.levelFor(after.getExp());
            if (reached > levelFrom) {
                levelUpCash = table.levelUpCashBetween(levelFrom, reached);
                memberService.levelUp(memberId, reached);                     // 레벨만 올린다
                // 레벨업 캐시도 규칙 캐시와 같은 addReward 경로로 준다 (캐시 변경 경로를 하나로 둔다)
                cashBalance = memberService.addReward(memberId, levelUpCash, 0).getCash();
                saveLevelUpCashHistories(memberId, table, levelFrom, reached, cashBalance - levelUpCash);
                levelTo = reached;
                // 청약·모의투자는 각각 운영 설정의 해금 레벨에 닿을 때만 열린다 (여러 레벨을 한 번에 올라도 한 번, #266)
                unlocks = UnlockRules.unlockedBetween(levelFrom, reached,
                        unlockLevelProvider.getLevel(UnlockFeature.TRADING),
                        unlockLevelProvider.getLevel(UnlockFeature.HOUSING));
                sendUnlockNotifications(memberId, unlocks);
            }
            expHistoryRepository.save(ExpHistory.create(
                    memberId, expSource, sourceId, rule.getRewardRuleId(), exp, after.getExp(), levelTo));
        }
        return RewardResultDTO.granted(exp, cash, levelUpCash, cashBalance, after.getExp(), levelFrom, levelTo, unlocks);
    }

    /**
     * 새로 열린 기능마다 해금 알림을 한 건씩 보낸다 (GRW-08). 호출한 grant 의 트랜잭션에 묶이므로 지급이 되돌려지면 알림도 사라진다.
     * 이동 주소는 화면설계의 모의투자·모의 청약 화면이다.
     */
    private void sendUnlockNotifications(Long memberId, List<String> unlocks) {
        for (String unlock : unlocks) {
            if (UnlockRules.TRADING.equals(unlock)) {
                notificationService.send(memberId, Notification.TYPE_UNLOCK, "모의투자가 열렸어요", "/user/demo-trading");
            } else if (UnlockRules.HOUSING.equals(unlock)) {
                notificationService.send(memberId, Notification.TYPE_UNLOCK, "모의 청약이 열렸어요",
                        "/user/demo-housing-subscription");
            }
        }
    }

    /**
     * 규칙의 경험치 기본값에 배수를 곱한다. 곱셈은 long 으로 해서 큰 기본값에서도 넘치지 않게 한 뒤,
     * 출석 경험치(ATTENDANCE_DAILY)에만 하루 상한을 적용한다. 다른 규칙은 값을 자르지 않는다 (배수 1 이면 규칙 값 그대로).
     */
    private static int scaledExp(RewardRule rule, int expMultiplier) {
        long scaled = (long) rule.getExpAmount() * expMultiplier;
        if (RewardRule.ATTENDANCE_DAILY.equals(rule.getCode())) {
            scaled = Math.min(scaled, ATTENDANCE_DAILY_EXP_CAP);
        }
        return (int) Math.min(scaled, Integer.MAX_VALUE);
    }

    /**
     * 시즌 랭킹 상위 3위 보상 캐시를 지급한다 (SP-64, #10). 랭킹을 공개할 때 순위별로 부른다 [호출: 이재성 랭킹 공개].
     * 순자산·수익률 랭킹 각각의 수상 기록(ranking_award)마다 한 번씩 부르고, 원인 ID 는 그 수상 기록의 ID 다.
     * 한 회원이 두 랭킹에 모두 들면 수상 기록이 두 개라 두 번 받고, 같은 수상 기록으로 다시 불러도 한 번만 지급된다
     * ({@link RewardResultDTO#isAlreadyGranted()}). 금액은 규칙(RANKING_RANK_1~3)에서 읽고, 규칙이 없거나 꺼져 있으면 REWARD_RULE_NOT_FOUND 다.
     *
     * @param rank            순위 (1~3). 그 밖의 값은 IllegalArgumentException
     * @param rankingAwardId  수상 기록 ID (ranking_award.ranking_award_id)
     */
    @Transactional
    public RewardResultDTO grantRankingReward(Long memberId, int rank, Long rankingAwardId) {
        return grant(memberId, RewardRule.rankingRuleCode(rank), rankingAwardId);
    }

    /**
     * 레벨 정책표를 읽는다. 행 수가 최대 레벨(Member.MAX_LEVEL)과 다르면 설정 오류로 보고 LEVEL_POLICY_INVALID 로 멈춘다
     * (표가 짧으면 레벨이 모자라게 계산되고, 길면 Member 가 거절하는 레벨까지 올리려 한다).
     * 이 예외는 grant 의 트랜잭션을 되돌려, 앞에서 더한 캐시·경험치와 이력이 모두 취소된다.
     */
    private LevelTable loadValidLevelTable() {
        LevelTable table = growthService.loadLevelTable();
        if (table.maxLevel() != Member.MAX_LEVEL) {
            throw new BusinessException(GrowthErrorCode.LEVEL_POLICY_INVALID);
        }
        return table;
    }

    /** 오른 레벨마다 레벨업 캐시 내역을 한 줄씩 남긴다 (원인 종류 LEVEL_UP, 원인 ID = 레벨. 같은 레벨은 한 번만 기록된다) */
    private void saveLevelUpCashHistories(Long memberId, LevelTable table, int fromLevel, int toLevel, long balanceBefore) {
        long balance = balanceBefore;
        for (int level = fromLevel + 1; level <= toLevel; level++) {
            long levelCash = table.policyOf(level).getLevelupCash();
            if (levelCash > 0) {
                balance += levelCash;
                cashHistoryRepository.save(CashHistory.earn(
                        memberId, CashHistory.SOURCE_LEVEL_UP, (long) level, null, levelCash, balance));
            }
        }
    }

    /**
     * 캐시를 사용한다 (시즌 참가, 청약통장 납입). 잔액은 0 미만이 될 수 없다 (SP-15).
     *
     * @param sourceType CashHistory.SOURCE_SEASON_JOIN, CashHistory.SOURCE_SUBSCRIPTION_DEPOSIT
     * @param sourceId   시즌 ID, 납입 ID 등. 같은 (회원, 원인 종류, 원인 ID)로는 한 번만 쓸 수 있다
     * @param amount     사용할 금액 (양수)
     * @return 사용 후 잔액
     * @throws BusinessException INVALID_AMOUNT(금액이 0 이하), NOT_ENOUGH_CASH(잔액 부족),
     *                           ALREADY_USED(같은 원인으로 이미 사용), MEMBER_NOT_FOUND(없는 회원)
     */
    @Transactional
    public long spendCash(Long memberId, String sourceType, Long sourceId, long amount) {
        if (memberId == null || sourceId == null || !USE_SOURCES.contains(sourceType)) {
            throw new IllegalArgumentException("memberId, sourceId 와 사용 가능한 sourceType 이 필요합니다: " + sourceType);
        }
        if (amount <= 0) {
            throw new BusinessException(GrowthErrorCode.INVALID_AMOUNT);
        }

        MemberBalanceDTO before = memberService.lockBalance(memberId);
        // grant 와 같은 이유로 잠금 조회로 확인한다 (바깥 트랜잭션의 스냅샷이 아니라 최신 커밋 이력을 본다)
        if (cashHistoryRepository.findWithLockByMemberIdAndSourceTypeAndSourceId(memberId, sourceType, sourceId).isPresent()) {
            throw new BusinessException(GrowthErrorCode.ALREADY_USED);
        }
        if (before.getCash() < amount) {
            throw new BusinessException(GrowthErrorCode.NOT_ENOUGH_CASH);
        }

        MemberBalanceDTO after = memberService.deductCash(memberId, amount);
        cashHistoryRepository.save(CashHistory.use(memberId, sourceType, sourceId, amount, after.getCash()));
        return after.getCash();
    }

    /**
     * 관리자 수동 지급·회수 (API 110, SP-17, 이슈 #39). 회원 행 잠금 → 잔액 확인 → 캐시 변경 → 내역(cash_history) 저장을 한 트랜잭션으로 한다.
     * 캐시를 바꾸는 길을 이 서비스 하나로 모으려고 관리자 조정도 여기서 한다. 작업 기록(admin_log)은 부르는 쪽(CashAdjustService)이
     * 같은 트랜잭션 안에서 남긴다. 금액 상한과 사유 공백 확인은 부르는 쪽 몫이다.
     *
     * @param give true 면 지급, false 면 회수
     * @return 내역 번호와 변경 전·후 잔액
     * @throws BusinessException INVALID_AMOUNT(금액이 0 이하), NOT_ENOUGH_CASH(회수할 캐시가 모자람), MEMBER_NOT_FOUND(없는 회원)
     */
    @Transactional
    public AdminCashChangeDTO adjustByAdmin(Long adminId, Long memberId, boolean give, long amount, String reason) {
        if (adminId == null || memberId == null || reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("adminId, memberId 와 사유가 필요합니다.");
        }
        if (amount <= 0) {
            throw new BusinessException(GrowthErrorCode.INVALID_AMOUNT);
        }

        MemberBalanceDTO before = memberService.lockBalance(memberId);
        MemberBalanceDTO after;
        CashHistory history;
        if (give) {
            after = memberService.addReward(memberId, amount, 0);
            history = CashHistory.adminGive(memberId, adminId, amount, after.getCash(), reason);
        } else {
            if (before.getCash() < amount) {
                throw new BusinessException(GrowthErrorCode.NOT_ENOUGH_CASH);
            }
            after = memberService.deductCash(memberId, amount);
            history = CashHistory.adminTake(memberId, adminId, amount, after.getCash(), reason);
        }
        CashHistory saved = cashHistoryRepository.save(history);
        return new AdminCashChangeDTO(saved.getCashHistoryId(), before.getCash(), after.getCash());
    }

    /** 사용 중인 보상 규칙이 주는 캐시. 규칙이 없거나 꺼져 있으면 0 (예: 출석 현황 화면이 보상 금액을 보여 줄 때) */
    public long findActiveCash(String ruleCode) {
        return rewardRuleRepository.findByCodeAndActiveTrue(ruleCode).map(RewardRule::getCashAmount).orElse(0);
    }

    /** 회원의 현재 캐시 잔액을 조회한다. 없는 회원이면 MEMBER_NOT_FOUND */
    public long getBalance(Long memberId) {
        return memberService.getBalance(memberId).getCash();
    }

    /** 회원의 캐시·경험치·레벨 현재 값을 조회한다 (성장 내역 API 응답용). */
    public MemberBalanceDTO getBalanceSnapshot(Long memberId) {
        return memberService.getBalance(memberId);
    }

    /**
     * 캐시 내역을 최신순으로 조회한다 (API 22).
     *
     * @param type "EARN"(적립·관리자 지급), "USE"(사용·관리자 회수), null 이면 전체
     */
    public Page<CashHistoryDTO> findCashHistories(Long memberId, String type, Pageable pageable) {
        Page<CashHistory> page;
        if (type == null || type.isBlank()) {
            page = cashHistoryRepository.findByMemberIdOrderByCreatedAtDescCashHistoryIdDesc(memberId, pageable);
        } else if (CashHistory.EARN.equals(type)) {
            page = cashHistoryRepository.findByMemberIdAndHistoryTypeInOrderByCreatedAtDescCashHistoryIdDesc(
                    memberId, EARN_TYPES, pageable);
        } else if (CashHistory.USE.equals(type)) {
            page = cashHistoryRepository.findByMemberIdAndHistoryTypeInOrderByCreatedAtDescCashHistoryIdDesc(
                    memberId, USE_TYPES, pageable);
        } else {
            throw new BusinessException(GrowthErrorCode.INVALID_HISTORY_TYPE);
        }
        return page.map(CashHistoryDTO::from);
    }

    /** 경험치 내역을 최신순으로 조회한다 (API 23). */
    public Page<ExpHistoryDTO> findExpHistories(Long memberId, Pageable pageable) {
        return expHistoryRepository.findByMemberIdOrderByCreatedAtDescExpHistoryIdDesc(memberId, pageable)
                .map(ExpHistoryDTO::from);
    }
}
