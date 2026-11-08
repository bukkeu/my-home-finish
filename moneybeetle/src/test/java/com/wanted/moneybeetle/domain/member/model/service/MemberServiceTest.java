package com.wanted.moneybeetle.domain.member.model.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wanted.moneybeetle.domain.member.model.dao.MemberRepository;
import com.wanted.moneybeetle.domain.member.model.dao.TermsRepository;
import com.wanted.moneybeetle.domain.member.model.dto.SignupDTO;
import com.wanted.moneybeetle.domain.member.model.dto.TermsDTO;
import com.wanted.moneybeetle.domain.member.model.entity.Terms;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/* comment.
*   담당 : DBA ② (회원·인증)
*   MemberService 1차 - Repository 를 Mock 으로 두고 서버·DB 없이 돈다
*  */
class MemberServiceTest {

    private MemberRepository memberRepository;
    private TermsRepository termsRepository;
    private MemberService memberService;

    @BeforeEach
    void setUp() {
        memberRepository = mock(MemberRepository.class);
        termsRepository = mock(TermsRepository.class);
        memberService = new MemberService(memberRepository, termsRepository);
    }

    @Test
    @DisplayName("이메일 중복 확인은 대소문자를 구분하지 않는다 (소문자로 바꿔 조회)")
    void emailCaseInsensitive() {
        when(memberRepository.existsByEmail("minji@gmail.com")).thenReturn(true);

        assertThat(memberService.isEmailAvailable("MinJi@Gmail.com").isAvailable()).isFalse();
        verify(memberRepository).existsByEmail("minji@gmail.com");
        assertThat(memberService.isEmailAvailable("new@gmail.com").isAvailable()).isTrue();
    }

    @Test
    @DisplayName("빈 이메일은 쓸 수 없다")
    void blankEmail() {
        assertThat(memberService.isEmailAvailable(" ").isAvailable()).isFalse();
        assertThat(memberService.isEmailAvailable(null).isAvailable()).isFalse();
    }

    @Test
    @DisplayName("형식이 틀리거나 100자를 넘는 이메일은 조회하지 않고 쓸 수 없다고 답한다")
    void invalidEmailIsNotAvailable() {
        String tooLong = "user@" + "d".repeat(60) + "." + "e".repeat(31) + ".com";   // 101자

        assertThat(tooLong).hasSize(101);
        assertThat(memberService.isEmailAvailable("not-an-email").isAvailable()).isFalse();
        assertThat(memberService.isEmailAvailable("minji@").isAvailable()).isFalse();
        assertThat(memberService.isEmailAvailable(tooLong).isAvailable()).isFalse();
        verify(memberRepository, never()).existsByEmail(anyString());
    }

    @Test
    @DisplayName("앞뒤 공백이 붙은 원본은 정규화하면 올바르더라도 쓸 수 없다 (가입 요청은 원본 그대로 검증하므로)")
    void paddedRawEmailIsNotAvailable() {
        assertThat(memberService.isEmailAvailable(" minji@gmail.com").isAvailable()).isFalse();
        assertThat(memberService.isEmailAvailable("minji@gmail.com ").isAvailable()).isFalse();
        verify(memberRepository, never()).existsByEmail(anyString());
    }

    @Test
    @DisplayName("100자 이하의 올바른 이메일은 중복만 본다")
    void maxLengthEmailChecksDuplicate() {
        String max = "user@" + "d".repeat(60) + "." + "e".repeat(30) + ".com";   // 정확히 100자

        assertThat(max).hasSize(100);
        assertThat(memberService.isEmailAvailable(max).isAvailable()).isTrue();
        verify(memberRepository).existsByEmail(max);
    }

    @Test
    @DisplayName("닉네임은 형식이 맞고 중복이 아닐 때만 쓸 수 있다")
    void nickname() {
        when(memberRepository.existsByNickname("민지")).thenReturn(true);

        assertThat(memberService.isNicknameAvailable("민지").isAvailable()).isFalse();   // 중복
        assertThat(memberService.isNicknameAvailable("새닉네임").isAvailable()).isTrue();
        assertThat(memberService.isNicknameAvailable("!").isAvailable()).isFalse();      // 형식 위반
    }

    @Test
    @DisplayName("약관 목록은 번호순으로 필수 여부와 함께 돌려준다")
    void agreements() {
        Terms t1 = Terms.create("서비스 이용약관", "본문1", true, "1.0", LocalDateTime.of(2026, 11, 1, 0, 0));
        Terms t3 = Terms.create("마케팅 정보 수신", "본문3", false, "1.0", LocalDateTime.of(2026, 11, 1, 0, 0));
        when(termsRepository.findAllByOrderByTermsIdAsc()).thenReturn(List.of(t1, t3));

        List<TermsDTO> result = memberService.findAgreements();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getTitle()).isEqualTo("서비스 이용약관");
        assertThat(result.get(0).isRequired()).isTrue();
        assertThat(result.get(1).isRequired()).isFalse();
    }

    @Test
    @DisplayName("signup 은 아직 구현 전이라 호출하면 구현 전임을 알린다 (#5 2차)")
    void signupNotYet() {
        assertThatThrownBy(() -> memberService.signup(new SignupDTO()))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
