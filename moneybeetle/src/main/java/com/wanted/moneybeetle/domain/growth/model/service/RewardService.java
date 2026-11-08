package com.wanted.moneybeetle.domain.growth.model.service;

import com.wanted.moneybeetle.domain.growth.exception.GrowthErrorCode;
import com.wanted.moneybeetle.domain.growth.model.dao.CashHistoryRepository;
import com.wanted.moneybeetle.domain.growth.model.dao.ExpHistoryRepository;
import com.wanted.moneybeetle.domain.growth.model.dao.RewardRuleRepository;
import com.wanted.moneybeetle.domain.growth.model.dto.CashHistoryDTO;
import com.wanted.moneybeetle.domain.growth.model.dto.ExpHistoryDTO;
import com.wanted.moneybeetle.domain.growth.model.dto.RewardResultDTO;
import com.wanted.moneybeetle.domain.growth.model.entity.CashHistory;
import com.wanted.moneybeetle.domain.growth.model.entity.ExpHistory;
import com.wanted.moneybeetle.domain.growth.model.entity.RewardRule;
import com.wanted.moneybeetle.domain.member.model.dto.MemberBalanceDTO;
import com.wanted.moneybeetle.domain.member.model.service.MemberService;
import com.wanted.moneybeetle.global.exception.BusinessException;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/* comment.
*   담당 : DBA ② (성장)
*   다른 도메인이 부르는 보상 창구 (역할분담 문서 §7 연동 약속)
*   - grant(memberId, ruleCode, sourceId)             : 경험치·캐시 지급   [호출: 신아림 학습 완료·퀴즈, 출석]
*   - spendCash(memberId, sourceType, sourceId, amt)  : 캐시 사용         [호출: 이재성 시즌 참가, 서수영 청약통장 납입]
*   - getBalance(memberId)                            : 캐시 잔액 조회
*   - findCashHistories / findExpHistories            : 내역 조회 (API 22, 23)
*
*   진행 상태 (이슈 #10)
*   - 1차: 메서드 이름·파라미터·반환값 공개, 내역 조회 구현.
*   - 2차(지금): grant / spendCash / getBalance 구현.
*     · 회원의 캐시·경험치는 이 서비스가 직접 고치지 않고 member.MemberService 메서드로만 바꾼다.
*     · 바꾸기 전에 회원 행을 잠가(비관적 락) 같은 사람의 동시 요청이 끼어들지 못하게 하고, 같은 트랜잭션 안에서
*       이중 지급·잔액을 확인한다. 이력(cash_history·exp_history)의 UNIQUE 키가 마지막 방어선이다.
*     · 레벨 재계산과 레벨업 캐시(SP-23)는 #11 에서 붙인다. 그때까지 지급 결과의 levelFrom·levelTo 는 현재 레벨이다.
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
            RewardRule.QUIZ_PERFECT, ExpHistory.SOURCE_QUIZ_PERFECT);

    /** 보상 규칙 코드 -> 캐시 이력의 원인 종류. 여기에 없는 규칙은 캐시를 줄 수 없다 */
    private static final Map<String, String> CASH_SOURCE_BY_RULE = Map.of(
            RewardRule.COURSE_COMPLETE, CashHistory.SOURCE_COURSE_COMPLETE,
            RewardRule.ATTENDANCE_STREAK, CashHistory.SOURCE_ATTENDANCE);

    /** 캐시를 쓸 수 있는 원인 종류 (SP-16) */
    private static final List<String> USE_SOURCES = List.of(
            CashHistory.SOURCE_SEASON_JOIN, CashHistory.SOURCE_SUBSCRIPTION_DEPOSIT);

    private final RewardRuleRepository rewardRuleRepository;
    private final CashHistoryRepository cashHistoryRepository;
    private final ExpHistoryRepository expHistoryRepository;
    private final MemberService memberService;

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

        long cash = rule.getCashAmount();
        int exp = rule.getExpAmount();
        String expSource = EXP_SOURCE_BY_RULE.get(rule.getCode());
        String cashSource = CASH_SOURCE_BY_RULE.get(rule.getCode());
        if ((exp > 0 && expSource == null) || (cash > 0 && cashSource == null)) {
            throw new BusinessException(GrowthErrorCode.REWARD_NOT_SUPPORTED);
        }

        // 회원 행을 먼저 잠근다. 같은 회원의 다른 요청은 여기서 기다리므로 아래 이중 지급 확인이 안전하다
        MemberBalanceDTO before = memberService.lockBalance(memberId);

        boolean alreadyGranted =
                (exp > 0 && expHistoryRepository.existsByMemberIdAndSourceTypeAndSourceId(memberId, expSource, sourceId))
                        || (cash > 0 && cashHistoryRepository
                        .existsByMemberIdAndSourceTypeAndSourceId(memberId, cashSource, sourceId));
        if (alreadyGranted) {
            return RewardResultDTO.alreadyGranted(before.getCash(), before.getExp(), before.getLevel());
        }
        if (!rule.hasReward()) {
            return RewardResultDTO.granted(0, 0, before.getCash(), before.getExp(), before.getLevel(), before.getLevel());
        }

        MemberBalanceDTO after = memberService.addReward(memberId, cash, exp);
        if (cash > 0) {
            cashHistoryRepository.save(CashHistory.earn(
                    memberId, cashSource, sourceId, rule.getRewardRuleId(), cash, after.getCash()));
        }
        if (exp > 0) {
            // TODO(#11): 레벨 재계산을 붙이면 levelAfter 는 새 레벨이 된다 (지금은 현재 레벨)
            expHistoryRepository.save(ExpHistory.create(
                    memberId, expSource, sourceId, rule.getRewardRuleId(), exp, after.getExp(), after.getLevel()));
        }
        return RewardResultDTO.granted(exp, cash, after.getCash(), after.getExp(), before.getLevel(), after.getLevel());
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
        if (cashHistoryRepository.existsByMemberIdAndSourceTypeAndSourceId(memberId, sourceType, sourceId)) {
            throw new BusinessException(GrowthErrorCode.ALREADY_USED);
        }
        if (before.getCash() < amount) {
            throw new BusinessException(GrowthErrorCode.NOT_ENOUGH_CASH);
        }

        MemberBalanceDTO after = memberService.deductCash(memberId, amount);
        cashHistoryRepository.save(CashHistory.use(memberId, sourceType, sourceId, amount, after.getCash()));
        return after.getCash();
    }

    /** 회원의 현재 캐시 잔액을 조회한다. 없는 회원이면 MEMBER_NOT_FOUND */
    public long getBalance(Long memberId) {
        return memberService.getBalance(memberId).getCash();
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
