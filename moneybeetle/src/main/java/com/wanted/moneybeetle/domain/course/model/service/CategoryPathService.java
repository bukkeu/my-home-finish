package com.wanted.moneybeetle.domain.course.model.service;

import com.wanted.moneybeetle.domain.course.model.dao.CategoryRepository;
import com.wanted.moneybeetle.domain.course.model.entity.Category;
import com.wanted.moneybeetle.global.config.CacheConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/* comment.
*   담당 : DBA ① 신아림 (#296)
*   카테고리 ID → "부동산 › 청약" 같은 표시 이름. 스크랩 목록·오답노트·통합 검색이 같이 쓴다
*   - 카테고리는 관리자만 가끔 바꾸므로 한 번 읽어 캐시에 두고, AdminCategoryService 가 바꾸면 캐시를 비운다
*   - @Cacheable 은 다른 빈에서 부를 때만 동작해서 (같은 클래스 안 호출은 프록시를 거치지 않음) CourseService 와 나눴다
*   - 비활성 카테고리도 넣는다 (이미 그 카테고리에 있는 강의의 이름표는 계속 보여야 한다)
*  */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CategoryPathService {

    private final CategoryRepository categoryRepository;

    /* 전체 카테고리의 표시 이름. 캐시에 들어가므로 바꿀 수 없는 Map 으로 돌려준다 */
    @Cacheable(CacheConfig.CATEGORY_PATHS)
    public Map<Long, String> findCategoryPaths() {
        return toPaths(categoryRepository.findAll());
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
