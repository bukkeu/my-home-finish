package com.wanted.moneybeetle.domain.trading.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

/* comment.
*   담당 : PM (모의투자)
*   테이블 ranking_award (상위 3위)
*  */
@Entity
@Table(name = "ranking_award")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RankingAward {

    public static final String TYPE_ASSET = "ASSET";
    public static final String TYPE_RETURN = "RETURN";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long rankingAwardId;

    private Long tradingSeasonId;

    /** 회원 (member 도메인 - ID 참조) */
    private Long memberId;

    /** ASSET 순자산 / RETURN 수익률 */
    private String rankingType;

    /** 순위 1~3 */
    private int rankNo;

    private String badgeName;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime awardedAt;

    private RankingAward(Long tradingSeasonId, Long memberId, String rankingType, int rankNo,
            String badgeName) {
        this.tradingSeasonId = tradingSeasonId;
        this.memberId = memberId;
        this.rankingType = rankingType;
        this.rankNo = rankNo;
        this.badgeName = badgeName;
    }

    public static RankingAward createAssetAward(Long tradingSeasonId, Long memberId, int rankNo,
            String badgeName) {
        return new RankingAward(tradingSeasonId, memberId, TYPE_ASSET, rankNo, badgeName);
    }

    public static RankingAward createReturnAward(Long tradingSeasonId, Long memberId, int rankNo,
            String badgeName) {
        return new RankingAward(tradingSeasonId, memberId, TYPE_RETURN, rankNo, badgeName);
    }
}
