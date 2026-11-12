package com.wanted.moneybeetle.domain.member.model.service;

import com.wanted.moneybeetle.domain.member.exception.MemberErrorCode;
import com.wanted.moneybeetle.domain.member.model.dao.InstructorProfileRepository;
import com.wanted.moneybeetle.domain.member.model.dto.InstructorProfileDTO;
import com.wanted.moneybeetle.domain.member.model.dto.InstructorProfileUpdateDTO;
import com.wanted.moneybeetle.domain.member.model.entity.InstructorProfile;
import com.wanted.moneybeetle.domain.member.model.policy.InstructorProfileRules;
import com.wanted.moneybeetle.domain.operation.model.service.ForbiddenWordService;
import com.wanted.moneybeetle.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/* comment.
*   담당 : DBA ② (회원·인증)
*   강사 본인 프로필 조회·수정 (이슈 #365, API 12-1·12-2)
*   - 로그인한 강사 본인의 instructor_profile 만 읽고 고친다 (회원 번호는 세션에서 꺼내 컨트롤러가 넘긴다)
*   - 이름에만 금칙어 검사를 한다 (FORBIDDEN_WORD). 닉네임은 건드리지 않는다 (API 14)
*   - 길이·고를 수 있는 분야는 요청 DTO 가 이미 검사했다. 값 저장은 Entity 의 changeProfile 이 한다 (변경 감지)
*  */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InstructorProfileService {

    private final InstructorProfileRepository instructorProfileRepository;
    private final ForbiddenWordService forbiddenWordService;    // operation 도메인 - 금칙어

    /** API 12-1 내 강사 프로필 */
    public InstructorProfileDTO getMyProfile(Long memberId) {
        return InstructorProfileDTO.from(getProfile(memberId));
    }

    /** API 12-2 내 강사 프로필 수정. 한 줄 소개·강사 소개를 비워 보내면 지운다 */
    @Transactional
    public InstructorProfileDTO updateMyProfile(Long memberId, InstructorProfileUpdateDTO request) {
        InstructorProfile profile = getProfile(memberId);
        String displayName = request.getDisplayName().strip();
        forbiddenWordService.check(displayName);
        profile.changeProfile(displayName, blankToNull(request.getHeadline()), blankToNull(request.getBio()),
                InstructorProfileRules.joinFields(request.getFields()));
        return InstructorProfileDTO.from(profile);
    }

    private InstructorProfile getProfile(Long memberId) {
        return instructorProfileRepository.findByMemberId(memberId)
                .orElseThrow(() -> new BusinessException(MemberErrorCode.INSTRUCTOR_PROFILE_NOT_FOUND));
    }

    private String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text.strip();
    }
}
