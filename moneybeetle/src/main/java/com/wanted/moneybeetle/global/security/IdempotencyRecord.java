package com.wanted.moneybeetle.global.security;

/* comment.
*   담당 : PM (중복 요청 방지, #65), 검토 : SCM
*   Idempotency-Key 한 건의 처리 기록 : 요청 내용 해시, 처리 상태, 처음 응답(상태 코드·본문)
*  */

import java.time.Duration;
import java.time.Instant;

public record IdempotencyRecord(String bodyHash, boolean completed, int status,
        String contentType, byte[] body, Instant createdAt) {

    static IdempotencyRecord inProgress(String bodyHash, Instant createdAt) {
        return new IdempotencyRecord(bodyHash, false, 0, null, new byte[0], createdAt);
    }

    IdempotencyRecord complete(int status, String contentType, byte[] body) {
        return new IdempotencyRecord(bodyHash, true, status, contentType, body.clone(), createdAt);
    }

    public boolean isSameBody(String otherHash) {
        return bodyHash.equals(otherHash);
    }

    public boolean isInProgress() {
        return !completed;
    }

    public boolean isExpired(Instant now, Duration ttl) {
        return !createdAt.plus(ttl).isAfter(now);
    }

    @Override
    public byte[] body() {
        return body.clone();
    }
}
