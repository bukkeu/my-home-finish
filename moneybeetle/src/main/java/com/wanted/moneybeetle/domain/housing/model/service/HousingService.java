package com.wanted.moneybeetle.domain.housing.model.service;

import com.wanted.moneybeetle.domain.housing.model.dao.HousingApplicationRepository;
import com.wanted.moneybeetle.domain.housing.model.dao.HousingComplexRepository;
import com.wanted.moneybeetle.domain.housing.model.dto.ComplexDTO;
import com.wanted.moneybeetle.domain.housing.model.dto.ComplexDetailDTO;
import com.wanted.moneybeetle.domain.housing.model.dto.MyApplicationDTO;
import com.wanted.moneybeetle.domain.housing.model.entity.HousingApplication;
import com.wanted.moneybeetle.domain.housing.model.entity.HousingComplex;
import com.wanted.moneybeetle.global.exception.BusinessException;
import com.wanted.moneybeetle.global.exception.CommonErrorCode;
import lombok.RequiredArgsConstructor;
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
}