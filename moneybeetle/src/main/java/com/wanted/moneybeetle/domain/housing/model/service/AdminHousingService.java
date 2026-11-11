package com.wanted.moneybeetle.domain.housing.model.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wanted.moneybeetle.domain.housing.exception.HousingErrorCode;
import com.wanted.moneybeetle.domain.housing.model.dao.HousingApplicationRepository;
import com.wanted.moneybeetle.domain.housing.model.dao.HousingComplexRepository;
import com.wanted.moneybeetle.domain.housing.model.dto.AdminComplexDTO;
import com.wanted.moneybeetle.domain.housing.model.dto.AnnounceResultDTO;
import com.wanted.moneybeetle.domain.housing.model.dto.ApplicantDTO;
import com.wanted.moneybeetle.domain.housing.model.dto.ComplexRequestDTO;
import com.wanted.moneybeetle.domain.housing.model.dto.ComputeResultDTO;
import com.wanted.moneybeetle.domain.housing.model.entity.HousingApplication;
import com.wanted.moneybeetle.domain.housing.model.entity.HousingComplex;
import com.wanted.moneybeetle.domain.housing.model.policy.HousingWinnerPolicy;
import com.wanted.moneybeetle.domain.member.model.service.MemberService;
import com.wanted.moneybeetle.domain.notification.model.entity.NotificationType;   // 알림 종류 enum (#289). Notification 엔티티는 쓰지 않는다
import com.wanted.moneybeetle.domain.notification.model.service.NotificationService;
import com.wanted.moneybeetle.domain.operation.model.service.AdminLogService;
import com.wanted.moneybeetle.global.exception.BusinessException;
import com.wanted.moneybeetle.global.exception.CommonErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/* comment.
*   담당 : SCM (모의 청약)
*   #47 관리자 공고 관리 (API 142~145, ADM-36)
*   - 등록·수정 : 입력값과 일정 순서(공고 ≤ 신청 시작 ≤ 마감 ≤ 발표)를 확인한다
*   - 신청자가 있으면 일정·공급 세대 수는 바꿀 수 없고(정보는 가능), 삭제도 할 수 없다 (SP-76)
*   - 삭제는 Soft Delete (deleted_at)
*   #48 신청자 목록·당첨자 산정·결과 발표 (API 146~148, ADM-37~39, SP-75)
*   - 산정 : 신청 마감 다음 날부터 한 번만. 가점 → 납입회차 → 추첨 순으로 공급 세대 수만큼 WIN (HousingWinnerPolicy)
*   - 발표 : 산정한 뒤 발표일부터 한 번만. 신청자 모두에게 알림(김예린 NotificationService)
*   - 수정·삭제·산정·발표·신청(#29)은 단지 행을 먼저 잠근다(findForUpdate) → 같은 단지 작업이 동시에 섞이지 않는다
*   #178 관리자 작업 기록 : 등록·수정·삭제·산정·발표를 admin_log 에 남긴다 (김예린 AdminLogService, DP-43)
*   - 작업과 같은 트랜잭션이라, 작업이 실패해 되돌려지면 기록도 남지 않는다
*  */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminHousingService {

    static final NotificationType NOTIFICATION_TYPE = NotificationType.HOUSING_RESULT;   // 알림 종류 enum (#289)
    static final String NOTIFICATION_LINK = "/user/demo-housing-subscription";      // 화면 설계 모의 청약 화면

    // admin_log (DB 설계 admin_log.action / target_type)
    static final String TARGET_TYPE = "HOUSING_COMPLEX";
    static final String ACTION_CREATE = "HOUSING_COMPLEX_CREATE";
    static final String ACTION_UPDATE = "HOUSING_COMPLEX_UPDATE";
    static final String ACTION_DELETE = "HOUSING_COMPLEX_DELETE";
    static final String ACTION_COMPUTE = "HOUSING_COMPUTE";
    static final String ACTION_ANNOUNCE = "HOUSING_ANNOUNCE";
    private static final ObjectMapper JSON = new ObjectMapper();

    private final HousingComplexRepository complexRepository;
    private final HousingApplicationRepository applicationRepository;
    private final Clock clock;
    private final HousingWinnerPolicy winnerPolicy;
    private final MemberService memberService;                 // 다른 도메인은 Service 로만 (역할분담 §7)
    private final NotificationService notificationService;
    private final AdminLogService adminLogService;

    // API 142 단지 목록 : 삭제 안 된 단지 + 신청자 수 (한 번에 센다)
    public List<AdminComplexDTO> findComplexes() {
        List<HousingComplex> complexes = complexRepository.findByDeletedAtIsNullOrderByApplyStartDateDesc();
        Map<Long, Long> applicants = countApplicants(complexes.stream().map(HousingComplex::getHousingComplexId).toList());
        LocalDate today = LocalDate.now(clock);
        return complexes.stream()
                .map(c -> AdminComplexDTO.from(c, today, applicants.getOrDefault(c.getHousingComplexId(), 0L)))
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
        Long complexId = complexRepository.save(complex).getHousingComplexId();
        adminLogService.record(adminId, ACTION_CREATE, TARGET_TYPE, complexId, toJson(Map.of("after", snapshot(complex))));
        return complexId;
    }

    // API 144 공고 수정 : 신청자가 있으면 일정·공급 세대 수를 바꾸는 수정은 거절, 정보만 바꾸는 수정은 허용 (SP-76)
    @Transactional
    public void updateComplex(Long adminId, Long complexId, ComplexRequestDTO request) {
        checkInput(request);
        HousingComplex complex = getComplex(complexId);
        Map<String, Object> before = snapshot(complex);

        boolean scheduleChanged = !complex.hasSameSchedule(request.getNoticeDate(), request.getApplyStartDate(),
                request.getApplyEndDate(), request.getAnnounceDate(), request.getSupplyUnits());
        // 산정한 공고는 신청자가 없어도 일정·세대 수를 바꿀 수 없다 (#48 SP-75)
        //  → 마감일을 미래로 옮겨 신청을 다시 받으면, 결과·순위가 없는 신청자가 생기고 재산정도 막혀 있기 때문
        if (scheduleChanged && complex.isComputed()) {
            throw new BusinessException(HousingErrorCode.ALREADY_COMPUTED);
        }
        if (scheduleChanged && hasApplicants(complexId)) {
            throw new BusinessException(HousingErrorCode.COMPLEX_HAS_APPLICANTS);
        }

        complex.changeInfo(request.getName(), request.getLocation(), request.getAreaType(),
                request.getSalePrice(), request.getSupplyType());
        complex.changeSchedule(request.getNoticeDate(), request.getApplyStartDate(), request.getApplyEndDate(),
                request.getAnnounceDate(), request.getSupplyUnits());
        // save 를 부르지 않아도 트랜잭션이 끝날 때 변경 감지로 저장된다
        adminLogService.record(adminId, ACTION_UPDATE, TARGET_TYPE, complexId,
                toJson(Map.of("before", before, "after", snapshot(complex))));
    }

    // API 145 공고 삭제 : 신청자가 없을 때만, Soft Delete
    @Transactional
    public void deleteComplex(Long adminId, Long complexId) {
        HousingComplex complex = getComplex(complexId);
        if (hasApplicants(complexId)) {
            throw new BusinessException(HousingErrorCode.COMPLEX_HAS_APPLICANTS);
        }
        Map<String, Object> before = snapshot(complex);            // 삭제 전 값을 먼저 담아 둔다
        LocalDateTime now = LocalDateTime.now(clock);
        complex.delete(now);
        adminLogService.record(adminId, ACTION_DELETE, TARGET_TYPE, complexId,
                toJson(Map.of("before", before, "deletedAt", now.toString())));
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

    // API 146 신청자 목록 : 산정 전에는 가점 → 납입회차 → 신청 순, 산정 뒤에는 산정 순위 순
    public List<ApplicantDTO> findApplicants(Long complexId) {
        HousingComplex complex = complexRepository.findByHousingComplexIdAndDeletedAtIsNull(complexId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        List<HousingApplication> applications = applicationRepository.findByHousingComplexId(complexId);
        Map<Long, String> nicknames = memberService.findNicknames(
                applications.stream().map(HousingApplication::getMemberId).toList());

        Comparator<HousingApplication> order = complex.isComputed()
                ? Comparator.comparing(HousingApplication::getResultRank, Comparator.nullsLast(Comparator.naturalOrder()))
                : Comparator.comparingInt(HousingApplication::getTotalScore).reversed()
                        .thenComparing(Comparator.comparingInt(HousingApplication::getPaidCountSnapshot).reversed())
                        .thenComparing(HousingApplication::getAppliedAt);
        return applications.stream()
                .sorted(order)
                .map(app -> ApplicantDTO.from(app, nicknames.get(app.getMemberId())))
                .toList();
    }

    // API 147 당첨자 산정 : 신청 마감 다음 날부터, 한 번만
    @Transactional
    public ComputeResultDTO compute(Long adminId, Long complexId) {
        HousingComplex complex = getComplex(complexId);
        if (!complex.isApplyClosed(LocalDate.now(clock))) {
            throw new BusinessException(HousingErrorCode.APPLY_NOT_CLOSED);
        }
        if (complex.isComputed()) {
            throw new BusinessException(HousingErrorCode.ALREADY_COMPUTED);
        }

        List<HousingApplication> applications = applicationRepository.findByHousingComplexId(complexId);
        int winners = winnerPolicy.rank(applications, complex.getSupplyUnits());
        LocalDateTime now = LocalDateTime.now(clock);
        complex.markComputed(now);
        // 신청마다 result·result_rank 가 바뀌었고, 트랜잭션이 끝날 때 변경 감지로 저장된다
        adminLogService.record(adminId, ACTION_COMPUTE, TARGET_TYPE, complexId,
                toJson(Map.of("applicants", applications.size(), "winners", winners,
                        "supplyUnits", complex.getSupplyUnits())));
        return new ComputeResultDTO(applications.size(), winners, now);
    }

    // API 148 결과 발표 : 산정한 뒤 발표일부터, 한 번만. 이때부터 회원에게 결과가 보인다 (#29 API 71)
    @Transactional
    public AnnounceResultDTO announce(Long adminId, Long complexId) {
        HousingComplex complex = getComplex(complexId);
        if (complex.isAnnounced()) {
            throw new BusinessException(HousingErrorCode.ALREADY_ANNOUNCED);
        }
        if (!complex.isComputed()) {
            throw new BusinessException(HousingErrorCode.NOT_COMPUTED);
        }
        if (complex.isBeforeAnnounceDate(LocalDate.now(clock))) {
            throw new BusinessException(HousingErrorCode.BEFORE_ANNOUNCE_DATE);
        }

        LocalDateTime now = LocalDateTime.now(clock);
        complex.announce(now);
        List<HousingApplication> applications = applicationRepository.findByHousingComplexId(complexId);
        String title = resultTitle(complex.getName());
        applications.forEach(app ->
                notificationService.send(app.getMemberId(), NOTIFICATION_TYPE, title, NOTIFICATION_LINK));
        adminLogService.record(adminId, ACTION_ANNOUNCE, TARGET_TYPE, complexId,
                toJson(Map.of("notified", applications.size())));
        return new AnnounceResultDTO(now, applications.size());
    }

    // 기록용 단지 값 (날짜는 "2026-11-03" 글자로). 순서를 지키려고 LinkedHashMap
    private static Map<String, Object> snapshot(HousingComplex c) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("name", c.getName());
        values.put("location", c.getLocation());
        values.put("areaType", c.getAreaType());
        values.put("salePrice", c.getSalePrice());
        values.put("supplyUnits", c.getSupplyUnits());
        values.put("supplyType", c.getSupplyType());
        values.put("noticeDate", String.valueOf(c.getNoticeDate()));
        values.put("applyStartDate", String.valueOf(c.getApplyStartDate()));
        values.put("applyEndDate", String.valueOf(c.getApplyEndDate()));
        values.put("announceDate", String.valueOf(c.getAnnounceDate()));
        return values;
    }

    private static String toJson(Map<String, Object> detail) {
        try {
            return JSON.writeValueAsString(detail);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("관리자 작업 기록을 JSON 으로 바꾸지 못했습니다.", e);
        }
    }

    // 발표 알림 제목 "<단지 이름> 청약 결과가 발표됐어요"
    //  단지 이름은 100자까지인데 알림 제목도 100자까지라(NotificationService.TITLE_MAX_LENGTH), 넘치면 알림 저장이 실패해 발표 전체가 롤백된다.
    //  그래서 알림 제목에서만 이름을 줄이고 "…" 를 붙인다 (단지 이름 자체는 그대로)
    static String resultTitle(String complexName) {
        String suffix = " 청약 결과가 발표됐어요";
        int maxName = NotificationService.TITLE_MAX_LENGTH - suffix.length();
        if (complexName.length() <= maxName) {
            return complexName + suffix;
        }
        int cut = maxName - 1;                                      // "…" 한 글자 자리
        if (Character.isHighSurrogate(complexName.charAt(cut - 1))) {
            cut--;                                                  // 이모지 같은 두 칸짜리 글자를 반으로 자르지 않는다
        }
        return complexName.substring(0, cut) + "…" + suffix;
    }

    // 수정·삭제·산정·발표용 : 단지 행을 잠그고 가져온다 (없거나 삭제됐으면 404)
    private HousingComplex getComplex(Long complexId) {
        return complexRepository.findForUpdate(complexId)
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
