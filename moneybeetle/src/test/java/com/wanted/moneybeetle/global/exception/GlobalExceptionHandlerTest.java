package com.wanted.moneybeetle.global.exception;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/* comment.
*   담당 : SCM (공통 기반·보안)
*   GlobalExceptionHandler #142 확인 (테스트용 컨트롤러로 각 예외를 일으킨다)
*   - @Valid 실패 → 400 INVALID_INPUT + error.fields
*   - 본문 JSON 형식 오류·필수 파라미터 누락·타입 오류 → 400 INVALID_INPUT, fields: null
*   - 업무 예외 응답에도 fields: null (모든 오류 응답이 같은 모양)
*   - 인증·권한 예외는 잡지 않고 다시 던진다 (Spring Security 가 401·403 처리)
*  */
class GlobalExceptionHandlerTest {

    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new TestController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @Getter
    @Setter
    static class SampleDTO {
        @NotBlank(message = "닉네임을 입력해 주세요")
        private String nickname;

        @Size(min = 8, message = "비밀번호는 8자 이상이에요")
        private String password;

        private String passwordConfirm;

        // 특정 칸이 아닌 검사 (여러 칸 비교)
        @AssertTrue(message = "비밀번호 확인이 일치하지 않아요")
        public boolean isPasswordConfirmed() {
            return password == null || password.equals(passwordConfirm);
        }
    }

    @RestController
    static class TestController {

        @PostMapping("/api/v1/test/valid")
        public String valid(@Valid @RequestBody SampleDTO request) {
            return "ok";
        }

        @GetMapping("/api/v1/test/param")
        public String param(@RequestParam int page) {
            return "ok";
        }

        @GetMapping("/api/v1/test/items/{id}")
        public String item(@PathVariable Long id) {
            return "ok";
        }

        @GetMapping("/api/v1/test/business")
        public String business() {
            throw new BusinessException(CommonErrorCode.NOT_FOUND);
        }

        @GetMapping("/api/v1/test/denied")
        public String denied() {
            throw new AccessDeniedException("관리자만");
        }

        @GetMapping("/api/v1/test/unauthenticated")
        public String unauthenticated() {
            throw new BadCredentialsException("로그인 실패");
        }

        @GetMapping("/api/v1/test/crash")
        public String crash() {
            throw new IllegalStateException("예상 못한 오류");
        }
    }

    @Test
    void Valid_실패는_400_INVALID_INPUT_과_입력칸별_안내() throws Exception {
        mockMvc.perform(post("/api/v1/test/valid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "nickname": "", "password": "short", "passwordConfirm": "different" }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.error.fields.length()").value(3))
                .andExpect(jsonPath("$.error.fields[*].field").value(
                        containsInAnyOrder("nickname", "password", "passwordConfirmed")))
                .andExpect(jsonPath("$.error.fields[?(@.field == 'nickname')].message").value("닉네임을 입력해 주세요"));
    }

    @Test
    void 본문_JSON_형식이_틀리면_400() throws Exception {
        mockMvc.perform(post("/api/v1/test/valid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ nickname: "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"))
                .andExpect(content().string(containsString("\"fields\":null")));
    }

    @Test
    void 필수_파라미터가_빠지면_400() throws Exception {
        mockMvc.perform(get("/api/v1/test/param"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
    }

    @Test
    void 파라미터_타입이_틀리면_400() throws Exception {
        mockMvc.perform(get("/api/v1/test/param").param("page", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
        mockMvc.perform(get("/api/v1/test/items/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
    }

    @Test
    void 업무_예외_응답에도_fields_null_이_있다() throws Exception {
        mockMvc.perform(get("/api/v1/test/business"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"))
                .andExpect(content().string(containsString("\"fields\":null")));
    }

    @Test
    void 권한_예외는_잡지_않고_Spring_Security_로_넘긴다() {
        // 필터 없이 돌리는 standalone MockMvc 에서는 다시 던진 예외가 그대로 밖으로 나온다
        // (실제 서버에서는 ExceptionTranslationFilter → SecurityErrorHandler 가 403 으로 답한다)
        assertThatThrownBy(() -> mockMvc.perform(get("/api/v1/test/denied")))
                .hasRootCauseInstanceOf(AccessDeniedException.class);
    }

    @Test
    void 인증_예외도_잡지_않고_Spring_Security_로_넘긴다() {
        assertThatThrownBy(() -> mockMvc.perform(get("/api/v1/test/unauthenticated")))
                .hasRootCauseInstanceOf(BadCredentialsException.class);
    }

    @Test
    void 예상하지_못한_예외는_500_INTERNAL_ERROR() throws Exception {
        mockMvc.perform(get("/api/v1/test/crash"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("INTERNAL_ERROR"));
    }
}
