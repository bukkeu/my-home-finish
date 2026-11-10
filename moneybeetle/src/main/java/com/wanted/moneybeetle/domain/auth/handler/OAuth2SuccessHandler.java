package com.wanted.moneybeetle.domain.auth.handler;

import com.wanted.moneybeetle.domain.auth.model.dto.SocialProfile;
import com.wanted.moneybeetle.domain.auth.model.service.SocialLoginService;
import com.wanted.moneybeetle.domain.auth.model.service.SocialProfileExtractor;
import com.wanted.moneybeetle.domain.growth.model.service.AttendanceService;
import com.wanted.moneybeetle.domain.member.exception.MemberErrorCode;
import com.wanted.moneybeetle.domain.member.model.dto.LoginMemberDTO;
import com.wanted.moneybeetle.domain.member.model.entity.Member;
import com.wanted.moneybeetle.global.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

/* comment.
*   담당 : DBA ② (회원·인증)
*   간편 로그인 성공 처리 - 처음이면 추가 정보 입력 화면으로 (DP-13, 이슈 #9)
*   - 구글·카카오 인증이 끝난 뒤 불린다. 제공자 정보(이메일·이름)를 꺼내 SocialLoginService 로 로그인한다.
*   - 이미 가입한 사람: 이메일·비밀번호 로그인과 같은 로그인 상태(AuthDetails)를 만들고 역할별 첫 화면으로 보낸다. 일반 회원이면 오늘 출석도 기록한다.
*   - 처음 온 사람: 로그인 상태를 만들지 않고(가입 전) SIGNUP_URL 로 보낸다. 화면이 API 8-1·8-2 로 이어서 가입한다.
*   - 막힌 경우(이미 가입된 이메일, 탈퇴·정지 계정, 이메일 없음 등): 로그인 화면으로 보내며 오류 코드를 oauthError 로 알린다. 오류 코드만 알리고 제공자 정보는 싣지 않는다.
*   - 브라우저가 주소를 따라가는 흐름이라 이메일 로그인(API 6)처럼 JSON 으로 답하지 않고 이동(302)시킨다.
*  */
@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler implements AuthenticationSuccessHandler {

    /** 처음 온 사람이 이어서 가입 정보를 입력하는 화면 (FE #50) */
    public static final String SIGNUP_URL = "/signup/social";
    /** 간편 로그인이 막혔을 때 돌아가는 화면. ?oauthError=오류코드 */
    public static final String LOGIN_ERROR_URL = "/login?oauthError=";
    private final SocialLoginService socialLoginService;
    private final AuthSessionWriter sessionWriter;
    private final AttendanceService attendanceService;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        try {
            if (!(authentication instanceof OAuth2AuthenticationToken token)) {
                throw new BusinessException(MemberErrorCode.OAUTH_FAILED);
            }
            SocialProfile profile = SocialProfileExtractor.extract(
                    token.getAuthorizedClientRegistrationId(), token.getPrincipal().getAttributes());
            SocialLoginService.Result result = socialLoginService.login(profile, request.getSession());

            if (result.signupRequired()) {
                sessionWriter.clear(request, response);   // 가입하기 전이라 로그인 상태가 아니다
                response.sendRedirect(SIGNUP_URL);
                return;
            }
            LoginMemberDTO member = result.member();
            sessionWriter.signIn(request, response, member);
            recordAttendance(member);
            response.sendRedirect(AuthSuccessHandler.defaultHome(member.getGrantedRole()));
        } catch (BusinessException e) {
            redirectError(request, response, e.getErrorCode().getCode());
        } catch (RuntimeException e) {
            // 어떤 값이 문제였는지(이메일·토큰)는 남기지 않고 예외 종류만 남긴다
            log.warn("간편 로그인 처리에 실패했어요: {}", e.getClass().getSimpleName());
            redirectError(request, response, MemberErrorCode.OAUTH_FAILED.getCode());
        }
    }

    private void redirectError(HttpServletRequest request, HttpServletResponse response, String code) throws IOException {
        sessionWriter.clear(request, response);
        response.sendRedirect(LOGIN_ERROR_URL + code);
    }

    /** 일반 회원이면 오늘 출석을 기록한다. 실패해도 로그인은 성공이다 (AuthSuccessHandler 와 같은 규칙, GRW-01) */
    private void recordAttendance(LoginMemberDTO member) {
        if (!Member.ROLE_MEMBER.equals(member.getGrantedRole())) {
            return;
        }
        try {
            attendanceService.checkToday(member.getMemberId());
        } catch (RuntimeException e) {
            log.warn("간편 로그인 출석 기록에 실패했어요: memberId={}, {}", member.getMemberId(), e.getClass().getSimpleName());
        }
    }
}
