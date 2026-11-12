package com.wanted.moneybeetle.domain.quiz.model.dto;

import java.time.LocalDateTime;

/* comment.
 *   담당 : DBA ① (강의·퀴즈)
 *   강의 하나의 지금 쓰는 문항 수 (테스트 퀴즈 OX / 퀴즈 4지선다)와 가장 늦게 바뀐 시각
 *   강사 센터 퀴즈 목록 (API 150, #366)이 course 도메인에서 쓴다. 보관한 문항(TEST_OLD / MAIN_OLD)은 세지 않는다
 *  */
public record CourseQuizStatDTO(Long courseId, long testCount, long mainCount, LocalDateTime updatedAt) {
}
