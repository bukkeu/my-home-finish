package com.wanted.moneybeetle.domain.growth.model.dao;

import static org.assertj.core.api.Assertions.assertThat;

import com.wanted.moneybeetle.domain.growth.model.entity.CashHistory;
import com.wanted.moneybeetle.domain.growth.model.entity.ExpHistory;
import jakarta.persistence.LockModeType;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.parser.Part;
import org.springframework.data.repository.query.parser.PartTree;

/* comment.
*   담당 : DBA ② (성장)
*   이력 Repository 의 잠금 조회(findWithLockBy...)가 DB 없이도 잘못 만들어지지 않았는지 확인한다.
*   - 메서드 이름이 엔티티 필드(memberId, sourceType, sourceId)로 해석되는가
*   - 비관적 락(PESSIMISTIC_WRITE)이 붙어 있는가 (붙어 있지 않으면 일반 조회와 같아져 스냅샷을 볼 수 있다)
*  */
class HistoryRepositoryQueryNameTest {

    private static final String METHOD = "findWithLockByMemberIdAndSourceTypeAndSourceId";

    @Test
    @DisplayName("캐시·경험치 이력의 잠금 조회 이름이 memberId·sourceType·sourceId 세 조건으로 해석된다")
    void queryNameResolvesToThreeProperties() {
        assertThat(propertiesOf(METHOD, CashHistory.class)).containsExactly("memberId", "sourceType", "sourceId");
        assertThat(propertiesOf(METHOD, ExpHistory.class)).containsExactly("memberId", "sourceType", "sourceId");
    }

    @Test
    @DisplayName("잠금 조회에는 PESSIMISTIC_WRITE 가 붙어 있다")
    void lockingReadsAreLocked() throws NoSuchMethodException {
        assertThat(lockModeOf(CashHistoryRepository.class)).isEqualTo(LockModeType.PESSIMISTIC_WRITE);
        assertThat(lockModeOf(ExpHistoryRepository.class)).isEqualTo(LockModeType.PESSIMISTIC_WRITE);
    }

    private static List<String> propertiesOf(String methodName, Class<?> entity) {
        return new PartTree(methodName, entity).getParts().stream()
                .map(Part::getProperty)
                .map(p -> p.getSegment())
                .toList();
    }

    private static LockModeType lockModeOf(Class<?> repository) throws NoSuchMethodException {
        Method method = repository.getMethod(METHOD, Long.class, String.class, Long.class);
        return method.getAnnotation(Lock.class).value();
    }
}
