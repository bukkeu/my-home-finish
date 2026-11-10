package com.wanted.moneybeetle.domain.home.model.dto;

import com.wanted.moneybeetle.domain.term.model.dto.EconomicTermDTO;
import java.util.List;

/* comment.
*   담당 : DBA ② (홈)
*   홈 화면 응답 (API 19) - 오늘의 경제 용어, 이어서 볼 강의, 학습 기록, 트레이닝 바로가기를 한 번에 준다.
*   - continueCourse 는 이어서 볼 강의가 없으면 null 이다. lessonId·lessonTitle·positionSeconds 는 그 강의에서 본 영상이 없으면 null 이다.
*  */
public record HomeDTO(
        List<EconomicTermDTO> todayTerms,
        ContinueCourse continueCourse,
        Learning learning,
        Training training) {

    public record ContinueCourse(Long courseId, String title, Long lessonId, String lessonTitle,
                                 Integer positionSeconds) {
    }

    public record Learning(long watchedLessons, long completedCourses) {
    }

    public record Training(Unlock trading, Unlock housing) {
    }

    /** 모의투자·모의 청약 해금 상태 (내 레벨이 requiredLevel 이상이면 unlocked) */
    public record Unlock(boolean unlocked, int requiredLevel) {
    }
}
