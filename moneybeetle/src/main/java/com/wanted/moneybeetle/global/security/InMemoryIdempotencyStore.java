package com.wanted.moneybeetle.global.security;

/* comment.
*   담당 : PM (중복 요청 방지, #65), 검토 : SCM
*   Idempotency-Key 기록을 메모리에 24시간 보관한다 (DP-37 초안 값)
*   - 보관 기간이 지난 기록은 새 기록을 남길 때 함께 지운다 (별도 예약 작업 없음)
*   - 서버를 재시작하면 기록이 사라진다. 한 대로 시연하는 지금 구성에서는 충분하다
*  */

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class InMemoryIdempotencyStore implements IdempotencyStore {

    public static final Duration TTL = Duration.ofHours(24);

    private final ConcurrentMap<String, IdempotencyRecord> records = new ConcurrentHashMap<>();
    private final Clock clock;

    public InMemoryIdempotencyStore(Clock clock) {
        this.clock = clock;
    }

    @Override
    public boolean start(String key, String bodyHash) {
        Instant now = clock.instant();
        removeExpired(now);
        return records.putIfAbsent(key, IdempotencyRecord.inProgress(bodyHash, now)) == null;
    }

    @Override
    public Optional<IdempotencyRecord> find(String key) {
        IdempotencyRecord record = records.get(key);
        if (record == null) {
            return Optional.empty();
        }
        if (record.isExpired(clock.instant(), TTL)) {
            records.remove(key, record);
            return Optional.empty();
        }
        return Optional.of(record);
    }

    @Override
    public void complete(String key, int status, String contentType, byte[] body) {
        records.computeIfPresent(key, (k, record) -> record.complete(status, contentType, body));
    }

    @Override
    public void remove(String key) {
        records.remove(key);
    }

    int size() {
        return records.size();
    }

    private void removeExpired(Instant now) {
        records.values().removeIf(record -> record.isExpired(now, TTL));
    }
}
