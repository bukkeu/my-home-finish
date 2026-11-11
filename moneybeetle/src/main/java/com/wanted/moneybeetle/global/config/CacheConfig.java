package com.wanted.moneybeetle.global.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/* comment.
*   담당 : DBA ① 신아림 (#296) - global 영역이라 SCM 확인을 받는다
*   모든 도메인이 쓰는 서버 메모리 캐시 (ConcurrentMap). 서버 한 대 기준이고, 외부 캐시 서버는 쓰지 않는다
*   - 캐시 이름은 미리 정하지 않는다. 처음 쓰일 때 만들어지므로, 캐시 이름 상수는 그 캐시를 쓰는 도메인이 가진다
*     (예: 강의 카테고리 이름 캐시는 CategoryPathService.CACHE_NAME)
*   - 데이터가 바뀌었을 때 옛 값이 남지 않게 하는 방법도 캐시를 쓰는 쪽이 정한다
*  */
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        return new ConcurrentMapCacheManager();
    }
}
