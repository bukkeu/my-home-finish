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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.util.ReflectionTestUtils;

/* comment.
*   담당 : DBA ① 신아림 (#296)
*   카테고리 표시 이름 캐시: 이름 만들기, 두 번째부터는 DB 를 읽지 않음, 관리자가 바꾸면 다시 읽음
*   스프링 캐시 프록시가 실제로 동작해야 해서 CacheConfig·두 서비스만 올린 작은 스프링 컨텍스트로 확인한다 (저장소는 가짜)
*  */
@SpringJUnitConfig(classes = {CacheConfig.class, CategoryPathService.class, AdminCategoryService.class})
class CategoryPathServiceTest {

    @MockitoBean
    private CategoryRepository categoryRepository;
    @MockitoBean
    private CourseRepository courseRepository;

    @Autowired
    private CategoryPathService categoryPathService;
    @Autowired
    private AdminCategoryService adminCategoryService;
    @Autowired
    private CacheManager cacheManager;

    private Category realEstate;

    @BeforeEach
    void setUp() {
        cacheManager.getCache(CacheConfig.CATEGORY_PATHS).clear();   // 테스트끼리 캐시가 섞이지 않게
        realEstate = category(Category.createCategory("부동산", 1), 1L);
        Category subscription = category(Category.createSubCategory(realEstate, "청약", 1), 11L);
        Category closed = category(Category.createSubCategory(realEstate, "경매", 2), 12L);
        closed.deactivate();
        when(categoryRepository.findAll()).thenReturn(List.of(realEstate, subscription, closed));
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
    @DisplayName("관리자가 카테고리를 바꾸면 캐시를 비워서 다음 조회는 새로 읽는다")
    void evictedWhenAdminChangesCategory() {
        categoryPathService.findCategoryPaths();
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(realEstate));
        when(courseRepository.existsByCategoryId(1L)).thenReturn(true);   // 강의가 있어 비활성 처리

        adminCategoryService.delete(1L);
        categoryPathService.findCategoryPaths();

        verify(categoryRepository, times(2)).findAll();
    }
}
