package com.wanted.moneybeetle.domain.home.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/* comment.
*   담당 : DBA ② (홈 화면, 이슈 #248)
*   HomePageController - 주소가 템플릿 이름에 연결되는지 서버 없이 확인한다. 템플릿이 실제로 그려지는지는 HomeTemplatesRenderTest(#51)가 확인한다.
*  */
class HomePageControllerTest {

    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new HomePageController()).build();

    @Test
    @DisplayName("/ 는 서비스 소개 메인(index), /user/home 은 홈 화면(home/home)에 연결된다")
    void routes() throws Exception {
        mockMvc.perform(get("/")).andExpect(status().isOk()).andExpect(view().name("index"));
        mockMvc.perform(get("/user/home")).andExpect(status().isOk()).andExpect(view().name("home/home"));
    }
}
