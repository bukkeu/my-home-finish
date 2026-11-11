package com.wanted.moneybeetle.domain.course.model.service;

import com.wanted.moneybeetle.domain.course.model.dao.CategoryRepository;
import com.wanted.moneybeetle.domain.course.model.entity.Category;
import com.wanted.moneybeetle.global.config.CacheConfig;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Collectors;

/* comment.
*   담당 : DBA ① 신아림 (#296)
*   카테고리 ID → "부동산 › 청약" 같은 표시 이름. 스크랩 목록·오답노트·통합 검색이 같이 쓴다
*   - 카테고리는 관리자만 가끔 바꾸므로 한 번 읽어 캐시에 두고, 바뀌면 다시 읽는다
*   - @Cacheable 은 다른 빈에서 부를 때만 동작해서 (같은 클래스 안 호출은 프록시를 거치지 않음) CourseService 와 나눴다
*   - 비활성 카테고리도 넣는다 (이미 그 카테고리에 있는 강의의 이름표는 계속 보여야 한다)
*
*   옛 이름이 캐시에 남지 않게 하는 방법 (버전 키)
*   - 캐시 키는 "카테고리 버전" 이다. 관리자 변경이 **커밋된 뒤에** 버전을 1 올린다 (markChanged). 롤백되면 그대로
*   - 조회는 시작할 때 버전을 키로 정한다. 변경 전에 옛 데이터를 읽던 조회가 늦게 끝나도 옛 버전 키에 넣으므로,
*     새 버전 키를 쓰는 다음 조회는 그 값을 보지 않고 새로 읽는다
*   - 캐시가 없을 때 읽는 쿼리는 새 읽기 전용 트랜잭션에서 실행한다. 부른 쪽 트랜잭션이 커밋 전에 시작한 스냅샷(MySQL 반복 읽기)으로
*     읽으면, 새 버전 키에 옛 데이터가 들어갈 수 있기 때문이다 (캐시가 있으면 쿼리도 트랜잭션도 없다)
*  */
@Service
public class CategoryPathService {

    private final CategoryRepository categoryRepository;
    private final CacheManager cacheManager;
    private final TransactionTemplate freshReadTransaction;
    private final AtomicLong revision = new AtomicLong();

    public CategoryPathService(CategoryRepository categoryRepository, CacheManager cacheManager,
                               PlatformTransactionManager transactionManager) {
        this.categoryRepository = categoryRepository;
        this.cacheManager = cacheManager;
        this.freshReadTransaction = new TransactionTemplate(transactionManager);
        this.freshReadTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.freshReadTransaction.setReadOnly(true);
    }

    /* 지금 카테고리 버전 (캐시 키) */
    public long currentRevision() {
        return revision.get();
    }

    /* 전체 카테고리의 표시 이름. 캐시에 들어가므로 바꿀 수 없는 Map 으로 돌려준다 */
    @Cacheable(cacheNames = CacheConfig.CATEGORY_PATHS, key = "#root.target.currentRevision()")
    public Map<Long, String> findCategoryPaths() {
        return freshReadTransaction.execute(status -> toPaths(categoryRepository.findAll()));
    }

    /* 카테고리를 바꾸는 트랜잭션 안에서 부른다 (AdminCategoryService). 커밋된 뒤에만 버전을 올리고 옛 캐시를 비운다.
     * 트랜잭션이 없으면 바로 올린다 */
    public void markChanged() {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            bumpRevision();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                bumpRevision();
            }
        });
    }

    private void bumpRevision() {
        revision.incrementAndGet();
        Cache cache = cacheManager.getCache(CacheConfig.CATEGORY_PATHS);
        if (cache != null) {
            cache.invalidate();   // 옛 버전 키 값은 더 쓰지 않으니 메모리에서 지운다
        }
    }

    /* 상위 › 소주제 이름을 만든다. 상위가 없으면 자기 이름만 */
    static Map<Long, String> toPaths(List<Category> categories) {
        Map<Long, Category> byId = categories.stream()
                .collect(Collectors.toMap(Category::getCategoryId, Function.identity()));
        Map<Long, String> paths = new HashMap<>();
        for (Category category : categories) {
            Category parent = category.isSubCategory() ? byId.get(category.getParentId()) : null;
            paths.put(category.getCategoryId(),
                    parent != null ? parent.getName() + " › " + category.getName() : category.getName());
        }
        return Map.copyOf(paths);
    }
}
