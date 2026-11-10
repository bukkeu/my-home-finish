package com.wanted.moneybeetle.domain.home.controller;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wanted.moneybeetle.domain.auth.model.dto.AuthDetails;
import com.wanted.moneybeetle.domain.course.model.service.CourseService;
import com.wanted.moneybeetle.domain.growth.model.dao.LevelPolicyRepository;
import com.wanted.moneybeetle.domain.growth.model.service.GrowthService;
import com.wanted.moneybeetle.domain.home.model.service.HomeService;
import com.wanted.moneybeetle.domain.member.model.dto.MemberBalanceDTO;
import com.wanted.moneybeetle.domain.member.model.service.MemberService;
import com.wanted.moneybeetle.domain.term.model.service.TermService;
import com.wanted.moneybeetle.domain.trading.model.dao.SectorRepository;
import com.wanted.moneybeetle.domain.trading.model.dao.StockRepository;
import com.wanted.moneybeetle.domain.trading.model.entity.Sector;
import com.wanted.moneybeetle.domain.trading.model.entity.Stock;
import com.wanted.moneybeetle.domain.trading.model.service.StockSearchService;
import com.wanted.moneybeetle.global.exception.GlobalExceptionHandler;
import com.wanted.moneybeetle.support.MockSecurityServices;
import com.wanted.moneybeetle.support.SecurityTestApp;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.context.WebApplicationContext;

/* comment.
*   담당 : PM (#15)
*   통합 검색 API 28 의 "Lv.8 이상 회원에게만 종목이 나온다" 확인 (CodeRabbit 요청: 실제 HomeService 를 거친 응답의 stocks)
*   - 실제 SecurityConfig + HomeController + HomeService + GrowthService + StockSearchService 를 함께 띄운다
*   - 가짜(mock)는 DB 쪽뿐 : MemberService(레벨을 돌려줌)·저장소·CourseService·TermService. 서버·DB 를 직접 띄운 확인은 아니다
*   - 로그인은 AuthDetails 를 세션에 넣은 것으로 대신한다
*  */
@SpringBootTest(classes = {SecurityTestApp.class, HomeController.class, HomeService.class,
        GrowthService.class, StockSearchService.class, GlobalExceptionHandler.class})
@MockSecurityServices
class HomeSearchLevelFlowTest {

    private static final Long MEMBER_ID = 8L;

    @MockitoBean
    private LevelPolicyRepository levelPolicyRepository;
    @MockitoBean
    private CourseService courseService;
    @MockitoBean
    private TermService termService;
    @MockitoBean
    private StockRepository stockRepository;
    @MockitoBean
    private SectorRepository sectorRepository;
    @MockitoBean
    private PlatformTransactionManager transactionManager;
    @Autowired
    private MemberService memberService;     // @MockSecurityServices 의 가짜 : 계정 상태·레벨(getBalance)을 정해 둔다

    @Autowired
    private WebApplicationContext context;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        when(memberService.isActiveMember(anyLong())).thenReturn(true);
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

        Stock stock = mock(Stock.class);
        when(stock.getStockId()).thenReturn(3L);
        when(stock.getName()).thenReturn("한빛전자");
        when(stock.getSectorId()).thenReturn(1L);
        Sector sector = mock(Sector.class);
        when(sector.getSectorId()).thenReturn(1L);
        when(sector.getName()).thenReturn("전자");
        when(sector.isActive()).thenReturn(true);
        when(stockRepository.findByNameContainingAndIsActiveTrueOrderByStockIdAsc("한빛")).thenReturn(List.of(stock));
        when(sectorRepository.findAllById(List.of(1L))).thenReturn(List.of(sector));
        when(courseService.search(anyString())).thenReturn(List.of());
    }

    private void levelIs(int level) {
        when(memberService.getBalance(MEMBER_ID)).thenReturn(MemberBalanceDTO.of(MEMBER_ID, level, 0, 0));
    }

    private AuthDetails member() {
        AuthDetails details = mock(AuthDetails.class);
        when(details.getMemberId()).thenReturn(MEMBER_ID);
        when(details.getUsername()).thenReturn("minji@moneybeetle.local");
        doReturn(List.of(new SimpleGrantedAuthority("MEMBER"))).when(details).getAuthorities();
        return details;
    }

    @Test
    void Lv8_회원은_종목이_검색_결과에_나온다() throws Exception {
        levelIs(8);

        mockMvc.perform(get("/api/v1/search").param("keyword", " 한빛 ").with(user(member())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.stocks.length()").value(1))
                .andExpect(jsonPath("$.data.stocks[0].stockId").value(3))
                .andExpect(jsonPath("$.data.stocks[0].name").value("한빛전자"))
                .andExpect(jsonPath("$.data.stocks[0].sectorName").value("전자"));
        verify(stockRepository).findByNameContainingAndIsActiveTrueOrderByStockIdAsc("한빛");   // 앞뒤 공백은 뺀다
    }

    @Test
    void Lv7_회원은_검색은_되지만_종목은_나오지_않는다() throws Exception {
        levelIs(7);

        mockMvc.perform(get("/api/v1/search").param("keyword", "한빛").with(user(member())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stocks.length()").value(0));
        verify(stockRepository, never()).findByNameContainingAndIsActiveTrueOrderByStockIdAsc(anyString());
    }

    @Test
    void 로그인_안_하면_401() throws Exception {
        mockMvc.perform(get("/api/v1/search").param("keyword", "한빛"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }
}
