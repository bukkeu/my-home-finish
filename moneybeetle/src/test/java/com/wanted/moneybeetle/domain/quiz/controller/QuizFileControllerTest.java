package com.wanted.moneybeetle.domain.quiz.controller;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wanted.moneybeetle.domain.auth.model.dto.AuthDetails;
import com.wanted.moneybeetle.domain.member.model.service.MemberService;
import com.wanted.moneybeetle.domain.quiz.model.service.QuizFileService;
import com.wanted.moneybeetle.global.exception.GlobalExceptionHandler;
import com.wanted.moneybeetle.support.MockSecurityServices;
import com.wanted.moneybeetle.support.SecurityTestApp;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/* comment.
*   담당 : 서수영 (#368)
*   API 153 퀴즈 문제 파일 읽기 : 실제 SecurityConfig + 컨트롤러 + 서비스 + GlobalExceptionHandler 로 확인한다 (DB 없음)
*   - 승인된 강사(INSTRUCTOR)만 / CSRF 토큰 필요 / 응답은 저장 API 요청과 같은 모양 / 파일·type 이 빠지면 500 이 아니라 400
*  */
@SpringBootTest(classes = {SecurityTestApp.class, QuizFileController.class, QuizFileService.class, GlobalExceptionHandler.class})
@MockSecurityServices
class QuizFileControllerTest {

    private static final String URL = "/api/v1/instructor/quiz-files/parse";

    @Autowired
    private WebApplicationContext context;
    /** ActiveMemberFilter 가 요청마다 계정 상태를 확인한다 (@MockSecurityServices 의 가짜). 이 테스트의 회원은 정상 계정이다 */
    @Autowired
    private MemberService memberService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        when(memberService.isActiveMember(anyLong())).thenReturn(true);
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private static AuthDetails login(String role) {
        AuthDetails details = mock(AuthDetails.class);
        when(details.getMemberId()).thenReturn(2L);
        when(details.getUsername()).thenReturn("seoyeon@moneybeetle.local");
        when(details.getRole()).thenReturn(role);
        doReturn(List.of(new SimpleGrantedAuthority(role))).when(details).getAuthorities();
        return details;
    }

    private static MockMultipartFile sample(String name) throws IOException {
        try (InputStream in = QuizFileControllerTest.class.getResourceAsStream("/quiz-files/" + name)) {
            return new MockMultipartFile("file", name, null, in.readAllBytes());
        }
    }

    @Test
    @DisplayName("강사가 테스트 퀴즈 CSV 를 올리면 저장 API(79)에 그대로 보낼 수 있는 문항 목록을 받는다")
    void instructorParsesTestQuiz() throws Exception {
        mockMvc.perform(multipart(URL).file(sample("test-quiz.csv")).param("type", "TEST")
                        .with(csrf()).with(user(login("INSTRUCTOR"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.questions.length()").value(5))
                .andExpect(jsonPath("$.data.questions[0].content").value("청약통장은 1인 1개만 만들 수 있다."))
                .andExpect(jsonPath("$.data.questions[0].answer").value("O"))
                .andExpect(jsonPath("$.data.questions[1].answer").value("X"));
    }

    @Test
    @DisplayName("강사가 퀴즈 엑셀을 올리면 보기 4개와 answerIndex(0~3)를 받는다 (저장 API 80 모양)")
    void instructorParsesMainQuiz() throws Exception {
        mockMvc.perform(multipart(URL).file(sample("main-quiz.xlsx")).param("type", "MAIN")
                        .with(csrf()).with(user(login("INSTRUCTOR"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.questions.length()").value(5))
                .andExpect(jsonPath("$.data.questions[0].choices.length()").value(4))
                .andExpect(jsonPath("$.data.questions[0].answerIndex").value(3));
    }

    @Test
    @DisplayName("줄이 틀리면 400 QUIZ_FILE_INVALID 이고 문구에 줄 번호가 있다")
    void invalidLineMessage() throws Exception {
        MockMultipartFile wrong = new MockMultipartFile("file", "q.csv", null,
                "문제,정답,해설\n문제,Y,해설\n문제,O,해설\n문제,O,해설\n문제,O,해설\n문제,O,해설\n".getBytes());
        mockMvc.perform(multipart(URL).file(wrong).param("type", "TEST").with(csrf()).with(user(login("INSTRUCTOR"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("QUIZ_FILE_INVALID"))
                .andExpect(jsonPath("$.error.message").value("2번째 줄: 정답은 O 또는 X 로 써 주세요"));
    }

    @Test
    @DisplayName("파일이 빠지면 500 이 아니라 400 QUIZ_FILE_INVALID")
    void missingFile() throws Exception {
        mockMvc.perform(multipart(URL).param("type", "TEST").with(csrf()).with(user(login("INSTRUCTOR"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("QUIZ_FILE_INVALID"));
    }

    @Test
    @DisplayName("multipart 가 아닌 JSON 으로 보내도 500 이 아니라 400")
    void jsonRequest() throws Exception {
        mockMvc.perform(post(URL).param("type", "TEST").contentType(MediaType.APPLICATION_JSON).content("{}")
                        .with(csrf()).with(user(login("INSTRUCTOR"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("QUIZ_FILE_INVALID"));
    }

    @Test
    @DisplayName("type 이 빠지거나 TEST·MAIN 이 아니면 400 INVALID_INPUT")
    void missingOrWrongType() throws Exception {
        mockMvc.perform(multipart(URL).file(sample("test-quiz.csv")).with(csrf()).with(user(login("INSTRUCTOR"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
        mockMvc.perform(multipart(URL).file(sample("test-quiz.csv")).param("type", "OX")
                        .with(csrf()).with(user(login("INSTRUCTOR"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
    }

    @Test
    @DisplayName("일반 회원(승인 전 강사 포함)은 403, 로그인 안 하면 401")
    void onlyInstructor() throws Exception {
        mockMvc.perform(multipart(URL).file(sample("test-quiz.csv")).param("type", "TEST")
                        .with(csrf()).with(user(login("MEMBER"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(multipart(URL).file(sample("test-quiz.csv")).param("type", "TEST").with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("CSRF 토큰이 없으면 403")
    void csrfRequired() throws Exception {
        mockMvc.perform(multipart(URL).file(sample("test-quiz.csv")).param("type", "TEST").with(user(login("INSTRUCTOR"))))
                .andExpect(status().isForbidden());
    }
}
