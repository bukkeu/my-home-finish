package com.wanted.moneybeetle.domain.growth.model.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/* comment.
*   담당 : DBA ② (성장·캐시·레벨)
*   캐시가 오가는 원인 종류 (cash_history.source_type). DB 에는 이름(name())이 그대로 문자열로 저장된다 (@Enumerated(STRING)).
*   - label : 캐시 내역 화면에 보이는 기본 문구 (CashHistoryDTO). 관리자 조정은 사유가 있으면 CashHistoryDTO 가 뒤에 붙인다.
*   - 다른 도메인이 문자열 상수(CashHistory.SOURCE_*)를 쓰려고 growth 엔티티를 import 하던 것을 이 enum 하나로 모은다 (이슈 #287).
*  */
@Getter
@RequiredArgsConstructor
public enum CashSource {

    ATTENDANCE("연속 출석 보상"),
    COURSE_COMPLETE("강의 완료"),
    LEVEL_UP("레벨업 보상"),
    SEASON_JOIN("모의투자 시즌 참가"),
    SUBSCRIPTION_DEPOSIT("청약통장 납입"),
    /** 관리자 수동 지급·회수. 사유가 있으면 화면은 "관리자 조정 · 사유" 로 보여 준다 */
    ADMIN("관리자 조정"),
    /** 시즌 랭킹 보상 (SP-64). 원인 ID = 수상 기록(ranking_award)의 ID 라서 같은 수상으로 두 번 받지 않는다 */
    RANKING("시즌 랭킹 보상");

    private final String label;
}
