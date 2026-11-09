package com.wanted.moneybeetle.domain.housing.model.service;

import com.wanted.moneybeetle.domain.growth.model.entity.CashHistory;
import com.wanted.moneybeetle.domain.growth.model.service.RewardService;
import com.wanted.moneybeetle.domain.housing.exception.HousingErrorCode;
import com.wanted.moneybeetle.domain.housing.model.dao.HousingApplicationRepository;
import com.wanted.moneybeetle.domain.housing.model.dao.HousingComplexRepository;
import com.wanted.moneybeetle.domain.housing.model.dao.SubscriptionAccountRepository;
import com.wanted.moneybeetle.domain.housing.model.dao.SubscriptionDepositRepository;
import com.wanted.moneybeetle.domain.housing.model.dto.*;
import com.wanted.moneybeetle.domain.housing.model.entity.HousingApplication;
import com.wanted.moneybeetle.domain.housing.model.entity.HousingComplex;
import com.wanted.moneybeetle.domain.housing.model.entity.SubscriptionAccount;
import com.wanted.moneybeetle.domain.housing.model.entity.SubscriptionDeposit;
import com.wanted.moneybeetle.global.common.PageResponse;
import com.wanted.moneybeetle.global.exception.BusinessException;
import com.wanted.moneybeetle.global.exception.CommonErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/* comment.
 *   담당 : SCM (모의 청약)
 *   청약통장 납입(캐시 차감은 RewardService), 가점 계산, 신청(기간·중복 확인), 결과 조회
 *   #27 : 청약 캘린더(API 63), 공고 상세(API 64)
 *  */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)              // 조회만 하는 Service (CLAUDE.md 규칙)
public class HousingService {

    private final HousingComplexRepository complexRepository;
    private final HousingApplicationRepository applicationRepository;
    private final Clock clock;               // 오늘 날짜 (TimeConfig, 서울 기준)

    private final SubscriptionAccountRepository accountRepository;
    private final SubscriptionDepositRepository depositRepository;
    private final RewardService rewardService;   // 캐시 차감·조회 (김예린, growth 도메인)

    // API 63 청약 캘린더 : 그 달에 일정이 걸친 단지 + 내가 신청했는지
    public List<ComplexDTO> findCalendar(YearMonth month, Long memberId) {
        // ① 그 달의 첫날·마지막 날
        LocalDate monthStart = month.atDay(1);
        LocalDate monthEnd = month.atEndOfMonth();

        // ② 그 달에 걸친 단지들
        List<HousingComplex> complexes = complexRepository
                .findByNoticeDateLessThanEqualAndAnnounceDateGreaterThanEqualAndDeletedAtIsNullOrderByApplyStartDate(
                        monthEnd, monthStart);

        // ③ 그중 내가 신청한 단지 번호 (DB 에 한 번만 물어본다)
        List<Long> complexIds = complexes.stream().map(HousingComplex::getId).toList();
        Set<Long> appliedIds = applicationRepository.findByMemberIdAndHousingComplexIdIn(memberId, complexIds)
                .stream()
                .map(HousingApplication::getHousingComplexId)
                .collect(Collectors.toSet());

        // ④ 단지마다 DTO 로
        LocalDate today = LocalDate.now(clock);
        return complexes.stream()
                .map(complex -> ComplexDTO.from(complex, today, appliedIds.contains(complex.getId())))
                .toList();
    }

    // API 64 공고 상세 : 경쟁률은 접수 마감 뒤에만, 내 신청 내역
    public ComplexDetailDTO getComplex(Long complexId, Long memberId) {
        // ① 단지 찾기 (없거나 삭제됐으면 404)
        HousingComplex complex = complexRepository.findByIdAndDeletedAtIsNull(complexId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));

        LocalDate today = LocalDate.now(clock);

        // ② 경쟁률 : 마감 뒤에만 (SP-74, SUB-10). 소수 둘째 자리까지 (예: 2.35)
        Double competitionRate = null;
        if (complex.isApplyClosed(today) && complex.getSupplyUnits() > 0) {
            long applicants = applicationRepository.countByHousingComplexId(complexId);
            competitionRate = Math.round(applicants * 100.0 / complex.getSupplyUnits()) / 100.0;
        }

        // ③ 내 신청 내역 (없으면 null)
        MyApplicationDTO myApplication = applicationRepository
                .findByMemberIdAndHousingComplexId(memberId, complexId)
                .map(MyApplicationDTO::from)
                .orElse(null);

        // ④ DTO 로
        return ComplexDetailDTO.from(complex, today, competitionRate, myApplication);
    }

    // 회원가입 직후 통장 개설 (김예린 MemberService.signup 이 호출, 역할분담 §7). 이미 있으면 그대로 둔다
    @Transactional
    public void openAccount(Long memberId) {
        if (accountRepository.findByMemberId(memberId).isPresent()) {
            return;
        }
        accountRepository.save(SubscriptionAccount.create(memberId));
    }

    // API 65 내 청약통장
    public AccountDTO getAccount(Long memberId) {
        SubscriptionAccount account = getAccountOf(memberId);
        long cash = rewardService.getBalance(memberId);
        return AccountDTO.from(account, LocalDate.now(clock), cash);
    }

    // API 66 청약통장 납입 : 내역 저장 + 캐시 차감 + 통장 반영을 한 트랜잭션으로 (하나라도 실패하면 모두 취소)
    @Transactional
    public DepositResultDTO deposit(Long memberId, long amount) {
        // ① 금액 범위 (SP-70)
        if (amount < DepositPolicyDTO.MIN_AMOUNT) {
            throw new BusinessException(HousingErrorCode.DEPOSIT_BELOW_MIN);
        }
        if (amount > DepositPolicyDTO.MAX_AMOUNT) {
            throw new BusinessException(HousingErrorCode.DEPOSIT_ABOVE_MAX);
        }

        // ② 통장 (잠금) ③ 하루 1회 (DP-35)
        SubscriptionAccount account = accountRepository.findWithLockByMemberId(memberId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        LocalDate today = LocalDate.now(clock);
        if (account.isPaidToday(today)) {
            throw new BusinessException(HousingErrorCode.ALREADY_PAID_TODAY);
        }

        // ④ 내역 저장 (먼저 저장해야 내역 번호를 캐시 사용 기록의 원인 번호로 쓸 수 있다)
        long recognized = DepositPolicyDTO.recognizedAmountOf(amount);
        SubscriptionDeposit deposit = depositRepository.save(SubscriptionDeposit.create(
                account.getId(), today, (int) amount, (int) recognized, account.getPaidCount() + 1));

        // ⑤ 캐시 차감 (부족하면 NOT_ENOUGH_CASH 예외 → ④ 도 함께 취소)
        long cash = rewardService.spendCash(memberId, CashHistory.SOURCE_SUBSCRIPTION_DEPOSIT, deposit.getId(), amount);

        // ⑥ 통장 반영 (save 안 해도 트랜잭션이 끝날 때 변경 감지로 저장된다)
        account.deposit(amount, recognized, today);

        return DepositResultDTO.from(deposit, account, cash);
    }

    // API 67 납입 내역 (최신순, 페이지)
    public PageResponse<DepositDTO> findDeposits(Long memberId, Pageable pageable) {
        SubscriptionAccount account = getAccountOf(memberId);
        Page<DepositDTO> page = depositRepository
                .findBySubscriptionAccountId(account.getId(), pageable)
                .map(DepositDTO::from);
        return PageResponse.from(page);
    }

    // 내 통장 (가입할 때 만들어지므로 없으면 404)
    private SubscriptionAccount getAccountOf(Long memberId) {
        return accountRepository.findByMemberId(memberId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
    }
}