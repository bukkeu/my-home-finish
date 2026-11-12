package com.wanted.moneybeetle.domain.trading.model.service;

import com.wanted.moneybeetle.domain.member.model.dto.PublicProfileDTO;
import com.wanted.moneybeetle.domain.member.model.service.MemberService;
import com.wanted.moneybeetle.domain.member.model.service.RankingAwardReader;
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
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/* comment.
*   담당 : PM (모의투자)
*   #26 시즌 랭킹 조회 (API 62, INV-16~18, SP-62)
*   - 관리자가 랭킹을 공개한 시즌만 볼 수 있다 (공개 전이면 RANKING_NOT_PUBLISHED). 시즌 중에는 비공개
*   - 순위는 정산 때 participant 에 확정해 둔 값(asset_rank / return_rank)을 쓴다. 동점은 같은 순위, 같은 순위 안에서는 참가 순
*   - type : ASSET(순자산, 기본) / RETURN(수익률)
*   - 내 순위(me) : 이 시즌에 참가한 회원이면 그 값, 아니면 null
*   - 상위 3위 수상(ranking_award)이 있으면 뱃지·칭호를 title·hasBadge 로 보여 준다
*   #338 회원별 수상 내역 (findAwards) : 공개 프로필(API 18)의 awards. member 쪽 인터페이스 RankingAwardReader 를 구현해서
*     member 가 trading 을 직접 참조하지 않게 한다 (trading -> member 한 방향, 도메인 순환 없음)
*  */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RankingService implements RankingAwardReader {

    public static final int DEFAULT_SIZE = 10;
    public static final int MAX_SIZE = 50;

    private final TradingSeasonRepository seasonRepository;
    private final SeasonParticipantRepository participantRepository;
    private final RankingAwardRepository awardRepository;
    private final MemberService memberService;

    /** API 62 시즌 랭킹 (type 이 비어 있으면 ASSET). memberId 는 로그인 회원 번호 */
    public RankingDTO findRankings(Long seasonId, String type, int page, int size, Long memberId) {
        String rankingType = normalizeType(type);
        if (page < 0 || size < 1 || size > MAX_SIZE) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
        TradingSeason season = seasonRepository.findById(seasonId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        if (!season.isRankingPublished()) {
            throw new BusinessException(TradingErrorCode.RANKING_NOT_PUBLISHED);
        }

        boolean asset = RankingAward.TYPE_ASSET.equals(rankingType);
        String rankField = asset ? "assetRank" : "returnRank";
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(rankField).ascending()
                .and(Sort.by("seasonParticipantId").ascending()));
        Page<SeasonParticipant> rows = asset
                ? participantRepository.findByTradingSeasonIdAndAssetRankIsNotNull(seasonId, pageRequest)
                : participantRepository.findByTradingSeasonIdAndReturnRankIsNotNull(seasonId, pageRequest);

        List<Long> memberIds = rows.getContent().stream().map(SeasonParticipant::getMemberId).toList();
        Map<Long, String> nicknames = memberService.findNicknames(memberIds);
        Map<Long, RankingAward> awards = memberIds.isEmpty() ? Map.of()
                : awardRepository.findByTradingSeasonIdAndRankingTypeAndMemberIdIn(seasonId, rankingType, memberIds)
                        .stream().collect(Collectors.toMap(RankingAward::getMemberId, Function.identity(),
                                (first, second) -> first));   // 시즌·회원·종류당 1건이 DB 유니크 키로 보장되지만, 어긋나도 랭킹 화면이 500 으로 깨지지 않게 한다

        List<RankingDTO.Item> items = rows.getContent().stream()
                .map(row -> {
                    RankingAward award = awards.get(row.getMemberId());
                    return new RankingDTO.Item(rankOf(row, asset), nicknames.get(row.getMemberId()),
                            award == null ? null : award.getBadgeName(), row.getFinalNetAsset(), row.getReturnRate(),
                            award != null);
                })
                .toList();
        return new RankingDTO(season.getName(), rankingType, rows.getTotalElements(), items,
                findMe(seasonId, memberId, asset));
    }

    /**
     * 회원 한 명의 랭킹 수상 내역 (#338, 공개 프로필 API 18 의 awards).
     * 최근 시즌(시작일 늦은 순)부터, 같은 시즌 안에서는 순자산(ASSET) → 수익률(RETURN) 순서. 수상이 없거나 회원 번호가 없으면 빈 목록
     */
    @Override
    public List<PublicProfileDTO.Award> findAwards(Long memberId) {
        if (memberId == null) {
            return List.of();
        }
        List<RankingAward> awards = awardRepository.findByMemberId(memberId);
        if (awards.isEmpty()) {
            return List.of();
        }
        Map<Long, TradingSeason> seasons = seasonRepository
                .findAllById(awards.stream().map(RankingAward::getTradingSeasonId).distinct().toList()).stream()
                .collect(Collectors.toMap(TradingSeason::getTradingSeasonId, Function.identity()));
        return awards.stream()
                // 랭킹을 공개한 시즌의 수상만 (시즌 랭킹 API 62 와 같은 기준)
                .filter(award -> seasons.containsKey(award.getTradingSeasonId())
                        && seasons.get(award.getTradingSeasonId()).isRankingPublished())
                .sorted(Comparator.comparing((RankingAward award) -> seasons.get(award.getTradingSeasonId()).getStartDate())
                        .reversed()
                        .thenComparing(award -> RankingAward.TYPE_ASSET.equals(award.getRankingType()) ? 0 : 1)
                        .thenComparingInt(RankingAward::getRankNo))
                .map(award -> new PublicProfileDTO.Award(seasons.get(award.getTradingSeasonId()).getName(),
                        award.getRankingType(), award.getRankNo()))
                .toList();
    }

    private RankingDTO.Me findMe(Long seasonId, Long memberId, boolean asset) {
        if (memberId == null) {
            return null;
        }
        return participantRepository.findByTradingSeasonIdAndMemberId(seasonId, memberId)
                .filter(SeasonParticipant::isSettled)
                .map(me -> new RankingDTO.Me(rankOf(me, asset), me.getFinalNetAsset(), me.getReturnRate()))
                .orElse(null);
    }

    private static Integer rankOf(SeasonParticipant participant, boolean asset) {
        return asset ? participant.getAssetRank() : participant.getReturnRank();
    }

    private static String normalizeType(String type) {
        if (type == null || type.isBlank()) {
            return RankingAward.TYPE_ASSET;
        }
        if (RankingAward.TYPE_ASSET.equals(type) || RankingAward.TYPE_RETURN.equals(type)) {
            return type;
        }
        throw new BusinessException(CommonErrorCode.INVALID_INPUT);
    }
}
