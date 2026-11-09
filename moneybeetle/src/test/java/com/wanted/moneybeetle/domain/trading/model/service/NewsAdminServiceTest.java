package com.wanted.moneybeetle.domain.trading.model.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wanted.moneybeetle.domain.trading.exception.TradingErrorCode;
import com.wanted.moneybeetle.domain.trading.model.dao.NewsRepository;
import com.wanted.moneybeetle.domain.trading.model.dao.NewsTagRepository;
import com.wanted.moneybeetle.domain.trading.model.dao.SectorRepository;
import com.wanted.moneybeetle.domain.trading.model.dao.StockRepository;
import com.wanted.moneybeetle.domain.trading.model.dao.TradingSeasonRepository;
import com.wanted.moneybeetle.domain.trading.model.dto.AdminNewsDTO;
import com.wanted.moneybeetle.domain.trading.model.dto.NewsSaveDTO;
import com.wanted.moneybeetle.domain.trading.model.entity.News;
import com.wanted.moneybeetle.domain.trading.model.entity.NewsTag;
import com.wanted.moneybeetle.domain.trading.model.entity.Sector;
import com.wanted.moneybeetle.domain.trading.model.entity.Stock;
import com.wanted.moneybeetle.domain.trading.model.entity.TradingSeason;
import com.wanted.moneybeetle.global.exception.BusinessException;
import com.wanted.moneybeetle.global.exception.CommonErrorCode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

/* comment.
*   담당 : PM (#43)
*   NewsAdminService 확인 (DB 없이 Repository 는 가짜(mock))
*   - 지난 턴(현재 턴 이하) 뉴스 : 제목·본문·메모만 수정, 턴·관련 분야·종목 변경과 삭제는 PAST_TURN_LOCKED
*   - 지난 턴에 등록하거나 옮기기 막음, 없는 분야·종목은 404, 목록에 관리자 메모 포함
*  */
class NewsAdminServiceTest {

    private static final long SEASON_ID = 7L;

    private TradingSeasonRepository seasonRepository;
    private NewsRepository newsRepository;
    private NewsTagRepository newsTagRepository;
    private SectorRepository sectorRepository;
    private StockRepository stockRepository;
    private NewsAdminService service;

    @BeforeEach
    void setUp() {
        seasonRepository = mock(TradingSeasonRepository.class);
        newsRepository = mock(NewsRepository.class);
        newsTagRepository = mock(NewsTagRepository.class);
        sectorRepository = mock(SectorRepository.class);
        stockRepository = mock(StockRepository.class);
        service = new NewsAdminService(seasonRepository, newsRepository, newsTagRepository, sectorRepository,
                stockRepository);

        // 시즌 7 은 5턴 진행 중
        TradingSeason season = TradingSeason.create("시즌 7", LocalDate.of(2026, 10, 19), new BigDecimal("100.00"),
                null, 1L);
        ReflectionTestUtils.setField(season, "tradingSeasonId", SEASON_ID);
        season.start();
        for (int i = 0; i < 5; i++) {
            season.advanceTurn();
        }
        when(seasonRepository.findById(SEASON_ID)).thenReturn(Optional.of(season));
        // 분야 1, 종목 1 은 있다
        when(sectorRepository.findAllById(Set.of(1L))).thenReturn(List.of(sector(1L, "반도체")));
        when(stockRepository.findAllById(Set.of(1L))).thenReturn(List.of(stock(1L, "한빛반도체")));
        when(sectorRepository.findAllById(Set.of())).thenReturn(List.of());
        when(stockRepository.findAllById(Set.of())).thenReturn(List.of());
    }

    private Sector sector(long id, String name) {
        Sector sector = Sector.create(name, (int) id);
        ReflectionTestUtils.setField(sector, "sectorId", id);
        return sector;
    }

    private Stock stock(long id, String name) {
        Stock stock = Stock.create(1L, name, 10_000);
        ReflectionTestUtils.setField(stock, "stockId", id);
        return stock;
    }

    private News savedNews(long id, int turnNo) {
        News news = News.create(SEASON_ID, turnNo, "원래 제목", "원래 본문", "원래 메모");
        ReflectionTestUtils.setField(news, "newsId", id);
        when(newsRepository.findById(id)).thenReturn(Optional.of(news));
        return news;
    }

    private static NewsSaveDTO request(int turnNo, List<Long> sectorIds, List<Long> stockIds) {
        return new NewsSaveDTO(turnNo, "새 제목", "새 본문", sectorIds, stockIds, "새 메모");
    }

    @Test
    void 다음_턴_뉴스를_관련_분야_종목과_함께_등록한다() {
        when(newsRepository.save(any(News.class))).thenAnswer(invocation -> {
            News saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "newsId", 40L);
            return saved;
        });

        Long newsId = service.createNews(SEASON_ID, request(6, List.of(1L), List.of(1L, 1L)));

        assertThat(newsId).isEqualTo(40L);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Iterable<NewsTag>> tags = ArgumentCaptor.forClass(Iterable.class);
        verify(newsTagRepository).saveAll(tags.capture());
        // 같은 종목 번호가 두 번 와도 한 번만 건다
        assertThat(tags.getValue()).hasSize(2)
                .anyMatch(tag -> tag.isSectorTag() && tag.getSectorId() == 1L)
                .anyMatch(tag -> tag.isStockTag() && tag.getStockId() == 1L);
    }

    @Test
    void 지난_턴에는_뉴스를_등록할_수_없다() {
        assertThatThrownBy(() -> service.createNews(SEASON_ID, request(5, List.of(), List.of())))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(TradingErrorCode.PAST_TURN_LOCKED);
        verify(newsRepository, never()).save(any());
    }

    @Test
    void 없는_분야나_종목을_걸면_NOT_FOUND() {
        when(stockRepository.findAllById(Set.of(99L))).thenReturn(List.of());

        assertThatThrownBy(() -> service.createNews(SEASON_ID, request(6, List.of(), List.of(99L))))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.NOT_FOUND);
        verify(newsRepository, never()).save(any());
    }

    @Test
    void 없는_시즌이면_NOT_FOUND() {
        when(seasonRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findNews(99L, null))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.NOT_FOUND);
    }

    @Test
    void 다음_턴_뉴스는_턴과_관련_분야_종목까지_고칠_수_있다() {
        News news = savedNews(40L, 6);

        service.changeNews(40L, request(8, List.of(1L), List.of()));

        assertThat(news.getTurnNo()).isEqualTo(8);
        assertThat(news.getTitle()).isEqualTo("새 제목");
        verify(newsTagRepository).deleteByNewsId(40L);
        verify(newsTagRepository).saveAll(anyIterable());
    }

    @Test
    void 다음_턴_뉴스를_지난_턴으로_옮길_수_없다() {
        News news = savedNews(40L, 6);

        assertThatThrownBy(() -> service.changeNews(40L, request(5, List.of(), List.of())))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(TradingErrorCode.PAST_TURN_LOCKED);
        assertThat(news.getTurnNo()).isEqualTo(6);
    }

    @Test
    void 지난_턴_뉴스는_제목_본문_메모만_고칠_수_있다() {
        News news = savedNews(9L, 5);
        when(newsTagRepository.findByNewsId(9L)).thenReturn(List.of(
                NewsTag.createForSector(9L, 1L), NewsTag.createForStock(9L, 1L)));

        service.changeNews(9L, request(5, List.of(1L), List.of(1L)));

        assertThat(news.getTitle()).isEqualTo("새 제목");
        assertThat(news.getAdminMemo()).isEqualTo("새 메모");
        verify(newsTagRepository, never()).deleteByNewsId(any());
    }

    @Test
    void 지난_턴_뉴스의_턴이나_관련_분야_종목은_바꿀_수_없다() {
        News news = savedNews(9L, 5);
        when(newsTagRepository.findByNewsId(9L)).thenReturn(List.of(NewsTag.createForStock(9L, 1L)));

        assertThatThrownBy(() -> service.changeNews(9L, request(6, List.of(), List.of(1L))))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(TradingErrorCode.PAST_TURN_LOCKED);
        assertThatThrownBy(() -> service.changeNews(9L, request(5, List.of(1L), List.of(1L))))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(TradingErrorCode.PAST_TURN_LOCKED);
        assertThat(news.getTitle()).isEqualTo("원래 제목");
    }

    @Test
    void 지난_턴_뉴스는_지울_수_없고_다음_턴_뉴스는_관련_분야_종목과_함께_지운다() {
        News past = savedNews(9L, 5);
        News future = savedNews(40L, 6);

        assertThatThrownBy(() -> service.deleteNews(9L))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(TradingErrorCode.PAST_TURN_LOCKED);
        verify(newsRepository, never()).delete(past);

        service.deleteNews(40L);
        verify(newsTagRepository).deleteByNewsId(40L);
        verify(newsRepository).delete(future);
    }

    @Test
    void 없는_뉴스를_고치거나_지우면_NOT_FOUND() {
        when(newsRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.changeNews(99L, request(6, List.of(), List.of())))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.NOT_FOUND);
        assertThatThrownBy(() -> service.deleteNews(99L))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.NOT_FOUND);
    }

    @Test
    void 목록은_관리자_메모와_관련_분야_종목_이름을_보여준다() {
        News news = savedNews(9L, 5);
        when(newsRepository.findByTradingSeasonIdAndTurnNoOrderByNewsIdAsc(SEASON_ID, 5)).thenReturn(List.of(news));
        when(newsTagRepository.findByNewsIdIn(List.of(9L))).thenReturn(List.of(
                NewsTag.createForSector(9L, 1L), NewsTag.createForStock(9L, 1L)));
        when(sectorRepository.findAll()).thenReturn(List.of(sector(1L, "반도체")));
        when(stockRepository.findAll()).thenReturn(List.of(stock(1L, "한빛반도체")));

        List<AdminNewsDTO> items = service.findNews(SEASON_ID, 5);

        assertThat(items).hasSize(1);
        assertThat(items.get(0).getAdminMemo()).isEqualTo("원래 메모");
        assertThat(items.get(0).getSectors()).extracting(AdminNewsDTO.Tag::getName).containsExactly("반도체");
        assertThat(items.get(0).getStocks()).extracting(AdminNewsDTO.Tag::getId).containsExactly(1L);
    }
}
