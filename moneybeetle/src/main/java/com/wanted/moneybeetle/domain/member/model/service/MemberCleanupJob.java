package com.wanted.moneybeetle.domain.member.model.service;

import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/* comment.
*   담당 : DBA ② (회원·인증)
*   매일 한 번 도는 정리 작업 (이슈 #66, DB설계 3-2·3-3)
*   1) 탈퇴한 지 30일이 지난 회원의 개인정보를 지운다 (DP-06): 이메일·비밀번호 해시·생년월일·성별·프로필 이미지 NULL, 닉네임 "탈퇴한 사용자#ID"
*   2) 만료된 비밀번호 재설정 토큰을 지운다 (DP-12)
*   - 시각: 매일 03:00 (서울). 접속이 적은 새벽으로 정했고, 설정 app.cleanup.cron 으로 바꿀 수 있다.
*   - 두 작업은 서로 막지 않는다. 한쪽이 실패해도 로그만 남기고 다른 쪽은 계속한다 (다음 날 다시 시도한다).
*   - 회원은 BATCH_SIZE 명씩 끊어 처리한다. 묶음마다 트랜잭션이 따로라서 중간에 실패해도 앞서 끝난 묶음은 남는다.
*   - 로그에는 지운 개수만 남기고 회원 정보는 남기지 않는다.
*   - 소셜 연결(social_account) 삭제는 간편 로그인(#9)으로 그 Entity 가 생긴 뒤에 이 작업에 더한다.
*  */
@Slf4j
@Component
@RequiredArgsConstructor
public class MemberCleanupJob {

    static final int BATCH_SIZE = 100;
    /** 정상이라면 닿지 않는 안전장치 (100명 x 1,000번 = 10만 명). 처리가 줄지 않는 버그가 있어도 끝없이 돌지 않게 한다 */
    static final int MAX_BATCHES = 1_000;

    private final MemberService memberService;
    private final AccountService accountService;
    private final Clock clock;

    @Scheduled(cron = "${app.cleanup.cron:0 0 3 * * *}", zone = "Asia/Seoul")
    public void runDaily() {
        purgeWithdrawnMembers();
        deleteExpiredTokens();
    }

    void purgeWithdrawnMembers() {
        try {
            LocalDateTime now = LocalDateTime.now(clock);
            int total = 0;
            for (int i = 0; i < MAX_BATCHES; i++) {
                int purged = memberService.purgeWithdrawnMembers(now, BATCH_SIZE);
                total += purged;
                if (purged < BATCH_SIZE) {
                    break;
                }
            }
            log.info("탈퇴 회원 개인정보 정리: {}명", total);
        } catch (RuntimeException e) {
            log.error("탈퇴 회원 개인정보 정리에 실패했어요. 다음 실행에 다시 시도해요.", e);
        }
    }

    void deleteExpiredTokens() {
        try {
            int deleted = accountService.deleteExpiredPasswordResetTokens(LocalDateTime.now(clock));
            log.info("만료된 비밀번호 재설정 토큰 삭제: {}건", deleted);
        } catch (RuntimeException e) {
            log.error("만료된 비밀번호 재설정 토큰 삭제에 실패했어요. 다음 실행에 다시 시도해요.", e);
        }
    }
}
