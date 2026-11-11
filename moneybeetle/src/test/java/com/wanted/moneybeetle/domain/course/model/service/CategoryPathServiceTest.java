package com.wanted.moneybeetle.domain.course.model.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wanted.moneybeetle.domain.course.model.dao.CategoryRepository;
import com.wanted.moneybeetle.domain.course.model.dao.CourseRepository;
import com.wanted.moneybeetle.domain.course.model.entity.Category;
import com.wanted.moneybeetle.global.config.CacheConfig;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

/* comment.
*   담당 : DBA ① 신아림 (#296)
*   카테고리 표시 이름 캐시: 이름 만들기, 두 번째부터는 DB 를 읽지 않음, 관리자 변경이 커밋되면 다시 읽음,
*   캐시가 없을 때 여러 요청이 동시에 와도 DB 읽기(= 연결 하나 더 쓰기)는 한 번만
*   - 캐시 프록시와 트랜잭션(커밋·롤백 뒤 동작)이 실제로 돌아야 해서, CacheConfig·두 서비스·실제 트랜잭션 관리자(H2 메모리 DB)만 올린
*     작은 스프링 컨텍스트로 확인한다. 저장소는 가짜라 findAll 이 몇 번 불렸는지로 "DB 를 읽었는지" 본다
*  */
@SpringJUnitConfig(classes = {CacheConfig.class, CategoryPathService.class, AdminCategoryService.class,
        CategoryPathServiceTest.TxConfig.class})
class CategoryPathServiceTest {

    @Configuration
    @EnableTransactionManagement
    static class TxConfig {
        @Bean
        DataSource dataSource() {
            return new DriverManagerDataSource("jdbc:h2:mem:category-path-cache-test;DB_CLOSE_DELAY=-1", "sa", "");
        }

        @Bean
        PlatformTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }

        @Bean
        TransactionTemplate transactionTemplate(PlatformTransactionManager transactionManager) {
            return new TransactionTemplate(transactionManager);
        }
    }

    @MockitoBean
    private CategoryRepository categoryRepository;
    @MockitoBean
    private CourseRepository courseRepository;

    @Autowired
    private CategoryPathService categoryPathService;
    @Autowired
    private AdminCategoryService adminCategoryService;
    @Autowired
    private TransactionTemplate tx;

    private Category realEstate;
    private List<Category> categories;

    @BeforeEach
    void setUp() {
        categoryPathService.markChanged();   // 트랜잭션 밖이라 바로 버전이 올라간다 → 테스트끼리 캐시가 섞이지 않는다
        realEstate = category(Category.createCategory("부동산", 1), 1L);
        Category subscription = category(Category.createSubCategory(realEstate, "청약", 1), 11L);
        Category closed = category(Category.createSubCategory(realEstate, "경매", 2), 12L);
        closed.deactivate();
        categories = List.of(realEstate, subscription, closed);
        when(categoryRepository.findAll()).thenReturn(categories);
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(realEstate));
        when(courseRepository.existsByCategoryId(1L)).thenReturn(true);   // 강의가 있어 삭제 대신 비활성 처리
    }

    private static Category category(Category category, long id) {
        ReflectionTestUtils.setField(category, "categoryId", id);
        return category;
    }

    @Test
    @DisplayName("상위는 자기 이름, 소주제는 '상위 › 소주제'. 비활성 카테고리도 이름이 있다")
    void buildsPaths() {
        Map<Long, String> paths = categoryPathService.findCategoryPaths();

        assertThat(paths).containsEntry(1L, "부동산")
                .containsEntry(11L, "부동산 › 청약")
                .containsEntry(12L, "부동산 › 경매");
        assertThatThrownBy(() -> paths.put(99L, "x")).isInstanceOf(UnsupportedOperationException.class);  // 캐시 값은 못 바꾼다
    }

    @Test
    @DisplayName("두 번째부터는 캐시에서 꺼내고 카테고리를 다시 읽지 않는다")
    void cachedAfterFirstCall() {
        categoryPathService.findCategoryPaths();
        categoryPathService.findCategoryPaths();
        categoryPathService.findCategoryPaths();

        verify(categoryRepository, times(1)).findAll();
    }

    @Test
    @DisplayName("관리자 변경이 커밋되기 전에는 캐시를 그대로 쓰고, 커밋된 뒤의 조회는 새로 읽는다")
    void reloadsOnlyAfterCommit() {
        categoryPathService.findCategoryPaths();

        tx.executeWithoutResult(status -> {
            adminCategoryService.delete(1L);
            categoryPathService.findCategoryPaths();
            verify(categoryRepository, times(1)).findAll();   // 아직 커밋 전: 캐시 사용
        });
        categoryPathService.findCategoryPaths();

        verify(categoryRepository, times(2)).findAll();       // 커밋 뒤: 새로 읽음
    }

    @Test
    @DisplayName("관리자 변경이 롤백되면 캐시를 그대로 쓴다")
    void keepsCacheOnRollback() {
        categoryPathService.findCategoryPaths();

        tx.executeWithoutResult(status -> {
            adminCategoryService.delete(1L);
            status.setRollbackOnly();
        });
        categoryPathService.findCategoryPaths();

        verify(categoryRepository, times(1)).findAll();
    }

    @Test
    @DisplayName("옛 데이터를 읽던 조회가 관리자 변경 커밋 뒤에 끝나도, 다음 조회는 그 옛 값을 쓰지 않고 새로 읽는다")
    void slowReaderDoesNotOverwriteNewerData() {
        Category renamed = category(Category.createCategory("집", 1), 1L);
        AtomicInteger reads = new AtomicInteger();
        when(categoryRepository.findAll()).thenAnswer(invocation -> {
            if (reads.incrementAndGet() == 1) {
                // 이 조회가 옛 데이터를 읽은 직후, 다른 요청(다른 스레드·다른 트랜잭션)에서 관리자 변경이 커밋된다
                CompletableFuture.runAsync(() -> tx.executeWithoutResult(status -> adminCategoryService.delete(1L))).join();
                return categories;
            }
            return List.of(renamed);
        });

        assertThat(categoryPathService.findCategoryPaths()).containsEntry(1L, "부동산");   // 늦게 끝난 조회는 옛 값

        assertThat(categoryPathService.findCategoryPaths()).containsEntry(1L, "집");      // 다음 조회는 새 값
        verify(categoryRepository, times(2)).findAll();
    }

    @Test
    @DisplayName("캐시가 없을 때 두 요청이 동시에 와도 DB 는 한 번만 읽고, 기다린 요청은 먼저 읽은 값을 쓴다")
    void concurrentMissesReadOnce() throws Exception {
        CountDownLatch firstReading = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        AtomicInteger reads = new AtomicInteger();
        when(categoryRepository.findAll()).thenAnswer(invocation -> {
            reads.incrementAndGet();
            firstReading.countDown();
            assertThat(releaseFirst.await(5, TimeUnit.SECONDS)).isTrue();   // 첫 요청이 읽는 동안 두 번째 요청이 들어온다
            return categories;
        });

        CompletableFuture<Map<Long, String>> first = CompletableFuture.supplyAsync(categoryPathService::findCategoryPaths);
        assertThat(firstReading.await(5, TimeUnit.SECONDS)).isTrue();
        CompletableFuture<Map<Long, String>> second = CompletableFuture.supplyAsync(categoryPathService::findCategoryPaths);
        Thread.sleep(200);                                                 // 두 번째 요청이 잠금 앞에서 기다리게 둔다
        releaseFirst.countDown();

        assertThat(first.get(5, TimeUnit.SECONDS)).containsEntry(11L, "부동산 › 청약");
        assertThat(second.get(5, TimeUnit.SECONDS)).containsEntry(11L, "부동산 › 청약");
        assertThat(reads.get()).isEqualTo(1);
    }
}
