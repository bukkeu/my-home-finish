package com.wanted.moneybeetle.global.config;

/* comment.
*   담당 : SCM (회원·공통)
*   WebMvcConfigurer
*   - LevelCheckInterceptor 등록 (/api/v1/trading/** Lv.8, /api/v1/housing/** Lv.12)
*   - 업로드 파일(프로필, 썸네일) 정적 경로 매핑
*  */

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {    // 스프링 MVC 설정을 바꿀 수 있게 해 주는 인터페이스

    @Value("${file.upload-dir}")    // yaml의 값 읽음
    private String uploadDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {     // 정적 자원 연결 메서드 이름
        registry.addResourceHandler("/uploads/**")  // 브라우저가 요청하는 주소 모양
                .addResourceLocations("file:" +uploadDir+ "/");
    }
}
