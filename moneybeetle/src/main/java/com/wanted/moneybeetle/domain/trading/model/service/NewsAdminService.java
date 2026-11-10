package com.wanted.moneybeetle.domain.trading.model.service;

/* comment.
*   담당 : PM (모의투자)
*   관리자 가상 뉴스 관리 (#43, API 131~134, ADM-25~27, SP-60, DP-41)
*   - 지난 턴 = 현재 턴 이하 (이미 사용자에게 공개된 뉴스). 가격표(#42)와 같은 기준
*   - 지난 턴 뉴스는 오타 수정만 : 제목·본문·관리자 메모만 고칠 수 있고, 공개 턴과 관련 분야·종목은 바꿀 수 없다.
*     삭제도 막는다 -> PAST_TURN_NEWS_LOCKED. 지난 턴에 새 뉴스를 넣거나 다음 턴 뉴스를 지난 턴으로 옮기는 것도 막는다
*   - 관련 분야·종목은 등록할 때와 다음 턴 이후 뉴스를 고칠 때 통째로 바꾼다. 없는 번호면 404
*   - 관리자 메모는 이 관리자 API 에만 나오고 사용자 뉴스 API(#24)에는 나오지 않는다
*   - TODO(#6, operation AdminLogService) 지난 턴 뉴스 수정은 admin_log 에 남긴다 (DB설계 데이터 정책).
*     admin_log.admin_id 가 NOT NULL 이라 로그인 관리자 번호와 AdminLogService 가 생긴 뒤 연결한다
*  */

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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NewsAdminService {

    private final TradingSeasonRepository seasonRepository;
    private final NewsRepository newsRepository;
    private final NewsTagRepository newsTagRepository;
    private final SectorRepository sectorRepository;
    private final StockRepository stockRepository;

    /** API 131 뉴스 목록 : 시즌 전체 또는 한 턴, 턴 순서. 관리자 메모 포함 */
    public List<AdminNewsDTO> findNews(Long seasonId, Integer turnNo) {
        getSeason(seasonId);
        List<News> newsList = turnNo == null
                ? newsRepository.findByTradingSeasonIdOrderByTurnNoAscNewsIdAsc(seasonId)
                : newsRepository.findByTradingSeasonIdAndTurnNoOrderByNewsIdAsc(seasonId, turnNo);
        if (newsList.isEmpty()) {
            return List.of();
        }

        Map<Long, List<NewsTag>> tagsByNews = newsTagRepository
                .findByNewsIdIn(newsList.stream().map(News::getNewsId).toList()).stream()
                .collect(Collectors.groupingBy(NewsTag::getNewsId));
        Map<Long, String> sectorNames = sectorRepository.findAll().stream()
                .collect(Collectors.toMap(Sector::getSectorId, Sector::getName));
        Map<Long, String> stockNames = stockRepository.findAll().stream()
                .collect(Collectors.toMap(Stock::getStockId, Stock::getName));

        return newsList.stream()
                .map(news -> {
                    List<NewsTag> tags = tagsByNews.getOrDefault(news.getNewsId(), List.of());
                    return AdminNewsDTO.of(news,
                            tags.stream().filter(NewsTag::isSectorTag)
                                    .map(tag -> AdminNewsDTO.Tag.of(tag.getSectorId(), sectorNames.get(tag.getSectorId())))
                                    .toList(),
                            tags.stream().filter(NewsTag::isStockTag)
                                    .map(tag -> AdminNewsDTO.Tag.of(tag.getStockId(), stockNames.get(tag.getStockId())))
                                    .toList());
                })
                .toList();
    }

    /** API 132 뉴스 등록 : 다음 턴 이후에만 */
    @Transactional
    public Long createNews(Long seasonId, NewsSaveDTO request) {
        TradingSeason season = getSeasonForUpdate(seasonId);
        checkNotPastTurn(request.getTurnNo(), season);
        checkTagsExist(request);

        News news = newsRepository.save(News.create(seasonId, request.getTurnNo(), request.getTitle(),
                request.getContent(), request.getAdminMemo()));
        newsTagRepository.saveAll(toTags(news.getNewsId(), request));
        return news.getNewsId();
    }

    /** API 133 뉴스 수정 : 지난 턴 뉴스는 제목·본문·메모만 */
    @Transactional
    public void changeNews(Long newsId, NewsSaveDTO request) {
        News news = getNews(newsId);
        TradingSeason season = getSeasonForUpdate(news.getTradingSeasonId());

        if (news.isPublishedBy(season.getCurrentTurn())) {
            if (news.getTurnNo() != request.getTurnNo() || !sameTags(newsTagRepository.findByNewsId(newsId), request)) {
                throw new BusinessException(TradingErrorCode.PAST_TURN_NEWS_LOCKED);
            }
            news.changeContent(request.getTitle(), request.getContent(), request.getAdminMemo());
            return;
        }

        checkNotPastTurn(request.getTurnNo(), season);
        checkTagsExist(request);
        news.changeContent(request.getTitle(), request.getContent(), request.getAdminMemo());
        news.changeTurnNo(request.getTurnNo());
        newsTagRepository.deleteByNewsId(newsId);
        newsTagRepository.saveAll(toTags(newsId, request));
    }

    /** API 134 뉴스 삭제 : 다음 턴 이후 뉴스만 (지난 턴은 지우지 않는다, DP-41) */
    @Transactional
    public void deleteNews(Long newsId) {
        News news = getNews(newsId);
        TradingSeason season = getSeasonForUpdate(news.getTradingSeasonId());
        if (news.isPublishedBy(season.getCurrentTurn())) {
            throw new BusinessException(TradingErrorCode.PAST_TURN_NEWS_LOCKED);
        }
        newsTagRepository.deleteByNewsId(newsId);
        newsRepository.delete(news);
    }

    private TradingSeason getSeason(Long seasonId) {
        return seasonRepository.findById(seasonId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
    }

    /**
     * 뉴스를 바꾸는 작업은 턴 진행(TurnAdminService.advanceTurn)과 같은 시즌 행 잠금을 잡고 현재 턴을 확인한다.
     * 점검 직후 다른 관리자가 다음 턴의 유일한 뉴스를 지워도 뉴스 없이 턴이 넘어가지 않는다
     */
    private TradingSeason getSeasonForUpdate(Long seasonId) {
        return seasonRepository.findByIdForUpdate(seasonId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
    }

    private News getNews(Long newsId) {
        return newsRepository.findById(newsId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
    }

    /** 현재 턴 이하(이미 공개된 턴)에는 뉴스를 넣거나 옮길 수 없다 */
    private static void checkNotPastTurn(int turnNo, TradingSeason season) {
        if (turnNo <= season.getCurrentTurn()) {
            throw new BusinessException(TradingErrorCode.PAST_TURN_NEWS_LOCKED);
        }
    }

    private void checkTagsExist(NewsSaveDTO request) {
        Set<Long> sectorIds = Set.copyOf(request.getSectorIds());
        Set<Long> stockIds = Set.copyOf(request.getStockIds());
        if (sectorRepository.findAllById(sectorIds).size() != sectorIds.size()
                || stockRepository.findAllById(stockIds).size() != stockIds.size()) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND);
        }
    }

    /** 요청의 관련 분야·종목이 지금 걸린 것과 같은지 (순서·중복 무시) */
    private static boolean sameTags(List<NewsTag> current, NewsSaveDTO request) {
        Set<Long> currentSectors = current.stream().filter(NewsTag::isSectorTag).map(NewsTag::getSectorId)
                .collect(Collectors.toSet());
        Set<Long> currentStocks = current.stream().filter(NewsTag::isStockTag).map(NewsTag::getStockId)
                .collect(Collectors.toSet());
        return currentSectors.equals(Set.copyOf(request.getSectorIds()))
                && currentStocks.equals(Set.copyOf(request.getStockIds()));
    }

    /** 한 행에 분야 또는 종목 하나 (중복 번호는 한 번만) */
    private static List<NewsTag> toTags(Long newsId, NewsSaveDTO request) {
        List<NewsTag> tags = new ArrayList<>();
        request.getSectorIds().stream().distinct().forEach(id -> tags.add(NewsTag.createForSector(newsId, id)));
        request.getStockIds().stream().distinct().forEach(id -> tags.add(NewsTag.createForStock(newsId, id)));
        return tags;
    }
}
