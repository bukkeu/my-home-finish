package com.wanted.moneybeetle.domain.member.model.service;

import com.wanted.moneybeetle.domain.housing.model.service.HousingService;
import com.wanted.moneybeetle.domain.member.model.dto.LoginMemberDTO;
import com.wanted.moneybeetle.domain.member.model.dto.SignupDTO;
import com.wanted.moneybeetle.domain.member.model.dto.SignupResultDTO;
import com.wanted.moneybeetle.domain.member.model.dto.SocialSignupDTO;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/* comment.
*   담당 : DBA ② (회원·인증)
*   회원가입 + 청약통장 자동 개설 (이슈 #5, SP-70 "가입과 동시에 개설")
*   - MemberService.signup 으로 회원을 만든 뒤, 같은 트랜잭션에서 housing.HousingService.openAccount 로 청약통장을 연다 (역할분담 §7 연동 약속).
*     통장 개설이 실패하면 회원 가입도 함께 되돌려져서, 통장 없는 회원이 생기지 않는다.
*   - MemberService 가 직접 HousingService 를 부르지 않는 이유: HousingService 가 RewardService 를 거쳐 MemberService 를 쓰므로,
*     MemberService 가 HousingService 를 가지면 서로 물려서 서버가 뜨지 않는다. 그래서 둘을 모두 아는 이 클래스가 이어 붙인다.
*   - 강사 가입(instructor-signup)은 학습자 통장이 필요 없어 이 경로를 쓰지 않는다.
*   - 가입 전에 이 세션에서 그 이메일의 본인인증(이메일 코드, 이슈 #9)을 마쳤는지 확인한다. 마치지 않았으면 IDENTITY_NOT_VERIFIED.
*     확인은 회원 저장 전에 하고, 인증 표시는 가입이 성공한 뒤에 지운다 (닉네임 중복 같은 오류로 가입이 실패해도 다시 인증하지 않게).
*  */
@Service
@RequiredArgsConstructor
public class MemberSignupService {

    private final MemberService memberService;
    private final HousingService housingService;
    private final SignupIdentityVerifier identityVerificationService;   // auth.IdentityVerificationService 가 구현 (member -> auth 직접 참조 없음)

    /** 회원가입 (API 4). 본인인증을 마친 이메일이어야 하고, 가입한 회원의 청약통장을 같은 트랜잭션에서 만든다 */
    @Transactional
    public SignupResultDTO signup(SignupDTO request, HttpSession session) {
        identityVerificationService.requireVerified(request.getEmail(), session);
        SignupResultDTO result = memberService.signup(request, true);
        housingService.openAccount(result.getMemberId());
        identityVerificationService.clear(session);
        return result;
    }

    /**
     * 간편 로그인(구글·카카오)으로 처음 온 사람의 가입 (API 8-2, 이슈 #9). 본인인증(이메일 코드)은 거치지 않는다:
     * 제공자가 확인해 준 이메일을 쓰기 때문이다 (DP-44). 회원·간편 로그인 연결과 청약통장을 같은 트랜잭션에서 만들고,
     * 로그인 처리에 쓸 회원 정보를 돌려준다.
     */
    @Transactional
    public LoginMemberDTO signupSocial(SocialSignupDTO request, String email, String provider, String providerUserId) {
        SignupResultDTO result = memberService.signupSocial(request, email, provider, providerUserId);
        housingService.openAccount(result.getMemberId());
        return memberService.findLoginMemberById(result.getMemberId())
                .orElseThrow(() -> new IllegalStateException("방금 가입한 회원을 찾을 수 없습니다."));
    }
}
