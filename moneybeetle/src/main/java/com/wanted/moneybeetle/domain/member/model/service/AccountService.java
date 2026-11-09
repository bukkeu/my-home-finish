package com.wanted.moneybeetle.domain.member.model.service;

import com.wanted.moneybeetle.domain.member.exception.MemberErrorCode;
import com.wanted.moneybeetle.domain.member.model.dao.MemberRepository;
import com.wanted.moneybeetle.domain.member.model.dao.PasswordResetTokenRepository;
import com.wanted.moneybeetle.domain.member.model.dto.MaskedEmailDTO;
import com.wanted.moneybeetle.domain.member.model.entity.Member;
import com.wanted.moneybeetle.domain.member.model.entity.PasswordResetToken;
import com.wanted.moneybeetle.domain.member.model.policy.SignupRules;
import com.wanted.moneybeetle.global.exception.BusinessException;
import com.wanted.moneybeetle.global.exception.CommonErrorCode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/* comment.
*   담당 : DBA ② (회원·인증)
*   계정 찾기·비밀번호·탈퇴 (이슈 #8, API 9~11·16·17)
*   - 아이디(이메일) 찾기, 비밀번호 재설정 토큰 발급·재설정, 비밀번호 변경, 회원 탈퇴(비활성화)
*   - 메일 발송과 토큰 원문 만들기는 auth.PasswordResetService 가 한다. 여기서는 토큰 해시만 받아 저장·확인한다.
*   - 시간은 공통 Clock(서울)으로 판단한다 (DP-42). 비밀번호는 BCrypt 로만 저장한다 (DP-02).
*   - 비밀번호를 바꾸거나 탈퇴할 때는 회원 행을 잠가서 같은 계정의 동시 요청이 차례로 처리되게 한다.
*  */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AccountService {

    private final MemberRepository memberRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    /**
     * 아이디(이메일) 찾기 (API 9). 닉네임과 생년월일이 모두 맞는 정상 회원의 이메일을 일부 가려서 돌려준다.
     * 맞는 회원이 없거나 정상 계정이 아니면 MEMBER_NOT_FOUND 다 (탈퇴·정지 회원의 이메일은 알려 주지 않는다).
     */
    public MaskedEmailDTO findMaskedEmail(String nickname, LocalDate birthDate) {
        Member member = memberRepository.findByNicknameAndBirthDate(nickname, birthDate)
                .filter(found -> found.isActive() && found.getEmail() != null)
                .orElseThrow(() -> new BusinessException(MemberErrorCode.MEMBER_NOT_FOUND));
        return new MaskedEmailDTO(maskEmail(member.getEmail()));
    }

    /**
     * 비밀번호 재설정 토큰을 저장한다 (API 10). 이메일이 가입된 정상 회원(비밀번호가 있는 계정)일 때만 저장하고 그 이메일을 돌려준다.
     * 없는 이메일이면 빈 값이다 - 호출한 쪽은 어느 경우든 같은 응답을 내서 가입 여부를 알려 주지 않는다.
     * 이 회원의 쓰지 않은 이전 토큰은 모두 쓸 수 없게 하고, 새 토큰은 10분 동안 유효하다 (DP-12).
     */
    @Transactional
    public Optional<String> issuePasswordResetToken(String email, String tokenHash) {
        String normalized = SignupRules.normalizeEmail(email);
        if (normalized == null || normalized.isBlank()) {
            return Optional.empty();
        }
        return memberRepository.findByEmail(normalized)
                .filter(member -> member.isActive() && member.getPasswordHash() != null)
                .map(member -> {
                    LocalDateTime now = LocalDateTime.now(clock);
                    passwordResetTokenRepository.save(PasswordResetToken.issue(member.getMemberId(), tokenHash, now));
                    return member.getEmail();
                });
    }

    /** 새 토큰의 메일이 발송 대기열에 들어간 뒤 부른다. 이 회원의 쓰지 않은 이전 토큰을 쓸 수 없게 한다 (새 토큰은 남긴다) */
    @Transactional
    public void invalidateOtherPasswordResetTokens(String tokenHash) {
        passwordResetTokenRepository.findByTokenHash(tokenHash).ifPresent(token ->
                passwordResetTokenRepository.invalidateUnusedExcept(token.getMemberId(), tokenHash, LocalDateTime.now(clock)));
    }

    /** 메일을 보내지 못했을 때 부른다. 새로 저장한 토큰을 지워서 이전 토큰이 그대로 유효하게 둔다 */
    @Transactional
    public void discardPasswordResetToken(String tokenHash) {
        passwordResetTokenRepository.deleteUnusedByTokenHash(tokenHash);
    }

    /**
     * 메일의 토큰으로 새 비밀번호를 정한다 (API 11). 새 비밀번호 확인 → 토큰(없음·만료 = TOKEN_EXPIRED, 사용함 = TOKEN_USED) 순서로 확인한다.
     * 토큰 행을 잠그고 읽어서 같은 토큰으로 동시에 두 번 재설정해도 한 번만 된다. 없는 토큰과 만료된 토큰은 구분하지 않는다.
     * 정상 계정이 아니면 TOKEN_EXPIRED 로 막는다.
     */
    @Transactional
    public void resetPassword(String tokenHash, String newPassword, String newPasswordConfirm) {
        validateNewPassword(newPassword, newPasswordConfirm);

        LocalDateTime now = LocalDateTime.now(clock);
        PasswordResetToken token = passwordResetTokenRepository.findByTokenHashForUpdate(tokenHash)
                .orElseThrow(() -> new BusinessException(MemberErrorCode.TOKEN_EXPIRED));
        if (token.isUsed()) {
            throw new BusinessException(MemberErrorCode.TOKEN_USED);
        }
        if (token.isExpired(now)) {
            throw new BusinessException(MemberErrorCode.TOKEN_EXPIRED);
        }

        Member member = memberRepository.findByIdForUpdate(token.getMemberId())
                .filter(Member::isActive)
                .orElseThrow(() -> new BusinessException(MemberErrorCode.TOKEN_EXPIRED));
        member.changePasswordHash(passwordEncoder.encode(newPassword));
        token.markUsed(now);
    }

    /** 비밀번호 변경 (API 16). 현재 비밀번호 확인(PASSWORD_WRONG) → 새 비밀번호 확인(PASSWORD_INVALID, PASSWORD_MISMATCH) 순서다 */
    @Transactional
    public void changePassword(Long memberId, String currentPassword, String newPassword, String newPasswordConfirm) {
        Member member = getMemberForUpdate(memberId);
        checkCurrentPassword(member, currentPassword);
        validateNewPassword(newPassword, newPasswordConfirm);
        member.changePasswordHash(passwordEncoder.encode(newPassword));
    }

    /**
     * 회원 탈퇴 (API 17). 현재 비밀번호를 확인한 뒤 계정을 비활성화(WITHDRAWN)한다 (DP-06).
     * 개인정보는 30일 뒤 파기 배치가 지운다. 관리자 계정은 이 API 로 탈퇴할 수 없다.
     */
    @Transactional
    public void withdraw(Long memberId, String password) {
        Member member = getMemberForUpdate(memberId);
        if (Member.ROLE_ADMIN.equals(member.getRole())) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }
        checkCurrentPassword(member, password);
        member.withdraw(LocalDateTime.now(clock));
    }

    private Member getMemberForUpdate(Long memberId) {
        return memberRepository.findByIdForUpdate(memberId)
                .orElseThrow(() -> new BusinessException(MemberErrorCode.MEMBER_NOT_FOUND));
    }

    /** 현재 비밀번호가 맞는가. 비밀번호가 없는 계정(간편 로그인 전용)은 확인할 수 없어 맞지 않는 것으로 본다 */
    private void checkCurrentPassword(Member member, String password) {
        if (password == null || member.getPasswordHash() == null
                || !passwordEncoder.matches(password, member.getPasswordHash())) {
            throw new BusinessException(MemberErrorCode.PASSWORD_WRONG);
        }
    }

    private void validateNewPassword(String newPassword, String newPasswordConfirm) {
        if (!SignupRules.isValidPassword(newPassword)) {
            throw new BusinessException(MemberErrorCode.PASSWORD_INVALID);
        }
        if (!newPassword.equals(newPasswordConfirm)) {
            throw new BusinessException(MemberErrorCode.PASSWORD_MISMATCH);
        }
    }

    /** 이메일 앞부분을 2글자(짧으면 1글자)만 보이고 가린다: minji@gmail.com → mi***@gmail.com */
    static String maskEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 0) {
            return "***";
        }
        String local = email.substring(0, at);
        int visible = local.length() <= 2 ? 1 : 2;
        return local.substring(0, visible) + "***" + email.substring(at);
    }
}
