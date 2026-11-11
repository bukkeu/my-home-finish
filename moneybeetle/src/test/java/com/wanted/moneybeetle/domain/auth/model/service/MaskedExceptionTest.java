package com.wanted.moneybeetle.domain.auth.model.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;

/* comment.
*   담당 : DBA ② (회원·인증)
*   MaskedException (이슈 #299, 점검표 C-4) - 로그에 남길 예외의 메시지에서 이메일 주소를 가리고, 클래스 이름·스택·원인은 그대로 두는지 확인한다
*  */
class MaskedExceptionTest {

    @Test
    @DisplayName("메시지의 이메일 주소를 가리고, 원래 예외의 클래스 이름은 메시지에 남긴다")
    void masksEmailAndKeepsClassName() {
        MailSendException original = new MailSendException("Invalid Addresses: <minji@gmail.com>, other.user+tag@sub.example.co.kr rejected");

        MaskedException masked = MaskedException.of(original);

        assertThat(masked.getMessage()).startsWith("org.springframework.mail.MailSendException: ")
                .doesNotContain("minji", "gmail", "other.user", "example.co.kr")
                .contains("Invalid Addresses: <***>, *** rejected");
    }

    @Test
    @DisplayName("원래 예외의 스택 트레이스를 그대로 가진다 (원인 위치를 찾을 수 있다)")
    void keepsStackTrace() {
        MailSendException original = new MailSendException("smtp down");

        MaskedException masked = MaskedException.of(original);

        assertThat(masked.getStackTrace()).isEqualTo(original.getStackTrace()).isNotEmpty();
    }

    @Test
    @DisplayName("원인(cause) 사슬도 같은 방식으로 복사하고, 원인의 메시지에 든 주소도 가린다")
    void copiesAndMasksCauseChain() {
        RuntimeException root = new IllegalStateException("550 5.1.1 <minji@gmail.com> user unknown");
        MailSendException original = new MailSendException("smtp down", root);

        MaskedException masked = MaskedException.of(original);

        assertThat(masked.getCause()).isInstanceOf(MaskedException.class);
        assertThat(masked.getCause().getMessage()).startsWith("java.lang.IllegalStateException: ")
                .doesNotContain("minji", "gmail").contains("<***>");
        assertThat(masked.getCause().getStackTrace()).isEqualTo(root.getStackTrace());
        assertThat(masked.getCause().getCause()).isNull();
    }

    @Test
    @DisplayName("메시지가 없는 예외도 다룬다")
    void nullMessage() {
        MaskedException masked = MaskedException.of(new IllegalStateException());

        assertThat(masked.getMessage()).isEqualTo("java.lang.IllegalStateException: ");
    }

    @Test
    @DisplayName("원인 사슬이 아주 길어도 정해진 단계까지만 복사한다 (무한 반복 방지)")
    void limitsDepth() {
        Throwable chain = new RuntimeException("level 0");
        for (int i = 1; i <= 12; i++) {
            chain = new RuntimeException("level " + i, chain);
        }

        MaskedException masked = MaskedException.of(chain);

        int depth = 0;
        for (Throwable t = masked; t.getCause() != null; t = t.getCause()) {
            depth++;
        }
        assertThat(depth).isLessThanOrEqualTo(6);
    }

    @Test
    @DisplayName("원인이 자기 자신인 예외도 무한 반복하지 않는다")
    void selfCause() {
        RuntimeException weird = new RuntimeException("self") {
            @Override
            public synchronized Throwable getCause() {
                return this;
            }
        };

        MaskedException masked = MaskedException.of(weird);

        assertThat(masked.getCause()).isNull();
    }
}
