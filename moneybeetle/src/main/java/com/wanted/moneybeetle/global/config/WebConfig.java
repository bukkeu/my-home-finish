package com.wanted.moneybeetle.global.config;

/* comment.
*   담당 : SCM (공통 기반·보안)
*   WebMvcConfigurer
*   - LevelCheckInterceptor 등록 : /api/v1/trading/**, /api/v1/housing/** 중
*   @RequireLevel 이 붙은 API 만 레벨 검사
*   - 업로드 파일(프로필, 썸네일) 정적 경로 매핑
* */

import com.wanted.moneybeetle.global.security.LevelCheckInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {    // 스프링 MVC 설정을 바꿀 수 있게 해 주는 인터페이스

    private final LevelCheckInterceptor levelCheckInterceptor;

    @Value("${file.upload-dir}")    // yaml의 값 읽음
    private String uploadDir;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(levelCheckInterceptor)
                .addPathPatterns("/api/v1/trading/**", "/api/v1/housing/**");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {     // 정적 자원 연결 메서드 이름
        registry.addResourceHandler("/uploads/**")  // 브라우저가 요청하는 주소 모양
                .addResourceLocations("file:" +uploadDir+ "/");
    }
}
