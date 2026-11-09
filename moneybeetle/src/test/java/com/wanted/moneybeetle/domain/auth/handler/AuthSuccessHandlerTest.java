package com.wanted.moneybeetle.domain.auth.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wanted.moneybeetle.domain.auth.model.dto.AuthDetails;
import com.wanted.moneybeetle.domain.growth.model.dto.AttendanceResultDTO;
import com.wanted.moneybeetle.domain.growth.model.service.AttendanceService;
import com.wanted.moneybeetle.domain.member.model.dto.LoginMemberDTO;
import com.wanted.moneybeetle.domain.member.model.entity.Member;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;

/* comment.
*   담당 : DBA ② (회원·인증)
*   로그인 성공 처리의 출석 기록 연결 (이슈 #12, API 6 attendance) - 서버·DB 없이 돈다
*  */
class AuthSuccessHandlerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private AttendanceService attendanceService;
    private AuthSuccessHandler handler;

    @BeforeEach
    void setUp() {
        attendanceService = mock(AttendanceService.class);
        handler = new AuthSuccessHandler(objectMapper, attendanceService);
    }

    private JsonNode login(String role) throws Exception {
        Member member = Member.signup("minji@gmail.com", "hash", "민지", LocalDate.of(1999, 4, 12), "FEMALE",
                LocalDate.of(2026, 11, 9));
        ReflectionTestUtils.setField(member, "memberId", 8L);
        AuthDetails details = new AuthDetails(LoginMemberDTO.from(member, role));
        MockHttpServletResponse response = new MockHttpServletResponse();
        handler.onAuthenticationSuccess(new MockHttpServletRequest(), response,
                new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities()));
        assertThat(response.getStatus()).isEqualTo(200);
        return objectMapper.readTree(response.getContentAsString()).get("data");
    }

    @Test
    @DisplayName("일반 회원이 로그인하면 오늘 출석을 기록하고 응답에 attendance 를 담는다")
    void memberLoginRecordsAttendance() throws Exception {
        when(attendanceService.checkToday(8L)).thenReturn(AttendanceResultDTO.checked(5, 10_000));

        JsonNode data = login(Member.ROLE_MEMBER);

        verify(attendanceService).checkToday(8L);
        assertThat(data.get("attendance").get("checkedToday").asBoolean()).isTrue();
        assertThat(data.get("attendance").get("newlyChecked").asBoolean()).isTrue();
        assertThat(data.get("attendance").get("streak").asInt()).isEqualTo(5);
        assertThat(data.get("attendance").get("rewardCash").asLong()).isEqualTo(10_000);
        assertThat(data.get("redirectUrl").asText()).isEqualTo("/user/home");
    }

    @Test
    @DisplayName("출석 기록이 실패해도 로그인은 성공하고 attendance 만 비운다")
    void attendanceFailureDoesNotBreakLogin() throws Exception {
        when(attendanceService.checkToday(8L)).thenThrow(new IllegalStateException("db down"));

        JsonNode data = login(Member.ROLE_MEMBER);

        assertThat(data.get("memberId").asLong()).isEqualTo(8L);
        assertThat(data.get("attendance").isNull()).isTrue();
    }

    @Test
    @DisplayName("강사·관리자 로그인에서는 출석을 기록하지 않는다")
    void nonMemberRolesSkipAttendance() throws Exception {
        JsonNode instructor = login(Member.ROLE_INSTRUCTOR);
        JsonNode admin = login(Member.ROLE_ADMIN);

        verify(attendanceService, never()).checkToday(anyLong());
        assertThat(instructor.get("attendance").isNull()).isTrue();
        assertThat(admin.get("attendance").isNull()).isTrue();
    }
}
