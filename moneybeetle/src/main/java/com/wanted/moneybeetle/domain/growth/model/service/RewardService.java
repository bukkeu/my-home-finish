package com.wanted.moneybeetle.domain.growth.model.service;

import com.wanted.moneybeetle.domain.growth.model.dao.CashHistoryRepository;
import com.wanted.moneybeetle.domain.growth.model.dao.ExpHistoryRepository;
import com.wanted.moneybeetle.domain.growth.model.dao.RewardRuleRepository;
import com.wanted.moneybeetle.domain.growth.model.dto.CashHistoryDTO;
import com.wanted.moneybeetle.domain.growth.model.dto.ExpHistoryDTO;
import com.wanted.moneybeetle.domain.growth.model.dto.RewardResultDTO;
import com.wanted.moneybeetle.domain.growth.model.entity.CashHistory;
import java.util.List;
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
*   진행 상태 (이슈 #10, 1차)
*   - 메서드 이름·파라미터·반환값은 확정해서 공개한다. 다른 담당자는 지금 이 시그니처로 호출 코드를 쓸 수 있다.
*   - 내역 조회는 구현되어 있다.
*   - grant / spendCash / getBalance 는 회원 잔액을 바꿔야 해서 member 도메인(Member 엔티티, MemberService)과
*     global(BusinessException, 이슈 #2)이 올라온 뒤에 구현한다 (2차). 그전에 부르면 UnsupportedOperationException.
*   - 회원의 레벨·경험치·캐시 값은 이 서비스가 직접 고치지 않고 MemberService 메서드를 통해서만 바꾼다.
*  */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RewardService {

    private static final List<String> EARN_TYPES = List.of(CashHistory.EARN, CashHistory.ADMIN_GIVE);
    private static final List<String> USE_TYPES = List.of(CashHistory.USE, CashHistory.ADMIN_TAKE);

    private final RewardRuleRepository rewardRuleRepository;
    private final CashHistoryRepository cashHistoryRepository;
    private final ExpHistoryRepository expHistoryRepository;

    /**
     * 활동 보상(캐시·경험치)을 지급한다.
     * 같은 (memberId, 활동, sourceId)로 다시 불러도 한 번만 지급되고, 두 번째부터는
     * {@link RewardResultDTO#isAlreadyGranted()} 가 true 인 결과를 돌려준다 (예외 없음).
     *
     * @param memberId 받는 회원
     * @param ruleCode 보상 규칙 코드 (RewardRule.COURSE_COMPLETE 등)
     * @param sourceId 원인 데이터 ID (강의 ID 등). 이중 지급 방지의 기준
     */
    @Transactional
    public RewardResultDTO grant(Long memberId, String ruleCode, Long sourceId) {
        // TODO(#10 2차): 사용 중인 규칙 조회 -> 이중 지급 확인 -> MemberService 로 잔액·경험치 반영 -> 내역 기록
        throw new UnsupportedOperationException("RewardService.grant 는 Member 엔티티와 이슈 #2 가 올라온 뒤 구현합니다 (#10 2차)");
    }

    /**
     * 캐시를 사용한다 (시즌 참가, 청약통장 납입). 잔액은 0 미만이 될 수 없다 (SP-15).
     * 잔액이 모자라면 GrowthErrorCode.NOT_ENOUGH_CASH 로 BusinessException 을 던진다.
     *
     * @param sourceType CashHistory.SOURCE_SEASON_JOIN, CashHistory.SOURCE_SUBSCRIPTION_DEPOSIT 등
     * @param sourceId   시즌 ID, 납입 ID 등
     * @param amount     사용할 금액 (양수)
     * @return 사용 후 잔액
     */
    @Transactional
    public long spendCash(Long memberId, String sourceType, Long sourceId, long amount) {
        // TODO(#10 2차): 금액 검사 -> 잔액 확인 -> MemberService 로 차감 -> CashHistory.use 기록
        throw new UnsupportedOperationException("RewardService.spendCash 는 Member 엔티티와 이슈 #2 가 올라온 뒤 구현합니다 (#10 2차)");
    }

    /** 회원의 현재 캐시 잔액을 조회한다. */
    public long getBalance(Long memberId) {
        // TODO(#10 2차): MemberService 에서 잔액 조회
        throw new UnsupportedOperationException("RewardService.getBalance 는 Member 엔티티가 올라온 뒤 구현합니다 (#10 2차)");
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
            throw new IllegalArgumentException("type 은 EARN 또는 USE 만 쓸 수 있습니다: " + type);
        }
        return page.map(CashHistoryDTO::from);
    }

    /** 경험치 내역을 최신순으로 조회한다 (API 23). */
    public Page<ExpHistoryDTO> findExpHistories(Long memberId, Pageable pageable) {
        return expHistoryRepository.findByMemberIdOrderByCreatedAtDescExpHistoryIdDesc(memberId, pageable)
                .map(ExpHistoryDTO::from);
    }
}
