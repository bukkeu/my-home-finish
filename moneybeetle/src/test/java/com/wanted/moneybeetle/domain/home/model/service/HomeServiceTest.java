package com.wanted.moneybeetle.domain.home.model.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.wanted.moneybeetle.domain.course.model.dto.CourseLabelDTO;
import com.wanted.moneybeetle.domain.course.model.service.CourseService;
import com.wanted.moneybeetle.domain.growth.model.service.GrowthService;
import com.wanted.moneybeetle.domain.home.model.dto.SearchResultDTO;
import com.wanted.moneybeetle.domain.trading.model.dto.StockSearchDTO;
import com.wanted.moneybeetle.domain.trading.model.service.StockSearchService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/* comment.
*   담당 : PM (#15)
*   HomeService 통합 검색 확인 (다른 도메인 서비스는 가짜(mock))
*   - 키워드 앞뒤 공백 제거, 빈 키워드는 빈 결과
*   - 종목은 모의투자 해금(Lv.8) 회원에게만, 로그인 회원이 없으면 찾지 않는다
*  */
class HomeServiceTest {

    private CourseService courseService;
    private StockSearchService stockSearchService;
    private GrowthService growthService;
    private HomeService service;

    @BeforeEach
    void setUp() {
        courseService = mock(CourseService.class);
        stockSearchService = mock(StockSearchService.class);
        growthService = mock(GrowthService.class);
        service = new HomeService(courseService, stockSearchService, growthService);
        when(courseService.search("청약")).thenReturn(List.of(CourseLabelDTO.of(7L, "청약통장 기초", "부동산 › 청약")));
        when(stockSearchService.searchStocks("청약")).thenReturn(List.of(StockSearchDTO.of(1L, "청약종목", "부동산")));
    }

    @Test
    void 키워드의_앞뒤_공백을_빼고_강의를_찾는다() {
        SearchResultDTO result = service.search("  청약 ", null);

        assertThat(result.getCourses()).extracting(CourseLabelDTO::getTitle).containsExactly("청약통장 기초");
        assertThat(result.getTerms()).isEmpty();
    }

    @Test
    void 빈_키워드는_아무것도_찾지_않는다() {
        assertThat(service.search("   ", 8L).getCourses()).isEmpty();
        assertThat(service.search(null, 8L).getStocks()).isEmpty();
        verifyNoInteractions(courseService, stockSearchService, growthService);
    }

    @Test
    void 종목은_Lv8_이상_회원에게만_보여준다() {
        when(growthService.getLevel(8L)).thenReturn(8);
        when(growthService.getLevel(3L)).thenReturn(7);

        assertThat(service.search("청약", 8L).getStocks()).extracting(StockSearchDTO::getName).containsExactly("청약종목");
        assertThat(service.search("청약", 3L).getStocks()).isEmpty();
    }

    @Test
    void 로그인_회원이_없으면_종목은_찾지_않는다() {
        assertThat(service.search("청약", null).getStocks()).isEmpty();
        verify(growthService, never()).getLevel(any());
        verify(stockSearchService, never()).searchStocks(any());
    }
}
