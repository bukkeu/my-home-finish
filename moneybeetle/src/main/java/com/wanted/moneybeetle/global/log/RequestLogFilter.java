package com.wanted.moneybeetle.global.log;

import com.wanted.moneybeetle.domain.auth.model.dto.AuthDetails;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/* comment.
*   담당 : SCM (공통 기반·보안)
*   #84 요청 로그 (시스템 설계 "로그", EP-05)
*   - 요청마다 요청 ID 를 정해 MDC("requestId")에 넣는다 → 이 요청 동안 남는 모든 로그 줄에 같은 ID 가 붙는다
*     (콘솔은 logging.pattern.correlation, local 의 파일 로그는 JSON 필드로)
*   - 응답 헤더 X-Request-Id 로도 돌려준다. 화면에서 오류를 신고할 때 이 ID 로 서버 로그를 찾을 수 있다
*   - 요청이 끝나면 한 줄 : "GET /api/v1/housing/complexes 200 12ms" (+ MDC 의 회원 ID)
*   - 비밀값을 남기지 않는다 : 경로만 남기고 쿼리(?token= 등)·본문·파라미터 값은 쓰지 않는다
*   - 보안 필터보다 먼저(가장 바깥에서) 돈다 → 로그인 처리·권한 거절 로그에도 요청 ID 가 붙는다
*  */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLogFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Request-Id";
    public static final String MDC_REQUEST_ID = "requestId";
    public static final String MDC_MEMBER_ID = "memberId";

    // 밖에서 보낸 요청 ID 는 영문·숫자·하이픈 36자까지만 받는다 (로그에 엉뚱한 글자가 섞이지 않게)
    private static final Pattern VALID_ID = Pattern.compile("[A-Za-z0-9-]{1,36}");
    // 정적 파일은 요청 로그를 남기지 않는다 (화면 하나에 수십 줄이 생겨 읽기 어렵다)
    private static final List<String> SKIP_PREFIXES =
            List.of("/css/", "/js/", "/images/", "/uploads/", "/favicon", "/.well-known/");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String requestId = resolveRequestId(request.getHeader(HEADER));
        MDC.put(MDC_REQUEST_ID, requestId);
        response.setHeader(HEADER, requestId);
        long start = System.nanoTime();
        try {
            chain.doFilter(request, response);
        } finally {
            String path = request.getRequestURI();                       // 쿼리 문자열은 포함하지 않는다
            if (!isStaticResource(path)) {
                Long memberId = findMemberId(request);
                if (memberId != null) {
                    MDC.put(MDC_MEMBER_ID, String.valueOf(memberId));
                }
                long elapsedMs = (System.nanoTime() - start) / 1_000_000;
                log.info("{} {} {} {}ms", request.getMethod(), path, response.getStatus(), elapsedMs);
            }
            MDC.remove(MDC_MEMBER_ID);
            MDC.remove(MDC_REQUEST_ID);
        }
    }

    static String resolveRequestId(String incoming) {
        if (incoming != null && VALID_ID.matcher(incoming).matches()) {
            return incoming;
        }
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private boolean isStaticResource(String path) {
        return SKIP_PREFIXES.stream().anyMatch(path::startsWith);
    }

    // 로그인한 회원 번호 : 이 필터가 보안 필터 바깥이라 요청이 끝난 뒤에는 SecurityContextHolder 가 비어 있을 수 있다.
    // 그래서 세션에 저장된 SecurityContext 도 본다. 로그인 안 했거나 로그아웃으로 세션이 사라졌으면 null
    private Long findMemberId(HttpServletRequest request) {
        Long fromHolder = memberIdOf(SecurityContextHolder.getContext());
        if (fromHolder != null) {
            return fromHolder;
        }
        HttpSession session = request.getSession(false);
        if (session == null) {
            return null;
        }
        try {
            Object saved = session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
            return saved instanceof SecurityContext context ? memberIdOf(context) : null;
        } catch (IllegalStateException invalidated) {                   // 로그아웃·탈퇴로 이미 끝난 세션
            return null;
        }
    }

    private Long memberIdOf(SecurityContext context) {
        Authentication authentication = context.getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthDetails details) {
            return details.getMemberId();
        }
        return null;
    }
}
