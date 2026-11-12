package com.wanted.moneybeetle.domain.member.model.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wanted.moneybeetle.domain.member.exception.MemberErrorCode;
import com.wanted.moneybeetle.domain.member.model.dao.InstructorProfileRepository;
import com.wanted.moneybeetle.domain.member.model.dto.InstructorProfileDTO;
import com.wanted.moneybeetle.domain.member.model.dto.InstructorProfileUpdateDTO;
import com.wanted.moneybeetle.domain.member.model.entity.InstructorProfile;
import com.wanted.moneybeetle.domain.operation.model.service.ForbiddenWordService;
import com.wanted.moneybeetle.global.exception.BusinessException;
import com.wanted.moneybeetle.global.exception.CommonErrorCode;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/* comment.
*   담당 : DBA ② (회원·인증)
*   InstructorProfileService (이슈 #365, API 12-1·12-2) - Repository 를 Mock 으로 두고 서버·DB 없이 돈다
*  */
class InstructorProfileServiceTest {

    private static final Long MEMBER = 7L;

    private InstructorProfileRepository repository;
    private ForbiddenWordService forbiddenWordService;
    private InstructorProfileService service;
    private InstructorProfile profile;

    @BeforeEach
    void setUp() {
        repository = mock(InstructorProfileRepository.class);
        forbiddenWordService = mock(ForbiddenWordService.class);
        service = new InstructorProfileService(repository, forbiddenWordService);
        profile = InstructorProfile.create(MEMBER, "박서연", "청약, ETF");     // 가입 때 직접 쓴 옛 형식의 분야
        when(repository.findByMemberId(MEMBER)).thenReturn(Optional.of(profile));
    }

    private InstructorProfileUpdateDTO request(String name, String headline, String bio, List<String> fields) {
        return new InstructorProfileUpdateDTO(name, headline, bio, fields);
    }

    @Test
    @DisplayName("조회: 내 강사 정보를 이름·한 줄 소개·강사 소개·분야 목록으로 돌려준다 (입력한 적 없는 소개는 null)")
    void getsMyProfile() {
        InstructorProfileDTO dto = service.getMyProfile(MEMBER);

        assertThat(dto.displayName()).isEqualTo("박서연");
        assertThat(dto.headline()).isNull();
        assertThat(dto.bio()).isNull();
        assertThat(dto.fields()).containsExactly("청약", "ETF");
    }

    @Test
    @DisplayName("조회: 강사 정보가 없는 회원이면 INSTRUCTOR_PROFILE_NOT_FOUND")
    void getFailsWithoutProfile() {
        when(repository.findByMemberId(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getMyProfile(99L))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(MemberErrorCode.INSTRUCTOR_PROFILE_NOT_FOUND));
    }

    @Test
    @DisplayName("수정: 공백을 다듬어 저장하고 분야는 쉼표로 이어 저장하며 저장된 값을 돌려준다")
    void updatesMyProfile() {
        InstructorProfileDTO dto = service.updateMyProfile(MEMBER,
                request("  박서연 강사  ", " 청약 가점, 한 번에 ", "10년차 공인중개사입니다.", List.of("청약", "부동산", "청약")));

        assertThat(profile.getDisplayName()).isEqualTo("박서연 강사");
        assertThat(profile.getHeadline()).isEqualTo("청약 가점, 한 번에");
        assertThat(profile.getBio()).isEqualTo("10년차 공인중개사입니다.");
        assertThat(profile.getField()).isEqualTo("청약,부동산");
        assertThat(dto.displayName()).isEqualTo("박서연 강사");
        assertThat(dto.fields()).containsExactly("청약", "부동산");
        verify(forbiddenWordService).check("박서연 강사");
    }

    @Test
    @DisplayName("수정: 한 줄 소개·강사 소개를 비워 보내면 지운다")
    void blankHeadlineAndBioAreCleared() {
        service.updateMyProfile(MEMBER, request("박서연", "소개", "긴 소개", List.of("청약")));

        InstructorProfileDTO dto = service.updateMyProfile(MEMBER, request("박서연", "  ", "", List.of("청약")));

        assertThat(dto.headline()).isNull();
        assertThat(dto.bio()).isNull();
        assertThat(profile.getHeadline()).isNull();
    }

    @Test
    @DisplayName("수정: 이름에 금칙어가 있으면 FORBIDDEN_WORD 이고 아무것도 바뀌지 않는다")
    void forbiddenNameIsRejected() {
        doThrow(new BusinessException(CommonErrorCode.FORBIDDEN_WORD)).when(forbiddenWordService).check("나쁜말");

        assertThatThrownBy(() -> service.updateMyProfile(MEMBER, request("나쁜말", "소개", "긴 소개", List.of("대출"))))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.FORBIDDEN_WORD));

        assertThat(profile.getDisplayName()).isEqualTo("박서연");
        assertThat(profile.getField()).isEqualTo("청약, ETF");
        assertThat(profile.getHeadline()).isNull();
    }

    @Test
    @DisplayName("수정: 강사 정보가 없으면 금칙어 검사도 하지 않고 INSTRUCTOR_PROFILE_NOT_FOUND")
    void updateFailsWithoutProfile() {
        when(repository.findByMemberId(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateMyProfile(99L, request("박서연", null, null, List.of("청약"))))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(MemberErrorCode.INSTRUCTOR_PROFILE_NOT_FOUND));
        verify(forbiddenWordService, never()).check(org.mockito.ArgumentMatchers.anyString());
    }
}
