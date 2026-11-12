package com.wanted.moneybeetle.domain.member.model.service;

import com.wanted.moneybeetle.domain.member.model.dto.PublicProfileDTO;
import java.util.List;

/* comment.
*   담당 : PM 이재성 (#338, 공개 프로필 수상 내역)
*   공개 프로필(API 18)의 랭킹 수상 내역을 받아 오는 창구.
*   member 가 trading 을 직접 알지 않도록 member 쪽에 둔 인터페이스이고, trading.RankingService 가 구현한다.
*   trading 은 이미 member(MemberService 의 닉네임 조회 등)를 쓰므로 참조는 trading -> member 한 방향으로 둔다 (WithdrawVerifier 와 같은 방식).
*  */
public interface RankingAwardReader {

    /**
     * 회원 한 명의 랭킹 수상 내역. 랭킹을 공개한 시즌만, 최근 시즌(시작일 늦은 순)부터,
     * 같은 시즌 안에서는 순자산(ASSET) → 수익률(RETURN), 순위 순서. 수상이 없거나 회원 번호가 없으면 빈 목록
     */
    List<PublicProfileDTO.Award> findAwards(Long memberId);
}
