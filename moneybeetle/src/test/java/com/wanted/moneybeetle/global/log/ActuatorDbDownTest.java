package com.wanted.moneybeetle.global.log;

import com.wanted.moneybeetle.support.MockSecurityServices;
import com.wanted.moneybeetle.support.SecurityTestApp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import javax.sql.DataSource;
import java.sql.SQLException;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/* comment.
*   담당 : SCM (공통 기반·보안)
*   #84 DB 연결이 끊기면 /actuator/health 가 DOWN(503)이 되는지 확인 (시스템 설계 "모니터링")
*   - 실제 MySQL 대신 연결할 때마다 실패하는 가짜 DataSource 를 넣는다 → Spring Boot 의 DB 상태 확인이 이 DataSource 로 연결을 시도한다
*   - 세부 정보는 숨기므로(show-details: never) 상태(DOWN)만 보인다
*  */
@SpringBootTest(classes = {SecurityTestApp.class, ActuatorDbDownTest.BrokenDataSource.class})
@MockSecurityServices
class ActuatorDbDownTest {

    @Configuration
    static class BrokenDataSource {
        @Bean
        DataSource dataSource() throws SQLException {
            DataSource dataSource = mock(DataSource.class);
            when(dataSource.getConnection()).thenThrow(new SQLException("Communications link failure"));
            return dataSource;
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
    void DB_연결이_실패하면_health_는_503_DOWN() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("DOWN"))
                .andExpect(jsonPath("$.components").doesNotExist());
    }
}
