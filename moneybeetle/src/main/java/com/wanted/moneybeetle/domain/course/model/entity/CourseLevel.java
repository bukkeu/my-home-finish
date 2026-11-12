package com.wanted.moneybeetle.domain.course.model.entity;

/* comment.
 *   담당 : DBA ① (강의·퀴즈) — #369 는 신아림이 나눠 준 작업 (이재성)
 *   강의 난이도 (course.level). DB 에는 이름 그대로(VARCHAR) 저장한다. 안 고르면 입문(BEGINNER)
 *  */
public enum CourseLevel {
    BEGINNER,       // 입문
    ELEMENTARY,     // 초급
    INTERMEDIATE,   // 중급
    ADVANCED;       // 고급

    /** 요청에 없으면(null) 입문. 값 검사는 CourseSaveDTO 가 먼저 한다 */
    public static CourseLevel orDefault(String level) {
        return level == null ? BEGINNER : valueOf(level);
    }
}
