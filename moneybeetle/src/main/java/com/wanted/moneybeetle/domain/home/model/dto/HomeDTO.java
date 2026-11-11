package com.wanted.moneybeetle.domain.home.model.dto;

import com.wanted.moneybeetle.domain.term.model.dto.EconomicTermDTO;
import java.util.List;

/* comment.
*   담당 : DBA ② (홈)
*   홈 화면 응답 (API 19) - 오늘의 경제 용어, 이어서 볼 강의, 학습 기록, 트레이닝 바로가기를 한 번에 준다.
*   - continueCourse 는 이어서 볼 강의가 없으면 null 이다. lessonId·lessonTitle·positionSeconds 는 그 강의에서 본 영상이 없으면 null 이다.
*     durationSeconds·lessonNumber·lessonCount·categoryName 은 시안의 "08:24 / 14:30", "3강 중 2강", 소주제 표시용이다 (#261).
*     본 영상이 없거나 값을 만들 수 없으면 null 이다.
*   - training 의 tradingSeason·housingSummary 는 시안의 트레이닝 카드 숫자(시즌·D-day·공고 수·납입 횟수)용이다 (#261).
*     해금되지 않은 기능이거나 값을 만들 수 없으면(시즌·통장 없음 등) 오류가 아니라 null 이다.
*  */
public record HomeDTO(
        List<EconomicTermDTO> todayTerms,
        ContinueCourse continueCourse,
        Learning learning,
        Training training) {

    public record ContinueCourse(Long courseId, String title, Long lessonId, String lessonTitle,
                                 Integer positionSeconds, Integer durationSeconds, Integer lessonNumber,
                                 Integer lessonCount, String categoryName) {
    }

    public record Learning(long watchedLessons, long completedCourses) {
    }

    public record Training(Unlock trading, Unlock housing, TradingSeason tradingSeason, HousingSummary housingSummary) {

        /** 요약 없이 해금 상태만 있는 경우 (요약은 null) */
        public Training(Unlock trading, Unlock housing) {
            this(trading, housing, null, null);
        }
    }

    /** 모의투자 카드 요약: 지금 보이는 시즌의 이름·상태·현재 턴·전체 턴·종료일까지 남은 일수(오늘 포함 아니고 종료일 - 오늘, 0 아래로 내려가지 않는다) */
    public record TradingSeason(Long seasonId, String name, String status, int currentTurn, int totalTurns, int daysLeft) {
    }

    /** 모의 청약 카드 요약: 접수 중인 공고 수(이번 달에 걸친 단지 중 상태 OPEN)와 내 청약통장 납입 횟수(통장이 없으면 null) */
    public record HousingSummary(int openComplexes, Integer paidCount) {
    }

    /** 모의투자·모의 청약 해금 상태 (내 레벨이 requiredLevel 이상이면 unlocked) */
    public record Unlock(boolean unlocked, int requiredLevel) {
    }
}
