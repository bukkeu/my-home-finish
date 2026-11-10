package com.wanted.moneybeetle.domain.trading.model.dto;

/* comment.
*   담당 : PM (모의투자)
*   시즌 참가 결과 (API 54) : 투자 가능 금액 = 모은 캐시 + 이월금 (SP-52)
*  */

import com.wanted.moneybeetle.domain.trading.model.entity.SeasonParticipant;
import lombok.Getter;

@Getter
public class ParticipationResultDTO {

    private final Long participantId;
    private final long savedCash;
    private final long carryoverIn;
    private final long investAmount;

    private ParticipationResultDTO(Long participantId, long savedCash, long carryoverIn, long investAmount) {
        this.participantId = participantId;
        this.savedCash = savedCash;
        this.carryoverIn = carryoverIn;
        this.investAmount = investAmount;
    }

    public static ParticipationResultDTO from(SeasonParticipant participant) {
        return new ParticipationResultDTO(participant.getSeasonParticipantId(), participant.getSavedCash(),
                participant.getCarryoverIn(), participant.getInvestAmount());
    }
}
