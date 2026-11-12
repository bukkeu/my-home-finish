package com.wanted.moneybeetle.domain.member.model.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wanted.moneybeetle.domain.member.exception.AdminMemberErrorCode;
import com.wanted.moneybeetle.domain.member.exception.MemberErrorCode;
import com.wanted.moneybeetle.domain.member.model.dao.AdminMemberRepository;
import com.wanted.moneybeetle.domain.member.model.dao.InstructorProfileRepository;
import com.wanted.moneybeetle.domain.member.model.dao.MemberRepository;
import com.wanted.moneybeetle.domain.member.model.dto.AdminMemberItemDTO;
import com.wanted.moneybeetle.domain.member.model.entity.InstructorProfile;
import com.wanted.moneybeetle.domain.member.model.entity.Member;
import com.wanted.moneybeetle.domain.member.model.entity.MemberStatus;
import com.wanted.moneybeetle.domain.operation.model.service.AdminLogService;
import com.wanted.moneybeetle.global.common.PageResponse;
import com.wanted.moneybeetle.global.exception.BusinessException;
import com.wanted.moneybeetle.global.exception.CommonErrorCode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/* comment.
*   담당 : DBA ① (사용자·강사 관리, #37)
*   관리자 회원 관리: 사용자·강사 목록(API 101), 계정 정지·해제(API 102), 강사 권한 부여·회수(API 103) (ADM-06)
*   - MemberService(DBA ②)와 따로 둬서 회원가입·로그인 작업과 충돌하지 않게 한다 (DBA ② 확인)
*   - 관리자 계정·탈퇴 계정은 바꾸지 않는다 (MEMBER_NOT_CHANGEABLE). 바꾼 내용은 admin_log 에 남긴다
*   - 정지하면 로그인할 수 없고(AuthService), 이미 로그인한 세션도 다음 요청에서 끊긴다(ActiveMemberFilter)
*  */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminMemberService {

    private static final Set<String> ROLES = Set.of(Member.ROLE_MEMBER, Member.ROLE_INSTRUCTOR);
    private static final Set<String> STATUSES = Set.of(MemberStatus.ACTIVE.name(), MemberStatus.SUSPENDED.name(), MemberStatus.WITHDRAWN.name());
    private static final int KEYWORD_MAX_LENGTH = 100;
    private static final String LOG_ACTION_SUSPEND = "MEMBER_SUSPEND";
    private static final String LOG_ACTION_ACTIVATE = "MEMBER_ACTIVATE";
    private static final String LOG_ACTION_ROLE = "MEMBER_ROLE_CHANGE";
    private static final String LOG_TARGET_MEMBER = "MEMBER";

    private final AdminMemberRepository adminMemberRepository;
    private final MemberRepository memberRepository;
    private final InstructorProfileRepository instructorProfileRepository;
    private final AdminLogService adminLogService;      // operation 도메인 - 관리자 작업 기록
    private final ObjectMapper objectMapper;            // admin_log detail JSON
    private final Clock clock;                          // Asia/Seoul 공통 시계

    /* API 101 사용자·강사 목록. 모르는 role·status 값은 필터 없이, 키워드는 앞뒤 공백 제거 */
    public PageResponse<AdminMemberItemDTO> findMembers(String role, String status, String keyword, Pageable pageable) {
        String roleFilter = role != null && ROLES.contains(role) ? role : null;
        String statusFilter = status != null && STATUSES.contains(status) ? status : null;
        String keywordFilter = keyword == null || keyword.isBlank() ? null : keyword.strip();
        if (keywordFilter != null && keywordFilter.length() > KEYWORD_MAX_LENGTH) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
        return PageResponse.from(adminMemberRepository.search(roleFilter, statusFilter, escapeLike(keywordFilter), pageable)
                .map(AdminMemberItemDTO::from));
    }

    /* API 102 계정 정지·해제 (SP-06). 정지할 때 사유 필수. 지금과 같은 상태면 아무것도 바꾸지 않는다.
     * 바꿀 수 있는지·어떻게 바꾸는지는 Member.changeStatus 가 정하고, 여기서는 "조회 → 상태 변경 → 바뀌었으면 관리자 로그"만 한다 (#295) */
    @Transactional
    public void changeStatus(Long adminId, Long memberId, String status, String reason) {
        MemberStatus target = toChangeableStatus(status);
        boolean suspending = target == MemberStatus.SUSPENDED;
        String trimmedReason = reason == null ? null : reason.strip();
        if (suspending && (trimmedReason == null || trimmedReason.isEmpty())) {
            throw new BusinessException(AdminMemberErrorCode.REASON_REQUIRED);
        }

        Member member = getChangeableMember(memberId);
        String before = member.getStatus();
        if (!member.changeStatus(target, trimmedReason)) {
            return;
        }

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("before", before);
        detail.put("after", target.name());
        if (suspending) {
            detail.put("reason", trimmedReason);
        }
        record(adminId, suspending ? LOG_ACTION_SUSPEND : LOG_ACTION_ACTIVATE, memberId, detail);
    }

    /* API 103 강사 권한 부여·회수 (SP-07). MEMBER ↔ INSTRUCTOR.
     * - INSTRUCTOR: 강사 정보가 있으면 승인, 없으면 닉네임을 표시 이름으로 만들어 승인 → 다음 로그인부터 강사 권한
     * - MEMBER: 강사 정보를 회수(REVOKED)로 바꾼다. 만든 강의는 그대로 남고 강사 화면·API만 막힌다
     * 지금과 같은 역할이면 아무것도 바꾸지 않는다 */
    @Transactional
    public void changeRole(Long adminId, Long memberId, String role) {
        if (!ROLES.contains(role)) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
        Member member = getChangeableMember(memberId);
        Optional<InstructorProfile> profile = instructorProfileRepository.findByMemberId(memberId);
        String before = member.getRole();
        boolean alreadyApproved = profile.map(InstructorProfile::isApproved).orElse(false);
        if (before.equals(role) && (Member.ROLE_MEMBER.equals(role) || alreadyApproved)) {
            return;
        }

        if (Member.ROLE_INSTRUCTOR.equals(role)) {
            InstructorProfile target = profile.orElseGet(() -> instructorProfileRepository.save(
                    InstructorProfile.create(memberId, member.getNickname(), null)));
            target.approve(adminId, LocalDateTime.now(clock));
        } else {
            profile.ifPresent(InstructorProfile::revoke);
        }
        member.changeRole(role);

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("before", before);
        detail.put("after", role);
        record(adminId, LOG_ACTION_ROLE, memberId, detail);
    }

    /* ===== 내부 도우미 ===== */

    /* LIKE 특수문자를 글자 그대로 찾게 한다 (쿼리의 escape '!'). 이스케이프 문자 '!'를 먼저 바꾼다 */
    private String escapeLike(String keyword) {
        if (keyword == null) {
            return null;
        }
        return keyword.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }

    /* 회원 행을 잠가 읽는다. 관리자·탈퇴 계정은 바꾸지 않는다 */
    /* API 102 요청 값: 관리자가 바꿀 수 있는 상태는 ACTIVE·SUSPENDED 뿐이다. 탈퇴(WITHDRAWN)·모르는 값은 INVALID_INPUT */
    private MemberStatus toChangeableStatus(String status) {
        if (status == null) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
        MemberStatus target;
        try {
            target = MemberStatus.from(status);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
        if (target == MemberStatus.WITHDRAWN) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
        return target;
    }

    private Member getChangeableMember(Long memberId) {
        Member member = memberRepository.findByIdForUpdate(memberId)
                .orElseThrow(() -> new BusinessException(MemberErrorCode.MEMBER_NOT_FOUND));
        if (Member.ROLE_ADMIN.equals(member.getRole()) || member.isWithdrawn()) {
            throw new BusinessException(AdminMemberErrorCode.MEMBER_NOT_CHANGEABLE);
        }
        return member;
    }

    private void record(Long adminId, String action, Long memberId, Map<String, Object> detail) {
        try {
            adminLogService.record(adminId, action, LOG_TARGET_MEMBER, memberId, objectMapper.writeValueAsString(detail));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("admin_log detail JSON 변환 실패", e);
        }
    }
}
