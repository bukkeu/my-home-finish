package com.wanted.moneybeetle.domain.housing.model.service;

import com.wanted.moneybeetle.domain.housing.exception.HousingErrorCode;
import com.wanted.moneybeetle.domain.housing.model.dao.HousingApplicationRepository;
import com.wanted.moneybeetle.domain.housing.model.dao.HousingComplexRepository;
import com.wanted.moneybeetle.domain.housing.model.dao.SubscriptionAccountRepository;
import com.wanted.moneybeetle.domain.housing.model.dto.ApplicationDTO;
import com.wanted.moneybeetle.domain.housing.model.dto.ApplicationResultDTO;
import com.wanted.moneybeetle.domain.housing.model.dto.ApplyResultDTO;
import com.wanted.moneybeetle.domain.housing.model.dto.ScoreDTO;
import com.wanted.moneybeetle.domain.housing.model.dto.ScoreRequestDTO;
import com.wanted.moneybeetle.domain.housing.model.entity.HousingApplication;
import com.wanted.moneybeetle.domain.housing.model.entity.HousingComplex;
import com.wanted.moneybeetle.domain.housing.model.entity.SubscriptionAccount;
import com.wanted.moneybeetle.domain.housing.model.policy.HousingScorePolicy;
import com.wanted.moneybeetle.global.common.PageResponse;
import com.wanted.moneybeetle.global.exception.BusinessException;
import com.wanted.moneybeetle.global.exception.CommonErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/* comment.
*   담당 : SCM (모의 청약)
*   #29 가점 계산(API 68) · 청약 신청(API 69) · 내 신청 내역(API 70) · 당첨 결과(API 71)
*   - 가점 점수표는 HousingScorePolicy (SP-71, 실제 가점제 별표1)
*   - 신청은 신청 기간 안에서만, 같은 단지 1회, 신청 후 취소·수정 불가 (SP-73)
*   - 결과는 관리자가 발표한 뒤에만 보여 준다 (SP-75). 당첨 산정은 #48
*  */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HousingApplicationService {

    private static final DateTimeFormatter RECEIPT_DATE = DateTimeFormatter.ofPattern("yyMMdd");

    private final HousingComplexRepository complexRepository;
    private final HousingApplicationRepository applicationRepository;
    private final SubscriptionAccountRepository accountRepository;
    private final HousingScorePolicy scorePolicy;
    private final Clock clock;

    // API 68 가점 계산기 : 입력한 무주택 기간·부양가족 + 내 통장 납입회차로 계산. 저장하지 않는다
    public ScoreDTO previewScore(Long memberId, ScoreRequestDTO request) {
        checkScoreInput(request);
        SubscriptionAccount account = getAccountOf(memberId);
        return scorePolicy.calculate(request.getHomelessYears(), request.getDependents(),
                account.getPaidCount(), LocalDate.now(clock));
    }

    // API 69 청약 신청 : 신청 기간 → 중복 → 가점 계산 → 접수번호 발급 → 저장
    @Transactional
    public ApplyResultDTO apply(Long memberId, Long complexId, ScoreRequestDTO request) {
        checkScoreInput(request);
        HousingComplex complex = complexRepository.findByIdAndDeletedAtIsNull(complexId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));

        LocalDate today = LocalDate.now(clock);
        if (!complex.isApplyOpen(today)) {
            throw new BusinessException(HousingErrorCode.NOT_IN_APPLY_PERIOD);
        }
        if (applicationRepository.existsByMemberIdAndHousingComplexId(memberId, complexId)) {
            throw new BusinessException(HousingErrorCode.ALREADY_APPLIED);
        }

        // 신청 시점의 납입회차로 점수를 계산해 그대로 저장한다 (나중에 납입해도 신청 점수는 바뀌지 않음)
        int paidCount = getAccountOf(memberId).getPaidCount();
        ScoreDTO score = scorePolicy.calculate(request.getHomelessYears(), request.getDependents(), paidCount, today);

        HousingApplication application = HousingApplication.create(
                memberId, complexId, nextReceiptNo(today),
                request.getHomelessYears(), request.getDependents(), paidCount,
                score.getHomelessScore(), score.getDependentScore(), score.getAccountScore(),
                LocalDateTime.now(clock));
        try {
            return ApplyResultDTO.from(applicationRepository.saveAndFlush(application));
        } catch (DataIntegrityViolationException e) {
            // 유니크 키가 2개라 어느 키에 걸렸는지 나눈다
            // - 접수번호(uk_housing_application_receipt_no) : 다른 회원과 같은 순간에 신청해 번호가 겹침 → 저장 안 됨, 다시 신청 안내
            // - 회원+단지(uk_housing_application_1)        : 같은 단지에 동시에 두 번 신청 → 중복 신청
            if (isReceiptNoConflict(e)) {
                throw new BusinessException(HousingErrorCode.RECEIPT_NO_CONFLICT);
            }
            throw new BusinessException(HousingErrorCode.ALREADY_APPLIED);
        }
    }

    // MySQL 메시지 예 : Duplicate entry 'MB-261103-0001' for key 'housing_application.uk_housing_application_receipt_no'
    private boolean isReceiptNoConflict(DataIntegrityViolationException e) {
        String message = String.valueOf(e.getMostSpecificCause().getMessage());
        return message.toLowerCase().contains("receipt_no");
    }

    // API 70 내 신청 내역 : 신청 최신순. 단지 이름·발표일은 한 번에 조회한다
    public PageResponse<ApplicationDTO> findMyApplications(Long memberId, Pageable pageable) {
        Page<HousingApplication> page = applicationRepository.findByMemberId(memberId, pageable);
        Map<Long, HousingComplex> complexes = complexRepository
                .findAllById(page.getContent().stream().map(HousingApplication::getHousingComplexId).toList())
                .stream()
                .collect(Collectors.toMap(HousingComplex::getId, Function.identity()));
        return PageResponse.from(page.map(app -> ApplicationDTO.from(app, complexes.get(app.getHousingComplexId()))));
    }

    // API 71 당첨 결과 : 본인 신청만, 발표 뒤에만 (SP-75)
    public ApplicationResultDTO getResult(Long memberId, Long applicationId) {
        HousingApplication application = applicationRepository.findById(applicationId)
                .filter(app -> app.getMemberId().equals(memberId))           // 남의 신청은 없는 것으로 본다
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        HousingComplex complex = complexRepository.findById(application.getHousingComplexId())
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));

        if (!complex.isAnnounced() || !application.hasResult()) {
            throw new BusinessException(HousingErrorCode.NOT_ANNOUNCED_YET);
        }
        return ApplicationResultDTO.from(application);
    }

    // 무주택 기간 0~50년, 부양가족 0~20명 (가점 계산기·신청 모두 이 검사를 거친다)
    private void checkScoreInput(ScoreRequestDTO request) {
        if (!request.isInRange()) {
            throw new BusinessException(HousingErrorCode.INVALID_SCORE_INPUT);
        }
    }

    // 접수번호 MB-yyMMdd-0001 : 오늘 들어온 신청 수 + 1 (API 69 예시 형식)
    private String nextReceiptNo(LocalDate today) {
        long seq = applicationRepository.countByAppliedAtGreaterThanEqual(today.atStartOfDay()) + 1;
        return String.format("MB-%s-%04d", today.format(RECEIPT_DATE), seq);
    }

    // 내 청약통장 (가입할 때 만들어지므로 없으면 404)
    private SubscriptionAccount getAccountOf(Long memberId) {
        return accountRepository.findByMemberId(memberId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
    }
}
