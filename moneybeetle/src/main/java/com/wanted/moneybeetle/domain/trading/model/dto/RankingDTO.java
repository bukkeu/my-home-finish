package com.wanted.moneybeetle.domain.trading.model.dto;

import java.math.BigDecimal;
import java.util.List;

/* comment.
*   담당 : PM (#26)
*   시즌 랭킹 응답 (API 62) : 랭킹 한 줄(items) + 내 순위(me, 이 시즌에 참가하지 않았으면 null)
*   rank 는 정산 때 확정된 순위다 (동점은 같은 순위). title·hasBadge 는 상위 3위 수상(뱃지·칭호) 여부
*  */
public record RankingDTO(String seasonName, String type, long totalCount, List<Item> items, Me me) {

    public record Item(int rank, String nickname, String title, long netAsset, BigDecimal returnRate,
            boolean hasBadge) {
    }

    public record Me(Integer rank, long netAsset, BigDecimal returnRate) {
    }
}
