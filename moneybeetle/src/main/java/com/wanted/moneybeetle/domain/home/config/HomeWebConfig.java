package com.wanted.moneybeetle.domain.home.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/* comment.
*   담당 : DBA ② (홈, 이슈 #248)
*   학습자 화면(/user/**)에 공통 레이아웃 값을 넣는 인터셉터(LayoutInterceptor)를 등록한다.
*   global.config.WebConfig 도 WebMvcConfigurer 라서 두 설정의 인터셉터가 함께 적용된다 (서로 다른 경로).
*  */
@Configuration
@RequiredArgsConstructor
public class HomeWebConfig implements WebMvcConfigurer {

    private final LayoutInterceptor layoutInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(layoutInterceptor).addPathPatterns("/user/**");
    }
}
