package com.wanted.moneybeetle.domain.trading.model.service;

/* comment.
*   담당 : PM (모의투자)
*   관리자 가상 뉴스 관리 (#43, API 131~134, ADM-25~27, SP-60, DP-41)
*   - 지난 턴 = 현재 턴 이하 (이미 사용자에게 공개된 뉴스). 가격표(#42)와 같은 기준
*   - 지난 턴 뉴스는 오타 수정만 : 제목·본문·관리자 메모만 고칠 수 있고, 공개 턴과 관련 분야·종목은 바꿀 수 없다.
*     삭제도 막는다 -> PAST_TURN_NEWS_LOCKED. 지난 턴에 새 뉴스를 넣거나 다음 턴 뉴스를 지난 턴으로 옮기는 것도 막는다
*   - 관련 분야·종목은 등록할 때와 다음 턴 이후 뉴스를 고칠 때 통째로 바꾼다. 없는 번호면 404
*   - 관리자 메모는 이 관리자 API 에만 나오고 사용자 뉴스 API(#24)에는 나오지 않는다
*   - 지난 턴 뉴스 수정은 admin_log 에 남긴다 (DB설계 DH-03 데이터 정책, #340) : NEWS_UPDATE / NEWS / 뉴스 번호,
*     detail 에 바뀌기 전후 제목·본문·관리자 메모. 바뀐 값이 없으면 남기지 않는다. 같은 트랜잭션이라 수정이 되돌려지면 기록도 없다.
*     다음 턴 이후 뉴스(아직 사용자에게 공개 전)의 등록·수정·삭제는 정책상 필수가 아니라 남기지 않는다
*  */

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wanted.moneybeetle.domain.operation.model.service.AdminLogService;
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
import java.util.LinkedHashMap;
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

    static final String ACTION_NEWS_UPDATE = "NEWS_UPDATE";
    static final String TARGET_TYPE_NEWS = "NEWS";
    private static final ObjectMapper JSON = new ObjectMapper();

    private final TradingSeasonRepository seasonRepository;
    private final NewsRepository newsRepository;
    private final NewsTagRepository newsTagRepository;
    private final SectorRepository sectorRepository;
    private final StockRepository stockRepository;
    private final AdminLogService adminLogService;                      // 관리자 작업 기록 (operation 도메인)

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

    /** API 133 뉴스 수정 : 지난 턴 뉴스는 제목·본문·메모만, 고치면 관리자 작업 기록에 남긴다 */
    @Transactional
    public void changeNews(Long newsId, NewsSaveDTO request, Long adminId) {
        TradingAdminCheck.requireAdmin(adminId);
        LockedNews locked = lockNewsAndSeason(newsId);
        News news = locked.news();
        TradingSeason season = locked.season();

        if (news.isPublishedBy(season.getCurrentTurn())) {
            if (news.getTurnNo() != request.getTurnNo() || !sameTags(newsTagRepository.findByNewsId(newsId), request)) {
                throw new BusinessException(TradingErrorCode.PAST_TURN_NEWS_LOCKED);
            }
            Map<String, Object> before = snapshot(news);
            news.changeContent(request.getTitle(), request.getContent(), request.getAdminMemo());
            Map<String, Object> after = snapshot(news);
            if (!before.equals(after)) {
                adminLogService.record(adminId, ACTION_NEWS_UPDATE, TARGET_TYPE_NEWS, newsId, toJson(before, after));
            }
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
        LockedNews locked = lockNewsAndSeason(newsId);
        News news = locked.news();
        if (news.isPublishedBy(locked.season().getCurrentTurn())) {
            throw new BusinessException(TradingErrorCode.PAST_TURN_NEWS_LOCKED);
        }
        newsTagRepository.deleteByNewsId(newsId);
        newsRepository.delete(news);
    }

    /** 작업 기록에 남길 값 : 지난 턴 뉴스에서 고칠 수 있는 제목·본문·관리자 메모 (공개 턴은 바뀌지 않지만 찾기 쉽게 함께 둔다) */
    private static Map<String, Object> snapshot(News news) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("turnNo", news.getTurnNo());
        values.put("title", news.getTitle());
        values.put("content", news.getContent());
        values.put("adminMemo", news.getAdminMemo());
        return values;
    }

    private static String toJson(Map<String, Object> before, Map<String, Object> after) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("before", before);
        detail.put("after", after);
        try {
            return JSON.writeValueAsString(detail);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("작업 기록을 만들지 못했습니다.", e);
        }
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

    /**
     * 뉴스 수정·삭제 : 시즌 번호만 먼저 읽고(News 를 영속성 컨텍스트에 올리지 않는다) 시즌 행을 잠근 뒤 뉴스를 잠금 읽기로 읽는다.
     * 시즌 잠금을 기다리는 사이 다른 관리자가 뉴스의 턴을 옮기고 턴을 넘겼어도, 최신 턴으로 공개 여부를 판단한다.
     * 단순 findById 는 앞서 읽은 객체나 오래된 스냅샷을 돌려줄 수 있어 쓰지 않는다
     */
    private LockedNews lockNewsAndSeason(Long newsId) {
        Long seasonId = newsRepository.findSeasonIdByNewsId(newsId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        TradingSeason season = getSeasonForUpdate(seasonId);
        News news = newsRepository.findByIdForUpdate(newsId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        return new LockedNews(news, season);
    }

    private record LockedNews(News news, TradingSeason season) {
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
