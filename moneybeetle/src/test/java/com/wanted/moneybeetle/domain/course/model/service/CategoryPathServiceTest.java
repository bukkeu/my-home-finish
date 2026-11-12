package com.wanted.moneybeetle.domain.course.model.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wanted.moneybeetle.domain.course.model.dao.CategoryRepository;
import com.wanted.moneybeetle.domain.course.model.dao.CourseRepository;
import com.wanted.moneybeetle.domain.course.model.entity.Category;
import com.zaxxer.hikari.HikariDataSource;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;

/* comment.
*   담당 : DBA ① 신아림 (#296, #349)
*   카테고리 표시 이름 snapshot : 이름 만들기, 메모리에서 읽기, 관리자 변경이 커밋된 뒤에만 다시 읽기, 롤백 무시,
*   읽는 동안 또 바뀌면 다시 읽기, 그리고 요청 중에는 DB 연결을 더 요청하지 않기(#349)
*   - 트랜잭션은 실제 관리자(H2 + 연결 풀)를 쓰고, 저장소는 가짜라 findAll 이 몇 번 불렸는지로 "DB 를 읽었는지" 본다
*   - 다시 읽기를 맡는 스레드는 테스트에서 직접 정한다 : 바로 실행(Runnable::run) 또는 모아 두고 나중에 실행(pending)
*  */
class CategoryPathServiceTest {

    private HikariDataSource dataSource;
    private TransactionTemplate tx;
    private CategoryRepository categoryRepository;
    private CourseRepository courseRepository;
    private Category realEstate;
    private List<Category> categories;
    private final List<Runnable> pending = new ArrayList<>();

    @BeforeEach
    void setUp() {
        dataSource = pool("category-path-" + System.nanoTime(), 4);
        tx = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        categoryRepository = mock(CategoryRepository.class);
        courseRepository = mock(CourseRepository.class);
        realEstate = category(Category.createCategory("부동산", 1), 1L);
        Category subscription = category(Category.createSubCategory(realEstate, "청약", 1), 11L);
        Category closed = category(Category.createSubCategory(realEstate, "경매", 2), 12L);
        closed.deactivate();
        categories = List.of(realEstate, subscription, closed);
        when(categoryRepository.findAll()).thenReturn(categories);
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(realEstate));
        when(courseRepository.existsByCategoryId(1L)).thenReturn(true);   // 강의가 있어 삭제 대신 비활성 처리
    }

    @AfterEach
    void tearDown() {
        dataSource.close();
    }

    private static HikariDataSource pool(String name, int size) {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl("jdbc:h2:mem:" + name + ";DB_CLOSE_DELAY=-1");
        ds.setUsername("sa");
        ds.setMaximumPoolSize(size);
        ds.setConnectionTimeout(250);   // 연결을 못 얻으면 0.25초 뒤 실패 → 멈춤을 테스트로 잡는다
        return ds;
    }

    private static Category category(Category category, long id) {
        ReflectionTestUtils.setField(category, "categoryId", id);
        return category;
    }

    /* 다시 읽기를 부른 스레드에서 바로 한다 */
    private CategoryPathService immediate() {
        return new CategoryPathService(categoryRepository, tx.getTransactionManager(), Runnable::run);
    }

    /* 다시 읽기를 모아 두기만 한다 (나중에 runPending 으로 실행) */
    private CategoryPathService deferred() {
        return new CategoryPathService(categoryRepository, tx.getTransactionManager(), pending::add);
    }

    private void runPending() {
        List<Runnable> tasks = new ArrayList<>(pending);
        pending.clear();
        tasks.forEach(Runnable::run);
    }

    private AdminCategoryService admin(CategoryPathService service) {
        return new AdminCategoryService(categoryRepository, courseRepository, service);
    }

    @Test
    @DisplayName("상위는 자기 이름, 소주제는 '상위 › 소주제'. 비활성 카테고리도 이름이 있고 값은 바꿀 수 없다")
    void buildsPaths() {
        CategoryPathService service = immediate();
        service.warmUp();

        Map<Long, String> paths = service.findCategoryPaths();

        assertThat(paths).containsEntry(1L, "부동산")
                .containsEntry(11L, "부동산 › 청약")
                .containsEntry(12L, "부동산 › 경매");
        assertThatThrownBy(() -> paths.put(99L, "x")).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("서버 시작 때 한 번 읽고, 그 뒤 요청은 메모리에서 꺼내 DB 를 읽지 않는다")
    void servedFromMemoryAfterWarmUp() {
        CategoryPathService service = immediate();
        service.warmUp();

        service.findCategoryPaths();
        service.findCategoryPaths();
        service.findCategoryPaths();

        verify(categoryRepository, times(1)).findAll();
    }

    @Test
    @DisplayName("관리자 변경이 커밋되기 전에는 그대로 쓰고, 커밋된 뒤에 다시 읽어 새 이름을 쓴다")
    void reloadsOnlyAfterCommit() {
        Category renamed = category(Category.createCategory("집", 1), 1L);
        when(categoryRepository.findAll()).thenReturn(categories, List.of(renamed));
        CategoryPathService service = immediate();
        service.warmUp();

        tx.executeWithoutResult(status -> {
            admin(service).delete(1L);
            assertThat(service.findCategoryPaths()).containsEntry(1L, "부동산");   // 아직 커밋 전 : 옛 이름
            verify(categoryRepository, times(1)).findAll();
        });

        verify(categoryRepository, times(2)).findAll();                          // 커밋 뒤 다시 읽음
        assertThat(service.findCategoryPaths()).containsEntry(1L, "집");          // 새 이름이 반영됨
    }

    @Test
    @DisplayName("다시 읽어 저장한 직후(아직 다시 읽는 중으로 표시된 때) 또 바뀌어도 놓치지 않고 한 번 더 읽는다")
    void changeRightAfterStoreIsNotMissed() {
        Category renamed = category(Category.createCategory("집", 1), 1L);
        when(categoryRepository.findAll()).thenReturn(categories, List.of(renamed));
        AtomicBoolean changeOnce = new AtomicBoolean();
        // 저장은 끝났지만 아직 reloading 이 풀리기 전에 관리자 변경이 커밋된 상황을 만든다
        CategoryPathService service = new CategoryPathService(categoryRepository, tx.getTransactionManager(), Runnable::run) {
            @Override
            boolean reload() {
                boolean stored = super.reload();
                if (changeOnce.compareAndSet(false, true)) {
                    markChanged();   // 이때는 다시 읽는 중이라 새로 예약되지 않는다
                }
                return stored;
            }
        };

        service.markChanged();

        verify(categoryRepository, times(2)).findAll();                  // 끝날 때 버전을 다시 보고 한 번 더 읽었다
        assertThat(service.findCategoryPaths()).containsEntry(1L, "집");
    }

    @Test
    @DisplayName("관리자 변경이 롤백되면 다시 읽지 않는다")
    void keepsSnapshotOnRollback() {
        CategoryPathService service = immediate();
        service.warmUp();

        tx.executeWithoutResult(status -> {
            admin(service).delete(1L);
            status.setRollbackOnly();
        });
        service.findCategoryPaths();

        verify(categoryRepository, times(1)).findAll();
    }

    @Test
    @DisplayName("다시 읽는 동안 관리자 변경이 또 커밋되면, 그 읽기 결과는 버리고 한 번 더 읽어 새 값을 쓴다")
    void reloadOvertakenByChangeReadsAgain() {
        CategoryPathService service = immediate();
        service.warmUp();
        Category renamed = category(Category.createCategory("집", 1), 1L);
        AtomicInteger reads = new AtomicInteger();
        when(categoryRepository.findAll()).thenAnswer(invocation -> {
            if (reads.incrementAndGet() == 1) {
                service.markChanged();   // 이 읽기 도중 다른 변경이 커밋됐다 (트랜잭션 밖이라 바로 반영)
                return categories;       // 옛 값
            }
            return List.of(renamed);
        });

        service.markChanged();

        assertThat(service.findCategoryPaths()).containsEntry(1L, "집");
        assertThat(reads.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("다시 읽기가 끝나기 전에도 요청은 기다리지 않고 바로 전 이름을 돌려준다")
    void requestDoesNotWaitForReload() {
        CategoryPathService service = deferred();
        service.reload();
        service.markChanged();   // 다시 읽기는 아직 실행되지 않았다

        assertThat(service.findCategoryPaths()).containsEntry(11L, "부동산 › 청약");
        verify(categoryRepository, times(1)).findAll();

        runPending();
        verify(categoryRepository, times(2)).findAll();
    }

    @Test
    @DisplayName("연결 풀이 1개뿐이고 바깥 트랜잭션이 그 연결을 쥐고 있어도, 처음 조회(snapshot 없음)가 멈추지 않는다")
    void noExtraConnectionInsideOuterTransaction() {
        HikariDataSource single = pool("category-path-single-" + System.nanoTime(), 1);
        try {
            TransactionTemplate outer = new TransactionTemplate(new DataSourceTransactionManager(single));
            CategoryPathService service = new CategoryPathService(categoryRepository, outer.getTransactionManager(),
                    pending::add);

            Map<Long, String> paths = assertTimeoutPreemptively(Duration.ofSeconds(3),
                    () -> outer.execute(status -> service.findCategoryPaths()));   // 새 연결을 기다리면 0.25초 뒤 실패한다

            assertThat(paths).containsEntry(11L, "부동산 › 청약");
            runPending();   // 바깥 트랜잭션이 끝난 뒤 전용 스레드 몫의 다시 읽기는 연결을 얻는다
            verify(categoryRepository, times(2)).findAll();
        } finally {
            single.close();
        }
    }
}
