package com.wanted.moneybeetle.domain.member.model.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wanted.moneybeetle.domain.member.exception.AdminMemberErrorCode;
import com.wanted.moneybeetle.domain.member.exception.MemberErrorCode;
import com.wanted.moneybeetle.domain.member.model.dao.AdminMemberRepository;
import com.wanted.moneybeetle.domain.member.model.dao.InstructorProfileRepository;
import com.wanted.moneybeetle.domain.member.model.dao.MemberRepository;
import com.wanted.moneybeetle.domain.member.model.dto.InstructorApplicationDTO;
import com.wanted.moneybeetle.domain.member.model.entity.InstructorProfile;
import com.wanted.moneybeetle.domain.member.model.entity.Member;
import com.wanted.moneybeetle.domain.operation.model.service.AdminLogService;
import com.wanted.moneybeetle.global.common.PageResponse;
import com.wanted.moneybeetle.global.exception.BusinessException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/* comment.
*   담당 : DBA ② (회원·인증) — 신청 목록·승인은 DBA ①가 #37 에서 추가 (DBA ② 확인)
*   강사 가입 신청, 승인·권한 회수 (ADM-06)
*   - API 104 강사 가입 신청 목록, API 105 강사 승인 (#37)
*   - API 12 강사 가입 신청은 본인인증(#210)이 들어온 뒤 #30 에서 이어서 더한다
*  */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InstructorService {

    private static final Set<String> APPLICATION_STATUSES = Set.of(
            InstructorProfile.PENDING, InstructorProfile.APPROVED, InstructorProfile.REVOKED);
    private static final String LOG_ACTION_APPROVE = "INSTRUCTOR_APPROVE";
    private static final String LOG_TARGET_MEMBER = "MEMBER";

    private final AdminMemberRepository adminMemberRepository;
    private final MemberRepository memberRepository;
    private final InstructorProfileRepository instructorProfileRepository;
    private final AdminLogService adminLogService;      // operation 도메인 - 관리자 작업 기록
    private final ObjectMapper objectMapper;            // admin_log detail JSON
    private final Clock clock;                          // Asia/Seoul 공통 시계

    /* API 104 강사 가입 신청 목록. status 가 없거나 모르는 값이면 승인 대기(PENDING) */
    public PageResponse<InstructorApplicationDTO> findApplications(String status, Pageable pageable) {
        String filter = status != null && APPLICATION_STATUSES.contains(status) ? status : InstructorProfile.PENDING;
        return PageResponse.from(adminMemberRepository.findApplications(filter, pageable));
    }

    /* API 105 강사 승인: 승인 대기(PENDING) 신청만. 강사 정보 APPROVED + 회원 역할 INSTRUCTOR
     * → 다음 로그인부터 INSTRUCTOR 권한 (MemberService.grantedRole, SP-07). 회원 행을 잠가 동시 처리와 겹치지 않게 한다 */
    @Transactional
    public void approve(Long adminId, Long memberId) {
        Member member = memberRepository.findByIdForUpdate(memberId)
                .orElseThrow(() -> new BusinessException(MemberErrorCode.MEMBER_NOT_FOUND));
        if (member.isWithdrawn() || Member.ROLE_ADMIN.equals(member.getRole())) {
            throw new BusinessException(AdminMemberErrorCode.MEMBER_NOT_CHANGEABLE);
        }
        InstructorProfile profile = instructorProfileRepository.findByMemberId(memberId)
                .orElseThrow(() -> new BusinessException(AdminMemberErrorCode.APPLICATION_NOT_FOUND));
        if (!profile.isPending()) {
            throw new BusinessException(AdminMemberErrorCode.APPLICATION_NOT_PENDING);
        }
        profile.approve(adminId, LocalDateTime.now(clock));
        member.changeRole(Member.ROLE_INSTRUCTOR);

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("before", InstructorProfile.PENDING);
        detail.put("after", InstructorProfile.APPROVED);
        detail.put("displayName", profile.getDisplayName());
        adminLogService.record(adminId, LOG_ACTION_APPROVE, LOG_TARGET_MEMBER, memberId, toJson(detail));
    }

    private String toJson(Map<String, Object> detail) {
        try {
            return objectMapper.writeValueAsString(detail);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("admin_log detail JSON 변환 실패", e);
        }
    }
}
