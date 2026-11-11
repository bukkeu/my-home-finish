package com.wanted.moneybeetle.global.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/* comment.
*   담당 : DBA ① 신아림 (#296, 카테고리 경로 캐시) - global 영역이라 SCM 확인을 받는다
*   서버 메모리 캐시 (ConcurrentMap). 서버 한 대 기준이고, 외부 캐시 서버는 쓰지 않는다
*   - 캐시 이름은 여기 상수로 모아 두고 @Cacheable 에서 이 상수를 쓴다
*   - 데이터가 바뀌었을 때 옛 값이 남지 않게 하는 방법은 캐시를 쓰는 쪽이 정한다 (카테고리: CategoryPathService 의 버전 키)
*  */
@Configuration
@EnableCaching
public class CacheConfig {

    public static final String CATEGORY_PATHS = "categoryPaths";   // 카테고리 ID → "상위 › 소주제" 이름 (CategoryPathService)

    @Bean
    public CacheManager cacheManager() {
        return new ConcurrentMapCacheManager(CATEGORY_PATHS);
    }
}
