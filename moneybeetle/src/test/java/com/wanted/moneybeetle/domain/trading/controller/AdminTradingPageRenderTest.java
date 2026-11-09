package com.wanted.moneybeetle.domain.trading.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wanted.moneybeetle.domain.auth.config.SecurityConfig;
import com.wanted.moneybeetle.global.security.SecurityErrorHandler;
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
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/* comment.
*   담당 : PM (모의투자)
*   관리자 모의투자 운영 화면(#62)이 그려지는지 확인한다. DB 없이 Security + Thymeleaf 만 띄운다
*   (LayoutFragmentRenderTest·SecurityConfigTest 와 같은 방식).
*  */
@SpringBootTest(classes = {AdminTradingPageRenderTest.TestApp.class, SecurityConfig.class,
        SecurityErrorHandler.class, AdminTradingPageController.class})
class AdminTradingPageRenderTest {

    @Configuration
    @EnableAutoConfiguration(exclude = {
            DataSourceAutoConfiguration.class, DataSourceTransactionManagerAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class, JpaRepositoriesAutoConfiguration.class})
    static class TestApp {
    }

    @Autowired
    private WebApplicationContext context;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private RequestPostProcessor as(String role) {
        return SecurityMockMvcRequestPostProcessors.user("tester").authorities(new SimpleGrantedAuthority(role));
    }

    @Test
    @DisplayName("관리자: 운영 화면이 그려지고 탭·영역·스크립트가 모두 들어 있다")
    void adminSeesTradingPage() throws Exception {
        String html = mockMvc.perform(get("/admin/trading-management").with(as("ADMIN")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(html).contains("id=\"at-panel-season\"", "id=\"at-panel-turn\"", "id=\"at-panel-stocks\"",
                "id=\"at-panel-prices\"", "id=\"at-panel-news\"", "id=\"at-panel-people\"", "id=\"at-panel-ranking\"");
        assertThat(html).contains("id=\"at-next-btn\"", "되돌릴 수 없어요");
        assertThat(html).contains("/js/admin-trading.js", "/css/admin-trading.css", "/js/common.js");
        assertThat(html).contains("mb-admin-side", "data-mb-pinned");   // 관리자 고정 사이드바
    }

    @Test
    @DisplayName("다음 턴 버튼은 점검 결과를 받기 전까지 비활성화로 그려진다")
    void nextTurnButtonStartsDisabled() throws Exception {
        String html = mockMvc.perform(get("/admin/trading-management").with(as("ADMIN")))
                .andReturn().getResponse().getContentAsString();

        assertThat(html).containsPattern("id=\"at-next-btn\"[^>]*disabled");
    }

    @Test
    @DisplayName("관리자가 아니면 403")
    void memberIsForbidden() throws Exception {
        mockMvc.perform(get("/admin/trading-management").with(as("MEMBER"))).andExpect(status().isForbidden());
        mockMvc.perform(get("/admin/trading-management").with(as("INSTRUCTOR"))).andExpect(status().isForbidden());
    }
}
