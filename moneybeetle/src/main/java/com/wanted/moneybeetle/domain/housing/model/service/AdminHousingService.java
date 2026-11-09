package com.wanted.moneybeetle.domain.housing.model.service;

import com.wanted.moneybeetle.domain.housing.exception.HousingErrorCode;
import com.wanted.moneybeetle.domain.housing.model.dao.HousingApplicationRepository;
import com.wanted.moneybeetle.domain.housing.model.dao.HousingComplexRepository;
import com.wanted.moneybeetle.domain.housing.model.dto.AdminComplexDTO;
import com.wanted.moneybeetle.domain.housing.model.dto.ComplexRequestDTO;
import com.wanted.moneybeetle.domain.housing.model.entity.HousingComplex;
import com.wanted.moneybeetle.global.exception.BusinessException;
import com.wanted.moneybeetle.global.exception.CommonErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/* comment.
*   담당 : SCM (모의 청약)
*   #47 관리자 공고 관리 (API 142~145, ADM-36)
*   - 등록·수정 : 입력값과 일정 순서(공고 ≤ 신청 시작 ≤ 마감 ≤ 발표)를 확인한다
*   - 신청자가 있으면 일정·공급 세대 수는 바꿀 수 없고(정보는 가능), 삭제도 할 수 없다 (SP-76)
*   - 삭제는 Soft Delete (deleted_at)
*   당첨 산정·발표(API 147·148, SP-75)는 #48 에서 이 클래스에 추가한다
*   TODO 관리자 작업 기록 : AdminLogService(김예린, 역할분담 §7)가 생기면 등록·수정·삭제를 남긴다
*  */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminHousingService {

    private final HousingComplexRepository complexRepository;
    private final HousingApplicationRepository applicationRepository;
    private final Clock clock;

    // API 142 단지 목록 : 삭제 안 된 단지 + 신청자 수 (한 번에 센다)
    public List<AdminComplexDTO> findComplexes() {
        List<HousingComplex> complexes = complexRepository.findByDeletedAtIsNullOrderByApplyStartDateDesc();
        Map<Long, Long> applicants = countApplicants(complexes.stream().map(HousingComplex::getId).toList());
        LocalDate today = LocalDate.now(clock);
        return complexes.stream()
                .map(c -> AdminComplexDTO.from(c, today, applicants.getOrDefault(c.getId(), 0L)))
                .toList();
    }

    // API 143 공고 등록 : 등록한 관리자 번호를 남긴다
    @Transactional
    public Long createComplex(Long adminId, ComplexRequestDTO request) {
        checkInput(request);
        HousingComplex complex = HousingComplex.create(
                request.getName(), request.getLocation(), request.getAreaType(), request.getSalePrice(),
                request.getSupplyUnits(), request.getSupplyType(),
                request.getNoticeDate(), request.getApplyStartDate(), request.getApplyEndDate(),
                request.getAnnounceDate(), adminId);
        return complexRepository.save(complex).getId();
    }

    // API 144 공고 수정 : 신청자가 있으면 일정·공급 세대 수를 바꾸는 수정은 거절, 정보만 바꾸는 수정은 허용 (SP-76)
    @Transactional
    public void updateComplex(Long complexId, ComplexRequestDTO request) {
        checkInput(request);
        HousingComplex complex = getComplex(complexId);

        boolean scheduleChanged = !complex.hasSameSchedule(request.getNoticeDate(), request.getApplyStartDate(),
                request.getApplyEndDate(), request.getAnnounceDate(), request.getSupplyUnits());
        if (scheduleChanged && hasApplicants(complexId)) {
            throw new BusinessException(HousingErrorCode.COMPLEX_HAS_APPLICANTS);
        }

        complex.changeInfo(request.getName(), request.getLocation(), request.getAreaType(),
                request.getSalePrice(), request.getSupplyType());
        complex.changeSchedule(request.getNoticeDate(), request.getApplyStartDate(), request.getApplyEndDate(),
                request.getAnnounceDate(), request.getSupplyUnits());
        // save 를 부르지 않아도 트랜잭션이 끝날 때 변경 감지로 저장된다
    }

    // API 145 공고 삭제 : 신청자가 없을 때만, Soft Delete
    @Transactional
    public void deleteComplex(Long complexId) {
        HousingComplex complex = getComplex(complexId);
        if (hasApplicants(complexId)) {
            throw new BusinessException(HousingErrorCode.COMPLEX_HAS_APPLICANTS);
        }
        complex.delete(LocalDateTime.now(clock));
    }

    // 입력값 확인 : 빈 값·0 이하 → INVALID_COMPLEX_INPUT, 일정 순서 → INVALID_SCHEDULE
    private void checkInput(ComplexRequestDTO request) {
        if (!request.isFilled()) {
            throw new BusinessException(HousingErrorCode.INVALID_COMPLEX_INPUT);
        }
        if (!request.isScheduleInOrder()) {
            throw new BusinessException(HousingErrorCode.INVALID_SCHEDULE);
        }
    }

    private HousingComplex getComplex(Long complexId) {
        return complexRepository.findByIdAndDeletedAtIsNull(complexId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
    }

    private boolean hasApplicants(Long complexId) {
        return applicationRepository.countByHousingComplexId(complexId) > 0;
    }

    // [단지 ID, 신청자 수] 목록을 Map 으로
    private Map<Long, Long> countApplicants(List<Long> complexIds) {
        if (complexIds.isEmpty()) {
            return Map.of();
        }
        return applicationRepository.countGroupByHousingComplexIdIn(complexIds).stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> (Long) row[1]));
    }
}
