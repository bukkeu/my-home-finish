package com.wanted.moneybeetle.domain.course.model.service;

import com.wanted.moneybeetle.domain.course.model.dao.CategoryRepository;
import com.wanted.moneybeetle.domain.course.model.entity.Category;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Collectors;

/* comment.
*   담당 : DBA ① 신아림 (#296, #349)
*   카테고리 ID → "부동산 › 청약" 같은 표시 이름. 스크랩 목록·오답노트·통합 검색이 같이 쓴다
*   - 카테고리는 관리자만 가끔 바꾸므로 한 번 읽어 서버 메모리(snapshot)에 두고, 바뀌면 다시 읽는다
*   - 비활성 카테고리도 넣는다 (이미 그 카테고리에 있는 강의의 이름표는 계속 보여야 한다)
*
*   요청 처리 중에는 DB 연결을 더 요청하지 않는다 (#349)
*   - 요청은 메모리의 snapshot 만 읽는다. 다시 읽기는 요청 스레드가 아니라 전용 스레드(category-path-reload)가 하고,
*     그 스레드는 다른 연결을 쥐고 있지 않으므로 연결이 모자라도 서로 기다리며 멈추는 일이 없다
*   - 채우는 때 : 서버 시작 직후(ApplicationReadyEvent), 관리자 변경이 커밋된 뒤(markChanged)
*   - 서버가 막 떠서 아직 snapshot 이 없을 때만, 부른 쪽 트랜잭션이 이미 쥔 연결로 한 번 읽는다 (새 연결 없음).
*     그 값은 부른 쪽의 오래된 스냅샷일 수 있어서, 곧바로 전용 스레드로 한 번 더 읽어 바꾼다
*
*   옛 이름이 남지 않게 하는 방법 (버전)
*   - 관리자 변경이 **커밋된 뒤에** 버전을 1 올린다. 롤백되면 그대로
*   - 다시 읽기는 시작할 때 버전을 적고 새 트랜잭션에서 읽는다. 다 읽은 뒤 버전이 그대로일 때만 snapshot 을 바꾼다
*     (읽는 동안 또 바뀌었으면 버리고 다시 읽는다). 그래서 변경 전에 시작한 읽기가 새 이름을 덮지 않는다
*   - 다시 읽기가 끝나기 전 아주 잠깐은 바로 전 이름을 보여 준다 (요청을 멈추지 않기 위해서)
*  */
@Slf4j
@Service
public class CategoryPathService {

    private final CategoryRepository categoryRepository;
    private final TransactionTemplate freshReadTransaction;   // 다시 읽기 전용 (요청 스레드에서는 쓰지 않는다)
    private final Executor reloadExecutor;
    private final ExecutorService ownedExecutor;              // 이 빈이 만든 스레드면 종료할 때 닫는다
    private final AtomicLong revision = new AtomicLong();
    private final AtomicBoolean reloading = new AtomicBoolean();
    private volatile Snapshot snapshot;                       // 아직 한 번도 못 읽었으면 null

    private record Snapshot(long revision, Map<Long, String> paths) {
    }

    @Autowired
    public CategoryPathService(CategoryRepository categoryRepository, PlatformTransactionManager transactionManager) {
        this(categoryRepository, transactionManager, Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "category-path-reload");
            thread.setDaemon(true);
            return thread;
        }));
    }

    /* 테스트용 : 다시 읽기를 어느 스레드에서 할지 정한다 (예: Runnable::run 이면 부른 스레드에서 바로) */
    CategoryPathService(CategoryRepository categoryRepository, PlatformTransactionManager transactionManager,
                        Executor reloadExecutor) {
        this.categoryRepository = categoryRepository;
        this.freshReadTransaction = new TransactionTemplate(transactionManager);
        this.freshReadTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.freshReadTransaction.setReadOnly(true);
        this.reloadExecutor = reloadExecutor;
        this.ownedExecutor = reloadExecutor instanceof ExecutorService service ? service : null;
    }

    /* 서버 시작 직후 미리 채운다. 이 시점에는 요청이 쥔 연결이 없다 */
    @EventListener(ApplicationReadyEvent.class)
    public void warmUp() {
        try {
            reload();
        } catch (RuntimeException e) {
            log.warn("카테고리 이름을 미리 읽지 못했어요. 첫 요청 때 다시 읽어요", e);
        }
    }

    /* 전체 카테고리의 표시 이름 (바꿀 수 없는 Map). 요청 중에는 새 DB 연결을 요청하지 않는다 */
    public Map<Long, String> findCategoryPaths() {
        Snapshot current = snapshot;
        if (current == null) {
            long startedRevision = revision.get();
            Map<Long, String> paths = toPaths(categoryRepository.findAll());   // 부른 쪽 트랜잭션(이미 쥔 연결)으로 읽는다
            store(startedRevision, paths);
            scheduleReload();                                                  // 부른 쪽 스냅샷이 오래됐을 수 있어 한 번 더 읽는다
            return paths;
        }
        if (current.revision() != revision.get()) {
            scheduleReload();
        }
        return current.paths();
    }

    /* 카테고리를 바꾸는 트랜잭션 안에서 부른다 (AdminCategoryService). 커밋된 뒤에만 버전을 올리고 다시 읽기를 맡긴다.
     * 트랜잭션이 없으면 바로 올린다 */
    public void markChanged() {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            changed();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                changed();
            }
        });
    }

    private void changed() {
        revision.incrementAndGet();
        scheduleReload();
    }

    /* 다시 읽기를 전용 스레드에 맡긴다. 이미 읽는 중이면 그 읽기가 끝난 뒤 버전을 보고 다시 읽는다 */
    private void scheduleReload() {
        if (!reloading.compareAndSet(false, true)) {
            return;
        }
        try {
            reloadExecutor.execute(() -> {
                boolean stillOld = false;
                try {
                    stillOld = !reload();
                } catch (RuntimeException e) {
                    log.warn("카테고리 이름을 다시 읽지 못했어요. 다음 요청 때 다시 읽어요", e);
                } finally {
                    reloading.set(false);
                }
                if (stillOld) {
                    scheduleReload();   // 읽는 동안 또 바뀌었다
                }
            });
        } catch (RejectedExecutionException e) {
            reloading.set(false);
        }
    }

    /* 새 트랜잭션에서 읽어 snapshot 을 바꾼다. 읽는 동안 버전이 바뀌어 버렸으면 false */
    boolean reload() {
        long startedRevision = revision.get();
        Map<Long, String> paths = freshReadTransaction.execute(status -> toPaths(categoryRepository.findAll()));
        return store(startedRevision, paths);
    }

    /* 읽기를 시작할 때의 버전이 지금 버전과 같을 때만 바꾼다 */
    private synchronized boolean store(long startedRevision, Map<Long, String> paths) {
        if (startedRevision != revision.get()) {
            return false;
        }
        Snapshot current = snapshot;
        if (current == null || current.revision() <= startedRevision) {
            snapshot = new Snapshot(startedRevision, paths);
        }
        return true;
    }

    @PreDestroy
    void shutdown() {
        if (ownedExecutor != null) {
            ownedExecutor.shutdownNow();
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
