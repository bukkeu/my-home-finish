package com.wanted.moneybeetle.domain.auth.model.service;

import com.wanted.moneybeetle.domain.member.model.service.AccountService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/* comment.
*   담당 : DBA ② (회원·인증)
*   비밀번호 찾기 - 토큰 발급(10분, 1회용), 메일 발송, 재설정 (USR-09, DP-12, 이슈 #8)
*   - 토큰 원문은 SecureRandom 32바이트(URL 에 쓸 수 있는 Base64)이고, DB 에는 SHA-256 해시만 저장한다 (AccountService).
*   - 메일 본문의 링크: {app.base-url}/password-reset?token=토큰원문 (재설정 화면은 FE 담당)
*   - 가입되지 않은 이메일이어도 요청은 같은 응답(202)으로 끝내서 가입 여부를 알려 주지 않는다.
*   - 메일 발송이 실패해도 응답은 같고, 서버 로그에만 남긴다 (토큰·이메일은 로그에 남기지 않는다, DP-02).
*   - 이 클래스는 트랜잭션을 열지 않는다. 토큰 저장은 AccountService 가 열고 끝낸 뒤 메일을 보낸다 (메일 서버가 느려도 DB 를 붙잡지 않는다).
*  */
@Slf4j
@Service
public class PasswordResetService {

    private static final int TOKEN_BYTES = 32;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final AccountService accountService;
    private final JavaMailSender mailSender;
    private final String baseUrl;
    private final String mailFrom;

    public PasswordResetService(AccountService accountService,
                                JavaMailSender mailSender,
                                @Value("${app.base-url}") String baseUrl,
                                @Value("${app.mail-from}") String mailFrom) {
        this.accountService = accountService;
        this.mailSender = mailSender;
        this.baseUrl = baseUrl;
        this.mailFrom = mailFrom;
    }

    /** 재설정 메일을 보낸다 (API 10). 가입된 정상 회원일 때만 토큰을 저장하고 메일을 보낸다. 어느 경우든 호출한 쪽에는 아무것도 알리지 않는다 */
    public void requestReset(String email) {
        String token = newToken();
        Optional<String> recipient = accountService.issuePasswordResetToken(email, hash(token));
        recipient.ifPresent(to -> send(to, token));
    }

    /** 메일의 토큰으로 새 비밀번호를 정한다 (API 11) */
    public void confirmReset(String token, String newPassword, String newPasswordConfirm) {
        accountService.resetPassword(hash(token == null ? "" : token), newPassword, newPasswordConfirm);
    }

    private void send(String to, String token) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailFrom);
        message.setTo(to);
        message.setSubject("[머니비틀] 비밀번호 재설정 안내");
        message.setText("아래 주소에서 10분 안에 새 비밀번호를 정해 주세요. 한 번만 사용할 수 있어요.\n\n"
                + baseUrl + "/password-reset?token=" + token + "\n\n"
                + "본인이 요청하지 않았다면 이 메일을 무시해 주세요.");
        try {
            mailSender.send(message);
        } catch (MailException e) {
            // 받는 사람·토큰이 로그에 남지 않도록 예외 종류만 남긴다
            log.error("비밀번호 재설정 메일 발송에 실패했어요: {}", e.getClass().getSimpleName());
        }
    }

    private static String newToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** 토큰 원문의 SHA-256 해시(16진수 64자). DB 에는 이 값만 저장한다 */
    static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 을 쓸 수 없어요.", e);
        }
    }
}
