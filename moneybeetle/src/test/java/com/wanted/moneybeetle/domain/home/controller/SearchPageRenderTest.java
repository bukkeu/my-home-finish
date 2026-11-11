package com.wanted.moneybeetle.domain.home.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/* comment.
*   담당 : PM (#56, 통합 검색 화면)
*   /user/search 화면 틀이 공통 조각(header·sidebar·notice)과 함께 그려지는지 확인한다.
*   TradingPageRenderTest 처럼 DB 없이 Thymeleaf + Security 만 띄운다. 검색 결과는 JS 가 API 28 로 채우므로 여기서는 틀만 본다.
*  */
@SpringBootTest(classes = {SearchPageRenderTest.TestApp.class, SearchPageController.class})
class SearchPageRenderTest {

    @Configuration
    @EnableAutoConfiguration(exclude = {
            DataSourceAutoConfiguration.class, DataSourceTransactionManagerAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class, JpaRepositoriesAutoConfiguration.class})
    static class TestApp {
        @Bean
        SecurityFilterChain chain(HttpSecurity http) throws Exception {
            return http.authorizeHttpRequests(a -> a.anyRequest().permitAll()).build();
        }
    }

    @Autowired
    private WebApplicationContext context;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    @DisplayName("학습자: 검색 화면이 200 으로 그려지고 검색창(name=q)·결과 영역·화면 JS·CSS 가 들어 있다")
    void rendersSearchPage() throws Exception {
        MockHttpServletResponse res = mockMvc.perform(get("/user/search").param("q", "청약")
                .with(user("tester").authorities(new SimpleGrantedAuthority("MEMBER")))).andReturn().getResponse();
        String html = res.getContentAsString();

        assertThat(res.getStatus()).isEqualTo(200);
        assertThat(html).contains("id=\"srch-form\"", "name=\"q\"", "id=\"srch-head\"", "id=\"srch-chips\"",
                "id=\"srch-result\"");
        assertThat(html).contains("/js/search.js", "/css/search.css", "/css/common.css", "/js/common.js");
        assertThat(html).contains("mb-notice", "<meta name=\"_csrf\"");
    }

    @Test
    @DisplayName("검색어가 없어도 화면이 그려지고, 공통 헤더·메뉴는 layout 값 없이 기본값으로 나온다")
    void rendersWithoutKeyword() throws Exception {
        String html = mockMvc.perform(get("/user/search")
                .with(user("tester").authorities(new SimpleGrantedAuthority("MEMBER")))).andReturn().getResponse()
                .getContentAsString();

        assertThat(html).contains("mb-header", "오늘의 경제단어", "id=\"srch-form\"");
    }
}
