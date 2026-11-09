package com.wanted.moneybeetle.domain.home.model.service;

/* comment.
*   담당 : DBA ② (홈 API, #13) · PM (통합 검색 API, #15)
*   course / term / trading 서비스의 조회 메서드만 모아서 보여 준다 (다른 도메인 테이블 직접 조회 금지)
*   - 통합 검색(#15, API 28) : 강의(보이는 강의만), 경제 용어, 종목(모의투자 해금 Lv.8 이상일 때만)
*     키워드는 앞뒤 공백을 빼고, 비어 있으면 빈 결과
*     TODO(DBA ② TermService) 경제 용어 검색 메서드가 생기면 terms 를 채운다 (지금은 빈 목록)
*  */

import com.wanted.moneybeetle.domain.course.model.service.CourseService;
import com.wanted.moneybeetle.domain.growth.model.service.GrowthService;
import com.wanted.moneybeetle.domain.home.model.dto.SearchResultDTO;
import com.wanted.moneybeetle.domain.trading.model.dto.StockSearchDTO;
import com.wanted.moneybeetle.domain.trading.model.service.StockSearchService;
import com.wanted.moneybeetle.global.security.UnlockFeature;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HomeService {

    private final CourseService courseService;
    private final StockSearchService stockSearchService;
    private final GrowthService growthService;

    /** API 28 통합 검색. memberId 는 세션의 로그인 회원 번호이고, 없으면 종목은 찾지 않는다 */
    public SearchResultDTO search(String keyword, Long memberId) {
        String word = keyword == null ? "" : keyword.trim();
        if (word.isEmpty()) {
            return SearchResultDTO.empty();
        }
        return SearchResultDTO.of(courseService.search(word), List.of(), searchStocksIfUnlocked(word, memberId));
    }

    /** 종목은 모의투자가 해금된 회원에게만 보여 준다 (INV-01) */
    private List<StockSearchDTO> searchStocksIfUnlocked(String keyword, Long memberId) {
        // TODO(SCM #4) 해금 레벨을 system_setting 에서 읽게 되면 같은 값을 쓴다 (LevelCheckInterceptor 와 동일)
        if (memberId == null || growthService.getLevel(memberId) < UnlockFeature.TRADING.getDefaultLevel()) {
            return List.of();
        }
        return stockSearchService.searchStocks(keyword);
    }
}
