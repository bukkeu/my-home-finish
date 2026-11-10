package com.wanted.moneybeetle.domain.trading.model.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wanted.moneybeetle.domain.member.model.service.MemberService;
import com.wanted.moneybeetle.domain.trading.exception.TradingErrorCode;
import com.wanted.moneybeetle.domain.trading.model.dao.RankingAwardRepository;
import com.wanted.moneybeetle.domain.trading.model.dao.SeasonParticipantRepository;
import com.wanted.moneybeetle.domain.trading.model.dao.TradingSeasonRepository;
import com.wanted.moneybeetle.domain.trading.model.dto.RankingDTO;
import com.wanted.moneybeetle.domain.trading.model.entity.RankingAward;
import com.wanted.moneybeetle.domain.trading.model.entity.SeasonParticipant;
import com.wanted.moneybeetle.domain.trading.model.entity.TradingSeason;
import com.wanted.moneybeetle.global.exception.BusinessException;
import com.wanted.moneybeetle.global.exception.CommonErrorCode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

/* comment.
*   담당 : PM (#26)
*   RankingService 시즌 랭킹 조회 확인 (DB 없이 Repository·다른 Service 는 가짜(mock))
*   - 공개 전 시즌은 RANKING_NOT_PUBLISHED, 공개된 시즌은 확정된 순위 순서(동점은 같은 순위)
*   - type ASSET / RETURN, 내 순위(me), 상위 3위 뱃지·칭호
*  */
class RankingServiceTest {

    private static final long SEASON_ID = 6L;

    private TradingSeasonRepository seasonRepository;
    private SeasonParticipantRepository participantRepository;
    private RankingAwardRepository awardRepository;
    private MemberService memberService;
    private RankingService service;
    private TradingSeason season;

    @BeforeEach
    void setUp() {
        seasonRepository = mock(TradingSeasonRepository.class);
        participantRepository = mock(SeasonParticipantRepository.class);
        awardRepository = mock(RankingAwardRepository.class);
        memberService = mock(MemberService.class);
        service = new RankingService(seasonRepository, participantRepository, awardRepository, memberService);

        season = TradingSeason.create("시즌 6", LocalDate.of(2026, 10, 19), 1L);
        ReflectionTestUtils.setField(season, "tradingSeasonId", SEASON_ID);
        when(seasonRepository.findById(SEASON_ID)).thenReturn(Optional.of(season));
        when(memberService.findNicknames(any())).thenReturn(Map.of(10L, "초록개미", 20L, "파랑고래", 30L, "노랑여우"));
    }

    private void publish() {
        season.changeStatus(TradingSeason.STATUS_RUNNING);
        season.changeStatus(TradingSeason.STATUS_SETTLING);
        season.completeSettlement(LocalDateTime.of(2026, 11, 2, 0, 10));
        season.publishRanking(LocalDateTime.of(2026, 11, 2, 9, 0));
    }

    private SeasonParticipant participant(Long id, Long memberId, long netAsset, String returnRate, int assetRank,
            int returnRank) {
        SeasonParticipant participant = SeasonParticipant.create(SEASON_ID, memberId, 1_000_000, 0);
        ReflectionTestUtils.setField(participant, "seasonParticipantId", id);
        participant.settle(netAsset, new BigDecimal(returnRate), 0);
        participant.changeRanks(assetRank, returnRank);
        return participant;
    }

    @Test
    void 공개_전_시즌은_RANKING_NOT_PUBLISHED_이고_순위를_읽지_않는다() {
        assertThatThrownBy(() -> service.findRankings(SEASON_ID, "ASSET", 0, 10, 10L))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(TradingErrorCode.RANKING_NOT_PUBLISHED));
        verify(participantRepository, never()).findByTradingSeasonIdAndAssetRankIsNotNull(any(), any());
    }

    @Test
    void 없는_시즌은_NOT_FOUND() {
        assertThatThrownBy(() -> service.findRankings(99L, "ASSET", 0, 10, 10L))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.NOT_FOUND));
    }

    @Test
    void 순자산_랭킹은_확정된_순위대로_닉네임과_함께_보여_주고_동점은_같은_순위다() {
        publish();
        SeasonParticipant first = participant(1L, 10L, 8_920_400, "42.80", 1, 1);
        SeasonParticipant tieA = participant(2L, 20L, 5_000_000, "10.00", 2, 2);
        SeasonParticipant tieB = participant(3L, 30L, 5_000_000, "10.00", 2, 2);
        when(participantRepository.findByTradingSeasonIdAndAssetRankIsNotNull(eq(SEASON_ID), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(first, tieA, tieB), PageRequest.of(0, 10), 3));
        when(awardRepository.findByTradingSeasonIdAndRankingTypeAndMemberIdIn(eq(SEASON_ID), eq("ASSET"), any()))
                .thenReturn(List.of(RankingAward.createAssetAward(SEASON_ID, 10L, 1, "내 집")));
        when(participantRepository.findByTradingSeasonIdAndMemberId(SEASON_ID, 20L)).thenReturn(Optional.of(tieA));

        RankingDTO ranking = service.findRankings(SEASON_ID, null, 0, 10, 20L);

        assertThat(ranking.seasonName()).isEqualTo("시즌 6");
        assertThat(ranking.type()).isEqualTo("ASSET");
        assertThat(ranking.totalCount()).isEqualTo(3);
        assertThat(ranking.items()).extracting(RankingDTO.Item::rank).containsExactly(1, 2, 2);
        assertThat(ranking.items()).extracting(RankingDTO.Item::nickname).containsExactly("초록개미", "파랑고래", "노랑여우");
        assertThat(ranking.items().get(0).netAsset()).isEqualTo(8_920_400);
        assertThat(ranking.items().get(0).hasBadge()).isTrue();
        assertThat(ranking.items().get(0).title()).isEqualTo("내 집");
        assertThat(ranking.items().get(1).hasBadge()).isFalse();
        assertThat(ranking.items().get(1).title()).isNull();
        assertThat(ranking.me()).isEqualTo(new RankingDTO.Me(2, 5_000_000L, new BigDecimal("10.00")));

        // 정렬은 "순위 순, 같은 순위 안에서는 참가 순" 요구사항의 핵심이라 조회에 넘긴 정렬 기준을 직접 확인한다
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(participantRepository).findByTradingSeasonIdAndAssetRankIsNotNull(eq(SEASON_ID), pageable.capture());
        assertThat(pageable.getValue().getSort().toString()).isEqualTo("assetRank: ASC,seasonParticipantId: ASC");
    }

    @Test
    void 수익률_랭킹은_수익률_순위로_정렬하고_수익률_수상을_보여_준다() {
        publish();
        SeasonParticipant a = participant(1L, 10L, 3_000_000, "55.00", 4, 1);
        when(participantRepository.findByTradingSeasonIdAndReturnRankIsNotNull(eq(SEASON_ID), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(a), PageRequest.of(0, 10), 1));
        when(awardRepository.findByTradingSeasonIdAndRankingTypeAndMemberIdIn(eq(SEASON_ID), eq("RETURN"), any()))
                .thenReturn(List.of(RankingAward.createReturnAward(SEASON_ID, 10L, 1, "수익왕")));
        when(participantRepository.findByTradingSeasonIdAndMemberId(SEASON_ID, 10L)).thenReturn(Optional.of(a));

        RankingDTO ranking = service.findRankings(SEASON_ID, "RETURN", 0, 10, 10L);

        assertThat(ranking.type()).isEqualTo("RETURN");
        assertThat(ranking.items().get(0).rank()).isEqualTo(1);
        assertThat(ranking.items().get(0).title()).isEqualTo("수익왕");
        assertThat(ranking.me().rank()).isEqualTo(1);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(participantRepository).findByTradingSeasonIdAndReturnRankIsNotNull(eq(SEASON_ID), pageable.capture());
        assertThat(pageable.getValue().getSort().toString()).startsWith("returnRank: ASC");
    }

    @Test
    void 참가하지_않았거나_로그인하지_않았으면_me_는_null() {
        publish();
        when(participantRepository.findByTradingSeasonIdAndAssetRankIsNotNull(eq(SEASON_ID), any(Pageable.class)))
                .thenReturn(Page.empty());
        when(participantRepository.findByTradingSeasonIdAndMemberId(SEASON_ID, 99L)).thenReturn(Optional.empty());

        assertThat(service.findRankings(SEASON_ID, "ASSET", 0, 10, 99L).me()).isNull();
        assertThat(service.findRankings(SEASON_ID, "ASSET", 0, 10, null).me()).isNull();
        assertThat(service.findRankings(SEASON_ID, "ASSET", 0, 10, null).items()).isEmpty();
    }

    @Test
    void 정산하지_않은_참가자는_me_가_없다() {
        publish();
        SeasonParticipant notSettled = SeasonParticipant.create(SEASON_ID, 10L, 1_000_000, 0);
        when(participantRepository.findByTradingSeasonIdAndAssetRankIsNotNull(eq(SEASON_ID), any(Pageable.class)))
                .thenReturn(Page.empty());
        when(participantRepository.findByTradingSeasonIdAndMemberId(SEASON_ID, 10L))
                .thenReturn(Optional.of(notSettled));

        assertThat(service.findRankings(SEASON_ID, "ASSET", 0, 10, 10L).me()).isNull();
    }

    @Test
    void 잘못된_type_page_size_는_INVALID_INPUT() {
        publish();

        for (Object[] bad : new Object[][] {{"PROFIT", 0, 10}, {"ASSET", -1, 10}, {"ASSET", 0, 0}, {"ASSET", 0, 51}}) {
            assertThatThrownBy(() -> service.findRankings(SEASON_ID, (String) bad[0], (int) bad[1], (int) bad[2], 1L))
                    .isInstanceOfSatisfying(BusinessException.class,
                            e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.INVALID_INPUT));
        }
    }
}
