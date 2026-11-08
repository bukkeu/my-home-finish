package com.wanted.moneybeetle.global.security;

/* comment.
*   담당 : PM 이재성 (#65), 검토 : SCM 서수영
*   Idempotency-Key 처리 기록 보관소 (DP-37)
*   지금은 메모리 구현(InMemoryIdempotencyStore)만 쓴다. 서버를 여러 대 띄우거나
*   재시작 후에도 기록이 필요해지면 이 인터페이스로 DB 구현을 따로 만든다
*  */

import java.util.Optional;

public interface IdempotencyStore {

    /** 기록이 없으면 "처리 중"으로 남기고 true, 이미 있으면 false */
    boolean start(String key, String bodyHash);

    /** 보관 기간이 지나지 않은 기록 (없을 수 있음) */
    Optional<IdempotencyRecord> find(String key);

    /** 처리 성공 결과를 남긴다 */
    void complete(String key, int status, String contentType, byte[] body);

    /** 처리 실패 등으로 기록을 지운다 (같은 키로 다시 시도할 수 있게) */
    void remove(String key);
}
