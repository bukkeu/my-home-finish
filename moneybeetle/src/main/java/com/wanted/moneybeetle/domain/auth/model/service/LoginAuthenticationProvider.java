package com.wanted.moneybeetle.domain.auth.model.service;

import com.wanted.moneybeetle.domain.auth.model.dto.AuthDetails;
import com.wanted.moneybeetle.domain.member.exception.MemberErrorCode;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/* comment.
*   담당 : DBA ② (회원·인증)
*   로그인 한 번을 하나의 트랜잭션으로 묶는 인증 제공자 (이슈 #6, DP-10)
*   - 회원 행 잠금 → 정지·탈퇴·잠금 확인 → 비밀번호 확인 → 성공·실패 기록이 같은 트랜잭션 안에서 이어진다.
*     같은 계정의 동시 시도는 행 잠금에서 차례로 처리되므로, 이미 잠긴 계정이 병렬 요청 사이로 로그인에 성공하거나 실패 횟수가
*     덮어써지는 일이 없다. (잠금 확인과 기록을 따로 하면, 둘 사이에 끼어든 요청이 이미 지난 확인을 믿고 통과할 수 있다)
*   - 실제 확인은 DaoAuthenticationProvider(+ AuthService 의 UserDetailsService)가 하고, 여기서는 그 앞뒤를 트랜잭션으로 감싸 기록한다
*   - 인증에 실패해도 실패 기록은 저장(커밋)돼야 한다. 예외를 트랜잭션 안에서 던지면 롤백되므로, 결과를 값으로 돌려받아
*     트랜잭션을 끝낸 뒤에 던진다
*   - 이번 실패로 계정이 잠기면(5회째 등) 비밀번호 오류 대신 LockedException(LOGIN_LOCKED)으로 알린다
*  */
@Component
public class LoginAuthenticationProvider implements AuthenticationProvider {

    private final DaoAuthenticationProvider delegate;
    private final AuthService authService;
    private final TransactionTemplate transaction;

    public LoginAuthenticationProvider(AuthService authService, PasswordEncoder passwordEncoder,
                                       PlatformTransactionManager transactionManager) {
        this.authService = authService;
        this.delegate = new DaoAuthenticationProvider(authService);
        this.delegate.setPasswordEncoder(passwordEncoder);
        this.transaction = new TransactionTemplate(transactionManager);
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        Attempt attempt = transaction.execute(status -> attempt(authentication));
        if (attempt.failure() != null) {
            throw attempt.failure();
        }
        return attempt.result();
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }

    /** 트랜잭션 안에서 한 번 시도한다. 인증 실패도 예외로 던지지 않고 값으로 돌려줘서 실패 기록이 커밋되게 한다 */
    private Attempt attempt(Authentication authentication) {
        try {
            Authentication result = delegate.authenticate(authentication);
            authService.recordLoginSuccess(((AuthDetails) result.getPrincipal()).getMemberId());
            return new Attempt(result, null);
        } catch (BadCredentialsException e) {
            // 비밀번호가 틀렸거나 없는 이메일이다. 없는 이메일이면 기록할 회원이 없어 false 다
            boolean locked = authService.recordLoginFailure(authentication.getName());
            if (locked) {
                return new Attempt(null, new LockedException(MemberErrorCode.LOGIN_LOCKED.getCode(), e));
            }
            return new Attempt(null, e);
        } catch (AuthenticationException e) {
            // 정지·탈퇴·잠금(InternalAuthenticationServiceException 으로 감싸진 것 포함) 등. 기록할 것이 없다
            return new Attempt(null, e);
        }
    }

    private record Attempt(Authentication result, AuthenticationException failure) {
    }
}
