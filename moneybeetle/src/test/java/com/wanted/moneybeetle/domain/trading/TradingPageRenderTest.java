package com.wanted.moneybeetle.domain.trading;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.wanted.moneybeetle.domain.trading.controller.TradingPageController;
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
*   담당 : PM (#57, 모의투자 화면)
*   /user/demo-trading 화면 틀이 공통 조각(header·sidebar·notice)과 함께 그려지는지 확인한다.
*   LayoutFragmentRenderTest 처럼 DB 없이 Thymeleaf + Security 만 띄운다. 데이터는 JS 가 API 로 채우므로 여기서는 틀만 본다.
*  */
@SpringBootTest(classes = {TradingPageRenderTest.TestApp.class, TradingPageController.class})
class TradingPageRenderTest {

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
    @DisplayName("학습자: 모의투자 화면이 200 으로 그려지고 홈·전체 종목·상세·주문·랭킹 영역과 화면 JS·CSS 가 들어 있다")
    void rendersTradingPage() throws Exception {
        MockHttpServletResponse res = mockMvc.perform(get("/user/demo-trading")
                .with(user("tester").authorities(new SimpleGrantedAuthority("MEMBER")))).andReturn().getResponse();
        String html = res.getContentAsString();

        assertThat(res.getStatus()).isEqualTo(200);
        assertThat(html).contains("id=\"trd-home\"", "id=\"trd-season\"", "id=\"trd-join-btn\"", "id=\"trd-market\"",
                "id=\"trd-all\"", "id=\"trd-detail\"", "id=\"trd-order\"", "id=\"trd-trades\"", "id=\"trd-ranking\"");
        assertThat(html).contains("/js/trading.js", "/css/trading.css", "/css/common.css", "/js/common.js");
        assertThat(html).contains("모의투자는 Lv.8부터 이용할 수 있어요", "/images/lock.png");
        assertThat(html).contains("mb-notice", "가상 실습이며 실제 금융 거래가 아니에요");
        assertThat(html).contains("<meta name=\"_csrf\"");
    }

    @Test
    @DisplayName("layout 값이 없어도 공통 헤더·메뉴가 기본값으로 그려진다 (LayoutDTO 공급처가 아직 없음)")
    void rendersWithoutLayout() throws Exception {
        String html = mockMvc.perform(get("/user/demo-trading")
                .with(user("tester").authorities(new SimpleGrantedAuthority("MEMBER")))).andReturn().getResponse()
                .getContentAsString();

        assertThat(html).contains("mb-header", "오늘의 경제단어", "모의 투자");
    }
}
