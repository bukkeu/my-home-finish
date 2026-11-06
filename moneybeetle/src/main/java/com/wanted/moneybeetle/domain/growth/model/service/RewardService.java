package com.wanted.moneybeetle.domain.growth.model.service;

/* comment.
*   담당 : PM (성장·홈·모의 청약)
*   다른 도메인이 부르는 보상 창구 (메서드 이름은 PM 이 먼저 정해서 공유)
*   - grant(memberId, 활동코드, sourceId) : 경험치·캐시 지급, 레벨업(여러 레벨 한꺼번에 SP-23), 해금 알림
*   - useCash(memberId, sourceType, sourceId, amount) : 시즌 참가, 청약 납입 차감 (0 미만 불가)
*   같은 활동 중복 지급 방지 (cash_history / exp_history UNIQUE)
*  */
public class RewardService {

}
